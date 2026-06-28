package com.electriclens.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri

object ImageLoader {
    fun decode(context: Context, uri: Uri): Bitmap? = try {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { d, _, _ -> d.isMutableRequired = false }
            .copy(Bitmap.Config.ARGB_8888, false)
    } catch (t: Throwable) { null }
}
