package com.example.cameramusicapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Musiqaning ovoz amplitudasini gorizontal chiziq shaklida, real vaqtda
 * chapdan o'ngga siljib boruvchi to'lqin sifatida chizadi.
 *
 * updateAmplitude(...) chaqirilganda yangi qiymat qo'shiladi va eng eski
 * qiymat ro'yxatdan chiqariladi (scrolling effekt).
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val amplitudes = ArrayDeque<Float>()
    private var maxSamples = 120

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val midLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        strokeWidth = 1.5f
    }

    /** 0f..1f oralig'ida normallashtirilgan amplituda qiymatini qo'shadi. */
    fun pushAmplitude(value: Float) {
        val v = value.coerceIn(0f, 1f)
        amplitudes.addLast(v)
        while (amplitudes.size > maxSamples) {
            amplitudes.removeFirst()
        }
        postInvalidateOnAnimation()
    }

    fun clear() {
        amplitudes.clear()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Kenglikka moslab namuna sonini belgilaymiz (har 6px ga bitta nuqta)
        maxSamples = max(40, w / 6)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midY = height / 2f
        canvas.drawLine(0f, midY, width.toFloat(), midY, midLinePaint)

        if (amplitudes.isEmpty()) return

        val stepX = width.toFloat() / maxSamples
        var x = width.toFloat() - (amplitudes.size * stepX)

        val list = amplitudes.toList()
        for (i in 0 until list.size - 1) {
            val amp1 = list[i] * (height / 2.2f)
            val amp2 = list[i + 1] * (height / 2.2f)
            canvas.drawLine(x, midY - amp1, x + stepX, midY - amp2, linePaint)
            canvas.drawLine(x, midY + amp1, x + stepX, midY + amp2, linePaint)
            x += stepX
        }
    }
}
