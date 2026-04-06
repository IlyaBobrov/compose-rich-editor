package com.mohamedrejeb.richeditor.ui.material3

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.extensions.RichCursorWrapper

/**
 * Material 3 Design filled rich text field.
 *  Кастомная версия с доработками
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun RichTextEditorPro(
    state: RichTextState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    scaffoldPadding: PaddingValues = PaddingValues(0.dp),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RichTextEditorDefaults.filledShape,
    colors: RichTextEditorColors = RichTextEditorDefaults.richTextEditorColors(),
    contentPadding: PaddingValues =
        if (label == null) {
            RichTextEditorDefaults.richTextEditorWithoutLabelPadding()
        } else {
            RichTextEditorDefaults.richTextEditorWithLabelPadding()
        },
) {
    RichCursorWrapper(state) { scrollState, onLayoutResult, onSizeChanged ->
        RichTextEditor(
            state = state,
            modifier = Modifier
                //.fillMaxSize()
                .padding(scaffoldPadding)
                .then(modifier)
                .padding(16.dp)
                .onSizeChanged { s -> onSizeChanged(s) },
            contentPadding = contentPadding,
            onTextLayout = {
                onLayoutResult(it)
                onTextLayout(it)
            },
            //modifierFilledContainerBox =  Modifier.verticalScroll(scrollState),
            decorationWrapper = { content ->
                Box(modifier = Modifier.verticalScroll(scrollState)) {
                    content()
                }
            },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            maxLength = maxLength,
            interactionSource = interactionSource,
            shape = shape,
            colors = colors,
        )
    }
}