package kz.codingOnTheMoon.colorpickerplugin.actions

import android.util.Log
import dev.ide.platform.log.Logger
import dev.ide.plugin.action.ActionContext
import dev.ide.plugin.action.ActionEffect
import dev.ide.plugin.action.ActionPlace
import dev.ide.plugin.action.ActionPlaces
import dev.ide.plugin.action.ActionResult
import dev.ide.plugin.action.IdeAction
import dev.ide.plugin.action.TextEdit
import kz.codingOnTheMoon.colorpickerplugin.ColorPickerPlugin
import kz.codingOnTheMoon.colorpickerplugin.util.ColorUtils
import kz.codingOnTheMoon.colorpickerplugin.util.Colors

class ColorPickerAction(val logger: Logger) : IdeAction {

    private val TAG = "ColorPickerAction"

    override val id: String = "kz.codingOnTheMoon.colorpickerplugin.color-picker.open"

    override val iconId: String? = "sparkle"

    override val text: String = "Open Color Picker"

    override val places: Set<ActionPlace> = setOf(ActionPlaces.EDITOR)

    override fun isVisible(ctx: ActionContext): Boolean {
        val filePath = ctx.activeFilePath ?: return false
        val caretOffset = ctx.caret?.offset ?: return false

 
        return ColorPickerPlugin
        .getColorLiterals(filePath)
        .any { literal ->
            caretOffset in literal.start until literal.end
        }
    }

    override suspend fun perform(ctx: ActionContext): ActionResult {
        Log.d(TAG, "1. preform() Started")

        val caret = ctx.caret ?: return ActionResult.message("No editor caret")
        Log.d(TAG, "2. got caret: $caret")

        val documentText = ctx.documentText ?: return ActionResult.message("No editor document")
        Log.d(TAG, "3. Got docText, length=${documentText.length}")

        val filePath = ctx.activeFilePath ?: return ActionResult.message("No active file")
        Log.d(TAG, "4. Got filePath: $filePath")

        Log.d(TAG, "5. Finding color at offset ${caret.offset}")
        val colorLiteral = ColorUtils.findColorUnderCaret(
            text = documentText,
            offset = caret.offset
        ) ?: return ActionResult.message(
            "No color literal under caret"
        )
        Log.d(TAG, "6. Found color $colorLiteral")
        Log.d(TAG, "Color: ${colorLiteral.color}")

        Log.d(TAG, "7. Requesting picker for ${colorLiteral.color}")
        ColorPickerPlugin.requestPicker(
            offset = caret.offset,
            currentColor = colorLiteral.color,
            filePath = filePath
        )

        Log.d(TAG, "8. About to wait for picker result...")
        val result = ColorPickerPlugin.awaitPickerResult()
        val (pickedColor, shouldPreserveFormat) = result ?: (null to true)
        Log.d(TAG, "9. Picked reuslt: $pickedColor")

        if (pickedColor == null) {
            return ActionResult.message("Cancelled")
        }

        Log.d(TAG, "10. formatting color....")
        val newColor = ColorUtils.formatColor(
            original = colorLiteral.color,
            color = pickedColor,
            shouldPreserveOGFormat = shouldPreserveFormat
        )
        Log.d(TAG, "11. Formatted color: $newColor")

        val textEdit = TextEdit.replace(
            start = colorLiteral.start,
            end = colorLiteral.end,
            text = newColor
        )

        Log.d(TAG, "Returning effect ")
        return ActionResult.effect(ActionEffect.ApplyEdits(textEdit))
    }
}
