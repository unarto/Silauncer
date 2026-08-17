# Laporan Audit & Cleanup Proyek

Berikut adalah hasil audit menyeluruh terhadap proyek untuk mengidentifikasi kode mati, placeholder, implementasi palsu, file hasil build, dan masalah kualitas kode.

## 1. Dead Code & Source
Tidak ada dead code signifikan pada level fungsi atau kelas di source code utama. Namun, ada file test sample yang tidak pernah dipakai untuk logic aplikasi nyata:
* **`app/src/test/java/com/example/ExampleRobolectricTest.kt`**
* **`app/src/test/java/com/example/ExampleUnitTest.kt`**
* **`app/src/test/java/com/example/GreetingScreenshotTest.kt`**
* **`app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`**
  * **Alasan:** Ini adalah file template bawaan (dummy testing). Package name-nya pun masih `com.example`.
  * **Tingkat Keyakinan:** 100% Aman dihapus.
  * **Dampak:** Tidak ada dampak ke aplikasi utama. Membersihkan struktur folder dari mock test yang tidak relevan.

## 2. Placeholder / Dummy
* **`app/src/test/screenshots/greeting.png`**
  * **Alasan:** Gambar dummy/mock dari screenshot test bawaan template.
  * **Tingkat Keyakinan:** 100% Aman dihapus.
  * **Dampak:** Ruang penyimpanan sedikit lebih lega.

* **`app/src/main/res/xml/data_extraction_rules.xml`**
  * **Alasan:** File template ini mengandung placeholder `<!-- TODO: Use <include> and <exclude> to control what is backed up. -->`. Selain itu file ini tidak pernah direferensikan dari `AndroidManifest.xml` (tidak ada `android:dataExtractionRules`).
  * **Tingkat Keyakinan:** 100% Aman dihapus.
  * **Dampak:** Mengurangi resource sampah yang tidak digunakan.

## 3. Binary & Build Artifact
* **Folder `app/build/`**
* **Folder `.build-outputs/`**
  * **Alasan:** Keduanya berisi artefak dari proses build sebelumnya (contohnya class files, APK, file cache lint). Seharusnya tidak dilacak/dibaca dan aman untuk di-clean.
  * **Tingkat Keyakinan:** 100% Aman dihapus.
  * **Dampak:** Proyek menjadi jauh lebih bersih; Gradle akan meng-generate ulang saat build berikutnya.

## 4. Resource
* **`app/src/main/res/xml/backup_rules.xml`**
  * **Alasan:** Tidak direferensikan dari `AndroidManifest.xml` (kurang atribut `android:fullBackupContent`).
  * **Tingkat Keyakinan:** 100% Aman dihapus.
  * **Dampak:** Mengurangi overhead build untuk resource XML yang terabaikan.
* **`app/src/main/res/values/colors.xml`**
  * **Alasan:** Terdapat warna dummy bawaan template yang tidak pernah digunakan: `purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700`, `black`, `white`.
  * **Tingkat Keyakinan:** 95% Sangat mungkin aman.
  * **Dampak:** Tidak berdampak buruk; mempermudah pengelolaan palet warna.
* **`app/src/main/res/values/strings.xml`**
  * **Alasan:** Terdapat string yang tidak dipakai (terdeteksi oleh lint): `settings` dan `hide`.
  * **Tingkat Keyakinan:** 95% Sangat mungkin aman.
  * **Dampak:** Tidak berdampak buruk.

## 5. Dependency
* **`androidx.constraintlayout:constraintlayout:2.1.4`**
  * **Alasan:** Library ini di-include di `app/build.gradle.kts` namun tidak ada satupun layout `.xml` atau kode Kotlin yang menggunakan `ConstraintLayout`.
  * **Tingkat Keyakinan:** 100% Aman dihapus.
  * **Dampak:** APK size menjadi lebih kecil dan waktu kompilasi sedikit lebih cepat.
* **KSP dependencies: `"ksp"(libs.androidx.room.compiler)` & `"ksp"(libs.moshi.kotlin.codegen)`**
  * **Alasan:** KSP plugins ini terdaftar di blok `dependencies` pada `app/build.gradle.kts`, padahal dependensi utama Room dan Moshi sendiri dalam posisi di-comment out (non-aktif).
  * **Tingkat Keyakinan:** 100% Aman dihapus/di-comment.
  * **Dampak:** Mencegah prosesor anotasi KSP yang tidak perlu dijalankan saat build.

## 6. Code Quality
* **Hardcoded String pada `app/src/main/java/com/silauncer/cepat/settings/SettingsActivity.kt`**
  * **Alasan:** Banyak penggunaan teks hardcoded (contoh: `"Grid Columns"`, `"Icon Size (dp)"`, `"Sorting Mode"`, dll).
  * **Rekomendasi:** Perlu dipindahkan ke `strings.xml`.
  * **Tingkat Keyakinan:** 100% Perlu diperbaiki.
* **Deprecated API pada `LauncherActivity.kt` (Baris 133)**
  * **Alasan:** Fungsi `onBackPressed()` sudah usang (deprecated) sejak Android 13 untuk men-support predictive back gesture. Lint menyarankan migrasi ke `OnBackPressedDispatcher`.
  * **Rekomendasi:** Ganti implementasi back handling dengan `OnBackPressedCallback`.
  * **Tingkat Keyakinan:** 100% Perlu diperbaiki.
* **Redundant Logic di `AppActionHandler.kt`**
  * **Alasan:** Penggunaan `Uri.parse("package:${app.packageName}")`. Android KTX merekomendasikan penggunaan extension function `"package:${app.packageName}".toUri()`.
  * **Rekomendasi:** Perbaiki gaya penulisan agar sesuai dengan panduan KTX modern.

---

### Rekomendasi Tindakan (Menunggu Konfirmasi)
1. Hapus semua file test placeholder bawaan template beserta folder-folder kosongnya (seperti `app/src/test/` dan `app/src/androidTest/`).
2. Hapus `backup_rules.xml` dan `data_extraction_rules.xml` karena tidak digunakan.
3. Bersihkan sisa warna dan string usang di resource XML.
4. Hapus referensi `ConstraintLayout` dan library KSP (`room` & `moshi`) dari Gradle.
5. Ekstrak string hardcoded dari `SettingsActivity` menuju `strings.xml`.
6. Migrasi dari `onBackPressed()` menggunakan `OnBackPressedDispatcher` di `LauncherActivity`.

Semua temuan di atas menunggu izin eksekusi dari Anda. Tidak ada tindakan penghapusan atau perubahan apa pun yang sudah dilakukan.
