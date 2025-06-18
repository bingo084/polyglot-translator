package com.bingo.polyglot.core.exception

import org.babyfish.jimmer.error.ErrorFamily
import org.babyfish.jimmer.error.ErrorField

/**
 * Error codes related to task operations.
 *
 * This enum can generate exceptions with specific error codes
 *
 * @author bingo
 */
@ErrorFamily
enum class TaskErrorCode {
  @ErrorField(name = "taskId", type = Long::class) TASK_NOT_FOUND,
  @ErrorField(name = "reason", type = String::class) CANCEL_FAILED,
  @ErrorField(name = "reason", type = String::class) INVALID_CONTENT_TYPE,
}
