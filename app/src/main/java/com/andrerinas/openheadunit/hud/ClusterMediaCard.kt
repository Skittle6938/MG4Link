package com.andrerinas.openheadunit.hud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

/**
 * The playing track as a card for the instrument cluster (#76), in the style of MG4 Dash's media
 * widget: the cover fills the card, darkened to the left and at the bottom, with the source at the
 * top and the title, artist and a thin progress line over it. No buttons: the cluster is not touched.
 *
 * Sizes are cluster pixels, scaled from the 260 px design, so the card does not depend on the
 * cluster display's density.
 */
internal class ClusterMediaCard(context: Context, side: Int) {
    private val scale = side / 260f
    private fun px(v: Float) = Math.round(v * scale)

    val view = FrameLayout(context)
    private val art = ImageView(context)
    private val placeholder = TextView(context)
    private val title = TextView(context)
    private val artist = TextView(context)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)

    init {
        val radius = px(14f).toFloat()
        view.setBackgroundColor(BACKGROUND)
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) = outline.setRoundRect(0, 0, v.width, v.height, radius)
        }

        art.scaleType = ImageView.ScaleType.CENTER_CROP
        view.addView(art, FrameLayout.LayoutParams(side, side))

        placeholder.text = "♪"
        placeholder.gravity = Gravity.CENTER
        placeholder.setTextColor(ACCENT)
        placeholder.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(90f).toFloat())
        view.addView(placeholder, FrameLayout.LayoutParams(side, px(190f)))

        // Darker to the left and at the bottom, so white text stays readable on any cover.
        val shade = View(context)
        shade.background = LayerDrawable(arrayOf(
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xBF060A10.toInt(), 0x33060A10)),
            GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xD9060A10.toInt(), 0x00060A10)).also {
                it.setGradientCenter(0.5f, 0.45f)
            },
        ))
        view.addView(shade, FrameLayout.LayoutParams(side, side))

        val source = TextView(context)
        source.text = SOURCE
        source.setTextColor(Color.WHITE)
        source.typeface = Typeface.DEFAULT_BOLD
        source.letterSpacing = 0.16f
        source.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(12f).toFloat())
        view.addView(source, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin = px(16f); it.topMargin = px(14f)
        })

        fun line(text: TextView, size: Float, color: Int, bottom: Float, bold: Boolean) {
            text.setTextColor(color)
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size).toFloat())
            text.isSingleLine = true
            text.ellipsize = TextUtils.TruncateAt.END
            if (bold) text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            view.addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM).also { it.leftMargin = px(16f); it.rightMargin = px(16f); it.bottomMargin = px(bottom) })
        }
        line(title, 22f, Color.WHITE, 46f, bold = true)
        line(artist, 15f, 0xFFE6E6EA.toInt(), 26f, bold = false)

        progress.max = 1000
        progress.progressDrawable = LayerDrawable(arrayOf(
            ColorDrawable(0x40FFFFFF),
            ClipDrawable(ColorDrawable(ACCENT), Gravity.START, ClipDrawable.HORIZONTAL),
        )).also { it.setId(0, android.R.id.background); it.setId(1, android.R.id.progress) }
        view.addView(progress, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, maxOf(2, px(3f)), Gravity.BOTTOM).also {
            it.leftMargin = px(16f); it.rightMargin = px(16f); it.bottomMargin = px(16f)
        })
    }

    fun bind(song: String, by: String, cover: Bitmap?, positionMs: Long, durationMs: Long) {
        if (title.text != song) title.text = song
        if (artist.text != by) artist.text = by
        art.setImageBitmap(cover)
        placeholder.visibility = if (cover == null) View.VISIBLE else View.GONE
        progress.visibility = if (durationMs > 0) View.VISIBLE else View.INVISIBLE
        if (durationMs > 0) progress.progress = (positionMs * 1000 / durationMs).toInt().coerceIn(0, 1000)
    }

    companion object {
        const val SOURCE = "ANDROID AUTO"
        /** The approved design's colours: MG4 Dash's default accent and card background. */
        const val ACCENT = 0xFF00D2FF.toInt()
        const val BACKGROUND = 0xFF06252C.toInt()
    }
}
