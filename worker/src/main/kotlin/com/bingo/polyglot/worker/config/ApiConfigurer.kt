package com.bingo.polyglot.worker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * Injects restClient beans.
 *
 * @author bingo
 */
@Configuration
class ApiConfigurer {
  @Value("\${whisper.base-url}") private lateinit var baseUrl: String

  /**
   * Creates a RestClient for the Whisper API.
   *
   * @return An instance of [WhisperApi].
   */
  @Bean fun whisperApi(): RestClient = RestClient.builder().baseUrl(baseUrl).build()
}
