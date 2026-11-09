package id.alquranhadits.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.alquranhadits.data.Ayah
import id.alquranhadits.data.Hadith
import id.alquranhadits.data.JENIS_KUTIP
import id.alquranhadits.data.Prefs
import id.alquranhadits.data.TextNorm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Memuat data di thread IO; null selama memuat. */
@Composable
fun <T> load(vararg keys: Any?, block: suspend () -> T): State<T?> =
    produceState<T?>(null, *keys) {
        value = withContext(Dispatchers.IO) { block() }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ArabicText(text: String, size: Float, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth(),
            fontSize = size.sp,
            lineHeight = (size * 1.85f).sp,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
fun NumberBadge(n: Int) {
    Box(
        Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = color, shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LinkTag(jenis: Int) {
    if (jenis == JENIS_KUTIP) Tag("Mengutip ayat", MaterialTheme.colorScheme.secondaryContainer)
    else Tag("Tema serupa", MaterialTheme.colorScheme.tertiaryContainer)
}

/** Sorot kata kunci (tanpa membedakan huruf besar/kecil). */
fun highlight(text: String, terms: List<String>, color: Color): AnnotatedString {
    if (terms.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    val ranges = mutableListOf<IntRange>()
    for (t in terms) {
        var i = lower.indexOf(t)
        while (i >= 0) {
            ranges.add(i until i + t.length)
            i = lower.indexOf(t, i + t.length)
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (r in ranges) addStyle(SpanStyle(background = color, fontWeight = FontWeight.SemiBold), r.first, r.last + 1)
    }
}

/** Potong teks di sekitar kata kunci pertama yang ditemukan. */
fun snippet(text: String, terms: List<String>, len: Int = 260): String {
    val flat = text.replace('\n', ' ')
    if (flat.length <= len) return flat
    val lower = flat.lowercase()
    val idx = terms.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return flat.take(len).trimEnd() + "…"
    val start = (idx - 70).coerceAtLeast(0)
    val end = (start + len).coerceAtMost(flat.length)
    return (if (start > 0) "…" else "") + flat.substring(start, end).trim() + (if (end < flat.length) "…" else "")
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, "Bagikan"))
}

fun copyText(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("teks", text))
    Toast.makeText(ctx, "Disalin", Toast.LENGTH_SHORT).show()
}

fun ayahShareText(a: Ayah) = "${a.ar}\n\n\"${a.idn}\"\n\n(${a.ref})"
fun hadithShareText(h: Hadith) = (if (h.ar.isNotBlank()) h.ar + "\n\n" else "") + h.idn + "\n\n(${h.ref})"

@Composable
fun ActionRow(key: String, share: String, prefs: Prefs, extra: @Composable () -> Unit = {}) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        extra()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { prefs.toggleBookmark(key) }) {
            val on = key in prefs.bookmarks
            Icon(if (on) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Simpan",
                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { copyText(ctx, share) }) { Icon(Icons.Filled.ContentCopy, "Salin", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = { shareText(ctx, share) }) { Icon(Icons.Filled.Share, "Bagikan", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Kartu ringkas sebuah ayat (untuk daftar hasil / keterkaitan). */
@Composable
fun AyahRow(a: Ayah, terms: List<String> = emptyList(), tag: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val prefs = Prefs.get(LocalContext.current)
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.ref, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                tag?.invoke()
            }
            Spacer(Modifier.size(6.dp))
            ArabicText(a.ar, prefs.arabicSize * 0.8f, Modifier.padding(vertical = 2.dp))
            Text(
                highlight(snippet(a.idn, terms, 320), terms, MaterialTheme.colorScheme.secondaryContainer),
                style = MaterialTheme.typography.bodyMedium, fontSize = (prefs.latinSize - 1).sp
            )
        }
    }
}

/** Kartu ringkas sebuah hadits. */
@Composable
fun HadithRow(h: Hadith, terms: List<String> = emptyList(), tag: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val prefs = Prefs.get(LocalContext.current)
    val body = if (terms.isNotEmpty() && h.idn.isNotEmpty()) snippet(h.idn, terms) else h.ringkas
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.ref, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                tag?.invoke()
            }
            Text(
                highlight(body, terms, MaterialTheme.colorScheme.secondaryContainer),
                style = MaterialTheme.typography.bodyMedium, fontSize = (prefs.latinSize - 1).sp
            )
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

fun arabicAyahEnd(n: Int) = " ۝" + TextNorm.toArabicDigits(n)
