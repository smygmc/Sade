package com.sade.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/** Cached blur levels from one capture per opening; no blur calculation during dragging. */
object HomeBackdrop {
    fun capture(home: View, paper: Int): List<Bitmap> {
        // Keep enough detail for a frosted image instead of flattening text/patterns into a solid tone.
        val sampling = 4
        val width = (home.width / sampling).coerceAtLeast(1)
        val height = (home.height / sampling).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(paper)
        canvas.scale(width.toFloat() / home.width.coerceAtLeast(1), height.toFloat() / home.height.coerceAtLeast(1))
        home.draw(canvas)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val radius = (16 * home.resources.displayMetrics.density / sampling).toInt().coerceIn(1, 64)
        return (1..4).map { level ->
            val blurred = BackdropBlur.blur(pixels, width, height, (radius * level / 4).coerceAtLeast(1))
            val frame = if (level == 4) bitmap else Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            frame.setPixels(blurred, 0, width, 0, 0, width, height)
            frame
        }
    }
}
