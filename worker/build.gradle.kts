plugins { id("com.google.devtools.ksp") version "2.1.21+" }

val jimmerVersion = "0.9.93"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  // core
  implementation(project(":core"))
  // Jimmer
  implementation("org.babyfish.jimmer:jimmer-spring-boot-starter:$jimmerVersion")
  ksp("org.babyfish.jimmer:jimmer-ksp:$jimmerVersion")
  // Database
  runtimeOnly("org.postgresql:postgresql")
  // Kafka
  implementation("org.springframework.kafka:spring-kafka")
}

tasks.withType<Test> { useJUnitPlatform() }
