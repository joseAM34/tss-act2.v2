package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.util.*;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UniversalTermLookup {
    private final JdbcTemplate jdbc;private final WikidataTerms wikidata;private final TextDictionaryService dictionaries;
    public UniversalTermLookup(JdbcTemplate jdbc,WikidataTerms wikidata,TextDictionaryService dictionaries){this.jdbc=jdbc;this.wikidata=wikidata;this.dictionaries=dictionaries;}
    @SuppressWarnings("unchecked")
    public Map<String,Object> lookup(String original){if(original==null||original.contains("@")||original.contains("://")||original.matches(".*\\d{6,}.*"))throw new IllegalArgumentException("Consulta términos sin contactos ni identificadores.");String term=TextMentions.normalize(original);if(term.isBlank()||term.length()>100)throw new IllegalArgumentException("Término no válido.");
        for(var theme:TextMentions.parse(dictionaries.global().rules()))if(theme.aliases().stream().anyMatch(a->TextMentions.normalize(a).equals(term)))return Map.of("status","ESTABLECIDO","term",term,"group",theme.label(),"aliases",theme.aliases(),"cached",true);
        String key=CanonicalJson.hash(term);var saved=jdbc.queryForList("SELECT payload_json,payload_hash FROM universal_term_lookup WHERE term_key=?",key);if(!saved.isEmpty()){var payload=CanonicalJson.read(saved.getFirst().get("payload_json").toString(),Map.class);if(!CanonicalJson.hash(payload).equals(saved.getFirst().get("payload_hash")))throw new IllegalStateException("La consulta guardada no coincide con su huella.");var result=new LinkedHashMap<String,Object>(payload);if(!"EXACT_UNIQUE_V2".equals(result.get("selection_policy"))){result.remove("group");result.remove("aliases");result.remove("url");result.put("status",((List<?>)result.getOrDefault("candidates",List.of())).isEmpty()?"SIN_RESULTADO":"REVISAR");result.put("selection_policy","EXACT_UNIQUE_V2");jdbc.update("UPDATE universal_term_lookup SET payload_json=?,payload_hash=? WHERE term_key=?",CanonicalJson.json(result),CanonicalJson.hash(result),key);}result.put("cached",true);return result;}
        var candidates=wikidata.search(term);var result=new LinkedHashMap<String,Object>();result.put("term",term);result.put("status",candidates.isEmpty()?"SIN_RESULTADO":"REVISAR");result.put("candidates",candidates);result.put("cached",false);
        var exact=candidates.stream().filter(c->TextMentions.normalize(c.get("label").toString()).equals(term)||TextMentions.normalize(Objects.toString(c.get("matched"),"")).equals(term)).toList();result.put("selection_policy","EXACT_UNIQUE_V2");
        if(exact.size()==1&&!(term.length()<=3&&candidates.size()>1)){var aliases=aliases(exact.getFirst().get("id").toString());result.put("group",TextMentions.normalize(exact.getFirst().get("label").toString()));result.put("aliases",aliases.get("aliases"));result.put("url",aliases.get("url"));}else if(exact.size()>1||(term.length()<=3&&candidates.size()>1))result.put("status","AMBIGUO");
        try{jdbc.update("INSERT INTO universal_term_lookup(term_key,term,payload_json,payload_hash,created_at) VALUES(?,?,?,?,?)",key,term,CanonicalJson.json(result),CanonicalJson.hash(result),OffsetDateTime.now());}catch(org.springframework.dao.DuplicateKeyException ex){/* Otra solicitud ya conservó el resultado. */}return result;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> aliases(String id){if(!id.matches("Q[0-9]{1,12}"))throw new IllegalArgumentException("Concepto no válido.");String key=CanonicalJson.hash("WIKIDATA:"+id);var rows=jdbc.queryForList("SELECT payload_json,payload_hash FROM universal_term_lookup WHERE term_key=?",key);if(!rows.isEmpty()){var payload=CanonicalJson.read(rows.getFirst().get("payload_json").toString(),Map.class);if(!CanonicalJson.hash(payload).equals(rows.getFirst().get("payload_hash")))throw new IllegalStateException("La consulta guardada no coincide con su huella.");return payload;}var payload=wikidata.aliases(id);try{jdbc.update("INSERT INTO universal_term_lookup(term_key,term,payload_json,payload_hash,created_at) VALUES(?,?,?,?,?)",key,"WIKIDATA:"+id,CanonicalJson.json(payload),CanonicalJson.hash(payload),OffsetDateTime.now());}catch(org.springframework.dao.DuplicateKeyException ex){/* Resultado conservado por otra solicitud. */}return payload;}
}
