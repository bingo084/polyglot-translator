package com.bingo.polyglot.apiserver.interceptor

import com.bingo.polyglot.apiserver.entity.BaseEntity
import com.bingo.polyglot.apiserver.entity.BaseEntityDraft
import org.babyfish.jimmer.kt.isLoaded
import org.babyfish.jimmer.sql.DraftInterceptor
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class BaseEntityDraftInterceptor() : DraftInterceptor<BaseEntity, BaseEntityDraft> {

  override fun beforeSave(draft: BaseEntityDraft, original: BaseEntity?) {
    val now = OffsetDateTime.now()

    if (original === null && !isLoaded(draft, BaseEntity::createTime)) {
      draft.createTime = now
    }

    if (!isLoaded(draft, BaseEntity::updateTime)) {
      draft.updateTime = now
    }
  }
}
