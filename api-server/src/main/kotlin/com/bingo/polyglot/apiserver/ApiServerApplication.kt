package com.bingo.polyglot.apiserver

import org.babyfish.jimmer.client.EnableImplicitApi
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@EnableImplicitApi @SpringBootApplication class ApiServerApplication

fun main(args: Array<String>) {
  runApplication<ApiServerApplication>(*args)
}
