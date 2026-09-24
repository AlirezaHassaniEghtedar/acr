package ir.personal.callrecorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.personal.callrecorder.accessibility.CallAccessibilityService
import ir.personal.callrecorder.data.Prefs
import ir.personal.callrecorder.databinding.ActivityMainBinding
import ir.personal.callrecorder.databinding.ItemRecordingBinding
import ir.personal.callrecorder.service.MiuiHelper
import ir.personal.callrecorder.service.RecordingService
import ir.personal.callrecorder.storage.RecordingStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * صفحه‌ی اصلی:
 *  ۱) وضعیت تنظیمات را نشان می‌دهد.
 *  ۲) مجوزها را با درخواست رسمی سیستم می‌گیرد (بدون هیچ میان‌بر).
 *  ۳) آزمایش «ضبط مستقیم VOICE_CALL» را انجام می‌دهد تا مشخص شود
 *     این گوشی بدون روت اجازه‌ی ضبط مستقیم مکالمه را می‌دهد یا نه؛
 *     اگر نه، خودکار به حالت «اسپیکر + میکروفون» سوییچ می‌شود.
 *  ۴) لیست فایل‌های ضبط‌شده‌ی محلی را نشان می‌دهد و اجازه‌ی حذف می‌دهد.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var pendingProbeAfterPermission = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            updateStatus()
            if (grants.values.all { it } && pendingProbeAfterPermission) {
                pendingProbeAfterPermission = false
                runDirectProbe()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.recycler.layoutManager = LinearLayoutManager(this)

        binding.btnPermissions.setOnClickListener { requestCorePermissions() }
        binding.btnAccessibility.setOnClickListener { MiuiHelper.openAccessibilitySettings(this) }
        binding.btnOverlay.setOnClickListener { MiuiHelper.openOverlaySettings(this) }
        binding.btnProbe.setOnClickListener { runDirectProbe() }
        binding.btnOpenFolder.setOnClickListener { MiuiHelper.openRecordingsFolder(this) }
        binding.btnRefresh.setOnClickListener { refreshList() }

        binding.btnMiui.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.btn_miui))
                .setItems(arrayOf("Autostart (اجازه‌ی اجرای خودکار)", "Battery saver (بدون محدودیت باتری)", "جزئیات برنامه")) { _, which ->
                    when (which) {
                        0 -> MiuiHelper.openAutostart(this)
                        1 -> MiuiHelper.openBattery(this)
                        else -> MiuiHelper.openAppDetails(this)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnTest.setOnClickListener {
            if (!hasMic()) {
                requestCorePermissions()
            } else {
                RecordingService.start(this, test = true)
                Toast.makeText(this, R.string.toast_test_started, Toast.LENGTH_SHORT).show()
            }
        }

        binding.swCallRecording.isChecked = prefs.recordingEnabled
        binding.swCallRecording.setOnCheckedChangeListener { _, checked ->
            prefs.recordingEnabled = checked
        }

        binding.swSpeaker.isChecked = prefs.speakerEnabled
        binding.swSpeaker.setOnCheckedChangeListener { _, checked ->
            prefs.speakerEnabled = checked
        }

        binding.swEncrypt.isChecked = prefs.encryptionEnabled
        binding.swEncrypt.setOnCheckedChangeListener { _, checked ->
            prefs.encryptionEnabled = checked
            Toast.makeText(
                this,
                if (checked) R.string.toast_enc_on else R.string.toast_enc_off,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshList()
    }

    // ---------- وضعیت ----------

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasPhone(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotif(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityOn(): Boolean {
        val expected = android.content.ComponentName(this, CallAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val className = CallAccessibilityService::class.java.name
        return enabled.split(':').any { it.contains(className, ignoreCase = true) || it.contains(expected, ignoreCase = true) }
    }

    private fun updateStatus() {
        val checks = listOf(
            (hasMic() && hasPhone() && hasNotif()) to getString(R.string.btn_permissions),
            isAccessibilityOn() to getString(R.string.btn_accessibility),
            Settings.canDrawOverlays(this) to getString(R.string.btn_overlay),
        )
        val missing = checks.filter { !it.first }.map { it.second }

        binding.statusText.text = when {
            missing.isEmpty() && prefs.probeDone -> getString(R.string.status_ok)
            missing.isEmpty() -> getString(R.string.status_ready)
            else -> getString(R.string.status_partial)
        }

        binding.statusDetail.text = buildString {
            if (missing.isNotEmpty()) {
                append("اقدام لازم:\n")
                missing.forEach { append("• $it\n") }
            }
            if (prefs.probeDone) {
                append(
                    if (prefs.directModeSupported) "• حالت فعال: ضبط مستقیم (VOICE_CALL)\n"
                    else "• حالت فعال: اسپیکر + میکروفون\n"
                )
            }
            append("• رمزنگاری: " + if (prefs.encryptionEnabled) "فعال (AES-256)" else "غیرفعال")
        }
    }

    // ---------- مجوزها ----------

    private fun requestCorePermissions() {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(wanted.toTypedArray())
    }

    // ---------- آزمایش ضبط مستقیم ----------

    private fun runDirectProbe() {
        if (!hasMic() || !hasPhone() || !hasNotif()) {
            pendingProbeAfterPermission = true
            requestCorePermissions()
            return
        }
        Toast.makeText(this, R.string.btn_probe_running, Toast.LENGTH_SHORT).show()
        thread {
            val result = probeVoiceCallSupported()
            runOnUiThread {
                prefs.probeDone = true
                prefs.directModeSupported = result
                updateStatus()
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.probe_question))
                    .setPositiveButton(R.string.probe_yes) { d, _ ->
                        prefs.speakerEnabled = true
                        binding.swSpeaker.isChecked = true
                        d.dismiss()
                    }
                    .setNegativeButton(R.string.probe_no) { d, _ ->
                        prefs.speakerEnabled = false
                        binding.swSpeaker.isChecked = false
                        d.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    /**
     * فقط در حالت مکالمه‌ی واقعی معنا دارد: یک نمونه‌ی ۱٫۵ ثانیه‌ای با
     * منبع VOICE_CALL می‌گیریم. اگر سیستم اجازه ندهد، start/stop خطا
     * می‌دهد و نتیجه‌ی false برمی‌گردد (یعنی باید از MIC استفاده کنیم).
     */
    private fun probeVoiceCallSupported(): Boolean {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        val state = tm.callState
        if (state != TelephonyManager.CALL_STATE_OFFHOOK) return false

        val file = File(cacheDir, "probe.tmp")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
        else @Suppress("DEPRECATION") MediaRecorder()
        var ok = false
        try {
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            Thread.sleep(1500)
            r.stop()
            ok = file.length() > 1000
        } catch (e: Exception) {
            ok = false
        } finally {
            try { r.release() } catch (e: Exception) { }
            file.delete()
        }
        return ok
    }

    // ---------- لیست فایل‌ها ----------

    private fun refreshList() {
        thread {
            val items = RecordingStore.list(this)
            runOnUiThread {
                binding.emptyText.visibility =
                    if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                binding.recycler.adapter = RecordingsAdapter(items) { rec ->
                    MaterialAlertDialogBuilder(this)
                        .setMessage("این فایل حذف شود؟")
                        .setPositiveButton(R.string.rec_delete) { _, _ ->
                            thread {
                                RecordingStore.delete(this@MainActivity, rec.uri)
                                runOnUiThread { refreshList() }
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }
    }
}

/**
 * آداپتر لیست فایل‌ها — فقط خواندن اطلاعاتی که خود اپ ذخیره کرده است.
 */
class RecordingsAdapter(
    private val items: List<RecordingStore.Recording>,
    private val onDelete: (RecordingStore.Recording) -> Unit,
) : RecyclerView.Adapter<RecordingsAdapter.VH>() {

    class VH(val binding: ItemRecordingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.binding.root.context
        val df = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        holder.binding.recName.text = item.name
        holder.binding.recMeta.text = buildString {
            append(df.format(Date(item.dateAddedSec * 1000)))
            append(" • ")
            append(Formatter.formatShortFileSize(ctx, item.sizeBytes))
            if (item.encrypted) append(" • 🔒")
        }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }
}
