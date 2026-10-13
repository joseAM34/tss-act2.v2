package bo.edu.umss.mercado.survey;

import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import bo.edu.umss.mercado.survey.Question.Option;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Instrumento inicial: los códigos y las escalas se conservan en las respuestas. */
@Component
public class SurveyCatalog {
    private static final String VERSION = "2026-10-borrador-1";
    private static final List<Option> SI_NO = List.of(o("SI", "Sí"), o("NO", "No"));
    private static final List<Option> ACUERDO_TITULADOS = List.of(
            o("TOTALMENTE_DESACUERDO", "Totalmente en desacuerdo"),
            o("DESACUERDO", "En desacuerdo"),
            o("ACUERDO", "De acuerdo"),
            o("TOTALMENTE_ACUERDO", "Totalmente de acuerdo"));
    private static final List<Option> ACUERDO_EMPLEADORES = List.of(
            o("TOTALMENTE_ACUERDO", "Totalmente de acuerdo"),
            o("PARCIALMENTE_ACUERDO", "Parcialmente de acuerdo"),
            o("TOTALMENTE_DESACUERDO", "Totalmente desacuerdo"),
            o("NO_SABE", "No sabe"));
    private static final List<Option> INSTITUCIONES = List.of(
            o("EUPG", "Escuela Universitaria de Posgrado (EUPG)"),
            o("POSGRADO_FCYT", "Dirección de Posgrado FCyT"),
            o("IBNORCA", "IBNORCA"),
            o("UNIVERSIDAD_INTERNACIONAL", "Universidades internacionales"),
            o("UNIVERSIDAD_PUBLICA_NACIONAL", "Universidades públicas nacionales"),
            o("UNIVERSIDAD_PRIVADA_NACIONAL", "Universidades privadas nacionales"),
            o("OTRO", "Otro"));
    private static final List<Option> SECTORES = List.of(
            o("DESARROLLO_SOFTWARE", "Desarrollo de software"),
            o("SERVICIOS_TI", "Servicios de tecnologías de información (TI)"),
            o("TELECOMUNICACIONES_REDES", "Telecomunicaciones y redes"),
            o("SOPORTE_MANTENIMIENTO", "Soporte técnico y mantenimiento de sistemas"),
            o("CONSULTORIA_TRANSFORMACION_DIGITAL", "Consultoría en sistemas / transformación digital"),
            o("BANCA_FINANZAS", "Banca y servicios financieros"),
            o("GOBIERNO", "Instituciones públicas / gobierno"),
            o("EDUCACION_INVESTIGACION", "Educación e investigación"),
            o("INDUSTRIA_MANUFACTURA", "Industria y manufactura"),
            o("COMERCIO", "Comercio y comercio electrónico"),
            o("SALUD", "Salud"),
            o("CIBERSEGURIDAD", "Seguridad informática / ciberseguridad"),
            o("ANALISIS_DATOS", "Análisis de datos / inteligencia de negocios"),
            o("EMPRENDIMIENTO_TECNOLOGICO", "Startups o emprendimientos tecnológicos"),
            o("OTROS", "Otros"));

    private final List<SurveyDefinition> surveys = List.of(titulados(), empleadores());

