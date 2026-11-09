package id.alquranhadits

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import id.alquranhadits.notif.Scheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(Scheduler.CHANNEL_AYAT, "Ayat pilihan harian", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Satu ayat Al-Qur'an pilihan setiap hari" }
        )
        nm.createNotificationChannel(
            NotificationChannel(Scheduler.CHANNEL_HADITS, "Hadits pilihan harian", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Satu hadits pilihan setiap hari" }
        )
        Scheduler.scheduleAll(this)
    }
}
