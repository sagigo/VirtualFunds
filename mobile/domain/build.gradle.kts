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
    // kotlinx-coroutines-core — used by AuthRepository to expose its state as a StateFlow.
    // Declared as `api` (not `implementation`) so :data and :app see the StateFlow / Flow types
    // transitively without each having to redeclare the dependency. This is the standard Gradle
    // pattern for library modules that put third-party types in their *public* signatures.
    api(libs.kotlinx.coroutines.core)

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
