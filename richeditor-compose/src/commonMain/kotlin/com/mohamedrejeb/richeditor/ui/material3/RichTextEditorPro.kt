package com.mohamedrejeb.richeditor.ui.material3

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.awaitSecondDownOrNull
import com.mohamedrejeb.richeditor.ui.extensions.RichCursorWrapper
import com.mohamedrejeb.richeditor.utils.getOffsetForPositionStrict
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onScrollStateReady: (ScrollState) -> Unit = {},
) {
    var lastTapPosition by remember { mutableStateOf<Offset?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var capturedScrollState by remember { mutableStateOf<ScrollState?>(null) }

    RichCursorWrapper(state) { scrollState, onLayoutResult, onSizeChanged ->
        LaunchedEffect(scrollState) {
            capturedScrollState = scrollState
            onScrollStateReady(scrollState)
        }

        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val topPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }
        val startPaddingPx = with(density) {
            contentPadding.calculateStartPadding(layoutDirection).toPx()
        }

        RichTextEditor(
            state = state,
            modifier = Modifier
                .padding(scaffoldPadding)
                .then(modifier)
                .onSizeChanged { s -> onSizeChanged(s) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        val firstUp = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (firstUp != null) {
                            lastTapPosition = firstUp.position
                            coroutineScope.launch {
                                delay(100)
                                lastTapPosition = null
                            }
                        }
                        val secondDown =
                            awaitSecondDownOrNull(firstUp, pass = PointerEventPass.Initial)
                        val secondUp = secondDown?.let {
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        }
                        val thirdDown = if (secondUp != null) {
                            awaitSecondDownOrNull(secondUp, pass = PointerEventPass.Initial)
                        } else null
                        if (thirdDown != null) {
                            thirdDown.consume()
                            state.handleTripleClick()
                        }
                    }
                },
            onValueChange = { newValue ->
                var finalValue = newValue
                if (newValue.selection.collapsed
                    && newValue.text == state.textFieldValue.text
                    && lastTapPosition != null
                ) {
                    val layout = state.textLayoutResult
                    val scroll = capturedScrollState?.value?.toFloat() ?: 0f
                    if (layout != null) {
                        // Корректируем: вычитаем padding, прибавляем scroll
                        val correctedPosition = Offset(
                            x = lastTapPosition!!.x - startPaddingPx,
                            y = lastTapPosition!!.y - topPaddingPx + scroll
                        )
                        val strictOffset = layout.getOffsetForPositionStrict(correctedPosition)
                        if (strictOffset != newValue.selection.start) {
                            finalValue = newValue.copy(selection = TextRange(strictOffset))
                        }
                    }
                    lastTapPosition = null
                }
                finalValue
            },
            contentPadding = contentPadding,
            onTextLayout = {
                onLayoutResult(it)
                onTextLayout(it)
            },
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