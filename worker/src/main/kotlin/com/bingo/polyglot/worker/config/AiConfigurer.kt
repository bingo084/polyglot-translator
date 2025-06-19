package com.bingo.polyglot.worker.config

import com.bingo.polyglot.core.constants.Language
import com.bingo.polyglot.core.entity.Translation
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**  */
@Configuration
class AiConfigurer {
  @Value("\${google.gemini.api-key}") private lateinit var apiKey: String
  @Value("\${google.gemini.model-name}") private lateinit var modelName: String

  @Bean
  fun googleAiGeminiChatModel(): GoogleAiGeminiChatModel =
    GoogleAiGeminiChatModel.builder()
      .apiKey(apiKey)
      .modelName(modelName)
      .responseFormat(ResponseFormat.JSON)
      .build()

  @Bean
  fun translator(chatModel: ChatModel): Translator =
    AiServices.create(Translator::class.java, chatModel)
}

interface Translator {
  @UserMessage(
    """
You are a multilingual translator. Translate the given text into the following languages: {{languages}}.

Text:
"{{text}}"
"""
  )
  fun translate(
    @V("text") text: String,
    @V("languages") languages: List<Language>,
  ): List<Translation>
}
