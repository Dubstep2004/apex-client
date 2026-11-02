import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

// Версия приложения для Windows — своя (1.x); в имени файлов релиза — версия Android (общий релиз)
val winVersion = Regex("VERSION = \"([0-9.]+)\"").find(file("src/main/kotlin/com/apexvpn/desktop/WinSys.kt").readText())!!.groupValues[1]

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.json:json:20240303")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

kotlin { jvmToolchain(17) }

compose.desktop {
    application {
        mainClass = "com.apexvpn.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
        buildTypes.release.proguard { isEnabled.set(false) }
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "APEX VPN"
            packageVersion = winVersion
            // Только латиница: jpackage на Windows читает файл аргументов в кодировке системы
            description = "APEX VPN for Windows"
            vendor = "APEX VPN"
            copyright = "APEX VPN"
            // Ядра sing-box, Xray, apexcli и базы сайтов кладутся в resources/windows при сборке (CI)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            // Вся среда Java: надёжнее, чем угадывать нужные модули (на Windows не проверить заранее)
            includeAllModules = true
            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
                menuGroup = "APEX VPN"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f1c9a52-8a7e-4b8e-9d0a-2b7d6a3c4e11"
            }
        }
    }
}

tasks.register("printVersion") { doLast { println(winVersion) } }
