package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.util.*;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TextDictionaryService {
    private final JdbcTemplate jdbc;
    public TextDictionaryService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Saved(int revision,String rules) {}
    @SuppressWarnings("unchecked")
    private String definition(WorkspaceResultsService.Snapshot frame,String key){return CanonicalJson.hash(((List<Map<String,Object>>)frame.logical().get("dictionary")).stream().filter(q->key.equals(q.get("question_id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Pregunta fuera de esta vista.")));}
    public Saved get(WorkspaceResultsService.Snapshot frame,String key){definition(frame,key);return selected(frame.logical(),key);}
    @SuppressWarnings("unchecked")
    private Map<String,Object> question(Map<String,Object> logical,String key){return ((List<Map<String,Object>>)logical.get("dictionary")).stream().filter(q->key.equals(q.get("question_id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Pregunta fuera del conjunto."));}
    private String context(Map<String,Object> logical,String key){var q=new LinkedHashMap<>(question(logical,key));q.remove("source_versions");q.remove("question_id");q.remove("privacy");return CanonicalJson.hash(Map.of("survey",logical.get("survey_id"),"question",q));}
    private List<Map<String,Object>> history(Map<String,Object> logical,String key){return jdbc.queryForList("SELECT * FROM scoped_text_dictionary_revision WHERE context_key=? ORDER BY revision DESC",context(logical,key));}
    private Saved privateSaved(List<Map<String,Object>> rows){return rows.stream().filter(r->"PRIVATE".equals(r.get("dictionary_scope"))).findFirst().map(this::verified).orElse(new Saved(0,""));}
    private Saved verified(Map<String,Object> row){String rules=(String)row.get("rules_text");if(!CanonicalJson.hash(rules).equals(row.get("rules_hash")))throw new IllegalStateException("El diccionario privado no coincide con su huella.");return new Saved(((Number)row.get("revision")).intValue(),rules);}
    public String scope(Map<String,Object> logical,String key){var rows=history(logical,key);return rows.isEmpty()?"GLOBAL":(String)rows.getFirst().get("dictionary_scope");}
    public Saved selected(Map<String,Object> logical,String key){var view=editor(logical,key,"");String rules=(String)view.get("rules");return new Saved(((Number)view.get("revision")).intValue(),"GLOBAL".equals(view.get("dictionaryScope"))?globalRules(rules):rules);}
    public Map<String,Object> editor(Map<String,Object> logical,String key,String requested){var rows=history(logical,key);String mode=requested==null||requested.isBlank()?(rows.isEmpty()?"GLOBAL":(String)rows.getFirst().get("dictionary_scope")):requested;checkScope(mode);var saved=mode.equals("PRIVATE")?privateSaved(rows):global();return Map.of("rules",saved.rules(),"revision",saved.revision(),"dictionaryScope",mode,"contextRevision",rows.isEmpty()?0:((Number)rows.getFirst().get("revision")).intValue());}
    private static void checkScope(String scope){if(!Set.of("GLOBAL","PRIVATE").contains(scope))throw new IllegalArgumentException("Elige diccionario global o privado.");}
    @Transactional
    public Map<String,Object> saveScoped(Map<String,Object> logical,String key,String mode,int expected,int contextRevision,String rules,String actor){
        checkScope(mode);if(actor==null||actor.isBlank()||actor.length()>100)throw new IllegalArgumentException("Administrador requerido.");
        var q=question(logical,key);jdbc.queryForList("SELECT id FROM survey WHERE id=? FOR UPDATE",logical.get("survey_id"));var rows=history(logical,key);int current=rows.isEmpty()?0:((Number)rows.getFirst().get("revision")).intValue();
        if(current!=contextRevision)throw new IllegalStateException("La elección del diccionario cambió. Recarga antes de guardar.");
        if(mode.equals("GLOBAL"))saveGlobal(expected,rules,actor);else if(privateSaved(rows).revision()!=expected)throw new IllegalStateException("El diccionario privado cambió. Recarga antes de guardar.");
        rules=TextMentions.canonicalize(rules);jdbc.update("INSERT INTO scoped_text_dictionary_revision(context_key,revision,survey_id,question_key,definition_hash,dictionary_scope,rules_text,rules_hash,actor,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",context(logical,key),current+1,logical.get("survey_id"),key,CanonicalJson.hash(q),mode,rules,CanonicalJson.hash(rules),actor,OffsetDateTime.now());return editor(logical,key,mode);
    }
    @SuppressWarnings("unchecked")
    public Map<String,Map<String,Object>> selections(Map<String,Object> logical){var selected=new LinkedHashMap<String,Map<String,Object>>();for(var q:(List<Map<String,Object>>)logical.get("dictionary")){if(!Set.of("text","textarea","email","tel").contains(q.getOrDefault("original_type","")))continue;String key=(String)q.get("question_id");var view=editor(logical,key,"");String rules=(String)view.get("rules");if("GLOBAL".equals(view.get("dictionaryScope")))rules=globalRules(rules);selected.put(key,Map.of("scope",view.get("dictionaryScope"),"revision",view.get("revision"),"rules",rules,"hash",CanonicalJson.hash(rules)));}return selected;}
    public static String globalRules(String rules){return TextMentions.parse(rules).stream().map(t->{var aliases=t.aliases().stream().filter(a->!TextMentions.normalize(a).matches("[0-9 ]+")).toList();return aliases.isEmpty()?"":t.label()+"="+String.join("|",aliases);}).filter(s->!s.isBlank()).collect(java.util.stream.Collectors.joining("\n"));}
    public Map<String,Object> scopedImport(Map<String,Object> logical,String key,String scope,String editor,String incoming){var view=editor(logical,key,scope);var result=new LinkedHashMap<>(importPreview(new Saved(((Number)view.get("revision")).intValue(),(String)view.get("rules")),editor,incoming));result.put("contextRevision",view.get("contextRevision"));result.put("dictionaryScope",scope);return result;}
    public Saved global(){var rows=jdbc.queryForList("SELECT revision,rules_text,rules_hash FROM universal_dictionary_revision ORDER BY revision DESC LIMIT 1");if(rows.isEmpty()){
        var legacy=jdbc.queryForList("SELECT d.rules_text,d.rules_hash FROM question_text_dictionary d WHERE d.revision=(SELECT MAX(x.revision) FROM question_text_dictionary x WHERE x.version_id=d.version_id AND x.question_key=d.question_key AND x.definition_hash=d.definition_hash) ORDER BY d.created_at,d.id");var rules=new ArrayList<String>();for(var row:legacy){String text=row.get("rules_text").toString();if(!CanonicalJson.hash(text).equals(row.get("rules_hash")))throw new IllegalStateException("Un diccionario anterior no coincide con su huella.");rules.add(text);}return new Saved(0,TextMentions.canonicalize(String.join("\n",rules)));}
        var row=rows.getFirst();String rules=(String)row.get("rules_text");if(!CanonicalJson.hash(rules).equals(row.get("rules_hash")))throw new IllegalStateException("El diccionario no coincide con su huella.");return new Saved(((Number)row.get("revision")).intValue(),rules);}
    @Transactional
    public Saved save(WorkspaceResultsService.Snapshot frame,String key,int expected,String rules,String actor){definition(frame,key);if(actor==null||actor.isBlank()||actor.length()>100)throw new IllegalArgumentException("Administrador requerido.");rules=TextMentions.canonicalize(rules);jdbc.queryForList("SELECT id FROM universal_dictionary_state WHERE id=1 FOR UPDATE");var previous=global();if(previous.revision()!=expected)throw new IllegalStateException("El diccionario universal cambió en otra pantalla. Recarga antes de guardar.");int revision=expected+1;jdbc.update("INSERT INTO universal_dictionary_revision(revision,rules_text,rules_hash,actor,created_at) VALUES(?,?,?,?,?)",revision,rules,CanonicalJson.hash(rules),actor,OffsetDateTime.now());jdbc.update("UPDATE universal_dictionary_state SET revision=? WHERE id=1",revision);return new Saved(revision,rules);}
    @Transactional
    public Saved saveGlobal(int expected,String rules,String actor){if(actor==null||actor.isBlank()||actor.length()>100)throw new IllegalArgumentException("Administrador requerido.");rules=TextMentions.canonicalize(rules);jdbc.queryForList("SELECT id FROM universal_dictionary_state WHERE id=1 FOR UPDATE");if(global().revision()!=expected)throw new IllegalStateException("El diccionario cambió. Vuelve a validar antes de guardar.");int revision=expected+1;jdbc.update("INSERT INTO universal_dictionary_revision(revision,rules_text,rules_hash,actor,created_at) VALUES(?,?,?,?,?)",revision,rules,CanonicalJson.hash(rules),actor,OffsetDateTime.now());jdbc.update("UPDATE universal_dictionary_state SET revision=? WHERE id=1",revision);return new Saved(revision,rules);}
    public Map<String,Object> importPreview(String editor,String incoming){if(incoming==null||incoming.isBlank())throw new IllegalArgumentException("Pega los grupos y variantes que quieres incorporar.");return importPreview(global(),editor,incoming);}
    private Map<String,Object> importPreview(Saved saved,String editor,String incoming){if(incoming==null||incoming.isBlank())throw new IllegalArgumentException("Pega los grupos y variantes que quieres incorporar.");String merged=TextMentions.canonicalize(saved.rules()+"\n"+editor+"\n"+incoming);var before=TextMentions.parse(saved.rules());var after=TextMentions.parse(merged);var changed=after.stream().filter(theme->before.stream().noneMatch(old->old.label().equals(theme.label())&&new HashSet<>(old.aliases()).equals(new HashSet<>(theme.aliases())))).map(theme->Map.of("group",theme.label(),"aliases",theme.aliases())).toList();return Map.of("rules",merged,"revision",saved.revision(),"groupsBefore",before.size(),"groupsAfter",after.size(),"changes",changed,"report",importReport(saved.rules(),merged,incoming));}
    public static List<Map<String,Object>> importReport(String saved,String merged,String incoming){
        var before=TextMentions.parse(TextMentions.canonicalize(saved));
        var received=new LinkedHashSet<String>();TextMentions.parse(TextMentions.canonicalize(incoming)).forEach(t->received.addAll(t.aliases()));
        var report=new ArrayList<Map<String,Object>>();
        for(var theme:TextMentions.parse(merged)){
            var previous=before.stream().filter(t->!Collections.disjoint(t.aliases(),theme.aliases())).toList();
            var established=new LinkedHashSet<String>();previous.forEach(t->established.addAll(t.aliases()));
            var added=theme.aliases().stream().filter(a->!established.contains(a)).toList();
            boolean unchanged=previous.size()==1&&added.isEmpty()&&previous.getFirst().label().equals(theme.label());
            if(unchanged&&Collections.disjoint(received,theme.aliases()))continue;
            String action=previous.isEmpty()?"Grupo nuevo":previous.size()>1?"Grupos unidos":unchanged?"Ya establecido, sin duplicar":"Grupo actualizado";
            report.add(Map.of("group",theme.label(),"action",action,"previousGroups",previous.stream().map(TextMentions.Theme::label).toList(),"addedAliases",added,"existingAliases",theme.aliases().stream().filter(a->received.contains(a)&&established.contains(a)).toList()));
        }
        return report;
    }
    private static final Set<String> STOP=Set.of("a","al","algo","ante","asi","cada","como","con","cual","cuando","de","del","desde","donde","el","ella","ellos","en","era","es","esa","ese","eso","esta","estas","este","esto","estos","etc","fue","ha","hay","la","las","lo","los","mas","me","mi","muy","no","nos","o","otra","otro","para","pero","por","porque","que","quien","se","sea","ser","si","sin","sobre","son","su","sus","tambien","tanto","tener","tiene","todo","todos","tu","un","una","unas","uno","unos","y","ya","yo","debe","deben","puede","pueden","actualmente","considero");
    private static String normalized(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace('-',' ');}
    private static final Set<String> CONNECTORS=Set.of("de","del","en","para","con","por","la","el","las","los");
    public static List<Map<String,Object>> extract(List<String> texts){
        var counts=new HashMap<String,Integer>();var labels=new HashMap<String,String>();
        for(String text:texts){var found=new HashSet<String>();String sanitized=text.replaceAll("<[^>]*>"," ").replaceAll("(?i)https?://\\S+|\\S+@\\S+|\\b\\d{6,}\\b"," ");for(String chunk:sanitized.split("[.,;:!?\\n()]+")){var words=chunk.strip().split("\\s+");for(int i=0;i<words.length;i++){var phrase=new ArrayList<String>();int content=0;for(int j=i;j<Math.min(words.length,i+6);j++){String word=words[j].replaceAll("[^\\p{L}\\p{N}+#-]",""),normalized=normalized(word);if(word.isEmpty())break;if(STOP.contains(normalized)){if(phrase.isEmpty()||!CONNECTORS.contains(normalized))break;phrase.add(word);continue;}if(++content>3)break;phrase.add(word);String label=String.join(" ",phrase),key=normalized(label);if(key.length()<2||key.length()>100||key.matches("[0-9 -]+"))continue;found.add(key);labels.putIfAbsent(key,label);}}}found.forEach(key->counts.merge(key,1,Integer::sum));}
        var candidates=counts.entrySet().stream().filter(e->counts.entrySet().stream().noneMatch(other->!other.getKey().equals(e.getKey())&&(" "+other.getKey()+" ").contains(" "+e.getKey()+" ")&&other.getValue()>=e.getValue())).sorted(Map.Entry.<String,Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(100).map(Map.Entry::getKey).toList();
        String rules=candidates.stream().map(term->term+"="+term).collect(java.util.stream.Collectors.joining("\n"));
        @SuppressWarnings("unchecked") var distribution=(List<Map<String,Object>>)TextMentions.count(texts,rules).get("distribution");
        return distribution.stream().filter(row->((Number)row.get("count")).intValue()>0).map(row->Map.<String,Object>of("term",row.get("label"),"count",row.get("count"),"base",texts.size())).toList();
    }
    public static List<Map<String,Object>> classify(List<Map<String,Object>> terms,String rules){var known=new HashSet<String>();TextMentions.parse(rules).forEach(theme->{known.add(normalized(theme.label()));theme.aliases().forEach(alias->known.add(normalized(alias)));});return terms.stream().map(term->{var result=new LinkedHashMap<String,Object>(term);boolean saved=known.contains(normalized(term.get("term").toString()));result.put("recognized",saved);result.put("recognition",saved?"En diccionario universal":"Nuevo: consultar");return (Map<String,Object>)result;}).toList();}
    public static List<Map<String,Object>> compoundConflicts(List<Map<String,Object>> terms){return terms.stream().filter(t->t.get("term").toString().contains(" ")).map(t->{String compound=t.get("term").toString();var parts=Arrays.stream(compound.split(" +")).filter(word->!STOP.contains(normalized(word))).distinct().toList();return Map.<String,Object>of("compound",compound,"individuals",parts);}).filter(t->((List<?>)t.get("individuals")).size()>1).toList();}
    public static List<Map<String,Object>> relations(List<Map<String,Object>> terms){
        var result=new ArrayList<Map<String,Object>>();
        for(int i=0;i<terms.size();i++)for(int j=i+1;j<terms.size();j++){String a=terms.get(i).get("term").toString(),b=terms.get(j).get("term").toString(),x=normalized(a),y=normalized(b);String reason=null;
            if(x.equals(y))reason="Variante de escritura";
            else if(x.equals(initials(y))||y.equals(initials(x)))reason="Posible abreviatura; confirmar contexto";
            else if(Math.min(x.length(),y.length())>=5&&Math.abs(x.length()-y.length())<=2&&distance(x,y)<=Math.max(x.length(),y.length())/7)reason="Escritura parecida; no implica sinonimia";
            if(reason!=null)result.add(Map.of("left",a,"right",b,"reason",reason));if(result.size()>=100)return result;
        }return result;
    }
    public static List<Map<String,Object>> relationsWithDictionary(List<Map<String,Object>> terms,String rules){var result=new ArrayList<Map<String,Object>>(relations(terms));for(var term:terms)for(var theme:TextMentions.parse(rules)){String current=term.get("term").toString();for(String alias:theme.aliases()){if(normalized(alias).equals(normalized(current)))continue;if(Math.abs(alias.length()-current.length())>2&&Math.min(alias.length(),current.length())>6)continue;var related=relations(List.of(term,Map.of("term",alias)));if(!related.isEmpty()){result.add(Map.of("left",theme.label(),"right",current,"reason","Diccionario universal: "+related.getFirst().get("reason")));break;}}if(result.size()>=100)return result;}return result;}
    private static String initials(String s){if(!s.contains(" "))return "";return Arrays.stream(s.split(" +")).filter(w->!STOP.contains(w)&&!w.isBlank()).map(w->w.substring(0,1)).reduce("",String::concat);}
    private static int distance(String a,String b){int[] previous=new int[b.length()+1];for(int j=0;j<previous.length;j++)previous[j]=j;for(int i=1;i<=a.length();i++){int[] next=new int[b.length()+1];next[0]=i;for(int j=1;j<=b.length();j++)next[j]=Math.min(Math.min(next[j-1]+1,previous[j]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));previous=next;}return previous[b.length()];}
}
