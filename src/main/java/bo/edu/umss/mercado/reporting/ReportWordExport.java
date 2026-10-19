package bo.edu.umss.mercado.reporting;

import java.io.IOException;
import java.util.*;

/** Uses only ReportService.get's verified snapshot, never live responses or dictionaries. */
public final class ReportWordExport {
    private ReportWordExport(){}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return o instanceof Map<?,?>?(Map<String,Object>)o:Map.of();}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> maps(Object o){return o instanceof List<?>?(List<Map<String,Object>>)o:List.of();}
    private static List<?> list(Object o){return o instanceof List<?> l?l:List.of();}
    private static String s(Object o){return Objects.toString(o,"Sin datos");}
    private static List<?> row(Object... values){return Arrays.asList(values);}
    private static boolean yes(Object o){return Boolean.TRUE.equals(o);}
    private static List<String> graphs(Map<String,Object> item,String defaultGraph){return item.containsKey("report_graphs")?list(item.get("report_graphs")).stream().map(Object::toString).toList():List.of(defaultGraph);}
    public static byte[] docx(Map<String,Object> report)throws IOException{
        var doc=new WordDocument();var content=map(report.get("content"));var template=map(content.get("template"));var analysis=map(content.get("analysis"));var result=map(content.getOrDefault("display_result",analysis.get("result")));var context=map(content.get("context"));
        doc.heading(template.get("title"),1);doc.paragraph(template.get("introduction"));
        doc.paragraph("Corte: "+s(context.get("cutoff_utc"))+" · Recepción: "+Objects.toString(context.get("from_date"),"sin inicio")+" a "+Objects.toString(context.get("until_date"),"sin final")+" · "+s(context.get("timezone")));
        doc.paragraph("Generado: "+s(report.get("created_at"))+" · Por: "+s(report.get("actor")));
        doc.paragraph(s(result.get("title"))+" · Versión: "+s(result.get("instrument_version"))+" · Origen: "+s(result.get("origin"))+" · Modo: "+s(result.get("mode")));
        doc.paragraph("Reporte de envíos seleccionados. No implica aprobación institucional ni representatividad poblacional. Las modificaciones en Word no alteran el reporte guardado en la aplicación.");
        for(Object section:list(template.get("sections"))){
            switch(section.toString()){
                case "CONTEXTO"->{doc.heading("Contexto y fuentes",2);doc.paragraph("Envíos: "+s(result.get("submission_count"))+" · Protocolo: "+s(result.get("protocol")));details(doc,context);}
                case "DESCRIPTIVOS"->{
                    doc.heading("Resultados descriptivos",2);
                    for(var variable:maps(result.get("variables")))variable(doc,variable,yes(template.get("charts")));
                    for(var stratum:maps(result.get("strata"))){doc.heading("Resultados separados por origen: "+s(stratum.get("origin")),2);doc.paragraph("Envíos: "+s(stratum.get("submission_count")));for(var v:maps(stratum.get("variables"))){doc.heading(v.get("label"),3);base(doc,v);distribution(doc,v);}}
                    for(var advanced:maps(content.get("advanced_charts")))advanced(doc,advanced,yes(template.get("charts")));
                }
                case "CRUCES"->{doc.heading("Cruces seleccionados",2);for(var cross:maps(content.getOrDefault("display_crosses",content.get("crosses"))))cross(doc,cross,yes(template.get("charts")));}
                case "COMENTARIOS"->{doc.heading("Comentarios e interpretación",2);doc.paragraph("Texto administrativo; las cifras proceden de fuentes calculadas.");doc.paragraph(content.get("comment"));}
                default->{}
            }
        }
        doc.heading("Referencias del reporte",2);
        doc.table(List.of(row("Referencia","Valor"),row("Reporte",report.get("id")),row("Huella del reporte",report.get("logical_hash")),row("Análisis",analysis.get("id")),row("Huella de resultados",analysis.get("result_hash")),row("Huella de fuente",analysis.get("source_hash")),row("Conjunto preparado",content.get("preparation_id")),row("Revisión de plantilla",content.get("template_revision_id"))));
        return doc.finish();
    }
    private static void base(WordDocument doc,Map<String,Object> v){
        doc.paragraph("Total: "+s(v.get("total"))+" · Con valor: "+s(v.get("answered"))+" · Sin respuesta: "+s(v.get("missing"))+" · No aplica: "+s(v.get("not_applicable"))+" · Base aplicable: "+s(v.get("applicable_base"))+" · % respuesta: "+s(v.get("response_percent")));
        if(v.get("source_versions")!=null)doc.paragraph("Versiones que aportan esta pregunta: "+v.get("source_versions"));
        if(v.get("unit")!=null)doc.paragraph("Unidad: "+v.get("unit"));
    }
    private static void distribution(WordDocument doc,Map<String,Object> v){
        var rows=new ArrayList<List<?>>();
        if(v.get("numeric") instanceof Map<?,?>){var n=map(v.get("numeric"));rows.add(row("Medida","Valor","Base numérica"));for(String k:List.of("min","max","mean","median","sample_standard_deviation"))rows.add(row(switch(k){case "min"->"Mínimo";case "max"->"Máximo";case "mean"->"Media";case "median"->"Mediana";default->"Desviación estándar muestral";},n.get(k),n.get("n")));}
        else{rows.add(row("Opción / estado","Conteo","Base envíos","% envíos","Base menciones","% menciones"));for(var o:maps(v.get("distribution")))rows.add(row(o.get("label"),o.get("count"),o.get("respondent_base"),o.get("respondent_percent"),o.get("mention_base"),o.get("mention_percent")));}
        doc.table(rows);
    }
    private static void variable(WordDocument doc,Map<String,Object> v,boolean charts)throws IOException{
        doc.heading(v.get("label"),3);base(doc,v);
        if(yes(v.get("coverage_only")))doc.paragraph("La cobertura indica qué envíos contienen respuesta; no describe el contenido de los textos.");
        if("checkbox".equals(v.get("type")))doc.paragraph("Selección múltiple: un envío puede aportar varias menciones; los porcentajes sobre envíos pueden sumar más de 100%.");
        // Equivalent tables remain available, like the expandable tables in the HTML report.
        distribution(doc,v);if(v.get("report_comment")!=null)doc.paragraph(v.get("report_comment"));
        var reps=graphs(v,"columns");boolean mentions=yes(v.get("report_text_table"))||reps.stream().anyMatch(r->r.startsWith("text-"));
        if(yes(v.get("sensitive_text"))&&(yes(v.get("report_text_content"))||mentions))doc.paragraph("ADVERTENCIA: datos sensibles o identificativos incluidos mediante confirmación administrativa explícita. Limita la distribución de este documento.");
        if(yes(v.get("report_text_content"))&&v.get("text_content") instanceof List<?> texts){doc.heading("Respuestas escritas",4);var rows=new ArrayList<List<?>>();rows.add(row("Respuesta"));for(Object t:texts)rows.add(row(t));doc.table(rows);}
        if(mentions&&v.get("text_counts") instanceof Map<?,?>){var counts=map(v.get("text_counts"));doc.heading("Coincidencias en respuestas abiertas",4);doc.paragraph("Diccionario: "+s(counts.get("dictionary_scope"))+" · Revisión: "+s(counts.get("dictionary_revision"))+" · Base: "+s(counts.get("base"))+" respuestas con valor.");doc.paragraph("Cada tema cuenta una vez por respuesta. Los compuestos tienen prioridad. Una respuesta puede mencionar varios temas. Circular y anillo distribuyen menciones de temas; no porcentajes de envíos.");var rows=new ArrayList<List<?>>();rows.add(row("Tema","Envíos","Base"));for(var o:maps(counts.get("distribution")))if(positive(o.get("count")))rows.add(row(o.get("label"),o.get("count"),o.get("base")));doc.table(rows);if(rows.size()==1)doc.paragraph("Sin coincidencias para esta fuente.");}
        // The snapshot may contain unselected enriched texts: never iterate those unless selected above.
        for(String rep:reps){
            if(rep.equals("box")){var box=map(map(v.get("advanced")).get("box"));doc.heading("Caja: resumen editable",4);doc.paragraph("Word conserva los cinco valores en una tabla editable; no se exporta una imagen ni un diagrama de caja nativo.");doc.paragraph(box.get("method"));var rows=new ArrayList<List<?>>();rows.add(row("Medida","Valor"));var values=list(box.get("values"));var labels=List.of("Mínimo","Q1","Mediana","Q3","Máximo");for(int i=0;i<values.size();i++)rows.add(row(labels.get(i),values.get(i)));doc.table(rows);continue;}
            if(!charts)continue;
            var labels=new ArrayList<Object>();var values=new ArrayList<Object>();String kind=rep.startsWith("text-")?rep.substring(5):rep;
            if(rep.startsWith("text-")){for(var o:maps(map(v.get("text_counts")).get("distribution")))if(positive(o.get("count"))){labels.add(o.get("label"));values.add(o.get("count"));}}
            else if(rep.equals("histogram")){var histogram=map(map(v.get("advanced")).get("histogram"));doc.paragraph(histogram.get("method"));for(var b:maps(histogram.get("bins"))){labels.add("["+s(b.get("lower"))+", "+s(b.get("upper"))+(yes(b.get("closed_upper"))?"]":")"));values.add(b.get("count"));}var rows=new ArrayList<List<?>>();rows.add(row("Intervalo","Envíos"));for(int i=0;i<labels.size();i++)rows.add(row(labels.get(i),values.get(i)));doc.table(rows);}
            else if(rep.equals("line")||rep.equals("area")){var time=map(map(v.get("advanced")).get("time"));doc.paragraph(time.get("method"));labels.addAll(list(time.get("months")));values.addAll(list(time.get("counts")));var rows=new ArrayList<List<?>>();rows.add(row("Mes de recepción (America/La_Paz)","Envíos con valor"));for(int i=0;i<labels.size();i++)rows.add(row(labels.get(i),values.get(i)));doc.table(rows);}
            else if(v.get("numeric") instanceof Map<?,?> n){labels.addAll(List.of("Mínimo","Mediana","Máximo"));for(String k:List.of("min","median","max"))values.add(n.get(k));}
            else for(var o:maps(v.get("distribution"))){labels.add(o.get("label"));values.add(o.get("count"));}
            doc.heading(ReportCustomization.label(rep),4);doc.chart(s(v.get("label")),kind,labels,List.of(new WordDocument.Series(rep.startsWith("text-")?"Envíos que mencionan el tema":v.get("numeric")!=null?"Valor ("+s(v.get("unit"))+")":"Envíos",values)));
        }
    }
    private static boolean positive(Object o){return o instanceof Number n&&n.doubleValue()>0;}
    private static void cross(WordDocument doc,Map<String,Object> saved,boolean charts)throws IOException{
        var c=map(saved.get("result"));var table=map(c.get("table"));String title=Objects.toString(saved.get("display_title"),s(c.get("row_label"))+" × "+s(c.get("column_label")));
        doc.heading(title,3);if(saved.get("report_comment")!=null)doc.paragraph(saved.get("report_comment"));doc.paragraph("Total: "+s(c.get("total_submissions"))+" · Pares con valor: "+s(table.get("n"))+" · Excluidos: "+s(c.get("excluded_pairs")));
        var columns=maps(c.get("column_options"));var rows=maps(c.get("row_options"));
        for(String metric:List.of("observed","row_percentages","column_percentages","total_percentages","expected")){
            doc.heading(switch(metric){case "observed"->"Conteos observados";case "row_percentages"->"% sobre total de cada fila";case "column_percentages"->"% sobre total de cada columna";case "total_percentages"->"% sobre pares con valor";default->"Frecuencias esperadas (diagnóstico)";},4);
            doc.table(matrix(rows,columns,list(table.get(metric))));
        }
        doc.paragraph("Totales de fila: "+s(table.get("row_totals"))+" · Totales de columna: "+s(table.get("column_totals")));
        doc.paragraph("Aplicabilidad: "+s(table.get("status"))+" · Motivo: "+s(table.get("reason"))+" · Alpha: "+s(table.get("alpha")));
        for(String metric:List.of("min_expected","below_five_count","chi_square","df","p","cramers_v","reject_h0"))doc.paragraph(metric+": "+s(table.get(metric)));
        if(table.get("p")==null)doc.paragraph("Prueba no ejecutada; la ausencia de p no significa ausencia de asociación.");
        doc.paragraph(c.get("multiplicity"));doc.paragraph("Estados de pares: "+s(c.get("pair_states")));doc.paragraph("Protocolo: "+s(c.get("protocol"))+" · Huella del cruce: "+s(saved.get("result_hash")));details(doc,map(saved.get("spec")));
        if(!charts||!positive(table.get("n")))return;
        for(String rep:graphs(saved,"heat")){
            if(rep.equals("heat")){doc.heading("Mapa de calor: tabla coloreada editable",4);doc.paragraph("Los colores representan magnitudes de los conteos observados; Word utiliza celdas editables en lugar de una imagen.");doc.table(matrix(rows,columns,list(table.get("observed"))),true);continue;}
            var series=new ArrayList<WordDocument.Series>();var observed=list(table.get("observed"));for(int col=0;col<columns.size();col++){var values=new ArrayList<Object>();for(Object r:observed)values.add(list(r).get(col));series.add(new WordDocument.Series(s(columns.get(col).get("label")),values));}doc.heading(ReportCustomization.label(rep),4);doc.chart(title,rep,rows.stream().map(r->r.get("label")).toList(),series);
        }
    }
    private static List<List<?>> matrix(List<Map<String,Object>> rows,List<Map<String,Object>> columns,List<?> matrix){var out=new ArrayList<List<?>>();var header=new ArrayList<Object>();header.add("Filas / columnas");columns.forEach(c->header.add(c.get("label")));out.add(header);for(int r=0;r<rows.size();r++){var row=new ArrayList<Object>();row.add(rows.get(r).get("label"));if(r<matrix.size())row.addAll(list(matrix.get(r)));out.add(row);}return out;}
    private static void advanced(WordDocument doc,Map<String,Object> a,boolean charts)throws IOException{
        doc.heading(a.get("title"),3);doc.paragraph(a.get("comment"));doc.paragraph(a.get("method"));
        if("scatter".equals(a.get("type"))){doc.paragraph("X: "+s(a.get("x_label"))+" ("+s(a.get("x_unit"))+") · Y: "+s(a.get("y_label"))+" ("+s(a.get("y_unit"))+") · Pares con valor: "+s(a.get("n"))+" · Excluidos: "+s(a.get("excluded")));var rows=new ArrayList<List<?>>();rows.add(row("X","Y","Frecuencia"));var xs=new ArrayList<Object>();var ys=new ArrayList<Object>();for(Object p:list(a.get("points"))){var point=list(p);rows.add(point);xs.add(point.get(0));ys.add(point.get(1));}doc.table(rows);doc.paragraph("Posiciones de pares con valor; las coordenadas repetidas conservan su frecuencia en la tabla. No prueba causalidad ni independencia.");if(charts&&yes(a.get("available")))doc.chart(s(a.get("title")),"scatter",xs,List.of(new WordDocument.Series(s(a.get("y_label")),ys)));}
        else if("radar".equals(a.get("type"))){var rows=new ArrayList<List<?>>();rows.add(row("Criterio","Acuerdo","Base sustantiva","No sabe","Sin respuesta","No aplica","% acuerdo"));var labels=new ArrayList<Object>();var values=new ArrayList<Object>();for(var c:maps(a.get("criteria"))){rows.add(row(c.get("label"),c.get("numerator"),c.get("base"),c.get("no_sabe"),c.get("missing"),c.get("not_applicable"),c.get("percentage")));labels.add(c.get("label"));values.add(c.get("percentage"));}doc.table(rows);doc.paragraph("Acuerdo parcial/total sobre base sustantiva propia de cada criterio. No sabe permanece separado; no se obtiene un índice global ni se rellenan series incompletas.");if(charts&&yes(a.get("available")))doc.chart(s(a.get("title")),"radar",labels,List.of(new WordDocument.Series("% acuerdo sustantivo",values)));}
        if(!yes(a.get("available")))doc.paragraph("Sin datos suficientes para dibujar esta serie; se conserva su tabla sin completar valores con ceros.");
    }
    private static void details(WordDocument doc,Map<String,Object> values){var rows=new ArrayList<List<?>>();rows.add(row("Referencia","Valor"));flatten(rows,"",values);doc.table(rows);}
    private static void flatten(List<List<?>> rows,String prefix,Object value){if(value instanceof Map<?,?> m)m.forEach((k,v)->flatten(rows,prefix.isEmpty()?k.toString():prefix+"."+k,v));else if(value instanceof List<?> l){for(int i=0;i<l.size();i++)flatten(rows,prefix+"["+i+"]",l.get(i));}else rows.add(row(prefix,value));}
}
