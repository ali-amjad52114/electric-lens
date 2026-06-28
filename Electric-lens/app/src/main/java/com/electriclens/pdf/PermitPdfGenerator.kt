package com.electriclens.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.electriclens.vm.EvidenceItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Session-supplied inputs for the permit. The generator renders these verbatim
 * and does NOT read app data sources — [faultCode] is the actual VLM-read value
 * held in session; the rest are reference fields passed through from the loaded
 * manual / asset.
 */
data class PermitInput(
    val faultCode: String,
    val confidence: Float,
    val runtimeMs: Long,
    val faultType: String,
    val isolationPoints: String,
    val assetName: String,
    val vfdId: String,
    val location: String,
    val evidence: List<EvidenceItem>,
    val blockEvents: List<String>
)

/**
 * On-device LOTO evidence-package PDF generator.
 *
 * Renders a multi-section A4 (595 x 842 pt) permit using [PdfDocument] +
 * [Canvas]/[Paint] only — NO external libraries, NO cloud. Captured evidence
 * photos are drawn as scaled thumbnails, one row per [EvidenceItem], with
 * automatic pagination onto fresh pages when the content runs past the bottom
 * margin. The file is written to cacheDir (exposed by res/xml/file_paths.xml)
 * so the Permit screen can share it via FileProvider.
 *
 * SAFETY: this document records visible lockout evidence ONLY. It does not
 * certify a zero-energy state and does not replace required testing or the
 * site-approved LOTO procedure. Approved wording is used verbatim below — no
 * "equipment is safe" / "AI guarantees safety" language.
 */
object PermitPdfGenerator {

    // ---- A4 page geometry (points) -----------------------------------------
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private val CONTENT_LEFT = MARGIN
    private val CONTENT_RIGHT = PAGE_WIDTH - MARGIN
    private val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN

    // ---- Evidence row geometry ---------------------------------------------
    private const val ROW_HEIGHT = 96f
    private const val THUMB_WIDTH = 120f
    private const val THUMB_HEIGHT = 80f
    private const val ROW_GAP = 8f

