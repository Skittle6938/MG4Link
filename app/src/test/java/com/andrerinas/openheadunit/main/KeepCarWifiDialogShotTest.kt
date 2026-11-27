package com.andrerinas.openheadunit.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.andrerinas.openheadunit.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The warning before the experimental "keep the car's Wi-Fi" option, to app/build/screenshots. */
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [28], qualifiers = "fr-w1920dp-h720dp-land-night-mdpi")
class KeepCarWifiDialogShotTest {

    @Test fun keepCarWifiDialog() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
        activity.setTheme(R.style.AppTheme)
        val dialog = MaterialAlertDialogBuilder(activity, R.style.DarkAlertDialog)
            .setTitle(R.string.dset_keep_car_wifi)
            .setMessage(R.string.dset_keep_car_wifi_explain)
            .setPositiveButton(R.string.dset_keep_car_wifi_enable, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.textSize = 24f
        shadowOf(Looper.getMainLooper()).idle()
        val decor = dialog.window!!.decorView
        val width = 1400
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        decor.layout(0, 0, width, decor.measuredHeight)
        val bitmap = Bitmap.createBitmap(width, decor.measuredHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.BLACK); decor.draw(this) }
        File("build/screenshots").mkdirs()
        File("build/screenshots/keep_car_wifi_dialog.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
