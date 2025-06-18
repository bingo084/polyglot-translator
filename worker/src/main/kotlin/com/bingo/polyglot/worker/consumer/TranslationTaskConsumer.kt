package com.bingo.polyglot.worker.consumer

import com.bingo.polyglot.core.constants.KafkaTopics
import com.bingo.polyglot.core.constants.TaskStatus
import com.bingo.polyglot.core.dto.CreateTaskMessage
import com.bingo.polyglot.core.entity.*
import com.bingo.polyglot.core.exception.TaskException
import com.bingo.polyglot.core.storage.MinioStorage
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
) {

  @KafkaListener(topics = [KafkaTopics.TRANSLATION_TASK_CREATE])
  fun listenCreateTask(message: CreateTaskMessage) {
    // TODO: Check current node's memory and CPU load before processing.
    val taskId = message.taskId
    logger.info("Received translation task create message, taskId=$taskId")

    try {
      val duration = measureTime {
        // 1. Load task details from the database
        val task =
          sql.findById(TRANSLATE_TASK, taskId)
            ?: throw TaskException.taskNotFound(
              message = "Task with id $taskId not found",
              taskId = taskId,
            )
        if (task.status != TaskStatus.PENDING) {
          logger.warn("Skip processing task $taskId because status is ${task.status}")
          return
        }

        // 2. Call Whisper service for speech recognition
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
        sql.executeUpdate(TranslateTask::class) {
          set(table.sttText, sttText)
          where(table.id eq taskId)
        }

        // 3. TODO: Perform accuracy validation

        // 4. TODO: Call translation API for multilingual translation

        // 5. Update task status to SUCCEEDED
        sql.executeUpdate(TranslateTask::class) {
          set(table.status, TaskStatus.SUCCEEDED)
          set(table.finishTime, OffsetDateTime.now())
          where(table.id eq taskId)
        }
      }
      logger.info("Task $taskId processed successfully, cost $duration")
    } catch (ex: Exception) {
      logger.error("Failed to process task $taskId", ex)
      sql.executeUpdate(TranslateTask::class) {
        set(table.status, TaskStatus.FAILED)
        set(table.errorMessage, ex.message)
        where(table.id eq taskId)
      }
    }
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(TranslationTaskConsumer::class.java)
    private val TRANSLATE_TASK =
      newFetcher(TranslateTask::class).by {
        allScalarFields()
        sourceAudio { allScalarFields() }
      }
  }
}
