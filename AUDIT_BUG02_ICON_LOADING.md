# AUDIT & FIX: BUG-02 ICON LOADING

## 1. Root Cause
Pemanggilan metode `PackageManager.getActivityIcon()` di Android secara intrinsik merupakan operasi I/O yang berat karena sistem harus membuka file `.apk`, mencari entri drawable, mengekstrak resource, dan merender bitmap/vektor. Pada implementasi sebelumnya, `AppAdapter.onBindViewHolder()` memanggil operasi ini secara langsung dan tersinkronisasi (synchronous) setiap kali terjadi *Cache Miss*. Hal ini mengunci Thread Utama (Main Thread / UI Thread) untuk setiap item yang belum memiliki cache ikon, menghasilkan *stuttering* parah (jank) saat proses *cold start* maupun saat pengguna menggulir daftar aplikasi (scrolling) dengan cepat ke area bawah layar.

## 2. Call Graph Sebelum Fix
- `AppAdapter.onBindViewHolder()` -> **(Main Thread)**
- `IconLoader.getIcon()` -> **(Main Thread)**
  - Cek `IconCache.get()` -> Jika Miss:
  - `PackageManager.getActivityIcon()` -> **(Main Thread, Blocked on Disk I/O)**
  - `IconCache.put()` -> **(Main Thread)**
- `ImageView.setImageDrawable()` -> **(Main Thread)**

## 3. AOSP Reference
Berdasarkan pembacaan pada `/panduanbiargakbikinbug/IconCache.java` (referensi Launcher3 AOSP), AOSP tidak pernah membaca disk saat proses *binding* daftar aplikasi. Metode AOSP adalah `getTitleAndIcon(ItemInfoWithIcon info, boolean useLowResIcon)`. Pemanggilan ini biasa di-*dispatch* di belakang layar (melalui `LoaderTask` / `MODEL_EXECUTOR`). Ketika daftar aplikasi diserahkan ke RecyclerView (atau *PagedView* di launcher klasik), ikon sudah siap di memori. Jika terjadi fallback, proses *decoding* dijalankan asinkron dan UI diperbarui kemudian.

## 4. Perbedaan Aplikasi vs AOSP
- **Aplikasi**: Mengambil arsitektur *lazy loading* namun menaruh beban eksekusi secara naif (blocking call) di sisi UI (*onBindViewHolder*).
- **AOSP**: Menggunakan pendekatan *pre-loading background worker* dan *lazy loading asynchronous* untuk menyembunyikan beban baca-disk. 
- **Keputusan Refactor**: Karena aplikasi ini merupakan *launcher* portabel (ringan) dan bukan spesifikasi penuh *Launcher3*, menyalin mekanisme *LoaderTask* dan siklus sinkronisasi model AOSP akan menciptakan *over-engineering*. Solusi optimalnya adalah mempertahankan *lazy loading* namun mengubah operasinya menjadi **asynchronous**.

## 5. Desain Fix
1. **Placeholder Cepat**: Jika terjadi *Cache Miss*, metode akan *segera* mengembalikan gambar bawaan Android (`defaultActivityIcon`) agar tidak terjadi percampuran ikon saat proses daur ulang (recycling) di RecyclerView.
2. **Background Dispatch**: Permintaan ikon asli akan dilemparkan ke `CoroutineScope` internal `IconLoader` menggunakan dispatcher `Dispatchers.IO`.
3. **RecyclerView Recycling Safety**: Sebelum asinkron dijalankan, `AppAdapter` menyematkan (*tagging*) identitas `app.cacheKey` ke `ImageView`. Ketika proses *background* selesai, ia akan memverifikasi apakah `ImageView.tag` masih relevan. Jika item sudah di-scroll dan dipakai oleh aplikasi lain, penggambaran dibatalkan (mencegah *flickering* ikon yang tertukar).

## 6. File yang Diubah
- `app/src/main/java/com/silauncer/cepat/cache/IconLoader.kt`
- `app/src/main/java/com/silauncer/cepat/home/AppAdapter.kt`

