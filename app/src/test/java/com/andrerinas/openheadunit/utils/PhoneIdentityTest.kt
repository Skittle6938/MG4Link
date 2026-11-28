package com.andrerinas.openheadunit.utils

import com.google.protobuf.ByteString
import com.google.protobuf.UnknownFieldSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #59: which paired phone is on the projection when it only says "Android". */
class PhoneIdentityTest {

    private val paired = listOf("AA:AA:AA:AA:AA:01" to "Pixel 7", "AA:AA:AA:AA:AA:02" to "Galaxy S23")

    private fun request(instanceId: String?): UnknownFieldSet {
        if (instanceId == null) return UnknownFieldSet.getDefaultInstance()
        val info = UnknownFieldSet.newBuilder()
            .addField(1, UnknownFieldSet.Field.newBuilder().addLengthDelimited(ByteString.copyFromUtf8(instanceId)).build())
            .build()
        return UnknownFieldSet.newBuilder()
            .addField(6, UnknownFieldSet.Field.newBuilder().addLengthDelimited(info.toByteString()).build())
            .build()
    }

    @Test fun `the instance id is read from the phone info`() {
        assertEquals("abc-123", PhoneIdentity.instanceId(request("abc-123")))
        assertNull(PhoneIdentity.instanceId(request(null)))
    }

    @Test fun `label first, then device name, then the remembered phone`() {
        assertEquals("AA:AA:AA:AA:AA:01" to "label", PhoneIdentity.resolve(paired, "Pixel 7", "Galaxy S23", null))
        assertEquals("AA:AA:AA:AA:AA:02" to "device name", PhoneIdentity.resolve(paired, "Android", "Galaxy S23", null))
        assertEquals("AA:AA:AA:AA:AA:01" to "remembered", PhoneIdentity.resolve(paired, "Android", "Android", "aa:aa:aa:aa:aa:01"))
    }

    @Test fun `nothing guessed with only generic names, nor a remembered phone no longer paired`() {
        assertNull(PhoneIdentity.resolve(paired, "Android", null, null))
        assertNull(PhoneIdentity.resolve(paired, "Android", "Android", "BB:BB:BB:BB:BB:BB"))
    }

    @Test fun `the car's own first links are not learned`() {
        assertFalse(PhoneIdentity.shouldLearn("abc", 5_000))
        assertTrue(PhoneIdentity.shouldLearn("abc", 45_000))
        assertFalse(PhoneIdentity.shouldLearn(null, 45_000))
    }
}
