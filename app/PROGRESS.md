# Progress Report

## SELESAI
- Memperbaiki bug pada fitur existing (IconLoader deduplication).
- Memperbaiki AppStateHolder untuk thread-safety dan caching yang efisien.
- Memperbaiki SettingsActivity agar tidak silent crash saat manual view rendering gagal, melainkan finish gracefully.
- Memperbaiki memory leak pada observer di AppAdapter dengan melepas listener saat di-detach.
- Mengatasi bug Long-Press Jitter & Drag Intercept di LauncherActivity & AppAdapter:
  - Mengubah cara deteksi long press sehingga kembali ditangani secara native oleh `AppAdapter` via `setOnLongClickListener`.
  - Memastikan `ItemTouchHelper` hanya mengurus pergeseran UI/Drag, tanpa mencuri (intercept) event sentuhan long-press bawaan.
- Membuat dokumentasi proyek pada file `/README.md`.

## SEDANG DIKERJAKAN
- (Tidak ada saat ini)

## TERTUNDA
- (Tidak ada saat ini)

## BELUM DIKERJAKAN
- (Tidak ada saat ini)

## DITEMUKAN
- OS gesture intercept default dari `ItemTouchHelper` sangat agresif, sudah dimatikan `isLongPressDragEnabled() = false` dan di-*trigger* manual.

## DIBATALKAN
- (Tidak ada)
