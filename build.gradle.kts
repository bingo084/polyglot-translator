plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21" apply false
    id("org.springframework.boot") version "3.5.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.bingo.polyglot"
    version = "0.0.1-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    if (name != "core") {
        apply(plugin = "org.jetbrains.kotlin.plugin.spring")
        apply(plugin = "org.springframework.boot")
        apply(plugin = "io.spring.dependency-management")
    }

    java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

    kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

    tasks.withType<Test> { useJUnitPlatform() }
}
