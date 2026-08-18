# AUDIT DRAG & PACKAGE CONCURRENCY — FORENSIC REPORT
**Project:** `com.silauncer.cepat` (Silauncer)  
**Target:** Concurrency & State Interaction between `ItemTouchHelper` Drag Gesture and Package Broadcasts (`PACKAGE_ADDED`, `PACKAGE_REMOVED`, `PACKAGE_CHANGED`)  
**Audit Mode:** STRICT READ-ONLY DEEP FORENSIC ANALYSIS  
**Date:** 2026-08-17  

---

## 1. Executive Verdict

### **VERDICT: UX INTERRUPTION ONLY (WITH ARCHITECTURAL DEBT)**

- **Crash & Memory Safety:** **100% SAFE** (Seluruh mutasi `AppAdapter.apps` dan `DiffUtil.dispatchUpdatesTo` diserialisasi secara aman pada Android Main Looper).
- **Data & MMKV Integrity:** **100% SAFE** (Tidak terjadi korupsi string, tidak ada duplikasi entri, dan tidak ada komponen *stale/ghost* yang tersimpan ke MMKV).
- **Behavioral Outcome:** Ketika broadcast paket OS tiba tepat saat jari pengguna sedang menahan dan menggeser ikon (*in-flight drag*), `submitList()` mereset adapter ke state sistem terbaru. Akibatnya, pergeseran visual yang belum di-release oleh pengguna **dibatalkan/dibuang**, dan urutan yang tersimpan di MMKV adalah urutan sebelum drag ditambah aplikasi baru (`A B C D E F`).

---

## 2. Actual Call Graph & Interacting Subsystems

```text
[Thread: OS System Server / BroadcastQueue]
  └── Broadcast Intent (ACTION_PACKAGE_ADDED / REMOVED / CHANGED)
        ↓
[Thread: Main Looper / UI]
  AppChangeReceiver.onReceive()
    └── lifecycleScope.launch (Coroutines Dispatcher.Main)
          ↓
[Thread: Worker Coroutine / Background IO & Default]
  LauncherAppController.handlePackageEvent()
    ├── AppDataSource.getInstalledApps() [IPC to PackageManager]
    ├── AppStateHolder.addApps() / removePackage() [Mutex protected]
    └── IconCache.removePackage()
          ↓
[Thread: Main Looper / UI]
  LauncherActivity.refreshAppsUI()
    └── LauncherAppController.refreshApps()
          ├── Reads AppStateHolder (Full Installed Apps)
          ├── Reads LauncherPreferences (prefs.appOrder & prefs.hiddenApps)
          └── AppSorter.sort(visibleApps, sortMode, appOrder)
    └── AppAdapter.submitList(sortedApps)
          ├── DiffUtil.calculateDiff()
          ├── apps.clear() & apps.addAll(sortedApps)
          └── diffResult.dispatchUpdatesTo(adapter)
```

---

## 3. Timeline Analysis & Mathematical Reconstruction

### Skenario Uji:
- **T0:** Adapter memiliki `[A, B, C, D, E]`. MMKV `appOrder` = `[A, B, C, D, E]`.
- **T1:** Pengguna melakukan long-press pada `B` (index 1). `dragStartedPosition = 1`.
- **T2:** Pengguna menggeser `B` ke antara `C` dan `D`. `adapter.moveItem(1, 2)` memutasi RAM list adapter menjadi `[A, C, B, D, E]`. Jari pengguna **belum dilepas** (*in-flight*).
- **T3:** OS memancarkan broadcast `ACTION_PACKAGE_ADDED` untuk aplikasi baru `F`.
- **T4:** `AppChangeReceiver` menerima event dan menjalankan coroutine.
- **T5:** `LauncherAppController.handlePackageEvent` menambahkan `F` ke `AppStateHolder`. State holder menjadi `[A, B, C, D, E, F]`.
- **T6:** `LauncherActivity.refreshAppsUI` memanggil `appController.refreshApps()`:
  - `getSortedVisibleApps()` membaca `AppStateHolder` (`[A, B, C, D, E, F]`) dan `prefs.appOrder` (`[A, B, C, D, E]`).
  - Karena jari pengguna belum dilepas pada T2, `prefs.appOrder` di MMKV **belum pernah diupdate** (masih `[A, B, C, D, E]`).
  - `AppSorter.sort()` menghasilkan `[A, B, C, D, E, F]`.
