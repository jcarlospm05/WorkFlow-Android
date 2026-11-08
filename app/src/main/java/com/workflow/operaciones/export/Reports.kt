package com.workflow.operaciones.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import androidx.core.content.FileProvider
import com.workflow.core.*
import java.io.File
import java.util.UUID

object Reports {
    fun protectedReport(context: Context, doc: DailyDocument, password: CharArray): File {
        val bytes = ProtectedReport.encode(doc, password)
        val file = target(context, "Jornada_${doc.day}", ProtectedReport.EXTENSION)
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
        return file
    }
    fun pdf(context: Context, doc: DailyDocument): File = pdf(context, ReportBuilder.build(ReportQuery(ReportType.GENERAL, doc.day), listOf(doc)))
    fun pdf(context: Context, report: VisualReport): File = target(context, "${report.query.type.name}_${report.query.from}_${report.query.to}", "pdf").also { VisualReportRenderer(context).write(report, it) }
    fun images(context: Context, report: VisualReport): List<File> = images(context, pdf(context, report))
    /** Preview and JPG use the actual PDF layout, keeping a single visual identity. */
    fun images(context: Context, pdf: File): List<File> {
        val output = mutableListOf<File>()
        try {
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width*2, page.height*2, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val file = target(context, "${pdf.nameWithoutExtension}_p${index+1}", "jpg")
                            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) }
                            output += file
                        } finally { bitmap.recycle() }
                    }
                }
            }
            return output
        } catch (e: Exception) { output.forEach { it.delete() }; throw e }
    }
    fun csv(context: Context, query: ReportQuery, documents: List<DailyDocument>): List<File> =
        ReportCsv.build(query, documents).map { table -> target(context, "${table.name}_${query.from}_${query.to}", "csv").also { it.writeBytes(table.encode()) } }
    private fun target(context: Context, name: String, extension: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val cutoff = System.currentTimeMillis()-24*60*60*1000L
        dir.listFiles()?.filter { it.lastModified()<cutoff }?.forEach { it.delete() }
        return File(dir, "WorkFlow_${name}_${UUID.randomUUID().toString().take(8)}.$extension")
    }
    private fun launchShare(context: Context, send: Intent, whatsapp: Boolean) {
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { name -> runCatching { context.packageManager.getPackageInfo(name,0) }.isSuccess }
        val launch = if (whatsapp && pkg!=null) send.setPackage(pkg) else Intent.createChooser(send,"Compartir WorkFlow")
        try { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: android.content.ActivityNotFoundException) {
            try { context.startActivity(Intent.createChooser(send.setPackage(null), "Compartir WorkFlow").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context, "No hay una aplicación disponible para compartir este archivo", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    fun share(context: Context, file: File, whatsapp: Boolean) = share(context,listOf(file),whatsapp)
    fun share(context: Context, files: List<File>, whatsapp: Boolean) = launchShare(context,shareIntent(context,files),whatsapp)
    internal fun shareIntent(context: Context, files: List<File>): Intent {
        require(files.isNotEmpty())
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context,"${context.packageName}.files",it) })
        val send = Intent(if (uris.size==1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            val types = files.map { when(it.extension) { ProtectedReport.EXTENSION -> ProtectedReport.MIME_TYPE; "jpg" -> "image/jpeg"; "csv" -> "text/csv"; else -> "application/pdf" } }.distinct()
            type = types.singleOrNull() ?: "*/*"
            if (uris.size==1) putExtra(Intent.EXTRA_STREAM,uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            clipData = ClipData.newRawUri("WorkFlow",uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return send
    }
    fun shareText(context: Context, text: String, whatsapp: Boolean = true) = launchShare(context,Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text) },whatsapp)
}
