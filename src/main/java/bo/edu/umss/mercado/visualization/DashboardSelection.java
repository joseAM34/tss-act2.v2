package bo.edu.umss.mercado.visualization;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/** Selección explícita: nombres permitidos y valores que después se verifican contra el snapshot. */
public record DashboardSelection(String study, String survey, String version, String from, String to,
                                 String year, String employment, String graduateSector, String training,
                                 String interest, String organizationType, String organizationSize,
                                 String organizationSector, String compareBy, String groupA, String groupB) {
    private static final Set<String> COMPARISONS = Set.of("tipo_organizacion", "estado_laboral", "sector_laboral", "tamano_organizacion");
    private static final Set<String> FIELDS = Set.of("study", "survey", "version", "from", "to", "year", "employment",
            "graduateSector", "training", "interest", "organizationType", "organizationSize", "organizationSector", "compareBy", "groupA", "groupB");

    public DashboardSelection {
        study = clean(study); survey = clean(survey); version = clean(version);
        from = clean(from); to = clean(to); year = clean(year); employment = clean(employment);
        graduateSector = clean(graduateSector); training = clean(training); interest = clean(interest);
        organizationType = clean(organizationType); organizationSize = clean(organizationSize);
        organizationSector = clean(organizationSector); compareBy = clean(compareBy);
        groupA = clean(groupA); groupB = clean(groupB);
        if (!survey.isEmpty() && !Set.of("titulados", "empleadores").contains(survey))
            throw new IllegalArgumentException("Seleccione un cuestionario conocido.");
        LocalDate first = date(from), last = date(to);
        if (first != null && last != null && first.isAfter(last))
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
        if (!year.isEmpty()) {
            if (!year.matches("[0-9]{1,4}")) throw new IllegalArgumentException("El año de egreso debe ser un entero.");
            year = Integer.toString(Integer.parseInt(year));
        }
        if (!compareBy.isEmpty() && !COMPARISONS.contains(compareBy))
            throw new IllegalArgumentException("La variable de comparación no está permitida.");
        if (compareBy.isEmpty() && (!groupA.isEmpty() || !groupB.isEmpty()))
            throw new IllegalArgumentException("Seleccione la variable para comparar dos grupos.");
        if (!compareBy.isEmpty() && (groupA.isEmpty() || groupB.isEmpty() || groupA.equals(groupB)))
            throw new IllegalArgumentException("La comparación requiere dos categorías distintas.");
        boolean graduates = !year.isEmpty() || !employment.isEmpty() || !graduateSector.isEmpty()
                || !training.isEmpty() || !interest.isEmpty() || Set.of("estado_laboral", "sector_laboral").contains(compareBy);
        boolean employers = !organizationType.isEmpty() || !organizationSize.isEmpty() || !organizationSector.isEmpty()
                || Set.of("tipo_organizacion", "tamano_organizacion").contains(compareBy);
        if (graduates && employers) throw new IllegalArgumentException("Los filtros de titulados y empleadores requieren selecciones separadas.");
        if ((graduates && survey.equals("empleadores")) || (employers && survey.equals("titulados")))
            throw new IllegalArgumentException("Los filtros no corresponden al cuestionario seleccionado.");
        if (survey.isEmpty() && graduates) survey = "titulados";
        if (survey.isEmpty() && employers) survey = "empleadores";
    }

    public static DashboardSelection none() { return from(Map.of()); }

    public static DashboardSelection from(Map<String, String> values) {
        if (values == null || !FIELDS.containsAll(values.keySet()))
            throw new IllegalArgumentException("El filtro solicitado no está permitido.");
        return new DashboardSelection(values.get("study"), values.get("survey"), values.get("version"),
                values.get("from"), values.get("to"), values.get("year"), values.get("employment"),
                values.get("graduateSector"), values.get("training"), values.get("interest"),
                values.get("organizationType"), values.get("organizationSize"), values.get("organizationSector"),
                values.get("compareBy"), values.get("groupA"), values.get("groupB"));
    }

    public boolean hasFilters() {
        return !study.isEmpty() || !survey.isEmpty() || !version.isEmpty() || !from.isEmpty() || !to.isEmpty()
                || !year.isEmpty() || !employment.isEmpty() || !graduateSector.isEmpty() || !training.isEmpty()
                || !interest.isEmpty() || !organizationType.isEmpty() || !organizationSize.isEmpty() || !organizationSector.isEmpty();
    }

    public boolean isEmpty() { return !hasFilters() && compareBy.isEmpty(); }

    public String comparisonVariable() { return compareBy.equals("sector_laboral") ? "sector" : compareBy; }

    private static String clean(String value) {
        String result = value == null ? "" : value.strip();
        if (result.length() > 160 || result.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("El valor de un filtro no es válido.");
        return result;
    }

    private static LocalDate date(String value) {
        if (value.isEmpty()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("Las fechas deben usar el formato AAAA-MM-DD.");
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("Las fechas deben usar el formato AAAA-MM-DD."); }
    }
}
