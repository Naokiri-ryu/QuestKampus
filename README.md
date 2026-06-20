# 🐉 QuestKampus: RPG-Themed Productivity App

**QuestKampus** adalah aplikasi manajemen tugas (To-Do List) berbasis Android yang menggabungkan elemen RPG (Role-Playing Game) untuk meningkatkan produktivitas mahasiswa. Ubah tugas kuliahmu menjadi misi legendaris, kumpulkan EXP, naikkan level, dan selesaikan tantangan bersama teman dalam satu *Party*!

---

## 🌟 Fitur Utama

### 1. Sistem RPG (Gamifikasi)
*   **Hero Stats:** Setiap user memiliki profil dengan bar **HP (Health Points)**, **EXP (Experience)**, **Level**, dan **Gold**.
*   **Rank Quest:** Tugas dikategorikan berdasarkan tingkat kesulitan:
    *   **Rank S (Legendary):** Proyek Besar / UAS (+500 EXP).
    *   **Rank A (Epic):** Laporan / UTS (+300 EXP).
    *   **Rank B (Elite):** Tugas Mingguan / Quiz (+150 EXP).
    *   **Rank C (Common):** Catatan / Tugas Harian (+50 EXP).
*   **Leveling System:** Raih EXP untuk naik level. Max EXP meningkat secara eksponensial seiring bertambahnya level.
*   **Health & Penalty:** 
    *   **Heal:** Menyelesaikan quest memulihkan HP Anda.
    *   **Damage:** Melewatkan deadline akan mengurangi HP secara otomatis. Jangan sampai HP-mu menyentuh angka 0!

### 2. Manajemen Quest (Tugas)
*   **Journal Quest:** Halaman detail untuk setiap tugas yang mencakup deskripsi, lampiran soal, dan deadline.
*   **Submission System:** Serahkan bukti penyelesaian berupa **Foto** (Upload) atau **Link** (Google Drive/GitHub).
*   **Filtering:** Urutkan tugas berdasarkan status: Aktif, Selesai, atau Gagal.
*   **Background Tracking:** Menggunakan *WorkManager* untuk memantau deadline secara real-time bahkan saat aplikasi ditutup.

### 3. Party System (Kolaborasi)
*   **Create/Join Party:** Buat kelompok belajar atau bergabung menggunakan PIN unik 6-digit.
*   **Party HP:** Kelompok memiliki bar HP bersama. Kegagalan anggota kelompok memengaruhi nyawa kelompok!
*   **Shared Quest Board:** Misi khusus yang hanya bisa diakses dan dikerjakan oleh anggota kelompok.
*   **Member List:** Pantau level dan kontribusi anggota dalam kelompokmu.

### 4. Notifikasi & UI
*   **Notification Helper:** Notifikasi saat berhasil naik level atau quest mendekati deadline.
*   **RPG UI Theme:** Antarmuka gelap yang elegan dengan aksen emas dan ikon bertema fantasi medieval.

---

## 🛠 Teknologi yang Digunakan

*   **Bahasa:** Kotlin
*   **UI Framework:** Android XML dengan ViewBinding
*   **Backend:** 
    *   **Firebase Auth:** Autentikasi user (Email & Password).
    *   **Firebase Firestore:** Database real-time untuk data Quest, User, dan Party.
    *   **Firebase Storage:** Penyimpanan bukti gambar dan avatar.
*   **Libraries:**
    *   **Glide:** Loading gambar dan avatar secara asinkron.
    *   **WorkManager:** Menangani logika penalti deadline di background.
    *   **Material Design 3:** Komponen UI modern.

---

## 🚀 Instalasi & Setup

1.  **Clone Repositori:**
    ```bash
    git clone https://github.com/username/QuestKampus.git
    ```
2.  **Buka di Android Studio:**
    Pastikan menggunakan versi terbaru (Ladybug atau lebih tinggi).
3.  **Konfigurasi Firebase:**
    *   Buat project baru di [Firebase Console](https://console.firebase.google.com/).
    *   Aktifkan *Authentication*, *Firestore*, dan *Storage*.
    *   Unduh file `google-services.json` dan letakkan di dalam folder `app/`.
4.  **Build & Run:**
    Hubungkan perangkat Android atau emulator, lalu tekan **Run**.

---

## 📂 Struktur Folder Utama

```text
com.example.questkampus
├── DeadlineWorker.kt      # Logika background check deadline
├── MainActivity.kt        # Dashboard utama & Quest Board
├── PartyActivity.kt       # Manajemen pembuatan/gabung kelompok
├── PartyDetailActivity.kt # Detail kelompok, anggota, & misi grup
├── QuestDetailActivity.kt # Detail tugas, edit, & submission
├── QuestAdapter.kt        # List adapter untuk tampilan quest
├── RpgTheme.kt            # Logika perhitungan EXP, HP, & Rank
└── NotificationHelper.kt  # Pengelola notifikasi sistem
```

---

## 📝 Kontribusi
Proyek ini dikembangkan untuk membantu mahasiswa tetap termotivasi dalam mengerjakan tugas. Kontribusi berupa *bug report* atau *pull request* sangat dipersilakan!

**"Selesaikan Questmu, Jadilah Legenda!"** ⚔️🛡️
