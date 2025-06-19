package com.bingo.polyglot.core.storage

import com.bingo.polyglot.core.config.MinioProperties
import com.bingo.polyglot.core.util.SpringContextHolder
import io.minio.GetObjectArgs
import io.minio.GetObjectResponse
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.springframework.stereotype.Component
import java.io.InputStream

/**
 * MinIO File Storage
 *
 * @author bingo
 */
@Component
class MinioStorage(
  private val minioClient: MinioClient,
  private val minioProperties: MinioProperties,
) {

  /** Upload object. */
  fun putObject(inputStream: InputStream, objectName: String, size: Long, contentType: String) {
    minioClient.putObject(
      PutObjectArgs.builder()
        .bucket(minioProperties.bucket)
        .`object`(objectName)
        .stream(inputStream, size, -1)
        .contentType(contentType)
        .build()
    )
  }

  /** Retrieve object stream. */
  fun getObject(objectName: String): GetObjectResponse =
    minioClient.getObject(
      GetObjectArgs.builder().bucket(minioProperties.bucket).`object`(objectName).build()
    )

  companion object {
    fun getUrl(objectName: String): String {
      val minioProperties = SpringContextHolder.getBean(MinioProperties::class.java)
      return minioProperties.publicUrl + "/" + minioProperties.bucket + "/" + objectName
    }
  }
}
