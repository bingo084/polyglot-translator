package com.bingo.polyglot.apiserver.service

import com.bingo.polyglot.core.constants.TaskStatus
import com.bingo.polyglot.core.dto.PageReq
import com.bingo.polyglot.core.dto.fetchPage
import com.bingo.polyglot.core.dto.orderBy
import com.bingo.polyglot.core.entity.TranslateTask
import com.bingo.polyglot.core.entity.by
import com.bingo.polyglot.core.entity.dto.TranslateTaskInput
import com.bingo.polyglot.core.entity.dto.TranslateTaskSpec
import com.bingo.polyglot.core.entity.id
import com.bingo.polyglot.core.entity.status
import com.bingo.polyglot.core.exception.TaskException
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.web.bind.annotation.*

/**
 * Translate task
 *
 * @author bingo
 */
@RestController
@RequestMapping("/translate-tasks")
class TranslateTaskService(private val sql: KSqlClient) {
  /** Find task */
  @GetMapping("{id}")
  fun findById(@PathVariable("id") id: Long): TranslateTask? = sql.findById(TRANSLATE_TASK, id)

  /** Find task */
  @GetMapping
  fun findById(spec: TranslateTaskSpec, page: PageReq): Page<TranslateTask> =
    sql
      .createQuery(TranslateTask::class) {
        where(spec)
        orderBy(page)
        select(table.fetch(TRANSLATE_TASK))
      }
      .fetchPage(page)

  /** Create task */
  @PostMapping
  fun create(input: TranslateTaskInput): Long =
    sql
      .save(input.toEntity { status = TaskStatus.PENDING }) { setMode(SaveMode.INSERT_ONLY) }
      .modifiedEntity
      .id

  /** Cancel task */
  @DeleteMapping("{id}")
  @Throws(TaskException.TaskNotFound::class, TaskException.CancelFailed::class)
  fun cancel(@PathVariable("id") id: Long): Boolean {
    val status =
      sql
        .createQuery(TranslateTask::class) {
          where(table.id eq id)
          select(table.status)
        }
        .fetchOneOrNull()
        ?: throw TaskException.taskNotFound(message = "Task with id $id not found", taskId = id)
    if (status !in listOf(TaskStatus.PENDING, TaskStatus.RUNNING)) {
      val message = "Only PENDING or RUNNING task can be cancelled, current status is $status"
      throw TaskException.cancelFailed(message = message, reason = message)
    }
    return sql.executeUpdate(TranslateTask::class) {
      set(table.status, TaskStatus.CANCELLED)
      where(table.id eq id)
    } > 0
  }

  companion object {
    private val TRANSLATE_TASK = newFetcher(TranslateTask::class).by { allScalarFields() }
  }
}
