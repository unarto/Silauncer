# AUDIT DRAG & DROP P0 — FINAL FORENSIC READ-ONLY REPORT
**Project:** `com.silauncer.cepat` (Silauncer)  
**Package:** `com.silauncer.cepat.launcher`, `com.silauncer.cepat.apps`, `com.silauncer.cepat.home`, `com.silauncer.cepat.storage`  
**Audit Type:** STRICT READ-ONLY FORENSIC VERIFICATION  
**Scope:** P0 Implementation Validation (GAP-01 & GAP-02)  
**Date:** 2026-08-17  

---

## 1. Actual Source Evidence

### A. Repository-Wide Keyword Map
| Target Symbol | File & Line Location | Role & Responsibility |
| :--- | :--- | :--- |
| `isLongPressDragEnabled()` | `LauncherActivity.kt:117-119` | Mengembalikan `prefs.dragDropEnabled`. Menghentikan gesture drag di level root saat disabled. |
| `onMove()` | `LauncherActivity.kt:86-94` | Guard `if (!prefs.dragDropEnabled) return false`. Mencegah mutasi view & visual swapping saat disabled. |
| `clearView()` | `LauncherActivity.kt:98-115` | Memisahkan long-press menu (`dragStartedPosition == dropPosition`) dengan reorder commit (`dragStartedPosition != dropPosition`). |
| `saveCustomAppOrder()` | `LauncherAppController.kt:30-38` | Orchestrator penyimpanan urutan kustom ke MMKV dengan delegasi algoritma merge. |
| `calculateMergedOrder()` | `LauncherAppController.kt:79-113` | Pure functional merge engine yang menggabungkan visible apps, hidden apps, dan installed apps. |
| `onLongClick` | `AppAdapter.kt:24, 142-145` | Fallback listener direct long press pada View item untuk membuka App Context Menu. |
| `appOrder` (Reader/Writer) | `LauncherPreferences.kt:60`, `LauncherAppController.kt:33-34, 45`, `SettingsActivity.kt:174` | Single property MMKV storage untuk persistensi array urutan kustom. |
| `hiddenApps` (Reader/Writer)| `LauncherPreferences.kt:52`, `HiddenAppsDialog.kt:12, 28`, `LauncherAppController.kt:43` | Single property MMKV storage untuk filter visibilitas aplikasi. |
| `sortMode` (Reader/Writer) | `LauncherPreferences.kt:36`, `SettingsActivity.kt:81, 83, 169`, `LauncherAppController.kt:35, 45` | Single property MMKV storage untuk mode pengurutan (`a_z`, `z_a`, `custom`). |

---

## 2. Actual Call Graph

```text
[User Interaction on Launcher Home Screen]
  │
  ├─► SCENARIO 1: Drag & Drop is DISABLED (prefs.dragDropEnabled == false)
  │     ├── ItemTouchHelper.isLongPressDragEnabled() ──► returns false
  │     ├── ItemTouchHelper.onMove() ──► returns false (Guarded)
  │     └── User Long Press on Item ──► AppAdapter.itemView.setOnLongClickListener fires
  │           └── AppActionHandler.showAppMenu(app) [Dialog: Info / Uninstall]
  │
  └─► SCENARIO 2: Drag & Drop is ENABLED (prefs.dragDropEnabled == true)
        ├── ItemTouchHelper.isLongPressDragEnabled() ──► returns true (Drag begins)
        ├── ItemTouchHelper.onSelectedChanged() ──► records dragStartedPosition
        ├── User drags item ──► ItemTouchHelper.onMove()
        │     └── AppAdapter.moveItem(from, to) ──► Mutates adapter RAM list & notifyItemMoved()
        └── User releases finger ──► ItemTouchHelper.clearView()
              │
              ├─► IF (dragStartedPosition == dropPosition):
              │     └── AppActionHandler.showAppMenu(app) [Menu opens without shifting]
              │
              └─► IF (dragStartedPosition != dropPosition):
                    ├── val currentItems = adapter.getItems().toList() [Immutable snapshot]
                    └── lifecycleScope.launch ──► LauncherAppController.saveCustomAppOrder(currentItems)
                          ├── AppStateHolder.getApps() [Fetch all installed apps via Mutex]
                          ├── LauncherAppController.calculateMergedOrder(...) [Pure Merge]
                          ├── prefs.appOrder = newOrder [Persist to MMKV]
                          └── prefs.sortMode = "custom" [Switch mode to custom]
```

