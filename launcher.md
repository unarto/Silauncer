# REBUILD ANDROID LAUNCHER — ULTRA LIGHTWEIGHT

BANGUN ULANG PROJECT INI MENJADI LAUNCHER ANDROID YANG SANGAT RINGAN, CEPAT, MINIMALIS, DAN TOUCH-FIRST.

KONSEP FINAL

Aplikasi hanya fokus pada:

- Home Screen
- App icons
- Vertical scrolling
- Custom Grid
- Icon size
- Sorting
- Manual app position
- Settings
- MMKV storage

JANGAN menambahkan fitur launcher lain di luar scope tersebut.

---

1. AUDIT SEBELUM IMPLEMENTASI

SEBELUM mengubah source code:

1. Audit seluruh project.
2. Identifikasi package/application ID.
3. Identifikasi Activity launcher existing.
4. Identifikasi AppLoader/AppManager existing.
5. Identifikasi storage existing.
6. Identifikasi UI existing yang masih dapat digunakan.
7. Identifikasi dependency yang masih digunakan.
8. Identifikasi fitur lama yang tidak diperlukan.
9. Gunakan kembali implementation yang masih benar.
10. Jangan rewrite file tanpa alasan.

Jangan mengganti package/application ID.

Jangan melakukan migrasi framework hanya demi migrasi.

---

2. FITUR FINAL

Launcher hanya terdiri dari:

HOME SCREEN
+
APP ICONS
+
GRID
+
SORTING
+
MANUAL POSITION
+
SETTINGS

Tidak ada fitur tambahan.

---

3. HOME SCREEN

Gunakan satu Home Screen.

Tidak ada:

- App Drawer
- halaman daftar aplikasi
- multiple home page
- horizontal pager
- swipe kiri/kanan untuk berpindah halaman
- search bar
- search screen
- widget
- feed
- news
- floating toolbox
- floating dashboard

Semua aplikasi langsung tampil di Home Screen.

Home Screen dapat:

- scroll vertikal ke atas
- scroll vertikal ke bawah

Gunakan satu layout/grid vertikal panjang.

---

4. WALLPAPER

Hapus seluruh sistem wallpaper custom.

Jangan membuat:

- WallpaperManager custom
- WallpaperPicker
- WallpaperRepository
- WallpaperStorage
- WallpaperCache
- BackgroundManager
- background image

Home Screen harus transparan.

Wallpaper sistem Android harus terlihat langsung.

Jangan menyimpan atau menyalin wallpaper.

---

5. THEME — HAPUS TOTAL

HAPUS seluruh custom theme system.

Jangan membuat:

- ThemeManager
- ThemeRepository
- ThemeEngine
- ColorTheme
- ThemeSelector
- ThemeSettings
- custom color system
- accent color system
- custom background color
- dark/light selector

Jangan menyediakan menu Theme di Settings.

Jangan membuat sistem warna launcher sendiri.

Gunakan resource/style Android minimum yang memang diperlukan oleh UI.

Jangan membuat pengganti ThemeManager.

---

6. SYSTEM LANGUAGE

Semua string UI harus menggunakan Android resources.

Gunakan:

res/values/strings.xml

dan:

res/values-id/strings.xml

jika diperlukan.

Jangan hard-code string UI di Kotlin/Java.

Jangan membuat language selector.

Launcher mengikuti bahasa sistem Android.

---

7. GRID

Default:

5 kolom x 5 baris per viewport.

Sediakan Custom Grid.

User dapat mengatur:

- Columns
- Rows

Contoh:

4x5
5x5
5x6
6x6
6x7

Grid harus responsif terhadap ukuran layar.

Tidak boleh menggunakan horizontal pager.

Grid harus touch-friendly.

---

8. APP ICON

Gunakan icon asli aplikasi Android.

Ambil dari:

PackageManager

atau:

LauncherApps

jika memang diperlukan.

Jangan membuat icon pack.

Jangan menggambar ulang icon.

Jangan melakukan image processing berat.

