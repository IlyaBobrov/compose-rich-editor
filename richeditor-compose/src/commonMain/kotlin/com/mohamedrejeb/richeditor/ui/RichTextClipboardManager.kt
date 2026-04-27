package com.mohamedrejeb.richeditor.ui

import android.content.ClipData
import android.util.Log
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.util.fastForEachIndexed
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.paragraph.type.ParagraphType.Companion.startText
import com.mohamedrejeb.richeditor.utils.append
import kotlin.math.max
import kotlin.math.min

public class RichTextClipboardManager(
    private val richTextState: RichTextState,
    private val clipboard: Clipboard
) : Clipboard {

    override val nativeClipboard: NativeClipboard
        get() = clipboard.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? {
        return clipboard.getClipEntry()
    }

    @OptIn(ExperimentalRichTextApi::class)
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val selection = if (richTextState.selection.collapsed)
            richTextState.lastNonCollapsedSelection
        else
            richTextState.selection

        Log.d("RICH_CLIPBOARD_TAG", "setClipEntry (effective): $selection")
        Log.d("RICH_CLIPBOARD_TAG", "selection.collapsed: ${selection.collapsed}")

        if (selection.collapsed) {
            clipboard.setClipEntry(clipEntry)
            return
        }

        val annotatedString = buildAnnotatedString {
            var index = 0

            richTextState.richParagraphList.fastForEachIndexed { i, paragraph ->
                withStyle(
                    paragraph.paragraphStyle.merge(
                        paragraph.type.getStyle(richTextState.config)
                    )
                ) {
                    // --- startText ---
                    val startText = paragraph.type.startText

                    if (
                        selection.min < index + startText.length &&
                        selection.max > index
                    ) {
                        val selectedText = startText.substring(
                            max(0, selection.min - index),
                            min(selection.max - index, startText.length)
                        )
                        Log.d("RICH_CLIPBOARD_TAG", "$selectedText")
                        append(selectedText)
                    }

                    index += startText.length

                    // --- spans ---
                    withStyle(RichSpanStyle.DefaultSpanStyle) {
                        index = append(
                            richSpanList = paragraph.children,
                            startIndex = index,
                            selection = selection,
                            richTextConfig = richTextState.config,
                        )

                        // --- перенос строки ---
                        if (!richTextState.singleParagraphMode &&
                            i != richTextState.richParagraphList.lastIndex
                        ) {
                            if (
                                selection.min < index + 1 &&
                                selection.max >= index
                            ) {
                                appendLine()
                            }
                            index++
                        }
                    }
                }
            }
        }

        clipboard.setClipEntry(annotatedString.toClipEntrySafe())
    }
}

private fun AnnotatedString.toClipEntrySafe(): ClipEntry {
    val clipData = ClipData.newPlainText(
        "rich_text",
        this.text // важно: используем plain text
    )
    return clipData.toClipEntry()
}