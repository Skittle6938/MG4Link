package com.andrerinas.openheadunit.hud

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import org.robolectric.RuntimeEnvironment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The cluster's media card (#76), with and without a cover, to app/build/screenshots. */
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [28], qualifiers = "mdpi")
class ClusterMediaCardShotTest {

    private fun cover(): Bitmap {
        val b = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 400f, 0xFF1A0B2E.toInt(), 0xFFC2306F.toInt(), Shader.TileMode.CLAMP) })
        c.drawCircle(260f, 150f, 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 50f, 0f, 250f, 0xFFFFB347.toInt(), 0xFFE8507A.toInt(), Shader.TileMode.CLAMP) })
        return b
    }

    private fun shot(name: String, cover: Bitmap?) {
        val card = ClusterMediaCard(RuntimeEnvironment.getApplication(), 260)
        card.bind("Midnight Boulevard", "Velvet Avenue", cover, 84_000, 200_000)
        val spec = View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY)
        card.view.measure(spec, spec)
        card.view.layout(0, 0, 260, 260)
        val bitmap = Bitmap.createBitmap(260, 260, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.BLACK); card.view.draw(this) }
        File("build/screenshots").mkdirs()
        File("build/screenshots/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun withCover() = shot("cluster_media_card", cover())
    @Test fun withoutCover() = shot("cluster_media_card_no_cover", null)
}
