package id.alquranhadits.data

import android.content.Context
import android.database.Cursor
import java.time.LocalDate

data class Surah(val n: Int, val ar: String, val latin: String, val arti: String, val type: String, val ayat: Int) {
    val tempat get() = if (type == "meccan") "Makkiyah" else "Madaniyah"
}

data class Ayah(val id: Int, val surah: Int, val ayah: Int, val ar: String, val idn: String, val surahLatin: String) {
    val ref get() = "QS. $surahLatin [$surah]: $ayah"
    val key get() = "a:$id"
}

data class Kitab(val id: Int, val slug: String, val nama: String, val jumlah: Int) {
    val pendek get() = nama.substringAfter(' ')
}

data class Hadith(val id: Int, val kitab: Int, val kitabNama: String, val nomor: Int, val ar: String, val idn: String, val ringkas: String) {
    val ref get() = "HR. ${kitabNama.substringAfter(' ')} No. $nomor"
    val key get() = "h:$id"
}

data class LinkedHadith(val h: Hadith, val jenis: Int, val skor: Double)
data class LinkedAyah(val a: Ayah, val jenis: Int, val skor: Double)
data class RelatedPair(val a: Ayah, val h: Hadith, val jenis: Int)
data class Topik(val id: Int, val nama: String, val kata: String)

data class SearchResult(
    val ayat: List<Ayah>, val ayatTotal: Int,
    val hadits: List<Hadith>, val haditsTotal: Int,
    val pairs: List<RelatedPair>, val pairsExact: Boolean,
    val directAyah: Ayah?, val directHadith: Hadith?,
)

const val JENIS_KUTIP = 1
const val JENIS_TEMA = 2

class Repo private constructor(ctx: Context) {
    private val db = Db.get(ctx)

    companion object {
        @Volatile private var inst: Repo? = null
        fun get(ctx: Context): Repo = inst ?: synchronized(this) {
            inst ?: Repo(ctx.applicationContext).also { inst = it }
        }
    }

    private fun <T> query(sql: String, args: Array<String>? = null, map: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        db.rawQuery(sql, args).use { c -> while (c.moveToNext()) out.add(map(c)) }
        return out
    }

    private fun count(sql: String, args: Array<String>?): Int =
        db.rawQuery(sql, args).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    // ---------- Al-Qur'an ----------
    private val AYAH_COLS = "a.id, a.surah, a.ayah, a.ar, a.idn, s.latin"
    private fun ayahOf(c: Cursor) = Ayah(c.getInt(0), c.getInt(1), c.getInt(2), c.getString(3), c.getString(4), c.getString(5))

    val surahList: List<Surah> by lazy {
        query("SELECT n, ar, latin, arti, type, ayat FROM surah ORDER BY n") {
            Surah(it.getInt(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5))
        }
    }

    fun surah(n: Int): Surah = surahList[n - 1]

    fun ayatOfSurah(n: Int): List<Ayah> =
        query("SELECT $AYAH_COLS FROM ayah a JOIN surah s ON s.n = a.surah WHERE a.surah = ? ORDER BY a.ayah", arrayOf("$n"), ::ayahOf)

    fun ayah(id: Int): Ayah? =
        query("SELECT $AYAH_COLS FROM ayah a JOIN surah s ON s.n = a.surah WHERE a.id = ?", arrayOf("$id"), ::ayahOf).firstOrNull()

    fun ayahByRef(surah: Int, ayah: Int): Ayah? = ayah(surah * 1000 + ayah)

    fun ayatByIds(ids: List<Int>): List<Ayah> {
        if (ids.isEmpty()) return emptyList()
        val m = query("SELECT $AYAH_COLS FROM ayah a JOIN surah s ON s.n = a.surah WHERE a.id IN (${ids.joinToString(",")})", null, ::ayahOf)
            .associateBy { it.id }
        return ids.mapNotNull { m[it] }
    }

    /** Jumlah hadits terkait per ayat dalam satu surah. */
    fun linkCounts(surah: Int): Map<Int, Int> =
        query("SELECT ayah_id, COUNT(*) FROM link WHERE ayah_id BETWEEN ? AND ? GROUP BY ayah_id",
            arrayOf("${surah * 1000}", "${surah * 1000 + 999}")) { it.getInt(0) to it.getInt(1) }.toMap()

