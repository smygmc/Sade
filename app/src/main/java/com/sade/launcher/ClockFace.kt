package com.sade.launcher

import android.content.*
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.View
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/** Redraws on minute changes; no second hand or continuous animation. */
class ClockFace(context: Context, ink: Int) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeCap = Paint.Cap.ROUND }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { invalidate() }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        invalidate()
    }
    override fun onDetachedFromWindow() { context.unregisterReceiver(receiver); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = LocalTime.now()
        contentDescription = String.format(java.util.Locale.getDefault(), "%02d:%02d", now.hour, now.minute)
        val radius = minOf(width, height) * 0.43f
        val cx = width / 2f; val cy = height / 2f
        pen.style = Paint.Style.STROKE; pen.strokeWidth = resources.displayMetrics.density
        for (i in 0..11) {
            val a = Math.PI * i / 6 - Math.PI / 2
            val inner = radius * if (i % 3 == 0) 0.86f else 0.94f
            canvas.drawLine(cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner,
                cx + cos(a).toFloat() * radius, cy + sin(a).toFloat() * radius, pen)
        }
        fun hand(angle: Double, length: Float, stroke: Float) {
            pen.strokeWidth = stroke * resources.displayMetrics.density
            val a = angle - Math.PI / 2
            canvas.drawLine(cx, cy, cx + cos(a).toFloat() * radius * length, cy + sin(a).toFloat() * radius * length, pen)
        }
        hand((now.hour % 12 + now.minute / 60.0) * Math.PI / 6, 0.5f, 3f)
        hand(now.minute * Math.PI / 30, 0.76f, 2f)
        pen.style = Paint.Style.FILL; canvas.drawCircle(cx, cy, 3 * resources.displayMetrics.density, pen)
    }
}
