# AUDIT AOSP IMPLEMENTATION: com.silauncer.cepat

## 1. Executive Summary
Audit komprehensif ini menganalisis arsitektur, data flow, dan implementasi backend dari "com.silauncer.cepat". Audit dilakukan tanpa mengubah source code aplikasi. Hasilnya menunjukkan bahwa meskipun aplikasi dapat dikompilasi dan berjalan (build pass), terdapat pelanggaran threading serius terkait pemanggilan I/O disk dan Inter-Process Communication (IPC) pada Main Thread, terutama pada sistem caching ikon dan penanganan event instalasi paket. Selain itu, ditemukan beberapa inefisiensi arsitektural yang berbeda dengan standar referensi AOSP (`/panduanbiargakbikinbug`).

## 2. Project Inventory
- **Kotlin Classes**: 
  - `LauncherActivity`, `LauncherAppController`, `LauncherApplication` (Launcher core)
  - `AppActionHandler`, `AppChangeReceiver`, `AppInfo`, `AppRepository`, `AppSorter` (Backend/App management)
  - `AppAdapter`, `OverScroll` (UI/Grid)
  - `IconCache`, `IconLoader` (Icon management)
  - `SettingsActivity`, `SettingsUi`, `HiddenAppsDialog` (Settings)
  - `LauncherPreferences` (Storage/Persistence)
- **XML/Resources**: Layouts (`activity_launcher.xml`, `item_app.xml`), drawables, mipmap icons, values/strings, themes.
- **Manifest**: `AndroidManifest.xml`
- **Gradle**: `build.gradle.kts` (App & Project), `settings.gradle.kts`, `gradle.properties`
- **Dependencies**: MMKV, AndroidX RecyclerView, AndroidX Lifecycle, Coroutines, AppCompat.

## 3. Actual Architecture
Arsitektur aplikasi merupakan pola MVC/MVP sederhana (bukan Clean Architecture murni atau MVVM).
- **View**: `LauncherActivity`, `AppAdapter`, `SettingsActivity`
- **Controller/Orchestrator**: `LauncherAppController` mengatur logika refresh dan mem-filter data dari Repository ke UI. `AppActionHandler` menangani interaksi pengguna.
- **Repository/Model**: `AppRepository` (Source of Truth untuk daftar aplikasi), `AppInfo` (Entitas aplikasi).
- **Storage**: `IconCache` (Memory), `LauncherPreferences` (Persistent Key-Value).

## 4. Data Flow
- **Initialization**: `LauncherActivity.onCreate()` -> `loadAppsInitialUI()` -> `LauncherAppController.loadAppsInitial()` -> (Background Thread) `AppRepository.loadInitialApps()` -> `AppSorter` -> UI (Adapter).
- **Package Change**: OS Intent -> `AppChangeReceiver.onReceive()` -> (Main Thread) `LauncherAppController.handlePackageEvent()` -> `AppRepository.addPackage/updatePackage` -> `LauncherApps.getActivityList()` (IPC) -> Invalidate `IconCache` -> `refreshAppsUI()`.
- **Icon Loading**: `AppAdapter.onBindViewHolder()` -> (Main Thread) `IconLoader.getIcon()` -> `PackageManager.getActivityIcon()` (Disk I/O) -> `IconCache.put()`.
- **Settings**: `SettingsActivity` -> Read/Write to `LauncherPreferences` (MMKV) -> Reset Layout -> `recreate()` -> `LauncherActivity.onResume()` applies new settings.

## 5. Backend Audit
- **App Discovery**: Menggunakan `LauncherApps.getActivityList()`. Cukup standar, tetapi hanya membaca user profile saat ini (`Process.myUserHandle()`), tidak mengakomodasi Work Profile.
- **App State**: Sinkronisasi dilakukan dengan blok `@Synchronized` di `AppRepository`. Aman secara thread, namun rawan *deadlock* atau freeze jika pemanggilnya dari Main Thread (seperti saat package event).
- **Icon System**: Implementasi yang paling bermasalah. Dekoding Drawable dari APK via PackageManager dilakukan secara sinkron pada UI thread.
- **Sorting/Hidden Apps**: Berjalan baik menggunakan ekstensi collection Kotlin. Data hidden apps menggunakan set package name.
- **Preferences**: MMKV sangat cepat, pembacaan preferensi pada Main Thread tidak menjadi masalah *bottleneck*.

