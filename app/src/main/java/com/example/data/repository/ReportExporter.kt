package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.School
import java.io.File
import java.io.FileOutputStream

object ReportExporter {

    fun exportPdfReport(
        context: Context,
        school: School?,
        reportTitle: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        footerSummary: String = ""
    ): Result<File> {
        return try {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val safeName = reportTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            val outFile = File(dir, "${safeName}_${System.currentTimeMillis()}.pdf")

            val doc = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f

            val navyPaint = Paint().apply {
                color = Color.parseColor("#0F294A")
                style = Paint.Style.FILL
            }
            val headerTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val subHeaderPaint = Paint().apply {
                color = Color.parseColor("#BAE6FD")
                textSize = 10f
                isAntiAlias = true
            }
            val titlePaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#1E293B")
                textSize = 9.5f
                isAntiAlias = true
            }
            val boldBodyPaint = Paint().apply {
                color = Color.parseColor("#0F294A")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val linePaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
            }

            var pageNumber = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas

            fun drawHeader() {
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 76f, navyPaint)
                canvas.drawText(school?.name ?: "BL School Hub", margin, 32f, headerTextPaint)
                val meta = listOfNotNull(
                    school?.schoolCode?.takeIf { it.isNotBlank() },
                    school?.city?.takeIf { it.isNotBlank() },
                    school?.contactNumber?.takeIf { it.isNotBlank() }
                ).joinToString("  •  ")
                canvas.drawText(meta.ifBlank { "Your School, One Digital Hub." }, margin, 52f, subHeaderPaint)
            }

            drawHeader()
            var y = 102f
            canvas.drawText(reportTitle, margin, y, titlePaint)
            y += 16f
            canvas.drawText(subtitle, margin, y, bodyPaint)
            y += 18f

            val colCount = headers.size.coerceAtLeast(1)
            val usableWidth = pageWidth - (margin * 2)
            val colWidth = usableWidth / colCount

            fun drawTableHeaders() {
                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
                y += 14f
                headers.forEachIndexed { i, h ->
                    canvas.drawText(h.take(18), margin + (i * colWidth), y, boldBodyPaint)
                }
                y += 8f
                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
                y += 14f
            }

            drawTableHeaders()

            for (row in rows) {
                if (y > pageHeight - 70f) {
                    doc.finishPage(page)
                    pageNumber++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    drawHeader()
                    y = 102f
                    drawTableHeaders()
                }
                row.forEachIndexed { i, cell ->
                    if (i < colCount) {
                        canvas.drawText(cell.take(20), margin + (i * colWidth), y, bodyPaint)
                    }
                }
                y += 16f
            }

            if (footerSummary.isNotBlank()) {
                y += 12f
                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
                y += 16f
                canvas.drawText(footerSummary, margin, y, boldBodyPaint)
            }

            doc.finishPage(page)
            FileOutputStream(outFile).use { doc.writeTo(it) }
            doc.close()
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportCsvReport(
        context: Context,
        reportTitle: String,
        headers: List<String>,
        rows: List<List<String>>
    ): Result<File> {
        return try {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val safeName = reportTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            val outFile = File(dir, "${safeName}_${System.currentTimeMillis()}.csv")

            val sb = StringBuilder()
            fun escapeCsv(v: String): String = "\"${v.replace("\"", "\"\"")}\""
            sb.appendLine(headers.joinToString(",") { escapeCsv(it) })
            rows.forEach { r -> sb.appendLine(r.joinToString(",") { escapeCsv(it) }) }

            outFile.writeText(sb.toString())
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareReportFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
