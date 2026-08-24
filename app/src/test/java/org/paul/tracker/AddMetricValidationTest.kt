package org.paul.tracker

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AddMetricValidationTest {
    @Test
    fun `blank label rejected`() {
        val fields = listOf(FieldForm(label = "Weight", unit = "lb"))
        assertNotNull(validateMetricForm("", fields))
        assertNotNull(validateMetricForm("   ", fields))
    }

    @Test
    fun `zero fields rejected`() {
        assertNotNull(validateMetricForm("Weight", emptyList()))
    }

    @Test
    fun `blank field label rejected`() {
        assertNotNull(validateMetricForm("Weight", listOf(FieldForm(label = "", unit = "lb"))))
        assertNotNull(validateMetricForm("Weight", listOf(FieldForm(label = "  ", unit = "lb"))))
    }

    @Test
    fun `valid label and field accepted including blank unit`() {
        assertNull(validateMetricForm("Weight", listOf(FieldForm(label = "Weight", unit = "lb"))))
        assertNull(validateMetricForm("Custom", listOf(FieldForm(label = "A", unit = ""))))
        assertNull(
            validateMetricForm(
                "BP",
                listOf(
                    FieldForm(label = "Sys", unit = "mmHg"),
                    FieldForm(label = "Dia", unit = "mmHg"),
                ),
            ),
        )
    }
}
