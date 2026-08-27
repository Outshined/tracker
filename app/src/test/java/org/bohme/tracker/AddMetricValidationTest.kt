package org.bohme.tracker

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AddMetricValidationTest {
    private val us = Locale.US
    private val weightFields = listOf(FieldForm(label = "Weight", unit = "lb"))

    @Test
    fun `blank label rejected`() {
        assertNotNull(validateMetricForm("", weightFields, "0", "100", us))
        assertNotNull(validateMetricForm("   ", weightFields, "0", "100", us))
    }

    @Test
    fun `zero fields rejected`() {
        assertNotNull(validateMetricForm("Weight", emptyList(), "0", "100", us))
    }

    @Test
    fun `blank field label rejected`() {
        assertNotNull(validateMetricForm("Weight", listOf(FieldForm(label = "", unit = "lb")), "0", "100", us))
        assertNotNull(validateMetricForm("Weight", listOf(FieldForm(label = "  ", unit = "lb")), "0", "100", us))
    }

    @Test
    fun `valid label and field accepted including blank unit`() {
        assertNull(validateMetricForm("Weight", weightFields, "0", "100", us))
        assertNull(validateMetricForm("Custom", listOf(FieldForm(label = "A", unit = "")), "0", "100", us))
        assertNull(
            validateMetricForm(
                "BP",
                listOf(
                    FieldForm(label = "Sys", unit = "mmHg"),
                    FieldForm(label = "Dia", unit = "mmHg"),
                ),
                "40",
                "200",
                us,
            ),
        )
    }

    @Test
    fun `graph min and max are required`() {
        assertEquals(
            "Graph min and max are required.",
            validateMetricForm("BHB", listOf(FieldForm(label = "BHB", unit = "mmol/L")), "", "5", us),
        )
        assertEquals(
            "Graph min and max are required.",
            validateMetricForm("BHB", listOf(FieldForm(label = "BHB", unit = "mmol/L")), "0", "", us),
        )
        assertEquals(
            "Graph min and max are required.",
            validateMetricForm("BHB", listOf(FieldForm(label = "BHB", unit = "mmol/L")), "  ", "5", us),
        )
    }

    @Test
    fun `graph min and max must be numbers`() {
        val fields = listOf(FieldForm(label = "BHB", unit = "mmol/L"))
        assertEquals(
            "Graph min and max must be numbers.",
            validateMetricForm("BHB", fields, "x", "5", us),
        )
        assertEquals(
            "Graph min and max must be numbers.",
            validateMetricForm("BHB", fields, "0", "nope", us),
        )
    }

    @Test
    fun `graph min must be less than max`() {
        val fields = listOf(FieldForm(label = "BHB", unit = "mmol/L"))
        assertEquals(
            "Graph min must be less than max.",
            validateMetricForm("BHB", fields, "5", "5", us),
        )
        assertEquals(
            "Graph min must be less than max.",
            validateMetricForm("BHB", fields, "5", "0", us),
        )
    }

    @Test
    fun `valid 0 and 5 for BHB`() {
        assertNull(
            validateMetricForm(
                "BHB",
                listOf(FieldForm(label = "BHB", unit = "mmol/L")),
                "0",
                "5",
                us,
            ),
        )
    }
}
