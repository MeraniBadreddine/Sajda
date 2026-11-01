package com.sajda.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.sajda.geo.GeoMath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * iOS-style compass.
 *
 * Rendering: hairline dial, minor ticks every 6°, major ticks + numerals every 30°,
 * slim fixed heading pointer, refined Qibla marker with an outer locating dot.
 *
 * Motion: the dial does not jump on sensor events. A frame callback eases the
 * displayed heading toward the target with frame-rate-independent exponential
 * smoothing — the Apple "buttery" feel — then stops when settled.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    // ── Motion state ──────────────────────────────────────────────
    private var targetHeading = 0f
    private var displayHeading = Float.NaN
    private var animating = false
    private var lastFrameNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!animating) return
            if (displayHeading.isNaN()) {
                displayHeading = targetHeading
                invalidate()
                stopDialAnimation()
                return
            }
            val dtSec = if (lastFrameNanos == 0L) 1f / 60f
            else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat()
            lastFrameNanos = frameTimeNanos

            val delta = GeoMath.angleDelta(displayHeading.toDouble(), targetHeading.toDouble())
            // Frame-rate independent exponential ease toward the target.
            val alpha = (1.0 - Math.exp(-SMOOTHING * dtSec)).coerceIn(0.0, 1.0)
            displayHeading = GeoMath.normalizeDegrees(displayHeading + delta * alpha).toFloat()

            if (abs(delta) < 0.02) {
                displayHeading = targetHeading
                invalidate()
                stopDialAnimation()
                return
            }
            Choreographer.getInstance().postFrameCallback(this)
            invalidate()
        }
    }

    private fun startDialAnimation() {
        if (animating) return
        animating = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopDialAnimation() {
        animating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    // ── Paints ────────────────────────────────────────────────────
    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.0f)
        color = Color.parseColor("#26FFFFFF")
    }
    private val minorTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.0f)
        color = Color.parseColor("#33EBEBF5")
        strokeCap = Paint.Cap.ROUND
    }
    private val majorTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.parseColor("#99EBEBF5")
        strokeCap = Paint.Cap.ROUND
    }
    private val degreePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#59EBEBF5")
        textAlign = Paint.Align.CENTER
        textSize = sp(9f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val qiblaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0A84FF")
    }
    private val qiblaStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.parseColor("#0A84FF")
        strokeCap = Paint.Cap.ROUND
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1C1C1E")
    }

    var deviceHeading: Float = 0f
        set(value) {
            field = value
            targetHeading = value
            if (!animating) startDialAnimation()
        }

    var qiblaBearing: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /** When true, dial is drawn quieter — heading is not trustworthy. */
    var uncertain: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val triangle = Path()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Ensure the first paint settles from a real frame, not a jump.
        if (!animating) startDialAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopDialAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dim = if (uncertain) 0.45f else 1f
        val layerSave = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255 * dim).toInt())
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - dp(24f)
        if (r <= 0f) {
            canvas.restoreToCount(layerSave)
            return
        }

        val heading = if (displayHeading.isNaN()) targetHeading else displayHeading

        canvas.save()
        canvas.rotate(-heading, cx, cy)

        canvas.drawCircle(cx, cy, r, dialPaint)

        // Minor ticks every 6° — dense, quiet, iOS-like rhythm
        for (deg in 0 until 360 step 6) {
            if (deg % 30 == 0) continue
            val rad = Math.toRadians(deg.toDouble())
            val x1 = cx + (r - dp(6f)) * sin(rad).toFloat()
            val y1 = cy - (r - dp(6f)) * cos(rad).toFloat()
            val x2 = cx + r * sin(rad).toFloat()
            val y2 = cy - r * cos(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, minorTick)
        }

        // Major ticks every 30° with numerals
        for (deg in 0 until 360 step 30) {
            val rad = Math.toRadians(deg.toDouble())
            val x1 = cx + (r - dp(11f)) * sin(rad).toFloat()
            val y1 = cy - (r - dp(11f)) * cos(rad).toFloat()
            val x2 = cx + r * sin(rad).toFloat()
            val y2 = cy - r * cos(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, majorTick)
        }

        // Cardinal letters N/E/S/W — N stays the brightest anchor
        drawDialText(canvas, cx, cy, r, 0f, "N", cardinalPaint)
        drawDialText(canvas, cx, cy, r, 90f, "E", degreePaint)
        drawDialText(canvas, cx, cy, r, 180f, "S", degreePaint)
        drawDialText(canvas, cx, cy, r, 270f, "W", degreePaint)

        // Degree numerals at non-cardinal 30° marks
        for (deg in 30 until 360 step 30) {
            if (deg % 90 == 0) continue
            drawDialText(canvas, cx, cy, r, deg.toFloat(), deg.toString(), degreePaint)
        }

        // Qibla marker — slim arrow, fixed in true space
        val qb = Math.toRadians(qiblaBearing.toDouble())
        val tipR = r - dp(2f)
        val baseR = r - dp(18f)
        val spread = Math.toRadians(5.5)
        triangle.reset()
        triangle.moveTo(cx + tipR * sin(qb).toFloat(), cy - tipR * cos(qb).toFloat())
        triangle.lineTo(cx + baseR * sin(qb - spread).toFloat(), cy - baseR * cos(qb - spread).toFloat())
        triangle.lineTo(cx + baseR * sin(qb + spread).toFloat(), cy - baseR * cos(qb + spread).toFloat())
        triangle.close()
        canvas.drawPath(triangle, qiblaPaint)

        val qx = cx + (r - dp(6f)) * sin(qb).toFloat()
        val qy = cy - (r - dp(6f)) * cos(qb).toFloat()
        canvas.drawLine(cx, cy, qx, qy, qiblaStroke)

        // Locating dot just OUTSIDE the rim — find the Qibla from a glance
        val dotR = r + dp(8f)
        canvas.drawCircle(
            cx + dotR * sin(qb).toFloat(),
            cy - dotR * cos(qb).toFloat(),
            dp(3f),
            qiblaPaint
        )

        canvas.restore()

        // Fixed heading pointer above the dial — slim, precise, iOS style
        val tipY = cy - r - dp(18f)
        triangle.reset()
        triangle.moveTo(cx, tipY)
        triangle.lineTo(cx - dp(4f), tipY + dp(9f))
        triangle.lineTo(cx + dp(4f), tipY + dp(9f))
        triangle.close()
        canvas.drawPath(triangle, pointerPaint)

        canvas.drawCircle(cx, cy, dp(3.5f), centerPaint)
        canvas.drawCircle(cx, cy, dp(3.5f), dialPaint)
        canvas.restoreToCount(layerSave)
    }

    private fun drawDialText(
        canvas: Canvas, cx: Float, cy: Float, r: Float, deg: Float, label: String, paint: Paint
    ) {
        val rad = Math.toRadians(deg.toDouble())
        val tr = r - dp(28f)
        val x = cx + tr * sin(rad).toFloat()
        val y = cy - tr * cos(rad).toFloat() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(label, x, y, paint)
    }

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity

    private companion object {
        /** Higher = snappier dial, lower = heavier. 10 ≈ iOS compass feel. */
        const val SMOOTHING = 10.0
    }
}