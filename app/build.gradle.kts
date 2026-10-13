plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// La versión se inyecta desde el workflow de lanzamientos (-PappVersion=1.2.3).
val appVersion: String = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "0.0.0"
val appVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersion)?.destructured
    ?.let { (major, minor, patch) -> major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt() }
    ?.coerceAtLeast(1) ?: 1

// Repositorio de GitHub donde se buscan las actualizaciones (owner/repo).
val updateRepo: String = (findProperty("updateRepo") as String?)
    ?: System.getenv("GITHUB_REPOSITORY")
    ?: "Nrpp/GTV-Life"

val keystoreFile: String? = System.getenv("ANDROID_KEYSTORE_FILE")

android {
    namespace = "io.github.nrpp.gtvlife"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.nrpp.gtvlife"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // Capturas de pantalla con Robolectric: ./gradlew testDebugUnitTest -Pscreenshots=true
        unitTests.all {
            val screenshots = (findProperty("screenshots") as String?) ?: "false"
            if (screenshots != "true") it.exclude("**/ScreenshotTest*")
            it.systemProperty("screenshots", screenshots)
            listOf("clockStyle", "accent").forEach { k -> (findProperty(k) as String?)?.let { v -> it.systemProperty(k, v) } }
            it.systemProperty("screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.path)
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/*.kotlin_module")
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("net.sf.biweekly:biweekly:0.6.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