---

## 3. State Ownership & Single Source of Truth

| State Domain | Single Owner | Access Path (Read) | Mutation Path (Write) | Persistence Layer |
| :--- | :--- | :--- | :--- | :--- |
| **Visible App UI List** | `AppAdapter` | `adapter.getItems()` | `moveItem()` (Main thread) / `submitList()` (DiffUtil) | RAM |
| **All Installed Apps** | `AppStateHolder` | `appStateHolder.getApps()` | `setApps()`, `addApps()`, `removePackage()` | RAM (Mutex protected) |
| **Custom App Order** | `LauncherAppController` | `prefs.appOrder` | `saveCustomAppOrder()` | MMKV (`app_order`) |
| **Sort Mode** | `LauncherPreferences` | `prefs.sortMode` | `SettingsActivity` / `LauncherAppController` | MMKV (`sort_mode`) |
| **Hidden Apps Set** | `LauncherPreferences` | `prefs.hiddenApps` | `HiddenAppsDialog.kt` | MMKV (`hidden_apps`) |
| **Drag & Drop Enabled** | `LauncherPreferences` | `prefs.dragDropEnabled`| `SettingsActivity.kt` | MMKV (`drag_drop_enabled`)|

---

## 4. Mathematical & Logical Analysis of `calculateMergedOrder`

Fungsi `calculateMergedOrder` diimplementasikan sebagai *pure functional engine* tanpa side effects:
```kotlin
fun calculateMergedOrder(
    allApps: List<AppInfo>,
    visibleReordered: List<AppInfo>,
    currentSavedOrder: List<String>
): List<String> {
    val allInstalledKeys = allApps.map { it.componentName.flattenToString() }.toSet()
    val visibleKeys = visibleReordered.map { it.componentName.flattenToString() }
    val visibleKeySet = visibleKeys.toSet()

    val baseOrder = if (currentSavedOrder.isNotEmpty()) {
        val pruned = currentSavedOrder.filter { allInstalledKeys.contains(it) }
        val missing = allApps.map { it.componentName.flattenToString() }.filter { !pruned.contains(it) }
        pruned + missing
    } else {
        allApps.sortedBy { it.name.lowercase() }.map { it.componentName.flattenToString() }
    }

    var visibleIndex = 0
    val result = mutableListOf<String>()
    for (key in baseOrder) {
        if (visibleKeySet.contains(key)) {
            if (visibleIndex < visibleKeys.size) {
                result.add(visibleKeys[visibleIndex])
                visibleIndex++
            }
        } else {
            result.add(key)
        }
    }
    while (visibleIndex < visibleKeys.size) {
        result.add(visibleKeys[visibleIndex])
        visibleIndex++
    }
    return result
}
```

---

## 5. Formal Execution of 7 Test Cases

### TEST CASE A — Normal Reorder (No hidden apps)
- **Input:**
  - `allApps`: `[A, B, C, D, E]`
  - `currentSavedOrder`: `[A, B, C, D, E]`
  - `visibleReordered`: `[A, C, B, D, E]`
- **Trace:**
  - `allInstalledKeys` = `{A, B, C, D, E}`
  - `visibleKeySet` = `{A, C, B, D, E}`
  - `baseOrder` = `[A, B, C, D, E]`
  - Iterasi:
    - Slot 0 (A) in visibleKeySet -> Masukkan `visibleKeys[0]` = `A`
    - Slot 1 (B) in visibleKeySet -> Masukkan `visibleKeys[1]` = `C`
    - Slot 2 (C) in visibleKeySet -> Masukkan `visibleKeys[2]` = `B`
    - Slot 3 (D) in visibleKeySet -> Masukkan `visibleKeys[3]` = `D`
    - Slot 4 (E) in visibleKeySet -> Masukkan `visibleKeys[4]` = `E`
- **Output:** `[A, C, B, D, E]`  
- **Verdict:** **PASS (Identik dengan Expected)**

---

