package com.workflow.operaciones.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.workflow.core.DailyCodec
import com.workflow.core.DailyDocument
import com.workflow.core.DailySummary
import java.io.File

object Reports {
    fun json(context: Context, doc: DailyDocument): File = target(context, doc, "json").apply { writeBytes(DailyCodec.encode(doc)) }
    fun pdf(context: Context, doc: DailyDocument): File {
        val file = target(context, doc, "pdf")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.rgb(22, 59, 67); typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val pdf = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            var y = 0f
            var pageNumber = 0
            fun nextPage() {
                page?.let(pdf::finishPage)
                page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
                val canvas = page!!.canvas
                paint.textSize = 22f; paint.isFakeBoldText = true
                canvas.drawText("WorkFlow", 36f, 42f, paint)
                paint.textSize = 10f; paint.isFakeBoldText = false
                canvas.drawText("Informe diario · ${doc.day} · Revisión ${doc.revision}", 36f, 62f, paint)
                canvas.drawText("Página $pageNumber · Datos personales: comparte con destinatarios autorizados", 36f, 816f, paint)
                y = 88f
            }
            nextPage()
            DailySummary.lines(doc).forEach { paragraph ->
                paragraph.split('\n').forEach { raw ->
                    var line = ""
                    raw.split(Regex("\\s+")).forEach { word ->
                        // Split unusually long words too, avoiding clipped or invisible user content.
                        word.chunked(40).forEach { part ->
                            val candidate = if (line.isEmpty()) part else "$line $part"
                            if (paint.measureText(candidate) > 520 && line.isNotEmpty()) {
                                if (y > 785f) nextPage()
                                page!!.canvas.drawText(line, 36f, y, paint); y += 15
                                line = part
                            } else line = candidate
                        }
                    }
                    if (y > 785f) nextPage()
                    page!!.canvas.drawText(line, 36f, y, paint); y += 15
                }
            }
            page?.let(pdf::finishPage)
            file.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
        return file
    }
    private fun target(context: Context, doc: DailyDocument, extension: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        return File(dir, "WorkFlow_${doc.day}_${doc.originId.take(8)}_r${doc.revision}.$extension")
    }
    fun share(context: Context, file: File, whatsapp: Boolean) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "json") "application/json" else "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Informe WorkFlow", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { name ->
            runCatching { context.packageManager.getPackageInfo(name, 0) }.isSuccess
        }
        val launch = if (whatsapp && pkg != null) send.setPackage(pkg) else Intent.createChooser(send, "Compartir informe WorkFlow")
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
