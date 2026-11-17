package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The parts of the MG4Dash agreement that live in the manifest and in when the option is published. */
class Mg4DashContractManifestTest {
    private val manifest by lazy {
        // Unit tests run from the module's folder.
        val file = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.exists() }
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun elements(parent: Element, tag: String): List<Element> =
        (0 until parent.getElementsByTagName(tag).length).map { parent.getElementsByTagName(tag).item(it) as Element }

    @Test
    fun `the disconnect receiver demands the signature permission`() {
        val receiver = elements(manifest, "receiver").firstOrNull { it.getAttribute("android:name") == ".app.UserDisconnectReceiver" }
        assertNotNull("receiver missing", receiver)
        assertEquals(Mg4DashContract.PERMISSION_USER_DISCONNECT, receiver!!.getAttribute("android:permission"))
        assertEquals(listOf(Mg4DashContract.ACTION_USER_DISCONNECT), elements(receiver, "action").map { it.getAttribute("android:name") })
    }

    @Test
    fun `the permission is declared with signature protection`() {
        val permission = elements(manifest, "permission").firstOrNull { it.getAttribute("android:name") == Mg4DashContract.PERMISSION_USER_DISCONNECT }
        assertNotNull("permission not declared", permission)
        assertEquals("signature", permission!!.getAttribute("android:protectionLevel"))
    }

    @Test
    fun `changing the option publishes the state when no screen is open`() {
        assertEquals(1, Mg4DashContract.valueToPublishOnChange(enabled = true, openScreens = 0))
        assertEquals(0, Mg4DashContract.valueToPublishOnChange(enabled = false, openScreens = 0))
    }

    @Test
    fun `with a screen open the new value waits for the next opening`() {
        assertNull(Mg4DashContract.valueToPublishOnChange(enabled = true, openScreens = 1))
        assertNull(Mg4DashContract.valueToPublishOnChange(enabled = false, openScreens = 2))
    }
}
