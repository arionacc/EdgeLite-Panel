# EdgeLite Panel

Panel tepi alternatif untuk ponsel Samsung One UI yang belum punya Edge Panel.

- Handle tipis di tepi layar (kiri atau kanan), geser ke dalam untuk membuka panel
- Buka aplikasi dalam mode layar penuh, split screen, atau jendela mengambang
- Tile Quick Settings untuk menyalakan atau mematikan panel
- Tampilan mengikuti mode gelap/terang dengan blur latar (Android 12+)

## Build di GitHub

1. Push seluruh isi folder ini ke repo GitHub (branch `main`).
2. Buka tab **Actions**, workflow **Build APK** berjalan otomatis.
3. Unduh `EdgeLite-debug-apk` dari bagian Artifacts, atau buat tag `v1.0.0` agar APK masuk ke Releases.

## Izin yang perlu diberikan di ponsel

1. Tampil di atas aplikasi lain (wajib)
2. Layanan aksesibilitas (untuk split screen otomatis)
3. Abaikan optimasi baterai (agar panel tidak dimatikan One UI)
4. Mode jendela: aktifkan freeform di Opsi Pengembang
