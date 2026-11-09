package id.alquranhadits.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.alquranhadits.data.Prefs
import id.alquranhadits.data.Repo
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val repo = Repo.get(ctx)
    val prefs = Prefs.get(ctx)
    val today = LocalDate.now()
    val ayah by load(today) { repo.dailyAyah(today) }
    val hadith by load(today) { repo.dailyHadith(today) }
    val last = prefs.lastRead

    Column(Modifier.fillMaxSize()) {
        AppBar(
            "Al-Qur'an & Hadits",
            subtitle = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID"))),
            actions = { IconButton(onClick = { nav.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Pengaturan") } }
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            // Kotak pencarian
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { nav.select(Tab.SEARCH) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Cari ayat & hadits (mis. sabar, 2:255, bukhari 1)", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            SectionTitle("Ayat Pilihan Hari Ini")
            ayah?.let { a ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { nav.push(Screen.AyahDetail(a.id)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ArabicText(a.ar + arabicAyahEnd(a.ayah), prefs.arabicSize)
                        Text(a.idn, fontSize = prefs.latinSize.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(a.ref, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        ActionRow(a.key, ayahShareText(a), prefs) {
                            TextButton(onClick = { nav.push(Screen.AyahDetail(a.id)) }) { Text("Hadits terkait") }
                        }
                    }
                }
            }

            SectionTitle("Hadits Pilihan Hari Ini")
            hadith?.let { h ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { nav.push(Screen.HadithDetail(h.id)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(h.ringkas, fontSize = prefs.latinSize.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(h.ref, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary)
                        ActionRow(h.key, hadithShareText(h), prefs) {
                            TextButton(onClick = { nav.push(Screen.HadithDetail(h.id)) }) { Text("Baca lengkap") }
                        }
                    }
                }
            }

            if (last > 0) {
                SectionTitle("Terakhir Dibaca")
                val s = repo.surah(last / 1000)
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clickable { nav.push(Screen.SurahDetail(s.n, last % 1000)) }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${s.n}. ${s.latin}", style = MaterialTheme.typography.titleMedium)
                            Text("Ayat ${last % 1000} dari ${s.ayat}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(s.ar, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SectionTitle("Jelajahi Tema (Al-Qur'an ↔ Hadits)")
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repo.topics.forEach { t ->
                    AssistChip(onClick = { nav.select(Tab.SEARCH, Screen.Search(t.kata.substringBefore(' '))) }, label = { Text(t.nama) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
