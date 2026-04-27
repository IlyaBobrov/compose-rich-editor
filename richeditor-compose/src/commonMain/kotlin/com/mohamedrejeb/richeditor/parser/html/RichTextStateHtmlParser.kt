package com.mohamedrejeb.richeditor.parser.html

import android.util.Log
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastForEachReversed
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichSpan
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.paragraph.RichParagraph
import com.mohamedrejeb.richeditor.paragraph.type.ConfigurableListLevel
import com.mohamedrejeb.richeditor.paragraph.type.DefaultParagraph
import com.mohamedrejeb.richeditor.paragraph.type.Heading
import com.mohamedrejeb.richeditor.paragraph.type.OrderedList
import com.mohamedrejeb.richeditor.paragraph.type.ParagraphType
import com.mohamedrejeb.richeditor.paragraph.type.ParagraphType.Companion.startText
import com.mohamedrejeb.richeditor.paragraph.type.UnorderedList
import com.mohamedrejeb.richeditor.parser.RichTextStateParser
import com.mohamedrejeb.richeditor.parser.utils.BoldSpanStyle
import com.mohamedrejeb.richeditor.parser.utils.H1SpanStyle
import com.mohamedrejeb.richeditor.parser.utils.H2SpanStyle
import com.mohamedrejeb.richeditor.parser.utils.H3SpanStyle
import com.mohamedrejeb.richeditor.parser.utils.H4SpanStyle
import com.mohamedrejeb.richeditor.parser.utils.H5SpanStyle
import com.mohamedrejeb.richeditor.parser.utils.H6SpanStyle
import com.mohamedrejeb.richeditor.parser.utils.ItalicSpanStyle
import com.mohamedrejeb.richeditor.parser.utils.MarkSpanStyle
import com.mohamedrejeb.richeditor.parser.utils.SmallSpanStyle
import com.mohamedrejeb.richeditor.parser.utils.StrikethroughSpanStyle
import com.mohamedrejeb.richeditor.parser.utils.SubscriptSpanStyle
import com.mohamedrejeb.richeditor.parser.utils.SuperscriptSpanStyle
import com.mohamedrejeb.richeditor.parser.utils.UnderlineSpanStyle
import com.mohamedrejeb.richeditor.utils.customMerge

internal object RichTextStateHtmlParser : RichTextStateParser<String> {

