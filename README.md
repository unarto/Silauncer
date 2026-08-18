# Silauncer

**Silauncer** adalah peluncur aplikasi (Android Launcher) minimalis dan super cepat yang dirancang untuk memberikan pengalaman navigasi yang efisien, ringan, serta bebas gangguan.

---

## 🚀 Fitur Utama

- **Peluncuran Aplikasi Cepat**: Menggunakan API `LauncherApps` bawaan Android untuk memuat daftar aplikasi dengan performa optimal.
- **Pengurutan & Susunan Kustom**:
  - Pilihan mode pengurutan: **A-Z**, **Z-A**, dan **Urutan Kustom (Custom Order)**.
  - Fitur **Drag and Drop** grid untuk mengatur posisi ikon aplikasi sesuai keinginan.
- **Sistem Caching Ikon Efisien**:
  - In-memory LruCache (`IconCache`) untuk memuat ikon dengan cepat tanpa lag saat scrolling.
  - Deduplikasi permintaan pemuatan ikon (*in-flight deduplication*) menggunakan Kotlin Coroutines.
- **Kustomisasi Tampilan Luas**:
  - Pengaturan grid fleksibel (4x4, 4x5, 5x5, 5x6, 6x6).
  - Penyesuaian ukuran ikon (dp), visibilitas label, ukuran label (sp), dan jarak antar ikon.
- **Manajemen Aplikasi Tersembunyi**: Sembunyikan aplikasi yang jarang digunakan dari halaman utama.
- **Menu Aksi Aplikasi (Long-Press)**: Akses cepat ke halaman *App Info* OS atau ajukan *Uninstall* aplikasi dengan menekan lama ikon aplikasi.
- **Penyimpanan Berperforma Tinggi**: Menggunakan **MMKV** untuk menyimpan konfigurasi dan urutan tata letak secara instan dan aman.
- **Sentuhan Halus (Over-Scroll Spring)**: Efek over-scroll berbasis dinamika spring animation saat menggulir grid.
- **Deteksi Otomatis Perubahan Aplikasi**: `AppChangeReceiver` menyegarkan grid secara otomatis saat ada instalasi, pembaruan, atau penghapusan paket aplikasi.

---

## 🛠️ Arsitektur & Komponen Utama

- `LauncherActivity`: Activity utama peluncur aplikasi dengan `RecyclerView` ber-layout `GridLayoutManager`.
- `LauncherAppController`: Pengendali logika aplikasi, memfilter aplikasi tersembunyi, serta mengelola pengurutan dan penggabungan urutan kustom.
- `AppDataSource` & `AppStateHolder`: Pengambil data aplikasi tersertifikasi dari sistem Android dan pengelola *state* yang aman untuk alur asynchronous/thread-safe.
- `GridDragAndDropHandler`: Pengelola gesture sentuhan (long-press dan drag) untuk memicu menu aplikasi serta drag-and-drop kustom.
- `IconLoader` & `IconCache`: Sistem pemuatan dan penyimpan cache ikon aplikasi secara asynchronous.
- `LauncherPreferences`: Pembungkus MMKV untuk persistensi pengaturan pengguna dan urutan aplikasi kustom.
- `SettingsActivity` & `SettingsUi`: Halaman preferensi kustomisasi peluncur.

---

## 📋 Persyaratan Sistem

- **Android Min SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 36
- **Bahasa**: Kotlin
- **Build System**: Gradle (Kotlin DSL)
- **UI Tooling**: AndroidX AppCompat, RecyclerView, DynamicAnimation, MMKV

---

## 📱 Cara Membangun (Build)

1. Buka proyek ini di Android Studio.
2. Pastikan SDK Android 36 dan JDK 11 atau yang lebih baru sudah terinstal.
3. Jalankan perintah build Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
4. Pasang APK hasil build ke perangkat Android atau emulator.
