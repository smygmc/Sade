package com.sade.launcher

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Saturation/value field. Draws only when the selection or hue changes. */
class ColorSquare(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = floatArrayOf(0f, 0f, 1f)
    private var shader: Shader? = null
    var onColor: ((Int) -> Unit)? = null
    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        updateShader(); invalidate()
    }
    fun setHue(hue: Float) {
        hsv[0] = hue
        updateShader(); invalidate(); onColor?.invoke(Color.HSVToColor(hsv))
    }
    private fun updateShader() {
        if (width == 0 || height == 0) return
        val hue = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        shader = ComposeShader(
            LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, hue, Shader.TileMode.CLAMP),
            LinearGradient(0f, 0f, 0f, height.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP),
            PorterDuff.Mode.SRC_OVER)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { updateShader() }
    override fun onDraw(canvas: Canvas) {
        paint.shader = shader; paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null; paint.style = Paint.Style.STROKE
        val d = resources.displayMetrics.density
        val x = hsv[1] * width; val y = (1 - hsv[2]) * height
        paint.strokeWidth = 4*d; paint.color = Color.BLACK
        canvas.drawCircle(x, y, 7*d, paint)
        paint.strokeWidth = 2*d; paint.color = Color.WHITE
        canvas.drawCircle(x, y, 7*d, paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false); performClick() }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false); return true }
        }
        if (width > 0 && height > 0) {
            hsv[1] = (event.x / width).coerceIn(0f, 1f)
            hsv[2] = 1 - (event.y / height).coerceIn(0f, 1f)
            invalidate(); onColor?.invoke(Color.HSVToColor(hsv))
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