    @OptIn(ExperimentalRichTextApi::class)
    override fun encode(input: String): RichTextState {
        Log.d("RICH_CLIPBOARD_TAG", "encode: input: $input")

        val openedTags = mutableListOf<Pair<String, Map<String, String>>>()
        val stringBuilder = StringBuilder()
        val richParagraphList = mutableListOf(RichParagraph())
        val lineBreakParagraphIndexSet = mutableSetOf<Int>()
        val toKeepEmptyParagraphIndexSet = mutableSetOf<Int>()
        var currentRichSpan: RichSpan? = null
        var currentListLevel = 0

        val handler = KsoupHtmlHandler
            .Builder()
            .onText {
                // In html text inside ul/ol tags is skipped
                val lastOpenedTag = openedTags.lastOrNull()?.first
                if (lastOpenedTag == "ul" || lastOpenedTag == "ol") return@onText

                if (lastOpenedTag in skippedHtmlElements) return@onText

                val addedText = KsoupEntities.decodeHtml(
                    removeHtmlTextExtraSpaces(
                        input = it,
                        trimStart = stringBuilder.lastOrNull() == null || stringBuilder.lastOrNull()
                            ?.isWhitespace() == true || stringBuilder.lastOrNull() == '\n',
                    )
                )

                if (addedText.isEmpty()) return@onText

                stringBuilder.append(addedText)

                val currentRichParagraph = richParagraphList.last()
                val safeCurrentRichSpan =
                    currentRichSpan ?: RichSpan(paragraph = currentRichParagraph)

                if (safeCurrentRichSpan.children.isEmpty()) {
                    safeCurrentRichSpan.text += addedText
                } else {
                    val newRichSpan = RichSpan(paragraph = currentRichParagraph)
                    newRichSpan.text = addedText
                    safeCurrentRichSpan.children.add(newRichSpan)
                }

                if (currentRichSpan == null) {
                    currentRichSpan = safeCurrentRichSpan
                    currentRichParagraph.children.add(safeCurrentRichSpan)
                }
            }
            .onOpenTag { name, attributes, _ ->
                val lastOpenedTag = openedTags.lastOrNull()?.first

                openedTags.add(name to attributes)

                if (name in skippedHtmlElements) {
                    return@onOpenTag
                }

                if (name == "ul" || name == "ol") {
                    // Todo: Apply ul/ol styling if exists
                    currentListLevel = currentListLevel + 1
                    return@onOpenTag
                }

                if (name == "body") {
                    stringBuilder.clear()
                    richParagraphList.clear()
                    richParagraphList.add(RichParagraph())
                    currentRichSpan = null
                }

                val cssStyleMap =
                    attributes["style"]?.let { CssEncoder.parseCssStyle(it) } ?: emptyMap()
                val cssSpanStyle = CssEncoder.parseCssStyleMapToSpanStyle(cssStyleMap)
                val tagSpanStyle = htmlElementsSpanStyleEncodeMap[name]

                val currentRichParagraph = richParagraphList.lastOrNull()
                val isCurrentRichParagraphBlank = currentRichParagraph?.isBlank() == true
                val isCurrentTagBlockElement = name in htmlBlockElements
                val isLastOpenedTagBlockElement = lastOpenedTag in htmlBlockElements

                // For <li> tags inside <ul> or <ol> tags
                if (
                    lastOpenedTag != null &&
                    isCurrentTagBlockElement &&
                    isLastOpenedTagBlockElement &&
                    name == "li" &&
                    currentRichParagraph != null &&
                    currentRichParagraph.type is DefaultParagraph &&
                    isCurrentRichParagraphBlank
                ) {
                    val paragraphType =
                        encodeHtmlElementToRichParagraphType(lastOpenedTag, currentListLevel)
                    currentRichParagraph.type = paragraphType

                    val cssParagraphStyle =
                        CssEncoder.parseCssStyleMapToParagraphStyle(cssStyleMap, attributes)
                    currentRichParagraph.paragraphStyle =
                        currentRichParagraph.paragraphStyle.merge(cssParagraphStyle)
                }

                if (isCurrentTagBlockElement) {
                    val newRichParagraph =
                        if (isCurrentRichParagraphBlank)
                            currentRichParagraph
                        else
                            RichParagraph()

                    var paragraphType: ParagraphType = DefaultParagraph()
                    if (name == "li" && lastOpenedTag != null) {
                        paragraphType =
                            encodeHtmlElementToRichParagraphType(lastOpenedTag, currentListLevel)
                    } else if (name.startsWith("h") && name.length == 2 && name[1].isDigit()) {
                        paragraphType = Heading(name[1].digitToInt())
                    }
                    val cssParagraphStyle =
                        CssEncoder.parseCssStyleMapToParagraphStyle(cssStyleMap, attributes)

                    newRichParagraph.paragraphStyle =
                        newRichParagraph.paragraphStyle.merge(cssParagraphStyle)
                    newRichParagraph.type = paragraphType

                    if (!isCurrentRichParagraphBlank) {
                        stringBuilder.append(' ')

                        richParagraphList.add(newRichParagraph)
                    }

                    val newRichSpan = RichSpan(paragraph = newRichParagraph)
                    newRichSpan.spanStyle = cssSpanStyle.customMerge(tagSpanStyle)

                    if (newRichSpan.spanStyle != SpanStyle()) {
                        currentRichSpan = newRichSpan
                        newRichParagraph.children.add(newRichSpan)
                    } else {
                        currentRichSpan = null
                    }
                } else if (name != BrElement) {
                    val richSpanStyle = encodeHtmlElementToRichSpanStyle(name, attributes)

                    val currentRichParagraph = richParagraphList.last()
                    val newRichSpan = RichSpan(paragraph = currentRichParagraph)
                    newRichSpan.spanStyle = cssSpanStyle.customMerge(tagSpanStyle)
                    newRichSpan.richSpanStyle = richSpanStyle

                    if (currentRichSpan != null) {
                        newRichSpan.parent = currentRichSpan
                        currentRichSpan?.children?.add(newRichSpan)
                    } else {
                        currentRichParagraph.children.add(newRichSpan)
                    }
                    currentRichSpan = newRichSpan
                } else {
                    // name == "br"
                    stringBuilder.append(' ')

                    val newParagraph =
                        if (richParagraphList.isEmpty())
                            RichParagraph()
                        else
                            RichParagraph(paragraphStyle = richParagraphList.last().paragraphStyle)

                    richParagraphList.add(newParagraph)

                    if (richParagraphList.lastIndex > 0)
                        lineBreakParagraphIndexSet.add(richParagraphList.lastIndex - 1)

                    lineBreakParagraphIndexSet.add(richParagraphList.lastIndex)

                    currentRichSpan = null

                    // Keep the same style when having a line break in the middle of a paragraph,
                    // Ex: <h1>Hello<br>World!</h1>
//                    if (isLastOpenedTagBlockElement && !isCurrentRichParagraphBlank)
//                        currentRichSpan?.let { richSpan ->
//                            val newRichSpan = richSpan.copy(
//                                text = "",
//                                textRange = TextRange.Zero,
//                                paragraph = newParagraph,
//                                children = mutableListOf(),
//                            )
//
//                            newParagraph.children.add(newRichSpan)
//
//                            currentRichSpan = newRichSpan
//                        }
//                    else
//                        currentRichSpan = null
                }
            }
            .onCloseTag { name, _ ->
                openedTags.removeLastOrNull()

                val isCurrentRichParagraphBlank = richParagraphList.lastOrNull()?.isBlank() == true
                val isCurrentTagBlockElement = name in htmlBlockElements && name != "li"

                if (isCurrentTagBlockElement && !isCurrentRichParagraphBlank) {
                    stringBuilder.append(' ')

                    val newParagraph =
                        if (richParagraphList.isEmpty())
                            RichParagraph()
                        else
                            RichParagraph(paragraphStyle = richParagraphList.last().paragraphStyle)

                    richParagraphList.add(newParagraph)

                    toKeepEmptyParagraphIndexSet.add(richParagraphList.lastIndex)

                    currentRichSpan = null
                }

                if (name == "ul" || name == "ol") {
                    currentListLevel = (currentListLevel - 1).coerceAtLeast(0)
                    return@onCloseTag
                }

                if (name in skippedHtmlElements)
                    return@onCloseTag

                if (name != BrElement)
                    currentRichSpan = currentRichSpan?.parent
            }
            .build()

        val parser = KsoupHtmlParser(
            handler = handler
        )

        parser.write(input)
        parser.end()

        for (i in richParagraphList.lastIndex downTo 0) {
            // Keep empty paragraphs if they are line breaks <br> or by block html elements
            if (i in lineBreakParagraphIndexSet || (i != richParagraphList.lastIndex && i in toKeepEmptyParagraphIndexSet))
                continue

            // Remove empty paragraphs
            if (richParagraphList[i].isBlank())
                richParagraphList.removeAt(i)
        }

        richParagraphList.forEach { richParagraph ->
            richParagraph.removeEmptyChildren()
        }

        return RichTextState(
            initialRichParagraphList = richParagraphList,
        )
    }