- **T7:** `AppAdapter.submitList([A, B, C, D, E, F])` dipanggil:
  - `DiffUtil.calculateDiff` membandingkan `[A, C, B, D, E]` dengan `[A, B, C, D, E, F]`.
  - List `apps` di-replace menjadi `[A, B, C, D, E, F]`.
  - RecyclerView me-reset posisi visual `B` kembali ke index 1 dan menaruh `F` di index 5.
- **T8:** Pengguna akhirnya melepaskan jari (*release*).
- **T9:** `ItemTouchHelper.clearView()` dieksekusi:
  - `dropPosition = viewHolder.adapterPosition`.
  - Karena list adapter baru saja di-reset oleh `submitList()`, ViewHolder `B` kini berada di adapter position **`1`**.
  - Evaluasi Percabangan `clearView()`:
    ```kotlin
    val dropPosition = viewHolder.adapterPosition // = 1
    if (dropPosition != -1 && dragStartedPosition == dropPosition) {
        // dragStartedPosition (1) == dropPosition (1) !
        val app = adapter.getItems().getOrNull(dropPosition)
        if (app != null) {
            actionHandler.showAppMenu(app) // Menu App B terbuka
        }
    }
    ```
- **T10:** `saveCustomAppOrder()` **TIDAK DIPANGGIL**.

### **Jawaban Pertanyaan Utama:**
Hasil urutan di MMKV adalah **`[A, B, C, D, E]`** (dan saat di-refresh berikutnya menjadi **`[A, B, C, D, E, F]`**).
Hasil **BUKAN** `A C B D E` dan **BUKAN** `A C B D E F`.
Pergeseran in-flight `B` yang belum di-commit dibatalkan secara bersih oleh pembaruan model dari OS.

---

## 4. State Transition Table

| Timeline | Event | `AppStateHolder` (RAM) | `AppAdapter.apps` (RAM) | `LauncherPreferences.appOrder` (MMKV) | Catatan Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **T0** | Steady State | `[A, B, C, D, E]` | `[A, B, C, D, E]` | `[A, B, C, D, E]` | Sinkron |
| **T1** | Drag Starts (B) | `[A, B, C, D, E]` | `[A, B, C, D, E]` | `[A, B, C, D, E]` | `dragStartedPosition = 1` |
| **T2** | `onMove(1, 2)` | `[A, B, C, D, E]` | `[A, C, B, D, E]` | `[A, B, C, D, E]` | Transient UI swap |
| **T3** | OS Broadcast (F) | `[A, B, C, D, E]` | `[A, C, B, D, E]` | `[A, B, C, D, E]` | Broadcast masuk |
| **T6** | `refreshAppsUI` | `[A, B, C, D, E, F]` | `[A, C, B, D, E]` | `[A, B, C, D, E]` | Controller query |
| **T7** | `submitList` | `[A, B, C, D, E, F]` | `[A, B, C, D, E, F]` | `[A, B, C, D, E]` | Adapter di-reset ke model |
| **T9** | User Release | `[A, B, C, D, E, F]` | `[A, B, C, D, E, F]` | `[A, B, C, D, E]` | `dropPosition == dragStartedPosition` |
| **T10**| Next Persist | `[A, B, C, D, E, F]` | `[A, B, C, D, E, F]` | `[A, B, C, D, E, F]` | `F` otomatis di akhir |

---

## 5. Exhaustive Lost-Update & Edge-Case Analysis

### Scenario A: Drag Selesai Setelah `submitList()`
- **Alur:** Dijelaskan pada Bab 3.
- **Hasil:** Gestur drag dianggap batal karena list di bawah jari pengguna diganti oleh list baru dari sistem. Tidak ada data loss di storage.

