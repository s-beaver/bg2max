import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Ключ release-подписи — вне репозитория. Путь к файлу с его настройками: переменная окружения
// BG2MAX_SIGNING, иначе keystore.properties в корне проекта (он в .gitignore).
// Образец — keystore.properties.example. Без файла release-сборка получается неподписанной.
val signingProps = Properties().apply {
    val file = System.getenv("BG2MAX_SIGNING")?.let { File(it) } ?: rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = signingProps.getProperty("storeFile") != null

android {
    namespace = "com.bg2max.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bg2max.app"
        minSdk = 26
        targetSdk = 34
        // При каждом релизе: versionCode +1, versionName по SemVer, запись в CHANGELOG.md,
        // git-тег vX.Y.Z на коммите релиза.
        versionCode = 2
        versionName = "1.1.0"
    }

    // Два варианта приложения из одного кода (общий код — src/main):
    //  reply — основной вариант «BG2MAX»: отправка кнопкой «Ответить» из уведомления MAX
    //          от имени аккаунта на этом телефоне, без бота, токена и интернета (src/reply);
    //  bot   — «BG2MAX Бот», для экспериментов: отправка через MAX Bot API, нужен свой бот
    //          и его токен (src/bot).
    // Разные applicationId — оба можно поставить на один телефон.
    flavorDimensions += "transport"
    productFlavors {
        create("bot") {
            dimension = "transport"
        }
        create("reply") {
            dimension = "transport"
            applicationIdSuffix = ".reply"
            // Своя нумерация, отдельно от bot.
            versionCode = 3
            versionName = "0.1.2"
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

// Имя APK с номером версии. Основной вариант — reply: bg2max-0.1.2.apk (release),
// bg2max-0.1.2-debug.apk; bot оставлен для экспериментов: bg2max-bot-1.1.0-debug.apk.
// Release без ключа — bg2max-0.1.2-unsigned.apk: такой файл на телефон не установится.
@Suppress("DEPRECATION")
android.applicationVariants.all {
    val variant = this
    val prefix = if (variant.flavorName == "reply") "bg2max" else "bg2max-${variant.flavorName}"
    outputs.all {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            when {
                variant.buildType.name != "release" -> "$prefix-${variant.versionName}-${variant.buildType.name}.apk"
                hasReleaseKey -> "$prefix-${variant.versionName}.apk"
                else -> "$prefix-${variant.versionName}-unsigned.apk"
            }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
