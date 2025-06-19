package com.bingo.polyglot.worker.consumer

import com.bingo.polyglot.core.constants.KafkaTopics
import com.bingo.polyglot.core.constants.Language
import com.bingo.polyglot.core.constants.TaskStatus
import com.bingo.polyglot.core.dto.CreateTaskMessage
import com.bingo.polyglot.core.entity.*
import com.bingo.polyglot.core.exception.TaskException
import com.bingo.polyglot.core.storage.MinioStorage
import com.bingo.polyglot.worker.config.Translator
import com.bingo.polyglot.worker.util.KafkaControl
import com.bingo.polyglot.worker.util.WerUtil
import com.fasterxml.jackson.databind.ObjectMapper
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
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.util.zip.GZIPOutputStream
import kotlin.time.measureTime

@Component
class TranslationTaskConsumer(
  private val sql: KSqlClient,
  private val minioStorage: MinioStorage,
  private val whisperApi: RestClient,
  private val translator: Translator,
  private val kafka: KafkaTemplate<String, CreateTaskMessage>,
  private val kafkaControl: KafkaControl,
  private val objectMapper: ObjectMapper,
) {

  @KafkaListener(
    id = KafkaTopics.TRANSLATION_TASK_CREATE,
    topics = [KafkaTopics.TRANSLATION_TASK_CREATE],
  )
  fun listenCreateTask(message: CreateTaskMessage) {
    val taskId = message.taskId
    val logPrefix = "[TranslationTask:$taskId]"
    // Check current node's memory load before processing.
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

    // Load task details from the database
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
        updateTaskToRunning(taskId)
        logger.info("$logPrefix Change translation task status to RUNNING")
        val result = mutableMapOf<String, MutableMap<String, MutableMap<String, String>>>()
        for ((i, audio) in task.audios.withIndex()) {
          checkCanceled(taskId)
          val logPrefix =
            "[TranslationTask:$taskId, Audio(${i + 1}/${task.audios.size}):${audio.id}]"

          // Call Whisper service for speech recognition
          logger.info("$logPrefix Start calling Whisper service")
          val sttText = generateSttByWhisperAndSave(audio)
          logger.info("$logPrefix Finished calling Whisper service and save STT text")

          val originalText = audio.originalText
          if (originalText != null && sttText != null) {
            checkCanceled(taskId)
            // Perform accuracy validation (WER)
            logger.info("$logPrefix Start calculating WER")
            val wer = calculateWerAndSave(originalText, sttText, audio.id)
            logger.info("$logPrefix Saved WER = $wer")

            checkCanceled(taskId)
            // Call translation API for multilingual translation
            logger.info("$logPrefix Start calling translation service")
            translateAndSetResult(originalText, task.targetLanguage, sttText, result, audio.id)
            logger.info("$logPrefix Finished calling translation service and set all result")
          }
          // Update processing progress
          updateProgress(i + 1, task.audios.size, taskId)
        }

        // Pack translation result into a single file and upload
        logger.info("$logPrefix Packing and uploading translation results")
        val path = packResultAndUpload(result, taskId)
        logger.info("$logPrefix Uploaded translation results to MinIO $path")

        // Update task status to SUCCEEDED
        updateTaskToSucceeded(taskId)
      }
      logger.info("$logPrefix Task processed successfully, cost $duration")
    } catch (ex: TaskException.Canceled) {
      logger.error(ex.message, ex)
    } catch (ex: Exception) {
      logger.error("$logPrefix Failed to process task", ex)
      updateTaskToFailed(ex, taskId)
      retryTask(task, logPrefix)
    }
  }

  /**
   * Update the translation task status to RUNNING in the database.
   *
   * @param taskId The ID of the translation task to update.
   */
  private fun updateTaskToRunning(taskId: Long) {
    sql.executeUpdate(TranslationTask::class) {
      set(table.status, TaskStatus.RUNNING)
      where(table.id eq taskId)
    }
  }

  /**
   * Update the translation task progress in the database.
   *
   * @param finished The number of finished audios.
   * @param total The total number of audios in the task.
   * @param taskId The ID of the translation task to update.
   */
  private fun updateProgress(finished: Int, total: Int, taskId: Long) {
    sql.executeUpdate(TranslationTask::class) {
      set(table.progress, finished.toDouble() / total)
      where(table.id eq taskId)
    }
  }

  /**
   * Update the translation task status to FAILED and set the error message.
   *
   * @param ex The exception that caused the failure.
   * @param taskId The ID of the translation task to update.
   */
  private fun updateTaskToFailed(ex: Exception, taskId: Long) {
    sql.executeUpdate(TranslationTask::class) {
      set(table.status, TaskStatus.FAILED)
      set(table.errorMessage, ex.message)
      where(table.id eq taskId)
    }
  }

  /**
   * Update the translation task status to SUCCEEDED and clear any error messages.
   *
   * @param taskId The ID of the translation task to update.
   */
  private fun updateTaskToSucceeded(taskId: Long) {
    sql.executeUpdate(TranslationTask::class) {
      set(table.status, TaskStatus.SUCCEEDED)
      set(table.errorMessage, null)
      set(table.finishTime, OffsetDateTime.now())
      where(table.id eq taskId)
    }
  }

  /**
   * Retry the translation task if it has not reached the maximum retry limit.
   *
   * @param task The translation task to retry.
   * @param logPrefix The log prefix for logging messages.
   */
  private fun retryTask(task: TranslationTask, logPrefix: String) {
    if (task.retryCount < 3) {
      logger.info(
        "$logPrefix Retrying task, current retry count: ${task.retryCount}, max retries: 3"
      )
      sql.executeUpdate(TranslationTask::class) {
        set(table.status, TaskStatus.RETRY_SCHEDULED)
        set(table.retryCount, table.retryCount + 1)
        where(table.id eq task.id)
      }
      sendMqMessage(task.id)
    } else {
      logger.warn("$logPrefix Retry skipped: max retry reached")
    }
  }

  /**
   * Pack the translation results into a GZIP compressed file and upload it to MinIO.
   *
   * @param result The translation results to be packed.
   * @param taskId The ID of the translation task for which results are being packed.
   * @return The path where the packed result is stored in MinIO.
   */
  private fun packResultAndUpload(
    result: MutableMap<String, MutableMap<String, MutableMap<String, String>>>,
    taskId: Long,
  ): String {
    val byteArray =
      ByteArrayOutputStream().use { bos ->
        GZIPOutputStream(bos).use { gzipOut ->
          gzipOut.write(objectMapper.writeValueAsBytes(result))
        }
        bos.toByteArray()
      }
    val path = "translation/${taskId}.pack"
    minioStorage.putObject(
      byteArray.inputStream(),
      path,
      byteArray.size.toLong(),
      "application/gzip",
    )
    sql.executeUpdate(TranslationTask::class) {
      set(table.resultPath, path)
      where(table.id eq taskId)
    }
    return path
  }

  /**
   * Translate the original text and STT text into target languages, and set the results in the
   * provided result map.
   *
   * @param originalText The original text from the audio.
   * @param targetLanguage The list of target languages to translate into.
   * @param sttText The STT text generated from the audio.
   * @param result The mutable map to store translation results.
   * @param audioId The ID of the audio entity for which translations are being processed.
   */
  private fun translateAndSetResult(
    originalText: String,
    targetLanguage: List<Language>,
    sttText: String,
    result: MutableMap<String, MutableMap<String, MutableMap<String, String>>>,
    audioId: Long,
  ) {
    val originalTranslations = translator.translate(originalText, targetLanguage)
    val sttTranslations = translator.translate(sttText, targetLanguage)
    // Set all results
    for (t in originalTranslations) {
      val textIdMap = result.getOrPut(t.language.name) { mutableMapOf() }
      val typeMap = textIdMap.getOrPut(audioId.toString()) { mutableMapOf() }
      typeMap["TEXT"] = t.text
    }

    for (t in sttTranslations) {
      val textIdMap = result.getOrPut(t.language.name) { mutableMapOf() }
      val typeMap = textIdMap.getOrPut(audioId.toString()) { mutableMapOf() }
      typeMap["AUDIO"] = t.text
    }
  }

  /**
   * Calculate Word Error Rate (WER) between original text and STT text, and save the result to the
   * database.
   *
   * @param originalText The original text from the audio.
   * @param sttText The STT text generated from the audio.
   * @param audioId The ID of the audio entity to update with the WER result.
   * @return The calculated WER value.
   */
  private fun calculateWerAndSave(originalText: String, sttText: String, audioId: Long): Double {
    val wer = WerUtil.calculate(originalText, sttText)
    sql.executeUpdate(Audio::class) {
      set(table.wer, wer)
      where(table.id eq audioId)
    }
    return wer
  }

  /**
   * Call Whisper service to generate STT text from audio file and save it to the database.
   *
   * @param audio The audio entity containing the file path and metadata.
   * @return The generated STT text, or null if the audio don't have text.
   */
  private fun generateSttByWhisperAndSave(audio: Audio): String? {
    val sttText =
      minioStorage.getObject(audio.path).use { response ->
        val builder = MultipartBodyBuilder()
        builder
          .part("audio_file", InputStreamResource(response))
          .filename(audio.name)
          .contentType(MediaType.valueOf(audio.contentType))
        whisperApi
          .post()
          .uri("/asr")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(builder.build())
          .retrieve()
          .body(String::class.java)
      }
    sql.executeUpdate(Audio::class) {
      set(table.sttText, sttText)
      where(table.id eq audio.id)
    }
    return sttText
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
        audios { allScalarFields() }
      }
  }
}
