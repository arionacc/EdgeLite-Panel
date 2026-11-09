package com.edgelite.panel

/**
 * Ikon kustom untuk widget di halaman Widget panel. Pilihan disimpan sebagai string pendek di [Prefs]:
 * `q:<kode>` untuk ikon bawaan EdgeLite (set ikon akses cepat, kodenya sama dengan [QuickAction.code]),
 * `a:<paket>` untuk ikon aplikasi terpasang, dan `p:<paket>/<nama>` untuk satu ikon dari paket ikon pihak ketiga ([IconPacks]). Tanpa pilihan, panel memakai ikon dari penyedia widget.
 */
object WidgetIcons {
    private const val BUILTIN = "q:"
    private const val APP = "a:"
    private const val PACK = "p:"

    /** Seluruh set ikon bawaan, urutannya sama dengan urutan [QuickAction]. */
    fun builtin(): List<QuickAction> = QuickAction.values().toList()

    fun builtinChoice(a: QuickAction): String = BUILTIN + a.code

    fun appChoice(pkg: String): String = APP + pkg

    /** Ikon bawaan untuk [choice], atau null bila pilihannya bukan ikon bawaan atau kodenya tak dikenal. */
    fun builtinFor(choice: String?): QuickAction? {
        if (choice == null || !choice.startsWith(BUILTIN)) return null
        val code = choice.substring(BUILTIN.length)
        return QuickAction.values().firstOrNull { it.code == code }
    }

    /** Paket aplikasi untuk [choice], atau null bila pilihannya bukan ikon aplikasi. */
    fun appFor(choice: String?): String? {
        if (choice == null || !choice.startsWith(APP)) return null
        return choice.substring(APP.length).takeIf { it.isNotEmpty() }
    }

    fun packChoice(pack: String, name: String): String = "$PACK$pack/$name"

    /** Pasangan (paket ikon, nama drawable) untuk [choice], atau null bila pilihannya bukan ikon paket. */
    fun packFor(choice: String?): Pair<String, String>? {
        if (choice == null || !choice.startsWith(PACK)) return null
        val body = choice.substring(PACK.length)
        val i = body.indexOf('/')
        if (i <= 0 || i == body.length - 1) return null
        return body.substring(0, i) to body.substring(i + 1)
    }

    /** Warna isi (gelap atau terang) yang terbaca di atas latar [bg], dipakai untuk glyph di atas warna kustom. */
    fun onColor(bg: Int): Int {
        val r = (bg shr 16) and 0xFF
        val g = (bg shr 8) and 0xFF
        val b = bg and 0xFF
        val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (lum > 0.6) 0xFF0F0F0F.toInt() else 0xFFEDEDED.toInt()
    }

    /** Apakah [choice] berbentuk pilihan yang sah. Dipakai saat membaca dari pengaturan. */
    fun isValid(choice: String): Boolean =
        Regex("([qa]:[A-Za-z0-9_.]+|p:[A-Za-z0-9_.]+/[A-Za-z0-9_.]+)").matches(choice)
}