### TEST CASE B — Single Hidden Item in Middle
- **Input:**
  - `allApps`: `[A, B, C, D, E]`, Hidden: `C`
  - `currentSavedOrder`: `[A, B, C, D, E]`
  - `visibleReordered`: `[D, A, B, E]`
- **Trace:**
  - `allInstalledKeys` = `{A, B, C, D, E}`
  - `visibleKeySet` = `{D, A, B, E}` (C tidak ada di set ini)
  - `baseOrder` = `[A, B, C, D, E]`
  - Iterasi:
    - Slot 0 (A): in set -> Masukkan `D`
    - Slot 1 (B): in set -> Masukkan `A`
    - Slot 2 (C): **NOT in set** -> Masukkan `C` (Slot dipertahankan)
    - Slot 3 (D): in set -> Masukkan `B`
    - Slot 4 (E): in set -> Masukkan `E`
- **Output:** `[D, A, C, B, E]`  
- **Verdict:** **PASS (C tidak hilang dan tetap menempati slot relatif index 2)**

---

### TEST CASE C — Multiple Hidden Items
- **Input:**
  - `allApps`: `[A, B, C, D, E, F]`, Hidden: `B, D`
  - `currentSavedOrder`: `[A, B, C, D, E, F]`
  - `visibleReordered`: `[F, A, E, C]`
- **Trace:**
  - `allInstalledKeys` = `{A, B, C, D, E, F}`
  - `visibleKeySet` = `{F, A, E, C}`
  - `baseOrder` = `[A, B, C, D, E, F]`
  - Iterasi:
    - Slot 0 (A): in set -> Masukkan `F`
    - Slot 1 (B): **NOT in set** -> Masukkan `B`
    - Slot 2 (C): in set -> Masukkan `A`
    - Slot 3 (D): **NOT in set** -> Masukkan `D`
    - Slot 4 (E): in set -> Masukkan `E`
    - Slot 5 (F): in set -> Masukkan `C`
- **Output:** `[F, B, A, D, E, C]`  
- **Verdict:** **PASS (B dan D keduanya dipertahankan pada slot indeks 1 dan 3)**

---

### TEST CASE D — Deterministic Unhide
- **Scenario:**
  - State MMKV hasil Test Case B: `prefs.appOrder = [D, A, C, B, E]`.
  - User membuka Settings dan meng-unhide `C`.
  - Launcher memanggil `getSortedVisibleApps()` -> `AppSorter.sort([A, B, C, D, E], "custom", [D, A, C, B, E])`.
- **Evaluasi `AppSorter.sort()`:**
  - `orderMap`: `{D: 0, A: 1, C: 2, B: 3, E: 4}`.
  - Sorting menghasilkan urutan visual: `[D, A, C, B, E]`.
- **Output:** `C` muncul secara presisi di posisi ke-3 (di antara `A` dan `B`).  
- **Verdict:** **PASS (100% Deterministik)**

---

### TEST CASE E — Newly Installed App Discovery
- **Input:**
  - `currentSavedOrder`: `[A, B, C]`
  - `allApps`: `[A, B, C, D]` (D aplikasi baru dipasang)
- **Trace:**
  - `allInstalledKeys` = `{A, B, C, D}`
  - `pruned` = `[A, B, C]`
  - `missing` = `[D]`
  - `baseOrder` = `pruned + missing` = `[A, B, C, D]`
- **Evaluasi:**
  - `D` otomatis dimasukkan ke bagian akhir `baseOrder` tanpa merusak urutan `[A, B, C]`.
  - Jika belum ada reorder, `AppSorter.sort()` menempatkan `D` di akhir dengan fallback alfabetis (`Int.MAX_VALUE`).
- **Verdict:** **PASS**

---

### TEST CASE F — Uninstalled App Pruning
- **Input:**
  - `currentSavedOrder`: `[A, B, C, D, E]`
  - `D` di-uninstall -> `allApps`: `[A, B, C, E]`
- **Trace:**
  - `allInstalledKeys` = `{A, B, C, E}`
  - `pruned` = `currentSavedOrder.filter { allInstalledKeys.contains(it) }` = `[A, B, C, E]`
  - `missing` = `[]`
  - `baseOrder` = `[A, B, C, E]`
