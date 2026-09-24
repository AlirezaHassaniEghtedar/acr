import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// اگر فایل keystore.properties در ریشه پروژه موجود باشد، خروجی release
// به‌صورت خودکار با آن کلید امضا می‌شود. در غیر این صورت release
// بدون امضا ساخته می‌شود و می‌توانید با apksigner امضا کنید (README).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "ir.personal.callrecorder"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.personal.callrecorder"
        minSdk = 26        // اندروید ۸٫۰
        targetSdk = 35     // اندروید ۱۵
        versionCode = 1
        versionName = "1.0.0"
    }

    if (keystorePropsFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // شفافیت عمدی: بدون Minify/ProGuard تا بایت‌کد خروجی دقیقاً
            // معادل همین سورس‌کد خوانا باشد؛ هیچ مبهم‌سازی (obfuscation) انجام نمی‌شود.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// فقط کتابخانه‌های رسمی AndroidX — بدون هیچ کتابخانه‌ی ثالث
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
}
