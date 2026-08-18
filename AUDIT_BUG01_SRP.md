# AUDIT BUG-01 SRP (READ-ONLY)

## 1. Executive Summary
Audit terhadap komponen inti pengolahan data aplikasi (`AppRepository`) membuktikan adanya pencampuran tanggung jawab (Single Responsibility Principle Violation) antara manajemen _state list_ aplikasi (Memori), pengunduhan data aplikasi dari Framework OS (`LauncherApps` IPC), serta abstraksi konkurensi (Mutex/Dispatcher). Namun, arsitektur yang digunakan masih memastikan kekebalan (*immutability*) data, dan penguncian mutex (`addActivityLocked`) berjalan aman dan menghindari duplikasi status. Solusi pemisahan peran disarankan ke dalam *Data Source* (Framework OS) dan *State Holder* (Memory/Model), tanpa perlu *over-engineering* seperti penggunaan Database (Room) ataupun Flow.

## 2. Files Audited
- `app/src/main/java/com/silauncer/cepat/apps/AppRepository.kt`
- `app/src/main/java/com/silauncer/cepat/launcher/LauncherAppController.kt`
- `app/src/main/java/com/silauncer/cepat/apps/AppChangeReceiver.kt`
- `app/src/main/java/com/silauncer/cepat/launcher/LauncherActivity.kt`

## 3. Dependency Graph
```text
LauncherActivity (UI / Lifecycle)
  └── LauncherAppController (Business Logic / Orchestrator)
        ├── LauncherPreferences (Settings)
        └── AppRepository (State Holder & Data Source)
              └── LauncherApps / OS Framework (IPC Access)
```

## 4. Actual Call Graph
**Skenario: Package Event**
`OS package event`
  → `AppChangeReceiver.onReceive()`
  → `LauncherActivity` (callback closure)
  → `LauncherAppController.handlePackageEvent()`
  → `AppRepository.addPackage() / removePackage() / updatePackage()`
  → `Android Framework (LauncherApps.getActivityList)`

**Skenario: Cold Start**
`LauncherActivity.onCreate()`
  → `LauncherActivity.loadAppsInitialUI()`
  → `LauncherAppController.loadAppsInitial()`
  → `AppRepository.loadInitialApps()`
  → `Android Framework (LauncherApps.getActivityList)`
  → `AppRepository.apps` (dimasukkan ke memori)
  → `AppSorter` (Di Controller)
  → `LauncherActivity.refreshAppsUI()` (kembali ke UI)

## 5. Threading Call Graph
```text
[MAIN] LauncherActivity.appChangeReceiver closure
   ↓
[MAIN] lifecycleScope.launch
   ↓
[MAIN -> SUSPEND] LauncherAppController.handlePackageEvent()
   ↓
[MAIN -> SUSPEND] AppRepository.addPackage(packageName)
   ↓
[IO] withContext(Dispatchers.IO) 
   ↓
[IO] launcherApps.getActivityList(packageName) (OS IPC)
   ↓
[IO] mutex.withLock { apps.add(...) }
   ↓
[MAIN] LauncherActivity.refreshAppsUI
```

## 6. State Ownership
- **Pemilik**: `AppRepository` (memiliki `private val apps = ArrayList<AppInfo>()`).
- **Penulis**: Hanya `AppRepository` melalui `addActivityLocked` dan `removePackage`.
- **Pembaca**: Caller membaca lewat `getApps()` / `loadInitialApps()`.
- **Sifat Data (Immutability)**: List yang dikembalikan adalah **COPY** murni (`apps.toList()`). Caller (Controller/Activity) TIDAK BISA memodifikasi _state internal_ (kecuali menembak ulang event add/remove). **PASS (Aman)**.