## 6. AOSP Comparison
| Fitur | Implementasi Aplikasi | Implementasi AOSP | Perbedaan | Dampak | Prioritas |
|---|---|---|---|---|---|
| **App Loading** | `AppRepository.loadInitialApps()` (Coroutines Dispatchers.IO) | `LoaderTask.java` & `AllAppsList.java` (Worker thread Executor) | Hampir setara, sama-sama di background. | Minimal | Low |
| **Package Events** | `AppChangeReceiver` -> `AppRepository` (Sinkron di Main Thread) | `CacheDataUpdatedTask.java` (Dieksekusi di `MODEL_EXECUTOR` background) | Aplikasi melakukan IPC (LauncherApps) di Main Thread. | UI Freeze saat instalasi | **CRITICAL** |
| **Icon Caching** | Sinkron via `IconLoader.getIcon()` pada `onBindViewHolder` | `IconCache.java` (Asinkron/Preload dengan `getTitleAndIcon()`) | Aplikasi membaca APK/Disk di UI Thread saat list di-*scroll*. | Stuttering / Jank parah | **CRITICAL** |
| **Persistence** | MMKV string / primitives | SQLite (`LauncherDbUtils.java`) | Aplikasi jauh lebih sederhana. Sesuai desain *lightweight*. | Minimal | Low |
| **Drag & Drop** | `ItemTouchHelper` pada RecyclerView | Kustom `DragSource.java`, `DropTarget.java` | Aplikasi memanfaatkan behavior standar list. | Cukup | Low |

## 7. Critical Bugs
**BUG-01: Main Thread IPC pada Package Event**
- **Severity**: CRITICAL
- **Class**: `LauncherAppController.kt`, `AppRepository.kt`
- **Method**: `handlePackageEvent`, `addPackage`
- **Behavior Sekarang**: Saat receiver menangkap intent (install/uninstall), `LauncherApps.getActivityList()` dieksekusi secara sinkron di Main Thread.
- **Expected Behavior**: Proses query informasi paket ke sistem operasi harus dilakukan di background thread.
- **Referensi AOSP**: `CacheDataUpdatedTask.java` (menangani perubahan paket di luar UI thread).
- **Dampak**: Jika sistem sibuk saat aplikasi diinstal/diupdate, launcher akan mengalami ANR (Application Not Responding).
- **Rekomendasi**: Bungkus `handlePackageEvent` di dalam Coroutine `Dispatchers.IO` atau buat *worker thread*.

**BUG-02: Disk I/O pada UI Thread untuk Icon Loading**
- **Severity**: CRITICAL
- **Class**: `IconLoader.kt`, `AppAdapter.kt`
- **Method**: `getIcon`, `onBindViewHolder`
- **Behavior Sekarang**: Jika ikon tidak ada di cache, `PackageManager.getActivityIcon()` dipanggil pada UI thread.
- **Expected Behavior**: Ikon diambil secara asinkron; saat loading, tampilkan placeholder, baru update imageView setelah selesai.
- **Referensi AOSP**: `IconCache.java` (pemrosesan ikon dilakukan secara asinkron atau dimuat saat *LoaderTask*).
- **Dampak**: *Scroll stuttering* ekstrem karena dekoding aset `.apk` menahan proses *rendering frame* 60fps/120fps RecyclerView.
- **Rekomendasi**: Ubah `IconLoader` agar mereturn `Drawable` placeholder segera, lalu lakukan fetching background dan post hasilnya via callback/Coroutine.

## 8. High Bugs
Tidak ada (Bug utama terpusat di kategori Critical terkait I/O dan Main Thread).

## 9. Medium Bugs
**BUG-03: Redundant AppRepository Initialization in Settings**
- **Severity**: MEDIUM
- **Class**: `HiddenAppsDialog.kt`
- **Method**: `show`
- **Behavior Sekarang**: Instansiasi `AppRepository` baru dan memanggil `loadInitialApps()` untuk mengisi *dialog list*.
- **Expected Behavior**: `SettingsActivity` tidak perlu men-scan ulang seluruh perangkat melalui `LauncherApps`.
- **Dampak**: Buang-buang resource sistem CPU.

## 10. Low Bugs
**BUG-04: Drag & Drop vs Long Press Conflict Mechanism**
- **Severity**: LOW
- **Class**: `LauncherActivity.kt`
- **Method**: `ItemTouchHelper.SimpleCallback.clearView`
- **Behavior Sekarang**: Membedakan "Drag" dan "Long press untuk Menu" hanya berdasarkan apakah posisi awal = posisi akhir.
- **Dampak**: Jika pengguna sedikit saja menggeser jari saat long press (gemetar), menu aplikasi gagal muncul.

