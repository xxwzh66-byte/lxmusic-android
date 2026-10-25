package com.lxmusic.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as scopeRotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.animation.core.animateFloatAsState

private const val FULL_TURN_MS = 4000

/**
 * A vinyl record that spins while [spinning] and freezes at its current angle
 * when paused (a plain infinite transition would snap back to 0).
 *
 * I-group upgrades over the v2.0 version:
 * - [tone]/[toneOn]: dominant artwork colour used for the groove tint and the
 *   fallback label so the disc always feels tied to the current song.
 * - More concentric grooves + a static specular sheen that does NOT rotate.
 * - Optional [toneArm]: pivots up when paused, drops onto the disc when playing.
 * - No artwork: theme-colour gradient label with a music note (I5).
 */
@Composable
fun RotatingArtwork(
    imageUrl: String?,
    spinning: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    tone: Color = Color(0xFF4F8BFF),
    toneOn: Boolean = false,
    toneArm: Boolean = false,
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        // Accumulating turns: resume continues from the current angle (I3).
        while (true) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(FULL_TURN_MS, easing = LinearEasing),
            )
        }
    }

    // Tonearm lift/drop (I1): 1f = resting off the disc, 0f = needle down.
    val armDrop by animateFloatAsState(
        targetValue = if (spinning) 0f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "tonearm",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {

        // --- rotating disc ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF3A3A3A), Color(0xFF141414), Color(0xFF0A0A0A)),
                    )
                )
                .rotate(rotation.value),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Grooves: many thin concentric rings between label and rim (I2).
                val radius = this.size.minDimension / 2f
                val stroke = 1.dp.toPx()
                var r = radius * 0.94f
                var shade = 0.10f
                while (r > radius * 0.60f) {
                    drawCircle(
                        color = if (toneOn) tone.copy(alpha = shade) else Color.White.copy(alpha = shade),
                        radius = r,
                        style = Stroke(width = stroke),
                    )
                    r -= 3.dp.toPx()
                    shade = if (shade > 0.055f) 0.05f else 0.10f
                }
            }

            if (imageUrl.isNullOrBlank()) {
                // No artwork (I5): theme-tone label with a music note.
                Box(
                    Modifier
                        .size(size * 0.62f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(tone.copy(alpha = 0.95f), tone.copy(alpha = 0.55f)),
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(size * 0.28f),
                    )
                }
            } else {
                // Album art, inset like a printed centre label (I4: 1:1 crop).
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size * 0.62f)
                        .clip(CircleShape),
                )
            }

            // Spindle hole.
            Box(
                Modifier
                    .size(size * 0.05f)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0A)),
            )
        }

        // --- static sheen on top of the disc (does not rotate) ---
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.White.copy(alpha = 0.06f),
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    )
                ),
        )

        // --- rim light ---
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = this.size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        // --- tonearm (I1) ---
        if (toneArm) {
            ToneArm(
                discSize = size,
                drop = armDrop,
                tint = if (toneOn) tone.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * A pivoting tonearm drawn with Canvas. The arm rotates around its rear pivot;
 * [drop] 0f = needle on the record, 1f = lifted to rest.
 */
@Composable
private fun ToneArm(
    discSize: Dp,
    drop: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // Geometry derived from the disc radius; drawn over the whole component.
    val armLen = discSize * 0.46f
    // Pivot sits exactly at the disc's top-right corner (over the rim).
    Canvas(modifier.fillMaxSize()) {
        val r = this.size.minDimension / 2f
        val pivot = Offset(
            r + r * 0.62f * 0.707f,   // disc center + (0.62R * cos45) right
            r - r * 0.62f * 0.707f,   // disc center - (0.62R * sin45) up
        )
        // -34 deg lifted, -18 deg dropped (negative = counter-clockwise).
        val angleDeg = -34f + (1f - drop) * 16f
        scopeRotate(degrees = angleDeg, pivot = pivot) {
            // counterweight behind the pivot
            drawCircle(
                color = tint.copy(alpha = 0.55f),
                radius = 5.dp.toPx(),
                center = Offset(pivot.x + 14.dp.toPx(), pivot.y),
            )
            // arm shaft reaching into the label area
            val end = Offset(
                pivot.x - armLen.toPx() * 0.86f,
                pivot.y + armLen.toPx() * 0.50f,
            )
            drawLine(
                color = tint,
                start = pivot,
                end = end,
                strokeWidth = 2.5.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            // bent headshell + needle at the far end
            drawLine(
                color = tint,
                start = end,
                end = Offset(end.x + 6.dp.toPx(), end.y + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawCircle(color = tint, radius = 3.dp.toPx(), center = Offset(end.x + 7.dp.toPx(), end.y + 5.dp.toPx()))
        }
        // pivot hub on top
        drawCircle(color = tint, radius = 4.dp.toPx(), center = pivot)
        drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 1.6.dp.toPx(), center = pivot)
    }
}
