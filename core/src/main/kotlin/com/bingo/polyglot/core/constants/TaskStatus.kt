package com.bingo.polyglot.core.constants

/**
 * Represents the lifecycle status of a task.
 *
 * Used to track the current execution state of a task, from creation to completion.
 *
 * @author bingo
 */
enum class TaskStatus {
  /** The task is created but has not started yet. */
  PENDING,

  /** The task is currently in progress. */
  RUNNING,

  /** The task was canceled before completion. */
  CANCELED,

  /** The task has finished execution but ended with an error. */
  FAILED,

  /** The task is waiting for retry after a failure. */
  RETRY_SCHEDULED,

  /** The task has successfully completed. */
  SUCCEEDED,
}
