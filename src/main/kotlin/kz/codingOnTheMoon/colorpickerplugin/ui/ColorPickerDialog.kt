package kz.codingOnTheMoon.colorpickerplugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.codingOnTheMoon.colorpickerplugin.ColorPickerPlugin
import kz.codingOnTheMoon.colorpickerplugin.util.ColorState
import kz.codingOnTheMoon.colorpickerplugin.util.ColorUtils
import kz.codingOnTheMoon.colorpickerplugin.util.Colors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(currentColor: String) {

    val initialColor = ColorUtils.parseColor(currentColor)
        ?: Colors(alpha = 255, red = 255, green = 0, blue = 0)

    var hexInput by rememberSaveable {
        mutableStateOf(initialColor.toHex())
    }
    var hexInputError by rememberSaveable { mutableStateOf(false) }

    var colorState by remember {
        mutableStateOf(
            ColorState(
                alpha = initialColor.alpha.toFloat(),
                red = initialColor.red.toFloat(),
                green = initialColor.green.toFloat(),
                blue = initialColor.blue.toFloat()
            )
        )
    }

    var shouldPreserveFormat by rememberSaveable { mutableStateOf(true) }

    val syncHexInput = {
        hexInput = colorState.toHex()
        hexInputError = false
    }

    val updateFromHex = { hex: String ->
        ColorUtils.parseColor(hex)?.let { parsed ->
            colorState = ColorState(
                alpha = parsed.alpha.toFloat(),
                red = parsed.red.toFloat(),
                green = parsed.green.toFloat(),
                blue = parsed.blue.toFloat()
            )
            hexInputError = false
        } ?: run {
            hexInputError = hex.isNotEmpty() && hex.length >= 3
        }
    }

    AlertDialog(
        onDismissRequest = {
            ColorPickerPlugin.sendPickerResult(null)
        },
        title = {
            Text("Pick a Color")
        },
        text = {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { newValue ->
                        hexInput = newValue
                        updateFromHex(newValue)
                    },
                    label = { 
                        Text("Hex Color")
                    },
                    placeholder = {
                        Text("#RGB, #RRGGBB, #ARGB, #AARRGGBB, or 0x...")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true,
                    isError = hexInputError,
                    supportingText = {
                        if (hexInputError) {
                            Text("Enter a valid hex color", color = Color.Red)
                        } else if (hexInput.isNotEmpty()) {
                            Text("Accepts: #RGB, #RRGGBB, #ARGB, #AARRGGBB, 0xRRGGBB, etc.", color = Color.Gray)
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ColorSlider(
                    name = "Alpha",
                    value = colorState.alpha,
                    onColorChange = {
                        colorState = colorState.copy(alpha = it)
                        syncHexInput()
                    }
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                ColorSlider(
                    name = "Red",
                    value = colorState.red,
                    onColorChange = {
                        colorState = colorState.copy(red = it)
                        syncHexInput()
                    }
                )

                ColorSlider(
                    name = "Green",
                    value = colorState.green,
                    onColorChange = {
                        colorState = colorState.copy(green = it)
                        syncHexInput()
                    }
                )

                ColorSlider(
                    modifier = Modifier.padding(bottom = 8.dp),
                    name = "Blue",
                    value = colorState.blue,
                    onColorChange = {
                        colorState = colorState.copy(blue = it)
                        syncHexInput()
                    }
                )
                
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(
                            Color(
                                alpha = colorState.alpha / 255f,
                                red = colorState.red / 255f,
                                green = colorState.green / 255f,
                                blue = colorState.blue / 255f
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = shouldPreserveFormat,
                        onCheckedChange = { shouldPreserveFormat = it }
                    )
                    Text("Preserve original color format")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ColorPickerPlugin.sendPickerResult(
                        colorState.toColors(),
                        shouldPreserveFormat
                    )
                },
                enabled = !hexInputError 
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    ColorPickerPlugin.sendPickerResult(null)
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorSlider(
    modifier: Modifier = Modifier,
    name: String,
    value: Float,
    onColorChange: (Float) -> Unit
) {
    Text(
        text = "$name: ${value.toInt()}",
        fontSize = 12.sp
    )
    Slider(
        value = value,
        onValueChange = onColorChange,
        valueRange = 0f .. 255f,
        modifier = Modifier.fillMaxWidth()
    )
}
