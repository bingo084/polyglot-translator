package com.bingo.polyglot.core.util

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class SpringContextHolder : ApplicationContextAware {
  override fun setApplicationContext(applicationContext: ApplicationContext) {
    Companion.applicationContext = applicationContext
  }

  companion object {
    private var applicationContext: ApplicationContext? = null

    fun <T> getBean(beanClass: Class<T>): T {
      return applicationContext!!.getBean(beanClass)
    }

    fun <T> getBean(beanName: String, beanClass: Class<T>): T {
      return applicationContext!!.getBean(beanName, beanClass)
    }
  }
}
