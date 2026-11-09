package com.edgelite.panel

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Paket ikon pihak ketiga (format yang dipakai ADW, Nova, GO, dan Apex). Paket dikenali dari intent yang
 * dideklarasikannya, daftar ikonnya dibaca dari `drawable.xml` (atau `appfilter.xml` bila tidak ada), dan
 * gambarnya dimuat dari resource paket itu lewat [PackageManager.getResourcesForApplication].
 * Tidak ada izin baru: manifes hanya menambah `queries` agar paket ikon terlihat di Android 11 ke atas.
 */
object IconPacks {
    class Pack(val pkg: String, val label: String)

    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME"
    )
    private val NAME = Regex("[A-Za-z0-9_.]+")
    private val cache = HashMap<String, List<String>>()

    /** Paket ikon yang terpasang, urut menurut nama. Cukup cepat, tetapi sebaiknya tidak di main thread. */
    fun installed(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, Pack>()
        ACTIONS.forEach { action ->
            runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrNull()?.forEach {
                val pkg = it.activityInfo.packageName
                if (pkg != ctx.packageName && pkg !in found) {
                    found[pkg] = Pack(pkg, runCatching { it.loadLabel(pm).toString() }.getOrDefault(pkg))
                }
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /** Nama drawable semua ikon di [pack]. Hasilnya disimpan sementara karena paket besar lambat dibaca. */
    @Synchronized
    fun icons(ctx: Context, pack: String): List<String> =
        cache.getOrPut(pack) { runCatching { read(ctx, pack) }.getOrDefault(emptyList()) }

    /** Memuat satu ikon dari [pack], atau null bila paket atau ikonnya tidak ada lagi. */
    fun load(ctx: Context, pack: String, name: String): Drawable? = runCatching {
        val res = ctx.packageManager.getResourcesForApplication(pack)
        val id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) null else res.getDrawable(id, null)
    }.getOrNull()

    private fun read(ctx: Context, pack: String): List<String> {
        val res = ctx.packageManager.getResourcesForApplication(pack)
        val names = LinkedHashSet<String>()
        // drawable.xml memuat daftar lengkap. appfilter.xml hanya cadangan, isinya ikon yang terhubung ke aplikasi.
        for (file in listOf("drawable", "appfilter")) {
            val parser = open(res, pack, file) ?: continue
            runCatching {
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                        parser.getAttributeValue(null, "drawable")?.let { if (NAME.matches(it)) names.add(it) }
                    }
                    ev = parser.next()
                }
            }
            if (names.isNotEmpty()) break
        }
        return names.filter { res.getIdentifier(it, "drawable", pack) != 0 }.sorted()
    }

    /** Membuka berkas xml paket, dari resource `xml/` dulu lalu dari assets. */
    private fun open(res: Resources, pack: String, name: String): XmlPullParser? {
        val id = res.getIdentifier(name, "xml", pack)
        if (id != 0) return runCatching { res.getXml(id) }.getOrNull()
        return runCatching {
            XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("$name.xml"), null) }
        }.getOrNull()
    }
}
