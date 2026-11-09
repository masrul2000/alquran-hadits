package id.alquranhadits.data

import java.text.Normalizer

/**
 * Normalisasi teks — HARUS identik dengan tools/build_db.py supaya kueri cocok dengan indeks FTS.
 */
object TextNorm {
    private val arabicToken = Regex("[\\u0600-\\u06ff\\u0750-\\u077f\\ufb50-\\ufdff\\ufe70-\\ufefc]+")
    private val harakat = Regex("[\\u0610-\\u061a\\u064b-\\u065f\\u0670\\u06d6-\\u06ed\\u0640]")
    private val alif = Regex("[\\u0622\\u0623\\u0625\\u0671\\u0672\\u0673]")
    private val weak = Regex("[\\u0627\\u0648\\u064a\\u0649\\u0621\\u0624\\u0626\\u06cc\\u06e5\\u06e6]")
    private val combining = Regex("\\p{Mn}+")
    private val apostrophe = Regex("['‘’`ʼ]")
    private val nonAlnum = Regex("[^a-z0-9\\s]")

    private val suf1 = listOf("lah", "kah", "pun", "nya", "ku", "mu")
    private val suf2 = listOf("kan", "an")
    private val pre = listOf("memper", "member", "meng", "meny", "mem", "men", "ber", "ter", "per",
        "peng", "pen", "pem", "me", "di", "ke")

    /** Sinonim ejaan yang umum dipakai (mis. "shalat" vs "salat" Kemenag 2019). */
    private val synonyms: List<Set<String>> = listOf(
        setOf("shalat", "salat", "sholat", "solat"),
        setOf("puasa", "shaum", "saum", "shiyam"),
        setOf("dzikir", "zikir", "dzikr"),
        setOf("taubat", "tobat"),
        setOf("rezeki", "rizki", "rizqi"),
        setOf("sedekah", "shadaqah", "sadaqah", "shodaqoh"),
        setOf("ramadhan", "ramadan"),
        setOf("wudhu", "wudu"),
        setOf("quran", "alquran", "qur"),
        setOf("akhirat", "akherat"),
        setOf("ikhlas", "ikhlash"),
        setOf("syahid", "syuhada"),
        setOf("jumat", "jumuah"),
        setOf("subuh", "shubuh", "fajar"),
        setOf("dzuhur", "zuhur", "zhuhur"),
        setOf("maghrib", "magrib"),
        setOf("isya", "isyak"),
        setOf("haram", "diharamkan"),
        setOf("doa", "berdoa"),
    )

    fun isArabic(s: String) = arabicToken.containsMatchIn(s)

    fun arSkeleton(word: String): String {
        var w = harakat.replace(word, "")
        w = alif.replace(w, "ا")
        w = w.replace('ة', 'ه')
        w = weak.replace(w, "")
        return w
    }

    fun normId(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFKD)
        t = combining.replace(t, "")
        t = apostrophe.replace(t, "")
        t = nonAlnum.replace(t, " ")
        return t.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun stem(word: String): String {
        var w = word
        if (w.length < 6 || !w.all { it in 'a'..'z' }) return w
        for (p in pre) {
            if (w.startsWith(p) && w.length - p.length >= 3) { w = w.substring(p.length); break }
        }
        for (group in listOf(suf1, suf2)) {
            for (s in group) {
                if (w.endsWith(s) && w.length - s.length >= 4) { w = w.substring(0, w.length - s.length); break }
            }
        }
        return w
    }

    /** Kata-kata latin (sudah dinormalisasi) untuk penyorotan hasil. */
    fun latinTerms(query: String): List<String> =
        normId(query).split(" ").filter { it.length >= 2 }

    /**
     * Bangun ekspresi MATCH FTS4. Setiap kata menjadi prefiks (*), beberapa kata = AND.
     * Mengembalikan null bila kueri kosong.
     */
    fun buildMatch(query: String): String? {
        val parts = mutableListOf<String>()
        for (raw in query.trim().split(Regex("\\s+"))) {
            if (raw.isBlank()) continue
            if (isArabic(raw)) {
                arabicToken.findAll(raw).map { arSkeleton(it.value) }.filter { it.isNotEmpty() }
                    .forEach { parts.add("$it*") }
            } else {
                for (w in normId(raw).split(" ").filter { it.length >= 2 }) {
                    val syn = synonyms.firstOrNull { w in it }
                    if (syn != null) {
                        parts.add(syn.joinToString(" OR ") { stem(it) + "*" })
                    } else {
                        parts.add(stem(w) + "*")
                    }
                }
            }
        }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    private val arabicDigits = "٠١٢٣٤٥٦٧٨٩"
    fun toArabicDigits(n: Int): String = n.toString().map { arabicDigits[it - '0'] }.joinToString("")
}
