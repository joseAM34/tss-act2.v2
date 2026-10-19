package bo.edu.umss.mercado.reporting;

import java.util.*;

/** Presentation choices only. All figures come from the verified frozen analysis. */
public final class ReportCustomization {
    private ReportCustomization() {}
    public record Question(String id, boolean included, String title, String comment,List<String> representations,boolean sensitiveAcknowledged) {
        public Question(String id,boolean included,String title,String comment,List<String> representations){this(id,included,title,comment,representations,false);}
        public Question(String id,boolean included,String title,String comment){this(id,included,title,comment,null);}
        public Question { representations=representations==null?null:List.copyOf(representations); }
    }
    public record Cross(String id,String title,String comment,List<String> representations) {
        public Cross(String id,String title,String comment){this(id,title,comment,null);}
        public Cross { representations=representations==null?null:List.copyOf(representations); }
    }
    public record Advanced(String type,List<String> questions,String title,String comment,boolean included) {
        public Advanced { questions=questions==null?null:List.copyOf(questions); }
    }
    public record Configuration(String analysisId,String title,String introduction,boolean context,boolean tables,boolean charts,
                                String comment,List<Question> questions,List<String> crossIds,List<Cross> crossSettings,List<Advanced> advanced) {
        public Configuration(String analysisId,String title,String introduction,boolean context,boolean tables,boolean charts,String comment,List<Question> questions,List<String> crossIds,List<Cross> crossSettings){this(analysisId,title,introduction,context,tables,charts,comment,questions,crossIds,crossSettings,null);}
        public Configuration(String analysisId,String title,String introduction,boolean context,boolean tables,boolean charts,String comment,List<Question> questions,List<String> crossIds) {
            this(analysisId,title,introduction,context,tables,charts,comment,questions,crossIds,null,null);
        }
        public Configuration {
            questions=questions==null?null:List.copyOf(questions);
            crossIds=crossIds==null?null:List.copyOf(crossIds);
            crossSettings=crossSettings==null?null:List.copyOf(crossSettings);
            advanced=advanced==null?null:List.copyOf(advanced);
        }
    }
    @SuppressWarnings("unchecked")
    public static Configuration defaults(Map<String,Object> analysis,List<String> crossIds) {
        var result=(Map<String,Object>)analysis.get("result");
        var variables=(List<Map<String,Object>>)result.get("variables");
        return new Configuration((String)analysis.get("id"),defaultTitle(result.get("title")),"",true,true,true,"",
                variables.stream().map(v->new Question((String)v.get("question_id"),true,(String)v.get("label"),"")).toList(),crossIds);
    }
    @SuppressWarnings("unchecked")
    public static void validate(Configuration c,Map<String,Object> analysis) {
        if(c==null || !Objects.equals(c.analysisId(),analysis.get("id")))throw new IllegalArgumentException("Análisis no válido.");
        text(c.title(),200,true);text(c.introduction(),4000,false);text(c.comment(),8000,false);
        var variables=(List<Map<String,Object>>)((Map<?,?>)analysis.get("result")).get("variables");
        if(c.questions()==null || c.questions().size()!=variables.size())throw new IllegalArgumentException("La selección debe corresponder a las preguntas del análisis.");
        var expected=variables.stream().map(v->v.get("question_id")).toList();
        if(!expected.equals(c.questions().stream().map(Question::id).toList()))throw new IllegalArgumentException("Las preguntas no corresponden al análisis o están repetidas.");
        for(int i=0;i<c.questions().size();i++){
            var q=c.questions().get(i);text(q.title(),2000,true);text(q.comment(),1000,false);
            if(Boolean.TRUE.equals(variables.get(i).get("sensitive_text"))&&!q.sensitiveAcknowledged()&&q.representations()!=null&&q.representations().stream().anyMatch(r->r.startsWith("text-")))throw new IllegalArgumentException("Confirma la advertencia de datos sensibles para esta pregunta antes de incluir sus textos o coincidencias.");
            if(q.representations()!=null){
                var allowed=new HashSet<>(List.of("table","columns","bars"));
                if(variables.get(i).get("text_counts") instanceof Map<?,?>)allowed.add("text-content");
                if(variables.get(i).get("text_counts") instanceof Map<?,?>)allowed.addAll(List.of("text-table","text-bars","text-pie","text-donut"));
                if(Set.of("radio","select","coverage").contains(variables.get(i).get("type"))&&variables.get(i).get("numeric")==null)allowed.addAll(List.of("pie","donut"));
                if(variables.get(i).get("advanced") instanceof Map<?,?> options){if(options.containsKey("histogram"))allowed.add("histogram");if(options.containsKey("box"))allowed.add("box");if(options.containsKey("time"))allowed.addAll(List.of("line","area"));}
                representations(q.representations(),allowed);
                if(q.included()&&!active(q.representations(),c))throw new IllegalArgumentException("Activa una representación para cada pregunta incluida.");
            }
        }
        if(c.crossIds()==null || c.crossIds().size()>10 || new HashSet<>(c.crossIds()).size()!=c.crossIds().size())throw new IllegalArgumentException("Selecciona hasta diez cruces distintos.");
        if(c.crossSettings()!=null){
            if(c.crossSettings().size()>100 || new HashSet<>(c.crossSettings().stream().map(Cross::id).toList()).size()!=c.crossSettings().size())throw new IllegalArgumentException("Configuración de cruces repetida o demasiado extensa.");
            for(var cross:c.crossSettings()){
                text(cross.id(),36,true);text(cross.title(),200,true);text(cross.comment(),1000,false);
                if(cross.representations()!=null){representations(cross.representations(),Set.of("table","heat","grouped","stacked"));
                    if(c.crossIds().contains(cross.id())&&!active(cross.representations(),c))throw new IllegalArgumentException("Activa una representación para cada comparación incluida.");}
            }
        }
        boolean included=c.questions().stream().anyMatch(Question::included);
        boolean advanced=c.advanced()!=null&&c.advanced().stream().anyMatch(Advanced::included);
        if(c.advanced()!=null){if(c.advanced().size()>100)throw new IllegalArgumentException("Demasiados gráficos avanzados.");for(var setting:c.advanced()){text(setting.title(),200,true);text(setting.comment(),1000,false);}}
        if(!included && c.crossIds().isEmpty()&&!advanced)throw new IllegalArgumentException("Incluye al menos una pregunta o comparación.");
        if(advanced&&!c.tables()&&!c.charts())throw new IllegalArgumentException("Activa tablas o gráficos para los gráficos avanzados.");
        if(included && !c.tables()&&!c.charts())throw new IllegalArgumentException("Activa las tablas o los gráficos para las preguntas incluidas.");
    }
    public static ReportService.Template template(Configuration c) {
        var sections=new ArrayList<String>();if(c.context())sections.add("CONTEXTO");
        sections.add("DESCRIPTIVOS");sections.add("CRUCES");sections.add("COMENTARIOS");
        return new ReportService.Template(c.title(),c.introduction(),sections,c.charts());
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> display(Configuration c,Map<String,Object> analysis) {
        var result=new LinkedHashMap<>((Map<String,Object>)analysis.get("result"));
        var original=(List<Map<String,Object>>)result.get("variables");var variables=new ArrayList<Map<String,Object>>();
        for(int i=0;i<c.questions().size();i++){
            var q=c.questions().get(i);if(!q.included())continue;
            var v=new LinkedHashMap<>(original.get(i));v.put("label",q.title());v.put("original_label",original.get(i).get("label"));
            v.put("report_comment",q.comment());v.put("report_tables",c.tables());variables.add(v);
            if(q.representations()!=null){v.put("report_tables",q.representations().contains("table")&&c.tables());v.put("report_text_table",q.representations().contains("text-table")&&c.tables());v.put("report_text_content",q.representations().contains("text-content")&&c.tables());v.put("report_graphs",c.charts()?q.representations().stream().filter(r->!Set.of("table","text-table","text-content").contains(r)).toList():List.of());}
        }
        result.put("variables",variables);
        if(result.get("strata") instanceof List<?> strata)result.put("strata",strata.stream().map(s->display(c,Map.of("result",s))).toList());
        return result;
    }
    private static String defaultTitle(Object title) { String value="Reporte: "+title;return value.substring(0,Math.min(200,value.length())); }
    public static List<Map<String,Object>> displayCrosses(Configuration config,List<Map<String,Object>> originals) {
        return originals.stream().map(original->{
            var shown=new LinkedHashMap<String,Object>(original);
            if(config.crossSettings()!=null)config.crossSettings().stream().filter(c->c.id().equals(original.get("id"))).findFirst().ifPresent(c->{shown.put("display_title",c.title());shown.put("report_comment",c.comment());
                if(c.representations()!=null){shown.put("report_tables",c.representations().contains("table")&&config.tables());shown.put("report_graphs",config.charts()?c.representations().stream().filter(r->!r.equals("table")).toList():List.of());}
            });
            return (Map<String,Object>)shown;
        }).toList();
    }
    public static boolean multiple(Configuration c){return c.questions()!=null&&c.questions().stream().anyMatch(q->q.representations()!=null)||c.crossSettings()!=null&&c.crossSettings().stream().anyMatch(q->q.representations()!=null);}
    public static boolean advanced(Configuration c){return c.questions()!=null&&c.questions().stream().anyMatch(q->q.representations()!=null&&q.representations().stream().anyMatch(r->Set.of("histogram","box","line","area").contains(r)))||c.advanced()!=null&&!c.advanced().isEmpty();}
    public static String label(String value){return switch(value){case "text-bars"->"Barras de coincidencias";case "text-pie"->"Circular de coincidencias";case "text-donut"->"Anillo de coincidencias";case "columns"->"Columnas verticales";case "bars"->"Barras horizontales";case "pie"->"Circular";case "donut"->"Anillo";case "heat"->"Mapa de calor";case "grouped"->"Barras agrupadas";case "stacked"->"Barras apiladas";case "histogram"->"Histograma";case "box"->"Caja: mínimo, Q1, mediana, Q3, máximo";case "line"->"Línea: envíos con valor por mes de recepción";case "area"->"Área: envíos con valor por mes de recepción";default->"Tabla";};}
    public static Question catalogQuestion(Question q,Configuration c){return new Question(q.id(),(c.tables()||c.charts())&&(q.representations()==null||active(q.representations(),c)),q.title(),q.comment(),q.representations(),q.sensitiveAcknowledged());}
    public static boolean activeCross(String id,Configuration c){
        if(c.crossSettings()==null)return true;
        return c.crossSettings().stream().filter(s->s.id().equals(id)).findFirst().map(s->s.representations()==null||active(s.representations(),c)).orElse(true);
    }
    public static Set<String> originalQuestions(Configuration c){return c.tables()?c.questions().stream().filter(q->q.included()&&q.representations()!=null&&q.representations().contains("text-content")).map(Question::id).collect(java.util.stream.Collectors.toSet()):Set.of();}
    public static Set<String> sensitiveQuestions(Configuration c){return c.questions().stream().filter(Question::sensitiveAcknowledged).map(Question::id).collect(java.util.stream.Collectors.toSet());}
    private static boolean active(List<String> representations,Configuration c){return representations.stream().anyMatch(r->Set.of("table","text-table","text-content").contains(r)?c.tables():c.charts());}
    private static void representations(List<String> values,Set<String> allowed){
        if(values.size()>allowed.size()||new HashSet<>(values).size()!=values.size()||!allowed.containsAll(values))throw new IllegalArgumentException("Representación no compatible o repetida. Revisa el tipo de pregunta o comparación.");
    }
    private static void text(String value,int max,boolean required) {
        if(value==null || value.length()>max || required&&value.isBlank() || value.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw new IllegalArgumentException("Texto de presentación no válido (revisa título, comentario o longitud).");
    }
}
