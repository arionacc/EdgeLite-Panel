package com.edgelite.panel

import android.accessibilityservice.AccessibilityService
import android.os.Build

/**
 * Aksi akses cepat di bagian atas panel. Semuanya memakai aksi global layanan aksesibilitas,
 * jadi tidak butuh izin tambahan dan aplikasi tidak menerima isi layar apa pun.
 * [code] disimpan di pengaturan, jangan diubah. Urutan di sini adalah urutan tampil di panel.
 */
enum class QuickAction(
    val code: String,
    val globalAction: Int,
    val icon: Int,
    val label: Int,
    private val minSdk: Int = 1
) {
    NOTIFICATIONS("n", AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, R.drawable.ic_qa_notifications, R.string.qa_notifications),
    QUICK_SETTINGS("q", AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, R.drawable.ic_qa_quick_settings, R.string.qa_quick_settings),
    RECENTS("r", AccessibilityService.GLOBAL_ACTION_RECENTS, R.drawable.ic_qa_recents, R.string.qa_recents),
    HOME("h", AccessibilityService.GLOBAL_ACTION_HOME, R.drawable.ic_qa_home, R.string.qa_home),
    BACK("b", AccessibilityService.GLOBAL_ACTION_BACK, R.drawable.ic_qa_back, R.string.qa_back),
    SCREENSHOT("s", AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, R.drawable.ic_qa_screenshot, R.string.qa_screenshot, 28),
    LOCK_SCREEN("l", AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, R.drawable.ic_qa_lock, R.string.qa_lock_screen, 28),
    POWER_MENU("p", AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, R.drawable.ic_qa_power, R.string.qa_power_menu);

    /** Apakah versi Android ini mendukung aksinya. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= minSdk

    companion object {
        fun available(): List<QuickAction> = values().filter { it.supported }

        fun fromCodes(s: String): List<QuickAction> =
            s.split(",").mapNotNull { c -> values().firstOrNull { it.code == c } }.distinct()
    }
}