    // ---- Colors -------------------------------------------------------------
    private val INK = Color.rgb(20, 24, 33)
    private val MUTED = Color.rgb(96, 104, 120)
    private val ACCENT = Color.rgb(11, 99, 173)
    private val WARN = Color.rgb(176, 84, 0)
    private val LINE = Color.rgb(205, 210, 220)
    private val THUMB_BG = Color.rgb(235, 237, 242)

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun generate(context: Context, input: PermitInput): File {
        val evidence = input.evidence
        val file = File(context.cacheDir, "electric-lens-permit.pdf")
        val document = PdfDocument()

        try {
            val generatedAtMs = System.currentTimeMillis()
            val permitId = "EL-" + String.format(Locale.US, "%06d", (generatedAtMs % 1_000_000L))

            // ---- Paints ----
            val titlePaint = textPaint(18f, INK, bold = true)
            val subTitlePaint = textPaint(11f, MUTED)
            val sectionPaint = textPaint(13f, ACCENT, bold = true)
            val labelPaint = textPaint(10f, MUTED, bold = true)
            val valuePaint = textPaint(11f, INK)
            val bodyPaint = textPaint(10.5f, INK)
            val warnPaint = textPaint(10.5f, WARN, bold = true)
            val disclaimerPaint = textPaint(9.5f, MUTED)
            val rowLabelPaint = textPaint(11f, INK, bold = true)
            val rowMetaPaint = textPaint(9.5f, MUTED)
            val footerPaint = textPaint(8.5f, MUTED)
            val linePaint = Paint().apply {
                color = LINE
                strokeWidth = 0.8f
                isAntiAlias = true
            }
            val rulePaint = Paint().apply {
                color = ACCENT
                strokeWidth = 1.6f
                isAntiAlias = true
            }

            // ---- Pagination state ----
            val pager = Pager(document)
            pager.newPage()
            var y = MARGIN + 6f

            // ---- Header ----
            pager.canvas().drawText(
                "Electric Lens — Electrical LOTO Evidence Package",
                CONTENT_LEFT, y, titlePaint
            )
            y += 18f
            pager.canvas().drawText(
                "On-device evidence record · No cloud · No network",
                CONTENT_LEFT, y, subTitlePaint
            )
            y += 10f
            pager.canvas().drawLine(CONTENT_LEFT, y, CONTENT_RIGHT, y, rulePaint)
            y += 20f

            // ---- Permit metadata block ----
            val metaRows = listOf(
                "Permit ID" to permitId,
                "Generated" to dateFmt.format(Date(generatedAtMs)),
                "Asset" to input.assetName,
                "VFD ID" to input.vfdId,
                "Location" to input.location,
                "Fault code" to input.faultCode,
                "Read confidence" to String.format(Locale.US, "%.2f", input.confidence),
                "Read runtime" to "${input.runtimeMs} ms (NPU)",
                "Fault type" to input.faultType,
                "Isolation points" to input.isolationPoints
            )

            y = drawSection(pager, "Permit Details", y, sectionPaint, rulePaint)
            for ((label, value) in metaRows) {
                pager.canvas().drawText(label.uppercase(Locale.US), CONTENT_LEFT, y, labelPaint)
                pager.canvas().drawText(value, CONTENT_LEFT + 140f, y, valuePaint)
                y += 18f
            }
            y += 8f

            // ---- Status line ----
            y = drawSection(pager, "Status", y, sectionPaint, rulePaint)
            pager.canvas().drawText(
                "LOTO evidence captured. Proceed to zero-energy verification.",
                CONTENT_LEFT, y, bodyPaint
            )
            y += 22f

            // ---- Evidence section ----
            y = drawSection(pager, "Captured Evidence", y, sectionPaint, rulePaint)

            if (evidence.isEmpty()) {
                pager.canvas().drawText(
                    "No evidence was captured during this session.",
                    CONTENT_LEFT, y, rowMetaPaint
                )
                y += 18f
            } else {
                evidence.forEachIndexed { index, item ->
                    // Paginate if this row would overflow the bottom margin.
                    if (y + ROW_HEIGHT > CONTENT_BOTTOM) {
                        pager.newPage()
                        y = MARGIN + 6f
                        y = drawSection(
                            pager, "Captured Evidence (cont.)", y, sectionPaint, rulePaint
                        )
                    }

                    val rowTop = y
                    // Thumbnail frame.
                    val thumbRect = RectF(
                        CONTENT_LEFT,
                        rowTop,
                        CONTENT_LEFT + THUMB_WIDTH,
                        rowTop + THUMB_HEIGHT
                    )
                    drawThumbnail(pager.canvas(), item.bitmap, thumbRect, linePaint)

                    // Text column.
                    val textX = CONTENT_LEFT + THUMB_WIDTH + 16f
                    var textY = rowTop + 16f
                    pager.canvas().drawText(
                        "${index + 1}. ${item.stepName}",
                        textX, textY, rowLabelPaint
                    )
                    textY += 16f
                    pager.canvas().drawText(
                        "Captured: ${tsFmt.format(Date(item.timestampMs))}",
                        textX, textY, rowMetaPaint
                    )
                    textY += 14f
                    pager.canvas().drawText(
                        "Evidence #${index + 1} of ${evidence.size}",
                        textX, textY, rowMetaPaint
                    )

                    // Row separator.
                    val rowBottom = rowTop + ROW_HEIGHT
                    pager.canvas().drawLine(
                        CONTENT_LEFT, rowBottom - ROW_GAP / 2f,
                        CONTENT_RIGHT, rowBottom - ROW_GAP / 2f, linePaint
                    )
                    y = rowBottom
                }
            }
            y += 12f

            // ---- Blocked / mismatch events ----
            y = drawSection(pager, "Blocked / Mismatch Events", y, sectionPaint, rulePaint)
            if (input.blockEvents.isEmpty()) {
                pager.canvas().drawText("None", CONTENT_LEFT, y, rowMetaPaint)
                y += 18f
            } else {
                input.blockEvents.forEach { event ->
                    if (y + 16f > CONTENT_BOTTOM) {
                        pager.newPage()
                        y = MARGIN + 6f
                        y = drawSection(
                            pager, "Blocked / Mismatch Events (cont.)", y, sectionPaint, rulePaint
                        )
                    }
                    pager.canvas().drawText("• $event", CONTENT_LEFT, y, bodyPaint)
                    y += 15f
                }
            }
            y += 12f

            // ---- Safety disclaimer ----
            if (y + 90f > CONTENT_BOTTOM) {
                pager.newPage()
                y = MARGIN + 6f
            }
            y = drawSection(pager, "Safety Disclaimer", y, sectionPaint, rulePaint)
            pager.canvas().drawText(
                "Visible lockout evidence only — not a zero-energy certification.",
                CONTENT_LEFT, y, warnPaint
            )
            y += 18f
            val disclaimerLines = listOf(
                "This document records visible lockout evidence only. It does NOT certify a",
                "zero-energy state, and it does NOT replace required electrical testing or the",
                "site-approved Lockout/Tagout (LOTO) procedure. Always verify the absence of",
                "energy with an approved tester and follow your facility's authorized procedure",
                "before performing any work."
            )
            for (line in disclaimerLines) {
                pager.canvas().drawText(line, CONTENT_LEFT, y, disclaimerPaint)
                y += 13f
            }

            // ---- Footers on every page ----
            pager.finishWithFooters(footerPaint, linePaint, permitId)

            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
        } catch (t: Throwable) {
            // Never throw to the caller for normal cases. Best-effort: ensure a
            // valid (possibly minimal) PDF exists at the expected path.
            ensureFallbackPdf(file)
        } finally {
            try {
                document.close()
            } catch (_: Throwable) {
            }
        }

        return file
    }

