package com.workflow.operaciones.data

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.workflow.core.*
import com.workflow.core.Module
import com.workflow.operaciones.export.Reports
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion=29)
class VisualReportsTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val day="2026-11-06"
    private val person=Operator(UUID.randomUUID().toString(),name="Persona sintética")
    private val asset=Asset(UUID.randomUUID().toString(),"EQ-TEST","Grúa","Inactivo temporal")
    private val item=StockItem(UUID.randomUUID().toString(),"Artículo sintético","unidades",5.0)
    private val ticket=Operation(time = "09:00", day=day,module=Module.TICKET,title="Procedimiento operativo",status="Emitido",operatorId=person.id,severity="Moderada",expected="Procedimiento completo",actual="Paso omitido",notes="Descripción y seguimiento de prueba.",recordedBy="Responsable sintético")
    private fun document():DailyDocument=DailyCodec.seal(DailyDocument(originId=UUID.randomUUID().toString(),supervisor="Responsable sintético",day=day,revision=1,
        operators=listOf(person),assets=listOf(asset),items=listOf(item),stockBalances=mapOf(item.id to 2.0),operations=listOf(
            ticket,Operation(time = "09:00", day=day,module=Module.TASK,title="Revisar equipos en taller",status="Pendiente"),
            Operation(time = "09:00", day=day,module=Module.ATTENDANCE,title="Asistencia",status="Presente",operatorId=person.id),
            Operation(time = "09:00", day=day,module=Module.FUEL,title="Combustible",status="Pendiente",assetId=asset.id,quantity=40.0),
            Operation(time = "09:00", day=day,module=Module.FAULT,title="Avería de prueba",status="Abierta",assetId=asset.id),
            Operation(time = "09:00", day=day,module=Module.MEAL,title="Cena",status="Solicitada",operatorIds=listOf(person.id)),
            Operation(time = "09:00", day=day,module=Module.PLAN,title="Turno previsto",status="Programado",operatorId=person.id),
            Operation(time = "09:00", day=day,module=Module.POSITION,title="Asignación",status="TRH",operatorId=person.id)
        )))
    @Test fun all_22_report_types_render_real_pdf_and_jpg_with_the_original_logo() {
        val doc=document()
        ReportType.entries.forEach { type ->
            val query=ReportQuery(type,day,operationId=if(type in listOf(ReportType.TICKET,ReportType.EVENT))ticket.id else null)
            val report=ReportBuilder.build(query,listOf(doc));val pdf=Reports.pdf(context,report)
            assertTrue(pdf.length()>1000)
            val images=Reports.images(context,pdf);assertTrue(images.isNotEmpty())
            val bitmap=BitmapFactory.decodeFile(images.first().absolutePath)
            assertTrue(bitmap.width>=1190);assertTrue(bitmap.height>=1440)
            var logoPixels=0
            for(x in 48 until 110)for(y in 40 until 102){val pixel=bitmap.getPixel(x,y);if(kotlin.math.abs(android.graphics.Color.green(pixel)-143)<8&&kotlin.math.abs(android.graphics.Color.red(pixel)-22)<8)logoPixels++}
            assertTrue("Logo original ausente en $type",logoPixels>100)
            if(type==ReportType.ASSETS) {
                var redArcPixels=0
                for(x in 48 until bitmap.width-48 step 2)for(y in 180 until bitmap.height-70 step 2) {
                    val c=bitmap.getPixel(x,y)
                    if(kotlin.math.abs(android.graphics.Color.red(c)-196)<14 && kotlin.math.abs(android.graphics.Color.green(c)-71)<14 && kotlin.math.abs(android.graphics.Color.blue(c)-71)<14)redArcPixels++
                }
                assertTrue("El estado de equipos inactivos debe usar el rojo de WorkFlow",redArcPixels>50)
            }
            bitmap.recycle()
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"reporte-real-${type.ordinal+1}-${type.name}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/WorkFlowDialogProof")
            }
            val uri=requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
            requireNotNull(context.contentResolver.openOutputStream(uri)).use{output->images.first().inputStream().use{it.copyTo(output)}}
            pdf.delete();images.forEach{it.delete()}
        }
    }
    @Test fun long_ticket_details_and_table_rows_continue_on_extra_pages() {
        val long=ticket.copy(notes="Detalle largo con áéíóú y seguimiento. ".repeat(75))
        val d=document().copy(operations=listOf(long))
        val report=ReportBuilder.build(ReportQuery(ReportType.TICKET,day,operationId=long.id),listOf(d))
        assertTrue(report.text().contains(long.notes.trimEnd()))
        val pdf=Reports.pdf(context,report);val pages=Reports.images(context,pdf)
        assertTrue("La descripción extensa debe continuar sin recortarse",pages.size>=2)
        pdf.delete();pages.forEach{it.delete()}
    }
    @Test fun csv_files_and_mixed_share_intents_preserve_every_attachment_and_read_grant() {
        val query=ReportQuery(ReportType.CLOSE,day)
        val doc=document()
        val tables=Reports.csv(context,query,listOf(doc))
        assertTrue(tables.size>=6)
        tables.forEach { assertTrue(it.readText().startsWith("\uFEFF")) }
        val pdf=Reports.pdf(context,ReportBuilder.build(query,listOf(doc)))
        val secret="Frase de prueba de transferencia".toCharArray()
        val progress=try{Reports.protectedReport(context,doc,secret)}finally{secret.fill('\u0000')}
        val files=tables+pdf+progress
        val intent=Reports.shareIntent(context,files)
        assertEquals(android.content.Intent.ACTION_SEND_MULTIPLE,intent.action)
        assertEquals("*/*",intent.type)
        assertEquals(files.size,intent.clipData!!.itemCount)
        assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val single=Reports.shareIntent(context,listOf(progress))
        assertEquals(ProtectedReport.MIME_TYPE,single.type)
        val open=android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(single.clipData!!.getItemAt(0).uri,ProtectedReport.MIME_TYPE)
        assertTrue(context.packageManager.queryIntentActivities(open,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).any{it.activityInfo.packageName==context.packageName})
        files.forEach{it.delete()}
    }

}
