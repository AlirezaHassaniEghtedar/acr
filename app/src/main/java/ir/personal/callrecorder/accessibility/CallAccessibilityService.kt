package ir.personal.callrecorder.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import ir.personal.callrecorder.data.Prefs

/**
 * سرویس دسترسی‌پذیری — وظیفه‌ی مشخص و محدود:
 * وقتی صفحه‌ی «تماس در حال مکالمه» سیستم باز شد، بلندگو را روشن می‌کند
 * تا صدای طرف مقابل به میکروفون برسد و ضبط کامل شود. با پایان تماس،
 * وضعیت قبلی بلندگو را برمی‌گرداند.
 *
 * این سرویس:
 *  • هیچ محتوایی از پنجره نمی‌خواند و ذخیره نمی‌کند (canRetrieveWindowContent=false)؛
 *  • فقط به نام پکیج صفحه‌ی فعال نگاه می‌کند (com.android.incallui و مشابه)؛
 *  • هیچ داده‌ای را به جایی نمی‌فرستد.
 *
 * چرا دسترسی‌پذیری؟ چون تنها API رسمیِ بدون روت است که هم‌زمان:
 *  ۱) اپ را در وضعیت «در حال استفاده» نگه می‌دارد (برای مجوز میکروفون
 *     در پس‌زمینه روی اندروید ۱۴+)، و
 *  ۲) به‌صورت قابل‌اعتماد شروع/پایان صفحه‌ی تماس را خبر می‌دهد.
 */
class CallAccessibilityService : AccessibilityService() {

    private var speakerWasOnBeforeCall = false
    private var inCallSession = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = Prefs(this)

        val pkg = event.packageName?.toString() ?: return
        val isCallUi = pkg in CALL_UI_PACKAGES

        if (isCallUi && prefs.speakerEnabled && !inCallSession) {
            inCallSession = true
            speakerWasOnBeforeCall = isSpeakerphoneOn()
            if (!speakerWasOnBeforeCall) setSpeakerphoneOn(true)
        } else if (!isCallUi && inCallSession) {
            // صفحه‌ی تماس بسته شد → بازگردانی وضعیت بلندگو
            inCallSession = false
            setSpeakerphoneOn(speakerWasOnBeforeCall)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // اگر کاربر سرویس را خاموش کرد، بلندگو را در وضعیت طبیعی بگذاریم.
        if (inCallSession) {
            inCallSession = false
            setSpeakerphoneOn(speakerWasOnBeforeCall)
        }
        return super.onUnbind(intent)
    }

    /**
     * روشن/خاموش‌کردن بلندگو بدون هشدار deprecation:
     *  • اندروید ۱۲ به بعد: مسیر رسمی جدید یعنی setCommunicationDevice
     *    با دستگاه SPEAKERPHONE است؛
     *  • اندروید ۸ تا ۱۱: همان متد قدیمی isSpeakerphoneOn که در آن
     *     نسخه‌ها هنوز معتبر است (فقط برای این شاخه از @Suppress
     *     استفاده می‌کنیم چون جایگزین قدیمی‌تر از minSdk وجود ندارد).
     */
    private fun setSpeakerphoneOn(on: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } ?: return
                audioManager.setCommunicationDevice(speaker)
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
        }
    }

    private fun isSpeakerphoneOn(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn
        }
    }

    companion object {
        // پکیج‌های صفحه‌ی تماس: AOSP و MIUI و Google Dialer
        private val CALL_UI_PACKAGES = setOf(
            "com.android.incallui",        // AOSP / MIUI
            "com.miui.incallui",           // برخی نسخه‌های MIUI
            "com.google.android.dialer",   // Google Dialer
        )
    }
}
