package com.sajda.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Minimal compass: dial, cardinal marks, Qibla marker, device-heading tick.
 * Dial rotates so the Qibla marker stays fixed in true space while the phone turns.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.0f)
        color = Color.parseColor("#33FFFFFF")
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.parseColor("#66FFFFFF")
        strokeCap = Paint.Cap.ROUND
    }
    private val majorTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.parseColor("#99EBEBF5")
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99EBEBF5")
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
        strokeWidth = dp(2.5f)
        color = Color.parseColor("#0A84FF")
        strokeCap = Paint.Cap.ROUND
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
            invalidate()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dim = if (uncertain) 0.45f else 1f
        val layerSave = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255 * dim).toInt())
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - dp(20f)
        if (r <= 0f) {
            canvas.restoreToCount(layerSave)
            return
        }

        canvas.save()
        canvas.rotate(-deviceHeading, cx, cy)

        canvas.drawCircle(cx, cy, r, dialPaint)
        canvas.drawCircle(cx, cy, r * 0.72f, dialPaint)

        for (deg in 0 until 360 step 10) {
            val rad = Math.toRadians(deg.toDouble())
            val major = deg % 30 == 0
            val inner = if (major) r - dp(14f) else r - dp(8f)
            val p = if (major) majorTick else tickPaint
            val x1 = cx + inner * sin(rad).toFloat()
            val y1 = cy - inner * cos(rad).toFloat()
            val x2 = cx + r * sin(rad).toFloat()
            val y2 = cy - r * cos(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, p)
        }

        drawCardinal(canvas, cx, cy, r, 0f, "N")
        drawCardinal(canvas, cx, cy, r, 90f, "E")
        drawCardinal(canvas, cx, cy, r, 180f, "S")
        drawCardinal(canvas, cx, cy, r, 270f, "W")

        val qb = Math.toRadians(qiblaBearing.toDouble())
        val tipR = r - dp(2f)
        val baseR = r - dp(18f)
        val spread = Math.toRadians(7.0)
        triangle.reset()
        triangle.moveTo(
            cx + tipR * sin(qb).toFloat(),
            cy - tipR * cos(qb).toFloat()
        )
        triangle.lineTo(
            cx + baseR * sin(qb - spread).toFloat(),
            cy - baseR * cos(qb - spread).toFloat()
        )
        triangle.lineTo(
            cx + baseR * sin(qb + spread).toFloat(),
            cy - baseR * cos(qb + spread).toFloat()
        )
        triangle.close()
        canvas.drawPath(triangle, qiblaPaint)

        val qx = cx + (r - dp(6f)) * sin(qb).toFloat()
        val qy = cy - (r - dp(6f)) * cos(qb).toFloat()
        canvas.drawLine(cx, cy, qx, qy, qiblaStroke)

        canvas.restore()

        val tipY = cy - r - dp(4f)
        triangle.reset()
        triangle.moveTo(cx, tipY)
        triangle.lineTo(cx - dp(7f), tipY + dp(12f))
        triangle.lineTo(cx + dp(7f), tipY + dp(12f))
        triangle.close()
        canvas.drawPath(triangle, needlePaint)

        canvas.drawCircle(cx, cy, dp(5f), centerPaint)
        canvas.drawCircle(cx, cy, dp(5f), dialPaint)
        canvas.restoreToCount(layerSave)
    }

    private fun drawCardinal(canvas: Canvas, cx: Float, cy: Float, r: Float, deg: Float, label: String) {
        val rad = Math.toRadians(deg.toDouble())
        val tr = r - dp(28f)
        val x = cx + tr * sin(rad).toFloat()
        val y = cy - tr * cos(rad).toFloat() - (textPaint.descent() + textPaint.ascent()) / 2
        textPaint.color = if (label == "N") Color.WHITE else Color.parseColor("#99EBEBF5")
        canvas.drawText(label, x, y, textPaint)
    }

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity
}
