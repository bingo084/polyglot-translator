package com.bingo.polyglot.core.config

import com.github.yitter.contract.IdGeneratorOptions
import com.github.yitter.idgen.YitIdHelper
import jakarta.annotation.PostConstruct
import org.babyfish.jimmer.sql.meta.UserIdGenerator
import org.springframework.stereotype.Component

@Component
class IdGenerator : UserIdGenerator<Long> {
  @PostConstruct
  fun init() {
    val workerId =
      System.getenv("WORKER_ID")?.toShortOrNull()
        ?: throw IllegalStateException("WORKER_ID must be set!")
    val options = IdGeneratorOptions(workerId)
    YitIdHelper.setIdGenerator(options)
  }

  override fun generate(entityType: Class<*>): Long = YitIdHelper.nextId()
}