### Scenario B: `submitList()` Selesai Setelah Drag Selesai
- **Alur:** Pengguna melepas jari pada T2. `clearView()` memanggil `saveCustomAppOrder([A, C, B, D, E])` yang langsung meng-update MMKV menjadi `[A, C, B, D, E]`. Kemudian broadcast diproses: `getSortedVisibleApps()` membaca MMKV baru (`[A, C, B, D, E]`) dan menambahkan `F` di akhir (`[A, C, B, D, E, F]`).
- **Hasil:** `prefs.appOrder` = `[A, C, B, D, E, F]`. Reorder tersimpan sempurna dan aplikasi baru masuk di akhir.

### Scenario C: `PACKAGE_REMOVED` Terjadi Saat Drag Aplikasi Tersebut
- **Kasus:** Pengguna sedang men-drag aplikasi `C`. Di latar belakang, aplikasi `C` di-uninstall oleh sistem (misal via Play Store background update/uninstall).
- **Alur:**
  1. `handlePackageEvent` membuang `C` dari `AppStateHolder`.
  2. `refreshAppsUI()` menghasilkan `[A, B, D, E]`.
  3. `submitList([A, B, D, E])` membuang ViewHolder `C` dari RecyclerView.
  4. Ketika pengguna melepas jari, `viewHolder.adapterPosition` bernilai `RecyclerView.NO_POSITION` (`-1`).
  5. Pada `LauncherActivity.kt:100, 107`:
     `if (dropPosition != -1 ...)` mengevaluasi `false`.
  6. `saveCustomAppOrder()` **TIDAK DIPANGGIL**.
- **Hasil:** **AMAN**. Tidak ada crash, dan `C` tidak akan tersimpan kembali ke MMKV.

### Scenario D: `PACKAGE_CHANGED` Terjadi Saat Drag
- **Kasus:** Aplikasi `B` sedang di-drag ketika update komponen selesai.
- **Alur:** `AppStateHolder` me-replace metadata `B`. `submitList()` me-refresh view. Drag ter-reset tanpa duplikasi.
- **Hasil:** **AMAN**.

---

## 6. DiffUtil & Main Thread Concurrency Safety

1. **DiffUtil Invocation:**
   `DiffUtil.calculateDiff(AppDiffCallback(apps, newList))` dipanggil secara sinkron di dalam `AppAdapter.submitList()`.
2. **Atomic Swap:**
   `apps.clear()` dan `apps.addAll(newList)` dieksekusi tepat sebelum `diffResult.dispatchUpdatesTo(this)`.
3. **No Cross-Thread Collision:**
   Karena `submitList` dan `ItemTouchHelper.onMove` keduanya berjalan pada Main Looper, tidak ada kemungkinan `DiffUtil` membaca list pada saat `moveItem` sedang memodifikasi indeks di thread terpisah.

---

## 7. AOSP Launcher3 Comparison

| Arsitektur | AOSP Launcher3 (`/panduanbiargakbikinbug`) | Silauncer Saat Ini | Penilaian & Implikasi |
| :--- | :--- | :--- | :--- |
| **In-Flight Drag Guard** | `DragController.isDragging()` dicek oleh `Launcher.java`. Model binding dari background `LoaderTask` ditunda (*deferred*) sampai drag selesai. | `submitList()` langsung dieksekusi begitu broadcast selesai diproses di coroutine. | **ARCHITECTURAL DEBT (Minor UX)**: Pada Silauncer, event instalasi paket yang sangat langka terjadi pada detik yang sama dengan drag akan membatalkan visual drag. |
| **Model Invalidation** | Atomic commit ke SQLite DB (`LauncherProvider`) saat `onDropCompleted()`. | Atomic commit ke MMKV via `saveCustomAppOrder()` saat `clearView()`. | **ACCEPTABLE SIMPLIFICATION** |

---

## 8. Final Classification & Summary

- **Verdict:** **UX INTERRUPTION ONLY**
- **Risiko Kerusakan Data / Crash:** **0%**
- **Catatan Arsitektur:** Perilaku saat ini sepenuhnya aman dari race condition memori, aman dari korupsi MMKV, dan aman dari crash `IndexOutOfBoundsException`. Satu-satunya dampak adalah interupsi visual pada gestur drag jika dan hanya jika pengguna sedang memindahkan ikon tepat pada detik aplikasi lain selesai dipasang/dihapus oleh sistem.
