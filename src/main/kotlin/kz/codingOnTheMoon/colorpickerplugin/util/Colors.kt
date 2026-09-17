package kz.codingOnTheMoon.colorpickerplugin.util

data class Colors(val alpha: Int, val red: Int, val green: Int, val blue: Int) {
    fun toHex(): String =
    "%02X%02X%02X%02X".format(alpha, red, green, blue)

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
                else -> throw IllegalArgumentException("Expected 6 or 8 digit hex color, got: $value")
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