package id.alquranhadits.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.alquranhadits.data.Prefs
import id.alquranhadits.data.Repo
import id.alquranhadits.notif.Scheduler

@Composable
fun SavedScreen(nav: Nav) {
    val ctx = LocalContext.current
    val repo = Repo.get(ctx)
    val prefs = Prefs.get(ctx)
    val keys = prefs.bookmarks
    val data by load(keys) {
        val a = keys.filter { it.startsWith("a:") }.mapNotNull { it.substring(2).toIntOrNull() }.sorted()
        val h = keys.filter { it.startsWith("h:") }.mapNotNull { it.substring(2).toIntOrNull() }.sorted()
        repo.ayatByIds(a) to repo.hadithByIds(h)
    }
    Column(Modifier.fillMaxSize()) {
        AppBar("Tersimpan", "${keys.size} item")
        val d = data
        if (d == null) { Loading(); return@Column }
        if (keys.isEmpty()) { EmptyNote("Belum ada yang disimpan.\nKetuk ikon penanda pada ayat atau hadits untuk menyimpannya."); return@Column }
        LazyColumn {
            if (d.first.isNotEmpty()) {
                item { SectionTitle("Ayat (${d.first.size})") }
                items(d.first, key = { it.key }) { a -> AyahRow(a) { nav.push(Screen.AyahDetail(a.id)) } }
            }
            if (d.second.isNotEmpty()) {
                item { SectionTitle("Hadits (${d.second.size})") }
                items(d.second, key = { it.key }) { h -> HadithRow(h) { nav.push(Screen.HadithDetail(h.id)) } }
            }
        }
    }
}

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val prefs = Prefs.get(ctx)
    @Suppress("UNUSED_VARIABLE") val v = prefs.notifVersion  // recomposition saat pengaturan notifikasi berubah
    val granted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    Column(Modifier.fillMaxSize()) {
        AppBar("Pengaturan", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle("Notifikasi harian")
            if (!granted) {
                Text("Izin notifikasi belum diberikan.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                OutlinedButton(
                    onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("Buka pengaturan notifikasi") }
            }
            NotifRow("Ayat pilihan", Prefs.AYAT, prefs)
            NotifRow("Hadits pilihan", Prefs.HADITS, prefs)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedButton(onClick = { Thread { Scheduler.show(ctx.applicationContext, Prefs.AYAT) }.start() }) { Text("Uji notifikasi ayat") }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedButton(onClick = { Thread { Scheduler.show(ctx.applicationContext, Prefs.HADITS) }.start() }) { Text("Uji notifikasi hadits") }
            }
            Text(
                "Jika notifikasi tidak muncul tepat waktu, nonaktifkan pengoptimalan baterai untuk aplikasi ini (terutama di HP Xiaomi, Oppo, Vivo, Realme).",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) { Text("Buka info aplikasi") }

            SectionTitle("Tampilan")
            Text("Ukuran teks Arab: ${prefs.arabicSize.toInt()}", Modifier.padding(horizontal = 16.dp))
            Slider(prefs.arabicSize, { prefs.updateArabicSize(it) }, valueRange = 18f..44f, modifier = Modifier.padding(horizontal = 16.dp))
            ArabicText("بِسْمِ اللّٰهِ الرَّحْمٰنِ الرَّحِيْمِ", prefs.arabicSize, Modifier.padding(horizontal = 16.dp))
            Text("Ukuran teks terjemahan: ${prefs.latinSize.toInt()}", Modifier.padding(horizontal = 16.dp))
            Slider(prefs.latinSize, { prefs.updateLatinSize(it) }, valueRange = 13f..24f, modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.fillMaxWidth().clickable { prefs.updateShowHadithArabic(!prefs.showHadithArabic) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tampilkan teks Arab hadits", Modifier.weight(1f))
                Switch(prefs.showHadithArabic, { prefs.updateShowHadithArabic(it) })
            }

            SectionTitle("Tentang & sumber data")
            Text(
                "• Terjemahan Al-Qur'an: Kementerian Agama RI, edisi penyempurnaan 2019 (Lajnah Pentashihan Mushaf Al-Qur'an).\n" +
                    "• Teks Arab Al-Qur'an & nama surah: paket quran-json.\n" +
                    "• Hadits (Arab & terjemahan Indonesia) 9 kitab: gadingnst/hadith-api (lisensi MIT).\n" +
                    "• Keterkaitan \"Mengutip ayat\" dideteksi dari teks Arab; \"Tema serupa\" dihitung otomatis dari kemiripan isi terjemahan. " +
                    "Untuk kajian mendalam, rujuk kitab syarah dan para ulama.",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun NotifRow(label: String, jenis: Int, prefs: Prefs) {
    val ctx = LocalContext.current
    val on = prefs.notifEnabled(jenis)
    val m = prefs.notifMinutes(jenis)
    val time = "%02d:%02d".format(m / 60, m % 60)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                if (on) "Setiap hari pukul $time (ketuk untuk ubah)" else "Nonaktif",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = on) {
                    TimePickerDialog(ctx, { _, h, min ->
                        prefs.setNotifMinutes(jenis, h * 60 + min)
                        Scheduler.schedule(ctx.applicationContext, jenis)
                    }, m / 60, m % 60, true).show()
                }
            )
        }
        Switch(on, { prefs.setNotifEnabled(jenis, it); Scheduler.schedule(ctx.applicationContext, jenis) })
    }
}
