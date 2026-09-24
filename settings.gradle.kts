// تنظیمات Gradle — مخزن‌های رسمی: Google Maven و Maven Central
// در شبکه‌های با محدودیت دسترسی به dl.google.com از میرور «علی‌بابا»
// (maven.aliyun.com) استفاده می‌شود که فقط همان آرتیفکت‌های رسمی گوگل را
// آینه می‌کند؛ کتابخانه‌های JetBrains مستقیم از Maven Central (repo1)
// دریافت می‌شوند. هیچ آرتیفکت غیررسمی به پروژه وارد نمی‌شود.
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google") {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx\\..*")
            }
        }
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google") {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx\\..*")
            }
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "PersonalCallRecorder"
include(":app")
