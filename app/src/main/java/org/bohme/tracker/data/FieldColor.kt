package org.bohme.tracker.data

fun parseColorHex(text: String): Int? {
    if (text.length != 7 && text.length != 9) return null
    if (text[0] != '#') return null
    val hex = text.substring(1)
    for (c in hex) {
        val ok = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
        if (!ok) return null
    }
    val value = hex.toLong(16)
    return if (hex.length == 6) {
        (0xFF000000L or value).toInt()
    } else {
        value.toInt()
    }
}

fun formatColorHex(argb: Int): String {
    val rgb = argb and 0x00FFFFFF
    return "#%06X".format(rgb)
}

fun lightenArgb(argb: Int, t: Float = 0.45f): Int {
    val a = (argb ushr 24) and 0xFF
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    fun lerp(c: Int): Int = (c + (255 - c) * t).toInt().coerceIn(0, 255)
    return (a shl 24) or (lerp(r) shl 16) or (lerp(g) shl 8) or lerp(b)
}
