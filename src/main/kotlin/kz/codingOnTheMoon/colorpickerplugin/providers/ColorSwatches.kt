package kz.codingOnTheMoon.colorpickerplugin.providers

import dev.ide.plugin.editor.EditorDecorationContext
import dev.ide.plugin.editor.EditorDecorationProvider
import dev.ide.plugin.editor.EditorDecorations
import dev.ide.plugin.editor.EditorInlay
import kz.codingOnTheMoon.colorpickerplugin.ColorPickerPlugin
import kz.codingOnTheMoon.colorpickerplugin.util.ColorUtils
import kz.codingOnTheMoon.colorpickerplugin.util.Colors

class ColorSwatches: EditorDecorationProvider {

    override val id: String = "kz.codingOnTheMoon.colorpickerplugin.ColorPickerPlugin-color-swatches"

    override fun appliesTo(ctx: EditorDecorationContext): Boolean {
        return ctx.path.endsWith(".kt") ||
               ctx.path.endsWith(".java") ||
               ctx.path.endsWith(".xml")
    }

    override suspend fun decorate(ctx: EditorDecorationContext): EditorDecorations {
        
        val hits = ColorUtils.findColors(ctx.text)
            
        ColorPickerPlugin.putDetectedColors(ctx.path, hits)
        val inlays = hits.map { (offset, _) ->
            EditorInlay(offset, " ")
        }
        
        return EditorDecorations(inlays = inlays)
    }
}
