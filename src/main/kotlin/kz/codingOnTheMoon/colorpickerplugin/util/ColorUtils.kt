package kz.codingOnTheMoon.colorpickerplugin.util

import kotlin.math.roundToInt

object ColorUtils {

    private val hexPattern = Regex(
        """(?:#|0x)[0-9A-Fa-f]{3,8}(?![0-9A-Fa-f])""",
        RegexOption.IGNORE_CASE
    )

    private val rgbPattern = Regex(
        """(?<![\w.])(?:(?:android\.graphics\.|androidx\.compose\.ui\.graphics\.)?Color)\.rgb\s*\(([^()]*)\)"""
    )

    private val argbPattern = Regex(
        """(?<![\w.])(?:(?:android\.graphics\.|androidx\.compose\.ui\.graphics\.)?Color)\.argb\s*\(([^()]*)\)"""
    )

    private val composeColorPattern = Regex(
        """(?<![\w.])(?:androidx\.compose\.ui\.graphics\.)?Color\s*\(([^()]*)\)"""
    )

    private val composeColorMethodPattern = Regex(
        """(?<![\w.])(?:androidx\.compose\.ui\.graphics\.)?Color\.(hsl|hsv)\s*\(([^()]*)\)"""
    )

    fun findColors(text: String): List<Pair<Int, Colors>> {
        return findColorLiterals(text)
            .mapNotNull { literal ->
                parseColorExpression(literal.color)?.let { color ->
                    literal.start to color
                }
            }
    }

    fun findColorUnderCaret(text: String, offset: Int): ColorLiteral? {
        return findColorLiterals(text).firstOrNull { offset in it.start until it.end }
    }

    fun findColorLiterals(text: String): List<ColorLiteral> {
        val matches = mutableListOf<ColorLiteral>()

        hexPattern.findAll(text).forEach { match ->
            if (match.range.first > 0 && text[match.range.first - 1].isLetterOrDigit()) return@forEach
            val clean = match.value.removePrefix("#").removePrefix("0x").removePrefix("0X")
            if (clean.length !in setOf(3, 4, 6, 8)) return@forEach
            matches += ColorLiteral(match.range.first, match.range.last + 1, match.value)
        }

        listOf(
            rgbPattern to 3,
            argbPattern to 4,
            composeColorPattern to null
        ).forEach { (pattern, expectedSize) ->
            pattern.findAll(text).forEach { match ->
                val args = match.groupValues.getOrNull(1) ?: return@forEach
                if (parseColorFromArgs(args, expectedSize) != null) {
                    matches += ColorLiteral(match.range.first, match.range.last + 1, match.value)
                }
            }
        }

        composeColorMethodPattern.findAll(text).forEach { match ->
            val method = match.groupValues.getOrNull(1) ?: return@forEach  // hsl or hsv
            val args = match.groupValues.getOrNull(2) ?: return@forEach    // arguments
            if (parseComposeColorMethodArgs(method, args) != null) {
                matches += ColorLiteral(match.range.first, match.range.last + 1, match.value)
            }
        }

        val uniqueMatches = matches.distinctBy { it.start to it.end }
            .sortedWith(compareBy<ColorLiteral> { it.start }.thenByDescending { it.end })
            
        return uniqueMatches.filterIndexed { idx, candidate ->
            uniqueMatches.subList(0, idx).none { other ->
                other.end > candidate.end  // Earlier match overlaps, skip candidate
            }
        }
    }

    fun parseColor(value: String): Colors? = parseColorExpression(value)

    private fun parseColorExpression(value: String): Colors? {
        val trimmed = value.trim()
        return parseHex(trimmed)
            ?: parsePatternMatch(rgbPattern, trimmed) { args -> parseColorArgs(args, 3) }
            ?: parsePatternMatch(argbPattern, trimmed) { args -> parseColorArgs(args, 4) }
            ?: parsePatternMatch(composeColorPattern, trimmed) { args -> parseComposeColorArgs(args) }
            ?: parsePatternMatch(composeColorMethodPattern, trimmed) { args, method ->
                parseComposeColorMethodArgs(args, method)
            }
    }