## 11. Missing/Incomplete Logic
- **Work Profile Support**: Aplikasi saat ini menggunakan `Process.myUserHandle()` statis. AOSP menggunakan `UserManager.getUserProfiles()` untuk mendukung aplikasi dari Work Profile.
- **Icon Persistence**: Jika aplikasi dimatikan (force close / low memory), cache memori terhapus. Saat dibuka, semua ikon harus di-load ulang. AOSP menggunakan cache SQLite/Files. (Mungkin tidak wajib untuk aplikasi *lightweight*, tetapi relevan).

## 12. Performance Findings
- **Icon Loading bottleneck**: Penyebab utama kelambatan.
- **Full Refresh on Package Change**: Aplikasi tidak secara presisi menyisipkan item baru (hanya mengandalkan `DiffUtil` setelah mem-filter ulang seluruh list via `refreshApps`). Masih tergolong cepat berkat algoritma ListAdapter, tapi tidak seoptimal AOSP.

## 13. Lifecycle Findings
- `AppChangeReceiver` didaftarkan (register) secara dinamis di `onCreate` dan di-unregister di `onDestroy`. Ini berarti *broadcast* yang terjadi saat launcher di-kill (misal karena low memory saat update aplikasi besar) akan terlewat. Namun, ketika hidup kembali, `loadAppsInitialUI` melakukan full scan. Ini sudah aman dan sesuai.

## 14. Persistence Findings
- Mekanisme `LauncherPreferences` (MMKV) aman, instan, thread-safe, dan stabil.

## 15. Package Event Findings
- Sesuai dengan referensi, sudah menangani `ACTION_PACKAGE_ADDED`, `REMOVED`, `CHANGED`, `REPLACED`. Masalah murni di pemrosesan pada thread yang salah.

## 16. Icon/Cache Findings
- Memori cache menggunakan `LruCache` berkapasitas 150 item. Tergolong wajar. Pengaturan pembersihan (`removePackage`) sudah akurat berbasis pola cache key (`UserHandle:ComponentName`).

## 17. Sorting/Ordering Findings
- Stabil. Penanganan urutan kustom (*Custom Order*) yang tertinggal karena aplikasi telah di-uninstall diabaikan dengan aman menggunakan fallback index kustom.

## 18. Hidden Apps Findings
- Sistem identifikasi *Hidden apps* menggunakan `packageName`. Jika ada dua *launcher activity* dalam satu paket, keduanya akan disembunyikan. Hal ini umumnya wajar, tapi AOSP lebih spesifik menggunakan `ComponentName` (meskipun fitur *hidden app* murni tidak ada di AOSP standar).

## 19. Drag & Drop Findings
- Fungsional, menggunakan pergerakan linear RecyclerView standar. Bukan tipe grid workspace bebas layaknya AOSP.

## 20. Settings Findings
- Terstruktur rapi. *Lifecycle handling* dialog dengan coroutine sudah terlindungi dari crash akibat *Activity destroying state*.

## 21. AOSP Reference Map
- `CacheDataUpdatedTask.java` -> Relevan untuk refactor Package Event (BUG-01).
- `IconCache.java` & `LoaderTask.java` -> Relevan untuk refactor Asynchronous Icon Loading (BUG-02).

## 22. Dependency Graph of Issues
Memperbaiki BUG-02 (Icon Loading) memiliki dampak tertinggi pada performa (Scroll). Memperbaiki BUG-01 (Package Event) menyelamatkan dari *Crash/ANR*. Keduanya bisa dikerjakan terpisah secara paralel.

## 23. Prioritized Fix Roadmap
**Phase 1: Critical Correctness (I/O di Main Thread)**
1. **Perbaikan IconLoader** (BUG-02): Refactor `IconLoader` dan `AppAdapter` agar menggunakan pola *asynchronous loading* (Coroutines / background thread) saat mengambil `Drawable` dari `PackageManager`.
2. **Perbaikan Package Events** (BUG-01): Membungkus pemanggilan `appController.handlePackageEvent` di dalam coroutine `Dispatchers.IO` atau mengubah metode `addPackage/updatePackage/removePackage` di `AppRepository` agar dieksekusi asinkron.

**Phase 2: Backend Consistency**
3. **Optimasi Hidden Apps Dialog** (BUG-03): Modifikasi `HiddenAppsDialog` agar dapat menerima *list* aplikasi dari memori `LauncherActivity` yang sudah ada alih-alih me-load ulang via `LauncherApps`.

