# Hidden Apps Boundary Post-Fix Audit

## 1. Objective
Memperbaiki hutang arsitektur (*Architectural Debt*) di dalam `HiddenAppsDialog` yang sebelumnya secara langsung memanggil `AppDataSource` (menghubungi OS IPC) dan melakukan pemetaan (mapping) tipe objek *framework* OS `LauncherActivityInfo` menjadi *domain model* UI `AppInfo`. Tujuannya adalah merestorasi prinsip *Single Responsibility Principle* (SRP) pada lapisan UI/Dialog.

## 2. Before Architecture
```text
SettingsActivity 
  └── HiddenAppsDialog (UI)
        ├── AppDataSource (OS API IPC)
        └── Mapping LauncherActivityInfo -> AppInfo secara manual di dalam UI
```
*UI secara vulgar menyentuh dan membedah struktur OS Data Source.*

## 3. After Architecture
```text
SettingsActivity (Orchestrator Lifecycle)
  └── GetInstalledAppsUseCase (Business Logic & Mapper)
        └── AppDataSource (OS API IPC)
  └── HiddenAppsDialog (Murni UI/Presentation)
```
*Dialog UI kini murni disuapi `List<AppInfo>` oleh Activity melalui perantara Use Case.*

## 4. Files Changed
- **Created**: `app/src/main/java/com/silauncer/cepat/apps/GetInstalledAppsUseCase.kt`
- **Modified**: `app/src/main/java/com/silauncer/cepat/settings/HiddenAppsDialog.kt`
- **Modified**: `app/src/main/java/com/silauncer/cepat/settings/SettingsActivity.kt`

## 5. Dependency Graph
```text
LauncherActivity (Aman)
  ...
SettingsActivity
  ├── GetInstalledAppsUseCase
  │     └── AppDataSource
  └── HiddenAppsDialog
        └── AppInfo (Domain Model)
```
*Tidak ada lagi aliran dependensi dari UI (HiddenAppsDialog) menuju `AppDataSource` maupun `LauncherActivityInfo`.*

## 6. Call Graph
```text
[MAIN] SettingsActivity (Klik "Manage Hidden Apps")
   ↓
[MAIN] lifecycleScope.launch
   ↓
[MAIN -> SUSPEND] GetInstalledAppsUseCase.invoke()
   ↓
[MAIN -> IO] AppDataSource.getActivities() (OS IPC: LauncherApps)
   ↓
[MAIN -> SUSPEND] GetInstalledAppsUseCase (Menerima Hasil & Mapping)
   ↓
[MAIN] HiddenAppsDialog.show(List<AppInfo>) (Render Dialog & Interaksi)
```

## 7. SRP Audit
- **HiddenAppsDialog**: HANYA presentation. Merakit UI dari array string dan checkbox, dan memancarkan aksi simpan (*Save*) ke `LauncherPreferences`. Tidak ada sentuhan OS. (**PASS**)
- **GetInstalledAppsUseCase**: HANYA perantara bisnis (*Orchestration & Mapping*). Bertanya pada sumber data dan memecah kodenya menjadi cetak biru *AppInfo* siap pakai. Tidak ada referensi View. (**PASS**)
- **AppDataSource**: HANYA sebagai gerbang *Android LauncherApps*. (**PASS**)
- **AppStateHolder & LauncherAppController**: Tak tersentuh dan tetap mempertahankan kemurnian SRP mereka (Evaluasi BUG-01). (**PASS**)

## 8. Threading Audit
Operasi I/O berat murni tersudut ke dalam coroutine bentukan `SettingsActivity.lifecycleScope.launch`, sebelum akhirnya terjun ke `Dispatchers.IO` lewat *DataSource*. Main Thread (UI) dijamin tidak akan pernah membeku *(freeze)* selama Dialog ini memanen daftar aplikasi tersembunyi.

## 9. Behavior Regression Audit
Aplikasi berjalan konsisten 100% dengan tingkah aslinya:
- Jumlah dan nama aplikasi terpasang tampil presisi.
- Filter ganda *(Duplicate prevention)* masih dijaga akurat oleh metode pemetaan `.distinctBy { it.componentName }`.
- Dialog masih tetap mengingat status kotak centang *(checked apps)* yang diikat ke `LauncherPreferences`.

## 10. BUG-01 Regression Check
- `AppStateHolder` mutlak menguasai status (state).
- `Mutex` tetap menjadi penjaga memori.
- Alur *loading* pergerakan *(Package Events)* tak tergoyahkan.
*(VERIFIED - NO REGRESSION)*

## 11. BUG-02 Regression Check
Proses asinkron ganda dari *Icon Loading* di `AppAdapter` beserta *In-Flight Deduplication* nya tetap utuh. Tidak sebaris kode pun yang mengatur `IconLoader` maupun *RecyclerView* disentuh. 
*(BUG-02 PRESERVED)*

## 12. Remaining Architecture Debt
Di dalam audit terdahulu (BUG-01), kita mengetahui bahwa `AppStateHolder` juga masih mengambil parameter fungsi berupa `LauncherActivityInfo` sebelum memetakannya ke `AppInfo` (seperti saat *addActivityLocked*). Idealisme arsitektur menyarankan *StateHolder* pun jangan membedah struktur OS, ia harusnya hanya menerima *Domain Model* murni `AppInfo`. Namun karena rambu-rambu larangan mengubah *AppStateHolder* secara tegas diterapkan di sesi perbaikan batas `HiddenAppsDialog` ini, hutang kelas tersebut didiamkan (Sengaja Ditinggalkan) demi kestabilan perbaikan yang terisolasi.

## 13. Build/Test Result
- Build Terminal: **BUILD SUCCESSFUL**.
- Seluruh perbaikan modul dan resolusi *import library* beroperasi sempurna tanpa regresi *compile-time*.

## 14. Final Verdict
**VERIFIED** (Perbaikan pada *Architectural Debt* antarmuka **HiddenAppsDialog Boundary** lulus standar SRP. UI tidak lagi tercemar logika akses OS, tanpa mengganggu stabilitas Bug-01 dan Bug-02).
