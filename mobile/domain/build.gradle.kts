plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    // Kotest — test framework used in :domain (pure JVM, no Android deps needed).
    // kotest-runner-junit5 bridges Kotest specs into JUnit 5 so Gradle can discover and run them.
    // kotest-assertions-core provides the rich matcher DSL (shouldBe, shouldThrow, etc.).
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test> {
    // Kotest uses the JUnit 5 platform (Jupiter). Without this, Gradle would default
    // to JUnit 4 and no tests would be discovered.
    useJUnitPlatform()
}