    override fun decode(richTextState: RichTextState, selection: Boolean): String {
        // Если selection=true и выделение не пустое — фильтруем по диапазону
        val selectionRange: TextRange? =
            if (selection && !richTextState.selection.collapsed)
                richTextState.selection
            else if (selection && !richTextState.lastNonCollapsedSelection.collapsed)
                richTextState.lastNonCollapsedSelection
            else
                null

        val builder = StringBuilder()
        val openedListTagNames = mutableListOf<String>()
        var lastParagraphGroupTagName: String? = null
        var lastParagraphGroupLevel = 0
        var isLastParagraphEmpty = false
        var currentListLevel = 0

        // Вычислим глобальные офсеты параграфов через существующий механизм
        // RichParagraph не хранит свой офсет напрямую — вычисляем накопленно,
        // используя тот же алгоритм что в updateAnnotatedString:
        // каждый параграф кроме первого добавляет 1 (line break символ ' ')
        // + type.startText.length + сумма длин дочерних спанов

        // Вспомогательная функция: получить текстовый диапазон параграфа
        // (используем getRichSpanListByTextRange с полным диапазоном для вычисления офсета)
        // Проще: пройдём по параграфам, накапливая offset вручную

        data class ParagraphBounds(val start: Int, val end: Int)

        fun computeParagraphBounds(): List<ParagraphBounds> {
            var index = 0
            return richTextState.richParagraphList.mapIndexed { i, para ->
                if (i > 0) index++ // line break ' '
                val start = index
                index += para.type.startText.length
                // Суммируем длины всех спанов рекурсивно
                fun spanLength(span: RichSpan): Int =
                    span.text.length + span.children.sumOf { spanLength(it) }
                para.children.forEach { index += spanLength(it) }
                ParagraphBounds(start, index)
            }
        }

        val paragraphBounds = if (selectionRange != null) computeParagraphBounds() else null

        richTextState.richParagraphList.fastForEachIndexed { index, richParagraph ->

            // --- Фильтрация по selection ---
            if (selectionRange != null && paragraphBounds != null) {
                val bounds = paragraphBounds[index]
                // Параграф полностью вне selection — пропускаем
                if (bounds.end <= selectionRange.min || bounds.start >= selectionRange.max)
                    return@fastForEachIndexed
            }

            val richParagraphType = richParagraph.type
            val isParagraphEmpty = richParagraph.isEmpty()
            val paragraphGroupTagName = decodeHtmlElementFromRichParagraphType(richParagraph.type)

            val isLineBreak = richParagraph.children.size == 1 &&
                    richParagraph.children.first().text.isBlank()
            if (isLineBreak) {
                builder.append("<br>")
                return@fastForEachIndexed
            }

            val paragraphLevel =
                if (richParagraphType is ConfigurableListLevel) richParagraphType.level else 0

            val isParagraphList = paragraphGroupTagName in listOf("ol", "ul")
            val isLastParagraphList = lastParagraphGroupTagName in listOf("ol", "ul")

            fun isCloseParagraphGroup(): Boolean {
                if (!isLastParagraphList) return false
                if (paragraphLevel > lastParagraphGroupLevel) return false
                if (lastParagraphGroupTagName == paragraphGroupTagName &&
                    paragraphLevel == lastParagraphGroupLevel
                ) return false
                return true
            }

            fun isCloseAllOpenedTags(): Boolean {
                if (isParagraphList) return false
                if (!isLastParagraphList) return false
                return true
            }

            fun isOpenParagraphGroup(): Boolean {
                if (!isParagraphList) return false
                if (isLastParagraphList &&
                    paragraphGroupTagName == openedListTagNames.lastOrNull() &&
                    paragraphLevel < lastParagraphGroupLevel
                ) return false
                if (isLastParagraphList &&
                    paragraphLevel == lastParagraphGroupLevel &&
                    paragraphGroupTagName == lastParagraphGroupTagName
                ) return false
                return true
            }

            if (isCloseAllOpenedTags()) {
                openedListTagNames.fastForEachReversed { builder.append("</$it>") }
                openedListTagNames.clear()
            } else if (isCloseParagraphGroup()) {
                // Close last paragraph group tag
                builder.append("</$lastParagraphGroupTagName>")
                openedListTagNames.removeLastOrNull()
                if (isLastParagraphList && paragraphLevel < lastParagraphGroupLevel) {
                    repeat(lastParagraphGroupLevel - paragraphLevel) {
                        openedListTagNames.removeLastOrNull()?.let { builder.append("</$it>") }
                    }
                }
            }

            if (isOpenParagraphGroup()) {
                builder.append("<$paragraphGroupTagName>")
                openedListTagNames.add(paragraphGroupTagName)
            }

            currentListLevel = paragraphLevel

            fun isLineBreak(): Boolean {
                if (!isParagraphEmpty) return false
                if (isParagraphList && lastParagraphGroupTagName != paragraphGroupTagName) return false
                return true
            }

            // Add line break if the paragraph is empty
            if (isLineBreak()) {
                val skipAddingBr = isLastParagraphEmpty && richParagraph.isEmpty() &&
                        index == richTextState.richParagraphList.lastIndex
                if (!skipAddingBr) builder.append("<$BrElement>")
            } else {
                // Create paragraph tag name
                val headingLevel = richParagraph.getHeadingLevel()
                val paragraphTagName = when {
                    paragraphGroupTagName == "ol" || paragraphGroupTagName == "ul" -> "li"
                    headingLevel != null -> "h$headingLevel"
                    else -> "p"
                }

                val paragraphCssMap =
                    CssDecoder.decodeParagraphStyleToCssStyleMap(richParagraph.paragraphStyle)
                val paragraphCss = CssDecoder.decodeCssStyleMap(paragraphCssMap)

                // Append paragraph opening tag
                builder.append("<$paragraphTagName")
                if (headingLevel == null && paragraphCss.isNotBlank())
                    builder.append(" style=\"$paragraphCss\"")
                builder.append(">")

                // Append paragraph children
                richParagraph.children.fastForEach { richSpan ->
                    // Передаём selectionRange для обрезки текста спана
                    builder.append(
                        decodeRichSpanToHtml(
                            richSpan = richSpan,
                            paragraphTagName = paragraphTagName,
                            selectionRange = selectionRange,
                        )
                    )
                }

                // Append paragraph closing tag
                builder.append("</$paragraphTagName>")
            }

            // Save last paragraph group tag name
            lastParagraphGroupTagName = paragraphGroupTagName
            lastParagraphGroupLevel = paragraphLevel

            isLastParagraphEmpty = isParagraphEmpty
        }

        openedListTagNames.fastForEachReversed { builder.append("</$it>") }
        openedListTagNames.clear()

        return builder.toString()
    }

