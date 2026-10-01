# راهنمای بیلد (Build Guide)

> همه‌ی دستورها در بلوک‌های کد به انگلیسی‌اند؛ توضیح‌ها فارسی.

این سند دقیقاً همان ابزارهایی را مستند می‌کند که این مخزن با آن‌ها بیلد و
تست شده است. هیچ ابزار غیررسمی‌ای لازم نیست؛ همه‌چیز از منابع رسمی
(google/dl.google.com، services.gradle.org، adoptium.net) دانلود می‌شود.

---

## ۱) نسخه‌های دقیق ابزارها

| ابزار | نسخه | توضیح |
|---|---|---|
| JDK | **17** (Temurin 17.0.20.1+1 تست شده) | AGP 8.6 و Gradle 8.7 با JDK 21+ کار نمی‌کنند؛ JDK 25 سیستم بیلد را می‌شکند |
| Gradle | **8.7** | از طریق wrapper (`./gradlew`) — نیازی به نصب جداگانه نیست |
| Android Gradle Plugin | **8.6.1** | در `gradle/libs.versions.toml` |
| Kotlin | **2.0.21** | در `gradle/libs.versions.toml` |
| compileSdk / targetSdk | **35** (اندروید ۱۵) | در `app/build.gradle.kts` |
| minSdk | **26** (اندروید ۸٫۰) | در `app/build.gradle.kts` |
| build-tools | **34.0.0** | برای aapt/zipalign/apksigner |
| platform | **android-35** | SDK platform برای کامپایل |
| cmdline-tools | **latest (12.0)** | فقط برای نصب SDK |
| platform-tools | 37.0.1 | adb و ... |
| NDK | — | این پروژه NDK ندارد (کد نیتیو نیست) |

---

## ۲) نصب روی Linux / macOS

### گام ۱ — JDK 17 (از منبع رسمی Adoptium)

```bash
# Linux x64
curl -sSL -o /tmp/jdk17.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
sudo mkdir -p /opt/jdk17 && sudo tar -xzf /tmp/jdk17.tar.gz -C /opt/jdk17 --strip-components=1

# macOS (arm64): همین URL با platform=mac و arch=aarch64
# curl -sSL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jdk/hotspot/normal/eclipse"
```

سپس `JAVA_HOME` را تنظیم کنید:

```bash
export JAVA_HOME=/opt/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # باید 17.x نشان دهد
```

### گام ۲ — Android SDK (فقط از dl.google.com)

```bash
mkdir -p ~/android-sdk/cmdline-tools
curl -sSL -o /tmp/cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q /tmp/cmdline-tools.zip -d /tmp/ctl
mv /tmp/ctl/cmdline-tools ~/android-sdk/cmdline-tools/latest
```

پذیرش لایسنس‌ها و نصب پلتفرم/ابزارها:

```bash
export ANDROID_HOME=~/android-sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
  "platform-tools" "platforms;android-35" "build-tools;34.0.0"
```

### گام ۳ — اتصال پروژه به SDK

مسیر SDK جزئیات محلیِ هر ماشین است و commit نمی‌شود (در `.gitignore` است).
یکی از دو راه:

**راه الف (توصیه‌شده):** فایل `local.properties` در ریشه‌ی پروژه بسازید:

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

**راه ب:** متغیر محیطی:

```bash
export ANDROID_HOME=$HOME/android-sdk
```

### گام ۴ — بیلد

```bash
./gradlew assembleDebug     # خروجی: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # اگر keystore.properties باشد امضا می‌شود، وگرنه unsigned
./gradlew test              # تست‌های واحد
./gradlew lint              # بررسی‌های استاتیک
```

بار اول بیلد چند دقیقه طول می‌کشد (دانلود وابستگی‌ها از گوگل/ماون‌سنترال)؛
اگر شبکه‌تان به dl.google.com محدود است، `settings.gradle.kts` از قبل
میرور maven.aliyun.com را دارد که فقط آرتیفکت‌های رسمی گوگل را آینه می‌کند.

---

## ۳) نصب روی Windows

1. **JDK 17:** نصب‌کننده‌ی msi از <https://adoptium.net/temurin/releases/?version=17>
2. **cmdline-tools:** فایل zip از
   <https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip>
   و باز کردن در `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`
