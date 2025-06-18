plugins { id("com.google.devtools.ksp") version "2.1.21+" }

val jimmerVersion = "0.9.93"
val idgenerator = "1.0.6"
val minioVersion = "8.5.17"

dependencies {
  implementation("org.springframework.boot:spring-boot:3.5.0")
  implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  // Jimmer
  implementation("org.babyfish.jimmer:jimmer-sql-kotlin:${jimmerVersion}")
  ksp("org.babyfish.jimmer:jimmer-ksp:$jimmerVersion")
  // IdGenerator
  implementation("com.github.yitter:yitter-idgenerator:${idgenerator}")
  // MinIO
  implementation("io.minio:minio:${minioVersion}")
}

tasks.withType<Test> { useJUnitPlatform() }
