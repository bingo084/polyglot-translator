package com.bingo.polyglot.worker.consumer

import com.bingo.polyglot.core.constants.KafkaTopics
import com.bingo.polyglot.core.constants.TaskStatus
import com.bingo.polyglot.core.dto.CreateTaskMessage
import com.bingo.polyglot.core.entity.*
import com.bingo.polyglot.core.exception.TaskException
import com.bingo.polyglot.core.storage.MinioStorage
import com.bingo.polyglot.worker.config.Translator
import com.bingo.polyglot.worker.util.WerUtil
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.OffsetDateTime
import kotlin.time.measureTime

@Component
class TranslationTaskConsumer(
  private val sql: KSqlClient,
  private val minioStorage: MinioStorage,
  private val whisperApi: RestClient,
  private val translator: Translator,
) {

  @KafkaListener(topics = [KafkaTopics.TRANSLATION_TASK_CREATE])
  fun listenCreateTask(message: CreateTaskMessage) {
    // TODO: Check current node's memory and CPU load before processing.
    val taskId = message.taskId
    val logPrefix = "[TranslationTask:$taskId]"
    logger.info("$logPrefix Received translation task create message")

    try {
      val duration = measureTime {
        // 1. Load task details from the database
        val task =
          sql.findById(TRANSLATE_TASK, taskId)
            ?: throw TaskException.taskNotFound(message = "Task $taskId not found", taskId = taskId)
        if (task.status != TaskStatus.PENDING) {
          logger.warn("$logPrefix Skip processing task because status is ${task.status}")
          return
        }
        checkCanceled(taskId)
        sql.executeUpdate(TranslationTask::class) {
          set(table.status, TaskStatus.RUNNING)
          where(table.id eq taskId)
        }
        logger.info("$logPrefix Change translation task status to RUNNING")

        checkCanceled(taskId)
        // 2. Call Whisper service for speech recognition
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
          // 3. Perform accuracy validation (WER)
          logger.info("$logPrefix Start calculating WER")
          val wer = WerUtil.calculate(originalText, sttText)
          sql.executeUpdate(TranslationTask::class) {
            set(table.wer, wer)
            where(table.id eq taskId)
          }
          logger.info("$logPrefix Saved WER = $wer")

          checkCanceled(taskId)
          // 4. Call translation API for multilingual translation
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

        // 5. Update task status to SUCCEEDED
        sql.executeUpdate(TranslationTask::class) {
          set(table.status, TaskStatus.SUCCEEDED)
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
    }
  }

  fun checkCanceled(taskId: Long) {
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

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(TranslationTaskConsumer::class.java)
    private val TRANSLATE_TASK =
      newFetcher(TranslationTask::class).by {
        allScalarFields()
        sourceAudio { allScalarFields() }
      }
  }
}