    @OptIn(ExperimentalRichTextApi::class)
    private fun decodeRichSpanToHtml(
        richSpan: RichSpan,
        parentFormattingTags: List<String> = emptyList(),
        paragraphTagName: String? = null,
        selectionRange: TextRange? = null,
    ): String {
        if (richSpan.isEmpty()) return ""

        // Обрезаем текст спана по selectionRange используя RichSpan.textRange
        val spanText: String = if (selectionRange != null && richSpan.text.isNotEmpty()) {
            val spanStart = richSpan.textRange.min
            val spanEnd = richSpan.textRange.max
            // Пересечение [spanStart, spanEnd) с [selectionRange.min, selectionRange.max)
            val clampedStart = maxOf(spanStart, selectionRange.min)
            val clampedEnd = minOf(spanEnd, selectionRange.max)
            if (clampedStart >= clampedEnd) {
                // Спан полностью вне selection — но дети могут быть внутри, продолжаем
                ""
            } else {
                // Локальные индексы внутри span.text
                val localStart = clampedStart - spanStart
                val localEnd = clampedEnd - spanStart
                richSpan.text.substring(localStart, localEnd)
            }
        } else {
            richSpan.text
        }

        // Если ни текста ни детей попадающих в selection — пропускаем
        // (проверка детей ниже)

        val spanHtml = decodeHtmlElementFromRichSpanStyle(richSpan.richSpanStyle)
        val tagName = spanHtml.first
        val tagAttributes = spanHtml.second

        // Convert attributes map to HTML string
        val tagAttributesStringBuilder = StringBuilder()
        tagAttributes.forEach { (key, value) ->
            tagAttributesStringBuilder.append(" $key=\"$value\"")
        }

        // Convert span style to CSS string
        val htmlStyleFormat = CssDecoder.decodeSpanStyleToHtmlStylingFormat(
            richSpan.spanStyle,
            parentTagName = paragraphTagName
        )
        val spanCss = CssDecoder.decodeCssStyleMap(htmlStyleFormat.cssStyleMap)
        val htmlTags = htmlStyleFormat.htmlTags
            .filter { it !in parentFormattingTags }
            .filter { it !in listOf("h1", "h2", "h3", "h4", "h5", "h6") }

        val childrenHtml = richSpan.children.joinToString("") { child ->
            decodeRichSpanToHtml(
                richSpan = child,
                parentFormattingTags = parentFormattingTags + htmlTags,
                paragraphTagName = paragraphTagName,
                selectionRange = selectionRange,
            )
        }

        // Если ни текст ни дети не дают контента — пропускаем тег целиком
        if (spanText.isEmpty() && childrenHtml.isEmpty()) return ""

        val isRequireOpeningTag =
            tagName != "span" || tagAttributes.isNotEmpty() || spanCss.isNotEmpty()

        val stringBuilder = StringBuilder()
        if (isRequireOpeningTag) {
            // Append HTML element with attributes and style
            stringBuilder.append("<$tagName$tagAttributesStringBuilder")
            if (spanCss.isNotEmpty()) stringBuilder.append(" style=\"$spanCss\"")
            stringBuilder.append(">")
        }

        htmlTags.forEach { stringBuilder.append("<$it>") }
        stringBuilder.append(escapeHtml(spanText))
        stringBuilder.append(childrenHtml)
        htmlTags.reversed().forEach { stringBuilder.append("</$it>") }

        if (isRequireOpeningTag) stringBuilder.append("</$tagName>")

        return stringBuilder.toString()
    }

