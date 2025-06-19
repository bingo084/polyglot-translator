package com.bingo.polyglot.core.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * MinIO configuration properties.
 *
 * @author bingo
 */
@Configuration
@ConfigurationProperties(prefix = "minio")
open class MinioProperties {
  lateinit var publicUrl: String
  lateinit var endpoint: String
  lateinit var accessKey: String
  lateinit var secretKey: String
  lateinit var bucket: String
}
