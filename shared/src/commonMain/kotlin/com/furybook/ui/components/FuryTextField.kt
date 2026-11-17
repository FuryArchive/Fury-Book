package com.furybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublBackground
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryBorderStrong
import com.furybook.ui.theme.FuryShapes

/** A full-width field with its label above it and an optional error line below. */
@Composable
fun FuryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), color = DublMuted, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            shape = FuryShapes.control,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DublText,
                unfocusedTextColor = DublText,
                focusedContainerColor = DublBackground,
                unfocusedContainerColor = DublBackground,
                errorContainerColor = DublBackground,
                focusedBorderColor = DublAccent,
                unfocusedBorderColor = FuryBorderStrong,
                errorBorderColor = DublDanger,
                cursorColor = DublAccent,
            ),
        )
        if (error != null) {
            Text(error, color = DublDanger, style = MaterialTheme.typography.bodySmall)
        }
    }
}
