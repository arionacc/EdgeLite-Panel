package com.edgelite.panel

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView

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
            host ?: EdgeAppWidgetHost(ctx.applicationContext, HOST_ID).also { host = it }
        }

    /** Jumlah pemakai yang sedang mendengarkan (layanan dan dialog pratinjau), supaya yang satu tidak mematikan yang lain. */
    private var listeners = 0

    /** Mulai mendengarkan pembaruan widget bila belum. Pasangkan dengan [release]. Mengembalikan false bila gagal. */
    @Synchronized
    fun acquire(ctx: Context): Boolean {
        if (listeners == 0 && runCatching { get(ctx).startListening() }.isFailure) return false
        listeners++
        return true
    }

    @Synchronized
    fun release(ctx: Context) {
        if (listeners == 0) return
        listeners--
        if (listeners == 0) runCatching { get(ctx).stopListening() }
    }

    /** Meminta penyedia widget mengirim ulang konten lewat siaran pembaruan standar. */
    fun requestUpdate(ctx: Context, id: Int, info: AppWidgetProviderInfo) {
        runCatching {
            ctx.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
            )
        }
    }

    fun info(ctx: Context, id: Int): AppWidgetProviderInfo? =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id) }.getOrNull()

    /** Hanya ID yang masih dikenal sistem. ID bisa hilang setelah pulihkan cadangan atau widget dihapus. */
    fun validIds(ctx: Context, ids: List<Int>): List<Int> = ids.filter { info(ctx, it) != null }

    /**
     * Opsi tuan rumah untuk widget: kategori layar utama dan ukuran (dp). Banyak widget bawaan Samsung membaca
     * kategori dan ukuran ini dari opsi widget sebelum menggambar, dan menampilkan "Tidak dapat menampilkan
     * konten" bila kosong. Dipakai saat widget diikat dan setiap kartu dibuka.
     */
    fun hostOptions(wDp: Int, hDp: Int): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp)
    }

    /** Memberi tahu penyedia ukuran kartu. Android 12 ke atas juga mengirim daftar ukuran untuk tata letak responsif. */
    @Suppress("DEPRECATION")
    fun applySize(view: android.appwidget.AppWidgetHostView, wDp: Int, hDp: Int) {
        val opts = hostOptions(wDp, hDp)
        if (Build.VERSION.SDK_INT >= 31) {
            view.updateAppWidgetSize(opts, listOf(SizeF(wDp.toFloat(), hDp.toFloat())))
        } else {
            view.updateAppWidgetSize(opts, wDp, hDp, wDp, hDp)
        }
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

/** Tuan rumah yang membuat [EdgeHostView], supaya galat tampilan widget bisa dijelaskan penyebabnya. */
class EdgeAppWidgetHost(ctx: Context, hostId: Int) : AppWidgetHost(ctx, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        EdgeHostView(context)
}

/**
 * Tampilan widget dengan keterangan galat sendiri. Bila sistem gagal menggambar konten widget, tampilan bawaan
 * hanya berbunyi "Problem loading widget". Di sini pesannya jelas, dan penyebabnya (jenis dan pesan galat
 * dari penyedia widget) ikut ditampilkan, supaya widget yang menolak tampil di luar peluncur bawaan bisa dikenali.
 */
class EdgeHostView(context: Context) : AppWidgetHostView(context) {
    private var errorBox: View? = null
    private var reasonView: TextView? = null

    override fun getErrorView(): View {
        val d = resources.displayMetrics.density
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val p = (16 * d).toInt()
            setPadding(p, p, p, p)
        }
        box.addView(TextView(context).apply {
            text = context.getString(R.string.widget_error_title)
            setTextColor(Ui.TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        })
        val reason = TextView(context).apply {
            setTextColor(Ui.MUTED_HI)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, 0)
            visibility = View.GONE
        }
        box.addView(reason)
        errorBox = box
        reasonView = reason
        return box
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        val box = errorBox ?: return
        if (remoteViews == null || getChildAt(0) !== box) return
        // Sistem menelan galatnya, jadi diulang sekali hanya untuk membaca penyebabnya.
        val cause = runCatching { remoteViews.apply(context, FrameLayout(context)) }.exceptionOrNull() ?: return
        val root = generateSequence<Throwable>(cause) { it.cause }.last()
        reasonView?.apply {
            text = context.getString(R.string.widget_error_reason, "${root.javaClass.simpleName}: ${root.message.orEmpty().take(140)}")
            visibility = View.VISIBLE
        }
    }
}
