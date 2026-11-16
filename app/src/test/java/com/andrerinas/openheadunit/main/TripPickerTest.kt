package com.andrerinas.openheadunit.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripPickerTest {

    @Test
    fun `the default is 20 points below the current charge`() {
        assertEquals(32, TripPicker.defaultForecast(52))
    }

    @Test
    fun `the default keeps at least 5 points and stays above zero`() {
        assertEquals(0, TripPicker.defaultForecast(15))
        assertEquals(0, TripPicker.defaultForecast(10))
    }

    @Test
    fun `a forecast less than 5 points below the current charge is refused`() {
        assertEquals(47, TripPicker.maxForecast(52))
        assertEquals(47, TripPicker.step(40, 10, 47))
        assertEquals(41, TripPicker.step(40, 1, 47))
        assertEquals(0, TripPicker.step(5, -10, 47))
    }

    @Test
    fun `below 5 percent nothing can be recorded`() {
        assertNull(TripPicker.maxForecast(4))
        assertNull(TripPicker.defaultForecast(4))
    }
}
