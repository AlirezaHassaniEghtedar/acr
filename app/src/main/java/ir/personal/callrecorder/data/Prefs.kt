package ir.personal.callrecorder.data

import android.content.Context

/**
 * لایه‌ی تنظیمات — همه‌چیز محلی (SharedPreferences) است.
 * هیچ داده‌ای از این کلاس به بیرون از دستگاه نمی‌رود.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** ضبط خودکار تماس‌ها فعال باشد؟ */
    var recordingEnabled: Boolean
        get() = sp.getBoolean(KEY_RECORDING, true)
        set(value) = sp.edit().putBoolean(KEY_RECORDING, value).apply()

    /** هنگام ضبط، بلندگو خودکار روشن شود؟ (برای گرفتن صدای طرف مقابل) */
    var speakerEnabled: Boolean
        get() = sp.getBoolean(KEY_SPEAKER, true)
        set(value) = sp.edit().putBoolean(KEY_SPEAKER, value).apply()

    /** فایل‌ها با AES-256 رمزنگاری شوند؟ */
    var encryptionEnabled: Boolean
        get() = sp.getBoolean(KEY_ENCRYPT, false)
        set(value) = sp.edit().putBoolean(KEY_ENCRYPT, value).apply()

    /**
     * نتیجه‌ی آزمایش کاربر:
     * true  = ضبط مستقیم VOICE_CALL روی این دستگاه جواب می‌دهد
     * false = حالت جایگزین (اسپیکر + میکروفون)
     */
    var directModeSupported: Boolean
        get() = sp.getBoolean(KEY_DIRECT_OK, false)
        set(value) = sp.edit().putBoolean(KEY_DIRECT_OK, value).apply()

    /** آیا آزمایش یک‌بار انجام شده است؟ */
    var probeDone: Boolean
        get() = sp.getBoolean(KEY_PROBE_DONE, false)
        set(value) = sp.edit().putBoolean(KEY_PROBE_DONE, value).apply()

    companion object {
        private const val KEY_RECORDING = "recording_enabled"
        private const val KEY_SPEAKER = "speaker_enabled"
        private const val KEY_ENCRYPT = "encryption_enabled"
        private const val KEY_DIRECT_OK = "direct_mode_supported"
        private const val KEY_PROBE_DONE = "probe_done"
    }
}
