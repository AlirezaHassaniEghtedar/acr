package ir.personal.callrecorder.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ذخیره‌سازی کاملاً محلی از طریق MediaStore (حتی بدون هیچ مجوز ذخیره‌سازی).
 *
 * محل نهایی: <حافظه داخلی>/Music/CallRecordings
 * این پوشه در همه‌ی فایل‌منیجرها دیده می‌شود و کاربر می‌تواند فایل‌ها را
 * مستقیم کپی/جابه‌جا/حذف کند.
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
     * فایل موقت را در MediaStore ذخیره می‌کند؛ اگر encrypted=true باشد
     * هنگام نوشتن رمزنگاری می‌شود.
     */
    fun save(context: Context, tempFile: File, displayName: String, encrypted: Boolean): Uri? {
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

    /** فهرست فایل‌های ضبط‌شده‌ی این اپ (جدیدترین اول). */
    fun list(context: Context): List<Recording> {
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

    fun delete(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            // اگر سیستم تأیید کاربر خواست، کاربر از پنجره‌ی سیستم اقدام می‌کند.
            false
        }
    }
}
