package kz.codingOnTheMoon.colorpickerplugin.util

import android.util.Log

data class Colors(val alpha: Int, val red: Int, val green: Int, val blue: Int) {
    fun toHex(): String = toHex(digitCount = 8)  // Default: 8-digit AARRGGBB
    
    fun toHex(digitCount: Int): String {
        return when {
            digitCount <= 0 -> ""
            digitCount <= 4 -> {
                val aHex = "%X".format(alpha / 17)
                val rHex = "%X".format(red / 17)
                val gHex = "%X".format(green / 17)
                val bHex = "%X".format(blue / 17)
                listOf(aHex, rHex, gHex, bHex).take(digitCount).joinToString("")
            }
            else -> { 
                val fullHex = "%02X%02X%02X%02X".format(alpha, red, green, blue)
                fullHex.takeLast(digitCount.coerceAtMost(8))  // Take rightmost N digits (max 8)
            }
        }
    }

    fun toArgb(): String =
    "0x${ "%02X%02X%02X%02X".format(alpha, red, green, blue)}"

    companion object {

        fun fromArgb(value: String): Colors {
            val clean = value
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")

            return when (clean.length) {
                6 -> {
                    Colors(
                        alpha = 0xFF,
                        red = clean.substring(0, 2).toInt(16),
                        green = clean.substring(2, 4).toInt(16),
                        blue = clean.substring(4, 6).toInt(16)
                    )
                }
                8 -> {
                    Colors(
                        alpha = clean.substring(0, 2).toInt(16),
                        red = clean.substring(2, 4).toInt(16),
                        green = clean.substring(4, 6).toInt(16),
                        blue = clean.substring(6, 8).toInt(16)
                    )
                }
                else -> {
                   Log.e(
                        "Colors.kt",
                        "Expected 6 or 8 digit hex color, got: $value"
                    )
                    throw IllegalArgumentException("Invalid color: $value") 
                }
            }
        }
    }
}

data class ColorState(
    val alpha: Float,
    val red: Float,
    val green: Float,
    val blue: Float
) {
    fun toColors() = Colors(alpha.toInt(), red.toInt(), green.toInt(), blue.toInt())
    fun toHex() = toColors().toHex()
}