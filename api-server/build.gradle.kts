plugins {}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  // core
  implementation(project(":core"))
}

tasks.withType<Test> { useJUnitPlatform() }
