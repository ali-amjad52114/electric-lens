package com.electriclens.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** Offline PDF → text. PDFBox-Android, no network. Caller runs this off-thread. */
object PdfTextExtractor {
    @Volatile private var initialized = false

    fun extractFromUri(context: Context, uri: Uri): String {
        ensureInit(context)
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ""
            PDDocument.load(input).use { doc -> return PDFTextStripper().getText(doc) }
        }
    }

    fun extractFromAsset(context: Context, assetPath: String): String {
        ensureInit(context)
        context.assets.open(assetPath).use { input ->
            PDDocument.load(input).use { doc -> return PDFTextStripper().getText(doc) }
        }
    }

    private fun ensureInit(context: Context) {
        if (!initialized) { PDFBoxResourceLoader.init(context.applicationContext); initialized = true }
    }
}
