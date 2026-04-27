package com.mohamedrejeb.richeditor.model

import android.util.Log
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.coerceIn
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.annotation.InternalRichTextApi
import com.mohamedrejeb.richeditor.paragraph.RichParagraph
import com.mohamedrejeb.richeditor.paragraph.type.ConfigurableListLevel
import com.mohamedrejeb.richeditor.paragraph.type.ConfigurableStartTextWidth
import com.mohamedrejeb.richeditor.paragraph.type.DefaultParagraph
import com.mohamedrejeb.richeditor.paragraph.type.Heading
import com.mohamedrejeb.richeditor.paragraph.type.OrderedList
import com.mohamedrejeb.richeditor.paragraph.type.ParagraphType
import com.mohamedrejeb.richeditor.paragraph.type.ParagraphType.Companion.startText
import com.mohamedrejeb.richeditor.paragraph.type.UnorderedList
import com.mohamedrejeb.richeditor.parser.html.RichTextStateHtmlParser
import com.mohamedrejeb.richeditor.parser.markdown.RichTextStateMarkdownParser
import com.mohamedrejeb.richeditor.utils.append
import com.mohamedrejeb.richeditor.utils.customMerge
import com.mohamedrejeb.richeditor.utils.getCommonRichStyle
import com.mohamedrejeb.richeditor.utils.getCommonStyle
import com.mohamedrejeb.richeditor.utils.getCommonType
import com.mohamedrejeb.richeditor.utils.getOffsetForPositionClamped
import com.mohamedrejeb.richeditor.utils.isSpecifiedFieldsEquals
import com.mohamedrejeb.richeditor.utils.removeRange
import com.mohamedrejeb.richeditor.utils.toText
import com.mohamedrejeb.richeditor.utils.unmerge
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.reflect.KClass

@Composable
public fun rememberRichTextState(): RichTextState {
    return rememberSaveable(saver = RichTextState.Saver) {
        RichTextState()
    }
}

public fun test1() {

}

