package com.hayu.app

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/** Baris yang membungkus ke bawah kalau penuh dan setiap barisnya rata tengah (untuk deretan badge). */
class FlowRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var hGap: Int = (6 * resources.displayMetrics.density).toInt()
    var vGap: Int = (6 * resources.displayMetrics.density).toInt()
    var centered: Boolean = false

    private class Row(val start: Int, val end: Int, val width: Int, val height: Int)

    private fun buildRows(maxW: Int, measure: Boolean): List<Row> {
        val rows = ArrayList<Row>()
        var first = -1
        var last = -1
        var lineW = 0
        var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            if (measure) {
                val lp = c.layoutParams
                c.measure(
                    ViewGroup.getChildMeasureSpec(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), 0, lp.width),
                    ViewGroup.getChildMeasureSpec(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, lp.height)
                )
            }
            val w = c.measuredWidth
            val h = c.measuredHeight
            if (first != -1 && lineW + hGap + w > maxW) {
                rows.add(Row(first, last, lineW, lineH))
                first = -1
                lineW = 0
                lineH = 0
            }
            if (first == -1) first = i
            last = i
            lineW += (if (lineW > 0) hGap else 0) + w
            if (h > lineH) lineH = h
        }
        if (first != -1) rows.add(Row(first, last, lineW, lineH))
        return rows
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val maxW = if (mode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE / 2
        else (size - paddingLeft - paddingRight).coerceAtLeast(0)

        val rows = buildRows(maxW, true)
        var totalH = 0
        var widest = 0
        for ((idx, r) in rows.withIndex()) {
            totalH += r.height + (if (idx > 0) vGap else 0)
            if (r.width > widest) widest = r.width
        }
        val w = if (mode == MeasureSpec.UNSPECIFIED) widest + paddingLeft + paddingRight else size
        setMeasuredDimension(w, totalH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = (r - l - paddingLeft - paddingRight).coerceAtLeast(0)
        val rows = buildRows(maxW, false)
        var y = paddingTop
        for (row in rows) {
            var x = paddingLeft + if (centered) (maxW - row.width) / 2 else 0
            for (i in row.start..row.end) {
                val c = getChildAt(i)
                if (c.visibility == View.GONE) continue
                val top = y + (row.height - c.measuredHeight) / 2
                c.layout(x, top, x + c.measuredWidth, top + c.measuredHeight)
                x += c.measuredWidth + hGap
            }
            y += row.height + vGap
        }
    }
}