    private inline fun <T> parsePatternMatch(
        pattern: Regex,
        value: String,
        handler: (String) -> T?
    ): T? {
        val match = pattern.matchEntire(value) ?: return null
        return handler(match.groupValues.getOrNull(1) ?: return null)
    }

    private inline fun <T> parsePatternMatch(
        pattern: Regex,
        value: String,
        handler: (String, String) -> T?
    ): T? {
        val match = pattern.matchEntire(value) ?: return null
        val group1 = match.groupValues.getOrNull(1) ?: return null
        val group2 = match.groupValues.getOrNull(2) ?: return null
        return handler(group1, group2)
    }

    private fun parseColorFromArgs(arguments: String, expectedSize: Int?): Colors? {
        return when (expectedSize) {
            3 -> parseColorArgs(arguments, 3)
            4 -> parseColorArgs(arguments, 4)
            else -> parseComposeColorArgs(arguments)
        }
    }
    
    private fun parseColorArgs(arguments: String, componentCount: Int): Colors? {
        val parts = arguments.split(',').map { it.trim() }
        if (parts.size != componentCount) return null

        val values = parts.map { part ->
            part.toIntOrNull()?.takeIf { it in 0..255 }
                ?: part.removeSuffix("f").removeSuffix("F").toFloatOrNull()
                    ?.takeIf { it in 0f..1f }?.let { (it * 255f).roundToInt() }
        }

        if (values.any { it == null }) return null

        return when (componentCount) {
            3 -> Colors(255, values[0]!!, values[1]!!, values[2]!!)
            4 -> Colors(values[0]!!, values[1]!!, values[2]!!, values[3]!!)
            else -> null
        }
    }

    private fun parseComposeColorArgs(arguments: String): Colors? {
        val trimmed = arguments.trim()

        parseComposePackedColor(trimmed)?.let { return it }

        val parts = trimmed.split(',').map { it.trim() }
        if (parts.size !in 3..4) return null

        val named = parts.mapNotNull { part ->
            val sep = part.indexOf('=')
            if (sep < 0) null else part.substring(0, sep).trim() to part.substring(sep + 1).trim()
        }

        return if (named.size == parts.size && named.isNotEmpty()) {
            val map = named.toMap()
            val red = parseFloatComponent(map["red"] ?: return null) ?: return null
            val green = parseFloatComponent(map["green"] ?: return null) ?: return null
            val blue = parseFloatComponent(map["blue"] ?: return null) ?: return null
            val alpha = parseFloatComponent(map["alpha"] ?: "1f") ?: return null
            Colors(alpha, red, green, blue)
        } else {
            // Positional: Color(red, green, blue [, alpha])
            val values = parts.map { parseFloatComponent(it) }
            if (values.any { it == null }) return null
            Colors(values.getOrNull(3) ?: 255, values[0]!!, values[1]!!, values[2]!!)
        }
    }

    private fun parseComposeColorMethodArgs(method: String, arguments: String): Colors? {
        val parts = arguments.split(',').map { it.trim() }
        if (parts.size !in 3..4) return null

        val named = parts.mapNotNull { part ->
            val sep = part.indexOf('=')
            if (sep < 0) null else part.substring(0, sep).trim() to part.substring(sep + 1).trim()
        }

        val values = if (named.size == parts.size && named.isNotEmpty()) {
            val map = named.toMap()
            listOf(
                map["hue"], map["saturation"],
                if (method == "hsl") map["lightness"] else map["value"],
                map["alpha"] ?: "1f"
            )
        } else {
            if (parts.size == 3) parts + "1f" else parts
        }

        val hue = parseFloat(values[0] ?: return null) ?: return null
        val sat = parseUnitFloat(values[1] ?: return null) ?: return null
        val third = parseUnitFloat(values[2] ?: return null) ?: return null
        val alpha = parseUnitFloat(values[3] ?: return null) ?: return null

        return if (method == "hsl") hslToColor(hue, sat, third, alpha)
        else hsvToColor(hue, sat, third, alpha)
    }

