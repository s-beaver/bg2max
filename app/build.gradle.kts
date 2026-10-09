plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

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
    //  bot   — отправка через MAX Bot API, нужен свой бот и его токен (src/bot);
    //  reply — отправка кнопкой «Ответить» из уведомления MAX от имени аккаунта
    //          на этом телефоне, без бота, токена и доступа в интернет (src/reply).
    // Разные applicationId — оба можно поставить на один телефон.
    flavorDimensions += "transport"
    productFlavors {
        create("bot") {
            dimension = "transport"
        }
        create("reply") {
            dimension = "transport"
            applicationIdSuffix = ".reply"
            // Своя нумерация: вариант экспериментальный, выпускается отдельно от bot.
            versionCode = 2
            versionName = "0.1.1"
        }
    }

    buildTypes {
        release {
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

// APK с вариантом и номером версии в имени: bg2max-bot-1.1.0-debug.apk, bg2max-reply-0.1.0-debug.apk
@Suppress("DEPRECATION")
android.applicationVariants.all {
    val variant = this
    outputs.all {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            "bg2max-${variant.flavorName}-${variant.versionName}-${variant.buildType.name}.apk"
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
