package com.andrerinas.openheadunit.main

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.andrerinas.openheadunit.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The "connection lost" screen over Android Auto (#72), written to app/build/screenshots/. */
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [28], qualifiers = "fr-w1920dp-h720dp-land-night-mdpi")
class ConnectionLostShotTest {

    @Test fun connectionLostWireless() = shot("connection_lost_wireless.png", wireless = true)

    @Test fun connectionLostUsb() = shot("connection_lost_usb.png", wireless = false)

    private fun shot(name: String, wireless: Boolean) {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
        val root = LayoutInflater.from(context).inflate(R.layout.activity_headunit, null)
        root.findViewById<TextView>(R.id.overlay_text).text = context.getString(R.string.connection_interrupted)
        root.findViewById<TextView>(R.id.overlay_detail).apply {
            text = if (wireless) {
                context.getString(R.string.connection_interrupted_detail) + "\n\n" + context.getString(R.string.wifi_disconnect_toast)
            } else {
                context.getString(R.string.connection_interrupted_detail)
            }
            visibility = View.VISIBLE
        }
        root.findViewById<Button>(R.id.disconnect_button).visibility = View.VISIBLE
        val width = 1920
        val height = 720
        repeat(2) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, width, height)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
