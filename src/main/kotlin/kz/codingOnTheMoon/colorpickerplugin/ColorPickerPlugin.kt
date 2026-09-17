package kz.codingOnTheMoon.colorpickerplugin

import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.ide.plugin.Plugin
import dev.ide.plugin.PluginRegistration
import dev.ide.plugin.action.UI_ACTION_EP
import dev.ide.plugin.editor.EDITOR_DECORATION_EP
import dev.ide.plugin.ui.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kz.codingOnTheMoon.colorpickerplugin.actions.ColorPickerAction
import kz.codingOnTheMoon.colorpickerplugin.providers.ColorSwatches
import kz.codingOnTheMoon.colorpickerplugin.ui.ColorPickerDialog
import kz.codingOnTheMoon.colorpickerplugin.util.Colors

class ColorPickerPlugin : Plugin, UiPlugin {

    override val id: String = "kz.codingOnTheMoon.colorpickerplugin"

    override fun register(reg: PluginRegistration) {
        val logger = reg.logger("ColorPickerPluginPlugin")
        logger.info("loaded")
        reg.register(
            ep = EDITOR_DECORATION_EP,
            impl = ColorSwatches()
        )
        reg.register(
            ep = UI_ACTION_EP,
            impl = ColorPickerAction(logger)
        )
    }

    override fun contribute(ui: UiRegistration) {
        ui.editorPainter(
            EditorPainter(
                id = "kz.codingOnTheMoon.colorpickerplugin.ColorPickerPlugin-colors",
                layer = EditorPaintLayer.AboveText,
                paint = { editorPaintContext: EditorPaintContext ->
                    val colors = getDetectedColors(editorPaintContext.path)
                    for((offset, color) in colors) {
                        val line = editorPaintContext.lineOf(offset)

                        if (line !in editorPaintContext.visibleLines || editorPaintContext.isHidden(line)) {
                            continue
                        }

                        val lineHeight = editorPaintContext.lineHeight
                        val squareSize = lineHeight * 0.7f

                        drawRoundRect(
                            color = androidx.compose.ui.graphics.Color(
                                red = color.red / 255f,
                                green = color.green / 255f,
                                blue = color.blue / 255f,
                                alpha = color.alpha / 255f
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                x = editorPaintContext.xOf(offset),
                                y = editorPaintContext.lineTop(line) + (lineHeight - squareSize) / 2
                            ),
                            size = androidx.compose.ui.geometry.Size(squareSize, squareSize),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(squareSize / 4)
                        )
                    }
                }
            )
        )
        ui.overlay(
            Overlay(
                id = "kz.codingOnTheMoon.colorpickerplugin.ColorPickerPlugin-color-picker-dialog",
                content = {
                    val isOpen by isPickerOpen.collectAsState()
                    
                    Log.d("ColorPickerOverlay", "Overlay state: isOpen=$isOpen, currentColor=${ColorPickerPlugin._currentColor.value}")
                    if (isOpen) {
                        Log.d("ColorPickerOverlay", "Rendering ColorPickerDialog")
                        ColorPickerDialog(currentColor.value)
                    }else{
                        Log.d("ColorPickerOverlay", "Dialog not rendering (isOpen=false)")
                    }
                }
            )
        )
    }

    companion object {

        private val _swatches = MutableStateFlow<Map<String, List<Pair<Int, Colors>>>>(emptyMap())
        val swatches: StateFlow<Map<String, List<Pair<Int, Colors>>>> = _swatches.asStateFlow()

        private val _currentColor = MutableStateFlow<String>("")
        val currentColor: StateFlow<String> = _currentColor.asStateFlow()

        private val _isPickerOpen = MutableStateFlow(false)
        val isPickerOpen: StateFlow<Boolean> = _isPickerOpen.asStateFlow()

        fun putDetectedColors(filePath: String, colors: List<Pair<Int, Colors>>) {
            val current = _swatches.value.toMutableMap()
            current[filePath] = colors
            _swatches.value = current
        }

        fun getDetectedColors(filePath: String):
        List<Pair<Int, Colors>> = _swatches.value[filePath] ?: emptyList()

        private val _colorPickerChannel = Channel<Pair<Colors?, Boolean>>(capacity = 1)

        private var currentFilePath: String? = null
        private var currentOffset: Int = -1

        fun requestPicker(offset: Int, currentColor: String, filePath: String) {
            currentOffset = offset
            _currentColor.value = currentColor
            currentFilePath = filePath
            _isPickerOpen.value = true
        }
        
        var shouldPreserveOGFormat = true 
        
        fun sendPickerResult(color: Colors?, shouldPreserve: Boolean = shouldPreserveOGFormat) {
            Log.d("ColorPickerPlugin", "sendPickerResult() called with: $color")
            shouldPreserveOGFormat = shouldPreserve
            _isPickerOpen.value = false
            try {
                val result = _colorPickerChannel.trySend(color to shouldPreserve)
                Log.d("ColorPickerPlugin", "trySend() result: $result")

                if (result.isFailure) {
                    Log.e("ColorPickerPlugin", "trySend() failed: ${result.exceptionOrNull()}")
                }
            } catch (e: Exception) {
                Log.e("ColorPickerPlugin", "sendPickerResult() exception: ${e.message}")
            }
        }

        suspend fun awaitPickerResult(): Pair<Colors?, Boolean>?{
            return try {
                _colorPickerChannel.receive()
            } catch (e: Exception) {
                null
            }
        }

        fun getCurrentFilePath() = currentFilePath
        fun getCurrentOffset() = currentOffset
        fun getCurrentOriginalColor() = currentColor.value

    }
}
