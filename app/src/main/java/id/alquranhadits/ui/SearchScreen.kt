package id.alquranhadits.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.alquranhadits.data.Prefs
import id.alquranhadits.data.Repo
import id.alquranhadits.data.SearchResult
import id.alquranhadits.data.TextNorm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(nav: Nav, initial: String) {
    val ctx = LocalContext.current
    val repo = Repo.get(ctx)
    val prefs = Prefs.get(ctx)
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf(initial) }
    var kitab by rememberSaveable { mutableIntStateOf(0) }  // 0 = semua kitab
    var tab by rememberSaveable { mutableIntStateOf(-1) }   // -1 = otomatis
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(query, kitab) {
        if (query.isBlank()) { result = null; busy = false; return@LaunchedEffect }
        delay(350)
        busy = true
        result = withContext(Dispatchers.IO) {
            try { repo.search(query, kitab.takeIf { it > 0 }) } catch (e: Exception) { null }
        }
        busy = false
    }
    val terms = remember(query) { TextNorm.latinTerms(query) }

    Column(Modifier.fillMaxSize()) {
        AppBar("Cari Al-Qur'an & Hadits", "Kata, frasa, teks Arab, atau rujukan")
        OutlinedTextField(
            value = query, onValueChange = { query = it; tab = -1 }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Hapus") } },
            placeholder = { Text("mis. sabar, berbakti orang tua, الصبر") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(selected = kitab == 0, onClick = { kitab = 0 }, label = { Text("Semua kitab") }) }
            items(repo.kitabList, key = { it.id }) { k ->
                FilterChip(selected = kitab == k.id, onClick = { kitab = if (kitab == k.id) 0 else k.id }, label = { Text(k.pendek) })
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        val r = result
        if (query.isBlank() || r == null) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tema populer", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repo.topics.forEach { t -> AssistChip(onClick = { query = t.kata.substringBefore(' ') }, label = { Text(t.nama) }) }
                }
                Text(
                    "Tips:\n• Beberapa kata = semua kata harus ada (mis. \"sabar musibah\").\n" +
                        "• Imbuhan otomatis: \"kesabaran\" juga menemukan \"sabar\".\n" +
                        "• Rujukan langsung: \"2:255\", \"18 10\", \"bukhari 1\", \"muslim 100\".\n" +
                        "• Teks Arab boleh tanpa harakat.\n" +
                        "• Tab \"Keterkaitan\" menampilkan pasangan ayat ↔ hadits yang saling terhubung dan sama-sama cocok dengan pencarian.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        val selected = if (tab >= 0) tab else when {
            r.pairs.isNotEmpty() -> 0
            r.ayatTotal > 0 -> 1
            else -> 2
        }
        TabRow(selectedTabIndex = selected) {
            Tab(selected = selected == 0, onClick = { tab = 0 }, text = { Text("Keterkaitan (${r.pairs.size})", maxLines = 1, fontSize = 13.sp) })
            Tab(selected = selected == 1, onClick = { tab = 1 }, text = { Text("Al-Qur'an (${r.ayatTotal})", maxLines = 1, fontSize = 13.sp) })
            Tab(selected = selected == 2, onClick = { tab = 2 }, text = { Text("Hadits (${r.haditsTotal})", maxLines = 1, fontSize = 13.sp) })
        }

        val listState = rememberLazyListState()
        LaunchedEffect(selected, r) { listState.scrollToItem(0) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            r.directAyah?.let { a ->
                item { SectionTitle("Rujukan langsung") }
                item { AyahRow(a) { nav.push(Screen.AyahDetail(a.id)) } }
            }
            r.directHadith?.let { h ->
                item { SectionTitle("Rujukan langsung") }
                item { HadithRow(h) { nav.push(Screen.HadithDetail(h.id)) } }
            }
            when (selected) {
                0 -> {
                    if (r.pairs.isEmpty()) item { EmptyNote("Tidak ada pasangan ayat–hadits yang terhubung untuk kata kunci ini.") }
                    else item {
                        Text(
                            if (r.pairsExact) "Pasangan ayat & hadits yang saling terhubung dan sama-sama memuat kata kunci."
                            else "Tidak ada hadits terhubung yang memuat kata kunci yang sama; berikut hadits yang terhubung dengan ayat-ayat hasil pencarian.",
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(r.pairs, key = { "p${it.a.id}_${it.h.id}" }) { p ->
                        ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Column {
                                Column(Modifier.fillMaxWidth().clickable { nav.push(Screen.AyahDetail(p.a.id)) }.padding(14.dp)) {
                                    Text(p.a.ref, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(highlight(snippet(p.a.idn, terms, 220), terms, MaterialTheme.colorScheme.secondaryContainer),
                                        style = MaterialTheme.typography.bodyMedium, fontSize = (prefs.latinSize - 1).sp)
                                }
                                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    HorizontalDivider(Modifier.weight(1f))
                                    Icon(Icons.Filled.SwapVert, null, Modifier.padding(horizontal = 6.dp), tint = MaterialTheme.colorScheme.outline)
                                    LinkTag(p.jenis)
                                    Spacer(Modifier.width(6.dp))
                                    HorizontalDivider(Modifier.weight(1f))
                                }
                                Column(Modifier.fillMaxWidth().clickable { nav.push(Screen.HadithDetail(p.h.id)) }.padding(14.dp)) {
                                    Text(p.h.ref, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(highlight(snippet(p.h.idn, terms, 260), terms, MaterialTheme.colorScheme.secondaryContainer),
                                        style = MaterialTheme.typography.bodyMedium, fontSize = (prefs.latinSize - 1).sp)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (r.ayat.isEmpty()) item { EmptyNote("Tidak ada ayat yang cocok.") }
                    items(r.ayat, key = { "a" + it.id }) { a -> AyahRow(a, terms) { nav.push(Screen.AyahDetail(a.id)) } }
                    if (r.ayatTotal > r.ayat.size) item { EmptyNote("Menampilkan ${r.ayat.size} dari ${r.ayatTotal}. Persempit kata kunci.") }
                }
                else -> {
                    if (r.hadits.isEmpty()) item { EmptyNote("Tidak ada hadits yang cocok.") }
                    items(r.hadits, key = { "h" + it.id }) { h -> HadithRow(h, terms) { nav.push(Screen.HadithDetail(h.id)) } }
                    if (r.haditsTotal > r.hadits.size) item { EmptyNote("Menampilkan ${r.hadits.size} dari ${r.haditsTotal}. Persempit kata kunci atau pilih kitab.") }
                }
            }
        }
    }
}
