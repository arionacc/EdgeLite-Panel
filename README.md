# EdgeLite Panel

Panel tepi alternatif untuk ponsel Samsung One UI yang belum punya Edge Panel.

- Handle tipis di tepi layar (kiri atau kanan), geser ke dalam untuk membuka panel
- Buka aplikasi dalam jendela mengambang atau layar penuh
- Ukuran jendela, sisi panel, dan posisi handle diatur terpisah untuk potret dan lanskap
- Tile Quick Settings untuk menyalakan atau mematikan panel
- Mulai otomatis: panel aktif lagi setelah ponsel restart
- Ekspor dan impor pengaturan ke berkas JSON, supaya update tidak mengulang dari awal
- Pengaturan dikelompokkan per kategori, dengan menu Tutorial dan solusi masalah di titik tiga

## Build di GitHub

1. Push seluruh isi folder ini ke repo GitHub (branch `main`).
2. Buka tab **Actions**, workflow **Build APK** berjalan otomatis.
3. Unduh `EdgeLite-debug-apk` dari bagian Artifacts, atau buat tag `v1.0.0` agar APK masuk ke Releases.

## Yang perlu diatur di ponsel

1. Nyalakan layanan EdgeLite Panel di Pengaturan, Aksesibilitas, Aplikasi terinstal (wajib). Layanan ini tidak membaca isi layar.
2. Android 13 ke atas: bila tombolnya abu-abu, buka Info aplikasi, titik tiga, Izinkan pengaturan yang dibatasi
3. Opsional: abaikan optimasi baterai
4. Jendela mengambang: aktifkan freeform di Opsi Pengembang

Tidak ada notifikasi dan tidak ada foreground service, jadi EdgeLite tidak muncul di "Periksa aktivitas latar belakang".

## Pernyataan

EdgeLite adalah proyek independen dan tidak terafiliasi dengan, didukung oleh, atau disponsori oleh Samsung Electronics. Samsung, One UI, dan Edge Panel adalah merek dagang milik pemiliknya masing-masing, disebut di sini hanya untuk menjelaskan kompatibilitas.

## Lisensi

MIT. Dibuat oleh Arion. Lihat berkas [LICENSE](LICENSE).

Source code: https://github.com/arionacc/EdgeLite-Panel