    public SurveyDefinition get(String slug) {
        return surveys.stream().filter(s -> s.slug().equals(slug)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta no encontrada"));
    }

    public List<SurveyDefinition> all() {
        return surveys;
    }

    private static SurveyDefinition titulados() {
        var q = new ArrayList<Question>();
        String datos = "Datos generales y formación complementaria";
        q.add(number("anio_egreso", datos, "¿En qué año egresó de Ingeniería en Sistemas?", true,
                1960, Year.now(ZoneId.of("America/La_Paz")).getValue(), "Escriba el año completo, por ejemplo: 2022."));
        q.add(choice("formacion_complementaria", datos,
                "Posterior a sus estudios de pregrado en Ingeniería en Sistemas, ¿ha realizado o se encuentra realizando algún programa de formación complementaria?",
                "radio", true, SI_NO, "Incluye posgrado, diplomados, doble titulación por diplomado y especializaciones."));
        q.add(conditional("nivel_cursado", datos,
                "¿Cuál es el programa de formación complementaria de mayor nivel que ha concluido o que se encuentra realizando?",
                "select", true, List.of(o("DIPLOMADO", "Diplomado"), o("ESPECIALIDAD", "Especialidad"),
                        o("MAESTRIA", "Maestría"), o("DOCTORADO", "Doctorado"), o("POSDOCTORADO", "Pos doctorado")),
                "formacion_complementaria", "SI", null));
        q.add(conditional("institucion_cursada", datos, "¿Dónde ha cursado el programa indicado?", "select", true,
                INSTITUCIONES, "formacion_complementaria", "SI", null));
        q.add(conditional("financiamiento_cursado", datos, "¿Cuál fue la fuente de financiamiento para cursar el programa indicado?",
                "radio", true, List.of(o("RECURSOS_PROPIOS", "Financiado con recursos propios"),
                        o("BECA_TOTAL", "Total financiamiento por medio de una beca"),
                        o("BECA_PARCIAL", "Financiado parcialmente por medio de una beca")),
                "formacion_complementaria", "SI", null));
        q.add(conditional("programa_cursado", datos, "Indique el nombre del programa", "text", true, List.of(),
                "formacion_complementaria", "SI", null));

        String futuro = "Interés en formación continua";
        q.add(choice("interes_posgrado", futuro,
                "¿Estaría interesado en realizar estudios en algún programa de posgrado dentro de las áreas de la Ingeniería en Sistemas?",
                "radio", true, SI_NO, null));
        q.add(conditional("area_interes", futuro, "¿En cuál de las siguientes áreas le interesaría realizar el programa?",
                "select", true, List.of(o("IA", "IA"), o("ROBOTICA", "Robótica"),
                        o("INGENIERIA_SOFTWARE", "Ingeniería de Software"), o("BASE_DATOS", "Base de Datos"),
                        o("REDES_SEGURIDAD", "Redes de Datos y Seguridad")), "interes_posgrado", "SI", null));
        q.add(conditional("institucion_interes", futuro,
                "En caso de realizar un estudio de posgrado, ¿en qué organización educativa optaría por realizar sus estudios de posgrado?",
                "select", true, INSTITUCIONES, "interes_posgrado", "SI", null));
        q.add(conditional("nivel_interes", futuro, "¿Qué nivel de posgrado le interesaría realizar?",
                "radio", true, List.of(o("MAESTRIA", "Maestría"), o("ESPECIALIDAD", "Especialidad"),
                        o("DOCTORADO", "Doctorado")), "interes_posgrado", "SI",
                "Seleccione su preferencia principal."));
        q.add(conditional("financiamiento_futuro", futuro,
                "¿Cuál sería la principal fuente de financiamiento para sus futuros estudios?", "radio", true,
                List.of(o("RECURSOS_PROPIOS", "Recursos propios"), o("BECA_ESTATAL", "Becas estatales"),
                        o("CREDITO_EDUCATIVO", "Créditos educativos"), o("PATROCINIO_EMPRESARIAL", "Patrocinio empresarial")),
                "interes_posgrado", "SI", "Seleccione la fuente principal que estima utilizar."));
        q.add(choice("opinion_posgrado_fcyt", futuro,
                "¿Considera que existen programas de formación de posgrado ofertados en la Dirección de Posgrado de la FCyT que son afines a las áreas de especialidad de un Ingeniero en Sistemas?",
                "radio", true, ACUERDO_TITULADOS, null));

        String empleo = "Trayectoria y situación laboral";
        q.add(number("anios_vida_profesional", empleo,
                "¿Cuántos son los años de vida profesional que lleva hasta la fecha?", false,
                0, 80, "Ingrese años completos. Puede dejarlo vacío si necesita expresar fracciones de año."));
        q.add(choice("estado_laboral", empleo, "¿Cuál de las siguientes afirmaciones describe su situación laboral actual?",
                "radio", true, List.of(o("EMPLEADO", "Actualmente me encuentro trabajando en una organización"),
                        o("DESEMPLEADO", "Actualmente NO trabajo"),
                        o("EMPRENDIMIENTO", "Actualmente dedico mi tiempo COMPLETO a un emprendimiento propio")), null));
        var sectoresTitulados = new ArrayList<>(SECTORES);
        sectoresTitulados.add(o("NO_APLICA", "No desempeño una actividad laboral actualmente"));
        q.add(choice("sector", empleo, "¿Cuál es el sector de su desempeño laboral actual?", "select", true,
                sectoresTitulados, "Si actualmente no trabaja, seleccione la opción que lo indica."));
        q.add(conditional("primer_empleo_actual", empleo, "¿El trabajo que ejerce actualmente es su primer empleo?",
                "radio", true, SI_NO, "estado_laboral", "EMPLEADO", null));
        q.add(choice("tiempo_primer_empleo", empleo,
                "Luego de titularse, ¿cuánto tiempo se demoró en conseguir su primer trabajo relacionado con su formación como Ingeniero en Sistemas?",
                "select", false, List.of(o("ANTES_TITULACION", "Ya trabajaba antes de titularse"),
                        o("MENOS_1_MES", "Menos de 1 mes"), o("ENTRE_1_4_MESES", "Entre 1 - 4 meses"),
                        o("ENTRE_4_8_MESES", "Entre 4 - 8 meses"), o("ENTRE_8_12_MESES", "Entre 8 - 12 meses"),
                        o("MAS_12_MESES", "Más de 12 meses")),
                "Si todavía no obtuvo un empleo relacionado con su formación, deje esta pregunta vacía."));
        q.add(choice("cantidad_empleos", empleo,
                "Desde que se tituló, ¿en cuántos empleos usted se ha desempeñado? (Incluyendo su actual empleo si corresponde)",
                "select", false, List.of(o("UNO", "1"), o("DOS", "2"), o("TRES", "3"), o("MAS_TRES", "Más de 3")),
                "Si aún no tuvo un empleo, deje esta pregunta vacía."));

        String negocio = "Emprendimiento propio";
        q.add(conditional("origen_emprendimiento", negocio, "¿Cuál es el origen del desarrollo de su propio emprendimiento?",
                "radio", true, List.of(o("IDEA_PROPIA", "Idea propia"), o("IDEA_CONJUNTA", "Idea conjunta con compañeros"),
                        o("STARTUP", "StartUp"), o("SPIN_OFF", "Spin Off (derivado de un proyecto de la Universidad)"),
                        o("OTRO", "Otro")), "estado_laboral", "EMPRENDIMIENTO", null));
        q.add(conditional("entregable_emprendimiento", negocio, "¿Qué tipo de entregable genera el negocio?",
                "radio", true, List.of(o("PRODUCTO", "Producto"), o("SERVICIO", "Servicio"), o("AMBOS", "Ambos")),
                "estado_laboral", "EMPRENDIMIENTO", null));
        q.add(conditional("financiamiento_emprendimiento", negocio, "¿Ha requerido financiamiento externo para su negocio?",
                "radio", true, List.of(o("PRESTAMO_BANCARIO", "Sí, mediante préstamos bancarios"),
                        o("INVERSORES", "Sí, mediante inversores"), o("PROPIO", "No, financiamiento propio")),
                "estado_laboral", "EMPRENDIMIENTO", null));
        q.add(conditional("satisfaccion_emprendimiento", negocio, "¿Qué tan satisfecho está con el rendimiento actual de su negocio?",
                "radio", true, List.of(o("INSATISFECHO", "Insatisfecho"), o("ALGO_SATISFECHO", "Algo satisfecho"),
                        o("SATISFECHO", "Satisfecho")), "estado_laboral", "EMPRENDIMIENTO", null));
        q.add(conditional("importancia_formacion_negocio", negocio,
                "¿Qué tan importante considera que fue su formación en Ingeniería en Sistemas para iniciar y gestionar su negocio?",
                "radio", true, List.of(o("NO_IMPORTANTE", "No es importante"), o("POCO_IMPORTANTE", "Poco importante"),
                        o("IMPORTANTE", "Importante"), o("MUY_IMPORTANTE", "Muy importante")),
                "estado_laboral", "EMPRENDIMIENTO", null));

        String formacion = "Valoración de la formación profesional";
        q.add(choice("satisfaccion_formacion", formacion,
                "De manera global, ¿cuál es el grado de satisfacción que ha desarrollado con la formación profesional recibida en la Carrera de Ingeniería de Sistemas?",
                "radio", true, ACUERDO_TITULADOS, "Se conserva la escala de acuerdo del instrumento original."));
        q.add(choice("concordancia_formacion", formacion,
                "Considera que existe concordancia entre la formación profesional recibida en la Carrera de Ingeniería de Sistemas y los requerimientos",
                "radio", true, ACUERDO_TITULADOS, "El enunciado termina así en el documento original; su redacción está pendiente de revisión."));
        q.add(multiple("aspectos_utiles", formacion, "¿Qué aspectos de la Carrera les resultaron de bastante utilidad?",
                false, List.of(
                        o("APLICACION_PRACTICA", "La aplicación práctica de conceptos teóricos en proyectos reales."),
                        o("PASANTIAS", "La oportunidad de realizar pasantías o prácticas profesionales."),
                        o("HORARIOS", "La distribución de los horarios de clases."),
                        o("HABILIDADES_ANALITICAS", "El desarrollo de habilidades analíticas y de resolución de problemas."),
                        o("EQUIPOS_MULTIDISCIPLINARIOS", "La colaboración de equipos multidisciplinarios para resolver problemas complejos."),
                        o("HERRAMIENTAS_INDUSTRIA", "La enseñanza de herramientas y software específico de la industria.")),
                0, 6, "Puede seleccionar varias opciones."));
        q.add(multiple("aspectos_mejorar", formacion, "¿Qué aspectos de la Carrera considera que pueden mejorarse?",
                false, List.of(
                        o("INTEGRACION_TEORIA_PRACTICA", "Mayor integración de la teoría con aplicaciones prácticas en el plan de estudios."),
                        o("VINCULACION_EMPRESAS", "Mayor relacionamiento del estudiante con las empresas por medio de prácticas profesionales."),
                        o("HORARIOS", "La distribución de horarios de clases. (Horario para trabajadores, módulos virtuales, etc.)"),
                        o("ACTUALIZACION_PLAN", "Actualización constante del plan de estudios para atender tendencias actuales."),
                        o("ACTIVIDADES_EXTRACURRICULARES", "Incremento de actividades extracurriculares (Investigación, servicio social, etc.)"),
                        o("ESPECIALIZACION", "Ofrecimiento de más opciones de especialización.")),
                0, 6, "Puede seleccionar varias opciones."));
        q.add(multiple("asignaturas_ventaja", formacion,
                "Indique 5 asignaturas cursadas en la Carrera que considera que le brindaron una ventaja competitiva al momento de buscar trabajo o desenvolverse profesionalmente.",
                false, List.of(o("INTRODUCCION", "Introducción"), o("BASE_DATOS", "Base de Datos"),
                        o("INTELIGENCIA_ARTIFICIAL", "Inteligencia Artificial"), o("REDES_COMPUTADORA", "Redes de Computadora"),
                        o("ELECTIVAS", "Electivas"), o("TIS", "T.I.S."), o("BASICAS", "Básicas (Física, Cálculo, Álgebras)"),
                        o("PLANIFICACION_GESTION", "Planificación de Proyectos y Gestión"),
                        o("REDES_AVANZADAS", "Tecnología Redes Avanzadas")),
                5, 5, "Si responde esta pregunta, seleccione 5 opciones."));
        q.add(text("otra_asignatura_ventaja", formacion, "Otra asignatura que le brindó una ventaja competitiva", "text", false, null));

        String contacto = "Red de contactos";
        q.add(choice("interes_red_contactos", contacto,
                "¿Le interesaría formar parte de una red de contactos oficial de la Carrera de Ingeniería en Sistemas para compartir experiencias, proyectos y ofertas laborales?",
                "radio", true, SI_NO, null));
        q.add(text("email_contacto", contacto, "Email de contacto", "email", false,
                "Opcional. Proporciónelo únicamente si desea compartirlo."));
        q.add(text("telefono_contacto", contacto, "Número telefónico de contacto", "tel", false,
                "Opcional. De preferencia con acceso a WhatsApp."));
        q.add(text("linkedin", contacto, "Link de LinkedIn", "url", false,
                "Opcional. Ingrese la dirección completa, comenzando con https://."));

        return new SurveyDefinition("titulados", "Encuesta a titulados de Ingeniería en Sistemas",
                "Su opinión ayudará a conocer la formación, las expectativas de posgrado y la empleabilidad de los titulados. "
                        + "No se recopilan su nombre ni datos de contacto automáticamente. Los campos de contacto son opcionales. "
                        + "Los resultados y respuestas se consultan en un panel protegido. Instrumento en versión inicial de revisión.",
                VERSION, q);
    }

    private static SurveyDefinition empleadores() {
        var q = new ArrayList<Question>();
        String datos = "Datos de la organización";
        q.add(text("email_contacto", datos, "Correo", "email", true,
                "Este instrumento solicita un correo y el nombre de la organización; las respuestas no son anónimas."));
        q.add(text("nombre_organizacion", datos, "Nombre de la Empresa o Institución", "text", true, null));
        q.add(choice("tipo_organizacion", datos, "Tipo de organización", "radio", true,
                List.of(o("PUBLICO", "Público"), o("PRIVADO", "Privado")), null));
        q.add(choice("tamano_organizacion", datos, "Tamaño de la organización", "select", true,
                List.of(o("GRANDE", "Grande (100 funcionarios o más)"),
                        o("MEDIANA", "Mediana (entre 31 a 99 funcionarios)"),
                        o("PEQUENA", "Pequeña (entre 11 a 30 funcionarios)"),
                        o("MICRO", "Micro (hasta 10 funcionarios)")), null));
        q.add(choice("sector", datos,
                "Indique el rubro o sector principal de la organización en la que se desempeña laboralmente el profesional titulado de la Carrera de Ingeniería de Sistemas.",
                "select", true, SECTORES, null));

        String empleo = "Empleabilidad";
        q.add(choice("contratacion_reciente", empleo,
                "¿La organización ha contratado Ingenieros de la carrera de Sistemas en los últimos 5 años?",
                "radio", true, SI_NO, null));
        q.add(choice("probabilidad_contratacion", empleo,
                "Considerando las necesidades actuales y proyectadas de la organización, ¿existe la posibilidad de incorporar Ingenieros de Sistemas en su equipo de trabajo durante los próximos años?",
                "radio", true, List.of(o("ALTA", "Sí, existe una alta probabilidad de contratación"),
                        o("SEGUN_CRECIMIENTO", "Sí, dependiendo del crecimiento o nuevos proyectos de la organización"),
                        o("NO_PREVISTA", "No se tiene prevista la contratación actualmente"),
                        o("NO_REQUIERE", "No requiere profesionales del área de Sistemas")), null));
        q.add(choice("nivel_formacion_demandado", empleo,
                "¿Qué nivel de formación académica o actualización profesional demanda su organización para contratar nuevos profesionales del área?",
                "select", true, List.of(o("LICENCIATURA", "Licenciatura"), o("DIPLOMADO", "Diplomado"),
                        o("ESPECIALIDAD", "Especialidad"), o("MAESTRIA", "Maestría"),
                        o("DOCTORADO", "Doctorado"), o("POSDOCTORADO", "Post-doctorado")),
                "Seleccione el nivel principal que demanda su organización."));
        q.add(choice("medio_convocatoria", empleo,
                "¿A través de qué medio convoca a profesionales en su organización (empresa o institución)?",
                "radio", false, List.of(o("ESCRITOS_AUDIOVISUALES", "Medios escritos y audiovisuales"),
                        o("INTERNET_REDES", "Internet y Redes sociales"),
                        o("POSTULACION_COMPETITIVA", "Postulación competitiva (convocatorias públicas y privadas)"),
                        o("OFERTA_PERSONAL", "Oferta profesional personal"),
                        o("RECOMENDACION", "Recomendación de terceros"), o("OTROS", "Otros")),
                "Seleccione el medio principal."));
        q.add(multiple("cargos_desempenados", empleo,
                "¿Qué tipo de cargos desempeñan los profesionales titulados de la Carrera de Ingeniería de Sistemas en su organización?",
                true, List.of(o("APOYO", "De apoyo / asistencia tecnológica"),
                        o("TECNICO", "Técnico (desarrollo de software, administración de sistemas, redes, bases de datos, soporte TI)"),
                        o("ANALISTA", "Analista (sistemas, datos, procesos o soluciones tecnológicas)"),
                        o("ESPECIALISTA", "Especialista (ciberseguridad, arquitectura de software, infraestructura tecnológica, entre otros)"),
                        o("SUPERVISOR", "Supervisor / Coordinador de área tecnológica"),
                        o("JEFATURA", "Jefatura o Gerencial (gestión de proyectos, dirección de TI, transformación digital)"),
                        o("CONSULTOR", "Consultor tecnológico"), o("OTRO", "Otro")), 1, 8,
                "Puede seleccionar más de una opción."));
        q.add(text("otro_cargo", empleo, "Otro cargo desempeñado", "text", false,
                "Puede especificarlo si seleccionó Otro."));
        q.add(text("areas_emergentes", empleo,
                "Desde su experiencia en el sector laboral, ¿qué nuevas áreas de conocimiento, tecnologías o tendencias emergentes considera importantes para la formación del profesional de Ingeniería de Sistemas?",
                "textarea", true, null));
        q.add(text("herramientas_tecnologicas", empleo,
                "¿Cuáles son las nuevas herramientas tecnológicas (software, plataformas o entornos de desarrollo) que, a su criterio, debería conocer y manejar el profesional titulado de la Carrera de Ingeniería de Sistemas?",
                "textarea", true, null));
        q.add(text("competencias_requeridas", empleo,
                "Desde su experiencia en el ámbito laboral, ¿qué habilidades, competencias y destrezas considera fundamentales que debe desarrollar el profesional de Ingeniería de Sistemas para responder adecuadamente a las necesidades de la organización y del mercado laboral?",
                "textarea", true, null));

        String perfil = "Formación y perfil profesional";
        String[] criterios = {
                "La Carrera de Ingeniería de Sistemas de la Universidad Mayor de San Simón brinda confianza a nuestra organización como formadora de profesionales del área tecnológica.",
                "Considero que el título profesional otorgado por la Carrera de Ingeniería de Sistemas es consistente con el nivel de formación y las competencias demostradas por sus titulados.",
                "Las autoridades de la Carrera de Ingeniería de Sistemas consultan regularmente nuestra opinión como empleadores respecto al perfil profesional y las competencias requeridas en el ámbito laboral.",
                "Conozco el perfil profesional y las competencias de egreso declaradas por la Carrera de Ingeniería de Sistemas.",
                "El perfil profesional declarado por la Carrera de Ingeniería de Sistemas es coherente con el desempeño laboral demostrado por sus titulados.",
                "La formación proporcionada por la Carrera de Ingeniería de Sistemas permite a sus titulados alcanzar un desempeño destacado tanto en el ámbito profesional como en su formación integral.",
                "La Carrera de Ingeniería de Sistemas considera nuestra opinión como empleadores e incorpora las necesidades del sector laboral en la definición y mejora de actividades de formación práctica, pasantías y modalidades de titulación.",
                "El desempeño profesional de los titulados de Ingeniería de Sistemas evidencia el desarrollo de competencias laborales como: comunicación efectiva, análisis y resolución de problemas, pensamiento crítico, aprendizaje continuo, trabajo en equipo, iniciativa personal y manejo adecuado de tecnologías de información.",
                "Los profesionales titulados de Ingeniería de Sistemas demuestran valores y actitudes relacionados con la ética profesional, responsabilidad social, inclusión, diversidad, respeto a los derechos humanos y compromiso con el medio ambiente.",
                "He participado en procesos de recopilación de información, evaluación o retroalimentación relacionados con el desempeño laboral de los titulados de la Carrera de Ingeniería de Sistemas."
        };
        for (int i = 0; i < criterios.length; i++) {
            q.add(choice("criterio_%02d".formatted(i + 1), perfil, criterios[i], "radio", true,
                    ACUERDO_EMPLEADORES, "Seleccione una valoración. No sabe es una respuesta válida."));
        }

        return new SurveyDefinition("empleadores", "Encuesta a empleadores",
                "Conoceremos la percepción del sector empleador sobre la formación y el desempeño de titulados de Ingeniería de Sistemas de la UMSS. "
                        + "Las respuestas se usarán para fines académicos e institucionales y se consultarán en un panel protegido. "
                        + "Se solicita correo y organización, por lo que esta encuesta recoge datos identificables. Instrumento en versión inicial de revisión.",
                VERSION, q);
    }

    private static Option o(String value, String label) {
        return new Option(value, label);
    }

    private static Question choice(String id, String section, String label, String type, boolean required,
                                   List<Option> options, String hint) {
        return new Question(id, section, label, type, required, options, null, null, 0, 1, hint, null, null);
    }

    private static Question text(String id, String section, String label, String type, boolean required, String hint) {
        return choice(id, section, label, type, required, List.of(), hint);
    }

    private static Question number(String id, String section, String label, boolean required,
                                   int min, int max, String hint) {
        return new Question(id, section, label, "number", required, List.of(), null, null, 0, 1, hint, min, max);
    }

    private static Question conditional(String id, String section, String label, String type, boolean required,
                                        List<Option> options, String dependsOn, String dependsValue, String hint) {
        return new Question(id, section, label, type, required, options, dependsOn, dependsValue,
                0, 1, hint, null, null);
    }

    private static Question multiple(String id, String section, String label, boolean required,
                                     List<Option> options, int min, int max, String hint) {
        return new Question(id, section, label, "checkbox", required, options, null, null, min, max, hint, null, null);
    }
}