## 7. Concurrency Audit
- `loadInitialApps()` me-reset isi cache dengan `apps.clear()`.
- `addPackage()` menyisipkan aplikasi. Keduanya dilindungi `mutex.withLock`.
- *Skenario Tabrakan*: Jika `loadInitialApps()` dan `addPackage()` tereksekusi pada frame yang sama, Mutex akan mem-blokir salah satu, lalu menjalankannya secara sekuensial. 
- *Jaminan Unik*: Logika periksa duplikat (`apps.any { it.componentName == component }`) di dalam `addActivityLocked()` memastikan tak akan pernah ada entri ganda, meskipun skenario race menumpuk permintaan *add*. **PASS (Sangat aman dari ConcurrentModificationException & Duplikat)**.

## 8. Dispatcher Audit
- Pemanggilan `withContext(Dispatchers.IO)` dilekatkan secara langsung (*hardcoded*) di badan `AppRepository`. 
- **Pertanyaan**: Apakah IPC Android Framework (query aplikasi terpasang) membutuhkan IO Thread? *Jawaban*: Ya. Disk query OS sangat berat.
- **Masalah Desain**: `AppRepository` memutuskan sendiri `Dispatcher` nya. Dalam pedoman *Clean Architecture/SRP* modern, ini merupakan minor SRP violation karena merangkap tugas eksekutor dan state-holding.

## 9. Android Framework Data Access
- Framework API yang dipakai: `LauncherApps.getActivityList()`
- Sesuai dengan abstraksi arsitektur, tugas menarik data dari `LauncherApps` (OS) seyogyanya merupakan delegasi dari komponen **Data Source**, bukan bercampur di dalam *State Model/Cache List* (Repository).

## 10. Package Event Audit
1. `ACTION_PACKAGE_ADDED`: (Caller `AppChangeReceiver`) -> Memanggil `addPackage` -> Dispatcher.IO -> `LauncherApps` API IPC -> Mutex lock -> Add -> Sort UI. (Aman).
2. `ACTION_PACKAGE_REMOVED`: (Caller `AppChangeReceiver`) -> Memanggil `removePackage`. TIDAK MENGGUNAKAN Dispatcher.IO karena tidak perlu IPC (hanya menghapus elemen list di memory). (Aman & Efisien).
3. `ACTION_PACKAGE_REPLACED/CHANGED`: -> `updatePackage` -> menghapus dan menambah kembali. (Aman).
*Kekurangan*: OS mungkin memancarkan broadcast tambahan untuk *package changed* saat komponen tidak berubah. Namun *DiffUtil* RecyclerView & Mutex mengabaikannya.

## 11. Initial Load Audit
- List awal dibangun dari `LauncherApps.getActivityList(null)` -> IPC di IO Thread -> UI tidak tertahan. Loading bisa dilakukan 2x jika lifecycle Activity *restart*, namun *refresh UI* dan state `clear()` selalu rapi di dalam Mutex. (Aman).

## 12. Data Immutability Audit
- *Caller* (`LauncherAppController`) menerima `List<AppInfo>` hasil pemanggilan fungsi `.toList()` (Deep shallow-copy).
- *Caller* tidak memegang instans `ArrayList` asli (*mutable reference*) milik Repository. (Sangat Aman).

## 13. SRP Analysis
| Responsibility | Class sekarang | Alasan berubah | SRP |
|---|---|---|---|
| OS application query | AppRepository | API OS (LauncherApps) berubah/deprecated | VIOLATION |
| Memory state / List apps | AppRepository | Struktur data cache (ArrayList -> Map) | VIOLATION |
| Concurrency (Mutex) | AppRepository | Threading requirement berubah | VIOLATION |
| Dispatcher (IO) | AppRepository | Testing requirement / Threading pool | VIOLATION |
| Package mutation (Add/Remove)| AppRepository | Aturan filtering baru | VIOLATION |

**Kesimpulan SRP**: `AppRepository` adalah representasi `God Class` berukuran kecil (karena ini launcher ringan) yang memikul urusan komunikasi *Operating System*, *Concurrency*, dan penyimpanan *In-Memory Database*.

