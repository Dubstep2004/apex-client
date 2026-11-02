import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ─── Настройки приложения ──────────────────────────────────────────────
// Своего сервера нет: обновления — с GitHub Releases, объявления — из Remnawave (заголовок announce),
// отчёты о сбоях хранятся на телефоне и отправляются только через «Написать в поддержку».

// Версия ядра Xray, которое кладётся в APK (core/xray-version)
val xrayVersion: String = rootProject.file("core/xray-version").takeIf { it.exists() }?.readText()?.trim()?.removePrefix("v") ?: "?"

// Откуда обновлять базы сайтов (*.srs). Пусто — официальные базы sing-box через jsDelivr
val rulesBaseUrl: String = (project.findProperty("rulesBaseUrl") as String?) ?: System.getenv("RULES_BASE_URL") ?: ""

// Единственный разрешённый хост подписки
val subscriptionHost = "sub.knownhosts.ru"

// Подпись релиза: keystore.properties в корне проекта или переменные окружения (CI)
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.apexvpn.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.apexvpn.client"
        minSdk = 24
        targetSdk = 35
        versionCode = 73
        versionName = "3.10.0"

        buildConfigField("String", "SUBSCRIPTION_HOST", "\"$subscriptionHost\"")
        buildConfigField("String", "RULES_BASE_URL", "\"$rulesBaseUrl\"")
        // Откуда приложение узнаёт о новых версиях: GitHub Releases этого репозитория
        buildConfigField("String", "GITHUB_REPO", "\"${System.getenv("GITHUB_REPOSITORY")?.takeIf { it.contains('/') } ?: "Dubstep2004/apex-vpn"}\"")
        buildConfigField("String", "XRAY_VERSION", "\"$xrayVersion\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "APEX_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                // Если отдельные секреты не заданы — пароль и имя ключа по умолчанию (scripts/make-keystore.sh)
                val storePass = signingValue("storePassword", "APEX_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "apexvpn"
                storePassword = storePass
                keyAlias = signingValue("keyAlias", "APEX_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "apex"
                // Ключ с сервера (PKCS12): пароль ключа совпадает с паролем хранилища
                keyPassword = signingValue("keyPassword", "APEX_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: storePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            // Распаковывать .so на устройство: ядро Xray запускается как файл из nativeLibraryDir
            useLegacyPackaging = true
            // Xray — готовый исполняемый файл, его не трогаем
            keepDebugSymbols += "**/libxray.so"
        }
    }
}

dependencies {
    // Ядро sing-box + парсер подписки (собирается скриптом core/build-core.sh)
    implementation(files("libs/libbox.aar"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // Сканер QR-кодов
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // 262. QR-код переноса на новый телефон
    implementation("com.google.zxing:core:3.4.1")
    // 11. Связь с часами Wear OS
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    // Google Play Services тянет старый Fragment 1.0 — поднимаем (иначе ломаются запросы разрешений)
    implementation("androidx.fragment:fragment-ktx:1.8.5")
}
