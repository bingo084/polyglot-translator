package com.bingo.polyglot.core.entity

import com.bingo.polyglot.core.storage.MinioStorage
import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Key

@Entity
interface Audio : BaseEntity {
  /** Original file name */
  val name: String

  /** Unique object key in storage */
  @Key val path: String

  /** Publicly accessible URL, derived from storage path */
  @Formula(dependencies = ["path"])
  val url: String
    get() = MinioStorage.getUrl(path)

  /** File size in bytes */
  val size: Long

  /** File extension, e.g., mp3, wav */
  val extension: String

  /** MIME content type, e.g., audio/mpeg */
  val contentType: String

  /** Optional manually provided original text. */
  val originalText: String?

  /** Text obtained from speech-to-text transcription (STT). */
  val sttText: String?

  /** Word Error Rate (WER) for the STT transcription. */
  val wer: Double?
}
