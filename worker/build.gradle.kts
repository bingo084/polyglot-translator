plugins { id("com.google.devtools.ksp") version "2.1.21+" }

val jimmerVersion = "0.9.93"
val langChain4jVersion = "1.1.0"
val oshiVersion = "6.8.2"

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
  // LangChain4j
  implementation(platform("dev.langchain4j:langchain4j-bom:$langChain4jVersion"))
  implementation("dev.langchain4j:langchain4j")
  implementation("dev.langchain4j:langchain4j-google-ai-gemini")
  // OSHI - Native Operating System and Hardware Information
  implementation("com.github.oshi:oshi-core:$oshiVersion")
}

tasks.withType<Test> { useJUnitPlatform() }
