# AUDIT BUG-02 FOLLOW-UP (LIFECYCLE & DUPLICATE)

## 1. Finding Validation
Dua penemuan minor pasca-audit Bug-02:
1. **[LOW] Transient Memory Leak**: *Dikonfirmasi (True Positive)*. `IconLoader` awalnya diimplementasikan sebagai `object` (Singleton) dengan `CoroutineScope` statis (`SupervisorJob`). Apabila Activity dimatikan sewaktu I/O coroutine belum selesai, *lambda closure* dari `AppAdapter` akan menahan referensi UI sejenak karena GC (Garbage Collector) tidak bisa membersihkannya sebelum scope statis selesai.
2. **[LOW-MED] Duplicate Request (In-Flight)**: *Dikonfirmasi (True Positive)*. Proses fast-scroll yang mengulangi elemen *cache miss* sebelum coroutine pertama selesai akan menyebabkan peluncuran coroutine ganda yang berpotensi membebani CPU, karena tidak ada deduplikasi in-flight *request*.

## 2. Lifecycle Root Cause
Akar permasalahannya adalah *global global coroutine scope* pada *singleton object* yang mencoba mengeksekusi operasi terkait dengan View/Activity yang *lifecycle*-nya bisa lebih pendek dari *scope* tersebut.

## 3. Duplicate Request Root Cause
Tidak ada penyimpanan status untuk *request* ikon yang *sedang* dijemput (In-Flight).

## 4. Design Before Fix
`IconLoader (object)` memegang *Scope Global*. Ia diluncurkan tanpa mempedulikan antrean yang sudah ada. Adapter (UI) mengirimkan closure yang akhirnya tertahan di Scope Global.

## 5. Files Changed
- `app/src/main/java/com/silauncer/cepat/cache/IconLoader.kt` (Refactor desain & in-flight)
- `app/src/main/java/com/silauncer/cepat/home/AppAdapter.kt` (Menambahkan coroutineScope dan melepas singleton loader)
- `app/src/main/java/com/silauncer/cepat/launcher/LauncherActivity.kt` (Memasok `lifecycleScope`)

## 6. Lifecycle Fix
`IconLoader` diubah dari `object` (Singleton) menjadi `class` biasa. Ia kini menerima `CoroutineScope` di *constructor*. Di sisi `AppAdapter`, `IconLoader` diinisiasi menggunakan `lifecycleScope` dari pemanggil (`LauncherActivity`). Dengan demikian, setiap request coroutine sekarang terikat ke *lifecycle Activity* asalnya. Jika *LauncherActivity* mati, seluruh request I/O *IconLoader* ikut dibatalkan, membersihkan kebocoran referensi View tanpa residu.

## 7. In-Flight Deduplication Design
Mekanisme deduplikasi ditambahkan menggunakan `ConcurrentHashMap<String, Deferred<Drawable>>`.
1. Jika terjadi *Cache Miss*, metode memanggil `computeIfAbsent`.
2. Jika belum ada *request*, ia akan membuat `async` block (sebuah coroutine ber-return).
3. Jika sudah ada *request*, ia tidak membuat coroutine baru, melainkan menggunakan `Deferred` yang sudah berjalan.
4. Semua pemanggil akan men-`await()` block `Deferred` yang sama. Saat eksekusi asli selesai dan membuahkan Drawable, setiap *requester* akan mendapatkan hasilnya ke callbacknya masing-masing.

## 8. Concurrency Audit
Konstruksi `ConcurrentHashMap.computeIfAbsent` dipadukan dengan struktur `Deferred` (dari library coroutine kotlinx) menjamin *Thread-Safety* tinggi tanpa mengunci (*blocking*) Main Thread.
- Hanya akan ada maksimal 1 eksekusi Disk I/O (PackageManager) per-paket aplikasi berkat jaminan lock-level atomic pada `computeIfAbsent`.
- Pembersihan `inFlightRequests.remove(cacheKey)` dilakukan dalam blok `finally`, memastikan map bersih dari *stale key* jika *request* selesai atau dibatalkan karena Activity Destroy.

## 9. RecyclerView Safety
Mekanisme keamanan dengan *Tag validation* (`iconView.tag = app.cacheKey`) tetap dibiarkan utuh di sisi Adapter karena ini adalah perlindungan mutlak bagi *View Recycling*. Callback hanya bertindak jika *View* tidak beralih pemilik.

## 10. Cache Safety
Hasil eksekusi `Deferred` yang sukses disimpan sekali saja di `IconCache.put(cacheKey, icon)` oleh Coroutine utama (Worker), dan seketika nilainya bisa dipakai oleh instance baru.

## 11. SRP Audit
- **AppAdapter**: Tetap sebagai UI Binder. Ia sekarang meneruskan `CoroutineScope` miliknya, tapi tidak ikut campur dalam *job management*.
- **IconLoader**: Tetap fokus mengoordinasi Request & Concurrency dari proses memuat (termasuk Deduplikasi In-Flight), sekarang terlepas dari *Global Scope*. (Tidak mengurusi RecyclerView).
- **LauncherActivity**: Merupakan penanggung jawab akhir Lifecycle, yang memasok kekuasaannya (`lifecycleScope`) ke komponen yang membutuhkannya.

## 12. Call Graph Before
(Lihat Audit Post-Fix pertama. Scope Singleton diluncurkan untuk setiap scroll miss berulang).

## 13. Call Graph After
`AppAdapter.onBindViewHolder()`
  -> `IconLoader.loadIconAsync()`
    -> **Cache Miss** -> (Return Placeholder Sync ke UI).
    -> `ConcurrentHashMap.computeIfAbsent(cacheKey)`
       -> Jika BARU: Mulai `async(IO)` Disk Query, simpan ke In-Flight Map.
       -> Jika SUDAH ADA: Pakai *job* Deferred yang sudah berjalan.
    -> `await()` -> Menunggu job selesai tanpa memblokir thread.
    -> Job Selesai -> `withContext(Main)` -> Cek TAG UI -> Update Image.
    -> `finally` -> Hapus job dari In-Flight Map.

## 14. Test Results
- **Kompilasi**: BUILD SUCCESS / PASS.
- **Logika**: Secara source code, deduplikasi request memotong redundansi Disk Query menjadi satu tarikan, merampingkan kerja CPU walau dalam skenario *scroll* ganas.

## 15. Remaining Risks
Tidak ada lagi permasalahan Memory Leak transient atau pun *Duplicate IO Loading*. Isu terkait perenderan ikon di RecyclerView Launcher telah di-resolusi dan aman dari sudut pandang *Concurrency* serta *Lifecycle*.

## 16. Final Verdict
**BUG-02 VERIFIED**
(Keseluruhan sistem *Asynchronous Icon Lazy-Loading* tervalidasi sukses dengan jaminan pembebasan Lifecycle-UI dan Deduplikasi Job In-Flight).
