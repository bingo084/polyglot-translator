package com.bingo.polyglot.apiserver.service

import com.bingo.polyglot.apiserver.constants.SourceType
import com.bingo.polyglot.core.constants.KafkaTopics
import com.bingo.polyglot.core.constants.Language
import com.bingo.polyglot.core.constants.TaskStatus
import com.bingo.polyglot.core.dto.CreateTaskMessage
import com.bingo.polyglot.core.dto.PageReq
import com.bingo.polyglot.core.dto.fetchPage
import com.bingo.polyglot.core.dto.orderBy
import com.bingo.polyglot.core.entity.*
import com.bingo.polyglot.core.entity.dto.TranslationTaskInput
import com.bingo.polyglot.core.entity.dto.TranslationTaskSpec
import com.bingo.polyglot.core.exception.TaskException
import com.bingo.polyglot.core.storage.MinioStorage
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*
import java.util.zip.GZIPInputStream

/**
 * Translation task
 *
 * @author bingo
 */
@RestController
@RequestMapping("/translation-tasks")
class TranslationTaskService(
  private val sql: KSqlClient,
  private val kafka: KafkaTemplate<String, CreateTaskMessage>,
  private val storage: MinioStorage,
  private val objectMapper: ObjectMapper,
) {
  /** Find task */
  @GetMapping("{id}")
  fun findById(@PathVariable("id") id: Long): TranslationTask? = sql.findById(TRANSLATE_TASK, id)

  /**
   * Get task result
   *
   * @param id Task ID
   * @param language Language of the result
   * @param audioId Audio ID
   * @param sourceType Source type of the task
   */
  @GetMapping("{id}/result")
  fun findResult(
    @PathVariable("id") id: Long,
    @RequestParam language: Language,
    @RequestParam audioId: Long,
    @RequestParam sourceType: SourceType,
  ): String? {
    val resultPath =
      sql
        .createQuery(TranslationTask::class) {
          where(table.id eq id)
          select(table.resultPath)
        }
        .fetchOneOrNull()
        ?: throw TaskException.taskNotFound(
          message = "TaskResult with id $id not found",
          taskId = id,
        )
    val uncompressed =
      GZIPInputStream(storage.getObject(resultPath).buffered()).use {
        it.readBytes().toString(Charsets.UTF_8)
      }
    val typeRef = object : TypeReference<Map<String, Map<String, Map<String, String>>>>() {}
    val result: Map<String, Map<String, Map<String, String>>> =
      objectMapper.readValue(uncompressed, typeRef)
    return result[language.name]?.get(audioId.toString())?.get(sourceType.name)
  }

  /** Find task */
  @GetMapping
  fun findById(spec: TranslationTaskSpec, page: PageReq): Page<TranslationTask> =
    sql
      .createQuery(TranslationTask::class) {
        where(spec)
        orderBy(page)
        select(table.fetch(TRANSLATE_TASK))
      }
      .fetchPage(page)

  /** Create task */
  @PostMapping
  fun create(input: TranslationTaskInput): Long {
    val taskId =
      sql
        .save(input.toEntity { status = TaskStatus.PENDING }) { setMode(SaveMode.INSERT_ONLY) }
        .modifiedEntity
        .id
    sendMqMessage(taskId)
    return taskId
  }

  /** Send message to Kafka to notify worker to create task */
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

  /** Cancel task */
  @DeleteMapping("{id}")
  @Throws(TaskException.TaskNotFound::class, TaskException.CancelFailed::class)
  fun cancel(@PathVariable("id") id: Long): Boolean =
    sql.transaction {
      val status =
        sql
          .createQuery(TranslationTask::class) {
            where(table.id eq id)
            select(table.status)
          }
          .fetchOneOrNull()
          ?: throw TaskException.taskNotFound(message = "Task with id $id not found", taskId = id)
      if (status !in listOf(TaskStatus.PENDING, TaskStatus.RUNNING)) {
        val message = "Only PENDING or RUNNING task can be canceled, current status is $status"
        throw TaskException.cancelFailed(message = message, reason = message)
      }
      sql.executeUpdate(TranslationTask::class) {
        set(table.status, TaskStatus.CANCELED)
        where(table.id eq id)
      } > 0
    }

  /** Upload audio file */
  @PostMapping("upload-audio")
  fun uploadAudio(
    @RequestParam file: MultipartFile,
    @RequestParam(required = false) originalText: String?,
  ): Long {
    val extension = file.originalFilename?.substringAfterLast(".") ?: ""
    val path = "audio/${UUID.randomUUID()}.$extension"
    val contentType = file.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
    if (!contentType.startsWith("audio/")) {
      val message = "Invalid content type: $contentType. Expected audio/*"
      throw TaskException.invalidContentType(message = message, reason = message)
    }
    val audioId =
      sql
        .save(
          Audio {
            name = file.originalFilename ?: path.substringAfter("/")
            this.path = path
            size = file.size
            this.extension = extension
            this.contentType = contentType
            this.originalText = originalText
          }
        ) {
          setMode(SaveMode.INSERT_ONLY)
        }
        .modifiedEntity
        .id
    storage.putObject(file.inputStream, path, file.size, contentType)
    return audioId
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(TranslationTaskService::class.java)
    private val TRANSLATE_TASK =
      newFetcher(TranslationTask::class).by {
        allScalarFields()
        resultUrl()
        audios {
          allScalarFields()
          url()
        }
      }
  }
}
