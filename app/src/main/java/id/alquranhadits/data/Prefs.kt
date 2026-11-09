package id.alquranhadits.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Pengaturan & data tersimpan; juga diekspos sebagai state Compose supaya UI ikut berubah. */
class Prefs private constructor(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs = inst ?: synchronized(this) {
            inst ?: Prefs(ctx.applicationContext).also { inst = it }
        }
        const val AYAT = 1
        const val HADITS = 2
    }

    fun notifEnabled(jenis: Int) = sp.getBoolean("notif_on_$jenis", true)
    fun setNotifEnabled(jenis: Int, on: Boolean) { sp.edit().putBoolean("notif_on_$jenis", on).apply(); bump() }

    /** Menit sejak tengah malam. Default: ayat 05:30, hadits 18:30. */
    fun notifMinutes(jenis: Int) = sp.getInt("notif_min_$jenis", if (jenis == AYAT) 5 * 60 + 30 else 18 * 60 + 30)
    fun setNotifMinutes(jenis: Int, m: Int) { sp.edit().putInt("notif_min_$jenis", m).apply(); bump() }

    var arabicSize by mutableStateOf(sp.getFloat("ar_size", 26f))
        private set
    fun updateArabicSize(v: Float) { arabicSize = v; sp.edit().putFloat("ar_size", v).apply() }

    var latinSize by mutableStateOf(sp.getFloat("lat_size", 16f))
        private set
    fun updateLatinSize(v: Float) { latinSize = v; sp.edit().putFloat("lat_size", v).apply() }

    var showHadithArabic by mutableStateOf(sp.getBoolean("hadith_ar", true))
        private set
    fun updateShowHadithArabic(v: Boolean) { showHadithArabic = v; sp.edit().putBoolean("hadith_ar", v).apply() }

    var bookmarks by mutableStateOf(sp.getStringSet("bookmarks", emptySet())!!.toSet())
        private set
    fun toggleBookmark(key: String) {
        val s = bookmarks.toMutableSet()
        if (!s.add(key)) s.remove(key)
        bookmarks = s
        sp.edit().putStringSet("bookmarks", s).apply()
    }

    /** Terakhir dibaca: id ayat (surah*1000+ayat). */
    var lastRead by mutableStateOf(sp.getInt("last_read", 0))
        private set
    fun updateLastRead(id: Int) {
        if (id == lastRead) return
        lastRead = id
        sp.edit().putInt("last_read", id).apply()
    }

    /** Penanda perubahan pengaturan notifikasi (untuk recomposition layar pengaturan). */
    var notifVersion by mutableStateOf(0)
        private set
    private fun bump() { notifVersion++ }
}
