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

  /** The task was cancelled before completion. */
  CANCELLED,

  /** The task has finished execution but ended with an error. */
  FAILED,

  /** The task has successfully completed. */
  SUCCEEDED,
}
