# Al-Qur'an & Hadits — Aplikasi Android

Aplikasi offline: Al-Qur'an 30 juz (teks Arab + terjemahan Kemenag RI 2019) dan 9 kitab hadits
(38.102 hadits, Arab + terjemahan), dengan notifikasi harian dan pencarian yang menghubungkan ayat ↔ hadits.

## Fitur
- **Notifikasi harian** — "Ayat Pilihan Hari Ini" (default 05:30) dan "Hadits Pilihan Hari Ini" (default 18:30).
  Jam bisa diubah/dimatikan di Pengaturan; ketuk notifikasi untuk membuka ayat/hadits beserta keterkaitannya.
  Pilihan berganti setiap hari (366 ayat + 366 hadits pilihan).
- **Pencarian terpadu** — satu kotak cari untuk Al-Qur'an dan Hadits:
  - kata Indonesia (dengan imbuhan otomatis: "kesabaran" → "sabar"; ejaan "shalat"/"salat" disamakan),
  - teks Arab (boleh tanpa harakat),
  - rujukan langsung: `2:255`, `18 10`, `bukhari 1`, `muslim 100`,
  - filter per kitab hadits.
- **Keterkaitan Al-Qur'an ↔ Hadits**
  - *Mengutip ayat*: 1.131 tautan — teks Arab hadits memuat ayat tersebut (dideteksi otomatis).
  - *Tema serupa*: ±37 ribu tautan — kemiripan isi terjemahan (TF-IDF).
  - Tab **Keterkaitan** di hasil cari menampilkan pasangan ayat–hadits yang sama-sama cocok dengan kata kunci.
  - Setiap ayat menampilkan "n hadits terkait"; setiap hadits menampilkan ayat terkait.
- Baca per surah, lanjut "terakhir dibaca", daftar hadits per kitab + lompat ke nomor.
- Simpan (bookmark), salin, bagikan; ukuran huruf Arab/terjemahan; mode gelap.

## Cara membuat APK

### Opsi A — Android Studio (paling mudah)
1. Pasang [Android Studio](https://developer.android.com/studio) (versi Ladybug 2024.2 atau lebih baru).
2. Ekstrak zip ini → **File ▸ Open** → pilih folder `AlQuranHadits`.
3. Tunggu Gradle sync selesai (pertama kali mengunduh dependensi, butuh internet).
4. **Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)**.
   APK ada di `app/build/outputs/apk/debug/app-debug.apk` → salin ke HP dan pasang.
   Atau colok HP (USB debugging aktif) lalu tekan ▶ Run.

### Opsi B — GitHub (tanpa install apa pun)
1. Buat repo baru di GitHub, unggah seluruh isi folder ini (termasuk `.github/`).
2. Buka tab **Actions** → workflow "Build APK" berjalan otomatis (±5 menit).
3. Unduh artefak `AlQuranHadits-apk` → berisi APK siap pasang.

### Opsi C — Command line
```
./gradlew assembleRelease      # butuh JDK 17 + Android SDK (ANDROID_HOME)
```
APK release ditandatangani dengan kunci debug agar langsung bisa dipasang. Untuk Play Store,
ganti `signingConfig` di `app/build.gradle.kts` dengan keystore Anda sendiri.

Catatan: saat pertama dibuka, aplikasi mengekstrak database (±125 MB) — butuh ruang ±160 MB dan beberapa detik.
Android 13+ akan meminta izin notifikasi. Di HP Xiaomi/Oppo/Vivo, matikan pengoptimalan baterai agar notifikasi tepat waktu.

## Struktur
```
app/src/main/assets/alquran_hadits.dat   database SQLite (FTS4) terkompresi
app/src/main/java/id/alquranhadits/
  data/   Db (ekstrak & buka DB), Repo (kueri), TextNorm (normalisasi & stemming), Prefs
  notif/  Scheduler (AlarmManager), DailyReceiver, BootReceiver
  ui/     Jetpack Compose: Beranda, Al-Qur'an, Hadits, Cari, Tersimpan, Pengaturan
tools/    skrip Python untuk membangun ulang database
```

### Membangun ulang database
```
python3 tools/parse_kemenag.py kemenag_raw.txt quran_id.json quran.json      # teks PDF Kemenag → JSON
python3 tools/build_db.py <folder hadits-*.txt> quran.json alquran_hadits.db  # butuh scikit-learn
gzip -9 -c alquran_hadits.db > app/src/main/assets/alquran_hadits.dat
stat -c %s alquran_hadits.db > app/src/main/assets/db_size.txt
```
Jika mengubah isi DB, naikkan `VERSION` di `data/Db.kt` agar DB lama di HP diganti.
Normalisasi di `TextNorm.kt` harus tetap sama dengan `build_db.py`.

## Sumber data
- Terjemahan Al-Qur'an: Kementerian Agama RI, edisi penyempurnaan 2019 (dari PDF Lajnah Pentashihan
  Mushaf Al-Qur'an). 6.227 ayat diurai dari PDF; 9 ayat yang gagal diurai memakai terjemahan Kemenag versi lama (paket quran-json).
- Teks Arab Al-Qur'an & nama surah: paket npm `quran-json`.
- Hadits 9 kitab: github.com/gadingnst/hadith-api (MIT).

Keterkaitan "tema serupa" dihitung otomatis dan bersifat bantuan telaah; rujuk kitab syarah/ulama untuk kajian mendalam.
