package com.sade.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Serial writes, only on explicit application or an appearance-color change. */
object WallpaperSync {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    fun apply(context: Context, explicit: Boolean, complete: (WallpaperOwnership.Result) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val prefs = app.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            val manager = WallpaperManager.getInstance(app)
            val result = WallpaperOwnership.apply(object : WallpaperOwnership.Backend {
                override fun ownedId() = prefs.getInt("sade_wallpaper_id", 0)
                override fun currentId() = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                override fun allowed() = manager.isWallpaperSupported && manager.isSetWallpaperAllowed
                override fun saveOwnedId(id: Int) { prefs.edit().putInt("sade_wallpaper_id", id).apply() }
                override fun setSelectedColor(): Int {
                    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    return try {
                        bitmap.eraseColor(Color.parseColor(prefs.getString("paper", "#F3F0E8")))
                        manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM)
                    } finally { bitmap.recycle() }
                }
            }, explicit)
            main.post { complete(result) }
        }
    }
}
