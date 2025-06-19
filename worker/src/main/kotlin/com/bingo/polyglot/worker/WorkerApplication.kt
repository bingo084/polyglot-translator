package com.bingo.polyglot.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication @EnableScheduling class WorkerApplication

fun main(args: Array<String>) {
  runApplication<WorkerApplication>(*args)
}
