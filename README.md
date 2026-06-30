# RTV Check 478 — Aplikasi Android Pengecekan RTV / Non-RTV

Aplikasi native Android (Kotlin + Jetpack Compose) untuk cek status RTV / Non-RTV
barang dengan scan barcode (UPC) atau input manual SKU.

## Fitur

- Scan barcode pakai kamera (CameraX + ZXing) — auto lookup begitu barcode terbaca
- Input manual UPC atau SKU lewat keyboard, lalu tekan Enter / tombol Cari
- Saat ditemukan, otomatis tampil:
  - Description
  - Status
  - Supplier No
  - Supplier Name
  - Keterangan (RTV / NON RTV / RTV PENGAJUAN / TUKAR GULING) — dengan badge warna
- Database SQLite embedded di dalam APK (offline, tidak butuh internet/WiFi toko)
  - 12.017 item dari `Data_Suplier__RTV_NON_RTV_.xlsx`
  - Lookup pertama coba cocokkan UPC, kalau tidak ketemu coba cocokkan SKU

## Cara build

1. Buka folder `rtv_app` ini di Android Studio (versi terbaru, minimal Hedgehog 2023.1+)
2. Tunggu Gradle sync selesai (akan otomatis download dependency)
3. Klik Run ▶ ke device/emulator, atau Build > Build Bundle(s)/APK(s) > Build APK(s)
4. APK hasil build ada di `app/build/outputs/apk/debug/app-debug.apk`

Minimum Android: 7.0 (API 24) — kompatibel dengan kebanyakan scanner Android lama
seperti Motorola/Zebra.

## Struktur penting

```
app/src/main/assets/items.db          <- database SQLite hasil convert dari Excel
app/src/main/java/.../data/           <- model data + helper database
app/src/main/java/.../scanner/        <- komponen kamera scan barcode
app/src/main/java/.../MainActivity.kt <- UI utama (Compose)
app/src/main/java/.../MainViewModel.kt<- logic lookup
```

## Update data Excel di kemudian hari

Kalau data supplier/RTV berubah, tinggal ganti `items.db`:

1. Jalankan ulang script convert Excel -> SQLite (lihat catatan di bawah)
2. Replace file `app/src/main/assets/items.db`
3. Naikkan `DB_VERSION` di `ItemDatabaseHelper.kt` (misal dari 1 ke 2) supaya app
   tahu harus re-copy database baru ke device, bukan pakai database lama yang
   sudah ter-cache di HP
4. Build ulang APK

Script convert (Python, butuh pandas + openpyxl):

```python
import pandas as pd, sqlite3
df = pd.read_excel('Data_Suplier__RTV_NON_RTV_.xlsx', sheet_name='Sheet1')
df.columns = ['upc','sku','description','status','supplier_no','supplier_name','keterangan']
for c in df.columns:
    df[c] = df[c].astype(str).str.strip().replace('nan', '')
conn = sqlite3.connect('items.db')
df.to_sql('items', conn, if_exists='replace', index=False)
conn.execute('CREATE INDEX idx_upc ON items(upc)')
conn.execute('CREATE INDEX idx_sku ON items(sku)')
conn.commit()
conn.close()
```

## Catatan teknis data

- Total 12.017 baris, kolom: Upc, SKU, SKU Desc, STS, SupplierNo, Supplier Name, KETERANGAN
- SKU 100% unik (dipakai sebagai fallback key)
- UPC unik untuk semua baris yang terisi; ada 33 baris dengan UPC kosong (lookup
  baris ini hanya bisa lewat SKU)
- Nilai Keterangan yang ada: `RTV ( PENGAJUAN )`, `RTV ( PENGAJUAN)`, `RTV`,
  `NON RTV`, `TUKAR GULING`, dan kosong (33 baris)
