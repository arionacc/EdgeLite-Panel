package com.edgelite.panel

import android.accessibilityservice.AccessibilityService
import android.os.Build

/** Cara sebuah aksi akses cepat dijalankan dan ditampilkan di halaman kedua panel. */
enum class QuickKind {
    /** Aksi global layanan aksesibilitas. Panel menutup, lalu aksi dijalankan. */
    GLOBAL,

    /** Slider yang digeser langsung di panel (volume, kecerahan). */
    SLIDER,

    /** Sakelar dengan status nyala atau mati yang ikut tampil di ikonnya. Panel tetap terbuka. */
    TOGGLE,

    /** Aksi sekali ketuk yang tidak punya status (kontrol media). Panel tetap terbuka. */
    ONESHOT,

    /** Membuka aplikasi lain (perekam layar). Panel menutup. */
    LAUNCH
}

/**
 * Aksi akses cepat di halaman kedua panel.
 * [code] disimpan di pengaturan, jangan diubah. Urutan di sini adalah urutan tampil di panel.
 * Aksi bertipe [QuickKind.GLOBAL] memakai aksi global layanan aksesibilitas tanpa izin tambahan.
 * Brightness dan putar otomatis butuh izin "Ubah pengaturan sistem" yang diminta saat pertama dipakai.
 * Rekam layar tidak merekam sendiri: membuka aplikasi perekam layar yang terpasang.
 */
enum class QuickAction(
    val code: String,
    val kind: QuickKind,
    val icon: Int,
    val label: Int,
    val globalAction: Int = 0,
    private val minSdk: Int = 1
) {
    NOTIFICATIONS("n", QuickKind.GLOBAL, R.drawable.ic_qa_notifications, R.string.qa_notifications, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
    QUICK_SETTINGS("q", QuickKind.GLOBAL, R.drawable.ic_qa_quick_settings, R.string.qa_quick_settings, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
    RECENTS("r", QuickKind.GLOBAL, R.drawable.ic_qa_recents, R.string.qa_recents, AccessibilityService.GLOBAL_ACTION_RECENTS),
    HOME("h", QuickKind.GLOBAL, R.drawable.ic_qa_home, R.string.qa_home, AccessibilityService.GLOBAL_ACTION_HOME),
    BACK("b", QuickKind.GLOBAL, R.drawable.ic_qa_back, R.string.qa_back, AccessibilityService.GLOBAL_ACTION_BACK),
    SCREENSHOT("s", QuickKind.GLOBAL, R.drawable.ic_qa_screenshot, R.string.qa_screenshot, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, 28),
    LOCK_SCREEN("l", QuickKind.GLOBAL, R.drawable.ic_qa_lock, R.string.qa_lock_screen, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, 28),
    POWER_MENU("p", QuickKind.GLOBAL, R.drawable.ic_qa_power, R.string.qa_power_menu, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG),

    // Ditambahkan di 1.2.0
    VOLUME("v", QuickKind.SLIDER, R.drawable.ic_qa_volume, R.string.qa_volume),
    BRIGHTNESS("g", QuickKind.SLIDER, R.drawable.ic_qa_brightness, R.string.qa_brightness),
    SCREEN_RECORD("c", QuickKind.LAUNCH, R.drawable.ic_qa_record, R.string.qa_screen_record),
    FLASHLIGHT("f", QuickKind.TOGGLE, R.drawable.ic_qa_flashlight, R.string.qa_flashlight, minSdk = 23),
    AUTO_ROTATE("o", QuickKind.TOGGLE, R.drawable.ic_qa_rotate, R.string.qa_auto_rotate),
    VIBRATE("m", QuickKind.TOGGLE, R.drawable.ic_qa_sound, R.string.qa_vibrate),
    MEDIA_PLAY("y", QuickKind.ONESHOT, R.drawable.ic_qa_play, R.string.qa_media_play),
    MEDIA_NEXT("x", QuickKind.ONESHOT, R.drawable.ic_qa_next, R.string.qa_media_next),
    MEDIA_PREV("z", QuickKind.ONESHOT, R.drawable.ic_qa_prev, R.string.qa_media_prev);

    /** Apakah versi Android ini mendukung aksinya. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= minSdk

    companion object {
        fun available(): List<QuickAction> = values().filter { it.supported }

        fun fromCodes(s: String): List<QuickAction> =
            s.split(",").mapNotNull { c -> values().firstOrNull { it.code == c } }.distinct()
    }
}
