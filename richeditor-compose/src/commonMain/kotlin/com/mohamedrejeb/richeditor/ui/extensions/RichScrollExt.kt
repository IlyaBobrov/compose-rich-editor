package com.mohamedrejeb.richeditor.ui.extensions

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import com.mohamedrejeb.richeditor.model.RichTextState

@Composable
public fun RichCursorWrapper(
    richFieldValue: RichTextState,
    content: @Composable (
        scrollState: ScrollState,
        onLayoutResult: (TextLayoutResult) -> Unit,
        onSizeChanged: (IntSize) -> Unit
    ) -> Unit
) {
    val scrollState = rememberScrollState()
    var size by remember { mutableStateOf(IntSize(0, 0)) }
    var height by remember { mutableStateOf(0) }
    var layoutResult: TextLayoutResult? by remember { mutableStateOf(null) }

    content(
        scrollState,
        { lr ->
            layoutResult = lr
        },
        { s ->
            size = s
        }
    )

    LaunchedEffect(richFieldValue.selection, size) {
        if (!richFieldValue.selection.collapsed) return@LaunchedEffect

        val cursorInView = richFieldValue.isCursorInView(
            layoutResult = layoutResult ?: return@LaunchedEffect,
            height = size.height.toFloat(),
            scrollValue = scrollState.value.toFloat()
        )
        height = size.height

        if (!cursorInView) {
            scrollState.scrollBy(
                richFieldValue.calculateRequiredSelectionScroll(
                    layoutResult = layoutResult!!,
                    height = height.toFloat(),
                    scrollValue = scrollState.value.toFloat()
                )
            )
        }
    }
}

public fun RichTextState.isCursorInView(
    layoutResult: TextLayoutResult,
    height: Float,
    scrollValue: Float
): Boolean = with(layoutResult) {
    val currentLine = try {
        getLineForOffset(selection.min)
    } catch (ex: IllegalArgumentException) {
        //System.err.println("Corrected Wrong Offset!")
        getLineForOffset(selection.min - 1)
    }
    val lineBottom = getLineBottom(currentLine)
    val lineTop = getLineTop(currentLine)
    lineBottom <= height + scrollValue && lineTop >= scrollValue
}

public fun RichTextState.calculateRequiredSelectionScroll(
    layoutResult: TextLayoutResult,
    height: Float,
    scrollValue: Float
): Float = with(layoutResult) {
    val currentLine = try {
        getLineForOffset(selection.min)
    } catch (ex: IllegalArgumentException) {
        //System.err.println("Corrected Wrong Offset!")
        getLineForOffset(selection.min - 1)
    }
    val lineTop = getLineTop(currentLine)
    val lineBottom = getLineBottom(currentLine)
    if (lineTop < scrollValue) -(scrollValue - lineTop)
    else if (lineBottom > height + scrollValue) lineBottom - (height + scrollValue)
    else 0f
}