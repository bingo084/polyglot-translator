package com.bingo.polyglot.worker.util

import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.stereotype.Component

@Component
class KafkaControl(private val registry: KafkaListenerEndpointRegistry) {
  fun pause(listenerId: String) {
    registry.listenerContainers.filter { it.listenerId == listenerId }.forEach { it.pause() }
  }

  fun resume(listenerId: String) {
    registry.listenerContainers.filter { it.listenerId == listenerId }.forEach { it.resume() }
  }
}
