package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The Android Auto screen keeps the system window animation (#23, back since 2.2.7-beta24). */
class ProjectionThemeTest {
    private fun parse(name: String): Element {
        val file = listOf("src/main/$name", "app/src/main/$name").map(::File).first { it.exists() }
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.all(tag: String): List<Element> =
        (0 until getElementsByTagName(tag).length).map { getElementsByTagName(tag).item(it) as Element }

    @Test
    fun `the projection theme leaves the window animation on`() {
        val style = parse("res/values/styles.xml").all("style").firstOrNull { it.getAttribute("name") == "AppTheme.Projection" }
        assertNotNull("theme missing", style)
        val item = style!!.all("item").firstOrNull { it.getAttribute("name") == "android:windowAnimationStyle" }
        assertNull("windowAnimationStyle overridden", item)
    }

    @Test
    fun `both projection screens use it`() {
        val activities = parse("AndroidManifest.xml").all("activity")
        for (name in listOf("com.andrerinas.openheadunit.aap.AapProjectionActivity", "com.allgo.app.androidauto.ProjectionActivity")) {
            val a = activities.firstOrNull { it.getAttribute("android:name") == name }
            assertNotNull("$name missing", a)
            assertEquals("@style/AppTheme.Projection", a!!.getAttribute("android:theme"))
        }
    }
}