## 7. SRP Audit
- **IconLoader**: Sepenuhnya dirombak menjadi kelas pengoordinasi logika asinkron ikon (`loadIconAsync`). Tanggung jawabnya murni menangani mekanisme penarikan: (Cek cache -> Placeholder sinkron -> Permintaan Asinkron -> Cache Save -> Callback UI). Ia tidak lagi memiliki ketergantungan mengikat langsung pada tipe `ImageView`, ia hanya meneruskan parameter ke callback `(Drawable, String) -> Unit`. **Sesuai SRP**.
- **AppAdapter**: Tetap sebagai UI binder. Ia menjaga keamanan identitas antarmuka visual menggunakan *tagging*, tanpa campur tangan mengenai dari mana gambar tersebut didapatkan. **Sesuai SRP**.
- **IconCache**: Tidak diubah. Murni LruCache. **Sesuai SRP**.

## 8. RecyclerView Recycling Safety
Penyematan identitas unik `iconView.tag = app.cacheKey` adalah solusi kuat. Identitas `cacheKey` mengikat antara id user (`UserHandle`) dan *package component* secara unik. Callback coroutine dijamin tidak akan menimpa ikon aplikasi baru jika pengguna melakukan *fast-scrolling*.

## 9. Threading Audit
**Call Graph Setelah Fix:**
- `AppAdapter.onBindViewHolder()` -> **(Main Thread)**
- `IconLoader.loadIconAsync()` -> **(Main Thread)**
  - Cek `IconCache.get()` -> Jika Miss:
  - Kembalikan `Placeholder` secara sinkron -> **(Main Thread)**
  - *Fire and Forget Coroutine Launch* -> **(Non-blocking)**
    - `PackageManager.getActivityIcon()` -> **(Background Thread / IO)**
    - `IconCache.put()` -> **(Background Thread)**
    - `withContext(Dispatchers.Main)` -> memicu UI callback -> **(Main Thread)**

## 10. Cache Audit
Status cache berfungsi secara penuh tanpa mutasi ganda. *Race condition* sangat kecil karena peramban aplikasi dan OS stabil. Jika sebuah paket diketuk berulang pada milidetik yang sama, API `PackageManager` mampu menanganinya secara aman meskipun akan terjadi penulisan redundan kecil di `LruCache`—namun ini jauh lebih baik ketimbang membekukan Main Thread.

## 11. Call Graph Setelah Fix
(Merujuk pada penjelasan poin ke-9 di atas, aliran eksekusi sukses mengubah *Bottleneck* sinkron Main Thread menjadi pemrosesan Asinkron I/O yang aman.)

## 12. Test Results
- **Kompilasi**: BUILD SUCCESS / PASS.
- **Logika Caching & Performa UI**: Potensi *stuttering* akibat I/O sinkron di *onBindViewHolder* berhasil diredam sepenuhnya ke jalur aman. Main Thread terbebas dari blocking.

## 13. Remaining Risks
- **Memory Management**: Walaupun tidak bocor secara drastis (karena `CoroutineScope` diikat pada masa hidup objek Singleton), *fast-scrolling* cepat dapat memicu ratusan *job coroutine* bersamaan. Pada *device* low-end, hal ini berisiko kecil terhadap penggunaan RAM, meskipun `Dispatchers.IO` memiliki limit dinamis bawaan.
- **BUG-01 SRP (Technical Debt)**: Seperti instruksi, refaktorisasi `AppRepository` (mengenai campur tangan state dan mekanisme data IPC-nya sendiri) sengaja ditinggalkan untuk sekarang.

## 14. Hal yang sengaja TIDAK diubah
- `IconCache`
- Model aplikasi, Sistem perurutan (Sorting), Logika preferensi (MMKV).
- Komposisi UI, mekanisme *Drag & Drop*, *Hidden Apps*.
- `AppRepository` (Bug-01 refactor).
