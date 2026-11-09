package id.alquranhadits.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.alquranhadits.data.Db
import id.alquranhadits.notif.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val nav = Nav()
    private var pending by mutableStateOf<Screen?>(null)

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Scheduler.scheduleAll(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val id = i?.getIntExtra(Scheduler.EXTRA_ID, 0) ?: 0
        if (id == 0) return
        pending = when (i?.getStringExtra(Scheduler.EXTRA_OPEN)) {
            "ayah" -> Screen.AyahDetail(id)
            "hadith" -> Screen.HadithDetail(id)
            else -> null
        }
        i?.removeExtra(Scheduler.EXTRA_ID)
    }

    @Composable
    private fun Root() {
        val ctx = LocalContext.current
        var ready by remember { mutableStateOf(Db.isReady(ctx)) }
        var progress by remember { mutableFloatStateOf(0f) }
        var error by remember { mutableStateOf<String?>(null) }

        if (!ready) {
            LaunchedEffect(Unit) {
                try {
                    withContext(Dispatchers.IO) { Db.install(ctx.applicationContext) { progress = it } }
                    ready = true
                } catch (e: Exception) {
                    error = e.message ?: e.toString()
                }
            }
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Al-Qur'an & Hadits", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        error?.let { "Gagal menyiapkan data: $it\nPastikan ruang penyimpanan cukup (±150 MB)." }
                            ?: "Menyiapkan data Al-Qur'an dan 9 kitab hadits…\n(hanya sekali, saat pertama dibuka)",
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
                    )
                    if (error == null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            return
        }

        val holder = rememberSaveableStateHolder()
        nav.onRemoved = { holder.removeState(it) }

        LaunchedEffect(pending) {
            pending?.let { nav.push(it); pending = null }
        }
        BackHandler(enabled = nav.stack.size > 1 || nav.tab != Tab.HOME) { nav.pop() }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    TabItem(Tab.HOME, "Beranda", Icons.Filled.Home)
                    TabItem(Tab.QURAN, "Al-Qur'an", Icons.AutoMirrored.Filled.MenuBook)
                    TabItem(Tab.HADITH, "Hadits", Icons.AutoMirrored.Filled.LibraryBooks)
                    TabItem(Tab.SEARCH, "Cari", Icons.Filled.Search)
                    TabItem(Tab.SAVED, "Tersimpan", Icons.Filled.Bookmarks)
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                val entry = nav.current
                holder.SaveableStateProvider(entry.key) {
                    ScreenHost(entry.screen, nav)
                }
            }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.RowScope.TabItem(tab: Tab, label: String, icon: ImageVector) {
        NavigationBarItem(
            selected = nav.tab == tab,
            onClick = { nav.select(tab) },
            icon = { Icon(icon, label) },
            label = { Text(label, maxLines = 1) },
        )
    }
}

@Composable
fun ScreenHost(screen: Screen, nav: Nav) {
    when (screen) {
        Screen.Home -> HomeScreen(nav)
        Screen.SurahList -> SurahListScreen(nav)
        is Screen.SurahDetail -> SurahScreen(nav, screen.surah, screen.scrollToAyah)
        is Screen.AyahDetail -> AyahDetailScreen(nav, screen.id)
        Screen.KitabList -> KitabListScreen(nav)
        is Screen.HadithList -> HadithListScreen(nav, screen.kitab)
        is Screen.HadithDetail -> HadithDetailScreen(nav, screen.id)
        is Screen.Search -> SearchScreen(nav, screen.initial)
        Screen.Saved -> SavedScreen(nav)
        Screen.Settings -> SettingsScreen(nav)
    }
}