Sediakan pengaturan:

Icon Size

User dapat mengubah ukuran icon.

---

9. APP LOADING

Pisahkan app loading dari UI.

Gunakan struktur:

apps/
AppRepository
AppInfo
AppLoader
AppSorter

AppLoader:

- membaca daftar aplikasi
- mengambil label
- mengambil package name
- mengambil launch intent
- mengambil icon

AppRepository:

- menyediakan data aplikasi
- mengelola daftar aplikasi

AppSorter:

- melakukan sorting

Jangan memasukkan logic tersebut seluruhnya ke LauncherActivity.

---

10. SORTING

Sediakan:

1. Alphabetical / A-Z
2. Z-A
3. Nama
4. Tanggal instalasi
5. Terbaru digunakan
6. Custom / Manual

Sorting harus dilakukan secara efisien.

Jangan melakukan sorting berulang jika data tidak berubah.

---

11. CUSTOM / MANUAL POSITION

Jika sorting = Custom:

User dapat memindahkan aplikasi secara manual.

Posisi harus disimpan persistent.

Ketika launcher dibuka kembali:

posisi tetap.

Jangan auto-sort ketika Custom aktif.

Jangan mengubah posisi manual user secara otomatis.

Ketika aplikasi dihapus:

hapus posisi package tersebut.

Ketika aplikasi baru ditambahkan:

tambahkan tanpa merusak posisi aplikasi existing.

---

12. APP ITEM

Tap:

langsung membuka aplikasi.

Long press:

menu sederhana:

- Edit Position
- App Info
- Uninstall
- Remove from Home

Tidak ada menu dashboard.

Tidak ada floating menu besar.

Gunakan UI Android sesederhana mungkin.

---

13. APP INFO

Gunakan Android system App Info Intent jika tersedia.

Jangan membuat App Info Activity sendiri jika tidak diperlukan.

---

14. UNINSTALL

Gunakan Android uninstall mechanism.

Jangan menggunakan:

- shell
- Root
- command executor
- external script

Jika uninstall tidak diizinkan Android:

tangani dengan aman tanpa crash.

---

15. REMOVE FROM HOME

Remove from Home:

- menghilangkan aplikasi dari Home Screen
- tidak uninstall aplikasi

Simpan status hidden/removed.

Aplikasi tetap terinstall.

Sediakan cara sederhana untuk mengembalikan aplikasi melalui Settings jika diperlukan.

Jangan membuat App Drawer untuk mengembalikan aplikasi.

---

16. RECENTLY USED

Sorting "Terbaru digunakan" harus ringan.

Catat timestamp penggunaan aplikasi seperlunya.

Jangan membuat:

- background tracking service
- foreground service
- polling
- database

Gunakan lifecycle/event yang tersedia.

---

17. STORAGE — MMKV

WAJIB menggunakan:

MMKV

sebagai persistent storage launcher.

Jangan menggunakan:

- SharedPreferences
- DataStore
- Room
- SQLite
- database lain

---

18. DATA MMKV

Simpan:

- grid columns
- grid rows
- icon size
- sorting mode
- custom app positions
- hidden/removed apps
- recently used timestamps jika diperlukan

Jangan menyimpan:

- wallpaper
- theme
- background image
- network data
- service state
- command
- script

---

19. STORAGE STRUCTURE

Gunakan:

storage/
PreferenceStore
AppPositionStore

PreferenceStore menangani:

- grid columns
- grid rows
- icon size
- sorting mode
- recently used timestamps jika diperlukan

AppPositionStore menangani:

- custom app positions
- hidden/removed apps

Jangan membuat StorageManager besar yang menangani semua hal.

---

20. MMKV INITIALIZATION

Jika project sudah memiliki Application class:

gunakan Application existing.

Inisialisasi MMKV secara aman.

Jangan membuat Application class kedua.

Gunakan MMKV dependency yang kompatibel dengan project.

Jangan menambahkan storage library lain.

---

21. SETTINGS

Settings hanya berisi:

Grid

- Columns
- Rows

Icon

- Icon Size

Sorting

- Sorting Mode

Jangan membuat menu:

- Theme
- Wallpaper
- Language
- Search
- Drawer
- Widget
- Gesture
- Background

---

22. BACKGROUND PROCESSING

PEKERJAAN YANG BERAT DAN TIDAK BERKAITAN LANGSUNG DENGAN UI WAJIB DIJALANKAN DI BACKGROUND THREAD.

Contoh pekerjaan yang boleh diproses di background:

- loading daftar aplikasi
- membaca metadata aplikasi
- mengambil informasi package dalam jumlah besar
- sorting daftar aplikasi
- membaca data MMKV dalam jumlah besar
- validasi posisi aplikasi
- menghitung layout data jika memang berat
- preprocessing data aplikasi

Jangan menjalankan pekerjaan tersebut secara langsung di Main/UI thread jika dapat menyebabkan frame drop atau startup lambat.

---

23. UI THREAD

Main thread hanya menangani:

- rendering UI
- input touch
- click
- long press
- scroll
- update view
- membuka aplikasi
- update hasil background task

Jangan menjalankan loop atau pekerjaan berat di Main thread.

---

24. BACKGROUND THREAD RULE

Gunakan mekanisme concurrency yang sederhana dan sesuai project existing.

Prioritaskan:

- Executor
- background thread
- coroutine jika project memang sudah menggunakannya

Jangan menambahkan framework concurrency hanya untuk pekerjaan sederhana.

Pekerjaan background harus:

1. dijalankan asynchronous
2. tidak memblokir UI
3. dapat dibatalkan jika Activity/Screen sudah tidak membutuhkan hasil
4. mengembalikan hasil ke Main thread
5. tidak membuat thread baru berulang-ulang tanpa kontrol

---

25. JANGAN BUAT BACKGROUND SERVICE

PENTING:

"Background processing" BUKAN berarti membuat service yang berjalan terus.

JANGAN membuat:

- foreground service
- persistent service
- daemon
- continuous worker
- polling service

Background thread hanya aktif ketika memang ada pekerjaan.

Setelah pekerjaan selesai:

thread/executor idle atau berhenti.

Launcher harus idle ketika tidak digunakan.

---

26. STARTUP OPTIMIZATION

Launcher harus menampilkan UI secepat mungkin.

Jangan menunggu seluruh daftar aplikasi selesai diproses sebelum Activity dapat menampilkan Home Screen jika hal tersebut menyebabkan startup lambat.

Gunakan pola:

1. LauncherActivity start.
2. Tampilkan Home Screen/container ringan.
3. Load configuration dari MMKV.
4. Load app data di background.
5. Setelah data siap, update Home Grid di Main thread.

Jangan melakukan operasi berat di "onCreate()" Main thread.

---

27. ICON LOADING

Icon loading harus efisien.

Jika mengambil banyak icon membutuhkan waktu:

- lakukan pekerjaan berat di background
- gunakan cache memory ringan jika diperlukan
- update icon secara bertahap jika diperlukan

Jangan membuat disk database/cache besar hanya untuk icon.

Jangan menyimpan seluruh bitmap icon secara persistent.

---

28. PACKAGE CHANGES

Launcher harus menangani perubahan aplikasi:

- install
- uninstall
- update

Gunakan mekanisme Android yang sesuai.

Jangan polling daftar package.

Jika package tidak lagi tersedia:

- hapus dari Home
- hapus posisi terkait
- update UI

Jangan crash.

---

29. PERFORMANCE

Target:

- startup cepat
- scrolling smooth
- touch response cepat
- RAM rendah
- CPU idle saat launcher tidak digunakan

Hindari:

- blur
- animasi berat
- shadow berlebihan
- bitmap besar
- database
- network
- background polling
- service permanen
- unnecessary observers
- unnecessary dependency

---

30. UI FRAMEWORK

Gunakan UI framework existing jika sudah sesuai.

