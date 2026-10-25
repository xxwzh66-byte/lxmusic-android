package com.lxmusic.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context

/**
 * Blurs artwork and extracts a dominant colour to build the dynamic gradient
 * background used by the now-playing screen.
 */
object ArtworkColors {

    data class Scheme(val primary: Color, val secondary: Color, val onPrimary: Color)

    private val fallback = Scheme(
        primary = Color(0xFF1E88E5),
        secondary = Color(0xFF2A2A72),
        onPrimary = Color.White,
    )

    /** Synchronous default used before artwork is loaded. */
    fun fallbackScheme(): Scheme = fallback

    suspend fun schemeFor(context: Context, url: String?): Scheme = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext fallback
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(120)
                .build()
            val result = context.imageLoader.execute(request)
            val drawable = (result as? SuccessResult)?.drawable ?: return@withContext fallback
            val bitmap = drawable.toBitmap()
            val palette = Palette.from(bitmap).generate()
            val primary = palette.getVibrantColor(palette.getMutedColor(0xFF1E88E5.toInt()))
            val dark = palette.getDarkMutedColor(0xFF17173A.toInt())
            Scheme(
                primary = Color(primary),
                secondary = Color(dark),
                onPrimary = if (palette.getVibrantSwatch()?.bodyTextColor == 0xFF000000.toInt()) Color.Black else Color.White,
            )
        } catch (_: Exception) {
            fallback
        }
    }

    private fun android.graphics.drawable.Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = intrinsicWidth.coerceAtLeast(1)
        val h = intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bmp
    }

    /** Returns a small (downscaled) copy of the artwork; the UI blurs it. */
}