## 14. AOSP Comparison
- **AOSP**: Konsep `LoaderTask` (Worker/Query OS) -> `AllAppsList` (Memory State) -> UI (Adapter).
- **Aplikasi Saat Ini**: OS Query + Memory State dicampur di dalam `AppRepository`. 
- **Rekomendasi**: Tidak perlu menduplikasi ruwetnya struktur `LoaderTask` / `Model` (karena aplikasi ini tidak memakai SQLite backend seperti Workspace AOSP). Kita hanya perlu memecah *AppRepository* menjadi dua kelas.

## 15. Confirmed Bugs
- **[SRP VIOLATION] AppRepository Overload**: Satu kelas mencampur aduk peran *Data Source* (IPC query OS) dan *Memory Cache State Holder* (List holding dan Mutex protection).

## 16. Potential Issues
- *Hardcoded Dispatcher*: `withContext(Dispatchers.IO)` dalam konstruksi langsung menyulitkan Testing jika unit-test murni JVM hendak dibuat (*not critical for current scope*).

## 17. False Positives
- *Concurrency Crash / Race Condition*: Tidak terjadi. Mutex lock dan cek duplikat di `addActivityLocked` sudah dirancang tahan peluru.
- *Blocking UI*: Tidak terjadi. Eksekusi berat di-*dispatch* keluar dari Main Thread.

## 18. Severity
- **LOW-MEDIUM (Technical/Architectural Debt)**. Arsitektur berisiko jika di-*scale* lebih besar, tetapi dari segi *correctness* dan *safety* runtime sekarang ini sangat tangguh (tidak menyebabkan *crash*).

## 19. Proposed Refactor Design
Memecah `AppRepository` menjadi dua kelas sederhana (Tanpa Flow / Room):
1. **`AppDataSource`**: Murni abstraksi untuk mengambil daftar `LauncherActivityInfo` dari Framework Android (`LauncherApps`). Di sinilah penetapan `Dispatchers.IO` disarangkan.
2. **`AppStateHolder`** (Bisa dinamakan ulang dari *AppRepository* saat ini): Murni memegang list in-memory `apps` dan mengunci status dengan Mutex. Ia tidak tahu menahu mengenai `LauncherApps`.
3. **Fasad / Use Case** (`LauncherAppController`): Tetap bertindak sebagai pihak yang mengoordinasikan *Data Source* (Ambil data dari OS) dan menyerahkannya untuk disimpan ke *State Holder*.

## 20. Dependency Direction
`LauncherActivity` -> `LauncherAppController` -> (`AppDataSource` + `AppStateHolder`)

## 21. Regression Risks
- BUG-02 sudah ditangkal di `AppAdapter`/`IconLoader`, perombakan API *State* ini berisiko menengah menyebabkan mis-komunikasi pengurutan daftar jika data yang di *emit* oleh `StateHolder` tidak bersih (*immutable*). Namun jika tipe kembalian dijamin `toList()`, akan 100% aman.

## 22. Test Plan
- *Initial load* saat 0 aplikasi (Mock DataSource).
- *Package Event Race*: Tambahkan "Package_X" dan "Hapus Package_X" secara simultan melalui Coroutine delay buatan, pastikan *StateHolder* stabil.
- Validasi kembalian `.toList()` memastikan referensi *caller* tak bisa merusak susunan memori.

## 23. Files Expected to Change
- `app/src/main/java/com/silauncer/cepat/apps/AppRepository.kt` (Refactor menjadi state holder / dipecah ke datasource).
- `app/src/main/java/com/silauncer/cepat/apps/AppDataSource.kt` (File baru, abstraksi murni OS LauncherApps).
- `app/src/main/java/com/silauncer/cepat/launcher/LauncherAppController.kt` (Injeksi dependensi baru).
- `app/src/main/java/com/silauncer/cepat/launcher/LauncherActivity.kt` (Membuat instance *Data Source* dan *State Holder*).

## 24. Final Recommendation
Gugurkan status `AppRepository` yang multifungsi. Ekstrak kode pemanggilan `LauncherApps.getActivityList` (beserta `Dispatchers.IO`) menuju sebuah class `AppDataSource`. Modifikasi class yang ada sebagai `AppStateHolder` (tanpa OS Context) yang murni hanya mengelola `List` dengan `Mutex`.
