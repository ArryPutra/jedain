package com.example.jedain

import java.util.Calendar

enum class ActivityCategory(val label: String) {
    STUDY_WORK("Fokus Tugas & Kerja"),
    OUTDOOR("Suasana Luar"),
    EXERCISE("Gerak & Olahraga"),
    SELF_CARE("Beres-Beres & Diri"),
    SKILL_HOBBY("Skill & Hobi"),
    REST_SOCIAL("Istirahat & Sosial")
}

data class ActivityPrompt(
    val id: String,
    val category: ActivityCategory,
    val headline: String,
    val subtitle: String,
    val preferredHours: IntRange
)

object DailyActivityPromptProvider {

    val allPrompts: List<ActivityPrompt> = listOf(
        // --- DINI HARI (00.00 - 04.59: 0..4) ---
        // Tidur, istirahat, relaksasi, menjauhkan HP, hidrasi
        ActivityPrompt(
            id = "dini_sleep_now",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Pejamkan mata dan tidur",
            subtitle = "Letakkan HP jauh dari jangkauan kasur dan tarik selimut untuk istirahat total.",
            preferredHours = 0..4
        ),
        ActivityPrompt(
            id = "dini_deep_breathing",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Latihan napas relaksasi 4-7-8",
            subtitle = "Tarik napas dalam, hembuskan perlahan, dan biarkan pikiranmu tenang tanpa layar.",
            preferredHours = 0..4
        ),
        ActivityPrompt(
            id = "dini_eye_palming",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Istirahatkan mata total",
            subtitle = "Tutup kelopak mata dan tempelkan telapak tangan hangat untuk meredakan lelah layar.",
            preferredHours = 0..4
        ),
        ActivityPrompt(
            id = "dini_quiet_mind",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Heningkan pikiran malam ini",
            subtitle = "Dengarkan keheningan malam tanpa membuka aplikasi apa pun di ponsel.",
            preferredHours = 0..4
        ),
        ActivityPrompt(
            id = "dini_night_water",
            category = ActivityCategory.SELF_CARE,
            headline = "Minum segelas air putih",
            subtitle = "Teguk air hangat untuk menghidrasi tubuh sebelum melanjutkan tidur dengan nyenyak.",
            preferredHours = 0..4
        ),
        ActivityPrompt(
            id = "dini_night_pillow",
            category = ActivityCategory.SELF_CARE,
            headline = "Atur bantal & selimut",
            subtitle = "Tepuk bantal agar empuk, rapikan selimut, dan cari posisi tidur yang paling nyaman.",
            preferredHours = 0..4
        ),
        ActivityPrompt(
            id = "dini_release_tension",
            category = ActivityCategory.SELF_CARE,
            headline = "Lepaskan ketegangan tubuh",
            subtitle = "Kendurkan otot bahu dan rahang yang kaku sambil berbaring santai di kasur.",
            preferredHours = 0..4
        ),

        // --- PAGI (05.00 - 09.59: 5..9) ---
        // Mandi, sarapan, merapikan tempat tidur, olahraga ringan, udara segar
        ActivityPrompt(
            id = "pagi_plan_today",
            category = ActivityCategory.STUDY_WORK,
            headline = "Catat 3 target hari ini",
            subtitle = "Tulis tiga hal paling penting yang ingin kamu selesaikan sebelum siang hari.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_check_schedule",
            category = ActivityCategory.STUDY_WORK,
            headline = "Cek agenda hari ini",
            subtitle = "Buka kalender sebentar untuk memastikan tidak ada janji atau tenggat yang terlewat.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_morning_air",
            category = ActivityCategory.OUTDOOR,
            headline = "Hirup udara segar pagi",
            subtitle = "Buka jendela atau melangkah ke luar kamar selama beberapa menit tanpa membawa HP.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_porch_sit",
            category = ActivityCategory.OUTDOOR,
            headline = "Duduk sebentar di teras",
            subtitle = "Nikmati hangatnya sinar matahari pagi sambil membiarkan mata istirahat dari layar.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_water_plants",
            category = ActivityCategory.OUTDOOR,
            headline = "Siram tanaman di halaman",
            subtitle = "Ambil wadah air dan siram tanaman sambil menikmati suasana pagi yang tenang.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_morning_jog",
            category = ActivityCategory.EXERCISE,
            headline = "Jogging santai di sekitar",
            subtitle = "Gunakan sepatu lari dan lakukan jogging ringan selama 15 menit keliling komplek.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_morning_stretch",
            category = ActivityCategory.EXERCISE,
            headline = "Lakukan peregangan tubuh",
            subtitle = "Regangkan otot punggung, lengan, dan kaki setelah bangun agar badan terasa segar.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_jumping_jacks",
            category = ActivityCategory.EXERCISE,
            headline = "Gerakan pemanasan tubuh",
            subtitle = "Lakukan beberapa jumping jacks atau senam ringan untuk memompa sirkulasi darah.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_make_bed",
            category = ActivityCategory.SELF_CARE,
            headline = "Rapikan tempat tidur",
            subtitle = "Kencangkan sprei dan susun bantal agar kamarmu rapi menyambut hari baru.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_refresh_shower",
            category = ActivityCategory.SELF_CARE,
            headline = "Mandi air segar & ganti pakaian",
            subtitle = "Basuh tubuh dengan air segar agar pikiran jernih dan siap menjalani aktivitas.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_morning_read",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Baca beberapa halaman buku",
            subtitle = "Buka buku bacaan fisik dan nikmati bab baru tanpa gangguan notifikasi ponsel.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_vocab_practice",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Latihan kosakata bahasa asing",
            subtitle = "Tulis dan hafalkan 3 kosakata baru dalam bahasa yang sedang kamu pelajari.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_mindful_breakfast",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Sarapan tenang tanpa layar",
            subtitle = "Nikmati sarapan pagimu perlahan tanpa meletakkan ponsel di dekat piring.",
            preferredHours = 5..9
        ),
        ActivityPrompt(
            id = "pagi_greet_family",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Sapa orang di rumah",
            subtitle = "Ucapkan salam pagi atau tanyakan rencana hari ini ke anggota keluarga di rumah.",
            preferredHours = 5..9
        ),

        // --- MENJELANG SIANG (10.00 - 12.59: 10..12) ---
        // Tugas, pekerjaan, belajar, aktivitas produktif
        ActivityPrompt(
            id = "siang_focus_15m",
            category = ActivityCategory.STUDY_WORK,
            headline = "Cicil tugas 15 menit",
            subtitle = "Buka satu tugas yang tertunda dan fokus kerjakan tanpa membuka aplikasi lain.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_clean_notes",
            category = ActivityCategory.STUDY_WORK,
            headline = "Rapikan catatan materi",
            subtitle = "Tulis ulang poin penting atau rapikan file catatan agar mudah dipelajari kembali.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_clear_inbox",
            category = ActivityCategory.STUDY_WORK,
            headline = "Balas pesan pekerjaan penting",
            subtitle = "Buka pesan kuliah atau kerjaan, balas yang prioritas, lalu tutup kembali aplikasinya.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_check_submission",
            category = ActivityCategory.STUDY_WORK,
            headline = "Periksa kembali draf tugas",
            subtitle = "Baca ulang pekerjaan sebelum dikumpulkan untuk memastikan tidak ada bagian yang kurang.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_step_outside",
            category = ActivityCategory.OUTDOOR,
            headline = "Melangkah keluar ruangan",
            subtitle = "Tinggalkan meja belajar sejenak untuk melihat langit dan pepohonan hijau.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_front_yard",
            category = ActivityCategory.OUTDOOR,
            headline = "Jalan ke teras depan",
            subtitle = "Berdiri di depan rumah selama dua menit untuk meluruskan kaki dan menghirup udara.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_shoulder_neck",
            category = ActivityCategory.EXERCISE,
            headline = "Regangkan leher & bahu",
            subtitle = "Putar bahu dan miringkan leher perlahan untuk meredakan kaku akibat menatap layar.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_desk_standup",
            category = ActivityCategory.EXERCISE,
            headline = "Berdiri dan jalan di tempat",
            subtitle = "Bangkit dari kursi, regangkan pinggang, dan melangkah santai selama beberapa menit.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_desk_cleanup",
            category = ActivityCategory.SELF_CARE,
            headline = "Bereskan meja kerja 5 menit",
            subtitle = "Singkirkan gelas kosong dan kertas yang berantakan dari area belajarmu.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_wash_face",
            category = ActivityCategory.SELF_CARE,
            headline = "Cuci muka dengan air sejuk",
            subtitle = "Basuh wajah di wastafel untuk mengusir rasa kantuk dan menyegarkan pandangan.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_code_tutorial",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Coba tutorial baru 15 menit",
            subtitle = "Tulis eksperimen kode kecil atau coba fitur baru dari tutorial yang tersimpan.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_explore_tool",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Pelajari shortcut aplikasi",
            subtitle = "Coba 3 tombol pintasan baru di IDE atau software yang biasa kamu pakai bekerja.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_tea_break",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Seduh teh hangat tanpa HP",
            subtitle = "Tunggu air mendidih, nikmati aroma minuman hangat sambil mengistirahatkan mata.",
            preferredHours = 10..12
        ),
        ActivityPrompt(
            id = "siang_hydrate_mindful",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Minum air putih perlahan",
            subtitle = "Ambil segelas air, minum perlahan sambil melatih kesadaran diri tanpa layar.",
            preferredHours = 10..12
        ),

        // --- SIANG-SORE (13.00 - 16.59: 13..16) ---
        // Menyelesaikan pekerjaan, jalan santai, olahraga, hobi
        ActivityPrompt(
            id = "sore_finish_pending",
            category = ActivityCategory.STUDY_WORK,
            headline = "Tuntaskan satu potongan kerja",
            subtitle = "Pilih bagian pekerjaan terkecil yang tertunda dan selesaikan tanpa distraksi.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_continue_project",
            category = ActivityCategory.STUDY_WORK,
            headline = "Lanjutkan proyek pekerjaan",
            subtitle = "Buka file tugas atau berkas kerjaan dan buat progres nyata selama 20 menit.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_read_reference",
            category = ActivityCategory.STUDY_WORK,
            headline = "Baca satu materi referensi",
            subtitle = "Pelajari dokumen atau modul bacaan tanpa tergoda melirik linimasa media sosial.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_afternoon_walk",
            category = ActivityCategory.OUTDOOR,
            headline = "Jalan santai di luar rumah",
            subtitle = "Keluar rumah selama 15 menit untuk menikmati suasana sore tanpa memegang ponsel.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_balcony_rest",
            category = ActivityCategory.OUTDOOR,
            headline = "Nikmati angin sore di teras",
            subtitle = "Duduk santai di teras sambil merasakan semilir angin sore yang menyejukkan.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_yard_cleanup",
            category = ActivityCategory.OUTDOOR,
            headline = "Bantu bersihkan halaman",
            subtitle = "Sapu daun kering di teras atau rapikan sandal di luar pintu rumah.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_brisk_walk",
            category = ActivityCategory.EXERCISE,
            headline = "Jalan cepat keliling komplek",
            subtitle = "Langkahkan kaki dengan ritme cepat selama 15 menit agar tubuh tidak loyo.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_pushups_light",
            category = ActivityCategory.EXERCISE,
            headline = "Push-up beberapa repetisi",
            subtitle = "Lakukan 10 push-up sesuai kemampuan untuk mengembalikan fokus dan energi.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_squats_energy",
            category = ActivityCategory.EXERCISE,
            headline = "Latihan squat 15 kali",
            subtitle = "Gerakkan otot kaki dengan squat teratur di sela-sela waktu istirahat bekerja.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_organize_files",
            category = ActivityCategory.SELF_CARE,
            headline = "Rapikan folder di laptop",
            subtitle = "Hapus file unduhan yang tidak terpakai dan kelompokkan dokumen ke folder rapi.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_separate_laundry",
            category = ActivityCategory.SELF_CARE,
            headline = "Pisahkan pakaian kotor",
            subtitle = "Masukkan baju ke keranjang cucian dan gantung pakaian yang masih bisa dipakai.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_sketch_doodle",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Coretan sketsa di kertas",
            subtitle = "Ambil pulpen dan kertas, gambar pola acak atau objek di meja untuk relaksasi pikiran.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_personal_project",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Kerjakan proyek hobi sejenak",
            subtitle = "Lanjutkan eksperimen kreatif pribadimu sedikit demi sedikit tanpa tekanan.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_listen_podcast",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Dengarkan podcast edukatif",
            subtitle = "Putar episode podcast menarik sambil bersandar di kursi tanpa scrolling medsos.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_power_nap",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Power nap singkat 15 menit",
            subtitle = "Pejamkan mata sejenak di kursi atau kasur untuk memulihkan kesegaran tubuh.",
            preferredHours = 13..16
        ),
        ActivityPrompt(
            id = "sore_chat_friend",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Kirim pesan kabar ke teman",
            subtitle = "Tanyakan kabar teman dekat lewat chat singkat tanpa scrolling linimasa.",
            preferredHours = 13..16
        ),

        // --- PETANG-MALAM (17.00 - 20.59: 17..20) ---
        // Makan malam, keluarga, beres-beres, kegiatan santai
        ActivityPrompt(
            id = "petang_review_today",
            category = ActivityCategory.STUDY_WORK,
            headline = "Evaluasi capaian hari ini",
            subtitle = "Tandai tugas yang sudah tuntas dan buat catatan singkat untuk persiapan esok.",
            preferredHours = 17..20
        ),
        ActivityPrompt(
            id = "petang_sunset_sky",
            category = ActivityCategory.OUTDOOR,
            headline = "Lihat suasana langit petang",
            subtitle = "Amati perubahan warna langit senja dari teras depan tanpa sibuk merekam kamera.",
            preferredHours = 17..20
        ),
        ActivityPrompt(
            id = "petang_nightfall_breeze",
            category = ActivityCategory.OUTDOOR,
            headline = "Rasakan sejuknya awal malam",
            subtitle = "Duduk sebentar di teras rumah menikmati udara dingin sebelum menutup pintu.",
            preferredHours = 17..20
        ),
        ActivityPrompt(
            id = "petang_light_yoga",
            category = ActivityCategory.EXERCISE,
            headline = "Peregangan santai petang",
            subtitle = "Lakukan peregangan otot ringan di lantai untuk melemaskan badan setelah seharian beraktivitas.",
            preferredHours = 17..20
        ),
        ActivityPrompt(
            id = "petang_wrist_ankle",
            category = ActivityCategory.EXERCISE,
            headline = "Putar pergelangan tangan & kaki",
            subtitle = "Lemaskan sendi pergelangan yang pegal akibat mengetik dan memegang ponsel.",
            preferredHours = 17..20
        ),
        ActivityPrompt(
            id = "petang_prep_tomorrow",
            category = ActivityCategory.SELF_CARE,
            headline = "Siapkan perlengkapan besok",
            subtitle = "Tata barang bawaan, buku, atau charger ke dalam tas agar pagimu tidak terburu-buru.",
            preferredHours = 17..20
        ),
        ActivityPrompt(
            id = "petang_tidy_living_room",
            category = ActivityCategory.SELF_CARE,
            headline = "Rapikan ruang tengah",
            subtitle = "Kembalikan bantal sofa dan letakkan barang yang berserakan ke tempat asalnya.",
            preferredHours = 17..20
        ),
        ActivityPrompt(
            id = "petang_play_instrument",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Mainkan alat musik favorit",
            subtitle = "Pegang gitar atau keyboard dan mainkan lagu kesukaan untuk melepas kepenatan.",
            preferredHours = 17..20
        ),
        ActivityPrompt(
            id = "petang_family_dinner",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Makan malam bersama keluarga",
            subtitle = "Simpan HP di meja lain dan nikmati kebersamaan makan malam dengan orang rumah.",
            preferredHours = 17..20
        ),
        ActivityPrompt(
            id = "petang_evening_chat",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Ngobrol santai tanpa layar",
            subtitle = "Ajak orang rumah atau teman berbincang santai tanpa diselingi mengecek ponsel.",
            preferredHours = 17..20
        ),

        // --- LARUT MALAM (21.00 - 23.59: 21..23) ---
        // Persiapan tidur, membaca ringan, refleksi, menjauhkan HP
        ActivityPrompt(
            id = "malam_night_skincare",
            category = ActivityCategory.SELF_CARE,
            headline = "Cuci muka & gosok gigi",
            subtitle = "Bersihkan wajah dan sikat gigi secara menyeluruh agar siap beristirahat dengan segar.",
            preferredHours = 21..23
        ),
        ActivityPrompt(
            id = "malam_dim_lights",
            category = ActivityCategory.SELF_CARE,
            headline = "Redupkan lampu kamar",
            subtitle = "Matikan lampu utama dan nyalakan lampu tidur temaram agar mata bersiap istirahat.",
            preferredHours = 21..23
        ),
        ActivityPrompt(
            id = "malam_bedtime_reading",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Membaca buku non-digital",
            subtitle = "Baca beberapa lembar buku fisik yang menenangkan sebelum mematikan lampu.",
            preferredHours = 21..23
        ),
        ActivityPrompt(
            id = "malam_journal_night",
            category = ActivityCategory.SKILL_HOBBY,
            headline = "Catat 3 hal baik hari ini",
            subtitle = "Tulis hal-hal kecil yang kamu syukuri hari ini di buku catatan sebelum tidur.",
            preferredHours = 21..23
        ),
        ActivityPrompt(
            id = "malam_screen_curfew",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Jauhkan HP dari tempat tidur",
            subtitle = "Aktifkan mode senyap dan letakkan ponsel di meja yang tidak terjangkau dari kasur.",
            preferredHours = 21..23
        ),
        ActivityPrompt(
            id = "malam_wind_down",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Mulai bersiap tidur malam",
            subtitle = "Hentikan aktivitas yang melelahkan dan biarkan tubuh berbaring rileks menuju tidur.",
            preferredHours = 21..23
        ),
        ActivityPrompt(
            id = "malam_quiet_reflection",
            category = ActivityCategory.REST_SOCIAL,
            headline = "Lepaskan beban pikiran",
            subtitle = "Tarik napas panjang, relakan hal yang belum selesai, dan izinkan dirimu tidur lelap.",
            preferredHours = 21..23
        )
    )

    fun getCurrentLocalHour(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }

    fun getNextPrompt(
        currentHour: Int,
        excludeId: String? = null
    ): ActivityPrompt {
        val safeHour = currentHour.coerceIn(0, 23)
        val timeMatchingPrompts = allPrompts.filter { safeHour in it.preferredHours }
        val pool = if (timeMatchingPrompts.isNotEmpty()) timeMatchingPrompts else allPrompts

        // Hindari ID yang sama jika ada alternatif
        val candidatesWithoutExclude = if (pool.size > 1 && excludeId != null) {
            pool.filter { it.id != excludeId }
        } else {
            pool
        }

        // Variasi: prioritaskan kategori berbeda jika tersedia
        val currentPrompt = allPrompts.find { it.id == excludeId }
        val differentCategoryCandidates = if (currentPrompt != null) {
            candidatesWithoutExclude.filter { it.category != currentPrompt.category }
        } else {
            emptyList()
        }

        val finalCandidates = if (differentCategoryCandidates.isNotEmpty()) {
            differentCategoryCandidates
        } else {
            candidatesWithoutExclude
        }

        return finalCandidates.randomOrNull() ?: allPrompts.first()
    }
}
