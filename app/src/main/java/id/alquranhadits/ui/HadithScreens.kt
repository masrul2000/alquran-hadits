package id.alquranhadits.ui

import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.alquranhadits.data.JENIS_KUTIP
import id.alquranhadits.data.JENIS_TEMA
import id.alquranhadits.data.Prefs
import id.alquranhadits.data.Repo
import kotlinx.coroutines.launch

@Composable
fun KitabListScreen(nav: Nav) {
    val repo = Repo.get(LocalContext.current)
    Column(Modifier.fillMaxSize()) {
        AppBar("Hadits", "9 kitab • ${"%,d".format(repo.kitabList.sumOf { it.jumlah }).replace(',', '.')} hadits")
        LazyColumn {
            items(repo.kitabList, key = { it.id }) { k ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.push(Screen.HadithList(k.id)) }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberBadge(k.id)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(k.nama, style = MaterialTheme.typography.titleMedium)
                        Text("${k.jumlah} hadits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 64.dp))
            }
        }
    }
}

@Composable
fun HadithListScreen(nav: Nav, kitabId: Int) {
    val ctx = LocalContext.current
    val repo = Repo.get(ctx)
    val k = repo.kitabList.first { it.id == kitabId }
    val list by load(kitabId) { repo.hadithPreviews(kitabId) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        AppBar(k.nama, "${k.jumlah} hadits", onBack = { nav.pop() })
        val l = list
        if (l == null) { Loading(); return@Column }
        NumberJump("Lompat ke nomor hadits") { no ->
            val idx = l.indexOfFirst { it.nomor >= no }
            if (idx >= 0) scope.launch { state.scrollToItem(idx) }
            else Toast.makeText(ctx, "Nomor tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
        LazyColumn(state = state) {
            items(l, key = { it.id }) { h ->
                Column(
                    Modifier.fillMaxWidth().clickable { nav.push(Screen.HadithDetail(h.id)) }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("No. ${h.nomor}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                    Text(h.ringkas, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun HadithDetailScreen(nav: Nav, id: Int) {
    val ctx = LocalContext.current
    val repo = Repo.get(ctx)
    val prefs = Prefs.get(ctx)
    val hadith by load(id) { repo.hadith(id) }
    val links by load(id) { repo.ayatForHadith(id) }

    Column(Modifier.fillMaxSize()) {
        val h = hadith
        AppBar(h?.ref ?: "Hadits", h?.kitabNama, onBack = { nav.pop() })
        if (h == null) { Loading(); return@Column }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (prefs.showHadithArabic && h.ar.isNotBlank()) ArabicText(h.ar, prefs.arabicSize * 0.85f)
                        Text(h.idn, fontSize = prefs.latinSize.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(h.ref, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        ActionRow(h.key, hadithShareText(h), prefs)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = {
                        scopeNeighbor(repo, h, false)?.let { nav.pop(); nav.push(Screen.HadithDetail(it)) }
                    }) { Text("‹ Sebelumnya") }
                    OutlinedButton(onClick = {
                        scopeNeighbor(repo, h, true)?.let { nav.pop(); nav.push(Screen.HadithDetail(it)) }
                    }) { Text("Berikutnya ›") }
                }
            }
            val l = links
            if (l == null) {
                item { Loading() }
            } else {
                val kutip = l.filter { it.jenis == JENIS_KUTIP }
                val tema = l.filter { it.jenis == JENIS_TEMA && kutip.none { k -> k.a.id == it.a.id } }
                if (kutip.isNotEmpty()) {
                    item { SectionTitle("Ayat yang dikutip dalam hadits ini (${kutip.size})") }
                    items(kutip, key = { "k" + it.a.id }) { la -> AyahRow(la.a, tag = { LinkTag(la.jenis) }) { nav.push(Screen.AyahDetail(la.a.id)) } }
                }
                if (tema.isNotEmpty()) {
                    item { SectionTitle("Ayat dengan tema serupa (${tema.size})") }
                    items(tema, key = { "t" + it.a.id }) { la -> AyahRow(la.a, tag = { LinkTag(la.jenis) }) { nav.push(Screen.AyahDetail(la.a.id)) } }
                }
                if (l.isEmpty()) item { EmptyNote("Belum ada ayat yang terhubung dengan hadits ini.") }
            }
        }
    }
}

/** Query kecil (indeks) — aman dijalankan langsung saat tombol ditekan. */
private fun scopeNeighbor(repo: Repo, h: id.alquranhadits.data.Hadith, next: Boolean): Int? = repo.neighbor(h, next)
