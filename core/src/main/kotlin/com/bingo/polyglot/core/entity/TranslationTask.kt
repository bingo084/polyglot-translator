package com.bingo.polyglot.core.entity

import com.bingo.polyglot.core.constants.Language
import com.bingo.polyglot.core.constants.TaskStatus
import com.bingo.polyglot.core.storage.MinioStorage
import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.ManyToMany
import org.babyfish.jimmer.sql.Serialized
import java.time.OffsetDateTime

/**
 * Entity representing a translation task.
 *
 * A translation task processes an audio file by transcribing its content (STT), translating it into
 * one or more target languages, and producing a result file. Tracks metadata such as status, error
 * message, and completion time.
 *
 * @author bingo
 */
@Entity
interface TranslationTask : BaseEntity {
  /** Current status of the task. */
  val status: TaskStatus

  /** source audio files to be translated. */
  @ManyToMany val audios: List<Audio>

  /** Target languages for translation. */
  @Serialized val targetLanguage: List<Language>

  /** Path of the resulting translated file, if available. */
  val resultPath: String?

  /** Publicly accessible URL, derived from storage path */
  @Formula(dependencies = ["resultPath"])
  val resultUrl: String?
    get() = resultPath?.let { MinioStorage.getUrl(it) }

  /** Current progress of the task, from 0.0 to 1.0. */
  val progress: Double

  /** Error message if the task failed or encountered issues. */
  val errorMessage: String?

  /** Number of times the task has been retried. */
  val retryCount: Int

  /** Timestamp indicating when the task was completed. */
  val finishTime: OffsetDateTime?
}

data class Translation(val language: Language, val text: String)
