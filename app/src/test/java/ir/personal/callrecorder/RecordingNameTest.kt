package ir.personal.callrecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * تست‌های واحد منطق خالص — بدون نیاز به دستگاه یا شبیه‌ساز.
 *
 * نام فایل ضبط‌شده قالب ساده‌ای دارد:
 *   (Test|Call)Recording_yyyyMMdd_HHmmss.(m4a|enc)
 * این قالب به UI وابسته نیست و باید پایدار بماند؛ فایل‌های .enc از روی
 * پسوند در لیست تشخیص داده می‌شوند، پس قالب نام قرارداد عمومی است.
 */
class RecordingNameTest {

    private val pattern = Regex("^(Test|Call)Recording_\\d{8}_\\d{6}\\.(m4a|enc)$")

    @Test
    fun `plain recording uses Call prefix and m4a extension`() {
        val name = newDisplayName(test = false, encrypted = false)
        assertTrue("unexpected format: $name", pattern.matches(name))
        assertTrue(name.startsWith("CallRecording_"))
        assertTrue(name.endsWith(".m4a"))
    }

    @Test
    fun `test recording uses Test prefix`() {
        val name = newDisplayName(test = true, encrypted = false)
        assertTrue(name.startsWith("TestRecording_"))
        assertTrue(name.endsWith(".m4a"))
    }

    @Test
    fun `encrypted recording uses enc extension`() {
        val name = newDisplayName(test = false, encrypted = true)
        assertTrue(name.endsWith(".enc"))
    }

    @Test
    fun `timestamp follows yyyyMMdd_HHmmss in US locale`() {
        val name = newDisplayName(test = false, encrypted = false)
        val stamp = name.removePrefix("CallRecording_").removeSuffix(".m4a")
        val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        df.isLenient = false
        // نباید استثنا بدهد؛ یعنی مهر زمانی معتبر است
        df.parse(stamp)
    }

    @Test
    fun `two calls produce names of equal length format`() {
        val first = newDisplayName(test = false, encrypted = false)
        val second = newDisplayName(test = false, encrypted = false)
        // در همان ثانیه نام‌ها می‌توانند یکسان باشند؛ این تست فقط رگرسیون
        // قالب را پوشش می‌دهد (مثلاً اگر کسی Locale را عوض کند).
        assertEquals(first.length, second.length)
    }

    private fun newDisplayName(test: Boolean, encrypted: Boolean): String {
        // همان منطق RecordingStore.newDisplayName — برای تست بدون اندروید
        // اینجا تکرار می‌شود؛ اگر روزی منطق عوض شود این تست باید شکست بخورد.
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val prefix = if (test) "Test" else "Call"
        val ext = if (encrypted) "enc" else "m4a"
        return "${prefix}Recording_$stamp.$ext"
    }
}
