package com.bingo.polyglot.core.entity

import com.bingo.polyglot.core.config.IdGenerator
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.MappedSuperclass
import java.time.OffsetDateTime

/**
 * BaseEntity
 *
 * @author bingo
 */
@MappedSuperclass
interface BaseEntity {
  /** ID */
  @Id @GeneratedValue(generatorType = IdGenerator::class) val id: Long

  /** CreateTime */
  val createTime: OffsetDateTime

  /** UpdateTime */
  val updateTime: OffsetDateTime
}
