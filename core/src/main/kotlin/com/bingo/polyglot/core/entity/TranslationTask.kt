package com.bingo.polyglot.core.entity

import com.bingo.polyglot.core.constants.Language
import com.bingo.polyglot.core.constants.TaskStatus
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.ManyToOne
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

  /** ID of the source audio file to be translated. */
  @ManyToOne val sourceAudio: Audio

  /** Optional manually provided original text. */
  val originalText: String?

  /** Text obtained from speech-to-text transcription (STT). */
  val sttText: String?

  /** Word Error Rate (WER) for the STT transcription. */
  val wer: Double?

  /** Target languages for translation. */
  @Serialized val targetLanguage: List<Language>

  /** ID of the resulting translated file, if available. */
  val resultFile: Long?

  /** Error message if the task failed or encountered issues. */
  val errorMessage: String?

  /** Timestamp indicating when the task was completed. */
  val finishTime: OffsetDateTime?
}
