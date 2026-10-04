package com.evsuite.hardware.saic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class NavigationHandoffTest {

    @Test fun `a destination becomes a geo uri both readings agree on`() {
        assertEquals(
            "geo:45.764000,4.835700?q=45.764000,4.835700",
            NavigationHandoff.geoUri(45.7640, 4.8357),
        )
    }

    @Test fun `a label travels in the q parameter`() {
        assertEquals(
            "geo:46.306900,4.828300?q=46.306900,4.828300(Ionity%20Exemple%20Est)",
            NavigationHandoff.geoUri(46.3069, 4.8283, "Ionity Exemple Est"),
        )
    }

    @Test fun `a french locale does not turn the decimal point into a field separator`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
        try {
            assertEquals(
                "geo:45.764000,4.835700?q=45.764000,4.835700",
                NavigationHandoff.geoUri(45.7640, 4.8357),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `a label cannot end itself or add a parameter`() {
        assertEquals(
            "geo:46.306900,4.828300?q=46.306900,4.828300(Total%20Energies%20A6)",
            NavigationHandoff.geoUri(46.3069, 4.8283, "Total (Energies) &A6"),
        )
    }

    @Test fun `an accented place name keeps its accents`() {
        // Carried literally rather than percent-encoded. Android's Uri is lenient about this and
        // a French place name stripped of its accents is a different name on a map.
        assertEquals(
            "geo:46.306900,4.828300?q=46.306900,4.828300(Mâcon)",
            NavigationHandoff.geoUri(46.3069, 4.8283, "Mâcon"),
        )
    }

    @Test fun `a label of nothing but punctuation is no label at all`() {
        assertEquals(
            "geo:46.306900,4.828300?q=46.306900,4.828300",
            NavigationHandoff.geoUri(46.3069, 4.8283, "()&"),
        )
    }

    @Test fun `coordinates that are not a place on earth are refused`() {
        // The common swap: a French longitude read as a latitude stays in range, but a latitude
        // read as a longitude does not always, and neither does anything from a parse failure.
        assertNull(NavigationHandoff.geoUri(191.0, 3.0))
        assertNull(NavigationHandoff.geoUri(43.0, 210.0))
        assertNull(NavigationHandoff.geoUri(Double.NaN, 3.0))
        assertNull(NavigationHandoff.geoUri(43.0, Double.POSITIVE_INFINITY))
    }

    @Test fun `the poles and the meridian are places`() {
        assertEquals("geo:-90.000000,-180.000000?q=-90.000000,-180.000000", NavigationHandoff.geoUri(-90.0, -180.0))
        assertEquals("geo:0.000000,0.000000?q=0.000000,0.000000", NavigationHandoff.geoUri(0.0, 0.0))
    }

    @Test fun `a point keeps its name as the driver reads it, not as a uri would`() {
        // The URI form replaces the parenthesis and escapes the space; the adapter form is a
        // parcel field and must not, or the map shows a name nobody wrote.
        assertEquals(
            NavigationHandoff.Poi(46.3069, 4.8283, "Ionity Exemple Est (A6)"),
            NavigationHandoff.poi(46.3069, 4.8283, "Ionity Exemple Est (A6)"),
        )
    }

    @Test fun `a name that is only whitespace falls back to the point itself`() {
        assertEquals("46.306900,4.828300", NavigationHandoff.poi(46.3069, 4.8283, "   ")?.name)
        assertEquals("46.306900,4.828300", NavigationHandoff.poi(46.3069, 4.8283)?.name)
    }

    @Test fun `control characters and doubled spaces cannot travel in a name`() {
        assertEquals("A B", NavigationHandoff.poi(43.0, 3.0, "A\u0000 \n B")?.name)
    }

    @Test fun `a point that is not on Earth is refused, exactly as the uri form refuses it`() {
        assertNull(NavigationHandoff.poi(91.0, 3.0, "north of the pole"))
        assertNull(NavigationHandoff.poi(43.0, 181.0, "past the date line"))
        assertNull(NavigationHandoff.poi(Double.NaN, 3.0, "nowhere"))
    }

    @Test fun `a name longer than a label is cut before it becomes a payload`() {
        val long = "x".repeat(200)
        assertEquals(48, NavigationHandoff.poi(43.0, 3.0, long)?.name?.length)
    }

    @Test fun `the pathway is capped rather than refused`() {
        val stops = List(20) { NavigationHandoff.Poi(43.0, 3.0, "stop $it") }
        assertEquals(NavigationHandoff.MAX_PATHWAY_POINTS, NavigationHandoff.pathway(stops).size)
        // The cap keeps the first stops: a plan is in the order it is driven.
        assertEquals("stop 0", NavigationHandoff.pathway(stops).first().name)
    }
}
