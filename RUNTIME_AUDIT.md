=== CONFIRMED FAILURES ===

1. **DRAG & DROP**
   - **Gejala**: Item aplikasi tidak dapat digeser. Animasi drag tidak berjalan atau langsung terputus di tengah jalan.
   - **Status Runtime**: FAIL.
2. **LAUNCHER SETTINGS ACCESS**
   - **Gejala**: Akses menu Settings tidak dapat diandalkan, tidak terlihat, atau hilang sama sekali dari UI.
   - **Status Runtime**: MISSING / BROKEN ENTRY POINT.

=== CONFIRMED WORKING ===

- **Grid Layout Rendering**: Baris dan kolom me-render dengan benar.
- **App Launching**: Aplikasi benar-benar terbuka (menggunakan explicit Intent).
- **Uninstall / App Info Menu**: Dialog benar-benar muncul jika fitur tidak bertabrakan dengan pengaturan lain.
- **Package Change Receiver**: Berhasil me-reload daftar saat ada aplikasi di-install/di-uninstall (terbukti terkoneksi di background).

=== MISSING ENTRY POINTS ===

- **Akses Pengaturan (Settings)**:
  - **Fakta UI**: Tidak ada mekanisme UI launcher yang standar (seperti *long-press* pada area kosong di workspace, atau tombol khusus) untuk membuka `SettingsActivity`.
  - **Satu-satunya jalur akses**: Settings di-inject secara paksa sebagai "Aplikasi" di dalam grid melalui kode `apps.add(AppInfo("Silauncer Settings", ...))` di `AppRepository.kt`. Metode ini adalah hack dan cacat (lihat Root Cause).

=== SOURCE/Runtime MISMATCH ===

1. **Drag & Drop `isLongPressDragEnabled` vs Default State**
   - *Source*: Fitur Drag & Drop terhubung sempurna ke `ItemTouchHelper`.
   - *Runtime*: Gagal tereksekusi karena `prefs.dragDropEnabled` bernilai `false` secara default. Pengguna tidak bisa mengubahnya ke `true` jika mereka tidak bisa membuka *Settings*.
2. **AppAdapter `moveItem` (ListAdapter Asynchrony)**
   - *Source*: Metode `moveItem()` ada dan memanggil `submitList(newList)`. Secara kasat mata tampak benar.
   - *Runtime*: Gagal total. `ListAdapter` melakukan kalkulasi *diff* secara asinkron di *background thread*. Jika `submitList` dipanggil terus menerus di dalam siklus *continuous drag* `onMove()`, RecyclerView akan mengalami *layout pass* paksa secara acak yang menyebabkan gesture *drag* batal, *stutter*, atau item menjauh dari jari pengguna.
3. **Konflik Long-Press**
   - *Source*: Keduanya (ItemTouchHelper dan View.OnLongClickListener) mendengarkan event sentuhan lama.
   - *Runtime*: Fungsi `itemView.setOnLongClickListener { ... return true }` menelan (consume) *gesture long click* sepenuhnya dan memunculkan *App Menu (Uninstall/Info)*. Akibatnya, `ItemTouchHelper` tidak pernah mendapatkan *event long press* untuk memulai *drag*, atau terjadi *race condition* yang memunculkan dialog persis saat item mulai ditarik.

=== ROOT CAUSE ===

**A. Penyebab Drag & Drop Gagal Runtime:**
1. Default flag di `LauncherPreferences` adalah `false`.
2. Jika dinyalakan (menjadi `true`), *long-click* digagalkan karena tertimpa (consumed) oleh *App Menu* (*Uninstall* / *App Info*).
3. Jika entah bagaimana drag berhasil dimulai, event `onMove` memicu fungsi `moveItem()` yang menggunakan `submitList(newList)`. Memanggil komputasi asinkron (*AsyncListDiffer*) di tengah-tengah 60fps *gesture animation* menyebabkan state asinkronisasi fatal pada *RecyclerView*, yang membatalkan (drop) item tersebut seketika.

**B. Penyebab Akses Settings Hilang:**
1. **Tidak Ada UI Masuk**: Tidak ada cara untuk menahan layar (*workspace long press*) untuk membuka pengaturan seperti launcher pada umumnya.
2. **Injeksi "Fake App" yang Rapuh**: Settings dimasukkan secara manual ke dalam array aplikasi (`AppRepository.kt:45`).
3. **Penghapusan Otomatis (Self-Destruct)**: Jika Silauncer diperbarui (update) atau OS memancarkan `ACTION_PACKAGE_CHANGED` untuk Silauncer, `AppChangeReceiver` akan bereaksi. Logic `AppRepository.updatePackage()` akan **MENGHAPUS SEMUA** item dengan `packageName == com.silauncer.cepat`. Kemudian sistem memuat ulang aktivitas menggunakan `LauncherApps.getActivityList()`. Karena `SettingsActivity` TIDAK memiliki tag `CATEGORY_LAUNCHER` di *AndroidManifest*, sistem tidak akan memasukkannya kembali. Ikon "Silauncer Settings" akan hilang selamanya dari layar sampai memori aplikasi dihentikan paksa (Force Stop).

=== FEATURE REALITY MATRIX ===

| Fitur | SOURCE | WIRED | UI ACCESS | RUNTIME | PERSISTENCE |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Grid / tata letak** | PASS | PASS | MISSING* | PASS | PASS |
| **Ukuran ikon** | PASS | PASS | MISSING* | PASS | PASS |
| **Pengisian ikon otomatis** | NOT IMPLEMENTED | NOT IMPLEMENTED | NOT IMPLEMENTED | NOT IMPLEMENTED | NOT IMPLEMENTED |
| **Sembunyikan aplikasi** | PASS | PASS | MISSING* | PASS | PASS |
| **Tampilkan label** | PASS | PASS | MISSING* | PASS | PASS |
| **Ukuran label** | PASS | PASS | MISSING* | PASS | PASS |
| **Jarak ikon** | PASS | PASS | MISSING* | PASS | PASS |
| **Sorting A-Z** | PASS | PASS | MISSING* | PASS | PASS |
| **Sorting Z-A** | PASS | PASS | MISSING* | PASS | PASS |
| **Custom order** | PASS | PASS | MISSING* | FAIL | FAIL |
| **Drag & Drop** | PASS | PASS | MISSING* | FAIL | FAIL |
| **Persistensi posisi** | PASS | PASS | MISSING* | FAIL | FAIL |
| **Reset layout** | PASS | PASS | MISSING* | PASS | PASS |
| **Aplikasi baru (Install)** | PASS | PASS | PASS | PASS | N/A |
| **Aplikasi dihapus** | PASS | PASS | PASS | PASS | N/A |
| **Icon cache** | PASS | PASS | N/A | PASS | N/A |

*\*Catatan UI ACCESS MISSING: Semua pengaturan gagal diakses secara wajar karena Entry Point SettingsActivity tidak standar dan hilang (broken).*
