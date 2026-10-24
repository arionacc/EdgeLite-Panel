package com.edgelite.panel

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

data class AppItem(val pkg: String, val label: String)

object AppRepo {
    private val main = Handler(Looper.getMainLooper())
    private var cache: List<AppItem>? = null
    private var loading = false
    private val waiting = mutableListOf<(List<AppItem>) -> Unit>()

    /** Membaca semua aplikasi beserta labelnya. Lambat, jadi jangan dipanggil di main thread. */
    fun launchable(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    /** Membuang cache supaya aplikasi yang baru dipasang atau dihapus ikut terbaca pada pemuatan berikutnya. */
    fun invalidate() {
        cache = null
    }

    /**
     * Memuat daftar di thread latar dan memanggil [onDone] di main thread. Bila cache sudah ada,
     * [onDone] langsung dipanggil. Tanpa [onDone] fungsi ini hanya memanaskan cache. Panggil dari main thread.
     */
    fun load(ctx: Context, onDone: (List<AppItem>) -> Unit = {}) {
        cache?.let {
            onDone(it)
            return
        }
        waiting.add(onDone)
        if (loading) return
        loading = true
        val app = ctx.applicationContext
        Thread {
            val list = runCatching { launchable(app) }.getOrDefault(emptyList())
            main.post {
                if (list.isNotEmpty()) cache = list
                loading = false
                val callbacks = waiting.toList()
                waiting.clear()
                callbacks.forEach { it(list) }
            }
        }.start()
    }
}
