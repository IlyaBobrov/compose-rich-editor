package com.mohamedrejeb.richeditor.ui.toolbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
public fun LinkToolbar(
    url: String,
    iconRemove: @Composable () -> Unit = {
        Icon(Icons.Filled.Delete, contentDescription = "Remove Link")
    },
    iconOpen: @Composable () -> Unit = {
        Icon(Icons.TwoTone.Share, contentDescription = "Open Link")
    },
    iconEdit: @Composable () -> Unit = {
        Icon(Icons.Default.Edit, contentDescription = "Edit Link")
    },
    iconClose: @Composable () -> Unit = {
        Icon(Icons.Default.Close, contentDescription = "Dismiss")
    },
    backgroundColor: Color? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    border: BorderStroke? = null,
    shadowElevation: Dp = 2.dp,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = shape,
        border = border,
        shadowElevation = shadowElevation,
        color = backgroundColor ?: MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(8.dp).then(modifier)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка удаления (unlink)
            IconButton(onClick = onRemove) { iconRemove() }
            // Кнопка перехода
            IconButton(onClick = onOpen) { iconOpen() }
            // Кнопка редактирования
            IconButton(onClick = onEdit) { iconEdit() }

            VerticalDivider(modifier = Modifier.height(24.dp))
            // Кнопка закрытия
            IconButton(onClick = onDismiss) { iconClose() }
        }
    }
}