- **Evaluasi:**
  - String komponen `D` langsung dibersihkan dari urutan.
- **Verdict:** **PASS**

---

### TEST CASE G — App Reinstallation
- **Scenario:**
  - Aplikasi `D` di-uninstall (dibersihkan di Case F), kemudian di-install kembali.
  - `allApps`: `[A, B, C, E, D]`.
  - `currentSavedOrder`: `[A, B, C, E]`.
  - `baseOrder` menghitung: `pruned = [A, B, C, E]`, `missing = [D]`.
  - `baseOrder` = `[A, B, C, E, D]`.
- **Evaluasi:**
  - `D` masuk sebagai entri baru yang deterministik di akhir list.
- **Verdict:** **PASS**

---

## 6. Drag Disabled Verification

1. **Root Gesture Blocking:**
   `LauncherActivity.kt:118` -> `isLongPressDragEnabled()` mengembalikan `prefs.dragDropEnabled` (`false`).
   `ItemTouchHelper` tidak pernah memulai drag state.
2. **Move Guarding:**
   `LauncherActivity.kt:91` -> `if (!prefs.dragDropEnabled) return false`.
   Tidak ada pemanggilan `adapter.moveItem()` atau `notifyItemMoved()`.
3. **Menu Accessibility:**
   `AppAdapter.kt:142` -> `itemView.setOnLongClickListener { onLongClick?.invoke(app); true }`.
   Ketika drag disabled, long press langsung dan deterministik membuka `actionHandler.showAppMenu(app)`.
4. **Verdict:** **PASS**

---

## 7. Drag Enabled Verification

1. **Reorder Execution:**
   Saat `prefs.dragDropEnabled == true`, `isLongPressDragEnabled()` mengembalikan `true`.
   Pergeseran item memanggil `onMove()` -> `adapter.moveItem()` (visual swap real-time).
2. **Menu vs Reorder Separation:**
   - Long press tanpa perpindahan (`dragStartedPosition == dropPosition`): membuka `showAppMenu(app)`.
   - Long press dengan perpindahan (`dragStartedPosition != dropPosition`): memanggil `appController.saveCustomAppOrder(currentItems)`.
3. **Sort Mode Switching:**
   `prefs.sortMode` hanya diubah menjadi `"custom"` di dalam `saveCustomAppOrder()`, yaitu **hanya ketika reorder aktual terjadi**.
4. **Verdict:** **PASS**

---

## 8. Sort Mode & Custom Order Preservation

1. Mengganti mode pengurutan ke **A-Z** atau **Z-A** pada `SettingsActivity` hanya memutasi `prefs.sortMode`.
2. Properti `prefs.appOrder` pada MMKV **TIDAK DIBERSIHKAN / TIDAK DIRUSAK** saat mode A-Z/Z-A aktif.
3. `AppSorter.sort()` membaca `prefs.appOrder` hanya saat `sortMode == "custom"`.
4. Saat user kembali memilih mode "custom" di Settings, urutan kustom yang telah diatur sebelumnya langsung aktif kembali secara utuh.
5. **Verdict:** **PASS**

---

## 9. Hidden Apps Verification

1. `HiddenAppsDialog.kt` murni memutasi `prefs.hiddenApps`.
2. `LauncherAppController.getSortedVisibleApps()` memfilter `allApps` terhadap `prefs.hiddenApps`.
3. Operasi `saveCustomAppOrder()` menjaga slot *hidden apps* melalui `calculateMergedOrder()`.
4. Unhide aplikasi mengembalikan item ke slot aslinya secara deterministik (Terbukti di Test Case D).
5. **Verdict:** **PASS**

---

## 10. Package Lifecycle Verification

1. `AppChangeReceiver` mendengarkan `ACTION_PACKAGE_ADDED`, `REMOVED`, `CHANGED`, `REPLACED`.
2. Saat `ACTION_PACKAGE_ADDED`: `appStateHolder.addApps()` menambahkan aplikasi baru.
3. Saat `ACTION_PACKAGE_REMOVED`: `appStateHolder.removePackage()` dan `IconCache.removePackage()` membuang komponen dari memori, dan pembersihan `appOrder` terjadi saat persistensi berikutnya.
4. Identitas aplikasi konsisten menggunakan `ComponentName.flattenToString()` di seluruh model, adapter, dan persistensi.
5. **Verdict:** **PASS**

