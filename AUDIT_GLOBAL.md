=== SUMMARY ===
Total Kotlin/Java: 14 files (main source)
Total class: 14 classes (excluding inner/helper classes)
Dead code: 4 template test files (ExampleRobolectricTest, ExampleUnitTest, GreetingScreenshotTest, ExampleInstrumentedTest)
Unused function: None detected in main source.
Unused dependency: `androidx.constraintlayout:constraintlayout`, `ksp` (room-compiler & moshi-kotlin-codegen)
Placeholder: `greeting.png`, `data_extraction_rules.xml`, `backup_rules.xml`
Mock: None
Dummy: None
Fake: None
Simulation: None
Unreachable: None
SRP violation: None (Concerns are well separated: UI, Controller, Repository, Persistency, Broadcasts, Handlers)
Broken runtime path: None (All features connected)
Settings disconnected: None
Build issue: Deprecated `onBackPressed()` call in `LauncherActivity`.
Deployment issue: None. APK generated successfully.

=== CRITICAL FINDINGS ===
P0
(Tidak ada temuan kritis yang menyebabkan aplikasi crash atau gagal fungsi.)

P1
File: `app/build.gradle.kts`
Masalah: Terdapat deklarasi dependency `androidx.constraintlayout:constraintlayout:2.1.4` namun tak ada satupun layout XML/Compose yang menggunakannya. Begitu juga plugin `ksp` dieksekusi meskipun library `room` dan `moshi` di-comment out.
Dampak: Proses build lebih lambat dan APK size sedikit membesar tanpa guna.
Severity: Medium
Rekomendasi: Hapus dependency constraint layout dan hapus/comment pemanggilan ksp.

P2
File: `app/src/main/java/com/silauncer/cepat/launcher/LauncherActivity.kt`
Class/function: `LauncherActivity` / `onBackPressed()`
Bukti call-site/reference: Baris 133
Masalah: Fungsi ini deprecated di Android 13+ untuk mendukung predictive back navigation.
Dampak: Lint error yang mematahkan proses build (jika lint dijalankan).
Severity: Low
Rekomendasi: Migrasi ke `OnBackPressedDispatcher`.

P3
File: `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml`, `colors.xml`
Masalah: Terdapat resource bawaan template yang tidak pernah direferensikan oleh manifest atau layout.
Dampak: Mengotori source tree.
Severity: Lowest
Rekomendasi: Hapus file dan tag yang tidak terpakai.

=== FEATURE REALITY MATRIX ===
- Grid / jumlah kolom:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Tata letak layar awal:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Ukuran ikon:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Pengisian ikon otomatis:
UI: NO, State: NO, Logic: NO, Persistence: NO, Runtime: NONE, Status: NOT IMPLEMENTED (Ini tampaknya perilaku standar grid mengisi ruang, tidak ada toggle eksplisit)

- Sembunyikan aplikasi:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Tampilkan label:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Ukuran label:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Jarak ikon:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Sorting A-Z:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Sorting Z-A:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Custom order:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Drag & Drop:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Persistensi posisi:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Reset layout:
UI: YES, State: YES, Logic: YES, Persistence: YES, Runtime: REAL, Status: IMPLEMENTED

- Aplikasi baru:
UI: N/A, State: N/A, Logic: YES, Persistence: N/A, Runtime: REAL, Status: IMPLEMENTED (via AppChangeReceiver & AppRepository)

- Aplikasi dihapus:
UI: N/A, State: N/A, Logic: YES, Persistence: N/A, Runtime: REAL, Status: IMPLEMENTED (via AppChangeReceiver & AppRepository)

- Aplikasi diperbarui:
UI: N/A, State: N/A, Logic: YES, Persistence: N/A, Runtime: REAL, Status: IMPLEMENTED (via AppChangeReceiver & AppRepository)

- Icon cache:
UI: N/A, State: N/A, Logic: YES, Persistence: N/A, Runtime: REAL, Status: IMPLEMENTED (via IconCache LruCache)

- Icon loading:
UI: N/A, State: N/A, Logic: YES, Persistence: N/A, Runtime: REAL, Status: IMPLEMENTED (via IconLoader)

- Package change receiver:
UI: N/A, State: N/A, Logic: YES, Persistence: N/A, Runtime: REAL, Status: IMPLEMENTED (via AppChangeReceiver)
