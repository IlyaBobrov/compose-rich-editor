package com.mohamedrejeb.richeditor.ui

import android.content.ClipData
import android.util.Log
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.toClipEntry
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState

public class RichTextClipboardManagerLite(
    private val richTextState: RichTextState,
    private val clipboard: Clipboard,
    private val plainText: String,
    private val htmlText: String,
) : Clipboard {

    override val nativeClipboard: NativeClipboard
        get() = clipboard.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? {
        val entry = clipboard.getClipEntry() ?: return null
        val clipData = entry.clipData

        val item = clipData.getItemAt(0)

        val htmlText = item.htmlText
        val plainText = item.text?.toString()

        Log.d("RICH_CLIPBOARD_TAG", "getClipEntry")
        Log.d("RICH_CLIPBOARD_TAG", "htmlText: $htmlText")
        Log.d("RICH_CLIPBOARD_TAG", "plainText: $plainText")
        when {
            htmlText != null -> {
                richTextState.addHtmlFromBufferToCursorPosition(htmlText)
                return null
            }

            plainText != null -> {
                richTextState.addHtmlFromBufferToCursorPosition("<p>$plainText</p>")
                return null
            }
        }

        return entry
    }

    @OptIn(ExperimentalRichTextApi::class)
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val clipData = ClipData.newHtmlText(
            "rich_text",
            plainText,
            htmlText
        ).toClipEntry()

        clipboard.setClipEntry(clipData)
    }
}