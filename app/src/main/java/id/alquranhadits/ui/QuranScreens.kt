package id.alquranhadits.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.alquranhadits.data.JENIS_KUTIP
import id.alquranhadits.data.JENIS_TEMA
import id.alquranhadits.data.Prefs
import id.alquranhadits.data.Repo
import id.alquranhadits.data.TextNorm
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview

@Composable
fun SurahListScreen(nav: Nav) {
    val repo = Repo.get(LocalContext.current)
    var filter by rememberSaveable { mutableStateOf("") }
    val all = repo.surahList
    val f = TextNorm.normId(filter).replace(" ", "")
    val list = if (f.isEmpty()) all else all.filter {
        TextNorm.normId(it.latin).replace(" ", "").contains(f) || TextNorm.normId(it.arti).contains(f) || "${it.n}" == f
    }
    Column(Modifier.fillMaxSize()) {
        AppBar("Al-Qur'an", "Terjemahan Kemenag RI 2019")
        OutlinedTextField(
            value = filter, onValueChange = { filter = it }, singleLine = true,
            placeholder = { Text("Cari nama surah…") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(state = rememberLazyListState()) {
            items(list, key = { it.n }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.push(Screen.SurahDetail(s.n)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberBadge(s.n)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.latin, style = MaterialTheme.typography.titleMedium)
                        Text("${s.arti} • ${s.tempat} • ${s.ayat} ayat", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(s.ar, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(Modifier.padding(start = 64.dp))
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun SurahScreen(nav: Nav, surahN: Int, scrollTo: Int) {
    val ctx = LocalContext.current
    val repo = Repo.get(ctx)
    val prefs = Prefs.get(ctx)
    val s = repo.surah(surahN)
    val ayat by load(surahN) { repo.ayatOfSurah(surahN) }
    val counts by load(surahN) { repo.linkCounts(surahN) }
    val state = rememberLazyListState()
    val hasHeader = surahN != 1 && surahN != 9
    var scrolled by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppBar("${s.n}. ${s.latin}", "${s.arti} • ${s.tempat} • ${s.ayat} ayat", onBack = { nav.pop() })
        val list = ayat
        if (list == null) { Loading(); return@Column }

        LaunchedEffect(list) {
            if (!scrolled && scrollTo > 1) state.scrollToItem((scrollTo - 1) + if (hasHeader) 1 else 0)
            scrolled = true
            snapshotFlow { state.firstVisibleItemIndex }.debounce(800).collect { i ->
                val idx = (i - if (hasHeader) 1 else 0).coerceIn(0, list.lastIndex)
                prefs.updateLastRead(list[idx].id)
            }
        }

        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            if (hasHeader) item {
                ArabicText("بِسْمِ اللّٰهِ الرَّحْمٰنِ الرَّحِيْمِ", prefs.arabicSize,
                    Modifier.padding(16.dp))
            }
            items(list, key = { it.id }) { a ->
                val n = counts?.get(a.id) ?: 0
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberBadge(a.ayah)
                        ActionRow(a.key, ayahShareText(a), prefs) {
                            if (n > 0) TextButton(onClick = { nav.push(Screen.AyahDetail(a.id)) }) {
                                Icon(Icons.Filled.Link, null, Modifier.padding(end = 4.dp))
                                Text("$n hadits terkait")
                            }
                        }
                    }
                    ArabicText(a.ar + arabicAyahEnd(a.ayah), prefs.arabicSize, Modifier.padding(vertical = 8.dp))
                    Text(a.idn, fontSize = prefs.latinSize.sp, modifier = Modifier.clickable { nav.push(Screen.AyahDetail(a.id)) })
                }
                HorizontalDivider()
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (surahN > 1) OutlinedButton(onClick = { nav.pop(); nav.push(Screen.SurahDetail(surahN - 1)) }) { Text("‹ Surah sebelumnya") }
                    else Spacer(Modifier.width(1.dp))
                    if (surahN < 114) OutlinedButton(onClick = { nav.pop(); nav.push(Screen.SurahDetail(surahN + 1)) }) { Text("Surah berikutnya ›") }
                }
            }
        }
    }
}

@Composable
fun AyahDetailScreen(nav: Nav, id: Int) {
    val ctx = LocalContext.current
    val repo = Repo.get(ctx)
    val prefs = Prefs.get(ctx)
    val ayah by load(id) { repo.ayah(id) }
    val links by load(id) { repo.hadithForAyah(id) }

    Column(Modifier.fillMaxSize()) {
        val a = ayah
        AppBar(a?.ref ?: "Ayat", "Ayat & hadits terkait", onBack = { nav.pop() })
        if (a == null) { Loading(); return@Column }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ArabicText(a.ar + arabicAyahEnd(a.ayah), prefs.arabicSize)
                        Text(a.idn, fontSize = prefs.latinSize.sp)
                        Text(a.ref, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        ActionRow(a.key, ayahShareText(a), prefs) {
                            TextButton(onClick = { nav.push(Screen.SurahDetail(a.surah, a.ayah)) }) { Text("Buka di surah") }
                        }
                    }
                }
            }
            val l = links
            if (l == null) {
                item { Loading() }
            } else {
                val kutip = l.filter { it.jenis == JENIS_KUTIP }
                val tema = l.filter { it.jenis == JENIS_TEMA && kutip.none { k -> k.h.id == it.h.id } }
                if (kutip.isNotEmpty()) {
                    item { SectionTitle("Hadits yang mengutip / menyebut ayat ini (${kutip.size})") }
                    items(kutip, key = { "k" + it.h.id }) { lh -> HadithRow(lh.h, tag = { LinkTag(lh.jenis) }) { nav.push(Screen.HadithDetail(lh.h.id)) } }
                }
                if (tema.isNotEmpty()) {
                    item { SectionTitle("Hadits dengan tema serupa (${tema.size})") }
                    items(tema, key = { "t" + it.h.id }) { lh -> HadithRow(lh.h, tag = { LinkTag(lh.jenis) }) { nav.push(Screen.HadithDetail(lh.h.id)) } }
                }
                if (l.isEmpty()) item { EmptyNote("Belum ada hadits yang terhubung dengan ayat ini.\nCoba cari temanya di menu Cari.") }
                item {
                    Text(
                        "“Mengutip ayat” = teks Arab hadits memuat ayat ini. “Tema serupa” = dihitung otomatis dari kemiripan isi terjemahan, sebagai bahan telaah lanjutan.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

/** Kolom angka untuk lompat cepat (dipakai di daftar hadits). */
@Composable
fun NumberJump(label: String, onGo: (Int) -> Unit) {
    var v by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = v, onValueChange = { v = it.filter(Char::isDigit).take(5) }, singleLine = true,
            placeholder = { Text(label) }, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { v.toIntOrNull()?.let(onGo) }) { Text("Buka") }
    }
}
