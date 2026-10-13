# Instrumento de recolección: primera implementación

Versión del catálogo: `2026-10-borrador-1`. Fecha: 2026-10-13.

Esta versión permite revisar y probar las encuestas. No representa un cuestionario final aprobado para recolectar datos del estudio. La eliminación de la repetición de criterios de empleadores y el acceso público a las encuestas con un panel protegido sí fueron indicados por el usuario; las adaptaciones restantes son propuestas del primer desarrollo.

Fuente: «Guía Metodológica y Técnica Integral — Análisis de Mercado y Herramienta de Recolección de Datos», entregada en el PDF de José Armando Mareño Mamani. Las páginas citadas corresponden al PDF. El catálogo implementado se encuentra en `src/main/java/bo/edu/umss/mercado/survey/SurveyCatalog.java`.

## Trazabilidad

| Encuesta / bloque | Fuente | Implementación |
| --- | --- | --- |
| Titulados: año de egreso, ocupación, sector | HU1, p. 3; empleo, p. 9 | Año numérico obligatorio entre 1960 y el año actual de `America/La_Paz`; situación laboral y sector obligatorios. El límite inferior es una validación provisional. Se reutiliza el catálogo de sectores del anexo de empleadores, p. 15, y se agrega `NO_APLICA` para quienes actualmente no trabajan. |
| Titulados: formación complementaria realizada | pp. 7–8 | Indicador sí/no, nivel, institución, financiamiento y nombre del programa. Los cuatro últimos dependen de una respuesta Sí. |
| Titulados: interés futuro y opinión de posgrado FCyT | p. 8 | Área e institución del anexo; valoración de FCyT con su escala original de cuatro categorías. |
| Titulados: nivel de posgrado futuro | HU2, pp. 3–4 | Pregunta propuesta con elección de una preferencia principal: Maestría, Especialidad o Doctorado. Depende de interés en posgrado. Esta cardinalidad aún no fue aprobada. |
| Titulados: financiación futura | HU3, p. 4 | Pregunta propuesta sobre fuente principal: recursos propios, becas estatales, créditos educativos o patrocinio empresarial. Selección única, pendiente de aprobación; se distingue del financiamiento de estudios ya cursados. |
| Titulados: experiencia y emprendimiento | pp. 9–10 | Años completos de experiencia opcionales; primer empleo, cantidad de empleos y las cinco preguntas de emprendimiento con las categorías del anexo. |
| Titulados: formación y mejora | pp. 11–12 | Escalas de acuerdo de cuatro categorías, aspectos útiles y mejorables, cinco asignaturas que brindaron una ventaja competitiva y texto para otra asignatura. |
| Titulados: red y contacto | p. 13 | Interés sí/no y correo, teléfono y LinkedIn opcionales. |
| Empleadores: organización | pp. 14–15 | Correo, nombre, tipo, tamaño y sector requeridos, según los asteriscos del anexo. |
| Empleadores: contratación y demanda | p. 16 | Contratación reciente, contratación proyectada y nivel de formación demandado (Licenciatura, Diplomado, Especialidad, Maestría, Doctorado, Post-doctorado). |
| Empleadores: convocatoria y cargos | pp. 16–17 | Medio de convocatoria con seis categorías y cargos desempeñados con selección múltiple; corresponde a las preguntas 8 y 9 del anexo. |
| Empleadores: áreas, herramientas y competencias | pp. 17–18 | Preguntas abiertas originales 10, 11 y 12; requeridas según los asteriscos del documento. |
| Empleadores: perfil profesional | Primera aparición, pp. 18–19 | Diez criterios originales una sola vez, en el mismo orden. Se omite la segunda lista de pp. 19–20 por decisión del usuario. |

## Escalas y códigos

Las respuestas almacenan códigos ASCII estables y las opciones presentan etiquetas en español. No se convierten automáticamente a puntajes numéricos. La versión del catálogo acompaña cada respuesta para distinguirla de cambios posteriores del instrumento.

| Bloque | Códigos / categorías |
| --- | --- |
| Acuerdo de titulados, pp. 8 y 11 | `TOTALMENTE_DESACUERDO`, `DESACUERDO`, `ACUERDO`, `TOTALMENTE_ACUERDO` |
| Satisfacción del emprendimiento, p. 10 | `INSATISFECHO`, `ALGO_SATISFECHO`, `SATISFECHO` |
| Importancia de la formación para emprender, p. 10 | `NO_IMPORTANTE`, `POCO_IMPORTANTE`, `IMPORTANTE`, `MUY_IMPORTANTE` |
| Criterios de empleadores, pp. 18–19 | `TOTALMENTE_ACUERDO`, `PARCIALMENTE_ACUERDO`, `TOTALMENTE_DESACUERDO`, `NO_SABE` |

