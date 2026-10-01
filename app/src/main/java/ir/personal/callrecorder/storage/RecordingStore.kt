package ir.personal.callrecorder.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ذخیره‌سازی کاملاً محلی — بدون هیچ مجوز ذخیره‌سازی.
 *
 * اندروید ۱۰ به بعد (API 29+): از طریق MediaStore در پوشه‌ی عمومی
 *   <حافظه داخلی>/Music/CallRecordings
 * ذخیره می‌شود؛ این پوشه در همه‌ی فایل‌منیجرها دیده می‌شود.
 *
 * اندروید ۸ تا ۹ (API 26-28): MediaStore مدرن (RELATIVE_PATH/IS_PENDING)
 * وجود ندارد و نوشتن در پوشه‌ی عمومی بدون مجوز WRITE_EXTERNAL_STORAGE
 * ممکن نیست — مجوزی که عمداً در این اپ وجود ندارد. بنابراین فایل‌ها در
 * پوشه‌ی اختصاصی اپ ذخیره می‌شوند:
 *   Android/data/ir.personal.callrecorder/files/Music/CallRecordings
 * رفتار پخش/حذف از داخل اپ یکسان است.
 */
object RecordingStore {

    const val RELATIVE_PATH = "Music/CallRecordings"

    data class Recording(
        val uri: Uri,
        val name: String,
        val sizeBytes: Long,
        val dateAddedSec: Long,
        val encrypted: Boolean,
    )

    fun newDisplayName(test: Boolean, encrypted: Boolean): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (test) "Test" else "Call"
        val ext = if (encrypted) "enc" else "m4a"
        return "${prefix}Recording_$stamp.$ext"
    }

    /**
     * فایل موقت را ذخیره می‌کند؛ اگر encrypted=true باشد هنگام نوشتن
     * رمزنگاری می‌شود. در صورت هر خطا (مثلاً پر بودن فضا) null برمی‌گردد
     * و فایل موقت را دست‌نخورده می‌گذارد تا فراخواننده پاکش کند.
     */
    fun save(context: Context, tempFile: File, displayName: String, encrypted: Boolean): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, tempFile, displayName, encrypted)
        } else {
            saveToAppDir(context, tempFile, displayName, encrypted)
        }
    }

    /** اندروید ۱۰ به بعد: ذخیره در Music/CallRecordings از طریق MediaStore. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(
        context: Context,
        tempFile: File,
        displayName: String,
        encrypted: Boolean,
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, if (encrypted) "application/octet-stream" else "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (encrypted) {
                    tempFile.inputStream().use { input -> Crypto.encryptToStream(input, out) }
                } else {
                    tempFile.inputStream().use { input -> input.copyTo(out, 8192) }
                }
            } ?: return null
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
    }

    /** اندروید ۸ تا ۹: ذخیره در پوشه‌ی اختصاصی اپ (بدون نیاز به مجوز). */
    private fun saveToAppDir(
        context: Context,
        tempFile: File,
        displayName: String,
        encrypted: Boolean,
    ): Uri? {
        val dir = legacyRecordingsDir(context) ?: return null
        val target = File(dir, displayName)
        return try {
            FileOutputStream(target).use { out ->
                if (encrypted) {
                    tempFile.inputStream().use { input -> Crypto.encryptToStream(input, out) }
                } else {
                    tempFile.inputStream().use { input -> input.copyTo(out, 8192) }
                }
            }
            Uri.fromFile(target)
        } catch (e: Exception) {
            runCatching { target.delete() }
            null
        }
    }

    fun legacyRecordingsDir(context: Context): File? {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return null
        return File(base, "CallRecordings").apply { mkdirs() }
    }

    /** فهرست فایل‌های ضبط‌شده‌ی این اپ (جدیدترین اول). */
    fun list(context: Context): List<Recording> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listViaMediaStore(context)
        } else {
            listFromAppDir(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listViaMediaStore(context: Context): List<Recording> {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%$RELATIVE_PATH%")
        val sort = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val result = mutableListOf<Recording>()
        resolver.query(collection, projection, selection, args, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val encrypted = name.endsWith(".enc")
                result.add(
                    Recording(
                        uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                        name = name,
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAddedSec = cursor.getLong(dateCol),
                        encrypted = encrypted,
                    )
                )
            }
        }
        return result
    }

    private fun listFromAppDir(context: Context): List<Recording> {
        val dir = legacyRecordingsDir(context) ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                Recording(
                    uri = Uri.fromFile(f),
                    name = f.name,
                    sizeBytes = f.length(),
                    dateAddedSec = f.lastModified() / 1000,
                    encrypted = f.name.endsWith(".enc"),
                )
            } ?: emptyList()
    }

    fun delete(context: Context, uri: Uri): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme != "file") {
                context.contentResolver.delete(uri, null, null) > 0
            } else {
                File(uri.path ?: return false).delete()
            }
        } catch (e: Exception) {
            // اگر سیستم تأیید کاربر خواست، کاربر از پنجره‌ی سیستم اقدام می‌کند.
            false
        }
    }
}
