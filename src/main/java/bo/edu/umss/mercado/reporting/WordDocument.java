package bo.edu.umss.mercado.reporting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Small OOXML writer: real Word tables and native charts with embedded editable workbooks. */
final class WordDocument {
    private static final String R="http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    private final StringBuilder body=new StringBuilder();
    private final Map<String,byte[]> parts=new LinkedHashMap<>();
    private int charts;
    static String x(Object value){return ReportExport.xml(Objects.toString(value,"Sin datos"));}
    void paragraph(Object text){paragraph(text,false,0);}
    void heading(Object text,int level){paragraph(text,true,level);}
    private void paragraph(Object text,boolean bold,int level){
        body.append("<w:p><w:pPr>");
        if(level>0)body.append("<w:keepNext/><w:outlineLvl w:val=\"").append(level-1).append("\"/>");
        body.append("<w:spacing w:after=\"120\"/></w:pPr><w:r><w:rPr>");
        if(bold)body.append("<w:b/><w:sz w:val=\"").append(level==1?32:26).append("\"/>");
        body.append("</w:rPr>").append(text(text)).append("</w:r></w:p>");
    }
    private static String text(Object value){
        StringBuilder out=new StringBuilder();String[] lines=Objects.toString(value,"Sin datos").split("\\R",-1);
        for(int i=0;i<lines.length;i++){if(i>0)out.append("<w:br/>");out.append("<w:t xml:space=\"preserve\">").append(x(lines[i])).append("</w:t>");}
        return out.toString();
    }
    void table(List<? extends List<?>> rows){table(rows,false);}
    void table(List<? extends List<?>> rows,boolean heat){
        if(rows.isEmpty())return;
        int cols=rows.stream().mapToInt(List::size).max().orElse(1);int width=9360/Math.max(1,cols);
        double max=rows.stream().skip(1).flatMap(List::stream).filter(Number.class::isInstance).map(Number.class::cast).mapToDouble(Number::doubleValue).max().orElse(0);
        body.append("<w:tbl><w:tblPr><w:tblW w:w=\"9360\" w:type=\"dxa\"/><w:tblBorders>");
        for(String border:List.of("top","left","bottom","right","insideH","insideV"))body.append("<w:").append(border).append(" w:val=\"single\" w:sz=\"4\" w:color=\"CCD9D6\"/>");
        body.append("</w:tblBorders></w:tblPr><w:tblGrid>");for(int i=0;i<cols;i++)body.append("<w:gridCol w:w=\"").append(width).append("\"/>");body.append("</w:tblGrid>");
        for(int r=0;r<rows.size();r++){
            body.append("<w:tr>");if(r==0)body.append("<w:trPr><w:tblHeader/></w:trPr>");
            for(int c=0;c<cols;c++){
                Object value=c<rows.get(r).size()?rows.get(r).get(c):"";String color=r==0?"E7EEE9":"FFFFFF";
                if(heat&&r>0&&c>0&&value instanceof Number n&&max>0){int v=(int)(255-155*n.doubleValue()/max);color=String.format(Locale.ROOT,"%02X%02XFF",v,v);}
                body.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(width).append("\" w:type=\"dxa\"/><w:shd w:fill=\"").append(color).append("\"/></w:tcPr><w:p><w:r>");
                if(r==0)body.append("<w:rPr><w:b/></w:rPr>");body.append(text(value)).append("</w:r></w:p></w:tc>");
            }
            body.append("</w:tr>");
        }
        body.append("</w:tbl><w:p/>");
    }
    record Series(String name,List<?> values){}
    void chart(String title,String kind,List<?> labels,List<Series> series)throws IOException{
        if(labels.isEmpty()||series.isEmpty()||series.stream().flatMap(s->s.values().stream()).noneMatch(Number.class::isInstance)){paragraph("Sin datos suficientes para representar: "+title);return;}
        int id=++charts;String name="chart"+id;
        var workbook=new ArrayList<List<Object>>();var headers=new ArrayList<Object>();headers.add(kind.equals("scatter")?"X":"Categoría");series.forEach(s->headers.add(s.name()));workbook.add(headers);
        for(int i=0;i<labels.size();i++){var row=new ArrayList<Object>();row.add(labels.get(i)==null?"":labels.get(i));for(var s:series)row.add(i<s.values().size()&&s.values().get(i)!=null?s.values().get(i):"");workbook.add(row);}
        parts.put("word/embeddings/"+name+".xlsx",ReportExport.xlsx(workbook));
        parts.put("word/charts/"+name+".xml",bytes(chartXml(title,kind,labels,series)));
        parts.put("word/charts/_rels/"+name+".xml.rels",bytes(rels("<Relationship Id=\"rId1\" Type=\""+R+"package\" Target=\"../embeddings/"+name+".xlsx\"/>")));
        body.append("<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"5943600\" cy=\"3657600\"/><wp:docPr id=\"").append(id).append("\" name=\"").append(x(title)).append("\"/><wp:cNvGraphicFramePr/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"><c:chart r:id=\"chart").append(id).append("\"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
    }
    private static String chartXml(String title,String kind,List<?> labels,List<Series> series){
        boolean pie=kind.equals("pie")||kind.equals("donut"),scatter=kind.equals("scatter"),radar=kind.equals("radar");
        String tag=pie?(kind.equals("pie")?"pieChart":"doughnutChart"):scatter?"scatterChart":radar?"radarChart":kind.equals("line")?"lineChart":kind.equals("area")?"areaChart":"barChart";
        var out=new StringBuilder("<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\""+R.substring(0,R.length()-1)+"\"><c:lang val=\"es-BO\"/><c:chart><c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>");
        out.append(x(title)).append("</a:t></a:r></a:p></c:rich></c:tx><c:overlay val=\"0\"/></c:title><c:autoTitleDeleted val=\"0\"/><c:plotArea><c:layout/><c:").append(tag).append('>');
        if(tag.equals("barChart"))out.append("<c:barDir val=\"").append(kind.equals("bars")?"bar":"col").append("\"/><c:grouping val=\"").append(kind.equals("stacked")?"stacked":"clustered").append("\"/>");
        if(tag.equals("lineChart")||tag.equals("areaChart"))out.append("<c:grouping val=\"standard\"/>");
        if(scatter)out.append("<c:scatterStyle val=\"marker\"/>");if(radar)out.append("<c:radarStyle val=\"marker\"/>");
        out.append("<c:varyColors val=\"").append(series.size()==1&&!scatter&&!radar?1:0).append("\"/>");
        for(int s=0;s<(pie?1:series.size());s++){
            var item=series.get(s);out.append("<c:ser><c:idx val=\"").append(s).append("\"/><c:order val=\"").append(s).append("\"/><c:tx><c:strRef><c:f>'Resultados'!$").append(ReportExport.column(s+2)).append("$1</c:f><c:strCache><c:ptCount val=\"1\"/><c:pt idx=\"0\"><c:v>").append(x(item.name())).append("</c:v></c:pt></c:strCache></c:strRef></c:tx>");
            if(scatter)out.append("<c:marker><c:symbol val=\"circle\"/><c:size val=\"5\"/></c:marker>");
            if(scatter)out.append("<c:xVal>").append(reference(labels,1,true)).append("</c:xVal><c:yVal>").append(reference(item.values(),s+2,true)).append("</c:yVal>");
            else out.append("<c:cat>").append(reference(labels,1,false)).append("</c:cat><c:val>").append(reference(item.values(),s+2,true)).append("</c:val>");
            out.append("</c:ser>");
        }
        if(tag.equals("barChart")){out.append("<c:gapWidth val=\"").append(kind.equals("histogram")?0:100).append("\"/>");if(kind.equals("stacked"))out.append("<c:overlap val=\"100\"/>");}
        if(kind.equals("donut"))out.append("<c:holeSize val=\"55\"/>");
        if(!pie)out.append("<c:axId val=\"10\"/><c:axId val=\"20\"/>");out.append("</c:").append(tag).append('>');
        if(!pie){out.append(axis(10,20,scatter,false,kind.equals("bars")?"l":"b"));out.append(axis(20,10,true,radar,kind.equals("bars")?"b":"l"));}
        out.append("</c:plotArea><c:legend><c:legendPos val=\"b\"/><c:layout/><c:overlay val=\"0\"/></c:legend><c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart><c:externalData r:id=\"rId1\"><c:autoUpdate val=\"0\"/></c:externalData></c:chartSpace>");return out.toString();
    }
    private static String axis(int id,int other,boolean numeric,boolean radar,String position){
        String tag=numeric?"valAx":"catAx";
        return "<c:"+tag+"><c:axId val=\""+id+"\"/><c:scaling><c:orientation val=\"minMax\"/>"+(radar?"<c:max val=\"100\"/><c:min val=\"0\"/>":"")+"</c:scaling><c:delete val=\"0\"/><c:axPos val=\""+position+"\"/><c:numFmt formatCode=\"General\" sourceLinked=\"1\"/><c:tickLblPos val=\"nextTo\"/><c:crossAx val=\""+other+"\"/><c:crosses val=\"autoZero\"/>"+(numeric?"<c:crossBetween val=\"between\"/>":"<c:auto val=\"1\"/><c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/>")+"</c:"+tag+">";
    }
    private static String reference(List<?> values,int column,boolean numeric){
        String tag=numeric?"num":"str",letter=ReportExport.column(column);var out=new StringBuilder("<c:"+tag+"Ref><c:f>'Resultados'!$"+letter+"$2:$"+letter+"$"+(values.size()+1)+"</c:f><c:"+tag+"Cache>");
        if(numeric)out.append("<c:formatCode>General</c:formatCode>");out.append("<c:ptCount val=\"").append(values.size()).append("\"/>");
        for(int i=0;i<values.size();i++){Object value=values.get(i);if(value==null||numeric&&!(value instanceof Number))continue;out.append("<c:pt idx=\"").append(i).append("\"><c:v>").append(x(value)).append("</c:v></c:pt>");}
        return out.append("</c:").append(tag).append("Cache></c:").append(tag).append("Ref>").toString();
    }
    byte[] finish()throws IOException{
        String ns=" xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\""+R.substring(0,R.length()-1)+"\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"";
        parts.put("word/document.xml",bytes("<w:document"+ns+"><w:body>"+body+"<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1273\" w:right=\"1273\" w:bottom=\"1273\" w:left=\"1273\"/></w:sectPr></w:body></w:document>"));
        StringBuilder relationships=new StringBuilder(),types=new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"xlsx\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>");
        for(int i=1;i<=charts;i++){relationships.append("<Relationship Id=\"chart").append(i).append("\" Type=\"").append(R).append("chart\" Target=\"charts/chart").append(i).append(".xml\"/>");types.append("<Override PartName=\"/word/charts/chart").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/>");}
        parts.put("word/_rels/document.xml.rels",bytes(rels(relationships.toString())));parts.put("_rels/.rels",bytes(rels("<Relationship Id=\"rId1\" Type=\""+R+"officeDocument\" Target=\"word/document.xml\"/>")));parts.put("[Content_Types].xml",bytes(types.append("</Types>").toString()));
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){for(var part:parts.entrySet()){var entry=new ZipEntry(part.getKey());entry.setTime(0);zip.putNextEntry(entry);zip.write(part.getValue());zip.closeEntry();}}return bytes.toByteArray();
    }
    private static String rels(String relationships){return "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+relationships+"</Relationships>";}
    private static byte[] bytes(String xml){return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+xml).getBytes(StandardCharsets.UTF_8);}
}
