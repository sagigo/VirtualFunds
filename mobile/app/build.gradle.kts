plugins {
    alias(libs.plugins.android.application)
    // kotlin-compose is the Compose compiler plugin (separate from kotlin-android since Kotlin 2.0).
    // AGP 9.x bundles Kotlin Android support — adding kotlin-android explicitly causes a conflict.
    alias(libs.plugins.kotlin.compose)
    // hilt wires up Dagger/Hilt for the whole app module.
    alias(libs.plugins.hilt)
    // ksp is the code generator that Hilt uses to produce its DI factories at compile time.
    alias(libs.plugins.ksp)
    // kotlin-serialization compiles @Serializable types — Navigation Compose 2.8+ uses these
    // for type-safe routes (see `ui/Routes.kt`).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.virtualfunds"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.virtualfunds"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Route local unit tests through JUnit 5 so Kotest specs are discovered.
        unitTests.all { it.useJUnitPlatform() }
    }
}

// kotlin { } is a Project-level extension (not android-scoped) — it must live outside android { }.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // lifecycle-viewmodel-compose: enables collectAsStateWithLifecycle() in Composables, which
    // pauses StateFlow collection while the UI is in the background (saving battery + avoiding
    // wasted work). Preferred over collectAsState() in any production Compose code.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Hilt — runtime library + KSP annotation processor.
    // ksp(...) runs the processor at compile time; it never ends up in the APK itself.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // hiltViewModel() helper that lets Compose screens obtain @HiltViewModels.
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Navigation Compose — type-safe routes (mobile/CLAUDE.md §Navigation).
    implementation(libs.androidx.navigation.compose)

    // kotlinx-serialization-json is the runtime that backs Navigation Compose's type-safe
    // routes (the compiler plugin generates KSerializers for @Serializable route objects).
    implementation(libs.kotlinx.serialization.json)

    // Coroutines for Android (adds the Main dispatcher tied to the Android main thread).
    implementation(libs.kotlinx.coroutines.android)

    // Tests
    testImplementation(libs.junit)
    // Kotest — same framework as :domain.
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    // MockK — mocking library for Kotlin; needed for ViewModel tests (mocks repositories,
    // SyncCoordinator). Handles final classes and suspend functions natively.
    testImplementation(libs.mockk)
    // coroutines-test — provides runTest, UnconfinedTestDispatcher, Dispatchers.setMain.
    // Essential for testing ViewModels that use viewModelScope.
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
