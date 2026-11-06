package com.workflow.operaciones.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.workflow.core.*
import com.workflow.operaciones.R
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.*

/** Fixed WorkFlow identity. Vector PDF using original logo and bundled Nunito Sans. */
internal class VisualReportRenderer(private val context: Context) {
    private val colors = mapOf("petrol" to "#163B43", "teal" to "#168F95", "light" to "#56B9BC", "selected" to "#DCEEEE", "amber" to "#E8B66A", "yellow" to "#F2CD60", "red" to "#C44747", "blue" to "#356EAA", "green" to "#2F7D58", "bg" to "#F7F8F6", "secondary" to "#59656B", "border" to "#DFE5E3", "sage" to "#E7EFE3", "sky" to "#E2EEF3", "sand" to "#F2EEDF", "rose" to "#F8E7E7", "white" to "#FFFFFF")
    private val regular = requireNotNull(ResourcesCompat.getFont(context,R.font.nunito_regular))
    private val medium = requireNotNull(ResourcesCompat.getFont(context,R.font.nunito_semibold))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private lateinit var pdf: PdfDocument
    private lateinit var page: PdfDocument.Page
    private lateinit var report: VisualReport
    private var number=0
    private var width=595f;private var height=842f;private var y=0f
    private val margin=30f
    private val body get()=width-2*margin
    private val bottom get()=height-48
    private val canvas get()=page.canvas
    private fun color(key:String)=Color.parseColor(colors[key]?:colors.getValue("petrol"))
    private fun font(size:Float,bold:Boolean=false,tone:String="petrol") {
        paint.textSize=size;paint.typeface=if(bold)medium else regular;paint.color=color(tone);paint.style=Paint.Style.FILL
    }
    private fun text(value:String,x:Float,top:Float,size:Float=9f,bold:Boolean=false,tone:String="petrol") {
        font(size,bold,tone);canvas.drawText(value,x,top-paint.fontMetrics.ascent,paint)
    }
    private fun lines(value:String,maxWidth:Float,size:Float=9f):List<String> {
        font(size)
        return value.split('\n').flatMap { raw ->
            if(raw.isEmpty())listOf("") else buildList {
                var rest=raw
                while(rest.isNotEmpty()) {
                    var count=paint.breakText(rest,true,maxWidth.coerceAtLeast(12f),null).coerceAtLeast(1)
                    if(count<rest.length) { val space=rest.lastIndexOf(' ',count-1);if(space>0)count=space }
                    add(rest.take(count).trimEnd());rest=rest.drop(count).trimStart()
                }
            }
        }
    }
    private fun card(x:Float,top:Float,w:Float,h:Float,tone:String="white",radius:Float=15f) {
        paint.style=Paint.Style.FILL;paint.color=0x0C163B43
        canvas.drawRoundRect(x,top+2,x+w,top+h+2,radius,radius,paint)
        paint.color=color(tone);canvas.drawRoundRect(x,top,x+w,top+h,radius,radius,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=.5f;paint.color=color("border")
        canvas.drawRoundRect(x,top,x+w,top+h,radius,radius,paint);paint.style=Paint.Style.FILL
    }
    private fun ensure(h:Float) { if(y+h>bottom)newPage() }
    private fun newPage() {
        if(number>0)pdf.finishPage(page)
        page=pdf.startPage(PdfDocument.PageInfo.Builder(width.toInt(),height.toInt(),++number).create())
        canvas.drawColor(color("bg"))
        val logo=requireNotNull(context.getDrawable(R.drawable.workflow_logo))
        logo.setBounds(30,31,72,73);logo.draw(canvas)
        text("WorkFlow",83f,27f,24f,true);text("INFORMES DE OPERACIÓN",84f,60f,8f,false,"secondary")
        val titleLines=lines(report.title,body,23f)
        titleLines.forEachIndexed { i,line->text(line,margin,94f+29*i,23f,true) }
        var top=94f+titleLines.size*29
        lines(report.subtitle,body,9.5f).forEach { text(it,margin,top,9.5f,false,"secondary");top+=13 }
        top+=12;card(margin,top,body,31f,"selected",10f);text(report.period,margin+12,top+9,9.5f,true);y=top+48
        paint.color=color("border");canvas.drawLine(margin,height-35,width-margin,height-35,paint)
        text("WorkFlow · ${report.query.type.label}",margin,height-29,7.5f,false,"secondary")
        text("Página $number",width-90,height-29,7.5f,false,"secondary")
    }
    fun write(value:VisualReport,file:File) {
        report=value;width=if(value.compact)600f else 595f;height=if(value.compact)720f else 842f;number=0;pdf=PdfDocument()
        try {
            newPage();metrics(value.metrics)
            value.sections.forEach { section ->
                section.calendar?.let { calendar(section.title,it) }
                if(section.chart.isNotEmpty())chart(section)
                if(section.headers.isNotEmpty())table(section)
                if(section.note.isNotBlank())note(if(section.headers.isEmpty()&&section.chart.isEmpty()&&section.calendar==null)section.title else "Observaciones",section.note)
            }
            pdf.finishPage(page);file.outputStream().use(pdf::writeTo)
        } catch(e:Exception) { file.delete();throw e } finally { pdf.close() }
    }
    private fun metrics(values:List<ReportMetric>) {
        values.chunked(4).forEach { row ->
            val w=(body-10*(row.size-1))/row.size;val labels=row.map { lines(it.label,w-24,8.5f) }
            val h=max(63f,42f+12*(labels.maxOfOrNull { it.size }?:1));ensure(h+14)
            row.forEachIndexed { i,item ->
                val x=margin+i*(w+10);card(x,y,w,h,item.tone)
                var size=25f;font(size,true)
                while(paint.measureText(item.value)>w-24&&size>9){size--;font(size,true)}
                text(item.value,x+12,y+5,size,true)
                labels[i].forEachIndexed { j,label->text(label,x+12,y+39+j*11,8.5f) }
            };y+=h+14
        }
    }
    private fun note(title:String,value:String) {
        var remaining=lines(value,body-28,9.3f)
        do {
            ensure(90f)
            val count=min(remaining.size,((bottom-y-47)/13).toInt().coerceAtLeast(1));val chunk=remaining.take(count);val h=43f+chunk.size*13
            card(margin,y,body,h,"sand");text(title,margin+14,y+10,11f,true)
            chunk.forEachIndexed { i,line->text(line,margin+14,y+32+i*13,9.3f,false,"secondary") }
            remaining=remaining.drop(count);y+=h+14;if(remaining.isNotEmpty())newPage()
        }while(remaining.isNotEmpty())
    }
    private fun widths(headers:List<String>):List<Float> = when(headers.size) {
        1->listOf(1f);2->if(headers[0]=="N.º")listOf(.12f,.88f)else listOf(.3f,.7f)
        3->listOf(.35f,.25f,.4f);4->listOf(.20f,.28f,.22f,.30f)
        else->List(headers.size){1f/headers.size}
    }.map{it*(body-16)}
    private fun table(section:ReportSection) {
        val ws=widths(section.headers);val headerLines=section.headers.mapIndexed{i,h->lines(h,ws[i]-10,8f)}
        val headerHeight=12f*headerLines.maxOf{it.size}+12
        val rows=section.rows.ifEmpty{listOf(listOf("Sin registros para estos filtros.")+List(section.headers.size-1){""})}
        var index=0;var leftover:List<List<String>>?=null;var continued=false
        while(index<rows.size||leftover!=null) {
            ensure(80f+headerHeight)
            val top=y;val titleH=34f;val chunks=mutableListOf<List<List<String>>>();val heights=mutableListOf<Float>();var used=titleH+headerHeight+12
            while(index<rows.size||leftover!=null) {
                val cellLines=leftover?:rows[index].mapIndexed{col,cell->lines(cell,ws[col]-10,8.8f)}
                val n=cellLines.maxOf{it.size};val available=((bottom-top-used-14)/12).toInt()
                if(available<1)break
                val take=min(n,available)
                if(take<n&&chunks.isNotEmpty()&&n<=10)break
                chunks+=cellLines.map{it.take(take)};heights+=take*12f+12;used+=heights.last()
                if(take<n){leftover=cellLines.map{it.drop(take)};break}
                leftover=null;index++
            }
            card(margin,top,body,used);text(section.title+if(continued)" · continuación"else "",margin+14,top+10,11f,true)
            var yy=top+titleH;paint.color=color("bg");canvas.drawRoundRect(margin+8,yy,width-margin-8,yy+headerHeight,6f,6f,paint)
            var xx=margin+11
            headerLines.forEachIndexed{col,ls->ls.forEachIndexed{j,s->text(s,xx,yy+5+j*12,8f,false,"secondary")};xx+=ws[col]};yy+=headerHeight
            chunks.forEachIndexed{row,cells->
                val h=heights[row];if(row%2==1){paint.color=color("bg");canvas.drawRect(margin+8,yy,width-margin-8,yy+h,paint)}
                xx=margin+11
                cells.forEachIndexed{col,ls->
                    val tone=if(ls.size==1)ReportBuilder.statusTone(ls[0])else "selected";val isStatus=ls.size==1&&tone in listOf("sage","sand","rose")
                    if(isStatus){font(8.8f);val pw=(paint.measureText(ls[0])+12).coerceAtMost(ws[col]-6);paint.color=color(tone);canvas.drawRoundRect(xx-3,yy+3,xx+pw,yy+21,9f,9f,paint)}
                    ls.forEachIndexed{j,s->text(s,xx,yy+6+j*12,8.8f,false,if(isStatus&&tone=="rose")"red"else "petrol")};xx+=ws[col]
                };yy+=h
            }
            y=top+used+14;if(index<rows.size||leftover!=null){newPage();continued=true}
        }
    }
    private fun chart(section:ReportSection) {
        if(section.donut&&section.chart.size<=8) {
            val h=max(140f,45f+section.chart.size*18);ensure(h);card(margin,y,body,h);text(section.title,margin+14,y+10,11f,true)
            val rect=RectF(margin+28,y+44,margin+104,y+120);val total=section.chart.sumOf{it.value};var angle=-90f
            val tones=listOf("teal","amber","blue","red","light","green","yellow","petrol")
            section.chart.forEachIndexed{i,item->
                val sweep=if(total>0)(360*item.value/total).toFloat()else 0f
                paint.color=color(tones[i]);paint.style=Paint.Style.STROKE;paint.strokeWidth=8f;canvas.drawArc(rect,angle,sweep,false,paint);angle+=sweep
                paint.style=Paint.Style.FILL;paint.color=color(tones[i]);canvas.drawCircle(margin+133,y+47+i*18,3.5f,paint)
                text("${item.label} · ${ReportBuilder.number(item.value)}",margin+143,y+41+i*18,9f)
            }
            paint.style=Paint.Style.FILL;text(ReportBuilder.number(total),margin+48,y+67,19f,true);y+=h+14
        }else section.chart.chunked(15).forEachIndexed{part,values->
            val h=46f+values.size*26;ensure(h);card(margin,y,body,h);text(section.title+if(part>0)" · continuación"else "",margin+14,y+10,11f,true)
            val maxValue=values.maxOfOrNull{it.value}?.coerceAtLeast(1.0)?:1.0
            values.forEachIndexed{i,item->
                val yy=y+37+i*26;text(lines(item.label,125f,8.3f).firstOrNull().orEmpty(),margin+14,yy,8.3f)
                val x=margin+148;val w=body-210;paint.color=color("bg");canvas.drawRoundRect(x,yy+3,x+w,yy+10,4f,4f,paint)
                paint.color=color(when(item.tone){"sage","selected"->"teal";"sand"->"amber";"rose"->"red";else->item.tone})
                canvas.drawRoundRect(x,yy+3,x+(w*item.value/maxValue).toFloat(),yy+10,4f,4f,paint);text(ReportBuilder.number(item.value),x+w+10,yy,8.3f)
            };y+=h+14
        }
    }
    private fun calendar(title:String,value:ReportCalendar) {
        val month=YearMonth.of(value.year,value.month);val offset=month.atDay(1).dayOfWeek.value-1;val weeks=ceil((offset+month.lengthOfMonth())/7.0).toInt();val h=67f+weeks*38
        ensure(h);card(margin,y,body,h);text(title,margin+14,y+10,12f,true)
        val cw=(body-28)/7;val x0=margin+14
        listOf("L","M","X","J","V","S","D").forEachIndexed{i,s->text(s,x0+i*cw+5,y+37,8f,false,"secondary")}
        for(day in 1..month.lengthOfMonth()) {
            val cell=offset+day-1;val x=x0+(cell%7)*cw;val yy=y+56+(cell/7)*38;text("$day",x+5,yy,9f)
            value.days.firstOrNull{it.day==day}?.let{
                val past=month.atDay(day)<LocalDate.now();val tone=if(it.completed==it.total)"teal"else if(past)"amber"else "blue"
                paint.color=color(tone);canvas.drawCircle(x+7,yy+23,2f,paint);text("${it.total} tareas",x+13,yy+18,6.5f,false,tone)
            }
        };y+=h+14
    }
}
