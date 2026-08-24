package org.bohme.tracker.stats

import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import org.bohme.tracker.data.FieldDef

fun parseLocaleNumber(text: String, locale: Locale): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val format = NumberFormat.getNumberInstance(locale)
    val pos = ParsePosition(0)
    val number = format.parse(trimmed, pos) ?: return null
    // Prefix-only parse would accept "180kg"; the field must be the whole number.
    if (pos.index != trimmed.length) return null
    val value = number.toDouble()
    if (!value.isFinite()) return null
    return value
}

fun parseRequiredFields(
    fields: List<FieldDef>,
    texts: Map<String, String>,
    locale: Locale,
): Map<String, Double>? {
    val out = LinkedHashMap<String, Double>(fields.size)
    for (field in fields) {
        val value = parseLocaleNumber(texts[field.id].orEmpty(), locale) ?: return null
        out[field.id] = value
    }
    return out
}

fun formatLocaleNumber(value: Double, locale: Locale): String {
    return NumberFormat.getNumberInstance(locale).format(value)
}
