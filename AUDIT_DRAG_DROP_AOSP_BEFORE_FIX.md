# AUDIT DRAG & DROP AOSP (BEFORE FIX) — P0 FORENSIC ANALYSIS
**Project:** `com.silauncer.cepat`  
**Target:** GAP-01 (Drag & Drop Disabled Bypass) & GAP-02 (Hidden Apps Custom Order Loss)  
**Date:** 2026-08-17  

---

## 1. Current Architecture & Call Graph

### Current Drag & Drop Call Graph:
```text
[User Touches & Holds Item in LauncherActivity]
  ↓ (ItemTouchHelper.SimpleCallback)
isLongPressDragEnabled() -> returns true unconditionally
  ↓ (ItemTouchHelper captures drag gesture)
onSelectedChanged(viewHolder, ACTION_STATE_DRAG)
  ↓ [dragStartedPosition recorded]
onMove(viewHolder, target)
  ↓
AppAdapter.moveItem(fromPosition, toPosition)
  ↓ [Mutates in-memory `apps: MutableList<AppInfo>` on Main Thread]
notifyItemMoved(fromPosition, toPosition)
  ↓ [Visual animation on RecyclerView]
clearView(recyclerView, viewHolder)
  ├─ IF (dragStartedPosition == dropPosition && dropPosition != -1)
  │    └── AppActionHandler.showAppMenu(app) [Dialog: Info / Uninstall]
  └─ IF (dragStartedPosition != dropPosition && dropPosition != -1)
       └── IF (prefs.dragDropEnabled)
             ├── val newOrder = adapter.getItems().map { it.componentName.flattenToString() }
             ├── prefs.appOrder = newOrder
             └── prefs.sortMode = "custom"
```

---

## 2. State Ownership & Persistence Analysis

### A. Current State Owners:
| State | Owner | Mutated By | Persisted In | Consumer |
| :--- | :--- | :--- | :--- | :--- |
| **`apps` (Visible List)** | `AppAdapter` | `ItemTouchHelper.onMove()` & `submitList()` | RAM only | RecyclerView ViewHolders |
| **`allInstalledApps`** | `AppStateHolder` | `AppDataSource` / Package Events | RAM only | `LauncherAppController` |
| **`appOrder`** | `LauncherPreferences` | `LauncherActivity.clearView()` | MMKV (`app_order`) | `AppSorter.sort()` |
| **`sortMode`** | `LauncherPreferences` | `SettingsActivity` & `LauncherActivity` | MMKV (`sort_mode`) | `AppSorter.sort()` |
| **`hiddenApps`** | `LauncherPreferences` | `HiddenAppsDialog` | MMKV (`hidden_apps`) | `LauncherAppController` filter |
| **`dragDropEnabled`**| `LauncherPreferences` | `SettingsActivity` | MMKV (`drag_drop_enabled`) | `LauncherActivity.clearView()` |

---

## 3. Root Cause of GAPs (Before Fix)

### GAP-01: Drag & Drop Disabled Bypass
- **File:** `LauncherActivity.kt:106-108`
- **Root Cause:** `isLongPressDragEnabled()` mengembalikan `true` secara konstan tanpa mengecek `prefs.dragDropEnabled`.
- **Dampak Perilaku:** Pengguna tetap dapat menggeser item secara visual di layar saat opsi *Drag & Drop* di-disable. Namun, saat dilepas, `clearView` tidak menyimpan urutan ke MMKV, menyebabkan posisi membal kembali saat aplikasi di-refresh atau di-resume.

### GAP-02: Hidden Apps Wipeout from `appOrder`
- **File:** `LauncherActivity.kt:96`
- **Root Cause:** `adapter.getItems()` hanya berisi aplikasi yang terlihat (*visible apps*). Saat diekstrak dan disimpan ke `prefs.appOrder`, seluruh aplikasi yang sedang disembunyikan (*hidden apps*) terhapus secara permanen dari string persistensi `app_order`.
- **Dampak Perilaku:** Ketika pengguna membuka Pengaturan dan meng-unhide aplikasi tersebut di masa depan, aplikasi tidak lagi memiliki entri di `appOrder` sehingga terlempar ke urutan paling bawah (`Int.MAX_VALUE`).

### GAP-03: Ambiguous Long-Press Menu Handling
- **File:** `LauncherActivity.kt:89-92`
- **Root Cause:** Long-press menu disematkan di dalam `clearView` milik `ItemTouchHelper` dengan kondisi `dragStartedPosition == dropPosition`. Jika pengguna mengalami sedikit jitter/gerakan jari (1 piksel) yang memicu `onMove`, menu gagal muncul atau item tertukar tanpa sengaja.

---

## 4. Perbandingan dengan Pola AOSP Launcher3

Di AOSP Launcher3 (`/panduanbiargakbikinbug`):
1. **Workspace Model (`BgDataModel.java`, `ItemInfo.java`):**
   Setiap item memiliki koordinat pasti (`screenId`, `cellX`, `cellY`, `container`).
2. **Persistence Atomicity:**
   Mutasi posisi item di-commit melalui model writer ke storage tanpa menghapus item di kontainer lain atau item yang tidak sedang ditampilkan di workspace.
3. **Pemisahan Responsibility:**
   UI View/Gesture (`DragController`) tidak menulis langsung ke persistent storage secara mentah. UI View mendelegasikan perubahan posisi ke Model Controller untuk digabungkan dengan state global.

---

## 5. Desain Perbaikan P0 (Architectural Remediation Plan)

### A. Nonaktifkan Gesture Saat Drag & Drop Disabled:
1. `isLongPressDragEnabled()` pada `ItemTouchHelper` membaca `prefs.dragDropEnabled`.
2. Saat `prefs.dragDropEnabled == false`, `isLongPressDragEnabled()` mengembalikan `false`.
3. Pada `AppAdapter`, pasang `itemView.setOnLongClickListener { onLongClick(app); true }` sehingga saat drag disabled, long press langsung dan deterministik membuka App Context Menu.
4. Saat `prefs.dragDropEnabled == true`, `ItemTouchHelper` menangkap long press untuk reorder; jika dilepas tanpa berpindah posisi, menu tetap terbuka.

### B. Single Source of Truth & Deterministic Order Merge di `LauncherAppController`:
Pindahkan logika persistensi urutan dari `LauncherActivity.clearView()` ke method terisolasi di `LauncherAppController`:
```kotlin
suspend fun saveCustomAppOrder(visibleReorderedApps: List<AppInfo>)
```
Algoritma Penggabungan (*Deterministic Order Merge*):
1. Dapatkan daftar seluruh aplikasi terpasang dari `AppStateHolder.getApps()`.
2. Dapatkan urutan tersimpan sebelumnya dari `prefs.appOrder`. Jika kosong, gunakan urutan alfabetis sebagai basis.
3. Petakan slot-slot item terlihat (*visible items*) ke urutan baru yang diatur pengguna.
4. Pertahankan posisi slot item tersembunyi (*hidden items*) pada urutan relatifnya di dalam `appOrder`.
5. Bersihkan entri paket yang sudah di-uninstall dari `appOrder`.
6. Simpan hasil penggabungan ke `prefs.appOrder` dan set `prefs.sortMode = "custom"`.

---