Jika XML/View sudah digunakan:

prioritaskan XML/View.

Jangan menambahkan Compose hanya untuk launcher sederhana.

Jika project sudah sepenuhnya menggunakan Compose dan migrasi ke View justru membuat project lebih kompleks:

pertahankan Compose.

Yang penting:

- ringan
- sederhana
- cepat
- stabil

---

31. SOURCE STRUCTURE

Gunakan struktur berdasarkan responsibility:

app/
└── src/
    └── main/
        ├── java/com/<EXISTING_PACKAGE>/
        │
        ├── launcher/
        │   ├── LauncherActivity
        │   └── LauncherApplication
        │
        ├── home/
        │   ├── HomeScreen
        │   ├── HomeController
        │   ├── HomeGrid
        │   ├── AppAdapter
        │   └── AppPositionManager
        │
        ├── apps/
        │   ├── AppRepository
        │   ├── AppInfo
        │   ├── AppLoader
        │   └── AppSorter
        │
        ├── settings/
        │   ├── SettingsActivity
        │   ├── SettingsRepository
        │   ├── GridSettings
        │   ├── IconSettings
        │   └── SortSettings
        │
        ├── storage/
        │   ├── PreferenceStore
        │   └── AppPositionStore
        │
        └── util/
            ├── IconUtils
            ├── PackageUtils
            └── LocaleUtils

        └── res/
            ├── drawable/
            ├── mipmap/
            ├── layout/
            ├── values/
            │   ├── strings.xml
            │   ├── dimens.xml
            │   └── styles.xml
            │
            └── values-id/
                └── strings.xml

Gunakan package existing project.

JANGAN mengganti package/application ID.

---

32. CLASS RESPONSIBILITY

LauncherActivity:

- entry point
- lifecycle
- menampilkan Home

HomeScreen:

- tampilan Home

HomeController:

- koordinasi Home

HomeGrid:

- grid/layout

AppAdapter:

- menampilkan app item

AppPositionManager:

- mengatur posisi

AppLoader:

- mengambil aplikasi

AppRepository:

- menyediakan data aplikasi

AppSorter:

- sorting

PreferenceStore:

- konfigurasi MMKV

AppPositionStore:

- posisi dan hidden state MMKV

SettingsActivity:

- settings UI

Jangan mencampur seluruh logic ke LauncherActivity.

---

33. JANGAN BUAT FILE GAJAH

Tidak boleh ada satu class yang menangani:

- app loading
- sorting
- grid
- settings
- storage
- icon
- launcher lifecycle

Pecah berdasarkan responsibility.

Jika file terlalu besar:

refactor menjadi beberapa class.

---

34. ANDROID HOME

LauncherActivity wajib dapat menjadi default Android HOME.

Manifest:

ACTION_MAIN

CATEGORY_HOME

CATEGORY_DEFAULT

LauncherActivity:

android:exported="true"

Pertahankan CATEGORY_LAUNCHER jika memang dibutuhkan project.

Pastikan launcher muncul sebagai pilihan default Home Android.

---

35. PACKAGE / APPLICATION ID

Pertahankan package/application ID existing.

JANGAN mengganti namespace/package hanya karena struktur contoh menggunakan:

com.<EXISTING_PACKAGE>

Struktur folder harus menyesuaikan package existing.

---

36. DEPENDENCY AUDIT

Pertahankan dependency yang masih digunakan.

Tambahkan MMKV jika belum ada.

Jangan menambahkan dependency untuk:

- database
- theme
- wallpaper
- networking
- analytics
- launcher framework
- icon pack

Hapus dependency hanya setelah dipastikan tidak digunakan.

---

37. CLEANUP FITUR LAMA

Audit dan hapus hanya implementation yang memang tidak sesuai scope launcher minimal.

Hapus fitur seperti:

- App Drawer
- Search
- Widget
- Feed
- News
- Floating Toolbox
- Custom Theme
- Custom Wallpaper
- Custom Gesture Engine

