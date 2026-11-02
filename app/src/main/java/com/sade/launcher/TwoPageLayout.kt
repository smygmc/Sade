package com.sade.launcher

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.widget.OverScroller
import kotlin.math.abs

/** Exact viewport measurement; returning Home cancels any pending animation. */
class TwoPageLayout(context: Context, private val drawerMode: Boolean = false) : ViewGroup(context) {
    private val topGap get() = minOf((height * .16f).toInt(), (144 * resources.displayMetrics.density).toInt())
    private val sideGap get() = (12 * resources.displayMetrics.density).toInt()
    private val extent get() = if (drawerMode) height - topGap else width
    private var drawerOffset = 0
    private val offset get() = if (drawerMode) drawerOffset else scrollX
    var drawerHandleHeight = 0
    private var fromHandle = false
    private var outsideDrawer = false
    private var velocity: VelocityTracker? = null
    private var backdropActive = false
    private val scrim = android.graphics.Paint()
    private var homeSnapshots = emptyList<android.graphics.Bitmap>()
    private val snapshotBounds = android.graphics.Rect()
    private val snapshotPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val paper = android.graphics.Color.parseColor(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("paper", "#F3F0E8"))
    private fun updateBackdrop(active: Boolean) {
        if (backdropActive == active || childCount == 0) return
        backdropActive = active
        homeSnapshots = if (active && width > 0 && height > 0) HomeBackdrop.capture(getChildAt(0), paper) else emptyList()
    }
    private fun moveTo(value: Int) {
        if (!drawerMode) { scrollTo(value, 0); return }
        drawerOffset = value.coerceIn(0, extent.coerceAtLeast(0))
        if (childCount >= 2) {
            getChildAt(1).translationY = (height - drawerOffset).toFloat()
            getChildAt(1).visibility = if (drawerOffset == 0) INVISIBLE else VISIBLE
            updateBackdrop(drawerOffset > 0)
            invalidate()
        }
    }
    private fun canScroll(view: View, direction: Int): Boolean = view.canScrollVertically(direction) ||
        (view is ViewGroup && (0 until view.childCount).any { canScroll(view.getChildAt(it), direction) })
    private val scroller = OverScroller(context)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var origin = 0
    private var vertical = false
    private var dragging = false
    var page = 0
        private set
    var onPageChanged: ((Int) -> Unit)? = null
    // Child focus/keyboard requests must not independently scroll this horizontal pager.
    override fun requestChildRectangleOnScreen(child: View, rectangle: android.graphics.Rect, immediate: Boolean) = false
    override fun onDetachedFromWindow() {
        scroller.abortAnimation(); dragging = false; moveTo(page * extent)
        velocity?.recycle(); velocity = null
        updateBackdrop(false)
        super.onDetachedFromWindow()
    }
    fun addPage(view: View) { addView(view, LayoutParams(-1, -1)) }
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec); val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        for (i in 0 until childCount) {
            val sheet = drawerMode && i == 1
            val gap = minOf((h * .16f).toInt(), (144 * resources.displayMetrics.density).toInt())
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(if (sheet) (w - 2 * sideGap).coerceAtLeast(0) else w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(if (sheet) h - gap else h, MeasureSpec.EXACTLY))
        }
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            if (drawerMode && i == 1) getChildAt(i).layout(sideGap, 0, width - sideGap, extent)
            else if (drawerMode) getChildAt(i).layout(0, 0, width, height)
            else getChildAt(i).layout(i * width, 0, (i + 1) * width, height)
        }
        if (changed) { updateBackdrop(false); scroller.abortAnimation(); dragging = false }
        // Child relayouts also happen after keyboard/PiP transitions without changing our bounds.
        if (!dragging && scroller.isFinished) moveTo(page * extent)
    }
    fun showPage(index: Int, animate: Boolean = true) {
        scroller.abortAnimation(); dragging = false
        page = index.coerceIn(0, 1)
        if (animate && extent > 0) {
            if (drawerMode) scroller.startScroll(0, drawerOffset, 0, page * extent - drawerOffset, 260)
            else scroller.startScroll(scrollX, 0, page * width - scrollX, 0, 180)
            postInvalidateOnAnimation()
        } else moveTo(page * extent)
        for (i in 0 until childCount) getChildAt(i).importantForAccessibility =
            if (i == page) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        onPageChanged?.invoke(page)
    }
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) { moveTo((if (drawerMode) scroller.currY else scroller.currX).coerceIn(0, extent)); postInvalidateOnAnimation() }
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                if (!drawerMode) moveTo(page * extent)
                downX = event.x; downY = event.y
                origin = offset; vertical = false; dragging = false
                outsideDrawer = drawerMode && page == 1 && (event.y < topGap || event.x < sideGap || event.x > width - sideGap)
                fromHandle = drawerMode && page == 1 && (outsideDrawer || event.y < topGap + drawerHandleHeight)
                if (outsideDrawer) return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX); val dy = abs(event.y - downY)
                if (drawerMode) {
                    if (dx > slop && dx > dy) vertical = true
                    if (!vertical && dy > slop && dy > dx) {
                        val opening = page == 0 && event.y < downY
                        if (DrawerGesture.capture(page, event.y - downY, fromHandle, canScroll(getChildAt(page), if (opening) 1 else -1))) dragging = true
                        else vertical = true
                    }
                    return dragging
                }
                if (dy > slop && dy > dx) vertical = true
                if (!vertical && dx > slop && dx > dy) dragging = true
            }
        }
        return dragging
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; origin = offset; scroller.abortAnimation() }
            MotionEvent.ACTION_MOVE -> {
                // Empty home areas deliver directly here, without a child to intercept from.
                if (drawerMode && !dragging) onInterceptTouchEvent(event)
                if (!drawerMode || dragging) moveTo((origin + (if (drawerMode) downY - event.y else downX - event.x).toInt()).coerceIn(0, extent))
            }
            MotionEvent.ACTION_UP -> {
                val delta = if (drawerMode) event.y - downY else event.x - downX
                if (outsideDrawer && abs(delta) < slop && abs(event.x - downX) < slop) { showPage(0); return true }
                if (abs(delta) < slop) performClick()
                velocity?.computeCurrentVelocity(1000)
                showPage(if (drawerMode) DrawerGesture.target(page, delta, velocity?.yVelocity ?: 0f, extent.toFloat(), resources.displayMetrics.density, slop, dragging)
                    else if (abs(delta) < width * .16f) page else if (delta < 0) page + 1 else page - 1)
            }
            MotionEvent.ACTION_CANCEL -> showPage(page)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    override fun drawChild(canvas: android.graphics.Canvas, child: View, drawingTime: Long): Boolean {
        if (drawerMode && child === getChildAt(0) && homeSnapshots.isNotEmpty()) {
            val progress = (drawerOffset.toFloat() / extent.coerceAtLeast(1)).coerceIn(0f, 1f)
            val level = progress * homeSnapshots.size
            val lower = level.toInt().coerceAtMost(homeSnapshots.size)
            val blend = level - lower
            snapshotBounds.set(0, 0, width, height)
            snapshotPaint.alpha = 255
            if (lower == 0) super.drawChild(canvas, child, drawingTime)
            else canvas.drawBitmap(homeSnapshots[lower - 1], null, snapshotBounds, snapshotPaint)
            if (lower < homeSnapshots.size && blend > 0f) {
                snapshotPaint.alpha = (blend * 255).toInt()
                canvas.drawBitmap(homeSnapshots[lower], null, snapshotBounds, snapshotPaint)
            }
            snapshotPaint.alpha = 255
            scrim.color = android.graphics.Color.argb((40 * progress).toInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
            return true
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            velocity?.recycle(); velocity = VelocityTracker.obtain()
        }
        velocity?.addMovement(event)
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            velocity?.recycle(); velocity = null
        }
        return handled
    }
}
