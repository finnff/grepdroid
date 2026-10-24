package com.grepdroid.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "Extracted Text" header with its "Hide repetitions" toggle, and the OCR text
 * below it, which can be long-pressed to select and copy part of it.
 */
@Composable
internal fun ExtractedTextSection(
    text: String,
    hideRepetitions: Boolean,
    onHideRepetitionsChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Extracted Text",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Hide repetitions",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Switch(
            checked = hideRepetitions,
            onCheckedChange = onHideRepetitionsChange,
            modifier = Modifier.scale(0.75f)
        )
    }
    SelectionContainer {
        Text(
            text = text,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