@OptIn(ExperimentalRichTextApi::class)
public class RichTextState internal constructor(
    initialRichParagraphList: List<RichParagraph>,
) {
    public constructor() : this(listOf(RichParagraph()))

    internal val richParagraphList = mutableStateListOf<RichParagraph>()
    public var visualTransformation: VisualTransformation by mutableStateOf(VisualTransformation.None)
    public var textFieldValue: TextFieldValue by mutableStateOf(TextFieldValue())
        private set

    internal val inlineContentMap = mutableStateMapOf<String, InlineTextContent>()
    internal val usedInlineContentMapKeys = mutableSetOf<String>()

    /**
     * Аннотированная строка, представляющая форматированный текст.
     */
    public var annotatedString: AnnotatedString by mutableStateOf(AnnotatedString(text = ""))
        private set

    /**
     * Выделение (selection) в форматированном тексте.
     */
    public var selection: TextRange
        get() = textFieldValue.selection
        set(value) {
            if (value.min >= 0 && value.max <= textFieldValue.text.length) {
                val newTextFieldValue = textFieldValue.copy(selection = value)
                updateTextFieldValue(newTextFieldValue)
            }
        }

    /**
     * Последнее выделение, которое не было пустым (collapsed).
     * Используется для копирования, если системное выделение сбросилось.
     */
    internal var lastNonCollapsedSelection: TextRange = TextRange.Zero
        private set

    public val composition: TextRange? get() = textFieldValue.composition

    public var singleParagraphMode: Boolean by mutableStateOf(false)

    internal var textLayoutResult: TextLayoutResult? by mutableStateOf(null)
        private set

    private var lastPressPosition: Offset? by mutableStateOf(null)

    private var currentAppliedSpanStyle: SpanStyle by mutableStateOf(
        getRichSpanByTextIndex(textIndex = selection.min - 1)?.fullSpanStyle
            ?: RichSpanStyle.DefaultSpanStyle
    )

    private var currentAppliedRichSpanStyle: RichSpanStyle by mutableStateOf(
        getRichSpanByTextIndex(textIndex = selection.min - 1)?.richSpanStyle
            ?: RichSpanStyle.Default
    )

    /**
     * Возвращает, является ли текущий выделенный текст ссылкой.
     */
    public val isLink: Boolean get() = currentAppliedRichSpanStyle::class == RichSpanStyle.Link::class

    /**
     * Возвращает текст выбранной ссылки.
     */
    public val selectedLinkText: String?
        get() =
            if (isLink)
                getRichSpanByTextIndex(textIndex = selection.min - 1)?.text
            else
                null

    /**
     * Возвращает URL выбранной ссылки.
     */
    public val selectedLinkUrl: String? get() = (currentAppliedRichSpanStyle as? RichSpanStyle.Link)?.url

    @Deprecated(
        message = "Use isCodeSpan instead",
        replaceWith = ReplaceWith("isCodeSpan"),
        level = DeprecationLevel.ERROR,
    )
    public val isCode: Boolean get() = isCodeSpan

    /**
     * Возвращает, является ли текущий выделенный текст кодом (code span).
     */
    public val isCodeSpan: Boolean get() = currentRichSpanStyle::class == RichSpanStyle.Code::class

    private var toAddSpanStyle: SpanStyle by mutableStateOf(SpanStyle())
    private var toRemoveSpanStyle: SpanStyle by mutableStateOf(SpanStyle())

    private var toAddRichSpanStyle: RichSpanStyle by mutableStateOf(RichSpanStyle.Default)
    private var toRemoveRichSpanStyleKClass: KClass<out RichSpanStyle> by mutableStateOf(
        RichSpanStyle.Default::class
    )

    @Deprecated(
        message = "Use isRichSpan with T or KClass instead",
        replaceWith = ReplaceWith("isRichSpan<>()"),
        level = DeprecationLevel.WARNING,
    )
    public fun isRichSpan(spanStyle: RichSpanStyle): Boolean =
        isRichSpan(spanStyle::class)

    public inline fun <reified T : RichSpanStyle> isRichSpan(): Boolean =
        isRichSpan(T::class)

    public fun isRichSpan(kClass: KClass<out RichSpanStyle>): Boolean {
        return currentRichSpanStyle::class == kClass
    }

    /**
     * Текущий строчный стиль.
     * Если выделение свёрнуто (collapsed), строчный стиль — это стиль символа, предшествующего выделению.
     * Если выделение не свёрнуто, строчный стиль — это стиль выделенного фрагмента.
     */
    public val currentSpanStyle: SpanStyle
        get() = currentAppliedSpanStyle.customMerge(toAddSpanStyle).unmerge(toRemoveSpanStyle)

    /**
     * Текущий богатый строчный стиль.
     * Если выделение свёрнуто (collapsed), богатый строчный стиль — это стиль символа, предшествующего выделению.
     * Если выделение не свёрнуто, богатый строчный стиль — это стиль выделенного фрагмента.
     */
    public val currentRichSpanStyle: RichSpanStyle
        get() = when {
            currentAppliedRichSpanStyle::class == RichSpanStyle.Default::class ->
                toAddRichSpanStyle

            currentAppliedRichSpanStyle::class == toRemoveRichSpanStyleKClass ->
                RichSpanStyle.Default

            else ->
                currentAppliedRichSpanStyle
        }

    internal var styledRichSpanList = mutableStateListOf<RichSpan>()
        private set

    private var currentAppliedParagraphStyle: ParagraphStyle by mutableStateOf(
        getRichParagraphByTextIndex(textIndex = selection.min - 1)?.paragraphStyle
            ?: richParagraphList.firstOrNull()?.paragraphStyle
            ?: RichParagraph.DefaultParagraphStyle
    )

    private var toAddParagraphStyle: ParagraphStyle by mutableStateOf(ParagraphStyle())
    private var toRemoveParagraphStyle: ParagraphStyle by mutableStateOf(ParagraphStyle())

    /**
     * Текущий стиль абзаца.
     * Если выделение свёрнуто (collapsed), стиль абзаца — это стиль абзаца, содержащего выделение.
     * Если выделение не свёрнуто, стиль абзаца — это стиль выделенного фрагмента.
     */
    public val currentParagraphStyle: ParagraphStyle
        get() = currentAppliedParagraphStyle
            .merge(toAddParagraphStyle)
            .unmerge(toRemoveParagraphStyle)

    private var currentRichParagraphType: ParagraphType by mutableStateOf(
        getRichParagraphByTextIndex(textIndex = selection.min - 1)?.type
            ?: DefaultParagraph()
    )

    public var isUnorderedList: Boolean by mutableStateOf(currentRichParagraphType is UnorderedList)
        private set
    public var isOrderedList: Boolean by mutableStateOf(currentRichParagraphType is OrderedList)
        private set
    public var isHeading: Boolean by mutableStateOf(currentRichParagraphType is Heading)
        private set
    public var currentHeadingLevel: Int by mutableStateOf(
        (currentRichParagraphType as? Heading)?.level ?: 0
    )
        private set
    public var isList: Boolean by mutableStateOf(isUnorderedList || isOrderedList)
        private set
    public var canIncreaseListLevel: Boolean by mutableStateOf(false)
        private set
    public var canDecreaseListLevel: Boolean by mutableStateOf(false)
        private set

    public val config: RichTextConfig = RichTextConfig(
        updateText = {
            updateTextFieldValue(textFieldValue)
        }
    )

    init {
        updateRichParagraphList(initialRichParagraphList)
    }

    /**
     * Public methods
     */

    @Deprecated(
        message = "Use config instead",
        replaceWith = ReplaceWith("config"),
        level = DeprecationLevel.WARNING,
    )
    public fun setConfig(
        linkColor: Color = Color.Unspecified,
        linkTextDecoration: TextDecoration? = null,
        codeColor: Color = Color.Unspecified,
        codeBackgroundColor: Color = Color.Unspecified,
        codeStrokeColor: Color = Color.Unspecified,
        listIndent: Int = -1
    ) {
        if (linkColor.isSpecified)
            config.linkColor = linkColor

        if (linkTextDecoration != null)
            config.linkTextDecoration = linkTextDecoration

        if (codeColor.isSpecified)
            config.codeSpanColor = codeColor

        if (codeBackgroundColor.isSpecified)
            config.codeSpanBackgroundColor = codeBackgroundColor

        if (codeStrokeColor.isSpecified)
            config.codeSpanStrokeColor = codeStrokeColor

        if (listIndent > -1)
            config.listIndent = listIndent

        updateTextFieldValue(textFieldValue)
    }

    // Text

    /**
     * Удаляет выделенный текст из текущего текстового поля.
     *
     * Этот метод удаляет текст, указанный параметром `selection`, из текущего текстового поля.
     *
     * @see removeTextRange
     */
    public fun removeSelectedText(): Unit =
        removeTextRange(selection)

    /**
     * Удаляет указанный диапазон текста из текущего текста.
     *
     * @param textRange Диапазон текста, подлежащий удалению.
     */
    public fun removeTextRange(
        textRange: TextRange
    ) {
        require(textRange.min >= 0) {
            "The start index must be non-negative."
        }

        require(textRange.max <= textFieldValue.text.length) {
            "The end index must be within the text bounds. " +
                    "The text length is ${textFieldValue.text.length}, " +
                    "but the end index is ${textRange.max}."
        }

        onTextFieldValueChange(
            newTextFieldValue = textFieldValue.copy(
                text = textFieldValue.text.removeRange(
                    startIndex = textRange.min,
                    endIndex = textRange.max,
                ),
                selection = TextRange(textRange.min),
            )
        )
    }

    /**
     * Заменяет текущий выделенный текст на предоставленный текст.
     *
     * @param text Новый текст для вставки.
     */
    public fun replaceSelectedText(text: String): Unit =
        replaceTextRange(selection, text)

    /**
     * Заменяет текст в указанном диапазоне на предоставленный текст.
     *
     * @param textRange Диапазон текста, подлежащий замене.
     * @param text Новый текст для вставки.
     */
    public fun replaceTextRange(
        textRange: TextRange,
        text: String
    ) {
        require(textRange.min >= 0) {
            "The start index must be non-negative."
        }

        require(textRange.max <= textFieldValue.text.length) {
            "The end index must be within the text bounds. " +
                    "The text length is ${textFieldValue.text.length}, " +
                    "but the end index is ${textRange.max}."
        }

        removeTextRange(textRange)
        addTextAfterSelection(text = text)
    }

    /**
     * Добавляет указанный текст в текстовое поле в позиции текущего выделения.
     *
     * @param text Текст, который нужно добавить.
     */
    public fun addTextAfterSelection(text: String): Unit =
        addTextAtIndex(
            index = if (selection.reversed) selection.start else selection.end,
            text = text
        )

    /**
     * Добавляет указанный текст в текстовое поле по указанному индексу.
     *
     * @param index Индекс, по которому следует добавить текст.
     * @param text Текст, который нужно добавить.
     */
    public fun addTextAtIndex(
        index: Int,
        text: String,
    ) {
        require(index >= 0) {
            "The index must be non-negative."
        }

        require(index <= textFieldValue.text.length) {
            "The index must be within the text bounds. " +
                    "The text length is ${textFieldValue.text.length}, " +
                    "but the index is $index."
        }

        val beforeText = textFieldValue.text.substring(0, index)
        val afterText = textFieldValue.text.substring(index, textFieldValue.text.length)
        val newText = "$beforeText$text$afterText"

        onTextFieldValueChange(
            newTextFieldValue = textFieldValue.copy(
                text = newText,
                selection = TextRange(index + text.length),
            )
        )
    }

    // SpanStyle

    /**
     * Возвращает [SpanStyle] текста в указанном диапазоне.
     * Если диапазон текста свёрнут (collapsed), возвращается стиль символа, предшествующего этому диапазону.
     *
     * @param textRange Диапазон текста.
     * @return [SpanStyle] текста в указанном диапазоне.
     */
    public fun getSpanStyle(textRange: TextRange): SpanStyle =
        if (textRange.collapsed) {
            val richSpan = getRichSpanByTextIndex(textIndex = textRange.min - 1)

            richSpan
                ?.fullSpanStyle
                ?: RichSpanStyle.DefaultSpanStyle
        } else {
            val richSpanList = getRichSpanListByTextRange(textRange)

            richSpanList
                .getCommonStyle()
                ?: RichSpanStyle.DefaultSpanStyle
        }

    /**
     * Возвращает [RichSpanStyle] текста в указанном диапазоне.
     * Если диапазон текста свёрнут (collapsed), возвращается стиль символа, предшествующего этому диапазону.
     *
     * @param textRange Диапазон текста.
     * @return [RichSpanStyle] текста в указанном диапазоне.
     */
    public fun getRichSpanStyle(textRange: TextRange): RichSpanStyle =
        if (textRange.collapsed) {
            val richSpan = getRichSpanByTextIndex(textIndex = textRange.min - 1)

            richSpan
                ?.fullStyle
                ?: RichSpanStyle.Default
        } else {
            val richSpanList = getRichSpanListByTextRange(textRange)

            richSpanList
                .getCommonRichStyle()
                ?: RichSpanStyle.Default
        }

    /**
     * Возвращает [ParagraphStyle] текста в указанном диапазоне.
     * Если диапазон текста свёрнут (collapsed), возвращается стиль абзаца, содержащего этот диапазон.
     *
     * @param textRange Диапазон текста.
     * @return [ParagraphStyle] текста в указанном диапазоне.
     */
    public fun getParagraphStyle(textRange: TextRange): ParagraphStyle =
        if (textRange.collapsed) {
            val richParagraph = getRichParagraphByTextIndex(textIndex = textRange.min - 1)

            richParagraph
                ?.paragraphStyle
                ?: RichParagraph.DefaultParagraphStyle
        } else {
            val richParagraphList = getRichParagraphListByTextRange(textRange)

            richParagraphList
                .getCommonStyle()
                ?: RichParagraph.DefaultParagraphStyle
        }

    /**
     * Переключает [SpanStyle]
     * Если переданный строчный стиль отсутствует в [currentSpanStyle], он будет добавлен.
     * Если переданный строчный стиль уже присутствует в [currentSpanStyle], он будет удалён.
     *
     * Пример: Вы можете переключить жирное начертание (FontWeight.Bold), передав:
     *
     * ```
     * SpanStyle(fontWeight = FontWeight.Bold)
     * ```
     *
     * @param spanStyle Строчный стиль, который будет переключён.
     * Будут переключены только указанные параметры, а неуказанные будут проигнорированы.
     * @see [addSpanStyle]
     * @see [removeSpanStyle]
     */
    public fun toggleSpanStyle(spanStyle: SpanStyle) {
        if (currentSpanStyle.isSpecifiedFieldsEquals(spanStyle))
            removeSpanStyle(spanStyle)
        else
            addSpanStyle(spanStyle)
    }

    /**
     * Добавляет новый [SpanStyle] в [currentSpanStyle]
     *
     * Пример: Вы можете добавить жирное начертание (FontWeight.Bold), передав:
     *
     * ```
     * SpanStyle(fontWeight = FontWeight.Bold)
     * ```
     *
     * @param spanStyle Строчный стиль, который будет добавлен в [currentSpanStyle].
     * Будут применены только указанные параметры, а неуказанные будут проигнорированы.
     * @see [removeSpanStyle]
     * @see [toggleSpanStyle]
     */
    public fun addSpanStyle(spanStyle: SpanStyle) {
        if (!currentSpanStyle.isSpecifiedFieldsEquals(spanStyle)) {
            toAddSpanStyle = toAddSpanStyle.customMerge(spanStyle)
            toRemoveSpanStyle = toRemoveSpanStyle.unmerge(spanStyle)
        }

        if (!selection.collapsed)
            applyRichSpanStyleToSelectedText()
    }

    /**
     * Добавляет новый [SpanStyle] для указанного [TextRange]
     *
     * Пример: Вы можете добавить жирное начертание (FontWeight.Bold) в указанный диапазон, передав:
     *
     * ```
     * state.addSpanStyle(SpanStyle(fontWeight = FontWeight.Bold), TextRange(0, 5))
     * ```
     *
     * @param spanStyle Строчный стиль, который будет добавлен в богатый спан.
     * @param textRange Диапазон текста, к которому будет применён строчный стиль.
     */
    public fun addSpanStyle(spanStyle: SpanStyle, textRange: TextRange) {
        val oldToRemoveSpanStyle = toRemoveSpanStyle
        val oldToAddSpanStyle = toAddSpanStyle

        toAddSpanStyle = spanStyle
        toRemoveSpanStyle = SpanStyle()

        applyRichSpanStyleToTextRange(textRange)

        toRemoveSpanStyle = oldToRemoveSpanStyle
        toAddSpanStyle = oldToAddSpanStyle
    }

    /**
     * Удаляет существующий [SpanStyle] из [currentSpanStyle]
     *
     * Пример: Вы можете удалить жирное начертание (FontWeight.Bold), передав:
     *
     * ```
     * SpanStyle(fontWeight = FontWeight.Bold)
     * ```
     *
     * @param spanStyle Строчный стиль, который будет удалён из [currentSpanStyle].
     * Будут удалены только указанные параметры, а неуказанные будут проигнорированы.
     * @see [addSpanStyle]
     * @see [toggleSpanStyle]
     */
    public fun removeSpanStyle(spanStyle: SpanStyle) {
        if (currentSpanStyle.isSpecifiedFieldsEquals(spanStyle)) {
            toRemoveSpanStyle = toRemoveSpanStyle.customMerge(spanStyle)
            toAddSpanStyle = toAddSpanStyle.unmerge(spanStyle)
        }

        if (!selection.collapsed)
            applyRichSpanStyleToSelectedText()
    }

    /**
     * Удаляет существующий [SpanStyle] из указанного [TextRange]
     *
     * Пример: Вы можете удалить жирное начертание (FontWeight.Bold) из указанного диапазона, передав:
     *
     * ```
     * state.removeSpanStyle(SpanStyle(fontWeight = FontWeight.Bold), TextRange(0, 5))
     * ```
     *
     * @param spanStyle Строчный стиль, который будет удалён из богатого спана.
     * @param textRange Диапазон текста, из которого будет удалён строчный стиль.
     */
    public fun removeSpanStyle(spanStyle: SpanStyle, textRange: TextRange) {
        val oldToRemoveSpanStyle = toRemoveSpanStyle
        val oldToAddSpanStyle = toAddSpanStyle

        toRemoveSpanStyle = spanStyle
        toAddSpanStyle = SpanStyle()

        applyRichSpanStyleToTextRange(textRange)

        toRemoveSpanStyle = oldToRemoveSpanStyle
        toAddSpanStyle = oldToAddSpanStyle
    }

    /**
     * Clear all [SpanStyle]s.
     */
    public fun clearSpanStyles() {
        removeSpanStyle(currentSpanStyle)
    }

    /**
     * Clear all [SpanStyle]s from a specific [TextRange].
     */
    public fun clearSpanStyles(textRange: TextRange) {
        removeSpanStyle(currentSpanStyle, textRange)
    }

    // RichSpanStyle

    /**
     * Добавляет ссылку в текстовое поле.
     * Ссылка будет добавлена после текущего выделения.
     *
     * @param text Текст ссылки.
     * @param url URL ссылки.
     */
    public fun addLink(
        text: String,
        url: String,
    ) {
        if (text.isEmpty()) return

        val paragraph = richParagraphList.firstOrNull() ?: return
        val linkStyle = RichSpanStyle.Link(
            url = url,
        )
        val linkRichSpan = RichSpan(
            text = text,
            richSpanStyle = linkStyle,
            paragraph = paragraph,
        )

        addRichSpanAtPosition(
            linkRichSpan,
            index = selection.min,
        )

        val beforeText = textFieldValue.text.substring(0, selection.min)
        val afterText = textFieldValue.text.substring(selection.min)
        val newText = "$beforeText$text$afterText"
        updateTextFieldValue(
            newTextFieldValue = textFieldValue.copy(
                text = newText,
                selection = TextRange(selection.min + text.length),
            )
        )
    }

    /**
     * Добавляет ссылку в выделенный текст.
     *
     * @param url URL ссылки.
     */
    public fun addLinkToSelection(
        url: String,
    ) {
        if (selection.collapsed) return

        val linkStyle = RichSpanStyle.Link(
            url = url,
        )

        toAddRichSpanStyle = linkStyle
        toRemoveRichSpanStyleKClass = RichSpanStyle.Default::class

        addRichSpan(
            spanStyle = linkStyle
        )
    }

    /**
     * Добавляет ссылку в указанный [TextRange].
     *
     * @param url URL ссылки.
     * @param textRange Диапазон текста, в который будет добавлена ссылка.
     */
    public fun addLinkToTextRange(
        url: String,
        textRange: TextRange,
    ) {
        if (textRange.collapsed) return

        val linkStyle = RichSpanStyle.Link(
            url = url,
        )

        toAddRichSpanStyle = linkStyle
        toRemoveRichSpanStyleKClass = RichSpanStyle.Default::class

        addRichSpan(
            spanStyle = linkStyle,
            textRange = textRange
        )
    }

    /**
     * Обновляет ссылку выделенного текста.
     *
     * @param url Новый URL ссылки.
     */
    public fun updateLink(
        url: String,
    ) {
        if (!isLink) return

        val linkStyle = RichSpanStyle.Link(
            url = url,
        )

        val richSpan = getSelectedLinkRichSpan() ?: return

        richSpan.richSpanStyle = linkStyle

        updateTextFieldValue(textFieldValue)
    }

    /**
     * Удаляет ссылку из выделенного текста.
     */
    public fun removeLink() {
        if (!isLink) return

        val richSpan = getSelectedLinkRichSpan() ?: return

        richSpan.richSpanStyle = RichSpanStyle.Default

        updateTextFieldValue(textFieldValue)
    }

    @Deprecated(
        message = "Use toggleCodeSpan instead",
        replaceWith = ReplaceWith("toggleCodeSpan()"),
        level = DeprecationLevel.ERROR,
    )
    public fun toggleCode(): Unit = toggleCodeSpan()

    public fun toggleCodeSpan(): Unit = toggleRichSpan(RichSpanStyle.Code())

    @Deprecated(
        message = "Use addCodeSpan instead",
        replaceWith = ReplaceWith("addCodeSpan()"),
        level = DeprecationLevel.ERROR,
    )
    public fun addCode(): Unit = addCodeSpan()

    public fun addCodeSpan(): Unit = addRichSpan(RichSpanStyle.Code())

    @Deprecated(
        message = "Use removeCodeSpan instead",
        replaceWith = ReplaceWith("removeCodeSpan()"),
        level = DeprecationLevel.ERROR,
    )
    public fun removeCode(): Unit = removeCodeSpan()

    public fun removeCodeSpan(): Unit = removeRichSpan(RichSpanStyle.Code())

    public fun toggleRichSpan(spanStyle: RichSpanStyle) {
        if (isRichSpan(spanStyle::class))
            removeRichSpan(spanStyle)
        else
            addRichSpan(spanStyle)
    }

    /**
     * Добавляет новый [RichSpanStyle] в выделенный текст или в текст,
     * который будет набран, если выделение свёрнуто (collapsed).
     *
     * @param spanStyle Богатый строчный стиль, который будет добавлен.
     */
    public fun addRichSpan(spanStyle: RichSpanStyle) {
        if (toRemoveRichSpanStyleKClass == spanStyle::class)
            toRemoveRichSpanStyleKClass = RichSpanStyle.Default::class
        toAddRichSpanStyle = spanStyle

        if (!selection.collapsed)
            applyRichSpanStyleToSelectedText()
    }

    /**
     * Добавляет новый [RichSpanStyle] в указанный [TextRange].
     *
     * @param spanStyle Богатый строчный стиль, который будет добавлен.
     * @param textRange Диапазон текста, к которому будет применён богатый строчный стиль.
     */
    public fun addRichSpan(
        spanStyle: RichSpanStyle,
        textRange: TextRange,
    ) {
        if (textRange.collapsed)
            return

        if (toRemoveRichSpanStyleKClass == spanStyle::class)
            toRemoveRichSpanStyleKClass = RichSpanStyle.Default::class
        toAddRichSpanStyle = spanStyle

        applyRichSpanStyleToTextRange(textRange)
    }

    /**
     * Удаляет существующий [RichSpanStyle] из выделенного текста или из текста,
     * который будет набран, если выделение свёрнуто (collapsed).
     *
     * @param spanStyle Богатый строчный стиль, который будет удалён.
     */
    public fun removeRichSpan(spanStyle: RichSpanStyle) {
        if (toAddRichSpanStyle::class == spanStyle::class)
            toAddRichSpanStyle = RichSpanStyle.Default
        toRemoveRichSpanStyleKClass = spanStyle::class

        if (!selection.collapsed)
            applyRichSpanStyleToSelectedText()
    }

    /**
     * Удаляет существующий [RichSpanStyle] из указанного [TextRange].
     *
     * @param spanStyle Богатый строчный стиль, который будет удалён.
     * @param textRange Диапазон текста, из которого будет удалён богатый строчный стиль.
     */
    public fun removeRichSpan(
        spanStyle: RichSpanStyle,
        textRange: TextRange
    ) {
        if (textRange.collapsed)
            return

        if (toAddRichSpanStyle::class == spanStyle::class)
            toAddRichSpanStyle = RichSpanStyle.Default
        toRemoveRichSpanStyleKClass = spanStyle::class

        applyRichSpanStyleToTextRange(textRange)
    }

    /**
     * Clear all [RichSpanStyle]s.
     */
    public fun clearRichSpans() {
        removeRichSpan(currentRichSpanStyle)
    }

    /**
     * Очищает все [RichSpanStyle] в указанном [TextRange].
     */
    public fun clearRichSpans(textRange: TextRange) {
        removeRichSpan(currentRichSpanStyle, textRange)
    }

    /**
     * Переключает [ParagraphStyle]
     * Если переданный стиль абзаца отсутствует в [currentParagraphStyle], он будет добавлен.
     * Если переданный стиль абзаца уже присутствует в [currentParagraphStyle], он будет удалён.
     *
     * Пример: Вы можете переключить выравнивание по центру, передав:
     *
     * ```
     * ParagraphStyle(textAlign = TextAlign.Center)
     * ```
     *
     * @param paragraphStyle Стиль абзаца, который будет переключён.
     * Будут переключены только указанные параметры, а неуказанные будут проигнорированы.
     * @see [addParagraphStyle]
     * @see [removeParagraphStyle]
     */
    public fun toggleParagraphStyle(paragraphStyle: ParagraphStyle) {
        if (currentParagraphStyle.isSpecifiedFieldsEquals(paragraphStyle))
            removeParagraphStyle(paragraphStyle)
        else
            addParagraphStyle(paragraphStyle)
    }

    /**
     * Добавляет новый [ParagraphStyle] в [currentParagraphStyle]
     *
     * Пример: Вы можете добавить выравнивание по центру, передав:
     *
     * ```
     * ParagraphStyle(textAlign = TextAlign.Center)
     * ```
     *
     * @param paragraphStyle Стиль абзаца, который будет добавлен в [currentParagraphStyle].
     * Будут применены только указанные параметры, а неуказанные будут проигнорированы.
     * @see [removeParagraphStyle]
     * @see [toggleParagraphStyle]
     */
    public fun addParagraphStyle(paragraphStyle: ParagraphStyle) {
        if (!currentParagraphStyle.isSpecifiedFieldsEquals(paragraphStyle)) {
            // If the selection is collapsed, we add the paragraph style to the paragraph containing the selection
            if (selection.collapsed) {
                val paragraph = getRichParagraphByTextIndex(selection.min - 1) ?: return
                paragraph.paragraphStyle = paragraph.paragraphStyle.merge(paragraphStyle)
            }
            // If the selection is not collapsed, we add the paragraph style to all the paragraphs in the selection
            else {
                val paragraphs = getRichParagraphListByTextRange(selection)
                if (paragraphs.isEmpty()) return
                paragraphs.fastForEach {
                    it.paragraphStyle = it.paragraphStyle.merge(paragraphStyle)
                }
            }
            // We update the annotated string to reflect the changes
            updateAnnotatedString()
            // We update the current paragraph style to reflect the changes
            updateCurrentParagraphStyle()
        }
    }

    /**
     * Удаляет существующий [ParagraphStyle] из [currentParagraphStyle]
     *
     * Пример: Вы можете удалить выравнивание по центру, передав:
     *
     * ```
     * ParagraphStyle(textAlign = TextAlign.Center)
     * ```
     *
     * @param paragraphStyle Стиль абзаца, который будет удалён из [currentParagraphStyle].
     * Будут удалены только указанные параметры, а неуказанные будут проигнорированы.
     * @see [addParagraphStyle]
     * @see [toggleParagraphStyle]
     */
    public fun removeParagraphStyle(paragraphStyle: ParagraphStyle) {
        if (currentParagraphStyle.isSpecifiedFieldsEquals(paragraphStyle)) {
            // If the selection is collapsed, we remove the paragraph style from the paragraph containing the selection
            if (selection.collapsed) {
                val paragraph = getRichParagraphByTextIndex(selection.min - 1) ?: return
                paragraph.paragraphStyle = paragraph.paragraphStyle.unmerge(paragraphStyle)
            }
            // If the selection is not collapsed, we remove the paragraph style from all the paragraphs in the selection
            else {
                val paragraphs = getRichParagraphListByTextRange(selection)
                if (paragraphs.isEmpty()) return
                paragraphs.fastForEach {
                    it.paragraphStyle = it.paragraphStyle.unmerge(paragraphStyle)
                }
            }
            // We update the annotated string to reflect the changes
            updateAnnotatedString()
            // We update the current paragraph style to reflect the changes
            updateCurrentParagraphStyle()
        }
    }

    public fun toggleUnorderedList() {
        val paragraphs = getRichParagraphListByTextRange(selection)
        if (paragraphs.isEmpty())
            return
        val isFirstParagraphUnorderedList = paragraphs.first().type is UnorderedList
        paragraphs.fastForEach { paragraph ->
            if (isFirstParagraphUnorderedList)
                removeUnorderedList(paragraph)
            else
                addUnorderedList(paragraph)
        }
    }

    public fun addUnorderedList() {
        val paragraphs = getRichParagraphListByTextRange(selection)

        paragraphs.fastForEach { paragraph ->
            addUnorderedList(paragraph)
        }
    }

    public fun removeUnorderedList() {
        val paragraphs = getRichParagraphListByTextRange(selection)

        paragraphs.fastForEach { paragraph ->
            removeUnorderedList(paragraph)
        }
    }

    /**
     * Переключает тип заголовка для текущего выделения.
     *
     * @param level Уровень заголовка.
     */
    public fun toggleHeading(level: Int) {
        if (singleParagraphMode) return

        val selectedRange = selection
        val paragraphs = getRichParagraphListByTextRange(selectedRange)
        if (paragraphs.isEmpty()) return

        val isFirstParagraphMatchingHeading =
            paragraphs.first().type is Heading && (paragraphs.first().type as Heading).level == level

        if (selectedRange.collapsed) {
            applyHeadingLogic(paragraphs, level, isFirstParagraphMatchingHeading)
            return
        }

        if (!isFirstParagraphMatchingHeading && paragraphs.size == 1) {
            val paragraph = paragraphs.first()
            val paragraphRange = paragraph.getTextRange()

            if (selectedRange.min > paragraphRange.min || selectedRange.max < paragraphRange.max) {
                var newText = textFieldValue.text
                var startShift = 0

                // (не на самой границе paragraphRange.max), иначе лишний \n съедает символ.
                if (selectedRange.max < paragraphRange.max) {
                    val maxIdx = selectedRange.max.coerceAtMost(newText.length)
                    //newText = newText.substring(0, maxIdx) + "\n" + newText.substring(maxIdx)
                    insertParagraphs(
                        listOf(RichParagraph(), RichParagraph()),
                        maxIdx
                    )
                }

                // 2. Затем обрабатываем НАЧАЛО
                if (selectedRange.min > paragraphRange.min) {
                    val minIdx = selectedRange.min.coerceAtMost(newText.length)
//                    newText = newText.substring(0, minIdx) + "\n" + newText.substring(minIdx)
                    startShift++

                    insertParagraphs(
                        listOf(RichParagraph(), RichParagraph()),
                        minIdx
                    )
                }

                // Корректируем выделение с учётом обоих сдвигов
                val newSelection = TextRange(
                    selectedRange.min + startShift,
                    selectedRange.max + startShift
                )

                // 3. Обновляем текст и состояние параграфов
                updateTextFieldValue(textFieldValue.copy(/*text = newText,*/ selection = newSelection))

                // 4. После updateTextFieldValue список параграфов изменился.
                // Получаем актуальный список для нового выделения.
                val updatedParagraphs = getRichParagraphListByTextRange(newSelection)

                // Применяем заголовок к новым параграфам напрямую
                applyHeadingLogic(updatedParagraphs, level, false)
                return
            }
        }

        // Обычный случай (выделение уже охватывает целые параграфы или их несколько)
        applyHeadingLogic(paragraphs, level, isFirstParagraphMatchingHeading)
    }

    private fun getHead(level: Int): SpanStyle = when (level) {
        1 -> com.mohamedrejeb.richeditor.parser.utils.H1SpanStyle
        2 -> com.mohamedrejeb.richeditor.parser.utils.H2SpanStyle
        3 -> com.mohamedrejeb.richeditor.parser.utils.H3SpanStyle
        4 -> com.mohamedrejeb.richeditor.parser.utils.H4SpanStyle
        5 -> com.mohamedrejeb.richeditor.parser.utils.H5SpanStyle
        6 -> com.mohamedrejeb.richeditor.parser.utils.H6SpanStyle
        else -> SpanStyle()
    }


    /**
     * Вспомогательный метод, чтобы не дублировать код и не вызывать рекурсию
     */
    private fun applyHeadingLogic(paragraphs: List<RichParagraph>, level: Int, isOff: Boolean) {
        val headingStyle = getHead(level)

        paragraphs.fastForEach { paragraph ->
            val currentType = paragraph.type
            val paragraphRange = paragraph.getTextRange()

            if (isOff) {
                resetParagraphType(paragraph)
                removeSpanStyle(headingStyle, paragraphRange)
            } else {
                if (currentType is Heading && currentType.level != level) {
                    val oldStyle = getHead(currentType.level)
                    removeSpanStyle(oldStyle, paragraphRange)
                }

                updateParagraphType(paragraph, Heading(level))
                addSpanStyle(headingStyle, paragraphRange)
            }
        }
        updateAnnotatedString()
    }

    public fun toggleOrderedList() {
        val paragraphs = getRichParagraphListByTextRange(selection)
        if (paragraphs.isEmpty())
            return
        val isFirstParagraphOrderedList = paragraphs.first().type is OrderedList
        paragraphs.fastForEach { paragraph ->
            if (isFirstParagraphOrderedList) {
                removeOrderedList(paragraph)
            } else {
                addOrderedList(paragraph)
            }
        }
    }

    public fun addOrderedList() {
        val paragraphs = getRichParagraphListByTextRange(selection)

        paragraphs.fastForEach { paragraph ->
            addOrderedList(paragraph)
        }
    }

    public fun removeOrderedList() {
        val paragraphs = getRichParagraphListByTextRange(selection)

        paragraphs.fastForEach { paragraph ->
            removeOrderedList(paragraph)
        }
    }

    /**
     * Increase the level of the current selected lists.
     *
     * If the current selection is not a list, this method does nothing.
     *
     * If multiple paragraphs are selected, they all must be lists.
     */
    public fun increaseListLevel() {
        if (!isList)
            return

        val paragraphs = getRichParagraphListByTextRange(selection)

        if (paragraphs.isEmpty())
            return

        if (!canIncreaseListLevel(paragraphs))
            return

        // Increase list level
        val levelNumberMap = mutableMapOf<Int, Int>()
        var minParagraphLevel = Int.MAX_VALUE
        var minParagraphLevelOrderedListNumber = -1
        var startParagraphIndex = -1
        var startParagraphLevel = -1
        var endParagraphIndex = -1
        var processedParagraphCount = 0

        val firstSelectedParagraph = paragraphs.first()

        for (i in richParagraphList.indices) {
            val paragraph = richParagraphList[i]
            val type = paragraph.type

            // Skip paragraphs before the selected paragraphs
            if (startParagraphIndex == -1) {
                if (paragraph == firstSelectedParagraph) {
                    startParagraphIndex = i
                    startParagraphLevel =
                        if (type is ConfigurableListLevel)
                            type.level
                        else
                            0
                } else {
                    if (type is ConfigurableListLevel) {
                        levelNumberMap.keys.toList().fastForEach { level ->
                            if (level > type.level)
                                levelNumberMap.remove(level)
                        }

                        if (type is OrderedList)
                            levelNumberMap[type.level] = type.number

                        if (type is UnorderedList)
                            levelNumberMap.remove(type.level)
                    } else {
                        levelNumberMap.clear()
                    }

                    continue
                }
            }

            if (processedParagraphCount >= paragraphs.size) {
                if (
                    type !is ConfigurableListLevel ||
                    type.level <= minParagraphLevel
                ) {
                    endParagraphIndex = i - 1
                    break
                }
            }

            if (type is ConfigurableListLevel) {
                if (type.level <= minParagraphLevel) {
                    minParagraphLevel = type.level
                    minParagraphLevelOrderedListNumber =
                        if (type is OrderedList)
                            type.number - 1
                        else
                            -1
                }

                type.level++
            } else {
                if (minParagraphLevel != Int.MAX_VALUE && minParagraphLevelOrderedListNumber != -1)
                    levelNumberMap[minParagraphLevel] = minParagraphLevelOrderedListNumber

                minParagraphLevel = Int.MAX_VALUE
                minParagraphLevelOrderedListNumber = -1
            }

            processedParagraphCount++
        }

        if (minParagraphLevel != Int.MAX_VALUE && minParagraphLevelOrderedListNumber != -1)
            levelNumberMap[minParagraphLevel] = minParagraphLevelOrderedListNumber

        // Adjust ordered list numbers
        val newTextFieldValue = adjustOrderedListsNumbers(
            startParagraphIndex = startParagraphIndex,
            startNumber = levelNumberMap[startParagraphLevel + 1]?.plus(1) ?: 1,
            textFieldValue = textFieldValue,
            initialLevelNumberMap = levelNumberMap,
        )

        updateTextFieldValue(
            newTextFieldValue = newTextFieldValue,
        )
    }

    /**
     * Уменьшает уровень текущих выбранных списков.
     *
     * Если текущее выделение не является списком, этот метод ничего не делает.
     *
     * Если выбрано несколько абзацев, все они должны быть списками.
     */
    public fun decreaseListLevel() {
        if (!isList)
            return

        val paragraphs = getRichParagraphListByTextRange(selection)

        if (paragraphs.isEmpty())
            return

        if (!canDecreaseListLevel(paragraphs))
            return

        // Decrease list level
        val levelNumberMap = mutableMapOf<Int, Int>()
        var minParagraphLevel = Int.MAX_VALUE
        var minParagraphLevelOrderedListNumber = -1
        var startParagraphIndex = -1
        var endParagraphIndex = -1
        var startParagraphLevel = -1
        var processedParagraphCount = 0

        val firstSelectedParagraph = paragraphs.first()

        for (i in richParagraphList.indices) {
            val paragraph = richParagraphList[i]
            val type = paragraph.type

            // Skip paragraphs before the selected paragraphs
            if (startParagraphIndex == -1) {
                if (paragraph == firstSelectedParagraph) {
                    startParagraphIndex = i
                    startParagraphLevel =
                        if (type is ConfigurableListLevel)
                            type.level
                        else
                            0
                } else {
                    if (type is ConfigurableListLevel) {
                        levelNumberMap.keys.toList().fastForEach { level ->
                            if (level > type.level)
                                levelNumberMap.remove(level)
                        }

                        if (type is OrderedList)
                            levelNumberMap[type.level] = type.number

                        if (type is UnorderedList)
                            levelNumberMap.remove(type.level)
                    } else {
                        levelNumberMap.clear()
                    }

                    continue
                }
            }

            if (processedParagraphCount >= paragraphs.size) {
                if (
                    type !is ConfigurableListLevel ||
                    type.level <= minParagraphLevel
                ) {
                    endParagraphIndex = i - 1
                    break
                }
            }

            if (type is ConfigurableListLevel) {
                if (type.level <= minParagraphLevel) {
                    minParagraphLevel = type.level
                    minParagraphLevelOrderedListNumber =
                        if (type is OrderedList)
                            type.number - 1
                        else
                            -1
                }

                type.level = (type.level - 1).coerceAtLeast(1)
            } else {
                minParagraphLevel = Int.MAX_VALUE
                minParagraphLevelOrderedListNumber = -1
            }

            processedParagraphCount++
        }

        // Adjust ordered list numbers
        val newTextFieldValue = adjustOrderedListsNumbers(
            startParagraphIndex = startParagraphIndex,
            startNumber = levelNumberMap[startParagraphLevel - 1]?.plus(1) ?: 1,
            textFieldValue = textFieldValue,
            initialLevelNumberMap = levelNumberMap,
        )

        updateTextFieldValue(
            newTextFieldValue = newTextFieldValue,
        )
    }

    /**
     * Private/Internal methods
     */

    /**
     * Возвращает [ParagraphType] текста в указанном диапазоне.
     * Если диапазон текста свёрнут (collapsed), возвращается тип абзаца, содержащего этот диапазон.
     *
     * @param textRange Диапазон текста.
     * @return [ParagraphType] текста в указанном диапазоне.
     */
    internal fun getParagraphType(textRange: TextRange): ParagraphType =
        if (textRange.collapsed) {
            val richParagraph = getRichParagraphByTextIndex(textIndex = textRange.min - 1)

            richParagraph
                ?.type
                ?: DefaultParagraph()
        } else {
            val richParagraphList = getRichParagraphListByTextRange(textRange)

            richParagraphList
                .getCommonType()
                ?: DefaultParagraph()
        }

    private fun getSelectedLinkRichSpan(): RichSpan? {
        val richSpan = getRichSpanByTextIndex(selection.min - 1)

        return getLinkRichSpan(richSpan)
    }

    private fun addUnorderedList(paragraph: RichParagraph) {
        val paragraphType = paragraph.type
        if (paragraphType is UnorderedList)
            return

        val index = richParagraphList.indexOf(paragraph)

        if (index == -1)
            return

        val listLevel =
            if (paragraphType is ConfigurableListLevel)
                paragraphType.level
            else
                1

        val newType = UnorderedList(
            config = config,
            initialLevel = listLevel,
        )

        val newTextFieldValue = adjustOrderedListsNumbers(
            startParagraphIndex = index,
            startNumber = 1,
            textFieldValue = updateParagraphType(
                paragraph = paragraph,
                newType = newType,
                textFieldValue = textFieldValue,
            ),
        )

        updateTextFieldValue(
            newTextFieldValue = newTextFieldValue
        )
    }

    private fun removeUnorderedList(paragraph: RichParagraph) {
        if (paragraph.type !is UnorderedList)
            return

        resetParagraphType(paragraph = paragraph)
    }

    private fun addOrderedList(paragraph: RichParagraph) {
        val paragraphType = paragraph.type

        if (paragraphType is OrderedList)
            return

        val index = richParagraphList.indexOf(paragraph)

        if (index == -1)
            return

        var orderedListNumber = 1

        val listLevel =
            if (paragraphType is ConfigurableListLevel)
                paragraphType.level
            else
                1

        for (i in index - 1 downTo 0) {
            val prevParagraph = richParagraphList[i]
            val prevParagraphType = prevParagraph.type

            if (prevParagraphType is ConfigurableListLevel && prevParagraphType.level < listLevel)
                break

            if (prevParagraphType is ConfigurableListLevel && prevParagraphType !is OrderedList)
                continue

            if (prevParagraphType !is OrderedList)
                break

            if (prevParagraphType.level > listLevel)
                continue

            orderedListNumber = prevParagraphType.number + 1

            break
        }

        val newType = OrderedList(
            number = orderedListNumber,
            config = config,
            initialLevel = listLevel,
        )

        val newTextFieldValue = adjustOrderedListsNumbers(
            startParagraphIndex = index,
            startNumber = orderedListNumber,
            textFieldValue = updateParagraphType(
                paragraph = paragraph,
                newType = newType,
                textFieldValue = textFieldValue,
            ),
        )

        updateTextFieldValue(
            newTextFieldValue = newTextFieldValue,
        )
    }

    private fun removeOrderedList(paragraph: RichParagraph) {
        if (paragraph.type !is OrderedList) return
        val index = richParagraphList.indexOf(paragraph)
        if (index == -1) return

        for (i in (index + 1)..richParagraphList.lastIndex) {
            val currentParagraphType = richParagraphList[i].type
            if (currentParagraphType !is OrderedList) break
            currentParagraphType.number = i - index
        }

        resetParagraphType(paragraph = paragraph)
    }

    /**
     * Увеличивает или уменьшает уровень списка для текущих выбранных списков при нажатии клавиши Tab.
     *
     * @param event Событие клавиши.
     * @return true, если уровень списка был увеличен или уменьшен, false в противном случае.
     */
    internal fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown)
            return false

        if (event.key != Key.Tab)
            return false

        if (
            event.isMetaPressed ||
            event.isCtrlPressed ||
            event.isAltPressed
        )
            return false

        if (!isList)
            return false

        if (event.isShiftPressed && canDecreaseListLevel())
            decreaseListLevel()
        else if (!event.isShiftPressed && canIncreaseListLevel())
            increaseListLevel()
        else
            return false

        return true
    }

    /**
     * Проверяет, можно ли увеличить уровень списка или нет.
     *
     * @param paragraphs Список абзацев для проверки.
     * @return true, если уровень списка можно увеличить, false в противном случае.
     */
    internal fun canIncreaseListLevel(
        paragraphs: List<RichParagraph> = getRichParagraphListByTextRange(selection),
    ): Boolean {
        if (paragraphs.isEmpty())
            return false

        val firstParagraph = paragraphs.first()
        val firstParagraphType = firstParagraph.type
        val firstParagraphIndex = richParagraphList.indexOf(firstParagraph)

        if (firstParagraphIndex == -1 || firstParagraphType !is ConfigurableListLevel)
            return false

        val previousParagraph = richParagraphList.getOrNull(firstParagraphIndex - 1)
        val previousParagraphType = previousParagraph?.type

        // The previous paragraph must be a list, otherwise we can't increase the list level
        if (previousParagraph == null || previousParagraphType !is ConfigurableListLevel)
            return false

        // The first paragraph must have the same or lower list level than the previous one
        if (firstParagraphType.level > previousParagraphType.level)
            return false

        paragraphs.fastForEach { paragraph ->
            val paragraphType = paragraph.type

            // All paragraphs must be ConfigurableListLevel
            if (paragraphType !is ConfigurableListLevel)
                return false

            // TODO: Maybe in the future we can remove this condition
            // The paragraph must have the same or higher list level than the first paragraph
            if (paragraphType.level < firstParagraphType.level)
                return false
        }

        return true
    }

    /**
     * Проверяет, можно ли уменьшить уровень списка или нет.
     *
     * @param paragraphs Список абзацев для проверки.
     * @return true, если уровень списка можно уменьшить, false в противном случае.
     */
    internal fun canDecreaseListLevel(
        paragraphs: List<RichParagraph> = getRichParagraphListByTextRange(selection),
    ): Boolean {
        if (paragraphs.isEmpty())
            return false

        paragraphs.fastForEach { paragraph ->
            val paragraphType = paragraph.type

            // All paragraphs must be ConfigurableListLevel
            if (paragraphType !is ConfigurableListLevel)
                return false

            // The paragraph list level must be at least 2
            if (paragraphType.level < 2)
                return false
        }

        return true
    }

    private fun updateParagraphType(
        paragraph: RichParagraph,
        newType: ParagraphType,
    ) {
        updateTextFieldValue(
            newTextFieldValue = updateParagraphType(
                paragraph = paragraph,
                newType = newType,
                textFieldValue = this.textFieldValue,
            )
        )
    }

    private fun updateParagraphType(
        paragraph: RichParagraph,
        newType: ParagraphType,
        textFieldValue: TextFieldValue,
    ): TextFieldValue {
        val selection = textFieldValue.selection
        val paragraphOldStartTextLength = paragraph.type.startText.length
        val textFieldValueDiff = this.textFieldValue.text.length - textFieldValue.text.length
        val firstNonEmptyChildIndex = paragraph.getFirstNonEmptyChild()?.textRange?.min?.let {
            if (it >= selection.min)
                it - textFieldValueDiff
            else
                it
        }
        val paragraphFirstChildStartIndex =
            (firstNonEmptyChildIndex ?: selection.min).coerceAtLeast(0)

        paragraph.type = newType

        // If the paragraph type start text length didn't change, we don't need to update the text field value
        if (paragraphOldStartTextLength == newType.startText.length)
            return textFieldValue

        val beforeText = textFieldValue.text.substring(
            0,
            paragraphFirstChildStartIndex - paragraphOldStartTextLength
        )
        val afterText = textFieldValue.text.substring(paragraphFirstChildStartIndex)

        val newSelectionMin =
            if (selection.min > paragraphFirstChildStartIndex) selection.min + newType.startText.length - paragraphOldStartTextLength
            else if (selection.min == paragraphFirstChildStartIndex) paragraphFirstChildStartIndex + newType.startText.length - paragraphOldStartTextLength
            else selection.min
        val newSelectionMax =
            if (selection.max > paragraphFirstChildStartIndex) selection.max + newType.startText.length - paragraphOldStartTextLength
            else if (selection.max == paragraphFirstChildStartIndex) paragraphFirstChildStartIndex + newType.startText.length - paragraphOldStartTextLength
            else selection.max

        return textFieldValue.copy(
            text = beforeText + newType.startText + afterText,
            selection = TextRange(
                newSelectionMin,
                newSelectionMax,
            ),
        )
    }

    private fun resetParagraphType(paragraph: RichParagraph) {
        updateParagraphType(paragraph, DefaultParagraph())
    }

    /**
     * Временно сохраняет новое значение текстового поля до его проверки.
     */
    private var tempTextFieldValue = textFieldValue

    /**
     * Обрабатывает новое значение текстового поля.
     *
     * @param newTextFieldValue Новое значение текстового поля.
     */
    public fun onTextFieldValueChange(newTextFieldValue: TextFieldValue) {
        tempTextFieldValue = newTextFieldValue

        if (tempTextFieldValue.text.length > textFieldValue.text.length)
            handleAddingCharacters()
        else if (tempTextFieldValue.text.length < textFieldValue.text.length)
            handleRemovingCharacters()
        else if (
            tempTextFieldValue.text == textFieldValue.text &&
            tempTextFieldValue.selection != textFieldValue.selection
        ) {
            val lastPressPosition = this.lastPressPosition
            if (lastPressPosition != null) {
                adjustSelection(lastPressPosition, newTextFieldValue.selection)
                return
            }
        }

        // Update text field value
        updateTextFieldValue()
    }

    /**
     * Handles updating the text field value and all the related states such as the [annotatedString] and [visualTransformation] to reflect the new text field value.
     *
     * @param newTextFieldValue the new text field value.
     */
    private fun updateTextFieldValue(newTextFieldValue: TextFieldValue = tempTextFieldValue) {
        tempTextFieldValue = newTextFieldValue

        if (!newTextFieldValue.selection.collapsed) {
            lastNonCollapsedSelection = newTextFieldValue.selection
        }

        if (!singleParagraphMode) {
            // Check for paragraphs
            checkForParagraphs()
        }

        if (
            tempTextFieldValue.text == textFieldValue.text &&
            tempTextFieldValue.selection != textFieldValue.selection
        ) {
            // Update selection
            textFieldValue = tempTextFieldValue
        } else {
            // Update the annotatedString and the textFieldValue with the new values
            updateAnnotatedString(tempTextFieldValue)
        }

        // Clear un-applied styles
        toAddSpanStyle = SpanStyle()
        toRemoveSpanStyle = SpanStyle()
        toAddRichSpanStyle = RichSpanStyle.Default
        toRemoveRichSpanStyleKClass = RichSpanStyle.Default::class

        // Update current span style
        updateCurrentSpanStyle()

        // Update current paragraph style
        updateCurrentParagraphStyle()

        // Clear [tempTextFieldValue]
        tempTextFieldValue = TextFieldValue()
    }

    /**
     * Обновляет [annotatedString] в соответствии с изменениями в [richParagraphList].
     * Этот метод обновит [annotatedString] и [textFieldValue] в соответствии с изменениями.
     * Если [newTextFieldValue] не передан, вместо него будет использоваться [textFieldValue].
     *
     * @param newTextFieldValue Новое значение текстового поля.
     * @see [textFieldValue]
     * @see [annotatedString]
     */
    internal fun updateAnnotatedString(newTextFieldValue: TextFieldValue = textFieldValue) {
        val newText =
            if (singleParagraphMode)
                newTextFieldValue.text
            else
                newTextFieldValue.text.replace('\n', ' ')

        val newStyledRichSpanList = mutableListOf<RichSpan>()

        usedInlineContentMapKeys.clear()

        annotatedString = buildAnnotatedString {
            var index = 0
            richParagraphList.fastForEachIndexed { i, richParagraph ->
                if (index > newText.length) {
                    richParagraphList.removeAt(i)
                    return@fastForEachIndexed
                }

                withStyle(richParagraph.paragraphStyle.merge(richParagraph.type.getStyle(config))) {
                    withStyle(
                        richParagraph.getStartTextSpanStyle() ?: RichSpanStyle.DefaultSpanStyle
                    ) {
                        append(richParagraph.type.startText)
                    }
                    val richParagraphStartTextLength = richParagraph.type.startText.length
                    richParagraph.type.startRichSpan.textRange =
                        TextRange(index, index + richParagraphStartTextLength)
                    index += richParagraphStartTextLength
                    withStyle(RichSpanStyle.DefaultSpanStyle) {
                        index = append(
                            state = this@RichTextState,
                            richSpanList = richParagraph.children,
                            startIndex = index,
                            text = newText,
                            selection = newTextFieldValue.selection,
                            onStyledRichSpan = {
                                newStyledRichSpanList.add(it)
                            },
                        )

                        if (!singleParagraphMode) {
                            // Add empty space in the end of each paragraph to fix an issue with Compose TextField
                            // that makes that last char non-selectable when having multiple paragraphs
                            if (i != richParagraphList.lastIndex && index < newText.length) {
                                append(' ')
                                index++
                            }
                        }
                    }
                }
            }
        }

        inlineContentMap.keys.forEach { key ->
            if (key !in usedInlineContentMapKeys) {
                inlineContentMap.remove(key)
            }
        }

        styledRichSpanList.clear()
        textFieldValue = newTextFieldValue.copy(text = annotatedString.text)
        visualTransformation = VisualTransformation { _ ->
            TransformedText(
                text = annotatedString,
                offsetMapping = OffsetMapping.Identity
            )
        }
        styledRichSpanList.addAll(newStyledRichSpanList)
    }

    /**
     * Обрабатывает добавление символов в текстовое поле.
     * Этот метод обновит [richParagraphList] в соответствии с изменениями.
     * Этот метод использует [tempTextFieldValue] для получения новых символов.
     */
    private fun handleAddingCharacters() {
        val typedCharsCount = tempTextFieldValue.text.length - textFieldValue.text.length
        var startTypeIndex = textFieldValue.selection.min
        val typedText = tempTextFieldValue.text.substring(
            startIndex = startTypeIndex,
            endIndex = startTypeIndex + typedCharsCount,
        )

        if (typedText == "\n") {
            // Принудительно очищаем composition в TextFieldValue перед тем,
            // как позволить параграфам перестроиться
            tempTextFieldValue = tempTextFieldValue.copy(composition = null)
        }

        val previousIndex = startTypeIndex - 1

        val activeRichSpan = getOrCreateRichSpanByTextIndex(previousIndex)

        if (activeRichSpan != null) {
            val isAndroidSuggestion =
                activeRichSpan.isLastInParagraph &&
                        activeRichSpan.textRange.max == startTypeIndex &&
                        tempTextFieldValue.selection.max == startTypeIndex + typedCharsCount + 1

            val typedText =
                if (isAndroidSuggestion)
                    "$typedText "
                else
                    typedText

            if (isAndroidSuggestion) {
                val beforeText =
                    tempTextFieldValue.text.substring(0, startTypeIndex + typedCharsCount)

                val afterText =
                    tempTextFieldValue.text.substring(startTypeIndex + typedCharsCount)

                tempTextFieldValue = tempTextFieldValue.copy(
                    text = "$beforeText $afterText",
                )
            }

            if (startTypeIndex < activeRichSpan.textRange.min) {
                val indexDiff = activeRichSpan.textRange.min - startTypeIndex
                val beforeTypedText = tempTextFieldValue.text.substring(
                    startIndex = 0,
                    endIndex = startTypeIndex,
                )
                val paragraphStartText = tempTextFieldValue.text.substring(
                    startIndex = startTypeIndex + typedCharsCount,
                    endIndex = activeRichSpan.textRange.min + typedCharsCount,
                )
                val afterTypedText = tempTextFieldValue.text.substring(
                    startIndex = activeRichSpan.textRange.min + typedCharsCount,
                    endIndex = tempTextFieldValue.text.length,
                )
                val newTypedText = beforeTypedText + paragraphStartText + typedText + afterTypedText
                tempTextFieldValue = tempTextFieldValue.copy(
                    text = newTypedText,
                    selection = TextRange(
                        (tempTextFieldValue.selection.min + indexDiff).coerceAtLeast(0),
                        (tempTextFieldValue.selection.max + indexDiff).coerceAtMost(newTypedText.length),
                    ),
                )
            }

            startTypeIndex = max(startTypeIndex, activeRichSpan.textRange.min)
            val startIndex = max(0, startTypeIndex - activeRichSpan.textRange.min)
            val beforeText =
                if (activeRichSpan.text.isEmpty())
                    ""
                else
                    activeRichSpan.text.substring(0, startIndex)

            val afterText =
                if (activeRichSpan.text.isEmpty())
                    ""
                else
                    activeRichSpan.text.substring(startIndex)

            val activeRichSpanFullSpanStyle = activeRichSpan.fullSpanStyle
            val newSpanStyle =
                activeRichSpanFullSpanStyle.customMerge(toAddSpanStyle).unmerge(toRemoveSpanStyle)
            val newRichSpanStyle =
                when {
                    toAddRichSpanStyle !is RichSpanStyle.Default ->
                        toAddRichSpanStyle

                    toRemoveRichSpanStyleKClass == activeRichSpan.richSpanStyle::class ->
                        RichSpanStyle.Default

                    else ->
                        activeRichSpan.richSpanStyle
                }

            val isToAddRemoveSpanStyleEmpty =
                toAddSpanStyle == SpanStyle() && toRemoveSpanStyle == SpanStyle()

            val isToAddRemoveRichSpanStyleEmpty =
                toAddRichSpanStyle is RichSpanStyle.Default && toRemoveRichSpanStyleKClass == RichSpanStyle.Default::class

            if (
                (isToAddRemoveSpanStyleEmpty && isToAddRemoveRichSpanStyleEmpty) ||
                (newSpanStyle == activeRichSpanFullSpanStyle && newRichSpanStyle::class == activeRichSpan.richSpanStyle::class)
            ) {
                activeRichSpan.text = beforeText + typedText + afterText

                checkListStart(richSpan = activeRichSpan)
            } else {
                handleUpdatingRichSpan(
                    richSpan = activeRichSpan,
                    beforeText = beforeText,
                    middleText = typedText,
                    afterText = afterText,
                    startIndex = startTypeIndex,
                    richSpanFullSpanStyle = activeRichSpanFullSpanStyle,
                    newSpanStyle = newSpanStyle,
                )
            }
        } else {
            if (richParagraphList.isEmpty()) {
                richParagraphList.add(RichParagraph())
            }

            val newRichSpan = RichSpan(
                paragraph = richParagraphList.last(),
                text = typedText,
                textRange = TextRange(startTypeIndex, startTypeIndex + typedText.length),
                spanStyle = toAddSpanStyle,
                richSpanStyle = toAddRichSpanStyle,
            )
            richParagraphList.last().children.add(newRichSpan)
        }
    }

    /**
     * Обрабатывает удаление символов из значения текстового поля.
     * Этот метод обновит [richParagraphList] в соответствии с изменениями.
     * Этот метод использует [tempTextFieldValue] для получения удалённых символов.
     */
    private fun handleRemovingCharacters() {
        val removedCharsCount = textFieldValue.text.length - tempTextFieldValue.text.length

        val minRemoveIndex =
            tempTextFieldValue.selection.min
                .coerceAtLeast(0)

        val maxRemoveIndex =
            (minRemoveIndex + removedCharsCount)
                .coerceAtMost(textFieldValue.text.length)

        val removeRange = TextRange(minRemoveIndex, maxRemoveIndex)

        val minRichSpan = getRichSpanByTextIndex(textIndex = minRemoveIndex, true) ?: return
        val maxRichSpan = getRichSpanByTextIndex(textIndex = maxRemoveIndex - 1, true) ?: return

        // Check deleted paragraphs
        val minParagraphIndex = richParagraphList.indexOf(minRichSpan.paragraph)
        val maxParagraphIndex = richParagraphList.indexOf(maxRichSpan.paragraph)

        // Remove paragraphs between the min and max paragraphs
        if (minParagraphIndex < maxParagraphIndex - 1 && !singleParagraphMode) {
            richParagraphList.removeRange(minParagraphIndex + 1, maxParagraphIndex)
        }

        // Get the first non-empty child of the min paragraph
        val minFirstNonEmptyChild = minRichSpan.paragraph.getFirstNonEmptyChild()
        val minParagraphStartTextLength = minRichSpan.paragraph.type.startRichSpan.text.length
        val minParagraphFirstChildMinIndex =
            minFirstNonEmptyChild?.textRange?.min ?: minParagraphStartTextLength

        // Get the first non-empty child of the max paragraph
        val maxFirstNonEmptyChild = maxRichSpan.paragraph.getFirstNonEmptyChild()
        val maxParagraphStartTextLength = maxRichSpan.paragraph.type.startRichSpan.text.length
        val maxParagraphFirstChildMinIndex =
            maxFirstNonEmptyChild?.textRange?.min ?: maxParagraphStartTextLength

        // TODO:
        //  Check if we can remove this condition since we are already checking below
        //  if the paragraph needs to be removed
        if (minParagraphIndex == maxParagraphIndex && !singleParagraphMode) {
            if (
                (minFirstNonEmptyChild == null || minFirstNonEmptyChild.text.isEmpty()) &&
                minRichSpan.paragraph.type.startText.isEmpty()
            ) {
                // Remove the min paragraph if it's empty (and the max paragraph is the same)
                richParagraphList.removeAt(minParagraphIndex)
            }
        }

        // Handle Remove the min paragraph custom text
        if (minRemoveIndex < minParagraphFirstChildMinIndex) {
            if (minRichSpan.paragraph.type.startText.isEmpty() && minParagraphIndex != maxParagraphIndex) {
                minRichSpan.paragraph.children.clear()
                richParagraphList.remove(minRichSpan.paragraph)
            } else {
                handleRemoveMinParagraphStartText(
                    removeIndex = minRemoveIndex,
                    paragraphStartTextLength = minParagraphStartTextLength,
                    paragraphFirstChildMinIndex = minParagraphFirstChildMinIndex,
                )

                // Save the old paragraph type
                val minParagraphOldType = minRichSpan.paragraph.type

                // Set the paragraph type to DefaultParagraph
                minRichSpan.paragraph.type = DefaultParagraph()

                // Check if it's a list and handle level appropriately
                if (
                    maxRemoveIndex - minRemoveIndex == 1 &&
                    minParagraphOldType is ConfigurableListLevel &&
                    minParagraphOldType.level > 1
                ) {
                    // Decrease level instead of exiting list
                    minParagraphOldType.level -= 1
                    tempTextFieldValue = updateParagraphType(
                        paragraph = minRichSpan.paragraph,
                        newType = minParagraphOldType,
                        textFieldValue = tempTextFieldValue,
                    )
                }
            }
        }

        // Handle Remove the max paragraph custom text
        if (maxRemoveIndex < maxParagraphFirstChildMinIndex) {
            handleRemoveMaxParagraphStartText(
                minRemoveIndex = minRemoveIndex,
                maxRemoveIndex = maxRemoveIndex,
                paragraphStartTextLength = maxParagraphStartTextLength,
                paragraphFirstChildMinIndex = maxParagraphFirstChildMinIndex,
            )

            tempTextFieldValue = adjustOrderedListsNumbers(
                startParagraphIndex = maxParagraphIndex + 1,
                startNumber = 1,
                textFieldValue = tempTextFieldValue,
            )
        }

        // Remove spans from the max paragraph
        val isMaxParagraphEmpty =
            maxRichSpan.paragraph.removeTextRange(
                removeRange,
                maxParagraphFirstChildMinIndex
            ) == null

        if (!singleParagraphMode) {
            if (maxParagraphIndex != minParagraphIndex) {
                // Remove spans from the min paragraph
                val isMinParagraphEmpty =
                    minRichSpan.paragraph.removeTextRange(
                        removeRange,
                        minParagraphFirstChildMinIndex
                    ) == null

                if (isMaxParagraphEmpty) {
                    // Remove the max paragraph if it's empty
                    richParagraphList.remove(maxRichSpan.paragraph)
                }

                if (isMinParagraphEmpty) {
                    // Set the min paragraph type to the max paragraph type
                    // Since the max paragraph is going to take the min paragraph's place
                    maxRichSpan.paragraph.type = minRichSpan.paragraph.type

                    // Remove the min paragraph if it's empty
                    richParagraphList.remove(minRichSpan.paragraph)
                }

                if (!isMinParagraphEmpty && !isMaxParagraphEmpty) {
                    // Merge the two paragraphs if they are not empty
                    mergeTwoRichParagraphs(
                        firstParagraph = minRichSpan.paragraph,
                        secondParagraph = maxRichSpan.paragraph,
                    )
                }
            }

            if (minRemoveIndex == minParagraphFirstChildMinIndex - minParagraphStartTextLength - 1) {
                if (minParagraphStartTextLength > 0) {
                    val beforeText = tempTextFieldValue.text.substring(
                        startIndex = 0,
                        endIndex = minRemoveIndex
                            .coerceAtMost(tempTextFieldValue.text.length),
                    )
                    val afterText =
                        if (minRemoveIndex + 1 > tempTextFieldValue.text.lastIndex)
                            ""
                        else
                            tempTextFieldValue.text.substring(
                                startIndex = minRemoveIndex + 1,
                            )

                    tempTextFieldValue = tempTextFieldValue.copy(
                        text = beforeText + afterText,
                        selection = TextRange(tempTextFieldValue.selection.min),
                    )
                }

                richParagraphList.getOrNull(minParagraphIndex - 1)?.let { previousParagraph ->
                    // Merge the two paragraphs if the line break is removed
                    mergeTwoRichParagraphs(
                        firstParagraph = previousParagraph,
                        secondParagraph = maxRichSpan.paragraph,
                    )
                }
            }
        }

        // Set current applied style to min rich span if the paragraph is empty
        if (
            config.preserveStyleOnEmptyLine &&
            minRichSpan.paragraph.isEmpty()
        ) {
            val minParagraphFirstRichSpan =
                minRichSpan.paragraph.getFirstNonEmptyChild() ?: run {
                    val richSpan =
                        RichSpan(
                            paragraph = minRichSpan.paragraph,
                            text = "",
                            textRange = TextRange(minRemoveIndex, minRemoveIndex),
                        )

                    minRichSpan.paragraph.children.add(richSpan)

                    richSpan
                }

            minParagraphFirstRichSpan.spanStyle = currentAppliedSpanStyle
            minParagraphFirstRichSpan.richSpanStyle = currentAppliedRichSpanStyle
        }

        checkOrderedListsNumbers(
            startParagraphIndex = minParagraphIndex - 1,
            endParagraphIndex = minParagraphIndex + 1,
        )
    }

    private fun handleRemoveMinParagraphStartText(
        removeIndex: Int,
        paragraphStartTextLength: Int,
        paragraphFirstChildMinIndex: Int,
    ) {
        if (removeIndex >= paragraphFirstChildMinIndex || paragraphStartTextLength <= 0)
            return

        val indexDiff = (paragraphStartTextLength - (paragraphFirstChildMinIndex - removeIndex))
            .coerceAtLeast(0)
        val beforeTextEndIndex =
            (paragraphFirstChildMinIndex - paragraphStartTextLength)
                .coerceAtMost(tempTextFieldValue.text.length)

        val beforeText =
            if (beforeTextEndIndex <= 0)
                ""
            else
                tempTextFieldValue.text.substring(
                    startIndex = 0,
                    endIndex = beforeTextEndIndex,
                )
        val afterText =
            if (tempTextFieldValue.text.length <= removeIndex)
                ""
            else
                tempTextFieldValue.text.substring(
                    startIndex = removeIndex,
                    endIndex = tempTextFieldValue.text.length,
                )
        val newText = beforeText + afterText
        val newSelection = TextRange(removeIndex - indexDiff)

        tempTextFieldValue = tempTextFieldValue.copy(
            text = newText,
            selection = newSelection,
        )
    }

    private fun handleRemoveMaxParagraphStartText(
        minRemoveIndex: Int,
        maxRemoveIndex: Int,
        paragraphStartTextLength: Int,
        paragraphFirstChildMinIndex: Int,
    ) {
        if (maxRemoveIndex < paragraphFirstChildMinIndex && paragraphStartTextLength > 0) {
            paragraphStartTextLength - (paragraphFirstChildMinIndex - maxRemoveIndex)

            val beforeText =
                if (minRemoveIndex <= 0)
                    ""
                else
                    tempTextFieldValue.text.substring(
                        startIndex = 0,
                        endIndex = minRemoveIndex,
                    )

            val afterTextStartIndex =
                minRemoveIndex + (paragraphFirstChildMinIndex - maxRemoveIndex)

            val afterText =
                if (tempTextFieldValue.text.length <= afterTextStartIndex)
                    ""
                else
                    tempTextFieldValue.text.substring(
                        startIndex = afterTextStartIndex,
                        endIndex = tempTextFieldValue.text.length,
                    )

            val newText = beforeText + afterText

            tempTextFieldValue = tempTextFieldValue.copy(
                text = newText,
            )
        }
    }

    private fun checkListStart(richSpan: RichSpan) {
        if (richSpan.paragraph.type !is DefaultParagraph)
            return

        if (!richSpan.isFirstInParagraph)
            return

        if (richSpan.text == "- " || richSpan.text == "* ") {
            richSpan.paragraph.type = UnorderedList(
                config = config,
            )
            richSpan.text = ""
        } else if (richSpan.text.matches(Regex("^\\d+\\. "))) {
            val dotIndex = richSpan.text.indexOf('.')
            if (dotIndex != -1) {
                val number = richSpan.text.substring(0, dotIndex).toIntOrNull() ?: 1
                richSpan.paragraph.type = OrderedList(
                    number = number,
                    config = config,
                )
                richSpan.text = ""
            }
        }
    }

    /**
     * Проверяет нумерацию упорядоченных списков и корректирует её при необходимости.
     *
     * @param startParagraphIndex Начальный индекс абзаца, с которого начинается проверка.
     * @param startNumber Начальное число, с которого начинать.
     * @param textFieldValue Значение текстового поля для обновления.
     * @return Обновлённое значение текстового поля.
     */
    private fun adjustOrderedListsNumbers(
        startParagraphIndex: Int,
        startNumber: Int,
        textFieldValue: TextFieldValue,
        initialLevelNumberMap: Map<Int, Int> = emptyMap(),
    ): TextFieldValue {
        var newTextFieldValue = textFieldValue
        // The map to store the list number of each list level, level -> number
        val levelNumberMap = mutableMapOf<Int, Int>()
        levelNumberMap.putAll(initialLevelNumberMap)

        // Update the paragraph type of the paragraphs after the new paragraph
        for (i in (startParagraphIndex)..(richParagraphList.lastIndex)) {
            val currentParagraph = richParagraphList[i]
            val currentParagraphType = currentParagraph.type

            if (currentParagraphType !is ConfigurableListLevel)
                break

            levelNumberMap.keys.toList().fastForEach { level ->
                if (level > currentParagraphType.level)
                    levelNumberMap.remove(level)
            }

            if (currentParagraphType is UnorderedList) {
                levelNumberMap[currentParagraphType.level] = 0
                continue
            }

            if (currentParagraphType !is OrderedList)
                break

            val currentNumber =
                if (i == startParagraphIndex)
                    startNumber
                else
                    levelNumberMap[currentParagraphType.level]
                        ?.plus(1)
                        ?: run {
                            if (levelNumberMap.containsKey(currentParagraphType.level - 1))
                                1
                            else
                                currentParagraphType.number
                        }

            levelNumberMap[currentParagraphType.level] = currentNumber

            newTextFieldValue = updateParagraphType(
                paragraph = currentParagraph,
                newType = OrderedList(
                    number = currentNumber,
                    config = config,
                    startTextWidth = currentParagraphType.startTextWidth,
                    initialLevel = currentParagraphType.level
                ),
                textFieldValue = newTextFieldValue,
            )
        }

        return newTextFieldValue
    }

    private fun checkOrderedListsNumbers(
        startParagraphIndex: Int,
        endParagraphIndex: Int,
    ) {
        // The map to store the list number of each list level, level -> number
        val levelNumberMap = mutableMapOf<Int, Int>()
        val startParagraph = richParagraphList.getOrNull(startParagraphIndex)
        val startParagraphType = startParagraph?.type
        if (startParagraphType is OrderedList)
            levelNumberMap[startParagraphType.level] = startParagraphType.number

        // Update the paragraph type of the paragraphs after the new paragraph
        for (i in (startParagraphIndex + 1)..richParagraphList.lastIndex) {
            val currentParagraph = richParagraphList[i]
            val currentParagraphType = currentParagraph.type

            if (currentParagraphType is ConfigurableListLevel) {
                // Clear the completed list levels
                levelNumberMap.keys.toList().fastForEach { level ->
                    if (level > currentParagraphType.level)
                        levelNumberMap.remove(level)
                }
            } else {
                // Clear the map if the current paragraph is not a list
                levelNumberMap.clear()
            }

            // Remove current list level from map if the current paragraph is an unordered list
            if (currentParagraphType is UnorderedList)
                levelNumberMap.remove(currentParagraphType.level)

            if (currentParagraphType is OrderedList) {
                val number =
                    levelNumberMap[currentParagraphType.level]
                        ?.plus(1)
                        ?: currentParagraphType.number

                levelNumberMap[currentParagraphType.level] = number

                tempTextFieldValue = updateParagraphType(
                    paragraph = currentParagraph,
                    newType = OrderedList(
                        number = number,
                        config = config,
                        startTextWidth = currentParagraphType.startTextWidth,
                        initialLevel = currentParagraphType.level
                    ),
                    textFieldValue = tempTextFieldValue,
                )
            }

            // Break if we reach the end paragraph index
            if (i >= endParagraphIndex)
                break
        }
    }

    private fun checkForParagraphs() {
        var index = tempTextFieldValue.text.lastIndex

        while (true) {
            // Search for the next paragraph
            index = tempTextFieldValue.text.lastIndexOf('\n', index)

            // If there are no more paragraphs, break
            if (index < textFieldValue.selection.min) break

            // Get the rich span style at the index to split it between two paragraphs
            val richSpan = getRichSpanByTextIndex(index)

            // If there is no rich span style at the index, continue (this should not happen)
            if (richSpan == null) {
                index--
                continue
            }

            // Get the paragraph style index of the rich span style
            val paragraphIndex = richParagraphList.indexOf(richSpan.paragraph)
            // If the paragraph index is -1, continue (this should not happen)
            if (paragraphIndex == -1) {
                index--
                continue
            }

            // Make sure the index is not less than the minimum text range of the rich span style
            // This is to make sure that the index is not in paragraph custom start text
            val sliceIndex = max(index, richSpan.textRange.min)

            // Create a new paragraph style
            val newParagraph = richSpan.paragraph.slice(
                startIndex = sliceIndex,
                richSpan = richSpan,
                removeSliceIndex = true,
            )

            // If the new paragraph is empty apply style depending on the config
            if (tempTextFieldValue.selection.collapsed && newParagraph.isEmpty()) {
                val newParagraphFirstRichSpan = newParagraph.getFirstNonEmptyChild()

                val isSelectionAtNewRichSpan =
                    newParagraphFirstRichSpan?.textRange?.min == tempTextFieldValue.selection.min - 1

                // Check if the cursor is at the new paragraph and if it's an empty list item
                if (
                    config.exitListOnEmptyItem &&
                    isSelectionAtNewRichSpan &&
                    richSpan.paragraph.isEmpty() &&
                    richSpan.paragraph.type is ConfigurableListLevel
                ) {
                    // Exit list by removing list formatting
                    tempTextFieldValue = updateParagraphType(
                        paragraph = richSpan.paragraph,
                        newType = DefaultParagraph(),
                        textFieldValue = tempTextFieldValue,
                    )
                    newParagraphFirstRichSpan.spanStyle = SpanStyle()
                    newParagraphFirstRichSpan.richSpanStyle = RichSpanStyle.Default

                    // Ignore adding the new paragraph
                    index--
                    continue
                } else if (
                    (!config.preserveStyleOnEmptyLine || richSpan.paragraph.isEmpty()) &&
                    isSelectionAtNewRichSpan
                ) {
                    newParagraphFirstRichSpan.spanStyle = SpanStyle()
                    newParagraphFirstRichSpan.richSpanStyle = RichSpanStyle.Default
                } else if (
                    config.preserveStyleOnEmptyLine &&
                    isSelectionAtNewRichSpan
                ) {
                    newParagraphFirstRichSpan.spanStyle = currentSpanStyle
                    newParagraphFirstRichSpan.richSpanStyle = currentRichSpanStyle
                }
            }

            // Get the text before and after the slice index
            val beforeText = tempTextFieldValue.text.substring(0, sliceIndex + 1)
            val afterText = tempTextFieldValue.text.substring(sliceIndex + 1)

            // Update the text field value to include the new paragraph custom start text
            tempTextFieldValue = tempTextFieldValue.copy(
                text = beforeText + newParagraph.type.startText + afterText,
                selection = TextRange(
                    start = tempTextFieldValue.selection.start + newParagraph.type.startText.length,
                    end = tempTextFieldValue.selection.end + newParagraph.type.startText.length,
                ),
            )

            // Add the new paragraph
            richParagraphList.add(paragraphIndex + 1, newParagraph)

            // Update the paragraph type of the paragraphs after the new paragraph
            val newParagraphType = newParagraph.type

            if (newParagraphType is OrderedList) {
                tempTextFieldValue = adjustOrderedListsNumbers(
                    startParagraphIndex = paragraphIndex + 1,
                    startNumber = newParagraphType.number,
                    textFieldValue = tempTextFieldValue,
                )
            }

            // Remove one from the index to continue searching for paragraphs
            index--
        }
    }

    /**
     * Обрабатывает добавление или удаление стилей [toAddSpanStyle] и [toRemoveSpanStyle] из выделенного текста.
     */
    private fun applyRichSpanStyleToSelectedText() {
        applyRichSpanStyleToTextRange(selection)
    }

    /**
     * Обрабатывает добавление или удаление стилей [toAddSpanStyle] и [toRemoveSpanStyle] из указанного [TextRange].
     *
     * @param textRange [TextRange], к которому применяются стили.
     */
    private fun applyRichSpanStyleToTextRange(
        textRange: TextRange
    ) {
        // Get the rich span list of the selected text
        val selectedRichSpanList = getRichSpanListByTextRange(textRange)

        val startSelectionIndex = textRange.min
        val endSelectionIndex = textRange.max

        // Loop through the rich span list
        for (i in selectedRichSpanList.lastIndex downTo 0) {
            val richSpan = selectedRichSpanList[i]

            // Get the text before, during, and after the selected text
            val beforeText =
                if (startSelectionIndex in richSpan.textRange)
                    richSpan.text.substring(0, startSelectionIndex - richSpan.textRange.start)
                else
                    ""

            val middleText =
                richSpan.text.substring(
                    maxOf(startSelectionIndex - richSpan.textRange.start, 0),
                    minOf(endSelectionIndex - richSpan.textRange.start, richSpan.text.length)
                )

            val afterText =
                if (endSelectionIndex - 1 in richSpan.textRange)
                    richSpan.text.substring(endSelectionIndex - richSpan.textRange.start)
                else
                    ""

            val richSpanFullSpanStyle = richSpan.fullSpanStyle
            val newSpanStyle =
                richSpanFullSpanStyle.customMerge(toAddSpanStyle).unmerge(toRemoveSpanStyle)

            val startApplyStyleIndex = maxOf(startSelectionIndex, richSpan.textRange.start)

            handleUpdatingRichSpan(
                richSpan = richSpan,
                startIndex = startApplyStyleIndex,
                beforeText = beforeText,
                middleText = middleText,
                afterText = afterText,
                newSpanStyle = newSpanStyle,
            )
        }

        updateTextFieldValue(textFieldValue)
    }

    /**
     * Применяет [toAddSpanStyle] и [toRemoveSpanStyle] к [RichSpan].
     *
     * @param richSpan [RichSpan], к которому применяются стили.
     * @param beforeText Текст до применения стилей.
     * @param middleText Текст, к которому применяются стили.
     * @param afterText Текст после применения стилей.
     * @param startIndex Начальный индекс текста, к которому применяются стили.
     * @param richSpanFullSpanStyle [SpanStyle] объекта [RichSpan].
     * @param newSpanStyle Новый [SpanStyle], применяемый к [RichSpan].
     */
    private fun handleUpdatingRichSpan(
        richSpan: RichSpan,
        beforeText: String,
        middleText: String,
        afterText: String,
        startIndex: Int,
        richSpanFullSpanStyle: SpanStyle = richSpan.fullSpanStyle,
        newSpanStyle: SpanStyle = richSpanFullSpanStyle.customMerge(toAddSpanStyle)
            .unmerge(toRemoveSpanStyle),
        newRichSpanStyle: RichSpanStyle =
            when {
                toAddRichSpanStyle !is RichSpanStyle.Default ->
                    toAddRichSpanStyle

                toRemoveRichSpanStyleKClass == richSpan.richSpanStyle::class ->
                    RichSpanStyle.Default

                else ->
                    richSpan.richSpanStyle
            },
    ) {
        if (richSpanFullSpanStyle == newSpanStyle && newRichSpanStyle::class == richSpan.richSpanStyle::class) return

        if (
            (toRemoveSpanStyle == SpanStyle() || !richSpanFullSpanStyle.isSpecifiedFieldsEquals(
                toRemoveSpanStyle
            )) &&
            (toRemoveRichSpanStyleKClass == RichSpanStyle.Default::class || newRichSpanStyle::class == richSpan.richSpanStyle::class)
        ) {
            applyStyleToRichSpan(
                richSpan = richSpan,
                beforeText = beforeText,
                middleText = middleText,
                afterText = afterText,
                startIndex = startIndex,
            )
        } else {
            handleRemovingStyleFromRichSpan(
                richSpan = richSpan,
                beforeText = beforeText,
                middleText = middleText,
                afterText = afterText,
                startIndex = startIndex,
                richSpanFullSpanStyle = richSpanFullSpanStyle,
                newSpanStyle = newSpanStyle,
                newRichSpanStyle = newRichSpanStyle,
            )
        }
    }

    /**
     * Обрабатывает применение нового [SpanStyle] и нового [RichSpanStyle] к [RichSpan].
     *
     * @param richSpan [RichSpan], к которому применяется новый [SpanStyle].
     * @param beforeText Текст до применения стилей.
     * @param middleText Текст, к которому применяются стили.
     * @param afterText Текст после применения стилей.
     * @param startIndex Начальный индекс текста, к которому применяются стили.
     */
    private fun applyStyleToRichSpan(
        richSpan: RichSpan,
        beforeText: String,
        middleText: String,
        afterText: String,
        startIndex: Int,
    ) {
        val fullSpanStyle = richSpan.fullSpanStyle

        // Simplify the richSpan tree if possible, by avoiding creating a new RichSpan.
        if (
            beforeText.isEmpty() &&
            afterText.isEmpty() &&
            richSpan.children.isEmpty()
        ) {
            richSpan.text = middleText
            richSpan.spanStyle = richSpan.spanStyle
                .copy(textDecoration = fullSpanStyle.textDecoration)
                .customMerge(toAddSpanStyle)
            richSpan.richSpanStyle =
                if (toAddRichSpanStyle !is RichSpanStyle.Default)
                    toAddRichSpanStyle
                else
                    richSpan.richSpanStyle

            return
        }

        richSpan.text = beforeText
        val newRichSpan =
            RichSpan(
                paragraph = richSpan.paragraph,
                parent = richSpan,
                text = middleText,
                textRange = TextRange(
                    startIndex,
                    startIndex + middleText.length
                ),
                spanStyle = SpanStyle(textDecoration = fullSpanStyle.textDecoration).customMerge(
                    toAddSpanStyle
                ),
                richSpanStyle =
                    if (toAddRichSpanStyle !is RichSpanStyle.Default)
                        toAddRichSpanStyle
                    else
                        richSpan.richSpanStyle,
            )

        val parent = richSpan.parent
        val index =
            parent?.children?.indexOf(richSpan) ?: richSpan.paragraph.children.indexOf(richSpan)
        var isRemoved = false

        val isRichSpanStylingEmpty =
            richSpan.spanStyle == SpanStyle() && richSpan.richSpanStyle is RichSpanStyle.Default

        if (middleText.isNotEmpty()) {
            if (
                (isRichSpanStylingEmpty || richSpan.text.isEmpty()) &&
                index != -1 &&
                richSpan.children.isEmpty()
            ) {
                newRichSpan.parent = richSpan.parent

                if (!isRichSpanStylingEmpty) {
                    newRichSpan.spanStyle = richSpan.spanStyle.customMerge(newRichSpan.spanStyle)
                    if (richSpan.richSpanStyle !is RichSpanStyle.Default && newRichSpan.richSpanStyle is RichSpanStyle.Default)
                        newRichSpan.richSpanStyle = richSpan.richSpanStyle
                }

                if (parent != null) {
                    parent.children.add(index + 1, newRichSpan)

                    if (richSpan.text.isEmpty()) {
                        parent.children.removeAt(index)
                        isRemoved = true
                    }
                } else {
                    richSpan.paragraph.children.add(index + 1, newRichSpan)

                    if (richSpan.text.isEmpty()) {
                        richSpan.paragraph.children.removeAt(index)
                        isRemoved = true
                    }
                }
            } else {
                richSpan.children.add(0, newRichSpan)
                newRichSpan.parent = richSpan
            }
        }

        if (afterText.isNotEmpty()) {
            val afterRichSpan =
                RichSpan(
                    paragraph = richSpan.paragraph,
                    parent = richSpan,
                    text = afterText,
                    textRange = TextRange(
                        startIndex + middleText.length,
                        startIndex + middleText.length + afterText.length
                    ),
                )

            if (
                (isRichSpanStylingEmpty || richSpan.text.isEmpty()) &&
                index != -1 &&
                richSpan.children.isEmpty()
            ) {
                afterRichSpan.parent = richSpan.parent

                if (!isRichSpanStylingEmpty) {
                    afterRichSpan.spanStyle =
                        richSpan.spanStyle.customMerge(afterRichSpan.spanStyle)
                    if (richSpan.richSpanStyle !is RichSpanStyle.Default && afterRichSpan.richSpanStyle is RichSpanStyle.Default)
                        afterRichSpan.richSpanStyle = richSpan.richSpanStyle
                }

                val addIndex =
                    if (isRemoved || middleText.isEmpty())
                        index + 1
                    else
                        index + 2

                if (parent != null) {
                    parent.children.add(addIndex, afterRichSpan)

                    if (richSpan.text.isEmpty() && !isRemoved)
                        parent.children.removeAt(index)
                } else {
                    richSpan.paragraph.children.add(addIndex, afterRichSpan)

                    if (richSpan.text.isEmpty() && !isRemoved)
                        richSpan.paragraph.children.removeAt(index)
                }
            } else {
                richSpan.children.add(1, afterRichSpan)
                afterRichSpan.parent = richSpan
            }
        } else {
            val firstRichSpan = richSpan.children.firstOrNull()
            val secondRichSpan = richSpan.children.getOrNull(1)

            if (
                firstRichSpan != null &&
                secondRichSpan != null &&
                firstRichSpan.spanStyle == secondRichSpan.spanStyle &&
                firstRichSpan.richSpanStyle == secondRichSpan.richSpanStyle
            ) {
                firstRichSpan.text += secondRichSpan.text
                firstRichSpan.children.addAll(secondRichSpan.children)
                richSpan.children.removeAt(1)
            }

            if (
                firstRichSpan != null &&
                richSpan.text.isEmpty() &&
                richSpan.children.size == 1
            ) {
                richSpan.text = firstRichSpan.text
                richSpan.spanStyle = richSpan.spanStyle.customMerge(firstRichSpan.spanStyle)
                richSpan.children.clear()
                richSpan.children.addAll(firstRichSpan.children)
            }
        }
    }

    /**
     * Обрабатывает удаление [SpanStyle] из [RichSpan].
     *
     * @param richSpan [RichSpan], из которого нужно удалить [SpanStyle].
     * @param beforeText Текст до удаления стилей.
     * @param middleText Текст, из которого удаляются стили.
     * @param afterText Текст после удаления стилей.
     * @param startIndex Начальный индекс текста, из которого удаляются стили.
     * @param richSpanFullSpanStyle [SpanStyle] объекта [RichSpan].
     * @param newSpanStyle Новый [SpanStyle], применяемый к [RichSpan].
     * @param newRichSpanStyle Новый [RichSpanStyle], применяемый к [RichSpan].
     */
    private fun handleRemovingStyleFromRichSpan(
        richSpan: RichSpan,
        beforeText: String,
        middleText: String,
        afterText: String,
        startIndex: Int,
        richSpanFullSpanStyle: SpanStyle,
        newSpanStyle: SpanStyle,
        newRichSpanStyle: RichSpanStyle,
    ) {
        richSpan.text = beforeText
        val parentRichSpan = richSpan.getClosestRichSpan(newSpanStyle, newRichSpanStyle)
        val newRichSpan = RichSpan(
            paragraph = richSpan.paragraph,
            parent = parentRichSpan,
            text = middleText,
            textRange = TextRange(
                startIndex,
                startIndex + middleText.length
            ),
            spanStyle = newSpanStyle.unmerge(parentRichSpan?.spanStyle),
            richSpanStyle = newRichSpanStyle,
        )
        val afterRichSpan = RichSpan(
            paragraph = richSpan.paragraph,
            parent = parentRichSpan,
            text = afterText,
            textRange = TextRange(
                startIndex + middleText.length,
                startIndex + middleText.length + afterText.length
            ),
            spanStyle = richSpanFullSpanStyle,
            richSpanStyle = richSpan.richSpanStyle,
        )

        val toShiftRichSpanList: MutableList<RichSpan> = mutableListOf()
        var previousRichSpan: RichSpan?
        var currentRichSpan: RichSpan? = richSpan

        toShiftRichSpanList.add(newRichSpan)
        if (afterRichSpan.text.isNotEmpty())
            toShiftRichSpanList.add(afterRichSpan)

        while (true) {
            previousRichSpan = currentRichSpan
            currentRichSpan = currentRichSpan?.parent

            if (currentRichSpan == null || currentRichSpan == parentRichSpan) {
                break
            } else {
                val index = currentRichSpan.children.indexOf(previousRichSpan)
                if (index in 0 until currentRichSpan.children.lastIndex) {
                    val currentRichSpanFullSpanStyle = currentRichSpan.fullSpanStyle
                    ((index + 1)..currentRichSpan.children.lastIndex).forEach {
                        val childRichSpan = currentRichSpan.children[it]

                        // Ignore shifting empty RichSpan.
                        if (childRichSpan.text.isEmpty() && childRichSpan.children.isEmpty()) {
                            return@forEach
                        }

                        // Merge RichSpan span style with parent RichSpan span style.
                        childRichSpan.spanStyle =
                            currentRichSpanFullSpanStyle.merge(childRichSpan.spanStyle)

                        // Lookup for RichSpan with the same span style and merge them to optimize the RichSpan tree.
                        val lastChild = toShiftRichSpanList.lastOrNull()
                        if (lastChild != null && lastChild.spanStyle == childRichSpan.spanStyle) {
                            if (lastChild.children.isEmpty()) {
                                lastChild.text += childRichSpan.text
                                lastChild.children.addAll(childRichSpan.children)
                            } else {
                                lastChild.children.add(childRichSpan)
                                childRichSpan.parent = lastChild
                                childRichSpan.spanStyle = RichSpanStyle.DefaultSpanStyle
                                for (i in childRichSpan.children.lastIndex downTo 0) {
                                    val child = childRichSpan.children[i]
                                    child.parent = lastChild
                                    childRichSpan.children.removeAt(i)
                                    lastChild.children.add(child)
                                }
                            }
                        } else {
                            childRichSpan.parent = parentRichSpan
                            toShiftRichSpanList.add(childRichSpan)
                        }
                    }

                    // Remove shifted RichSpan from parent RichSpan.
                    currentRichSpan.children.removeRange(index + 1, currentRichSpan.children.size)

                    // Remove empty RichSpan.
                    if (previousRichSpan?.isEmpty() == true) {
                        currentRichSpan.children.removeAt(index)
                    }
                }
            }
        }

        if (parentRichSpan == null || currentRichSpan == null) {
            val index = richSpan.paragraph.children.indexOf(previousRichSpan)
            if (index in 0..richSpan.paragraph.children.lastIndex) {
                richSpan.paragraph.children.addAll(
                    index + 1,
                    toShiftRichSpanList
                )
            }

            // Remove empty RichSpan.
            if (previousRichSpan?.isEmpty() == true) {
                richSpan.paragraph.children.removeAt(index)
            }
        } else {
            val index = parentRichSpan.children.indexOf(previousRichSpan)
            if (index in 0..parentRichSpan.children.lastIndex) {
                parentRichSpan.children.addAll(
                    index + 1,
                    toShiftRichSpanList
                )
            }

            // Remove empty RichSpan.
            if (previousRichSpan?.isEmpty() == true) {
                parentRichSpan.children.removeAt(index)
            }
        }

        if (richSpan.text.isEmpty() && richSpan.children.isEmpty()) {
            val parent = richSpan.parent
            if (parent != null) {
                parent.children.remove(richSpan)
            } else {
                richSpan.paragraph.children.remove(richSpan)
            }
        }
    }

    private fun addRichSpanAtPosition(
        vararg richSpan: RichSpan,
        index: Int,
    ) {
        val previousIndex = index - 1

        val activeRichSpan = getRichSpanByTextIndex(previousIndex)

        // If there is no active rich span, add the rich span to the last paragraph
        if (activeRichSpan == null) {
            richParagraphList.last().children.addAll(richSpan)
            return
        }

        val startIndex = max(0, index - activeRichSpan.textRange.min)
        val beforeText = activeRichSpan.text.substring(0, startIndex)
        val afterText = activeRichSpan.text.substring(startIndex)

        // Simplify the richSpan tree if possible, by avoiding creating a new RichSpan.
        if (
            beforeText.isEmpty() &&
            afterText.isEmpty() &&
            activeRichSpan.children.isEmpty() &&
            richSpan.size == 1
        ) {
            activeRichSpan.text = richSpan.first().text
            activeRichSpan.richSpanStyle = richSpan.first().richSpanStyle
            return
        }

        activeRichSpan.text = beforeText

        var addedTextLength = 0
        for (i in richSpan.lastIndex downTo 0) {
            val newRichSpan = richSpan[i]
            newRichSpan.paragraph = activeRichSpan.paragraph
            newRichSpan.parent = activeRichSpan
            activeRichSpan.children.add(
                0,
                newRichSpan
            )
            addedTextLength += newRichSpan.text.length
        }
        if (afterText.isNotEmpty()) {
            activeRichSpan.children.add(
                1,
                RichSpan(
                    paragraph = activeRichSpan.paragraph,
                    parent = activeRichSpan,
                    text = afterText,
                    textRange = TextRange(
                        index + addedTextLength,
                        index + addedTextLength + afterText.length
                    ),
                )
            )
        } else {
            val firstRichSpan = activeRichSpan.children.firstOrNull()
            val secondRichSpan = activeRichSpan.children.getOrNull(1)

            if (
                firstRichSpan != null &&
                secondRichSpan != null &&
                firstRichSpan.spanStyle == secondRichSpan.spanStyle
            ) {
                firstRichSpan.text += secondRichSpan.text
                firstRichSpan.children.addAll(secondRichSpan.children)
                activeRichSpan.children.removeAt(1)
            }

            if (
                firstRichSpan != null &&
                activeRichSpan.text.isEmpty() &&
                activeRichSpan.children.size == 1 &&
                richSpan.size == 1
            ) {
                activeRichSpan.text = firstRichSpan.text
                activeRichSpan.spanStyle =
                    richSpan.first().spanStyle.customMerge(firstRichSpan.spanStyle)
                activeRichSpan.children.clear()
                activeRichSpan.children.addAll(firstRichSpan.children)
            }
        }
    }

    private fun getToShiftRichSpanList(
        startRichSpan: RichSpan,
        endRichSpan: RichSpan?,
    ): List<RichSpan> {
        val toShiftRichSpanList: MutableList<RichSpan> = mutableListOf()
        var previousRichSpan: RichSpan?
        var currentRichSpan: RichSpan? = startRichSpan

        while (true) {
            previousRichSpan = currentRichSpan
            currentRichSpan = currentRichSpan?.parent

            if (currentRichSpan == null || currentRichSpan == endRichSpan) {
                break
            } else {
                val index = currentRichSpan.children.indexOf(previousRichSpan)
                if (index in 0 until currentRichSpan.children.lastIndex) {
                    ((index + 1)..currentRichSpan.children.lastIndex).forEach {
                        val richSpan = currentRichSpan.children[it]
                        richSpan.spanStyle = richSpan.fullSpanStyle
                        richSpan.parent = endRichSpan
                        toShiftRichSpanList.add(richSpan)
                    }
                    currentRichSpan.children.removeRange(index + 1, currentRichSpan.children.size)
                }
            }
        }

        return toShiftRichSpanList
    }

    /**
     * Разделяет [RichParagraph] по [startIndex] и [richSpan], который содержит [startIndex].
     * Переданный [RichParagraph] будет изменён и будет содержать только текст до [startIndex].
     * А новый [RichParagraph] будет возвращён, содержащий текст после [startIndex].
     *
     * @param startIndex Начальный индекс для разделения.
     * @param richSpan [RichSpan], который содержит [startIndex].
     * @return Новый [RichParagraph].
     */
    private fun RichParagraph.slice(
        startIndex: Int,
        richSpan: RichSpan,
        removeSliceIndex: Boolean,
    ): RichParagraph {
        val newRichParagraph = RichParagraph(
            paragraphStyle = paragraphStyle,
            type = type.getNextParagraphType(),
        )

        var previousRichSpan: RichSpan
        var currentRichSpan: RichSpan = richSpan

        val textStartIndex =
            if (startIndex == type.startRichSpan.textRange.min)
                startIndex - richSpan.textRange.min + type.startRichSpan.text.length
            else
                startIndex - richSpan.textRange.min

        newRichParagraph.type.startRichSpan.paragraph = newRichParagraph
        newRichParagraph.type.startRichSpan.textRange = TextRange(
            0,
            newRichParagraph.type.startRichSpan.text.length
        )

        val beforeText =
            richSpan.text.substring(
                startIndex = 0,
                endIndex = textStartIndex
                    .coerceIn(0, richSpan.text.length)
            )
        val afterTextStartIndex =
            if (removeSliceIndex)
                textStartIndex + 1
            else
                textStartIndex
        val afterText =
            richSpan.text.substring(
                startIndex = afterTextStartIndex
                    .coerceIn(0, richSpan.text.length)
            )

        richSpan.text = beforeText
        richSpan.textRange = TextRange(
            richSpan.textRange.min,
            richSpan.textRange.min + beforeText.length
        )

        // We don't copy the current rich span style to the new rich span
        val newRichSpan = RichSpan(
            paragraph = newRichParagraph,
            parent = null,
            text = afterText,
            textRange = TextRange(
                startIndex,
                startIndex + afterText.length
            ),
            spanStyle = richSpan.fullSpanStyle,
        )

        newRichParagraph.children.add(newRichSpan)

        for (i in richSpan.children.lastIndex downTo 0) {
            val childRichSpan = richSpan.children[i]
            richSpan.children.removeAt(i)
            childRichSpan.parent = newRichSpan
            childRichSpan.paragraph = newRichParagraph
            newRichSpan.children.add(childRichSpan)
        }

        while (true) {
            previousRichSpan = currentRichSpan
            currentRichSpan = currentRichSpan.parent ?: break

            val index = currentRichSpan.children.indexOf(previousRichSpan)
            if (index in 0 until currentRichSpan.children.lastIndex) {
                ((index + 1)..currentRichSpan.children.lastIndex).forEach {
                    val childRichSpan = currentRichSpan.children[it]
                    childRichSpan.spanStyle = childRichSpan.fullSpanStyle
                    childRichSpan.parent = null
                    childRichSpan.paragraph = newRichParagraph
                    newRichParagraph.children.add(childRichSpan)
                }
                currentRichSpan.children.removeRange(index + 1, currentRichSpan.children.size)
            }
        }

        val index = richSpan.paragraph.children.indexOf(previousRichSpan)
        if (index in 0 until richSpan.paragraph.children.lastIndex) {
            ((index + 1)..richSpan.paragraph.children.lastIndex).forEach {
                val childRichSpan = richSpan.paragraph.children[it]
                childRichSpan.spanStyle = childRichSpan.fullSpanStyle
                childRichSpan.parent = null
                childRichSpan.paragraph = newRichParagraph
                newRichParagraph.children.add(childRichSpan)
            }
            richSpan.paragraph.children.removeRange(index + 1, richSpan.paragraph.children.size)
        }

        return newRichParagraph
    }

    /**
     * Разделяет [RichSpan] по [startIndex] и [richSpan], который содержит [startIndex].
     * Переданный [RichSpan] будет изменён и будет содержать только текст до [startIndex].
     * А новый [RichSpan] будет возвращён, содержащий текст после [startIndex].
     *
     * @param startIndex Начальный индекс для разделения.
     * @param richSpan [RichSpan], который содержит [startIndex].
     * @return Новый [RichSpan].
     */
    private fun RichSpan.slice(
        startIndex: Int,
        richSpan: RichSpan,
    ): RichSpan {
        val newRichSpan = RichSpan(
            paragraph = richSpan.paragraph,
        )

        var previousRichSpan: RichSpan
        var currentRichSpan: RichSpan = richSpan

        val textStartIndex = startIndex - richSpan.textRange.min
        val beforeText =
            if (textStartIndex > 0) richSpan.text.substring(0, textStartIndex) else "" // + ' '
        val afterText = richSpan.text.substring(textStartIndex + 1)

        richSpan.text = beforeText
        richSpan.textRange = TextRange(
            richSpan.textRange.min,
            richSpan.textRange.min + beforeText.length
        )

        val afterRichSpan = RichSpan(
            paragraph = richSpan.paragraph,
            parent = newRichSpan,
            text = afterText,
            textRange = TextRange(
                startIndex,
                startIndex + afterText.length
            ),
            spanStyle = richSpan.fullSpanStyle,
        )

        newRichSpan.children.add(afterRichSpan)

        for (i in richSpan.children.lastIndex downTo 0) {
            val childRichSpan = richSpan.children[i]
            richSpan.children.removeAt(i)
            childRichSpan.parent = afterRichSpan
            afterRichSpan.children.add(childRichSpan)
        }

        while (true) {
            previousRichSpan = currentRichSpan
            currentRichSpan = currentRichSpan.parent ?: break

            val index = currentRichSpan.children.indexOf(previousRichSpan)
            if (index in 0 until currentRichSpan.children.lastIndex) {
                ((index + 1)..currentRichSpan.children.lastIndex).forEach {
                    val childRichSpan = currentRichSpan.children[it]
                    childRichSpan.spanStyle = childRichSpan.fullSpanStyle
                    childRichSpan.parent = null
                    newRichSpan.children.add(childRichSpan)
                }
                currentRichSpan.children.removeRange(index + 1, currentRichSpan.children.size)
            }
        }

        val index = richSpan.paragraph.children.indexOf(previousRichSpan)
        if (index in 0 until richSpan.paragraph.children.lastIndex) {
            ((index + 1)..richSpan.paragraph.children.lastIndex).forEach {
                val childRichSpan = richSpan.paragraph.children[it]
                childRichSpan.spanStyle = childRichSpan.fullSpanStyle
                childRichSpan.parent = null
                newRichSpan.children.add(childRichSpan)
            }
            richSpan.paragraph.children.removeRange(index + 1, richSpan.paragraph.children.size)
        }

        return newRichSpan
    }

    /**
     * Объединяет два [RichParagraph] в один.
     * [firstParagraph] будет изменён и будет содержать текст как [firstParagraph], так и [secondParagraph].
     * А [secondParagraph] будет удалён.
     *
     * @param firstParagraph Первый [RichParagraph].
     * @param secondParagraph Второй [RichParagraph].
     */
    private fun mergeTwoRichParagraphs(
        firstParagraph: RichParagraph,
        secondParagraph: RichParagraph,
    ) {
        // Update the children paragraph of the second paragraph to the first paragraph.
        secondParagraph.updateChildrenParagraph(firstParagraph)

        // Add the children of the second paragraph to the first paragraph.
        firstParagraph.children.addAll(secondParagraph.children)

        // Remove the second paragraph from the rich paragraph list.
        richParagraphList.remove(secondParagraph)
    }

    /**
     * Обновляет [currentAppliedSpanStyle] до значения [SpanStyle], которое должно быть применено к текущему выделению.
     */
    private fun updateCurrentSpanStyle() {
        if (selection.collapsed) {
            val richSpan = getRichSpanByTextIndex(textIndex = selection.min - 1)

            currentAppliedRichSpanStyle = richSpan
                ?.fullStyle
                ?: RichSpanStyle.Default
            currentAppliedSpanStyle = richSpan
                ?.fullSpanStyle
                ?: RichSpanStyle.DefaultSpanStyle

//            if (
//                config.preserveStyleOnEmptyLine &&
//                (richSpan == null || (richSpan.isFirstInParagraph && richSpan.paragraph.isEmpty()))
//            ) {
//                val paragraphBefore =
//                    if (selection.min - 2 < 0)
//                        null
//                    else
//                        getRichParagraphByTextIndex(selection.min - 2)
//
//                if (paragraphBefore == null || paragraphBefore.isNotEmpty()) {
//                    toAddRichSpanStyle = currentRichSpanStyle
//                    toAddSpanStyle = currentSpanStyle
//                }
//            }
        } else {
            val richSpanList = getRichSpanListByTextRange(selection)

            currentAppliedRichSpanStyle = richSpanList
                .getCommonRichStyle()
                ?: RichSpanStyle.Default
            currentAppliedSpanStyle = richSpanList
                .getCommonStyle()
                ?: RichSpanStyle.DefaultSpanStyle
        }
    }

    /**
     * Возвращает общий [RichSpanStyle] для [RichSpan] в указанном [textRange].
     *
     * @param textRange [TextRange], для которого нужно получить общий [RichSpanStyle].
     * @return Общий [RichSpanStyle] для [RichSpan] в указанном [textRange].
     */
    private fun getCommonRichSpanStyleByTextRange(
        textRange: TextRange,
    ): RichSpanStyle {
        val richSpanList = getRichSpanListByTextRange(textRange)

        return richSpanList.getCommonRichStyle() ?: RichSpanStyle.Default
    }

    /**
     * Возвращает общий [SpanStyle] для [RichSpan] в указанном [textRange].
     *
     * @param textRange [TextRange], для которого нужно получить общий [SpanStyle].
     * @return Общий [SpanStyle] для [RichSpan] в указанном [textRange].
     */
    private fun getCommonSpanStyleByTextRange(
        textRange: TextRange,
    ): SpanStyle {
        val richSpanList = getRichSpanListByTextRange(textRange)

        return richSpanList.getCommonStyle() ?: RichSpanStyle.DefaultSpanStyle
    }

    /**
     * Обновляет [currentAppliedParagraphStyle] до значения [ParagraphStyle], которое должно быть применено к текущему выделению.
     */
    private fun updateCurrentParagraphStyle() {
        if (selection.collapsed) {
            val richParagraph = getRichParagraphByTextIndex(selection.min - 1)

            currentRichParagraphType = richParagraph?.type
                ?: richParagraphList.firstOrNull()?.type
                        ?: DefaultParagraph()
            currentAppliedParagraphStyle = richParagraph?.paragraphStyle
                ?: richParagraphList.firstOrNull()?.paragraphStyle
                        ?: RichParagraph.DefaultParagraphStyle
            isUnorderedList = richParagraph?.type is UnorderedList
            isOrderedList = richParagraph?.type is OrderedList
            isHeading = richParagraph?.type is Heading
            currentHeadingLevel = (richParagraph?.type as? Heading)?.level ?: 0
            isList = isUnorderedList || isOrderedList
            canIncreaseListLevel = richParagraph?.let { canIncreaseListLevel(listOf(it)) } == true
            canDecreaseListLevel = richParagraph?.let { canDecreaseListLevel(listOf(it)) } == true
        } else {
            val richParagraphList = getRichParagraphListByTextRange(selection)

            currentRichParagraphType = richParagraphList
                .getCommonType()
                ?: DefaultParagraph()
            currentAppliedParagraphStyle = richParagraphList
                .getCommonStyle()
                ?: ParagraphStyle()

            isUnorderedList = richParagraphList.all { it.type is UnorderedList }
            isOrderedList = richParagraphList.all { it.type is OrderedList }
            isHeading = richParagraphList.all { it.type is Heading }
            currentHeadingLevel =
                if (isHeading) (richParagraphList.firstOrNull()?.type as? Heading)?.level
                    ?: 0 else 0
            isList = richParagraphList.all { it.type is UnorderedList || it.type is OrderedList }
            canIncreaseListLevel = canIncreaseListLevel(richParagraphList)
            canDecreaseListLevel = canDecreaseListLevel(richParagraphList)
        }
    }

    public fun onTextLayout(
        textLayoutResult: TextLayoutResult,
        density: Density,
    ) {
        this.textLayoutResult = textLayoutResult
        adjustRichParagraphLayout(
            density = density,
        )
    }

    private fun adjustRichParagraphLayout(
        density: Density,
    ) {
        var isParagraphUpdated = false

        textLayoutResult?.let { textLayoutResult ->
            val offsetLimit =
                if (
                    textLayoutResult.multiParagraph.didExceedMaxLines &&
                    textLayoutResult.multiParagraph.maxLines > 0 &&
                    textLayoutResult.multiParagraph.maxLines != Int.MAX_VALUE
                )
                    textLayoutResult.getLineEnd(textLayoutResult.multiParagraph.maxLines - 1)
                else
                    textLayoutResult.layoutInput.text.text.length

            richParagraphList.forEachIndexed { index, richParagraph ->
                val paragraphType = richParagraph.type

                if (
                    paragraphType is ConfigurableStartTextWidth &&
                    paragraphType.startText.isNotEmpty() &&
                    paragraphType.startRichSpan.textRange.max <= offsetLimit
                ) {
                    val start =
                        textLayoutResult.getHorizontalPosition(
                            offset = paragraphType.startRichSpan.textRange.min,
                            usePrimaryDirection = true
                        )
                    val end =
                        textLayoutResult.getHorizontalPosition(
                            offset = paragraphType.startRichSpan.textRange.max,
                            usePrimaryDirection = true
                        )
                    val distanceSp =
                        with(density) {
                            (end - start).absoluteValue.toSp()
                        }

                    if (paragraphType.startTextWidth != distanceSp) {
                        paragraphType.startTextWidth = distanceSp
                        isParagraphUpdated = true
                    }
                }
            }
        }

        if (isParagraphUpdated)
            updateTextFieldValue(textFieldValue)
    }

    internal fun getLinkByOffset(offset: Offset): String? {
        val richSpan = getRichSpanByOffset(offset)
        val linkRichSpan = getLinkRichSpan(richSpan)

        return (linkRichSpan?.richSpanStyle as? RichSpanStyle.Link)?.url
    }

    internal fun isLink(offset: Offset): Boolean {
        val richSpan = getRichSpanByOffset(offset)
        val linkRichSpan = getLinkRichSpan(richSpan)

        return linkRichSpan != null
    }

    private fun getLinkRichSpan(initialRichSpan: RichSpan?): RichSpan? {
        var richSpan = initialRichSpan

        while (richSpan != null && richSpan.richSpanStyle !is RichSpanStyle.Link) {
            richSpan = richSpan.parent
        }

        return richSpan
    }

    public fun handleTripleClick() {
        val cursor = textFieldValue.selection.start

        val paragraph = richParagraphList.firstOrNull {
            cursor in it.getTextRange().min until it.getTextRange().max
        } ?: return

        val range = paragraph.getTextRange()

        val isLast = richParagraphList.lastOrNull() == paragraph
        val end = if (isLast) range.max else range.max + 1

        updateTextFieldValue(
            textFieldValue.copy(selection = TextRange(range.min, end))
        )
    }

    @OptIn(InternalRichTextApi::class)
    private fun getRichSpanByOffset(offset: Offset): RichSpan? {
        this.textLayoutResult?.let { textLayoutResult ->
            val position = textLayoutResult.getOffsetForPositionClamped(offset)
            return getRichSpanByTextIndex(position, true)
        }
        return null
    }

    private fun adjustSelection(
        pressPosition: Offset,
        newSelection: TextRange? = null,
    ) {
        val selection = newSelection ?: this.selection
        var pressX = pressPosition.x
        var pressY = pressPosition.y
        val textLayoutResult = this.textLayoutResult ?: return
        var index = 0
        var lastIndex = 0

        // Get the length of the text
        val textLength = textLayoutResult.layoutInput.text.length

        // Ensure pressY is within valid bounds
        pressY = pressY.coerceIn(0f, textLayoutResult.size.height.toFloat())

        for (i in 0 until textLayoutResult.lineCount) {
            val start = textLayoutResult.getLineStart(i)
            val top = textLayoutResult.getLineTop(i)

            if (i == 0) {
                if (start > 0f) {
                    pressX += start
                }

                if (top > 0f) {
                    pressY += top
                }
            }

            // Make sure pressY is within the current line's top position
            if (i == 0 && top > pressY) {
                break
            }

            if (top > pressY) {
                index = lastIndex
                break
            }

            lastIndex = index

            if (textLayoutResult.layoutInput.text.text.lastIndex == -1)
                break

            richParagraphList.getOrNull(index)?.let { paragraph ->
                val textRange = paragraph.getTextRange().coerceIn(
                    0, textLayoutResult.layoutInput.text.text.lastIndex
                )

                val pStartTop = textLayoutResult.getBoundingBox(textRange.min).top
                val pEndTop = textLayoutResult.getBoundingBox(textRange.max).top

                val pStartEndTopDiff = (pStartTop - pEndTop).absoluteValue
                val pEndTopLTopDiff = (pEndTop - top).absoluteValue

                if (pStartEndTopDiff < 2f || pEndTopLTopDiff < 2f || pEndTop < top) {
                    index++
                }
            }
        }

        if (index > richParagraphList.lastIndex)
            index = richParagraphList.lastIndex

        val selectedParagraph = richParagraphList.getOrNull(index) ?: return
        val nextParagraph = richParagraphList.getOrNull(index + 1)
        val nextParagraphStart =
            if (nextParagraph == null)
                null
            else
                (nextParagraph.getFirstNonEmptyChild() ?: nextParagraph.type.startRichSpan)
                    .textRange.min.minus(nextParagraph.type.startText.length)

        // Handle selection adjustments
        if (
            selection.collapsed &&
            selection.min == nextParagraphStart
        ) {
            updateTextFieldValue(
                textFieldValue.copy(
                    selection = TextRange(
                        (selection.min - 1).coerceAtLeast(0),
                        (selection.min - 1).coerceAtLeast(0)
                    )
                )
            )
        } else if (
            selection.collapsed &&
            index == richParagraphList.lastIndex &&
            selectedParagraph.isEmpty() &&
            selection.min == selectedParagraph.getFirstNonEmptyChild()?.textRange?.min?.minus(1)
        ) {
            updateTextFieldValue(
                textFieldValue.copy(
                    selection = TextRange(
                        (selection.min + 1).coerceAtMost(textLength - 1),
                        (selection.min + 1).coerceAtMost(textLength - 1)
                    )
                )
            )
        } else if (newSelection != null) {
            // Ensure newSelection is within valid bounds
            val adjustedSelection = TextRange(
                newSelection.start.coerceIn(0, textLength),
                newSelection.end.coerceIn(0, textLength)
            )
            updateTextFieldValue(
                textFieldValue.copy(
                    selection = adjustedSelection
                )
            )
        }
    }

    private fun getRichParagraphByTextIndex(
        textIndex: Int,
    ): RichParagraph? {
        if (singleParagraphMode || textIndex < 0)
            return richParagraphList.firstOrNull()

        var index = 0
        var paragraphIndex = -1
        return richParagraphList.fastFirstOrNull { richParagraphStyle ->
            paragraphIndex++
            val result = richParagraphStyle.getRichSpanByTextIndex(
                paragraphIndex = paragraphIndex,
                textIndex = textIndex,
                offset = index,
            )
            index = result.first
            result.second != null
        }
    }

    /**
     * Возвращает список [RichParagraph], которые содержат хотя бы часть указанного [searchTextRange].
     * Если ни один [RichParagraph] не содержит хотя бы часть указанного [searchTextRange], возвращается пустой список.
     *
     * @param searchTextRange Искомый диапазон [TextRange].
     * @return Список [RichParagraph], содержащих часть указанного [searchTextRange],
     * или пустой список, если таких [RichParagraph] не существует.
     */
    internal fun getRichParagraphListByTextRange(searchTextRange: TextRange): List<RichParagraph> {
        if (singleParagraphMode)
            return richParagraphList.toList()

        var index = 0
        val richParagraphList = mutableListOf<RichParagraph>()
        this.richParagraphList.fastForEachIndexed { paragraphIndex, richParagraphStyle ->
            val result = richParagraphStyle.getRichSpanListByTextRange(
                paragraphIndex = paragraphIndex,
                searchTextRange = searchTextRange,
                offset = index,
            )

            val paragraphStartIndex =
                if (paragraphIndex == 0)
                    0
                else if (searchTextRange.collapsed)
                    index + 1
                // If the search text range is not collapsed, we need to ignore the first index of the paragraph.
                // Because the first index of the paragraph is the last index of the previous paragraph.
                else
                    index + 2

            val isCursorInParagraph =
                searchTextRange.min in paragraphStartIndex..result.first ||
                        searchTextRange.max in paragraphStartIndex..result.first

            if (result.second.isNotEmpty() || isCursorInParagraph)
                richParagraphList.add(richParagraphStyle)

            index = result.first
        }
        return richParagraphList
    }

    private fun getOrCreateRichSpanByTextIndex(
        textIndex: Int,
        ignoreCustomFiltering: Boolean = false,
    ): RichSpan? {
        val richSpan =
            getRichSpanByTextIndex(
                textIndex = textIndex,
                ignoreCustomFiltering = ignoreCustomFiltering,
            )

        if (richSpan == null && textIndex < 0) {
            val firstParagraph = richParagraphList.firstOrNull() ?: return null
            val newRichSpan = RichSpan(
                paragraph = firstParagraph,
                text = "",
            )
            firstParagraph.children.add(0, newRichSpan)
            return newRichSpan
        }

        return richSpan
    }

    /**
     * Возвращает [RichSpan], который содержит указанный [textIndex].
     * Если ни один [RichSpan] не содержит указанный [textIndex], возвращается null.
     *
     * @param textIndex Индекс текста для поиска.
     * @return [RichSpan], содержащий указанный [textIndex], или null, если такого [RichSpan] не существует.
     */
    internal fun getRichSpanByTextIndex(
        textIndex: Int,
        ignoreCustomFiltering: Boolean = false,
    ): RichSpan? {
        // If the text index is equal or less than 0, we can return the first non-empty child of the first paragraph.
        if (textIndex < 0) {
            val firstParagraph = richParagraphList.firstOrNull() ?: return null
            return firstParagraph.getFirstNonEmptyChild(firstParagraph.type.startText.length)
        }

        var index = 0
        richParagraphList.fastForEachIndexed { paragraphIndex, richParagraph ->
            val result = richParagraph.getRichSpanByTextIndex(
                paragraphIndex = paragraphIndex,
                textIndex = textIndex,
                offset = index,
                ignoreCustomFiltering = ignoreCustomFiltering,
            )
            if (result.second != null)
                return result.second
            else
                index = result.first
        }
        return null
    }

    /**
     * Возвращает список [RichSpan], которые содержат хотя бы часть указанного [searchTextRange].
     * Если ни один [RichSpan] не содержит хотя бы часть указанного [searchTextRange], возвращается пустой список.
     *
     * @param searchTextRange Искомый диапазон [TextRange].
     * @return Список [RichSpan], содержащих часть указанного [searchTextRange], или пустой список, если таких [RichSpan] не существует.
     */
    private fun getRichSpanListByTextRange(searchTextRange: TextRange): List<RichSpan> {
        var index = 0
        val richSpanList = mutableListOf<RichSpan>()
        richParagraphList.fastForEachIndexed { paragraphIndex, richParagraphStyle ->
            val result = richParagraphStyle.getRichSpanListByTextRange(
                paragraphIndex = paragraphIndex,
                searchTextRange = searchTextRange,
                offset = index,
            )
            richSpanList.addAll(result.second)
            index = result.first
        }
        return richSpanList
    }

    /**
     * Вспомогательный внутренний метод для тестирования
     */
    internal fun printParagraphs() {
        richParagraphList.fastForEachIndexed { i, richParagraph ->
            println("Paragraph $i: $richParagraph")
        }
    }

    /**
     * Возвращает копию [RichTextState].
     * Может использоваться для создания снимка текущего состояния.
     *
     * @return Копия [RichTextState].
     */
    public fun copy(): RichTextState {
        val richParagraphList = richParagraphList.map { it.copy() }
        val richTextState = RichTextState(richParagraphList)
        richTextState.updateTextFieldValue(textFieldValue)
        richTextState.config.linkColor = config.linkColor
        richTextState.config.linkTextDecoration = config.linkTextDecoration
        richTextState.config.codeSpanColor = config.codeSpanColor
        richTextState.config.codeSpanBackgroundColor = config.codeSpanBackgroundColor
        richTextState.config.codeSpanStrokeColor = config.codeSpanStrokeColor
        richTextState.config.listIndent = config.listIndent
        richTextState.config.orderedListIndent = config.orderedListIndent
        richTextState.config.unorderedListIndent = config.unorderedListIndent
        richTextState.config.preserveStyleOnEmptyLine = config.preserveStyleOnEmptyLine

        return richTextState
    }

    /**
     * Обновляет [RichTextState] переданным [text].
     *
     * @param text Текст для обновления состояния.
     */
    public fun setText(
        text: String,
        selection: TextRange = TextRange(text.length),
    ): RichTextState {
        val textFieldValue =
            TextFieldValue(
                text = text,
                selection = selection,
            )

        onTextFieldValueChange(
            newTextFieldValue = textFieldValue
        )
        return this
    }

    /**
     * Обновляет [RichTextState] переданным [html].
     *
     * @param html HTML для установки.
     */
    public fun setHtml(html: String): RichTextState {
        val richParagraphList = RichTextStateHtmlParser.encode(html).richParagraphList
        updateRichParagraphList(richParagraphList)
        //textFieldValue = textFieldValue.copy(selection = TextRange(0))
        return this
    }

    public fun addHtmlFromBufferToCursorPosition(html: String) {
        if (annotatedString.text.isEmpty() && selection.max == 0) {
            val r = setHtml(html)
            selection = r.selection
            return
        }

        // 1. Декодируем HTML в список абзацев с помощью встроенного парсера
        val encodedState = RichTextStateHtmlParser.encode(html)
        val newParagraphs = encodedState.richParagraphList

//        if (newParagraphs.isEmpty()) return

        // 2. Получаем текущую позицию курсора (или конец выделения)
        val position = selection.min

        // 3. Если что-то выделено, удаляем выделенный текст перед вставкой
        if (!selection.collapsed) {
            removeSelectedText()
        }


        selection = TextRange(position)

        // 4. Используем внутренний метод для вставки абзацев в указанную позицию
        insertParagraphs(
            newParagraphs = newParagraphs,
            position = position
        )

        // 5. Обновляем визуальное представление
        //finishTextEditing()
    }

    /**
     * Вставляет HTML после текущего выделения.
     *
     * @param html HTML-контент для вставки.
     */
    public fun insertHtmlAfterSelection(html: String) {
        val newParagraphs = RichTextStateHtmlParser.encode(html).richParagraphList
        val position = selection.max

        selection = TextRange(selection.max)

        insertParagraphs(
            newParagraphs = newParagraphs,
            position = position,
        )
    }

    /**
     * Вставляет HTML в указанную [position] в [RichTextState].
     *
     * Поведение зависит от содержимого:
     * 1. Один параграф:
     *    - Вставка в текущий параграф
     *    - Стили сохраняются
     * 2. Несколько параграфов:
     *    - Текущий параграф разделяется
     *    - Новые вставляются между частями
     *    - Стили сохраняются
     *
     * Особые случаи:
     * - position = 0 → вставка в начало
     * - position = длина текста → вставка в конец
     * - пустой HTML → без изменений
     *
     * @param html HTML-контент
     * @param position позиция вставки
     */
    public fun insertHtml(html: String, position: Int) {
        val newParagraphs = RichTextStateHtmlParser.encode(html).richParagraphList

        insertParagraphs(
            newParagraphs = newParagraphs,
            position = position,
        )
    }

    /**
     * Обновляет [RichTextState] из Markdown.
     *
     * @param markdown Markdown-текст
     */
    public fun setMarkdown(markdown: String): RichTextState {
        val richParagraphList = RichTextStateMarkdownParser.encode(markdown).richParagraphList
        updateRichParagraphList(richParagraphList)
        //textFieldValue = textFieldValue.copy(selection = TextRange(0))
        return this
    }

    /**
     * Вставляет Markdown после выделения.
     *
     * @param markdown Markdown-контент
     */
    public fun insertMarkdownAfterSelection(markdown: String) {
        val newParagraphs = RichTextStateMarkdownParser.encode(markdown).richParagraphList
        val position = selection.max

        selection = TextRange(selection.max)

        insertParagraphs(
            newParagraphs = newParagraphs,
            position = position,
        )
    }

    /**
     * Вставляет указанное [markdown] содержимое в указанную [позицию] в [RichTextState].
     *
     * Поведение вставки зависит от содержимого Markdown и позиции вставки:
     * 1. Если Markdown содержит один абзац:
     *    - Содержимое вставляется точно в указанную позицию внутри существующего абзаца
     *    - Все стили (как строчные, так и блочные) сохраняются
     * 2. Если Markdown содержит несколько абзацев:
     *    - Текущий абзац разделяется в точке вставки
     *    - Новые абзацы вставляются между разделёнными частями
     *    - Все стили исходного абзаца сохраняются в обеих разделённых частях
     *
     * Особые случаи:
     * - Если позиция равна 0, содержимое вставляется в начало
     * - Если позиция равна длине текста, содержимое добавляется в конец
     * - Если Markdown пуст, изменения не производятся
     *
     * @param markdown Вставляемое содержимое в формате Markdown.
     * @param позиция Позиция, в которую нужно вставить содержимое Markdown.
     */
    public fun insertMarkdown(markdown: String, position: Int) {
        val newParagraphs = RichTextStateMarkdownParser.encode(markdown).richParagraphList

        insertParagraphs(
            newParagraphs = newParagraphs,
            position = position,
        )
    }

    /**
     * Вставляет указанные [newParagraphs] в указанную [позицию] в [RichTextState].
     *
     * Поведение вставки зависит от списка абзацев и позиции вставки:
     * 1. Если список содержит один абзац:
     *    - Содержимое вставляется точно в указанную позицию внутри существующего абзаца
     *    - Все стили (как строчные, так и блочные) сохраняются
     * 2. Если список содержит несколько абзацев:
     *    - Текущий абзац разделяется в точке вставки
     *    - Новые абзацы вставляются между разделёнными частями
     *    - Все стили исходного абзаца сохраняются в обеих разделённых частях
     *
     * Особые случаи:
     * - Если позиция равна 0, содержимое вставляется в начало
     * - Если позиция равна длине текста, содержимое добавляется в конец
     * - Если список пуст, изменения не производятся
     *
     * @param newParagraphs Новые абзацы для вставки.
     * @param позиция Позиция, в которую нужно вставить новые абзацы.
     */
    internal fun insertParagraphs(
        newParagraphs: List<RichParagraph>,
        position: Int
    ) {
        Log.d("RICH_CLIPBOARD_TAG", "insertParagraphs: position: $position")
        commitCompositionIfNeeded()

        // Баг 2 fix: coerceIn первым
        val safePosition = position.coerceIn(0, annotatedString.text.length)

        val beforeText = annotatedString.text.substring(0, safePosition)
        val afterText = annotatedString.text.substring(safePosition)
        val sanitizedText = beforeText.trimEnd() + afterText
        textFieldValue =
            textFieldValue.copy(text = sanitizedText, selection = TextRange(safePosition))

        if (newParagraphs.isEmpty()) {
            Log.e("RICH_CLIPBOARD_TAG", "newParagraph isEmpty")
            return
        }

        if (richParagraphList.isEmpty())
            richParagraphList.add(RichParagraph())

        richParagraphList.first().let { p ->
            if (p.children.isEmpty())
                p.children.add(RichSpan(paragraph = p))
        }

        val firstNewParagraph = newParagraphs.first()

        val richSpan = getRichSpanByTextIndex(
            textIndex = if (safePosition == 0) 0 else safePosition - 1,
            ignoreCustomFiltering = true,
        ) ?: return

        val targetParagraph = richSpan.paragraph
        val paragraphIndex = richParagraphList.indexOf(targetParagraph)

        // Баг 1 fix: вместо пустого параграфа — вставляем реальные newParagraphs
        if (safePosition == annotatedString.text.length) {
            if (newParagraphs.size == 1) {
                firstNewParagraph.updateChildrenParagraph(firstNewParagraph)
                // Мёрджим в последний параграф если он пустой, иначе добавляем новый
                if (targetParagraph.isEmpty()) {
                    targetParagraph.paragraphStyle = firstNewParagraph.paragraphStyle
                    targetParagraph.type = firstNewParagraph.type
                    targetParagraph.children.addAll(firstNewParagraph.children)
                    targetParagraph.updateChildrenParagraph(targetParagraph)
                } else {
                    val newParagraph = firstNewParagraph.copy()
                    richParagraphList.add(paragraphIndex + 1, newParagraph)
                }
            } else {
                newParagraphs.forEachIndexed { i, para ->
                    val newPara = para.copy()
                    richParagraphList.add(paragraphIndex + 1 + i, newPara)
                    newPara.updateChildrenParagraph(newPara)
                }
            }
            updateRichParagraphList()
            return
        }

        //val sliceIndex = max(position, richSpan.textRange.min)
        val sliceIndex = position

        val targetParagraphFirstHalf = targetParagraph
        val targetParagraphSecondHalf = targetParagraph.slice(
            richSpan = richSpan,
            startIndex = sliceIndex,
            removeSliceIndex = false,
        )

        if (targetParagraphFirstHalf.isEmpty() && firstNewParagraph.isNotEmpty()) {
            targetParagraphFirstHalf.paragraphStyle = firstNewParagraph.paragraphStyle
            targetParagraphFirstHalf.type = firstNewParagraph.type
        }

        if (newParagraphs.size == 1) {
            // Before position + Pasted Content + After Position

            firstNewParagraph.updateChildrenParagraph(targetParagraphFirstHalf)
            targetParagraphSecondHalf.updateChildrenParagraph(targetParagraphFirstHalf)
            targetParagraphFirstHalf.children.addAll(firstNewParagraph.children)
            targetParagraphFirstHalf.children.addAll(targetParagraphSecondHalf.children)
            targetParagraphFirstHalf.removeEmptyChildren()
        } else {
            // Before position + First pasted paragraph
            // Pasted paragraphs between first and last
            // Last pasted paragraph + After position

            val lastNewParagraph = newParagraphs.last()

            // Before position + First pasted paragraph
            firstNewParagraph.updateChildrenParagraph(targetParagraphFirstHalf)
            targetParagraphFirstHalf.children.addAll(firstNewParagraph.children)
            targetParagraphFirstHalf.removeEmptyChildren()

            // Pasted paragraphs between first and last
            if (newParagraphs.size >= 3) {
                val middleParagraphs = newParagraphs.subList(1, newParagraphs.size - 1)
                richParagraphList.addAll(paragraphIndex + 1, middleParagraphs)
            }

            // Last pasted paragraph + After position
            targetParagraphSecondHalf.updateChildrenParagraph(lastNewParagraph)
            lastNewParagraph.children.addAll(targetParagraphSecondHalf.children)
            lastNewParagraph.removeEmptyChildren()
            richParagraphList.add(paragraphIndex + newParagraphs.size - 1, lastNewParagraph)
        }

        // Update the state
        updateRichParagraphList()
    }

    /**
     * Обновляет [RichTextState] с помощью указанного [newRichParagraphList].
     * [RichTextState] будет обновлён с помощью указанного [newRichParagraphList], а [annotatedString] будет обновлён.
     *
     * @param newRichParagraphList Список [RichParagraph] для обновления [RichTextState].
     */
    internal fun updateRichParagraphList(newRichParagraphList: List<RichParagraph>) {
        richParagraphList.clear()
        richParagraphList.addAll(newRichParagraphList)
        updateRichParagraphList()
    }

    internal fun updateRichParagraphList() {
        if (richParagraphList.isEmpty())
            richParagraphList.add(RichParagraph())

        val beforeTextLength = annotatedString.text.length

        val newStyledRichSpanList = mutableListOf<RichSpan>()

        usedInlineContentMapKeys.clear()

        annotatedString = buildAnnotatedString {
            var index = 0
            richParagraphList.fastForEachIndexed { i, richParagraph ->
                withStyle(richParagraph.paragraphStyle.merge(richParagraph.type.getStyle(config))) {
                    withStyle(
                        richParagraph.getStartTextSpanStyle() ?: RichSpanStyle.DefaultSpanStyle
                    ) {
                        append(richParagraph.type.startText)
                    }

                    val richParagraphStartTextLength = richParagraph.type.startText.length
                    richParagraph.type.startRichSpan.textRange =
                        TextRange(index, index + richParagraphStartTextLength)
                    index += richParagraphStartTextLength
                    withStyle(RichSpanStyle.DefaultSpanStyle) {
                        index = append(
                            state = this@RichTextState,
                            richSpanList = richParagraph.children,
                            startIndex = index,
                            onStyledRichSpan = {
                                newStyledRichSpanList.add(it)
                            },
                        )

                        if (!singleParagraphMode) {
                            if (i != richParagraphList.lastIndex) {
                                append(' ')
                                index++
                            }
                        }
                    }
                }
            }
        }

        inlineContentMap.keys.forEach { key ->
            if (key !in usedInlineContentMapKeys) {
                inlineContentMap.remove(key)
            }
        }

        /*val selectionIndex =
            (textFieldValue.selection.min + (annotatedString.text.length - beforeTextLength)).coerceIn(0, annotatedString.text.length)*/

        val selectionIndex =
            if (beforeTextLength == 0)
                textFieldValue.selection.min.coerceIn(0, annotatedString.text.length)
            else
                (textFieldValue.selection.min + (annotatedString.text.length - beforeTextLength))
                    .coerceIn(0, annotatedString.text.length)

        styledRichSpanList.clear()
        textFieldValue = TextFieldValue(
            text = annotatedString.text,
            selection = TextRange(selectionIndex),
        )
        visualTransformation = VisualTransformation { _ ->
            TransformedText(
                text = annotatedString,
                offsetMapping = OffsetMapping.Identity
            )
        }
        styledRichSpanList.addAll(newStyledRichSpanList)

        // Clear un-applied styles
        toAddSpanStyle = SpanStyle()
        toRemoveSpanStyle = SpanStyle()
        toAddRichSpanStyle = RichSpanStyle.Default
        toRemoveRichSpanStyleKClass = RichSpanStyle.Default::class

        // Update current span style
        updateCurrentSpanStyle()

        // Update current paragraph style
        updateCurrentParagraphStyle()

        // Check paragraphs type
        checkParagraphsType()
    }

    private fun checkParagraphsType() {
        tempTextFieldValue = textFieldValue

        // Todo: It's not the best way to set start text span style, try to set it from parser
        var orderedListStartTextSpanStyle = SpanStyle()

        val levelNumberMap = hashMapOf<Int, Int>()

        richParagraphList.fastForEachIndexed { index, richParagraph ->
            val type = richParagraph.type

            if (type is ConfigurableListLevel) {
                // Clear the completed list levels
                levelNumberMap.keys.toList().fastForEach { level ->
                    if (level > type.level)
                        levelNumberMap.remove(level)
                }
            } else {
                // Clear the map if the current paragraph is not a list
                levelNumberMap.clear()
            }

            // Remove current list level from map if the current paragraph is an unordered list
            if (type is UnorderedList)
                levelNumberMap.remove(type.level)

            if (type is OrderedList) {
                val orderedListNumber =
                    levelNumberMap[type.level]
                        ?.plus(1)
                        ?: 1

                levelNumberMap[type.level] = orderedListNumber

                if (orderedListNumber == 1)
                    orderedListStartTextSpanStyle =
                        richParagraph.getFirstNonEmptyChild()?.spanStyle ?: SpanStyle()

                tempTextFieldValue = updateParagraphType(
                    paragraph = richParagraph,
                    newType = OrderedList(
                        number = orderedListNumber,
                        config = config,
                        startTextWidth = type.startTextWidth,
                        initialLevel = type.level
                    ),
                    textFieldValue = tempTextFieldValue,
                )

                type.number = orderedListNumber
            } else {
                orderedListStartTextSpanStyle = SpanStyle()
            }
        }

        updateTextFieldValue()
    }

    /**
     * Возвращает [RichTextState] в виде текстовой строки.
     *
     * @return Текстовая строка.
     */
    public fun toText(): String =
        toText(richParagraphList = richParagraphList)

    /**
     * Декодирует [RichTextState] в HTML-строку.
     *
     * @return HTML-строка.
     */
    public fun toHtml(selection: Boolean = false): String {
        return RichTextStateHtmlParser.decode(this, selection)
    }

    /**
     * Декодирует [RichTextState] в строку формата Markdown.
     *
     * @return Строка в формате Markdown.
     */
    public fun toMarkdown(): String {
        return RichTextStateMarkdownParser.decode(this, false)
    }

    /**
     * Очищает [RichTextState] и устанавливает [TextFieldValue] в пустое значение.
     */
    public fun clear() {
        richParagraphList.clear()
        richParagraphList.add(RichParagraph())
        updateTextFieldValue(TextFieldValue())
    }

    public companion object {
        public val Saver: Saver<RichTextState, *> = listSaver(
            save = {
                listOf(
                    it.toHtml(),
                    it.selection.start.toString(),
                    it.selection.end.toString(),
                )
            },
            restore = {
                val html = it[0]
                val selectionStart = it[1].toInt()
                val selectionEnd = it[2].toInt()
                val selection = TextRange(selectionStart, selectionEnd)
                val richTextState = RichTextState()
                richTextState.setHtml(html)
                richTextState.updateTextFieldValue(
                    richTextState.textFieldValue.copy(
                        selection = selection
                    )
                )
                richTextState
            }
        )
    }

    //by ilya bobrov

    private fun commitCompositionIfNeeded() {
        val compositionRange = textFieldValue.composition
        if (compositionRange != null) {
            val composedText =
                textFieldValue.text.substring(compositionRange.min, compositionRange.max)
            replaceTextRange(compositionRange, composedText)
            // После коммита очищаем составление
            textFieldValue = textFieldValue.copy(composition = null)
        }
    }
}