**Phase 3: Interaction & Polish**
4. **Perbaikan Sensitivitas Menu** (BUG-04): Mengimplementasikan *GestureDetector* khusus atau pendekatan *LongClickListener* murni pada `ViewHolder` tanpa menunggangi `ItemTouchHelper` untuk memunculkan App Menu.

## 24. Validation Plan
- Pantau *Logcat* StrictMode untuk memastikan tidak ada pesan `DiskReadViolation` atau `DiskWriteViolation` di UI Thread.
- Tes *smoothness* scrolling menggunakan *Profile GPU Rendering* (harus konsisten di bawah garis 16ms).
- Install/Uninstall aplikasi via *ADB* selagi launcher di posisi *foreground* dan pastikan tidak ada efek *Freeze*.

## 25. Files Requiring Changes
Berdasarkan *roadmap*:
- `IconLoader.kt`
- `AppAdapter.kt`
- `LauncherActivity.kt`
- `LauncherAppController.kt`
- `HiddenAppsDialog.kt`

## AUDIT RULE
"/panduanbiargakbikinbug" adalah reference source READ-ONLY.
Tidak ada file AOSP yang dimodifikasi.
Tidak membuat implementasi berdasarkan asumsi.
Tidak melakukan refactor/perbaikan selama audit.
Semua informasi berasal dari source code aktual proyek.

## 26. BUG-01 VERIFICATION
Berdasarkan refactor terbaru, issue utama (IPC Main Thread) telah ditangani dengan memindahkan pemanggilan I/O ke dalam `Dispatchers.IO`. Namun, audit menemukan beberapa detail implementasi yang masih bisa diperdebatkan secara SRP, meskipun status bloking Main Thread (*ANR risk*) sudah terselesaikan (FALSE POSITIVE post-refactor / SOLVED).
- **Status (ANR/UI Freeze)**: NOT CONFIRMED (Telah diperbaiki).
- **Status (SRP Violation)**: CONFIRMED (Akan dibahas pada sesi SRP).

## 27. BUG-01 THREADING CALL GRAPH
Berdasarkan source code aktual setelah refactor:
1. `AppChangeReceiver.onReceive()` -> OS memicu event. Berjalan di **Main Thread**.
2. Callback di `LauncherActivity` -> Mengeksekusi `lifecycleScope.launch { ... }`. Berjalan di **Main Thread** (default dispatcher untuk lifecycleScope).
3. `appController.handlePackageEvent()` -> Sebuah fungsi `suspend`. Dipanggil di **Main Thread**.
4. Tergantung *action*:
   - Jika `ACTION_PACKAGE_ADDED`: Memanggil `appRepository.addPackage(packageName, user)`.
     - Internal `addPackage` menggunakan `withContext(Dispatchers.IO)`. -> Pindah ke **Background Thread (IO)**.
     - Terjadi **IPC / Disk I/O** (pemanggilan `launcherApps.getActivityList`).
     - Hasil ditambahkan ke `apps` list di dalam `mutex.withLock`. -> Aman.
     - Eksekusi kembali (resume) ke **Main Thread**.
   - Jika `ACTION_PACKAGE_REMOVED`: Memanggil `appRepository.removePackage(packageName, user)`.
     - Fungsi ini hanya membungkus `apps.removeAll` dengan `mutex.withLock`. TIDAK ADA `withContext(Dispatchers.IO)`. -> Berjalan murni di **Main Thread**.
     - Memanggil `IconCache.removePackage(packageName)` -> Berjalan di **Main Thread**. (Ini adalah mutasi memory map biasa, tidak ada I/O, sangat cepat).
5. `refreshAppsUI()` -> Berjalan di **Main Thread**. Menyerahkan state list terbaru ke adapter.

## 28. BUG-01 SRP RE-AUDIT
- **AppChangeReceiver**: Responsibility: Hanya meneruskan event OS. Dependency: Higher-order function. **Sesuai SRP**.
- **LauncherActivity**: Responsibility: Mengawasi UI, lifecycle, dan me-launch coroutine untuk delegasi logic. **Sesuai SRP**.
- **LauncherAppController**: Responsibility: Mengoordinasi event menjadi aksi nyata di Repository, dan invalidasi Cache. Mengembalikan `boolean` (apakah UI perlu update). **Sesuai SRP**.
- **AppRepository**: 
  - Responsibility aktual: (1) Menyimpan memory list aplikasi `apps` (State management). (2) Berkomunikasi dengan OS (Data access/IPC). (3) Mengurus `Mutex` (Concurrency). (4) Mengurus thread context switching via `withContext(Dispatchers.IO)` (Coroutine dispatching).
  - Kesimpulan SRP: **POTENTIAL SRP VIOLATION (CONFIRMED)**. Class ini bercampur antara bertindak sebagai Data Source (mengambil data dari OS) dan State Holder/Cache Memory internal.