3. همان دو دستور sdkmanager بخش بالا (در PowerShell، به‌جای `yes` enter بزنید)
4. `local.properties` (به‌جاگذاری بک‌اسلش با دو بک‌اسلش):

```properties
sdk.dir=C:\\Users\\YOUR_USER\\android-sdk
```

5. بیلد: `gradlew.bat assembleDebug`

---

## ۴) چرا JDK 17 و نه JDK جدیدتر؟

- AGP 8.6 و Gradle 8.7 با JDK 25 (که در برخی توزیع‌ها پیش‌فرض است) سازگار
  نیستند و بیلد با خطای «Unsupported class file major version» شکست می‌خورد.
- اگر روی سیستمتان چند JDK دارید، Gradle به‌طور خودکار `JAVA_HOME` را
  می‌خواند؛ می‌توانید مسیر JDK 17 را در `~/.gradle/gradle.properties` هم
  ثابت کنید:

```properties
org.gradle.java.home=/opt/jdk17
```

---

## ۵) امضا و انتشار release (جریان کامل)

### یک‌بار: ساخت کلید

```bash
keytool -genkeypair -v -keystore ~/.android-keys/acr/release.jks \
  -alias callrecorder -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "رمز-قوی-تصادفی" -keypass "همان-رمز" \
  -dname "CN=Personal Call Recorder, OU=Personal, O=Personal, C=IR"
```

**مهم:** کلید را **بیرون از مخزن** نگه دارید و از آن **نسخه‌ی پشتیبان
آفلاین** بگیرید (کپی روی فلش/هارد جدا + رمز در مدیر رمزها). رمز تصادفی
بسازید؛ مثلاً: `openssl rand -base64 24`

### یک‌بار: اتصال کلید به پروژه

فایل `keystore.properties` (gitignored) در ریشه:

```properties
storeFile=/home/YOUR_USER/.android-keys/acr/release.jks
storePassword=...
keyAlias=callrecorder
keyPassword=...
```

(نمونه: `keystore.properties.example`)

### هر انتشار

```bash
./gradlew assembleRelease
```

اگر `keystore.properties` موجود نباشد بیلد **شکست نمی‌خورد**؛ فقط خروجی
`app-release-unsigned.apk` می‌شود.

### راستی‌آزمایی خروجی

```bash
BT=$ANDROID_HOME/build-tools/34.0.0
$BT/zipalign -c -v 4 app/build/outputs/apk/release/app-release.apk
$BT/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
$BT/aapt dump badging app/build/outputs/apk/release/app-release.apk | grep uses-permission
sha256sum app/build/outputs/apk/release/app-release.apk
```

انتظار: `Verifies` + `v2: true` و `v3: true`؛ مجوزها دقیقاً همان ۶ مجوز
جدول README (بدون INTERNET، بدون QUERY_ALL_PACKAGES).

### اگر کلید را گم کردید

- فایل‌های `.enc` مستقل از کلید امضای اپ‌اند (کلید آن‌ها Android Keystore
  است) — با تعویض کلید امضا از بین نمی‌روند.
- اما **به‌روزرسانی روی نسخه‌ی نصب‌شده ممکن نیست**: اندروید فقط اجازه
  می‌دهد APK با همان کلید قبلی، جای نسخه‌ی نصب‌شده نصب شود. کاربر باید
  نسخه‌ی قبلی را حذف و نسخه‌ی جدید را نصب کند. v3 signing فقط «چرخش کلید
  از این به بعد» را ممکن می‌کند؛ جایگزینی کلیدِ گم‌شده‌ی قبلی را نه.

---

## ۶) راستی‌آزمایی APK دانلودشده

قبل از نصب هر APK (از Release گیت‌هاب یا جایی دیگر):

```bash
# ۱) چک‌سام
sha256sum PersonalCallRecorder.apk
# باید با مقداری که در RELEASE_NOTES/گیت‌هاب منتشر شده یکی باشد

# ۲) صحت امضا
$BT/apksigner verify --print-certs PersonalCallRecorder.apk

# ۳) مجوزها — باید بدون INTERNET باشد
$BT/aapt dump badging PersonalCallRecorder.apk | grep -i permission
```

اگر `apksigner verify` شکست بخورد یا مجوز INTERNET دیدید، APK دستکاری
شده — نصب نکنید.