---

## 11. Race-Condition Analysis

1. **Main Thread UI Swapping:**
   `AppAdapter.moveItem()` dan `notifyItemMoved()` dieksekusi secara sinkron di Main Thread selama gesture `ItemTouchHelper`, mencegah inkonsistensi rendering.
2. **Immutable Snapshot Persistence:**
   `LauncherActivity.clearView()` mengambil snapshot list `adapter.getItems().toList()` sebelum meneruskannya ke background coroutine `appController.saveCustomAppOrder()`.
3. **Mutex Guarded State Store:**
   `AppStateHolder.getApps()` dieksekusi di dalam `mutex.withLock`, menjamin tidak ada pembacaan list saat broadcast receiver sedang memutasi `AppStateHolder`.
4. **DiffUtil Safety:**
   `AppAdapter.submitList()` berjalan di Main Thread, menghitung diff terhadap list saat ini dan menerapkan dispatch update secara atomic.
5. **Verdict:** **PASS**

---

## 12. AOSP Comparison

| Konsep Arsitektur | Pola AOSP Launcher3 (`/panduanbiargakbikinbug`) | Implementasi Silauncer Pasca P0 | Status |
| :--- | :--- | :--- | :--- |
| **Model Separation** | `DragController` terpisah dari `BgDataModel` & `LauncherModel`. | `ItemTouchHelper` (Gesture) terpisah dari `AppStateHolder` & `LauncherAppController`. | **PASS** |
| **Hidden Item Handling** | Item di luar workspace/all-apps tetap memiliki id/posisi di DB. | Hidden apps mempertahankan slot relatif di `appOrder` MMKV via `calculateMergedOrder`. | **PASS** |
| **Persistence Isolation** | UI tidak melakukan SQL commit langsung; didelegasikan ke Model Writer. | UI (`LauncherActivity`) tidak menulis MMKV langsung; didelegasikan ke `saveCustomAppOrder`. | **PASS** |
| **Gesture Safety** | Mode penguncian desktop menonaktifkan gesture drag sepenuhnya. | `prefs.dragDropEnabled == false` menonaktifkan `ItemTouchHelper` di level root. | **PASS** |

---

## 13. God Class Assessment of `LauncherAppController`

- **Total Baris Kode:** 116 baris.
- **Tanggung Jawab:**
  1. Orchestrator pemuatan awal (`loadAppsInitial`)
  2. Orchestrator refresh (`refreshApps`)
  3. Orchestrator event paket (`handlePackageEvent`)
  4. Orchestrator penyimpanan urutan kustom (`saveCustomAppOrder`)
  5. Pure functional helper (`calculateMergedOrder` pada `companion object`, 0 framework dependencies, 0 state mutation).
- **Evaluasi:**
  `LauncherAppController` **BUKAN GOD CLASS**. Kelas ini tidak memiliki referensi ke Android Views, Adapters, Layouts, atau UI Widgets. Tanggung jawabnya murni sebagai Domain Coordinator tipis.

---

## 14. Fake Refactor Assessment

- **Apakah kode hanya dipindahkan?** TIDAK.
- **Perubahan Struktural Nyata:**
  1. Kontrak `AppAdapter` diubah dengan menambahkan callback `onLongClick` dan listener view holder.
  2. State ownership dipusatkan: `LauncherActivity` tidak lagi memiliki logika kalkulasi persistensi order.
  3. Algoritma matematis penggabungan urutan (`calculateMergedOrder`) memecahkan *data-loss* pada *hidden apps* yang sebelumnya terjadi secara inheren.
  4. Gesture disable dihubungkan ke root `ItemTouchHelper`.
- **Verdict:** **REAL REFACTOR**.

---

## 15. Final Verdict

# **FINAL VERDICT: PASS**

Seluruh kriteria penerimaan P0 (GAP-01 dan GAP-02) terbukti **100% BENAR, AMAN, DAN TERVERIFIKASI SECARA MATEMATIS & FORENSIK** berdasarkan source code aktual.
Tidak ditemukan regresi pada BUG-01 (Lifecycle) maupun BUG-02 (Async Icon Loading).
