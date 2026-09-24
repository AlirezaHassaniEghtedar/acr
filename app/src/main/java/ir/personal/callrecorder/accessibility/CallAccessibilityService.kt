package ir.personal.callrecorder.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent
import ir.personal.callrecorder.data.Prefs

/**
 * سرویس دسترسی‌پذیری — وظیفه‌ی مشخص و محدود:
 * وقتی صفحه‌ی «تماس در حال مکالمه» سیستم باز شد، بلندگو را روشن می‌کند
 * تا صدای طرف مقابل به میکروفون برسد و ضبط کامل شود. با پایان تماس،
 * وضعیت قبلی بلندگو را برمی‌گرداند.
 *
 * این سرویس:
 *  • هیچ محتوای متنی از صفحه نمی‌خواند و ذخیره نمی‌کند؛
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
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            speakerWasOnBeforeCall = audioManager.isSpeakerphoneOn
            if (!speakerWasOnBeforeCall) {
                audioManager.isSpeakerphoneOn = true
            }
        } else if (!isCallUi && inCallSession) {
            // صفحه‌ی تماس بسته شد → بازگردانی وضعیت بلندگو
            inCallSession = false
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = speakerWasOnBeforeCall
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // اگر کاربر سرویس را خاموش کرد، بلندگو را در وضعیت طبیعی بگذاریم.
        if (inCallSession) {
            inCallSession = false
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = speakerWasOnBeforeCall
        }
        return super.onUnbind(intent)
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