## 29. BUG-02 ICON LOADING AUDIT
**Call Graph & Tracing (Berdasarkan source aktual `AppAdapter.kt` & `IconLoader.kt`):**
1. Saat user melakukan scroll, RecyclerView akan memanggil `AppAdapter.onBindViewHolder()`. Pemanggilan ini secara absolut terjadi di **Main Thread**.
2. `onBindViewHolder` memanggil sinkron: `IconLoader.getIcon(itemView.context, app)`.
3. Di dalam `IconLoader`:
   - Memeriksa `IconCache.get()`. (Memory LruCache, cepat).
   - *Cache Miss*: Langsung memanggil blok `try { pm.getActivityIcon(appInfo.componentName) }`.
   - Metode `PackageManager.getActivityIcon()` memaksa OS untuk membaca informasi APK dari storage disk, melakukan ekstraksi aset Drawable, dan merender bitmap/vector. Semuanya tertahan di **Main Thread**.
   - Setelah selesai, menyimpannya ke `IconCache.put()`, lalu mereturn ikon tersebut ke `AppAdapter`.
4. Selama langkah ke-3 terjadi, UI Thread sepenuhnya terkunci menunggu I/O Disk. 

**Analisis Terhadap Kondisi Real:**
- *Cold start*: Frame pertama akan *stutter* keras karena 20-30 ikon dirender serentak di Main Thread tanpa *placeholder*.
- *Scroll*: Ketika user menggulir list dengan cepat ke area *Cache Miss*, UI akan patah-patah (Jank) karena render frame drop ke bawah 60 FPS untuk memberi jalan pada operasi `PackageManager`.
- **Status Bug BUG-02**: **CONFIRMED** (Sangat Kritis).

## 30. AOSP COMPARISON
Referensi AOSP di `/panduanbiargakbikinbug`:
- **Package Event & Background Model**: AOSP menggunakan `LoaderTask` dan `CacheDataUpdatedTask` yang berjalan dalam `MODEL_EXECUTOR`. *AllAppsList* (state holder) dipisahkan dari proses worker (SRP murni).
- **Icon Loading / Cache**: Pada `IconCache.java`, AOSP memiliki metode `getTitleAndIcon(ItemInfoWithIcon info, boolean useLowResIcon)` yang dieksekusi selama fase *asynchronous pre-load* (melalui Model/LoaderTask). Saat UI di-bind (`AppAdapter`), list item telah memiliki referensi bitmap ikon (termasuk *Low-Res* icon jika scroll sangat cepat). UI thread TIDAK PERNAH memanggil `PackageManager.getActivityIcon()` secara sinkron dari adapter.

## 31. ROOT CAUSE & SEVERITY (UPDATE)
- **BUG-01 SRP**: `AppRepository` memikul beban abstraksi *threading* dan eksekusi IPC sekaligus menyimpan *state*, sehingga menabrak batas SRP murni. (Severity: MEDIUM - Arsitektural).
- **BUG-02 ICON LOADING**: Implementasi `IconLoader` yang memanggil API Disk I/O Android Framework langsung saat *binding* RecyclerView. (Severity: CRITICAL - Performa UI).

## 32. FILES INVOLVED
- `app/src/main/java/com/silauncer/cepat/cache/IconLoader.kt`
- `app/src/main/java/com/silauncer/cepat/home/AppAdapter.kt`
- `app/src/main/java/com/silauncer/cepat/apps/AppRepository.kt` (Untuk evaluasi ulang pemisahan SRP).

## 33. RECOMMENDED FIX ORDER
1. Selesaikan **BUG-02**: Ubah `IconLoader` agar segera mereturn *placeholder* jika terjadi *Cache Miss*. Tarik operasi `PackageManager.getActivityIcon()` ke dalam Coroutine background, dan berikan callback/StateFlow ke `AppAdapter` agar imageview terupdate setelah selesai dimuat.
2. Evaluasi dan refactor **BUG-01 SRP**: Pecah `AppRepository` menjadi `AppDataSource` (murni query ke OS) dan `AppStateHolder` (murni menyimpan list aplikasi dalam memori).

## 34. TEST/VALIDATION PLAN
- **BUG-02 Validation**: Gunakan 'Profile GPU Rendering' di perangkat. Garis hijau/merah di layar tidak boleh menembus batas 16ms (60fps limit) ketika *fast-scrolling* di list aplikasi.