`NO_SABE` es una categoría nominal válida: no significa cero, desacuerdo ni respuesta vacía. Se cuenta como una categoría propia en frecuencias y se excluye de cualquier futura puntuación ordinal. Ninguna de estas escalas se reemplaza por una escala 1–5; el anexo y la sugerencia de HU4 difieren y el anexo se conserva en esta versión. Los diez criterios tienen identificadores `criterio_01` a `criterio_10` y cada uno aporta una sola respuesta a sus frecuencias.

## Obligatoriedad y saltos propuestos

Los campos obligatorios están señalados en el formulario. Un campo condicional solo se exige cuando su pregunta de control tiene el valor indicado; las respuestas de preguntas ocultas no deben guardarse. Los saltos son propuestas de usabilidad, ya que el PDF muestra los enunciados y las opciones sin definir toda la lógica del formulario.

- `formacion_complementaria=SI` muestra nivel, institución, financiamiento y nombre del programa cursado, requeridos en esa rama.
- `interes_posgrado=SI` muestra área, institución, nivel y financiamiento futuros, requeridos en esa rama. La opinión general de posgrado FCyT permanece visible.
- `estado_laboral=EMPRENDIMIENTO` muestra las cinco preguntas de negocio, requeridas en esa rama.
- `estado_laboral=EMPLEADO` muestra la pregunta de primer empleo actual.
- Para titulados se proponen como obligatorias las preguntas de control, año de egreso, ocupación, sector, valoraciones de formación y red de contactos. La exportación del anexo marca «Obligatorio» sin establecer de manera inequívoca la obligatoriedad individual de todas estas preguntas: esta propuesta debe revisarse.
- Años de experiencia, tiempo al primer empleo y número de empleos son opcionales; se evita exigir una categoría laboral inexistente a personas sin experiencia. Los intervalos de tiempo al primer empleo se conservan, aunque sus límites se solapan en 4, 8 y 12 meses: necesitan una definición metodológica antes de recolección definitiva.
- El servidor verifica la coherencia del sector actual: `DESEMPLEADO` exige `NO_APLICA`; quienes trabajan o emprenden deben seleccionar un sector de actividad.
- Aspectos útiles, aspectos a mejorar y asignaturas de ventaja son opcionales. Si se responde la pregunta de asignaturas de ventaja se exigen exactamente cinco, conforme al enunciado y la indicación de selección del PDF. El texto de otra asignatura es complementario y no se cuenta entre esas cinco selecciones.
- En empleadores se conserva la obligatoriedad de los campos con asterisco. Las diez valoraciones se proponen requeridas y admiten «No sabe», porque no todos los empleadores tendrán información para valorar cada criterio. La convocatoria es opcional, sin asterisco en el anexo. Los cargos admiten selección múltiple; el texto para especificar otro cargo es opcional. Se propone selección única del nivel académico demandado y del medio principal de convocatoria, ya que la extracción no establece su cardinalidad explícitamente; esta propuesta está pendiente de revisión.
- Correo, teléfono y LinkedIn de titulados permanecen opcionales aun cuando se indique interés en la red. No se deduce consentimiento para publicar esos datos ni para contactar a personas a partir del envío del formulario.

## Campos y decisiones pendientes

1. **Edad y género (p. 6):** la exportación muestra únicamente «35 años o más» y «Hombre», sin un catálogo completo. Se omiten en esta primera versión; no se inventaron rangos de edad ni categorías de género.
2. **Asignaturas de poca utilidad (pp. 12–13):** el enunciado solicita cinco y la instrucción dice seleccionar dos. Se omiten esta pregunta y su campo complementario hasta que se defina la cantidad. La pregunta de asignaturas de ventaja sí es consistente y está incluida.
3. **Tiempo desempleado (p. 9):** el campo no define unidad ni aceptación de fracciones. Se omite; no se infiere desempleo como cero cuando esté ausente. Los años de vida profesional se ofrecen de manera opcional como años completos, con rango técnico provisional 0–80; se debe definir cómo representar fracciones antes de usar esta variable en análisis final.
4. **Concordancia formación / requerimientos (p. 11):** el enunciado está truncado en el PDF. Se conserva ese texto y se informa en el campo; debe completarse antes de la encuesta final.
5. **Selección de nivel académico y convocatoria, empleadores (p. 16):** confirmar si las preguntas 7 y 8 exigen selección única o admiten varias opciones. La primera implementación usa la preferencia principal para ambas.
6. **Anonimato de empleadores:** la introducción del PDF promete anonimato, pero solicita correo y organización obligatorios. La pantalla explica que recoge datos identificables y que las respuestas se consultan en un panel protegido. Se necesita resolver la política final de identificación, uso y retención antes del trabajo de campo.
7. **Catálogos futuros de nivel y financiamiento, sector de titulados y obligatoriedad:** son propuestas de esta implementación, no decisiones aprobadas por el usuario. Ningún resultado deberá presentarse como validación institucional del instrumento.

Las preguntas omitidas por ambigüedad no se almacenan ni se muestran como respuestas vacías del participante. Antes de iniciar la toma de datos definitiva deben cerrarse estos pendientes, fijarse una versión aprobada y documentarse cualquier migración.