    /**
     * Encodes HTML elements to [RichSpanStyle].
     */
    @OptIn(ExperimentalRichTextApi::class)
    private fun encodeHtmlElementToRichSpanStyle(
        tagName: String,
        attributes: Map<String, String>,
    ): RichSpanStyle =
        when (tagName) {
            "a" ->
                RichSpanStyle.Link(url = attributes["href"].orEmpty())

            CodeSpanTagName, OldCodeSpanTagName ->
                RichSpanStyle.Code()

            "img" ->
                RichSpanStyle.Image(
                    model = attributes["src"].orEmpty(),
                    width = (attributes["width"]?.toIntOrNull() ?: 0).sp,
                    height = (attributes["height"]?.toIntOrNull() ?: 0).sp,
                    contentDescription = attributes["alt"] ?: ""
                )

            else ->
                RichSpanStyle.Default
        }

    /**
     * Decodes HTML elements from [RichSpanStyle].
     */
    @OptIn(ExperimentalRichTextApi::class)
    private fun decodeHtmlElementFromRichSpanStyle(
        richSpanStyle: RichSpanStyle,
    ): Pair<String, Map<String, String>> =
        when (richSpanStyle) {
            is RichSpanStyle.Link ->
                "a" to mapOf(
                    "href" to richSpanStyle.url,
                    "target" to "_blank"
                )

            is RichSpanStyle.Code ->
                CodeSpanTagName to emptyMap()

            is RichSpanStyle.Image ->
                if (richSpanStyle.model is String)
                    "img" to mapOf(
                        "src" to richSpanStyle.model,
                        "width" to richSpanStyle.width.value.toString(),
                        "height" to richSpanStyle.height.value.toString(),
                    )
                else
                    "span" to emptyMap()

            else ->
                "span" to emptyMap()
        }