    // ---- Section header helper ---------------------------------------------
    private fun drawSection(
        pager: Pager,
        title: String,
        yIn: Float,
        sectionPaint: Paint,
        rulePaint: Paint
    ): Float {
        var y = yIn
        if (y + 30f > CONTENT_BOTTOM) {
            pager.newPage()
            y = MARGIN + 6f
        }
        pager.canvas().drawText(title, CONTENT_LEFT, y, sectionPaint)
        y += 6f
        pager.canvas().drawLine(CONTENT_LEFT, y, CONTENT_RIGHT, y, rulePaint)
        y += 18f
        return y
    }

    // ---- Thumbnail drawing (scaled, center-cropped into frame) --------------
    private fun drawThumbnail(canvas: Canvas, bitmap: Bitmap?, frame: RectF, linePaint: Paint) {
        // Background placeholder.
        val bg = Paint().apply { color = THUMB_BG; isAntiAlias = true }
        canvas.drawRect(frame, bg)

        if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
            // Scale-to-fit (preserve aspect ratio) inside the frame.
            val scale = minOf(
                frame.width() / bitmap.width,
                frame.height() / bitmap.height
            )
            val drawW = bitmap.width * scale
            val drawH = bitmap.height * scale
            val left = frame.left + (frame.width() - drawW) / 2f
            val top = frame.top + (frame.height() - drawH) / 2f
            val src = Rect(0, 0, bitmap.width, bitmap.height)
            val dst = RectF(left, top, left + drawW, top + drawH)
            val imgPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            canvas.drawBitmap(bitmap, src, dst, imgPaint)
        }

        // Border around the thumbnail frame.
        canvas.drawRect(frame, linePaint.asStroke())
    }

    // ---- Footer on each page -----------------------------------------------
    private fun Pager.finishWithFooters(footerPaint: Paint, linePaint: Paint, permitId: String) {
        val total = pageCount
        pages.forEachIndexed { idx, page ->
            val c = page.canvas
            val fy = PAGE_HEIGHT - MARGIN + 22f
            c.drawLine(CONTENT_LEFT, fy - 14f, CONTENT_RIGHT, fy - 14f, linePaint)
            c.drawText(
                "Electric Lens · $permitId · Generated on-device",
                CONTENT_LEFT, fy, footerPaint
            )
            val pageLabel = "Page ${idx + 1} of $total"
            val w = footerPaint.measureText(pageLabel)
            c.drawText(pageLabel, CONTENT_RIGHT - w, fy, footerPaint)
        }
        finishAll()
    }

    // ---- Fallback: guarantee a valid 1-page PDF exists ---------------------
    private fun ensureFallbackPdf(file: File) {
        try {
            val doc = PdfDocument()
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = doc.startPage(info)
            val p = textPaint(14f, INK, bold = true)
            page.canvas.drawText(
                "Electric Lens — Electrical LOTO Evidence Package",
                MARGIN, MARGIN + 20f, p
            )
            page.canvas.drawText(
                "Evidence package could not be fully rendered.",
                MARGIN, MARGIN + 44f, textPaint(11f, MUTED)
            )
            doc.finishPage(page)
            FileOutputStream(file).use { out -> doc.writeTo(out) }
            doc.close()
        } catch (_: Throwable) {
            // Last resort: leave whatever exists; do not throw to the caller.
        }
    }

    // ---- Paint helpers ------------------------------------------------------
    private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint().apply {
            this.color = color
            textSize = size
            isAntiAlias = true
            typeface = if (bold) {
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            } else {
                android.graphics.Typeface.DEFAULT
            }
        }

    private fun Paint.asStroke(): Paint = Paint(this).apply {
        style = Paint.Style.STROKE
    }

    /**
     * Small pagination helper. Pages are kept open until [finishAll] so that
     * per-page footers (with the correct total page count) can be drawn last.
     */
    private class Pager(private val document: PdfDocument) {
        val pages = mutableListOf<PdfDocument.Page>()
        val pageCount: Int get() = pages.size

        fun newPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo
                .Builder(PAGE_WIDTH, PAGE_HEIGHT, pages.size + 1)
                .create()
            val page = document.startPage(info)
            pages.add(page)
            return page
        }

        fun canvas(): Canvas = pages.last().canvas

        fun finishAll() {
            pages.forEach { document.finishPage(it) }
        }
    }
}
