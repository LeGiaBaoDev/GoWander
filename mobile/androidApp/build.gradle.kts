import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/** Reads one key from the repo's .env (not committed); empty when the file or the key is absent. Never logged. */
fun envValue(key: String): String {
    val file = rootProject.file("../.env")
    if (!file.exists()) return ""
    val props = Properties().apply { file.inputStream().use { load(it) } }
    return props.getProperty(key).orEmpty().trim()
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    runtimeOnly(libs.maplibre.compose.runtime.opengl.android)
}

android {
    namespace = "com.gowander.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.gowander.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1-poc"
        // AUTH-2: the Web client ID is the token audience the server checks; the Android client is matched by
        // package + SHA-1 in Google Cloud (ADR-040 §3.5). No secret is involved.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${envValue("GOOGLE_CLIENT_ID")}\"")
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_11 }
}
