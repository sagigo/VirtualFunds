import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    // hilt + ksp wire up compile-time DI code generation.
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.virtualfunds.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 31

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Read Supabase credentials from local.properties (gitignored).
        // BuildConfig fields bake these values into the compiled library so the
        // SupabaseModule can access them without exposing them in source control.
        val localProperties = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) localProperties.load(localFile.inputStream())
        buildConfigField("String", "SUPABASE_URL",
            "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        // Required to generate the BuildConfig class with our custom fields above.
        buildConfig = true
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
    implementation(libs.androidx.core.ktx)

    // Hilt — runtime + KSP processor.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Supabase Kotlin SDK.
    // The BOM aligns all supabase-kt artifact versions so we never declare individual versions.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)  // PostgREST client (table reads + RPC calls)
    implementation(libs.supabase.auth)       // Auth client (sign in/up/out, session management)

    // Ktor OkHttp engine — the HTTP layer supabase-kt uses on Android.
    implementation(libs.ktor.client.okhttp)

    // Coroutines core (platform-agnostic; the Android dispatcher is in :app).
    implementation(libs.kotlinx.coroutines.core)

    // EncryptedSharedPreferences for persisting the Supabase session token securely.
    // Backed by Android Keystore. Note: this library is in maintenance mode; acceptable for v1.
    implementation(libs.androidx.security.crypto)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
