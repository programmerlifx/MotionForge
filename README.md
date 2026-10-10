# MotionForge — Android Video Editor

MotionForge adalah fondasi aplikasi editor video Android bergaya motion graphics dengan tema gelap dan aksen ungu. Nama/branding dibuat sendiri; bukan aplikasi resmi Alight Motion.

## Fitur yang tersedia pada build awal
- Impor video melalui pemilih dokumen Android.
- Preview video.
- Timeline layer untuk video, teks, audio (metadata layer), dan preset efek (daftar layer).
- Dialog tambah layer teks.
- Pengaturan titik mulai dan akhir potong video.
- Ekspor potongan video MP4 melalui AndroidX Media3 Transformer.
- Simpan data proyek JSON ke direktori privat aplikasi.
- Ikon launcher vector drawable dan UI responsif berbasis Android Views.

## Batasan versi awal
Layer teks/audio dan pilihan efek masih dicatat pada timeline sebagai konsep proyek, belum dikomposisikan ke dalam hasil ekspor. Ekspor saat ini menerapkan pemotongan video saja. Belum ada keyframe/kurva animasi, compositing multi-layer, efek GPU real-time, atau pemuatan ulang proyek. Fitur tersebut memerlukan implementasi tahap berikutnya dan pengujian pada berbagai perangkat.

## Build APK melalui GitHub Actions
1. Buat repository baru di GitHub, misalnya `MotionForge`.
2. Unggah seluruh isi folder proyek ini ke root repository (folder `.github` juga harus ikut).
3. Buka tab **Actions** dan aktifkan Actions jika diminta.
4. Pilih workflow **Build MotionForge APK**, lalu tekan **Run workflow**.
5. Tunggu sampai workflow hijau.
6. Buka hasil run, turun ke bagian **Artifacts**, unduh `MotionForge-debug-apk`.
7. Ekstrak ZIP artifact untuk memperoleh `app-debug.apk`, lalu instal di perangkat Android. Jika diminta, izinkan instalasi dari sumber tersebut.

Workflow juga otomatis berjalan saat push ke branch `main` atau `master`.

## Build lokal
Butuh JDK 17, Android SDK platform 35, dan Gradle 8.9:
```bash
gradle assembleDebug
```

## Lokasi file di aplikasi
- Proyek: `Android/data/com.motionforge.editor/files/projects/motionforge_project.json`
- Ekspor: `Android/data/com.motionforge.editor/files/MotionForge_<timestamp>.mp4`

Android versi baru dapat membatasi akses langsung ke direktori `Android/data`; gunakan pengelola file yang mendukungnya atau tambahkan ekspor/share ke galeri pada tahap berikutnya.

## Rencana pengembangan
1. Persistensi dan pemuatan ulang proyek.
2. Audio mixing dan waveform.
3. Efek video menggunakan Media3 Effects.
4. Layer compositing untuk teks/gambar.
5. Keyframe dengan interpolasi dan kurva easing.
6. Ekspor ke Movies/Gallery dan berbagi.
