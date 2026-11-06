package com.edgelite.panel

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.os.Handler

/**
 * Tuan rumah widget Android untuk EdgeLite. Satu instance dipakai bersama oleh halaman pengaturan
 * (menambah dan menghapus widget) dan layanan aksesibilitas (menampilkan widget di panel), karena
 * sistem mengenali widget lewat ID tuan rumah ini.
 */
object WidgetHost {

    /** ID tuan rumah. Jangan diubah: widget yang sudah ditambahkan terikat pada ID ini. */
    private const val HOST_ID = 0x45444C54

    @Volatile
    private var host: AppWidgetHost? = null

    fun get(ctx: Context): AppWidgetHost =
        host ?: synchronized(this) {
            host ?: AppWidgetHost(ctx.applicationContext, HOST_ID).also { host = it }
        }

    fun info(ctx: Context, id: Int): AppWidgetProviderInfo? =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id) }.getOrNull()

    /** Hanya ID yang masih dikenal sistem. ID bisa hilang setelah pulihkan cadangan atau widget dihapus. */
    fun validIds(ctx: Context, ids: List<Int>): List<Int> = ids.filter { info(ctx, it) != null }

    /** Ukuran widget dalam sel kisi (satu sel sekitar 70 dp), seperti yang ditampilkan peluncur. */
    fun cells(px: Int, density: Float): Int = maxOf(1, ((px / density).toInt() + 30) / 70)
}

/**
 * Konteks untuk tampilan widget yang hidup di dalam layanan. Saat widget diketuk, sistem memanggil
 * startIntentSender pada konteks tampilannya, dan konteks layanan tidak mendukungnya (hanya Activity).
 * Kelas ini meneruskannya lewat IntentSender langsung. [onLaunched] dipanggil dengan true bila
 * berhasil dikirim, supaya panel bisa ditutup.
 */
class WidgetContext(base: Context, private val onLaunched: (Boolean) -> Unit) : ContextWrapper(base) {

    override fun startIntentSender(
        intent: IntentSender,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int
    ) {
        startIntentSender(intent, fillInIntent, flagsMask, flagsValues, extraFlags, null)
    }

    override fun startIntentSender(
        intent: IntentSender,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int,
        options: Bundle?
    ) {
        // Tidak boleh melempar: pemanggilnya ada di dalam kode widget dan kegagalan di sini akan
        // menjatuhkan layanan aksesibilitas.
        val ok = runCatching {
            val fill = Intent(fillInIntent ?: Intent())
            fill.flags = (fill.flags and flagsMask.inv()) or (flagsValues and flagsMask)
            fill.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val sent = Build.VERSION.SDK_INT >= 34 && sendWithOptions(intent, fill, options)
            // Cadangan: varian publik tanpa ActivityOptions (cukup di bawah Android 14).
            if (!sent) intent.sendIntent(baseContext, 0, fill, null, null, null)
        }.isSuccess
        onLaunched(ok)
    }

    /**
     * Android 14 ke atas: pengirim PendingIntent harus mengizinkan peluncuran dari latar belakang lewat
     * ActivityOptions. IntentSender.sendIntent publik tidak menerima opsi, jadi varian tujuh argumen
     * (tersembunyi di SDK) dipanggil lewat refleksi. Mengembalikan false bila tidak tersedia atau gagal,
     * lalu pemanggil memakai varian publik.
     */
    private fun sendWithOptions(intent: IntentSender, fill: Intent, system: Bundle?): Boolean = runCatching {
        val m = IntentSender::class.java.getMethod(
            "sendIntent",
            Context::class.java, Int::class.javaPrimitiveType, Intent::class.java,
            IntentSender.OnFinished::class.java, Handler::class.java, String::class.java, Bundle::class.java
        )
        val bal = ActivityOptions.makeBasic().apply {
            setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }.toBundle()
        val merged = Bundle().apply {
            if (system != null) putAll(system)
            putAll(bal)
        }
        m.invoke(intent, baseContext, 0, fill, null, null, null, merged)
        true
    }.getOrDefault(false)
}
