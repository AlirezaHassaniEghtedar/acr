package ir.personal.callrecorder.service

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import ir.personal.callrecorder.R

/**
 * ابزار بازکردن صفحات تنظیمات MIUI (و اندروید خام) برای اپ ما.
 *
 * نکته‌ی شفافیت: این کلاس هیچ کاری را دور از چشم کاربر انجام نمی‌دهد؛
 * فقط صفحه‌ی تنظیمات مربوطه را برای کاربر باز می‌کند تا خودش تصمیم بگیرد.
 * همه‌ی مسیرها با try/catch همراهند و اگر در نسخه‌ای از MIUI کار نکرد،
 * به صفحه‌ی تنظیمات اندروید برمی‌گردیم.
 */
object MiuiHelper {

    fun isXiaomiDevice(): Boolean =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    /** صفحه‌ی Autostart در اپ Security شیائومی. */
    fun openAutostart(context: Context) {
        val miuiIntent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
        if (!tryStart(context, miuiIntent)) openAppDetails(context)
    }

    /** صفحه‌ی باتری: نسخه‌های مختلف MIUI اکتیویتی‌های متفاوتی دارند. */
    fun openBattery(context: Context) {
        val candidates = listOf(
            // صفحه‌ی جزئیات باتری اپ در powerkeeper (MIUI 12 به بعد)
            Intent().setComponent(
                ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
            ).putExtra("package_name", context.packageName)
                .putExtra("package_label", getAppLabel(context)),
            // لیست Battery saver اندروید
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
        )
        for (candidate in candidates) {
            if (tryStart(context, candidate)) return
        }
        openAppDetails(context)
    }

    /** تنظیمات دسترسی‌پذیری سیستم. */
    fun openAccessibilitySettings(context: Context) {
        tryStart(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** مجوز نمایش روی برنامه‌ها (SYSTEM_ALERT_WINDOW). */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        if (!tryStart(context, intent)) {
            tryStart(context, Intent(Settings.ACTION_APPLICATION_SETTINGS))
        }
    }

    /** صفحه‌ی جزئیات اپ (مجوزها، باتری، Autostart در برخی نسخه‌ها). */
    fun openAppDetails(context: Context) {
        tryStart(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    fun openRecordingsFolder(context: Context) {
        // فایل‌منیجر سیستم را روی پوشه‌ی Music باز می‌کنیم؛ در MIUI
        // معمولاً فایل‌منیجر خود شیائومی پاسخ می‌دهد.
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary:Music/CallRecordings"),
                "vnd.android.document/directory")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!tryStart(context, intent)) {
            Toast.makeText(context, R.string.storage_summary, Toast.LENGTH_LONG).show()
        }
    }

    private fun getAppLabel(context: Context): String =
        context.applicationInfo.loadLabel(context.packageManager).toString()

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
