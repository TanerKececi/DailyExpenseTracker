package com.example.dailyexpensetracker.ui.common.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartGeometryTest {

    @Test
    fun `sweepAngles sum to a full circle`() {
        val angles = DonutChartView.sweepAngles(listOf(1.0, 2.0, 3.0, 4.0))

        assertEquals(360f, angles.sum(), 0.01f)
    }

    @Test
    fun `sweepAngles are proportional to their values`() {
        val angles = DonutChartView.sweepAngles(listOf(25.0, 75.0))

        assertEquals(90f, angles[0], 0.01f)
        assertEquals(270f, angles[1], 0.01f)
    }

    @Test
    fun `sweepAngles gives a single value the whole circle`() {
        val angles = DonutChartView.sweepAngles(listOf(42.0))

        assertEquals(1, angles.size)
        assertEquals(360f, angles[0], 0.01f)
    }

    @Test
    fun `sweepAngles returns empty for an empty list`() {
        assertTrue(DonutChartView.sweepAngles(emptyList()).isEmpty())
    }

    @Test
    fun `sweepAngles returns empty when every value is zero`() {
        assertTrue(DonutChartView.sweepAngles(listOf(0.0, 0.0)).isEmpty())
    }

    @Test
    fun `sweepAngles ignores negative values rather than producing negative arcs`() {
        val angles = DonutChartView.sweepAngles(listOf(-5.0, 100.0))

        assertEquals(0f, angles[0], 0.01f)
        assertEquals(360f, angles[1], 0.01f)
    }

    @Test
    fun `barHeights maps the largest value to the full height`() {
        val heights = BarChartView.barHeights(listOf(10.0, 20.0, 40.0), 200f)

        assertEquals(200f, heights[2], 0.01f)
        assertEquals(100f, heights[1], 0.01f)
        assertEquals(50f, heights[0], 0.01f)
    }

    @Test
    fun `barHeights returns zeros when every value is zero`() {
        val heights = BarChartView.barHeights(listOf(0.0, 0.0), 200f)

        assertEquals(listOf(0f, 0f), heights)
    }

    @Test
    fun `barHeights returns empty for an empty list`() {
        assertTrue(BarChartView.barHeights(emptyList(), 200f).isEmpty())
    }

    @Test
    fun `barHeights clamps negatives to zero`() {
        val heights = BarChartView.barHeights(listOf(-10.0, 50.0), 100f)

        assertEquals(0f, heights[0], 0.01f)
        assertEquals(100f, heights[1], 0.01f)
    }
}
