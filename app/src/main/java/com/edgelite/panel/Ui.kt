package com.edgelite.panel

/**
 * Palet warna dan token desain bergaya SnipBox: hitam pekat, permukaan abu gelap, aksen abu terang.
 *
 * Warna di blok pertama dipakai juga oleh panel sungguhan di [EdgeAccessibilityService]
 * dan [PanelPreview], jadi nilainya jangan diubah. Blok kedua hanya dipakai halaman pengaturan.
 */
object Ui {
    val BG = 0xFF0F0F0F.toInt()
    val PANEL = 0xFF141414.toInt()
    val SURFACE = 0xFF1C1C1C.toInt()
    val OUTLINE = 0xFF2E2E2E.toInt()
    val TEXT = 0xFFEDEDED.toInt()
    val MUTED = 0xFF8A8A8A.toInt()
    val LIGHT = 0xFFE6E6E6.toInt()
    val ON_LIGHT = 0xFF0F0F0F.toInt()

    // ---- Halaman pengaturan

    /** Latar kartu. Dibedakan dari [BG] lewat warna, bukan garis tepi. */
    val CARD = 0xFF181818.toInt()

    /** Latar kontrol (segmen, tombol sekunder) yang berada di atas kartu. */
    val CONTROL = 0xFF2A2A2A.toInt()

    /** Garis pemisah tipis di dalam kartu. */
    val DIVIDER = 0xFF2A2A2A.toInt()

    /** Teks sekunder di atas [CONTROL]. Rasio kontras di atas 4,5:1. */
    val MUTED_HI = 0xFFA8A8A8.toInt()

    // Jarak dan ukuran dalam dp. Semua kontrol yang bisa diketuk minimal 48 dp.
    const val PAGE_PAD_DP = 24
    const val SECTION_GAP_DP = 40
    const val CARD_GAP_DP = 14
    const val CARD_RADIUS_DP = 24
    const val HERO_RADIUS_DP = 28
    const val CONTROL_HEIGHT_DP = 48
    const val ROW_MIN_DP = 72

    // Skala tipografi dalam sp, supaya ikut ukuran huruf sistem.
    const val TEXT_TITLE = 28f
    const val TEXT_HERO = 20f
    const val TEXT_CARD_TITLE = 16f
    const val TEXT_BODY = 14f
    const val TEXT_SECONDARY = 13f
    const val TEXT_CAPTION = 12f
}
