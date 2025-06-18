package com.bingo.polyglot.core.config

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.SetBucketPolicyArgs
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Inject MinIO client bean.
 *
 * @author bingo
 */
@Configuration
open class MinioConfigurer(private val minioProperties: MinioProperties) {

  @Bean
  open fun minioClient(): MinioClient {
    val client =
      MinioClient.builder()
        .endpoint(minioProperties.endpoint)
        .credentials(minioProperties.accessKey, minioProperties.secretKey)
        .build()
    val bucketExists =
      client.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.bucket).build())
    if (!bucketExists) {
      client.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.bucket).build())
      client.setBucketPolicy(
        SetBucketPolicyArgs.builder()
          .bucket(minioProperties.bucket)
          .config(
            """{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": [
                    "*"
                ]
            },
            "Action": [
                "s3:GetBucketLocation",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::polyglot"
            ]
        },
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": [
                    "*"
                ]
            },
            "Action": [
                "s3:GetObject"
            ],
            "Resource": [
                "arn:aws:s3:::polyglot/*"
            ]
        }
    ]
}"""
          )
          .build()
      )
    }
    return client
  }
}
