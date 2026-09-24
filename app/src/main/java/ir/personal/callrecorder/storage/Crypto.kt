package ir.personal.callrecorder.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * رمزنگاری اختیاری فایل‌های ضبط‌شده با AES-256-GCM.
 *
 * کلید در Android Keystore ساخته می‌شود و هرگز از دستگاه خارج نمی‌شود
 * (حتی خود اپ هم به بایت‌های خام کلید دسترسی ندارد؛ فقط می‌تواند
 * عملیات رمز/رمزگشایی را به سخت‌افزار بسپارد).
 *
 * قالب فایل رمزنگاری‌شده:
 *   [1 بایت: طول IV (12)] [12 بایت IV] [AES-GCM ciphertext + tag]
 */
object Crypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "call_recorder_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** طول IV استاندارد GCM */
    private const val IV_LENGTH = 12

    fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** ورودی را روی خروجی رمزنگاری‌شده می‌نویسد (هر دو بسته می‌شوند). */
    fun encryptToStream(input: InputStream, output: OutputStream) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        output.write(IV_LENGTH)
        output.write(iv)
        CipherOutputStream(output, cipher).use { encrypted ->
            input.use { it.copyTo(encrypted, 8192) }
        }
    }

    /** فایلِ ذخیره‌شده در MediaStore را به یک فایل موقتِ رمزگشایی‌شده تبدیل می‌کند. */
    fun decryptToTempFile(outputDir: java.io.File, inputStream: InputStream, tempName: String): java.io.File {
        val temp = java.io.File(outputDir, tempName)
        inputStream.use { raw ->
            val ivLength = raw.read()
            require(ivLength in 1..32) { "bad iv length" }
            val iv = ByteArray(ivLength)
            var read = 0
            while (read < ivLength) {
                val n = raw.read(iv, read, ivLength - read)
                require(n > 0) { "truncated iv" }
                read += n
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            CipherInputStream(raw, cipher).use { decrypted ->
                temp.outputStream().use { tempOut ->
                    decrypted.copyTo(tempOut, 8192)
                }
            }
        }
        return temp
    }

    /**
     * تشخیص ابتدایی رمزنگاری‌شده بودن: بایت اول در فایل m4a خام همیشه 0 است
     * (باکس ftyp)، در قالب ما 12 است. برای نمایش لیست کافی است؛ نام فایل
     * (.enc) مرجع اصلی است.
     */
    fun looksEncrypted(firstByte: Int): Boolean = firstByte == IV_LENGTH
}
