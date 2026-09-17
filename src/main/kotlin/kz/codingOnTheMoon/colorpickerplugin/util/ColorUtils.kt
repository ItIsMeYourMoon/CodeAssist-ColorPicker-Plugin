package kz.codingOnTheMoon.colorpickerplugin.util

object ColorUtils {

    private val hexPattern = Regex(
        """(?:#|0x)[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?""",
        RegexOption.IGNORE_CASE
    )

    fun findColors(text: String): List<Pair<Int, Colors>> {
        return hexPattern
        .findAll(text)
        .mapNotNull { match ->
            parseColor(match.value)?.let { color ->
                match.range.first to color
            }
        }
        .toList()
    }

    fun findColorUnderCaret(
        text: String,
        offset: Int
    ): ColorLiteral? {
        for (match in hexPattern.findAll(text)) {
            if (offset in match.range) {
                return ColorLiteral(
                    start = match.range.first,
                    end = match.range.last + 1,
                    color = match.value
                )
            }
        }

        return null
    }

    fun parseColor(value: String): Colors? {
        return try {
            val clean = value
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")

            when (clean.length) {
                6 -> Colors(
                    alpha = 255,
                    red = clean.substring(0, 2).toInt(16),
                    green = clean.substring(2, 4).toInt(16),
                    blue = clean.substring(4, 6).toInt(16)
                )

                8 -> Colors(
                    alpha = clean.substring(0, 2).toInt(16),
                    red = clean.substring(2, 4).toInt(16),
                    green = clean.substring(4, 6).toInt(16),
                    blue = clean.substring(6, 8).toInt(16)
                )

                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun formatColor(
        original: String,
        color: Colors,
        shouldPreserveOGFormat: Boolean = true
    ): String {
        val hex = color.toHex()

        if (!shouldPreserveOGFormat) {
            return when {
                original.startsWith("#") -> "#$hex"
                original.startsWith("0x", ignoreCase = true) -> "0x$hex"
                else -> hex
            }
        }
        return when {
            original.startsWith("#") && original.length == 7 -> {
                "#${hex.substring(2)}"  // 6-digit
            }
            original.startsWith("#") && original.length == 9 -> {
                "#$hex"  // 8-digit
            }
            original.startsWith("0x", ignoreCase = true) && original.length == 8 -> {
                "0x${hex.substring(2)}"  // 6-digit 0x format
            }
            original.startsWith("0x", ignoreCase = true) && original.length == 10 -> {
                "0x$hex"  // 8-digit 0x format
            }
            else -> "#$hex"
        }
    }

    data class ColorLiteral(
        val start: Int,
        val end: Int,
        val color: String
    )
}
