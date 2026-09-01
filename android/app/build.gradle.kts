import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localMelangePersonalKey = rootProject.file(".melange.local.properties")
    .takeIf { it.isFile }
    ?.inputStream()
    ?.use { input ->
        Properties().apply { load(input) }.getProperty("MELANGE_PERSONAL_KEY").orEmpty()
    }
    .orEmpty()

val melangePersonalKey = providers.environmentVariable("MELANGE_PERSONAL_KEY")
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?: localMelangePersonalKey

fun String.asJavaStringLiteral(): String = buildString {
    append('"')
    this@asJavaStringLiteral.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> {
                if (character.code < 0x20) {
                    append("\\${character.code.toString(8).padStart(3, '0')}")
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

android {
    namespace = "ai.zetic.realtimetranslate"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.zetic.realtimetranslate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MELANGE_PERSONAL_KEY", melangePersonalKey.asJavaStringLiteral())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation("com.zeticai.mlange:mlange:1.10.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