    /**
     * Encodes HTML elements to [ParagraphType].
     */
    private fun encodeHtmlElementToRichParagraphType(
        tagName: String,
        listLevel: Int,
    ): ParagraphType {
        return when (tagName) {
            "ul" -> UnorderedList(initialLevel = listLevel)
            "ol" -> OrderedList(number = 1, initialLevel = listLevel)
            else -> DefaultParagraph()
        }
    }

    /**
     * Decodes HTML elements from [ParagraphType].
     */
    private fun decodeHtmlElementFromRichParagraphType(
        richParagraphType: ParagraphType,
    ): String {
        return when (richParagraphType) {
            is UnorderedList -> "ul"
            is OrderedList -> "ol"
            is Heading -> "h${richParagraphType.level}"
            else -> "p"
        }
    }

    private fun RichParagraph.getHeadingLevel(): Int? {
        val styles = children.map { it.spanStyle }

        return when {
            styles.any { it == H1SpanStyle } -> 1
            styles.any { it == H2SpanStyle } -> 2
            styles.any { it == H3SpanStyle } -> 3
            styles.any { it == H4SpanStyle } -> 4
            styles.any { it == H5SpanStyle } -> 5
            styles.any { it == H6SpanStyle } -> 6
            else -> null
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

}

/**
 * Encodes HTML elements to [SpanStyle].
 *
 * @see <a href="https://www.w3schools.com/html/html_formatting.asp">HTML formatting</a>
 */
internal val htmlElementsSpanStyleEncodeMap = mapOf(
    "b" to BoldSpanStyle,
    "strong" to BoldSpanStyle,
    "i" to ItalicSpanStyle,
    "em" to ItalicSpanStyle,
    "u" to UnderlineSpanStyle,
    "ins" to UnderlineSpanStyle,
    "s" to StrikethroughSpanStyle,
    "strike" to StrikethroughSpanStyle,
    "del" to StrikethroughSpanStyle,
    "sub" to SubscriptSpanStyle,
    "sup" to SuperscriptSpanStyle,
    "mark" to MarkSpanStyle,
    "small" to SmallSpanStyle,
    "h1" to H1SpanStyle,
    "h2" to H2SpanStyle,
    "h3" to H3SpanStyle,
    "h4" to H4SpanStyle,
    "h5" to H5SpanStyle,
    "h6" to H6SpanStyle,
)

/**
 * Decodes HTML elements from [SpanStyle].
 *
 * @see <a href="https://www.w3schools.com/html/html_formatting.asp">HTML formatting</a>
 */
internal val htmlElementsSpanStyleDecodeMap = mapOf(
    BoldSpanStyle to "b",
    ItalicSpanStyle to "i",
    UnderlineSpanStyle to "u",
    StrikethroughSpanStyle to "s",
    SubscriptSpanStyle to "sub",
    SuperscriptSpanStyle to "sup",
    MarkSpanStyle to "mark",
    SmallSpanStyle to "small",
//    H1SpanStyle to "h1",
//    H2SpanStyle to "h2",
//    H3SpanStyle to "h3",
//    H4SpanStyle to "h4",
//    H5SpanStyle to "h5",
//    H6SpanStyle to "h6",
)

internal const val CodeSpanTagName = "code"
internal const val OldCodeSpanTagName = "code-span"