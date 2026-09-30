import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// اگر فایل keystore.properties در ریشه پروژه موجود باشد، خروجی release
// به‌صورت خودکار با آن کلید امضا می‌شود. در غیر این صورت release
// بدون امضا ساخته می‌شود و بیلد هرگز به‌خاطر نبود کلید شکست نمی‌خورد.
// این فایل و خود keystore هرگز نباید commit شوند (.gitignore پوشش می‌دهد).
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
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    lint {
        // گزارش‌ها به‌صورت متن هم تولید شود تا در CI/ترمینال قابل خواندن باشد.
        textReport = true
        abortOnError = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// فقط کتابخانه‌های رسمی AndroidX/Material — بدون هیچ کتابخانه‌ی ثالث.
// نسخه‌ها در gradle/libs.versions.toml نگه‌داری می‌شوند.
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit)
}
