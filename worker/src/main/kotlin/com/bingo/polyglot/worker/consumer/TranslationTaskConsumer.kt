package com.bingo.polyglot.worker.consumer

import com.bingo.polyglot.core.constants.KafkaTopics
import com.bingo.polyglot.core.constants.TaskStatus
import com.bingo.polyglot.core.dto.CreateTaskMessage
import com.bingo.polyglot.core.entity.TranslateTask
import com.bingo.polyglot.core.entity.id
import com.bingo.polyglot.core.entity.status
import com.bingo.polyglot.core.exception.TaskException
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TranslationTaskConsumer(private val sql: KSqlClient) {

  private val logger = LoggerFactory.getLogger(TranslationTaskConsumer::class.java)

  @KafkaListener(topics = [KafkaTopics.TRANSLATION_TASK_CREATE])
  fun listenCreateTask(message: CreateTaskMessage) {
    // TODO: Check current node's memory and CPU load before processing.
    val taskId = message.taskId
    logger.info("Received translation task create message, taskId=$taskId")

    try {
      // 1. Load task details from the database
      val task =
        sql.findById(TranslateTask::class, taskId)
          ?: throw TaskException.taskNotFound(
            message = "Task with id $taskId not found",
            taskId = taskId,
          )
      if (task.status != TaskStatus.PENDING) {
        logger.warn("Skip processing task $taskId because status is ${task.status}")
        return
      }

      // 2. TODO: Call Whisper service for speech recognition

      // 3. TODO: Perform accuracy validation

      // 4. TODO: Call translation API for multilingual translation

      // 5. Update task status to SUCCEEDED
      updateTaskStatus(taskId, TaskStatus.SUCCEEDED)
      logger.info("Task $taskId processed successfully")
    } catch (ex: Exception) {
      logger.error("Failed to process task $taskId", ex)
      updateTaskStatus(taskId, TaskStatus.FAILED)
    }
  }

  fun updateTaskStatus(taskId: Long, status: TaskStatus) {
    sql.executeUpdate(TranslateTask::class) {
      set(table.status, status)
      where(table.id eq taskId)
    }
  }
}