Jika fitur tersebut memang tidak lagi digunakan.

Jangan menghapus code yang masih diperlukan oleh launcher core.

---

38. RESOURCE CLEANUP

Hapus resource tidak terpakai setelah audit:

- drawer layout
- search layout
- widget resource
- theme resource
- wallpaper resource
- custom background
- floating toolbox resource

Jangan menghapus resource yang masih direferensikan.

---

39. NO EXTRA FEATURES

Jangan menambahkan:

- plugin system
- cloud sync
- backup cloud
- analytics
- ads
- network service
- news
- feed
- widget
- search
- app drawer
- custom wallpaper
- custom theme
- launcher marketplace

Tetap pada scope.

---

40. DEFAULT CONFIGURATION

Default:

Grid Columns:
5

Grid Rows:
5

Sorting:
A-Z

Icon:
default size yang sesuai density layar

Background:
transparent

Home:
vertical scrolling

---

41. BUILD VALIDATION

Setelah implementation selesai:

WAJIB menjalankan build.

Periksa:

1. Kotlin/Java compile
2. Gradle
3. Resource merger
4. Manifest merger
5. Duplicate resources
6. Duplicate classes
7. Missing imports
8. Unresolved references
9. MMKV initialization
10. LauncherActivity
11. HOME intent
12. exported=true
13. package/application ID

Perbaiki semua compile error.

Jangan menyatakan build berhasil sebelum build benar-benar dijalankan.

---

42. FINAL AUDIT

Pastikan:

[ ] Satu Home Screen

[ ] Vertical scrolling

[ ] Tidak ada horizontal pager

[ ] Tidak ada App Drawer

[ ] Tidak ada Search

[ ] Tidak ada Widget

[ ] Tidak ada Feed

[ ] Tidak ada Floating Toolbox

[ ] Tidak ada Custom Gesture Engine

[ ] Tidak ada Custom Theme System

[ ] Tidak ada ThemeManager

[ ] Tidak ada custom wallpaper

[ ] Wallpaper sistem terlihat

[ ] Icon aplikasi asli

[ ] Icon size configurable

[ ] Grid default 5x5

[ ] Custom Grid

[ ] A-Z

[ ] Z-A

[ ] Nama

[ ] Tanggal instalasi

[ ] Terbaru digunakan

[ ] Custom/manual sorting

[ ] Manual position persistent

[ ] Remove from Home

[ ] App Info

[ ] Uninstall

[ ] MMKV storage

[ ] Tidak ada database

[ ] Tidak ada persistent background service

[ ] Heavy processing tidak memblokir Main thread

[ ] UI update dilakukan di Main thread

[ ] Bahasa mengikuti sistem Android

[ ] Package/application ID tetap

[ ] LauncherActivity menjadi HOME

[ ] Build berhasil

---

43. FINAL REPORT

Setelah build berhasil, laporkan:

AUDIT

Ringkasan arsitektur project sebelum perubahan.

FILE DIBUAT

Daftar file baru.

FILE DIUBAH

Daftar file yang diubah dan alasan.

FILE DIHAPUS

Daftar file yang dihapus dan alasan.

DEPENDENCY

- dependency dipertahankan
- dependency ditambahkan
- dependency dihapus

STORAGE

Konfirmasi bahwa konfigurasi launcher menggunakan MMKV.

BACKGROUND PROCESSING

Jelaskan pekerjaan berat apa saja yang dipindahkan dari Main thread ke background processing.

Pastikan tidak ada background service permanen.

MANIFEST

Konfirmasi:

- ACTION_MAIN
- CATEGORY_HOME
- CATEGORY_DEFAULT
- exported=true
- package/application ID tetap

BUILD

Tampilkan:

- variant
- status build
- error
- perbaikan

FINAL RESULT

Launcher harus terasa seperti:

Home Screen Android yang sangat ringan, cepat, transparan, touch-first, dan langsung menampilkan aplikasi dalam grid vertikal.

Jangan menambahkan fitur lain di luar spesifikasi.
