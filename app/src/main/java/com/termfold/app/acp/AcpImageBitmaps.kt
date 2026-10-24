package com.termfold.app.acp

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Shared base64 image decode for chat thumbnails and full-screen preview. */
internal object AcpImageBitmaps {

    const val PREVIEW_MAX_LONG_SIDE = 2560

    data class Decoded(val bitmap: ImageBitmap, val width: Int, val height: Int)

    fun decodeThumbnail(image: AcpBlock.Image, targetPx: Int): ImageBitmap? =
        decode(image, maxLongSide = null, minShortSide = targetPx)?.bitmap

    fun decodePreview(image: AcpBlock.Image): Decoded? =
        decode(image, maxLongSide = PREVIEW_MAX_LONG_SIDE, minShortSide = null)

    private fun decode(
        image: AcpBlock.Image,
        maxLongSide: Int?,
        minShortSide: Int?,
    ): Decoded? {
        val bytes = runCatching { Base64.decode(image.base64, Base64.DEFAULT) }.getOrNull()
            ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        when {
            maxLongSide != null ->
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxLongSide) sample *= 2
            minShortSide != null ->
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= minShortSide) sample *= 2
        }
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull() ?: return null
        return Decoded(bitmap.asImageBitmap(), bounds.outWidth, bounds.outHeight)
    }
}
