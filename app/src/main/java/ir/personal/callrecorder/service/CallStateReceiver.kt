package ir.personal.callrecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import ir.personal.callrecorder.data.Prefs

/**
 * گیرنده‌ی تغییر وضعیت تلفن.
 *
 * شروع ضبط: وقتی تماس «برقرار» شد (OFFHOOK) — هم ورودی هم خروجی.
 * پایان ضبط: وقتی تماس قطع شد (IDLE).
 *
 * نکته‌ی شفافیت: در اندروید ۱۰ به بعد، MIUI ممکن است بر اساس تنظیم
 * Autostart اپ، این برادکست را تحویل ندهد. راه‌حل رسمی همان فعال‌کردن
 * Autostart در Security app است (به README و دکمه‌ی تنظیمات MIUI رجوع کنید).
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val prefs = Prefs(context)
        if (!prefs.recordingEnabled) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> RecordingService.start(context, test = false)
            TelephonyManager.EXTRA_STATE_IDLE -> RecordingService.stop(context)
        }
    }
}