    // ---------- Hadits ----------
    private val HADITH_COLS = "h.id, h.kitab, k.nama, h.nomor, h.ar, h.idn, h.ringkas"
    private fun hadithOf(c: Cursor) = Hadith(c.getInt(0), c.getInt(1), c.getString(2), c.getInt(3), c.getString(4), c.getString(5), c.getString(6))
    private val HADITH_LITE = "h.id, h.kitab, k.nama, h.nomor, '', '', h.ringkas"

    val kitabList: List<Kitab> by lazy {
        query("SELECT id, slug, nama, jumlah FROM kitab ORDER BY id") { Kitab(it.getInt(0), it.getString(1), it.getString(2), it.getInt(3)) }
    }

    fun hadith(id: Int): Hadith? =
        query("SELECT $HADITH_COLS FROM hadith h JOIN kitab k ON k.id = h.kitab WHERE h.id = ?", arrayOf("$id"), ::hadithOf).firstOrNull()

    fun hadithByNomor(kitab: Int, nomor: Int): Hadith? =
        query("SELECT $HADITH_COLS FROM hadith h JOIN kitab k ON k.id = h.kitab WHERE h.kitab = ? AND h.nomor >= ? ORDER BY h.nomor LIMIT 1",
            arrayOf("$kitab", "$nomor"), ::hadithOf).firstOrNull()

    fun hadithPreviews(kitab: Int): List<Hadith> =
        query("SELECT $HADITH_LITE FROM hadith h JOIN kitab k ON k.id = h.kitab WHERE h.kitab = ? ORDER BY h.nomor", arrayOf("$kitab"), ::hadithOf)

    fun hadithByIds(ids: List<Int>): List<Hadith> {
        if (ids.isEmpty()) return emptyList()
        val m = query("SELECT $HADITH_COLS FROM hadith h JOIN kitab k ON k.id = h.kitab WHERE h.id IN (${ids.joinToString(",")})", null, ::hadithOf)
            .associateBy { it.id }
        return ids.mapNotNull { m[it] }
    }

    fun neighbor(h: Hadith, next: Boolean): Int? {
        val sql = if (next) "SELECT id FROM hadith WHERE kitab = ? AND nomor > ? ORDER BY nomor LIMIT 1"
        else "SELECT id FROM hadith WHERE kitab = ? AND nomor < ? ORDER BY nomor DESC LIMIT 1"
        return query(sql, arrayOf("${h.kitab}", "${h.nomor}")) { it.getInt(0) }.firstOrNull()
    }

    // ---------- Keterkaitan ----------
    fun hadithForAyah(ayahId: Int): List<LinkedHadith> =
        query("SELECT $HADITH_LITE, l.jenis, l.skor FROM link l JOIN hadith h ON h.id = l.hadith_id JOIN kitab k ON k.id = h.kitab " +
                "WHERE l.ayah_id = ? ORDER BY l.jenis, l.skor DESC LIMIT 40", arrayOf("$ayahId")) {
            LinkedHadith(hadithOf(it), it.getInt(7), it.getDouble(8))
        }.distinctBy { it.h.id }

    fun ayatForHadith(hadithId: Int): List<LinkedAyah> =
        query("SELECT $AYAH_COLS, l.jenis, l.skor FROM link l JOIN ayah a ON a.id = l.ayah_id JOIN surah s ON s.n = a.surah " +
                "WHERE l.hadith_id = ? ORDER BY l.jenis, l.skor DESC LIMIT 30", arrayOf("$hadithId")) {
            LinkedAyah(ayahOf(it), it.getInt(6), it.getDouble(7))
        }.distinctBy { it.a.id }

    // ---------- Pencarian ----------
    private val kitabAlias = mapOf(
        "bukhari" to 1, "muslim" to 2, "abu daud" to 3, "abudaud" to 3, "tirmidzi" to 4, "tirmizi" to 4,
        "nasai" to 5, "ibnu majah" to 6, "ibnumajah" to 6, "ahmad" to 7, "darimi" to 8, "malik" to 9,
    )

