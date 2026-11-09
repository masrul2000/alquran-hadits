package id.alquranhadits.notif

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import id.alquranhadits.R
import id.alquranhadits.data.Db
import id.alquranhadits.data.Prefs
import id.alquranhadits.data.Repo
import id.alquranhadits.ui.MainActivity
import java.util.Calendar

object Scheduler {
    const val CHANNEL_AYAT = "harian_ayat"
    const val CHANNEL_HADITS = "harian_hadits"
    const val EXTRA_JENIS = "jenis"
    const val EXTRA_OPEN = "open"
    const val EXTRA_ID = "id"

    fun scheduleAll(ctx: Context) {
        schedule(ctx, Prefs.AYAT)
        schedule(ctx, Prefs.HADITS)
    }

    private fun pending(ctx: Context, jenis: Int): PendingIntent {
        val i = Intent(ctx, DailyReceiver::class.java).putExtra(EXTRA_JENIS, jenis)
        return PendingIntent.getBroadcast(ctx, jenis, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun schedule(ctx: Context, jenis: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, jenis)
        am.cancel(pi)
        val prefs = Prefs.get(ctx)
        if (!prefs.notifEnabled(jenis)) return
        val m = prefs.notifMinutes(jenis)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, m / 60)
            set(Calendar.MINUTE, m % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 30_000) cal.add(Calendar.DAY_OF_YEAR, 1)
        // Alarm tidak-eksak (tidak butuh izin khusus), tetap berbunyi saat mode Doze.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, jenis: Int) {
        if (!Db.isReady(ctx)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val repo = Repo.get(ctx)
        val title: String
        val body: String
        val sub: String
        val open: String
        val id: Int
        val channel: String
        if (jenis == Prefs.AYAT) {
            val a = repo.dailyAyah() ?: return
            title = "Ayat Pilihan Hari Ini"
            body = a.idn
            sub = a.ref
            open = "ayah"; id = a.id; channel = CHANNEL_AYAT
        } else {
            val h = repo.dailyHadith() ?: return
            title = "Hadits Pilihan Hari Ini"
            body = h.ringkas
            sub = h.ref
            open = "hadith"; id = h.id; channel = CHANNEL_HADITS
        }
        val tap = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, open).putExtra(EXTRA_ID, id)
        val pi = PendingIntent.getActivity(ctx, 100 + jenis, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_quran)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(sub)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n— $sub"))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(jenis, n)
        } catch (e: SecurityException) {
            // izin dicabut
        }
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jenis = intent.getIntExtra(Scheduler.EXTRA_JENIS, Prefs.AYAT)
        val app = context.applicationContext
        val result = goAsync()
        Thread {
            try {
                Scheduler.show(app, jenis)
            } finally {
                Scheduler.schedule(app, jenis)
                result.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.scheduleAll(context.applicationContext)
    }
}
