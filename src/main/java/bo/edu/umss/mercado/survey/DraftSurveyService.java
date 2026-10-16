package bo.edu.umss.mercado.survey;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.data.InstrumentRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Administra únicamente estructuras en borrador; la publicación tiene su propio servicio. */
@Service
public class DraftSurveyService {
    public static final List<String> TYPES = List.of("radio", "checkbox", "select", "text", "textarea", "number", "email", "tel", "url");
    private static final Set<String> CHOICES = Set.of("radio", "checkbox", "select");
    private final JdbcTemplate jdbc;
    private final SurveyCatalog catalog;

    public DraftSurveyService(JdbcTemplate jdbc, SurveyCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    public record Section(String id, String title) {}
    public record Item(String sectionId, Question question) {}
    public record Draft(String id, String surveyId, String slug, String title, String description,
                        long revision, String sourceSlug, String sourceVersion,
                        List<Section> sections, List<Item> items, String versionCode) {
        public Draft { sections = List.copyOf(sections); items = List.copyOf(items); }
        public List<Item> itemsInSection(String sectionId) { return items.stream().filter(i -> i.sectionId().equals(sectionId)).toList(); }
        public SurveyDefinition definition() {
            return new SurveyDefinition(slug, title, description, versionCode, items.stream().map(Item::question).toList());
        }
    }
    public record Summary(String id, String title, long revision, int questionCount, String updatedAt) {}

    public boolean editable(String id) {
        var states = jdbc.queryForList("SELECT status FROM survey_version WHERE id = ?", String.class, id);
        if (states.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Versión no encontrada.");
        return "BORRADOR".equals(states.getFirst());
    }

    public List<Summary> recent() {
        return jdbc.query("""
                SELECT v.id, v.title, v.revision, v.updated_at,
                       (SELECT COUNT(*) FROM survey_question q WHERE q.version_id = v.id) AS question_count
                FROM survey_version v WHERE v.status = 'BORRADOR' ORDER BY v.updated_at DESC, v.id
                """, (rs, n) -> new Summary(rs.getString("id"), rs.getString("title"), rs.getLong("revision"),
                rs.getInt("question_count"), rs.getObject("updated_at", OffsetDateTime.class).toString()));
    }

    @Transactional(readOnly = true)
    public Draft get(String id) {
        var headers = jdbc.queryForList("""
                SELECT v.*, s.slug FROM survey_version v JOIN survey s ON s.id = v.survey_id WHERE v.id = ?
                """, id);
        if (headers.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrador no encontrado.");
        Map<String, Object> h = headers.getFirst();
        if (!"BORRADOR".equals(h.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta versión no es editable.");
        var sections = jdbc.query("SELECT section_id, title FROM survey_section WHERE version_id = ? ORDER BY position",
                (rs, n) -> new Section(rs.getString(1), rs.getString(2)), id);
        var titles = new LinkedHashMap<String, String>();
        sections.forEach(s -> titles.put(s.id(), s.title()));
        var options = new LinkedHashMap<String, List<Question.Option>>();
        jdbc.query("SELECT question_id, option_code, label FROM survey_question_option WHERE version_id = ? ORDER BY position",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> options.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(new Question.Option(rs.getString(2), rs.getString(3))), id);
        var items = jdbc.query("SELECT * FROM survey_question WHERE version_id = ? ORDER BY position", (rs, n) -> {
            String qid = rs.getString("question_id");
            String sid = rs.getString("section_id");
            return new Item(sid, new Question(qid, titles.get(sid), rs.getString("label"), rs.getString("type"),
                    rs.getBoolean("required"), options.getOrDefault(qid, List.of()), rs.getString("depends_on"),
                    rs.getString("depends_value"), rs.getInt("min_selections"), rs.getInt("max_selections"),
                    rs.getString("hint"), rs.getObject("min_value", Integer.class), rs.getObject("max_value", Integer.class)));
        }, id);
        Long currentRevision = jdbc.queryForObject("SELECT revision FROM survey_version WHERE id = ?", Long.class, id);
        if (currentRevision == null || currentRevision.longValue() != ((Number) h.get("revision")).longValue()) throw conflict();
        return new Draft(id, (String) h.get("survey_id"), (String) h.get("slug"), (String) h.get("title"),
                (String) h.get("description"), ((Number) h.get("revision")).longValue(),
                (String) h.get("source_slug"), (String) h.get("source_version"), sections, items, (String) h.get("version_code"));
    }

    @Transactional
    public String create(String title, String description, String actor) {
        return insert(title, description, null, null, List.of(), List.of(), actor, "CREAR");
    }

    @Transactional
    public String duplicate(String id, String actor) {
        Draft source = get(id);
        return insert(copyTitle(source.title()), source.description(), source.sourceSlug(), source.sourceVersion(),
                source.sections(), source.items(), actor, "DUPLICAR");
    }

    @Transactional
    public String copyLegacy(String slug, String actor) {
        SurveyDefinition source = catalog.get(slug);
        var sections = new LinkedHashMap<String, Section>();
        var items = new ArrayList<Item>();
        for (Question q : source.questions()) {
            Section section = sections.computeIfAbsent(q.section(), label -> new Section(uuid(), label));
            items.add(new Item(section.id(), q));
        }
        return insert(copyTitle(source.title()), source.description(), source.slug(), source.version(),
                List.copyOf(sections.values()), items, actor, "COPIAR_CATALOGO");
    }

    @Transactional
    public String copyPublished(PublishedSurvey source, String actor) {
        return insert(copyTitle(source.definition().title()), source.definition().description(), source.definition().slug(),
                source.definition().version(), source.sections(), source.items(), actor, "CREAR_VARIANTE");
    }

    @Transactional
    public void updateHeader(String id, long revision, String title, String description, String actor) {
        Draft d = get(id);
        save(new Draft(d.id(), d.surveyId(), d.slug(), title, description, d.revision(), d.sourceSlug(),
                d.sourceVersion(), d.sections(), d.items(), d.versionCode()), revision, actor, "EDITAR_TITULO");
    }

    @Transactional
    public void section(String id, long revision, String sectionId, String title, String actor) {
        Draft d = get(id);
        var sections = new ArrayList<>(d.sections());
        if (sectionId == null) sections.add(new Section(uuid(), title));
        else {
            int index = sectionIndex(d, sectionId);
            sections.set(index, new Section(sectionId, title));
        }
        save(withStructure(d, sections, d.items()), revision, actor, "EDITAR_SECCION");
    }

    @Transactional
    public void removeSection(String id, long revision, String sectionId, String actor) {
        Draft d = get(id);
        sectionIndex(d, sectionId);
        if (d.items().stream().anyMatch(i -> i.sectionId().equals(sectionId)))
            throw new IllegalArgumentException("Mueve o elimina las preguntas de la sección antes de eliminarla.");
        save(withStructure(d, d.sections().stream().filter(s -> !s.id().equals(sectionId)).toList(), d.items()),
                revision, actor, "ELIMINAR_SECCION");
    }

    @Transactional
    public void moveSection(String id, long revision, String sectionId, int direction, String actor) {
        Draft d = get(id);
        var sections = new ArrayList<>(d.sections());
        move(sections, sectionIndex(d, sectionId), direction);
        save(withStructure(d, sections, d.items()), revision, actor, "ORDENAR_SECCION");
    }

    @Transactional
    public void question(String id, long revision, String sectionId, Question question, boolean adding, String actor) {
        Draft d = get(id);
        sectionIndex(d, sectionId);
        var items = new ArrayList<>(d.items());
        int index = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).question().id().equals(question.id())) index = i;
        if (adding && index >= 0) throw new IllegalArgumentException("El identificador de pregunta ya existe.");
        if (!adding && index < 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pregunta no encontrada.");
        if (adding) items.add(new Item(sectionId, question)); else items.set(index, new Item(sectionId, question));
        save(withStructure(d, d.sections(), items), revision, actor, "EDITAR_PREGUNTA");
    }

    @Transactional
    public void removeQuestion(String id, long revision, String questionId, String actor) {
        Draft d = get(id);
        questionIndex(d.items(), questionId);
        save(withStructure(d, d.sections(), d.items().stream().filter(i -> !i.question().id().equals(questionId)).toList()),
                revision, actor, "ELIMINAR_PREGUNTA");
    }

    @Transactional
    public void moveQuestion(String id, long revision, String questionId, int direction, String actor) {
        Draft d = get(id);
        Item item = d.items().get(questionIndex(d.items(), questionId));
        var siblings = new ArrayList<>(d.items().stream().filter(i -> i.sectionId().equals(item.sectionId())).toList());
        move(siblings, questionIndex(siblings, questionId), direction);
        var all = new ArrayList<Item>();
        for (Section section : d.sections()) all.addAll(section.id().equals(item.sectionId()) ? siblings
                : d.items().stream().filter(i -> i.sectionId().equals(section.id())).toList());
        save(withStructure(d, d.sections(), all), revision, actor, "ORDENAR_PREGUNTA");
    }

    private String insert(String title, String description, String sourceSlug, String sourceVersion,
                          List<Section> sections, List<Item> items, String actor, String operation) {
        String surveyId = uuid();
        Draft d = withStructure(new Draft(uuid(), surveyId, "encuesta-" + surveyId.substring(0, 18), title, description,
                0, sourceSlug, sourceVersion, sections, items, "borrador-1"), sections, items);
        validate(d, actor);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO survey (id, study_id, slug, created_at, actor) VALUES (?, ?, ?, ?, ?)",
                surveyId, InstrumentRegistry.STUDY_ID, d.slug(), now, actor);
        jdbc.update("""
                INSERT INTO survey_version (id, survey_id, version_code, status, title, description, revision,
                    source_slug, source_version, created_at, updated_at)
                VALUES (?, ?, 'borrador-1', 'BORRADOR', ?, ?, 0, ?, ?, ?, ?)
                """, d.id(), surveyId, title, description, sourceSlug, sourceVersion, now, now);
        writeStructure(d);
        event(d, 0, actor, operation);
        jdbc.update("UPDATE survey SET draft_version_id = ? WHERE id = ?", d.id(), surveyId);
        return d.id();
    }

    String insertSuccessor(PublishedSurvey source, String actor, String predecessorId) {
        String versionId = uuid();
        Draft d = withStructure(new Draft(versionId, source.surveyId(), source.definition().slug(), source.definition().title(),
                source.definition().description(), 0, source.sourceSlug(), source.sourceVersion(), source.sections(),
                source.items(), "borrador-" + versionId), source.sections(), source.items());
        validate(d, actor);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO survey_version (id, survey_id, version_code, status, title, description, revision,
                    source_slug, source_version, created_at, updated_at, predecessor_id)
                VALUES (?, ?, ?, 'BORRADOR', ?, ?, 0, ?, ?, ?, ?, ?)
                """, d.id(), d.surveyId(), d.versionCode(), d.title(), d.description(), d.sourceSlug(), d.sourceVersion(), now, now, predecessorId);
        if (jdbc.update("UPDATE survey SET draft_version_id = ? WHERE id = ? AND draft_version_id IS NULL", d.id(), d.surveyId()) != 1) throw conflict();
        writeStructure(d); event(d, 0, actor, "CREAR_SUCESORA");
        return d.id();
    }

    void validateForPublication(Draft d, String actor) {
        validate(d, actor);
        if (d.items().isEmpty()) throw new IllegalArgumentException("Añade al menos una pregunta antes de publicar.");
        if (d.sections().stream().anyMatch(s -> d.itemsInSection(s.id()).isEmpty()))
            throw new IllegalArgumentException("Completa o elimina las secciones vacías antes de publicar.");
    }

    private void save(Draft d, long expected, String actor, String operation) {
        if (d.revision() != expected) throw conflict();
        validate(d, actor);
        int changed = jdbc.update("""
                UPDATE survey_version SET title = ?, description = ?, revision = revision + 1, updated_at = ?
                WHERE id = ? AND revision = ? AND status = 'BORRADOR'
                """, d.title(), d.description(), OffsetDateTime.now(ZoneOffset.UTC), d.id(), expected);
        if (changed != 1) throw conflict();
        writeStructure(d);
        event(d, expected + 1, actor, operation);
    }

    private void writeStructure(Draft d) {
        jdbc.update("DELETE FROM survey_question_option WHERE version_id = ?", d.id());
        jdbc.update("DELETE FROM survey_question WHERE version_id = ?", d.id());
        jdbc.update("DELETE FROM survey_section WHERE version_id = ?", d.id());
        int position = 0;
        for (Section s : d.sections()) jdbc.update("INSERT INTO survey_section VALUES (?, ?, ?, ?)", d.id(), s.id(), s.title(), position++);
        position = 0;
        for (Item item : d.items()) {
            Question q = item.question();
            jdbc.update("""
                    INSERT INTO survey_question (version_id, question_id, section_id, position, label, type, required,
                        hint, min_selections, max_selections, min_value, max_value, depends_on, depends_value)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, d.id(), q.id(), item.sectionId(), position++, q.label(), q.type(), q.required(), q.hint(),
                    q.minSelections(), q.maxSelections(), q.min(), q.max(), q.dependsOn(), q.dependsValue());
            int optionPosition = 0;
            for (Question.Option o : q.options()) jdbc.update("INSERT INTO survey_question_option VALUES (?, ?, ?, ?, ?)",
                    d.id(), q.id(), o.value(), o.label(), optionPosition++);
        }
    }

    private void event(Draft d, long revision, String actor, String operation) {
        jdbc.update("""
                INSERT INTO survey_definition_event VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, uuid(), d.id(), revision, operation, actor, OffsetDateTime.now(ZoneOffset.UTC),
                CanonicalJson.json(eventDefinition(d)), CanonicalJson.hash(eventDefinition(d)));
    }

    private Map<String, Object> eventDefinition(Draft d) {
        return Map.of("schema_version", "BORRADOR_V1", "survey_id", d.surveyId(), "definition", d.definition(),
                "sections", d.sections(), "items", d.items(), "source_slug", d.sourceSlug() == null ? "" : d.sourceSlug(),
                "source_version", d.sourceVersion() == null ? "" : d.sourceVersion());
    }

    private Draft withStructure(Draft d, List<Section> sections, List<Item> items) {
        var normalized = new ArrayList<Item>();
        for (Section section : sections) for (Item item : items) if (item.sectionId().equals(section.id())) {
            Question q = item.question();
            normalized.add(new Item(section.id(), new Question(q.id(), section.title(), q.label(), q.type(), q.required(),
                    q.options(), q.dependsOn(), q.dependsValue(), q.minSelections(), q.maxSelections(),
                    q.hint() == null ? "" : q.hint(), q.min(), q.max())));
        }
        if (normalized.size() != items.size()) throw new IllegalArgumentException("Una pregunta tiene una sección inexistente.");
        return new Draft(d.id(), d.surveyId(), d.slug(), d.title(), d.description(), d.revision(), d.sourceSlug(),
                d.sourceVersion(), sections, normalized, d.versionCode());
    }

    private void validate(Draft d, String actor) {
        text(d.title(), 200, true, "El título"); text(d.description(), 5000, false, "La descripción");
        text(actor, 100, true, "El usuario administrador");
        if (d.sections().size() > 40 || d.items().size() > 200) throw new IllegalArgumentException("Se admiten hasta 40 secciones y 200 preguntas.");
        var sectionIds = new HashSet<String>();
        for (Section s : d.sections()) {
            text(s.title(), 200, true, "El título de sección");
            if (!sectionIds.add(s.id())) throw new IllegalArgumentException("Hay una sección duplicada.");
        }
        var preceding = new LinkedHashMap<String, Question>();
        for (Item item : d.items()) {
            Question q = item.question();
            code(q.id(), 64, "Identificador de pregunta");
            if (Set.of("formToken", "consent").contains(q.id())) throw new IllegalArgumentException("Ese identificador está reservado para el envío del formulario.");
            if (preceding.containsKey(q.id())) throw new IllegalArgumentException("Hay preguntas con el mismo identificador.");
            if (!TYPES.contains(q.type())) throw new IllegalArgumentException("El tipo de pregunta no está disponible.");
            text(q.label(), 2000, true, "El enunciado"); text(q.hint(), 2000, false, "La ayuda");
            var codes = new HashSet<String>();
            if (q.options().size() > 100) throw new IllegalArgumentException("Se admiten hasta 100 opciones por pregunta.");
            for (Question.Option o : q.options()) {
                code(o.value(), 100, "Código de opción"); text(o.label(), 1000, true, "El texto de opción");
                if (!codes.add(o.value())) throw new IllegalArgumentException("Hay códigos de opción repetidos.");
            }
            if (CHOICES.contains(q.type()) != !q.options().isEmpty())
                throw new IllegalArgumentException("Las preguntas de selección necesitan opciones; los demás tipos no las admiten.");
            if (q.minSelections() < 0 || q.maxSelections() < 1 || q.minSelections() > q.maxSelections()
                    || (q.type().equals("checkbox") && q.maxSelections() > q.options().size())
                    || (!q.type().equals("checkbox") && (q.maxSelections() != 1 || q.minSelections() > 1)))
                throw new IllegalArgumentException("Revisa el mínimo y máximo de selecciones.");
            if (q.type().equals("number")) {
                if ((q.min() != null && (q.min() < 0 || q.min() > 999999999))
                        || (q.max() != null && (q.max() < 0 || q.max() > 999999999))
                        || (q.min() != null && q.max() != null && q.min() > q.max()))
                    throw new IllegalArgumentException("Los enteros deben estar entre 0 y 999999999, con mínimo no mayor al máximo.");
            } else if (q.min() != null || q.max() != null) throw new IllegalArgumentException("Solo las preguntas numéricas admiten rango.");
            if (q.dependsOn() != null) {
                Question control = preceding.get(q.dependsOn());
                if (control == null || !CHOICES.contains(control.type()) || q.dependsValue() == null || q.dependsValue().isBlank())
                    throw new IllegalArgumentException("La condición debe referirse a una pregunta de selección anterior.");
                Set<String> allowed = control.options().stream().map(Question.Option::value).collect(java.util.stream.Collectors.toSet());
                List<String> values = List.of(q.dependsValue().split("\\|", -1));
                if (!allowed.containsAll(values) || new HashSet<>(values).size() != values.size())
                    throw new IllegalArgumentException("La condición contiene opciones inexistentes o repetidas.");
            } else if (q.dependsValue() != null) throw new IllegalArgumentException("La condición necesita una pregunta de control.");
            preceding.put(q.id(), q);
        }
    }

    private static void text(String value, int max, boolean required, String label) {
        if (value == null || (required && value.isBlank()) || value.length() > max || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException(label + " debe tener " + (required ? "entre 1 y " : "hasta ") + max + " caracteres válidos.");
    }
    private static void code(String value, int max, String label) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z][A-Za-z0-9_]*"))
            throw new IllegalArgumentException(label + ": usa letras, números y guion bajo, comenzando con una letra.");
    }
    private int sectionIndex(Draft d, String id) {
        for (int i = 0; i < d.sections().size(); i++) if (d.sections().get(i).id().equals(id)) return i;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sección no encontrada.");
    }
    private int questionIndex(List<Item> items, String id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).question().id().equals(id)) return i;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pregunta no encontrada.");
    }
    private <T> void move(List<T> items, int index, int direction) {
        if (Math.abs(direction) != 1 || index + direction < 0 || index + direction >= items.size())
            throw new IllegalArgumentException("No se puede mover más allá del inicio o final.");
        java.util.Collections.swap(items, index, index + direction);
    }
    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "El borrador cambió en otra pestaña. Recarga antes de guardar.");
    }
    private static String copyTitle(String title) { return "Copia de " + title.substring(0, Math.min(title.length(), 191)); }
    private static String uuid() { return UUID.randomUUID().toString(); }
}