    fun search(query: String, kitabFilter: Int?): SearchResult {
        val q = query.trim()
        // Rujukan langsung: "2:255", "2 255", "bukhari 1"
        var directAyah: Ayah? = null
        var directHadith: Hadith? = null
        Regex("^(\\d{1,3})\\s*[:.\\s]\\s*(\\d{1,3})$").find(q)?.let { m ->
            val s = m.groupValues[1].toInt(); val a = m.groupValues[2].toInt()
            if (s in 1..114) directAyah = ayahByRef(s, a)
        }
        val nq = (" " + TextNorm.normId(q) + " ").replace(" hr ", " ").replace(" no ", " ").trim()
        Regex("^([a-z ]+?)\\s+(\\d{1,5})$").find(nq)?.let { m ->
            val k = kitabAlias[m.groupValues[1].trim()]
            if (k != null) directHadith = hadithByNomor(k, m.groupValues[2].toInt())
        }
        val match = TextNorm.buildMatch(q)
            ?: return SearchResult(emptyList(), 0, emptyList(), 0, emptyList(), true, directAyah, directHadith)

        val qSub = "SELECT docid FROM quran_fts WHERE quran_fts MATCH ?"
        val hSub = "SELECT docid FROM hadith_fts WHERE hadith_fts MATCH ?"
        val kf = if (kitabFilter != null) " AND h.kitab = $kitabFilter" else ""

        val ayatTotal = count("SELECT COUNT(*) FROM ($qSub)", arrayOf(match))
        val ayat = query("SELECT $AYAH_COLS FROM ayah a JOIN surah s ON s.n = a.surah WHERE a.id IN ($qSub) ORDER BY a.id LIMIT 300",
            arrayOf(match), ::ayahOf)
        val haditsTotal = count("SELECT COUNT(*) FROM hadith h WHERE h.id IN ($hSub)$kf", arrayOf(match))
        val hadits = query("SELECT $HADITH_COLS FROM hadith h JOIN kitab k ON k.id = h.kitab WHERE h.id IN ($hSub)$kf ORDER BY h.id LIMIT 300",
            arrayOf(match), ::hadithOf)

        // Keterkaitan: pasangan ayat–hadits yang KEDUANYA cocok dengan kueri.
        var exact = true
        var pairRows = query("SELECT l.ayah_id, l.hadith_id, l.jenis FROM link l JOIN hadith h ON h.id = l.hadith_id " +
                "WHERE l.ayah_id IN ($qSub) AND l.hadith_id IN ($hSub)$kf ORDER BY l.jenis, l.skor DESC LIMIT 150",
            arrayOf(match, match)) { Triple(it.getInt(0), it.getInt(1), it.getInt(2)) }
        if (pairRows.isEmpty() && ayatTotal > 0) {
            // Bila tidak ada pasangan yang sama-sama cocok, tampilkan hadits terkait dari ayat yang cocok.
            exact = false
            pairRows = query("SELECT l.ayah_id, l.hadith_id, l.jenis FROM link l JOIN hadith h ON h.id = l.hadith_id " +
                    "WHERE l.ayah_id IN ($qSub)$kf ORDER BY l.jenis, l.skor DESC LIMIT 60",
                arrayOf(match)) { Triple(it.getInt(0), it.getInt(1), it.getInt(2)) }
        }
        val pa = ayatByIds(pairRows.map { it.first }.distinct()).associateBy { it.id }
        val ph = hadithByIds(pairRows.map { it.second }.distinct()).associateBy { it.id }
        val seen = HashSet<Long>()
        val pairs = pairRows.mapNotNull { (a, h, j) ->
            if (!seen.add(a.toLong() * 1_000_000 + h)) return@mapNotNull null
            val aa = pa[a]; val hh = ph[h]
            if (aa != null && hh != null) RelatedPair(aa, hh, j) else null
        }
        return SearchResult(ayat, ayatTotal, hadits, haditsTotal, pairs, exact, directAyah, directHadith)
    }

    val topics: List<Topik> by lazy {
        query("SELECT id, nama, kata FROM topik ORDER BY id") { Topik(it.getInt(0), it.getString(1), it.getString(2)) }
    }

    // ---------- Pilihan harian ----------
    private fun dailyRef(jenis: Int, date: LocalDate): Int? {
        val n = count("SELECT COUNT(*) FROM daily WHERE jenis = ?", arrayOf("$jenis"))
        if (n == 0) return null
        val idx = ((date.toEpochDay() % n) + n) % n
        return query("SELECT ref FROM daily WHERE jenis = ? AND urut = ?", arrayOf("$jenis", "$idx")) { it.getInt(0) }.firstOrNull()
    }

    fun dailyAyah(date: LocalDate = LocalDate.now()): Ayah? = dailyRef(1, date)?.let { ayah(it) }
    fun dailyHadith(date: LocalDate = LocalDate.now()): Hadith? = dailyRef(2, date)?.let { hadith(it) }
}
