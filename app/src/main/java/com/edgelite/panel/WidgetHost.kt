package com.edgelite.panel

import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.RequiresApi

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

    /**
     * Penangan ketukan widget untuk Android 12 ke atas. Memakai PendingIntent langsung, yang boleh
     * membawa ActivityOptions, sehingga izin peluncuran dari latar belakang Android 14 bisa dipasang
     * (IntentSender tidak mendukungnya). Di versi lebih lama dipakai [WidgetContext].
     * [onLaunched] dipanggil dengan true bila berhasil dikirim.
     */
    @RequiresApi(31)
    fun interactionHandler(onLaunched: (Boolean) -> Unit): RemoteViews.InteractionHandler =
        RemoteViews.InteractionHandler { view, pendingIntent, response ->
            // Tidak boleh melempar: kegagalan di sini terjadi di dalam kode widget di layanan aksesibilitas.
            val ok = runCatching {
                val launch = response.getLaunchOptions(view)
                val fill = Intent(launch.first)
                fill.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opts = launch.second
                if (Build.VERSION.SDK_INT >= 34) {
                    opts.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                pendingIntent.send(view.context, 0, fill, null, null, null, opts.toBundle())
            }.isSuccess
            onLaunched(ok)
            true
        }

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
            // IntentSender.sendIntent publik tidak menerima ActivityOptions, jadi [options] dari sistem diabaikan.
            intent.sendIntent(baseContext, 0, fill, null, null, null)
        }.isSuccess
        onLaunched(ok)
    }
}
