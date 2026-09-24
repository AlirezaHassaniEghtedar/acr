package ir.personal.callrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.personal.callrecorder.MainActivity
import ir.personal.callrecorder.R
import ir.personal.callrecorder.data.Prefs
import ir.personal.callrecorder.storage.RecordingStore
import java.io.File

/**
 * سرویس ضبط — Foreground Service با نوع «microphone».
 *
 * قوانین حاکم (همه از مستندات رسمی اندروید):
 *  • از اندروید ۱۴، ایجاد سرویس foreground با نوع microphone از حالت
 *    پس‌زمینه فقط وقتی مجاز است که اپ در «حالت مجاز» باشد؛ در این پروژه
 *    مجوز SYSTEM_ALERT_WINDOW (نمایش روی برنامه‌ها) یکی از موارد معاف
 *    از محدودیت شروع از پس‌زمینه است و ما دقیقاً از همین مسیر رسمی
 *    استفاده می‌کنیم.
 *  • اگر سیستم دسترسی میکروفون را موقتاً مسدود کند (isClientSilenced)،
 *    فایل بی‌صدا دور ریخته می‌شود تا فایل خالی ذخیره نکنیم.
 *
 * هیچ داده‌ای به اینترنت ارسال نمی‌شود؛ فقط فایل محلی ساخته می‌شود.
 */
class RecordingService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var isTestRecording = false
    private var directMode = false
    private var recording = false

    private val testStopRunnable = Runnable { stopRecording(discard = false) }


    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording(discard = false)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                isTestRecording = intent.getBooleanExtra(EXTRA_TEST, false)
                directMode = prefs.directModeSupported
                startAsForeground()
                startRecording()
            }
        }
        // اگر سیستم سرویس را کشت، دوباره با آخرین دستور بالا نمی‌آوریم تا
        // هیچ‌گاه بدون کنترل کاربر در پس‌زمینه فعال نشود (رفتار ضد-malware).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopRecording(discard = true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- foreground ----------

    private fun startAsForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else 0
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopSelf = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle(getString(R.string.notif_recording_title))
            .setContentText(getString(R.string.notif_recording_text))
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notif_stop_action), stopSelf)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notif_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // ---------- recording ----------

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startRecording() {
        if (recording) return
        if (!hasMicPermission()) { stopSelf(); return }

        val dir = File(cacheDir, "pending").apply { mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.tmp")
        tempFile = file

        try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()

            // منبع صدا: اگر آزمایش نشان داد VOICE_CALL روی این دستگاه کار
            // می‌کند از آن استفاده می‌کنیم؛ در غیر این صورت میکروفون معمولی.
            r.setAudioSource(if (directMode) MediaRecorder.AudioSource.VOICE_CALL else MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setAudioChannels(1)
            r.setMaxDuration(MAX_DURATION_MS)
            r.setOutputFile(file.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecording(discard = false)
                }
            }
            r.prepare()
            r.start()
            recorder = r
            recording = true

            if (isTestRecording) handler.postDelayed(testStopRunnable, TEST_DURATION_MS)

            registerMicMonitoring()
        } catch (e: Exception) {
            // میکروفون در دسترس نیست (مثلاً اپ دیگری آن را گرفته) —
            // فایل ناقص را پاک می‌کنیم و بی‌سروصدا خارج می‌شویم.
            recorder = null
            recording = false
            file.delete()
            tempFile = null
            stopSelf()
        }
    }

    private fun registerMicMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            audioManager.registerAudioRecordingCallback(
                object : AudioManager.AudioRecordingCallback() {
                    override fun onRecordingConfigChanged(configs: MutableList<android.media.AudioRecordingConfiguration>) {
                        if (!recording) return
                        val silenced = configs.any { it.isClientSilenced }
                        if (silenced) {
                            // سیستم میکروفون را برای ما قطع کرده است؛ فایل بی‌ارزش است.
                            stopRecording(discard = true)
                        }
                    }
                },
                handler
            )
        }
    }

    private fun stopRecording(discard: Boolean) {
        if (!recording) { stopSelf(); return }
        recording = false
        handler.removeCallbacks(testStopRunnable)

        val file = tempFile
        tempFile = null
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // اگر stop ناموفق بود فایل قابل پخش نیست.
        }
        try { recorder?.release() } catch (e: Exception) {}
        recorder = null

        if (file != null && file.exists() && file.length() > 0 && !discard) {
            val encrypted = prefs.encryptionEnabled
            val name = RecordingStore.newDisplayName(isTestRecording, encrypted)
            RecordingStore.save(this, file, name, encrypted)
        }
        file?.delete()

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 42
        private const val EXTRA_TEST = "test"
        const val ACTION_START = "ir.personal.callrecorder.action.START"
        const val ACTION_STOP = "ir.personal.callrecorder.action.STOP"
        const val MAX_DURATION_MS = 60 * 60 * 1000        // حداکثر ۱ ساعت
        const val TEST_DURATION_MS = 10_000L

        fun start(context: Context, test: Boolean) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TEST, test)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
