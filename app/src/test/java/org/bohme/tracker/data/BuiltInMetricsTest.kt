package org.bohme.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `built-in field colors are null`() {
        assertTrue(BuiltInMetrics.ALL.all { metric -> metric.fields.all { it.color == null } })
    }

    @Test
    fun `seed graph ranges match constants`() {
        assertEquals(BuiltInMetrics.WEIGHT_GRAPH_MIN, byId.getValue("weight").graphMin!!, 0.0)
        assertEquals(BuiltInMetrics.WEIGHT_GRAPH_MAX, byId.getValue("weight").graphMax!!, 0.0)
        assertEquals(BuiltInMetrics.BHB_GRAPH_MIN, byId.getValue("bhb").graphMin!!, 0.0)
        assertEquals(BuiltInMetrics.BHB_GRAPH_MAX, byId.getValue("bhb").graphMax!!, 0.0)
        assertEquals(BuiltInMetrics.GLUCOSE_GRAPH_MIN, byId.getValue("glucose").graphMin!!, 0.0)
        assertEquals(BuiltInMetrics.GLUCOSE_GRAPH_MAX, byId.getValue("glucose").graphMax!!, 0.0)
        assertEquals(BuiltInMetrics.BLOOD_PRESSURE_GRAPH_MIN, byId.getValue("blood_pressure").graphMin!!, 0.0)
        assertEquals(BuiltInMetrics.BLOOD_PRESSURE_GRAPH_MAX, byId.getValue("blood_pressure").graphMax!!, 0.0)
    }
}
