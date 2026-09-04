package com.example.dailyexpensetracker.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.dailyexpensetracker.R

/**
 * Paired expense/income bars per period. Geometry lives in the companion's pure [barHeights] so
 * it can be unit-tested without instrumentation.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(val label: String, val expense: Double, val income: Double)

    private var bars: List<Bar> = emptyList()

    private val expensePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.soft_red)
    }
    private val incomePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.soft_blue)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = resources.displayMetrics.density * 10f
        textAlign = Paint.Align.CENTER
    }
    fun setBars(list: List<Bar>) {
        bars = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        // Reserve the full ascent-to-descent band, not just the glyph box: month labels like
        // "Apr" and "Sep" have descenders, and drawing them on a baseline at the view's bottom
        // edge clips their tails off.
        val metrics = labelPaint.fontMetrics
        val labelHeight = (metrics.descent - metrics.ascent) + resources.displayMetrics.density * 4f
        val plotHeight = height - labelHeight
        if (plotHeight <= 0f) return

        // One shared scale across both series: normalising each to its own max would make a
        // small income bar look the same height as a large expense bar.
        val heights = barHeights(bars.flatMap { listOf(it.expense, it.income) }, plotHeight)

        val slotWidth = width.toFloat() / bars.size
        val barWidth = slotWidth * 0.28f
        val gap = slotWidth * 0.06f

        bars.forEachIndexed { index, bar ->
            val centerX = slotWidth * index + slotWidth / 2f
            val expenseHeight = heights[index * 2]
            val incomeHeight = heights[index * 2 + 1]

            canvas.drawRect(
                centerX - barWidth - gap / 2f, plotHeight - expenseHeight,
                centerX - gap / 2f, plotHeight, expensePaint
            )
            canvas.drawRect(
                centerX + gap / 2f, plotHeight - incomeHeight,
                centerX + barWidth + gap / 2f, plotHeight, incomePaint
            )
            canvas.drawText(bar.label, centerX, height - metrics.descent, labelPaint)
        }
    }

    companion object {
        /**
         * Values scaled so the largest maps to [maxHeightPx]. Negatives clamp to zero, and an
         * all-zero input returns zeros rather than dividing by zero. Result is always the same
         * length as the input.
         */
        fun barHeights(values: List<Double>, maxHeightPx: Float): List<Float> {
            if (values.isEmpty()) return emptyList()
            val safe = values.map { if (it > 0.0) it else 0.0 }
            val max = safe.max()
            if (max <= 0.0) return safe.map { 0f }
            return safe.map { (it / max * maxHeightPx).toFloat() }
        }
    }
}
