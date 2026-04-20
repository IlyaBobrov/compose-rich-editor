package com.mohamedrejeb.richeditor.parser.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

internal val MarkBackgroundColor = Color.Yellow
internal val SmallFontSize = 0.8f.em

internal val BoldSpanStyle = SpanStyle(fontWeight = FontWeight.Bold)
internal val ItalicSpanStyle = SpanStyle(fontStyle = FontStyle.Italic)
internal val UnderlineSpanStyle = SpanStyle(textDecoration = TextDecoration.Underline)
internal val StrikethroughSpanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
internal val SubscriptSpanStyle = SpanStyle(baselineShift = BaselineShift.Subscript)
internal val SuperscriptSpanStyle = SpanStyle(baselineShift = BaselineShift.Superscript)
internal val MarkSpanStyle = SpanStyle(background = MarkBackgroundColor)
internal val SmallSpanStyle = SpanStyle(fontSize = SmallFontSize)
public val H1SpanStyle: SpanStyle = SpanStyle(fontSize = 34.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp)
public val H2SpanStyle: SpanStyle = SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
public val H3SpanStyle: SpanStyle = SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp)
public val H4SpanStyle: SpanStyle = SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp)
public val H5SpanStyle: SpanStyle = SpanStyle(fontSize = 0.83.em, fontWeight = FontWeight.Normal)
public val H6SpanStyle: SpanStyle = SpanStyle(fontSize = 0.75.em, fontWeight = FontWeight.Normal)