package com.bingo.polyglot.worker.consumer

import com.bingo.polyglot.core.constants.KafkaTopics
import com.bingo.polyglot.core.constants.TaskStatus
import com.bingo.polyglot.core.dto.CreateTaskMessage
import com.bingo.polyglot.core.entity.*
import com.bingo.polyglot.core.exception.TaskException
import com.bingo.polyglot.core.storage.MinioStorage
import com.bingo.polyglot.worker.config.Translator
import com.bingo.polyglot.worker.util.KafkaControl
import com.bingo.polyglot.worker.util.WerUtil
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.plus
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import oshi.SystemInfo
import java.time.OffsetDateTime
import kotlin.time.measureTime

@Component
class TranslationTaskConsumer(
  private val sql: KSqlClient,
  private val minioStorage: MinioStorage,
  private val whisperApi: RestClient,
  private val translator: Translator,
  private val kafka: KafkaTemplate<String, CreateTaskMessage>,
  private val kafkaControl: KafkaControl,
) {

  @KafkaListener(
    id = KafkaTopics.TRANSLATION_TASK_CREATE,
    topics = [KafkaTopics.TRANSLATION_TASK_CREATE],
  )
  fun listenCreateTask(message: CreateTaskMessage) {
    val taskId = message.taskId
    val logPrefix = "[TranslationTask:$taskId]"
    // 1. Check current node's memory load before processing.
    val ratio = memoryUsageRatio()
    if (ratio > 0.9) {
      logger.warn(
        "$logPrefix Memory usage ratio is too high: ${"%.2f".format(ratio * 100)}%, skipping task processing"
      )
      kafkaControl.pause(KafkaTopics.TRANSLATION_TASK_CREATE)
      sendMqMessage(taskId)
      return
    }
    logger.info("$logPrefix Received translation task create message")
    // 2. Load task details from the database
    val task =
      sql.findById(TRANSLATE_TASK, taskId)
        ?: throw TaskException.taskNotFound(message = "Task $taskId not found", taskId = taskId)
    if (task.status !in listOf(TaskStatus.PENDING, TaskStatus.RETRY_SCHEDULED)) {
      logger.warn("$logPrefix Skip processing task because status is ${task.status}")
      return
    }

    try {
      val duration = measureTime {
        checkCanceled(taskId)
        sql.executeUpdate(TranslationTask::class) {
          set(table.status, TaskStatus.RUNNING)
          where(table.id eq taskId)
        }
        logger.info("$logPrefix Change translation task status to RUNNING")
        checkCanceled(taskId)
        // 3. Call Whisper service for speech recognition
        logger.info("$logPrefix Start calling Whisper service")
        val sttText =
          minioStorage.getObject(task.sourceAudio.path).use { response ->
            val builder = MultipartBodyBuilder()
            builder
              .part("audio_file", InputStreamResource(response))
              .filename(task.sourceAudio.name)
              .contentType(MediaType.valueOf(task.sourceAudio.contentType))
            whisperApi
              .post()
              .uri("/asr")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(builder.build())
              .retrieve()
              .body(String::class.java)
          }
        logger.info("$logPrefix Finished calling Whisper service")
        sql.executeUpdate(TranslationTask::class) {
          set(table.sttText, sttText)
          where(table.id eq taskId)
        }
        logger.info("$logPrefix Saved STT text")

        val originalText = task.originalText
        if (originalText != null && sttText != null) {
          checkCanceled(taskId)
          // 4. Perform accuracy validation (WER)
          logger.info("$logPrefix Start calculating WER")
          val wer = WerUtil.calculate(originalText, sttText)
          sql.executeUpdate(TranslationTask::class) {
            set(table.wer, wer)
            where(table.id eq taskId)
          }
          logger.info("$logPrefix Saved WER = $wer")

          checkCanceled(taskId)
          // 5. Call translation API for multilingual translation
          logger.info("$logPrefix Start calling translation service")
          val originalTranslations = translator.translate(originalText, task.targetLanguage)
          val sttTranslations = translator.translate(sttText, task.targetLanguage)
          logger.info("$logPrefix Finished calling translation service")
          sql.executeUpdate(TranslationTask::class) {
            set(table.originalTranslations, originalTranslations)
            set(table.sttTranslations, sttTranslations)
            where(table.id eq taskId)
          }
          logger.info("$logPrefix Saved translations")
        }

        // 6. Update task status to SUCCEEDED
        sql.executeUpdate(TranslationTask::class) {
          set(table.status, TaskStatus.SUCCEEDED)
          set(table.errorMessage, null)
          set(table.finishTime, OffsetDateTime.now())
          where(table.id eq taskId)
        }
      }
      logger.info("$logPrefix Task processed successfully, cost $duration")
    } catch (ex: TaskException.Canceled) {
      logger.error(ex.message, ex)
    } catch (ex: Exception) {
      logger.error("$logPrefix Failed to process task", ex)
      sql.executeUpdate(TranslationTask::class) {
        set(table.status, TaskStatus.FAILED)
        set(table.errorMessage, ex.message)
        where(table.id eq taskId)
      }
      if (task.retryCount < 3) {
        logger.info(
          "$logPrefix Retrying task, current retry count: ${task.retryCount}, max retries: 3"
        )
        sql.executeUpdate(TranslationTask::class) {
          set(table.status, TaskStatus.RETRY_SCHEDULED)
          set(table.retryCount, table.retryCount + 1)
          where(table.id eq taskId)
        }
        sendMqMessage(taskId)
      } else {
        logger.warn("$logPrefix Retry skipped: max retry reached")
      }
    }
  }

  @Scheduled(fixedDelay = 30_000)
  fun monitorMemoryAndControlKafkaListener() {
    if (memoryUsageRatio() > 0.85) {
      kafkaControl.pause(KafkaTopics.TRANSLATION_TASK_CREATE)
      logger.warn(
        "Memory usage is high: ${"%.2f".format(memoryUsageRatio() * 100)}%, paused Kafka listener"
      )
    } else {
      kafkaControl.resume(KafkaTopics.TRANSLATION_TASK_CREATE)
      logger.info(
        "Memory back to normal: ${"%.2f".format(memoryUsageRatio() * 100)}%, resumed Kafka listener"
      )
    }
  }

  private fun checkCanceled(taskId: Long) {
    val status =
      sql
        .createQuery(TranslationTask::class) {
          where(table.id eq taskId)
          select(table.status)
        }
        .fetchOneOrNull()
    if (status == TaskStatus.CANCELED) {
      throw TaskException.canceled(
        "[TranslationTask:$taskId] Task is canceled, terminating processing"
      )
    }
  }

  private fun sendMqMessage(taskId: Long) {
    kafka.send(KafkaTopics.TRANSLATION_TASK_CREATE, CreateTaskMessage(taskId)).whenComplete {
      result,
      ex ->
      if (ex != null) {
        logger.error(
          "[TranslationTask:$taskId] Failed to send task create message. Marking task as FAILED.",
          ex,
        )
        sql.executeUpdate(TranslationTask::class) {
          set(table.status, TaskStatus.FAILED)
          set(table.errorMessage, ex.message)
          where(table.id eq taskId)
        }
      } else {
        logger.info(
          "[TranslationTask:$taskId] Successfully sent task create message, topic=${result.recordMetadata.topic()}, partition=${result.recordMetadata.partition()}, offset=${result.recordMetadata.offset()}"
        )
      }
    }
  }

  /**
   * Get the memory usage ratio of the current node.
   *
   * Powered by oshi library.
   *
   * @return Memory usage ratio as a double value between 0.0 and 1.0
   */
  private fun memoryUsageRatio(): Double {
    val memory = SystemInfo().hardware.memory
    val used = memory.total - memory.available
    return used.toDouble() / memory.total
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(TranslationTaskConsumer::class.java)
    private val TRANSLATE_TASK =
      newFetcher(TranslationTask::class).by {
        allScalarFields()
        sourceAudio { allScalarFields() }
      }
  }
}
