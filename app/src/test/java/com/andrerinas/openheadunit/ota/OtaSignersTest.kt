package com.andrerinas.openheadunit.ota

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaSignersTest {
    private val a = byteArrayOf(1, 2, 3)
    private val b = byteArrayOf(4, 5, 6)

    @Test fun sameCertificateIsAccepted() = assertTrue(OtaInstaller.sameSigners(listOf(a), listOf(a.copyOf())))
    @Test fun orderDoesNotMatter() = assertTrue(OtaInstaller.sameSigners(listOf(a, b), listOf(b, a)))
    @Test fun otherCertificateIsRefused() = assertFalse(OtaInstaller.sameSigners(listOf(a), listOf(b)))
    @Test fun extraSignerIsRefused() = assertFalse(OtaInstaller.sameSigners(listOf(a), listOf(a, b)))
    @Test fun unsignedIsRefused() = assertFalse(OtaInstaller.sameSigners(emptyList(), emptyList()))
}
