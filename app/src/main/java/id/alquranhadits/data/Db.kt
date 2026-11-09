package id.alquranhadits.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/** Database bawaan (assets/alquran_hadits.db.gz) disalin & diekstrak sekali saat pertama dibuka. */
object Db {
    private const val NAME = "alquran_hadits.db"
    private const val VERSION = 1

    @Volatile
    private var db: SQLiteDatabase? = null

    fun isReady(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences("db", Context.MODE_PRIVATE)
        return p.getInt("ver", 0) == VERSION && ctx.getDatabasePath(NAME).exists()
    }

    fun install(ctx: Context, onProgress: (Float) -> Unit) {
        val target = ctx.getDatabasePath(NAME)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "$NAME.tmp")
        val expected = ctx.assets.open("db_size.txt").bufferedReader().use { it.readText().trim().toLong() }
        GZIPInputStream(BufferedInputStream(ctx.assets.open("$NAME.gz"), 1 shl 16), 1 shl 16).use { input ->
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(1 shl 16)
                var total = 0L
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (total - last > 2_000_000) {
                        last = total
                        onProgress((total.toFloat() / expected).coerceAtMost(1f))
                    }
                }
            }
        }
        synchronized(this) {
            db?.close()
            db = null
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) throw IllegalStateException("Gagal menyiapkan database")
        ctx.getSharedPreferences("db", Context.MODE_PRIVATE).edit().putInt("ver", VERSION).apply()
        onProgress(1f)
    }

    fun get(ctx: Context): SQLiteDatabase {
        db?.let { return it }
        return synchronized(this) {
            db ?: SQLiteDatabase.openDatabase(
                ctx.getDatabasePath(NAME).path, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).also { db = it }
        }
    }
}
