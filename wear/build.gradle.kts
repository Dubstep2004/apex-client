// 11. APEX CLIENT для часов Wear OS: кнопка включения, состояние, сервер и пинг.
// Тот же applicationId и тот же ключ подписи, что у приложения для телефона —
// иначе часы и телефон не увидят друг друга (Wearable Data Layer).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Версия часов = версия телефона (берём из app/build.gradle.kts)
val appGradle = rootProject.file("app/build.gradle.kts").readText()
val appVersionCode = Regex("versionCode = (\\d+)").find(appGradle)!!.groupValues[1].toInt()
val appVersionName = Regex("versionName = \"([^\"]+)\"").find(appGradle)!!.groupValues[1]

android {
    namespace = "com.apexvpn.client.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.apexvpn.client"
        minSdk = 30
        targetSdk = 34
        // На 1 меньше, чем у телефона: часы и телефон обязаны иметь один applicationId (иначе не увидят
        // друг друга), и если по ошибке открыть apex-watch на телефоне, Android откажется «понижать»
        // APEX CLIENT до приложения для часов — вместо того чтобы заменить им приложение на телефоне.
        versionCode = appVersionCode - 1
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("APEX_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                val storePass = System.getenv("APEX_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "apexvpn"
                storePassword = storePass
                keyAlias = System.getenv("APEX_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "apex"
                keyPassword = System.getenv("APEX_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: storePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    // Открыть ссылку (GitHub, Telegram) на сопряжённом телефоне
    implementation("androidx.wear:wear-remote-interactions:1.1.0")
    // 409. Плитка
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.guava:listenablefuture:1.0")
    // 410. Значок на циферблат
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
