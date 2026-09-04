package com.example.dailyexpensetracker.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * A ring of proportional arcs. Geometry lives in the companion's pure [sweepAngles] so it can be
 * unit-tested without instrumentation.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Segment(val value: Double, val color: Int)

    private var segments: List<Segment> = emptyList()

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val bounds = RectF()

    fun setSegments(list: List<Segment>) {
        segments = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sweeps = sweepAngles(segments.map { it.value })
        if (sweeps.isEmpty()) return

        val size = minOf(width, height).toFloat()
        val stroke = size * RING_THICKNESS
        arcPaint.strokeWidth = stroke

        val left = (width - size) / 2f + stroke / 2f
        val top = (height - size) / 2f + stroke / 2f
        bounds.set(left, top, left + size - stroke, top + size - stroke)

        var startAngle = -90f // 12 o'clock
        for (index in sweeps.indices) {
            arcPaint.color = segments[index].color
            canvas.drawArc(bounds, startAngle, sweeps[index], false, arcPaint)
            startAngle += sweeps[index]
        }
    }

    companion object {
        private const val RING_THICKNESS = 0.18f

        /**
         * Values converted to sweep angles totalling 360°. Negative values are treated as zero.
         * Returns an empty list when there is nothing to draw, so callers must not assume the
         * result is the same length as the input.
         */
        fun sweepAngles(values: List<Double>): List<Float> {
            val safe = values.map { if (it > 0.0) it else 0.0 }
            val total = safe.sum()
            if (total <= 0.0) return emptyList()
            return safe.map { (it / total * 360.0).toFloat() }
        }
    }
}
