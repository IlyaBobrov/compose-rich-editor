package com.mohamedrejeb.richeditor.paragraph.type

import androidx.compose.ui.text.ParagraphStyle
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichSpan
import com.mohamedrejeb.richeditor.model.RichTextConfig
import com.mohamedrejeb.richeditor.paragraph.RichParagraph

internal class Heading(val level: Int) : ParagraphType {
    override fun getStyle(config: RichTextConfig): ParagraphStyle {
        return ParagraphStyle()
    }

    @OptIn(ExperimentalRichTextApi::class)
    override val startRichSpan: RichSpan =
        RichSpan(paragraph = RichParagraph(type = this))

    override fun getNextParagraphType(): ParagraphType =
        Heading(level)

    override fun copy(): ParagraphType =
        Heading(level)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Heading) return false
        if (level != other.level) return false
        return true
    }

    override fun hashCode(): Int {
        return level
    }
}
