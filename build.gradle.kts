// فایل build ریشه پروژه
// پلاگین‌های رسمی گوگل و جت‌برینز — نسخه‌ها در gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
