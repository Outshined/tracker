package org.bohme.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuiltInMetricsTest {
    private val byId = BuiltInMetrics.ALL.associateBy { it.id }

    @Test
    fun `ids weight bhb glucose blood_pressure`() {
        assertEquals(listOf("weight", "bhb", "glucose", "blood_pressure"), BuiltInMetrics.ALL.map { it.id })
    }

    @Test
    fun `field counts 1 1 1 3`() {
        assertEquals(1, byId.getValue("weight").fields.size)
        assertEquals(1, byId.getValue("bhb").fields.size)
        assertEquals(1, byId.getValue("glucose").fields.size)
        assertEquals(3, byId.getValue("blood_pressure").fields.size)
    }

    @Test
    fun `units lb mmol_L mg_dL mmHg and bpm`() {
        assertEquals("lb", byId.getValue("weight").fields.single().unit)
        assertEquals("mmol/L", byId.getValue("bhb").fields.single().unit)
        assertEquals("mg/dL", byId.getValue("glucose").fields.single().unit)
        assertEquals(
            listOf("mmHg", "mmHg", "bpm"),
            byId.getValue("blood_pressure").fields.map { it.unit },
        )
    }

    @Test
    fun `BP field ids systolic diastolic pulse`() {
        assertEquals(
            listOf("systolic", "diastolic", "pulse"),
            byId.getValue("blood_pressure").fields.map { it.id },
        )
    }

    @Test
    fun `weight field id is lb not kg`() {
        assertEquals("lb", byId.getValue("weight").fields.single().id)
        assertFalse(byId.getValue("weight").fields.any { it.id == "kg" })
    }
}
