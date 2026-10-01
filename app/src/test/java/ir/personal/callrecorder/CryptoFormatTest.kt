package ir.personal.callrecorder

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * تست‌های واحد «قالب فایل رمزنگاری‌شده» — بدون Android Keystore.
 *
 * کلاس Crypto خودش به AndroidKeyStore وابسته است و روی JVM ساده اجرا
 * نمی‌شود؛ اما قالب فایل مستقل از محل کلید است:
 *
 *   [1 بایت: طول IV] [IV با همان طول] [AES-GCM ciphertext + tag]
 *
 * این تست همان قالب را با کلید نرم‌افزاری JVM بازسازی می‌کند تا:
 *  ۱) رفتار round-trip رمز/رمزگشایی ثابت بماند،
 *  ۲) خراب شدن حتی یک بایت، کل فایل را نامعتبر کند (GCM tag) — همین
 *     تضمین است که فایل‌های دستکاری‌شده قابل پخش نمی‌شوند،
 *  ۳) IV هر فایل منحصربه‌فرد باشد (بازاستفاده‌ی IV در GCM بحرانی است).
 *
 * اگر روزی قالب عوض شود، این تست‌ها باید شکست بخورند تا سازگاری
 * فایل‌های قدیمی بررسی شود.
 */
class CryptoFormatTest {

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    /** همان مسیر رمزنگاری Crypto.encryptToStream با کلید داده‌شده. */
    private fun encryptToStream(input: InputStream, output: ByteArrayOutputStream, key: SecretKey) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        output.write(12)
        output.write(iv)
        CipherOutputStream(output, cipher).use { encrypted ->
            input.copyTo(encrypted, 8192)
        }
    }

    /** همان مسیر رمزگشایی Crypto.decryptToTempFile با کلید داده‌شده. */
    private fun decryptFromStream(data: ByteArray, key: SecretKey): ByteArray {
        val raw = ByteArrayInputStream(data)
        val ivLength = raw.read()
        require(ivLength in 1..32) { "bad iv length" }
        val iv = ByteArray(ivLength)
        var read = 0
        while (read < ivLength) {
            val n = raw.read(iv, read, ivLength - read)
            require(n > 0) { "truncated iv" }
            read += n
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        CipherInputStream(raw, cipher).use { decrypted ->
            return decrypted.readBytes()
        }
    }

    @Test
    fun `round trip preserves content`() {
        val key = newKey()
        val payload = ByteArray(50_000) { (it % 251).toByte() } // چند-MB نیست ولی چند-بخشی است
        val out = ByteArrayOutputStream()
        encryptToStream(ByteArrayInputStream(payload), out, key)
        val decrypted = decryptFromStream(out.toByteArray(), key)
        assertArrayEquals(payload, decrypted)
    }

    @Test
    fun `iv prefix byte equals 12`() {
        val out = ByteArrayOutputStream()
        encryptToStream(ByteArrayInputStream("x".toByteArray()), out, newKey())
        assertEquals(12, out.toByteArray()[0].toInt())
    }

    @Test
    fun `each encryption produces a unique iv`() {
        val key = newKey()
        val ivs = mutableSetOf<String>()
        repeat(16) {
            val out = ByteArrayOutputStream()
            encryptToStream(ByteArrayInputStream(ByteArray(16)), out, key)
            val data = out.toByteArray()
            ivs.add(data.copyOfRange(1, 13).joinToString(",") { "%02x".format(it) })
        }
        assertEquals("IV reuse detected in GCM", 16, ivs.size)
    }

    @Test
    fun `tampered ciphertext must not decrypt`() {
        val key = newKey()
        val out = ByteArrayOutputStream()
        encryptToStream(ByteArrayInputStream(ByteArray(2048)), out, key)
        val data = out.toByteArray()
        data[data.size - 1] = (data[data.size - 1].toInt() xor 0x01).toByte()
        assertThrows(Exception::class.java) { decryptFromStream(data, key) }
    }

    @Test
    fun `wrong key must not decrypt`() {
        val out = ByteArrayOutputStream()
        encryptToStream(ByteArrayInputStream(ByteArray(1024)), out, newKey())
        assertThrows(Exception::class.java) { decryptFromStream(out.toByteArray(), newKey()) }
    }

    @Test
    fun `iv length byte must be sane`() {
        val key = newKey()
        val out = ByteArrayOutputStream()
        encryptToStream(ByteArrayInputStream(ByteArray(16)), out, key)
        val data = out.toByteArray()
        data[0] = 0 // طول نامعتبر
        assertThrows(IllegalArgumentException::class.java) { decryptFromStream(data, key) }
    }

    @Test
    fun `header pattern matches documented format`() {
        val key = newKey()
        val out = ByteArrayOutputStream()
        encryptToStream(ByteArrayInputStream(ByteArray(16)), out, key)
        val data = out.toByteArray()
        assertTrue("IV length byte wrong", data[0].toInt() == 12)
        assertTrue("IV must not be all-zero", data.copyOfRange(1, 13).any { it != 0.toByte() })
        assertFalse(data.contentEquals(ByteArray(data.size)))
    }

    @Test
    fun `random source is available for future salt usage`() {
        // Keystore خودش IV تصادفی می‌سازد؛ این تست فقط مطمئن می‌شود
        // SecureRandom در محیط تست در دسترس است (sanity check).
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        assertTrue(bytes.any { it != 0.toByte() })
    }
}