    private fun parseHex(value: String): Colors? {
        val clean = value.removePrefix("#").removePrefix("0x").removePrefix("0X")
        return try {
            when (clean.length) {
                3 -> Colors(255, "${clean[0]}${clean[0]}".toInt(16), "${clean[1]}${clean[1]}".toInt(16), "${clean[2]}${clean[2]}".toInt(16))
                4 -> Colors("${clean[0]}${clean[0]}".toInt(16), "${clean[1]}${clean[1]}".toInt(16), "${clean[2]}${clean[2]}".toInt(16), "${clean[3]}${clean[3]}".toInt(16))
                6 -> Colors(255, clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16))
                8 -> Colors(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16), clean.substring(6, 8).toInt(16))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseComposePackedColor(value: String): Colors? {
        val clean = value.removeSuffix("L").removeSuffix("l")
        if (!clean.startsWith("0x", ignoreCase = true)) return null
        val hex = clean.substring(2)
        if (hex.length != 8 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return parseHex(clean)
    }

    private fun parseFloat(value: String): Float? = value.removeSuffix("f").removeSuffix("F").toFloatOrNull()
    private fun parseUnitFloat(value: String): Float? = parseFloat(value)?.takeIf { it in 0f..1f }
    private fun parseFloatComponent(value: String): Int? = parseUnitFloat(value)?.let { (it * 255f).roundToInt() }

    private fun hslToColor(hue: Float, saturation: Float, lightness: Float, alpha: Float): Colors {
        val h = ((hue % 360f) + 360f) % 360f / 360f
        if (saturation == 0f) {
            val value = (lightness * 255f).roundToInt()
            return Colors((alpha * 255f).roundToInt(), value, value, value)
        }

        val q = if (lightness < 0.5f) lightness * (1f + saturation) else lightness + saturation - lightness * saturation
        val p = 2f * lightness - q
        val r = hueToRgb(p, q, h + 1f / 3f)
        val g = hueToRgb(p, q, h)
        val b = hueToRgb(p, q, h - 1f / 3f)

        return Colors((alpha * 255f).roundToInt(), (r * 255f).roundToInt(), (g * 255f).roundToInt(), (b * 255f).roundToInt())
    }

    private fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Float): Colors {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val c = value * saturation
        val x = c * (1f - kotlin.math.abs((h % 2f) - 1f))
        val m = value - c

        val (r, g, b) = when {
            h in 0f..1f -> Triple(c, x, 0f)
            h in 1f..2f -> Triple(x, c, 0f)
            h in 2f..3f -> Triple(0f, c, x)
            h in 3f..4f -> Triple(0f, x, c)
            h in 4f..5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Colors((alpha * 255f).roundToInt(), ((r + m) * 255f).roundToInt(), ((g + m) * 255f).roundToInt(), ((b + m) * 255f).roundToInt())
    }

    private fun hueToRgb(p: Float, q: Float, t: Float): Float {
        val th = t.let { if (it < 0f) it + 1f else if (it > 1f) it - 1f else it }
        return when {
            th < 1f / 6f -> p + (q - p) * 6f * th
            th < 1f / 2f -> q
            th < 2f / 3f -> p + (q - p) * (2f / 3f - th) * 6f
            else -> p
        }
    }

    private fun rgbToHsl(color: Colors): FloatArray {
        val r = color.red / 255f
        val g = color.green / 255f
        val b = color.blue / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val lightness = (max + min) / 2f

        if (delta == 0f) return floatArrayOf(0f, 0f, lightness)

        val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        val hue = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        return floatArrayOf(hue, saturation, lightness)
    }

    private fun rgbToHsv(color: Colors): FloatArray {
        val r = color.red / 255f
        val g = color.green / 255f
        val b = color.blue / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val hue = if (delta == 0f) 0f else when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val saturation = if (max == 0f) 0f else delta / max
        return floatArrayOf(hue, saturation, max)
    }

    fun formatColor(original: String, color: Colors, shouldPreserveOGFormat: Boolean = true): String {
        return when {
            original.contains("argb", ignoreCase = true) -> formatColorCall(original, color, "argb", 4)
            original.contains("rgb", ignoreCase = true) -> formatColorCall(original, color, "rgb", 3)
            original.contains("hsl", ignoreCase = true) || original.contains("hsv", ignoreCase = true) ->
                formatColorMethod(original, color)
            original.contains("Color(") -> formatComposeColor(original, color)
            shouldPreserveOGFormat && original.startsWith("#", ignoreCase = false) -> "#${color.toHex()}"
            shouldPreserveOGFormat && original.startsWith("0x", ignoreCase = true) -> "0x${color.toHex()}"
            else -> "#${color.toHex()}"
        }
    }

 
    private fun formatColorCall(original: String, color: Colors, method: String, componentCount: Int): String {
        val args = original.substringAfter("$method(").substringBeforeLast(")")
        val parts = args.split(',').map { it.trim() }
        val isFloat = parts.size == componentCount && parts.all { isFloatColorComponent(it) }

        val components = when (method) {
            "rgb" -> listOf(color.red, color.green, color.blue)
            "argb" -> listOf(color.alpha, color.red, color.green, color.blue)
            else -> return original
        }

        return if (isFloat) {
            buildString {
                append(original.substringBefore("Color.$method"))
                append("Color.$method(")
                components.joinTo(this, ", ") { formatUnitFloat(it / 255f) }
                append(")")
            }
        } else {
            buildString {
                append(original.substringBefore("Color.$method"))
                append("Color.$method(")
                components.joinTo(this, ", ") { it.toString() }
                append(")")
            }
        }
    }

    private fun formatColorMethod(original: String, color: Colors): String {
        val method = if (original.contains("hsl", ignoreCase = true)) "hsl" else "hsv"
        val args = original.substringAfter("$method(").substringBeforeLast(")")
        val parts = args.split(',').map { it.trim() }
        val hasAlpha = parts.size == 4

        val values = if (method == "hsl") rgbToHsl(color) else rgbToHsv(color)

        return buildString {
            append(original.substringBefore("Color.$method"))
            append("Color.$method(")
            append(formatFloatValue(values[0]))
            append(", ")
            append(formatUnitFloat(values[1]))
            append(", ")
            append(formatUnitFloat(values[2]))
            if (hasAlpha) {
                append(", ")
                append(formatUnitFloat(color.alpha / 255f))
            }
            append(")")
        }
    }

    private fun formatComposeColor(original: String, color: Colors): String {
        val args = original.substringAfter("Color(").substringBeforeLast(")")
        val trimmed = args.trim()

        if (parseComposePackedColor(trimmed) != null) {
            val suffix = if (trimmed.endsWith("L", ignoreCase = true)) "L" else ""
            return original.substringBefore("Color(") + "Color(0x${color.toHex()}$suffix)"
        }

        val parts = trimmed.split(',').map { it.trim() }
        val named = parts.mapNotNull { part ->
            val sep = part.indexOf('=')
            if (sep < 0) null else part.substring(0, sep).trim() to part.substring(sep + 1).trim()
        }

        return if (named.size == parts.size && named.isNotEmpty()) {
            val map = named.toMap()
            val hasAlpha = map.containsKey("alpha")
            buildString {
                append(original.substringBefore("Color("))
                append("Color(red = ")
                append(formatUnitFloat(color.red / 255f))
                append(", green = ")
                append(formatUnitFloat(color.green / 255f))
                append(", blue = ")
                append(formatUnitFloat(color.blue / 255f))
                if (hasAlpha) {
                    append(", alpha = ")
                    append(formatUnitFloat(color.alpha / 255f))
                }
                append(")")
            }
        } else {
            original.substringBefore("Color(") + "Color(0x${color.toHex()})"
        }
    }

    private fun isFloatColorComponent(value: String): Boolean = value.contains('.') || value.endsWith('f', ignoreCase = true)
    private fun formatUnitFloat(value: Float): String = "%.6f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.').let {
        if (it == "") "0" else if (it == "1") "1" else it + "f"
    }

    private fun formatFloatValue(value: Float): String = "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.').let {
        if (it == "") "0" else it + "f"
    }

    data class ColorLiteral(val start: Int, val end: Int, val color: String)
}
