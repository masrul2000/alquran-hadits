package id.alquranhadits.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Tab { HOME, QURAN, HADITH, SEARCH, SAVED }

sealed interface Screen {
    data object Home : Screen
    data object SurahList : Screen
    data class SurahDetail(val surah: Int, val scrollToAyah: Int = 0) : Screen
    data class AyahDetail(val id: Int) : Screen
    data object KitabList : Screen
    data class HadithList(val kitab: Int) : Screen
    data class HadithDetail(val id: Int) : Screen
    data class Search(val initial: String = "") : Screen
    data object Saved : Screen
    data object Settings : Screen
}

data class Entry(val key: Long, val screen: Screen)

class Nav {
    private var counter = 0L
    var tab by mutableStateOf(Tab.HOME)
        private set
    val stack = mutableStateListOf(Entry(counter++, Screen.Home))
    val current: Entry get() = stack.last()

    /** Dipanggil saat sebuah entri dibuang, untuk membersihkan state tersimpan. */
    var onRemoved: (Long) -> Unit = {}

    fun push(s: Screen) { stack.add(Entry(counter++, s)) }

    fun pop(): Boolean {
        if (stack.size <= 1) {
            if (tab != Tab.HOME) { select(Tab.HOME); return true }
            return false
        }
        onRemoved(stack.removeAt(stack.lastIndex).key)
        return true
    }

    fun select(t: Tab, root: Screen? = null) {
        tab = t
        stack.forEach { onRemoved(it.key) }
        stack.clear()
        stack.add(Entry(counter++, root ?: rootOf(t)))
    }

    private fun rootOf(t: Tab): Screen = when (t) {
        Tab.HOME -> Screen.Home
        Tab.QURAN -> Screen.SurahList
        Tab.HADITH -> Screen.KitabList
        Tab.SEARCH -> Screen.Search()
        Tab.SAVED -> Screen.Saved
    }
}
