package com.lxmusic.android.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shows a short toast describing what a control does, then runs [onClick]. */
fun toast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * Toolbar icon button that pops a one-line hint explaining its function, so
 * every action is self-describing (the app has no icon legend).
 */
@Composable
fun HintIconButton(
    icon: ImageVector,
    contentDescription: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 22.dp,
    buttonSize: Dp = SearchBarHeight,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            toast(context, hint)
            onClick()
        },
        modifier = modifier.size(buttonSize),
        colors = colors,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * Same as the icon overload above, but lets the caller draw arbitrary content
 * (used by the big play button so the buffering spinner can replace the icon).
 */
@Composable
fun HintIconButton(
    contentDescription: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = SearchBarHeight,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            toast(context, hint)
            onClick()
        },
        modifier = modifier.size(buttonSize),
        colors = colors,
    ) {
        content()
    }
}
