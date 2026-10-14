# Instrumento de recolección: borrador de fase 2

Versión del catálogo: `2026-10-borrador-2`. Fecha: 2026-10-13. Referencia de requisitos incorporada: [02_REQUISITOS_CUESTIONARIOS.md](02_REQUISITOS_CUESTIONARIOS.md). Correspondencia completa T01-T41/E00-E22/E09_OTRO, matriz de obligatoriedad y criterios CA01-CA20: [ACEPTACION_CUESTIONARIOS.md](ACEPTACION_CUESTIONARIOS.md).

Esta versión permite revisar y probar las encuestas. No representa un cuestionario final aprobado institucionalmente para recolectar datos del estudio. La eliminación de la repetición de criterios y el acceso público con panel protegido fueron indicados por el usuario. Las adaptaciones de edad, género, tiempo en meses, asignaturas de poca utilidad, filtro de experiencia y competencias fueron aprobadas para el borrador de fase 2; las demás propuestas se identifican como tales en la matriz.

Fuente: «Guía Metodológica y Técnica Integral — Análisis de Mercado y Herramienta de Recolección de Datos», entregada en el PDF de José Armando Mareño Mamani. Las páginas citadas corresponden al PDF. El catálogo implementado se encuentra en `src/main/java/bo/edu/umss/mercado/survey/SurveyCatalog.java`.

## Trazabilidad

| Encuesta / bloque | Fuente | Implementación |
| --- | --- | --- |
| Titulados: edad y género | p. 6 | Adaptación aprobada para borrador: edad en años enteros y género Hombre/Mujer/Otro/Prefiero no responder. Ambos opcionales; el rango 0-120 es técnico provisional. La copia original solo muestra «35 años o más» y «Hombre». |
| Titulados: año de egreso, ocupación, sector | HU1, p. 3; empleo, p. 9 | Año numérico obligatorio entre 1960 y el año actual de `America/La_Paz`; situación laboral y sector obligatorios. El límite inferior es una validación provisional. Se reutiliza el catálogo de sectores del anexo de empleadores, p. 15, y se agrega `NO_APLICA` para quienes actualmente no trabajan. |
| Titulados: formación complementaria realizada | pp. 7–8 | Indicador sí/no, nivel, institución, financiamiento y nombre del programa. Los cuatro últimos dependen de una respuesta Sí. |
| Titulados: interés futuro y opinión de posgrado FCyT | p. 8 | Área e institución del anexo; valoración de FCyT con su escala original de cuatro categorías. |
| Titulados: nivel de posgrado futuro | HU2, pp. 3–4 | Pregunta propuesta con elección de una preferencia principal: Maestría, Especialidad o Doctorado. Depende de interés en posgrado. Esta cardinalidad aún no fue aprobada. |
| Titulados: financiación futura | HU3, p. 4 | Pregunta propuesta sobre fuente principal: recursos propios, becas estatales, créditos educativos o patrocinio empresarial. Selección única, pendiente de aprobación; se distingue del financiamiento de estudios ya cursados. |
| Titulados: experiencia y emprendimiento | pp. 9–10 | Vida profesional y desempleo en meses enteros opcionales, aprobados para borrador. Filtro de experiencia relacionada/otras áreas/sin experiencia; historial según experiencia, primer empleo según situación actual y las cinco preguntas de emprendimiento con categorías originales. |
| Titulados: formación y mejora | pp. 11–13 | Escalas originales de acuerdo, aspectos útiles y mejorables, cinco asignaturas de ventaja si se responde y hasta dos de poca utilidad; textos de otra asignatura complementarios sin sumarlos al conteo. |
| Titulados: competencias técnicas y transversales | HU4, p. 4; adaptación aprobada para borrador | Seis ítems técnicos y seis transversales, solo para experiencia relacionada. Adecuación de formación al trabajo actual o previo; cinco grados y «No puedo valorar» nominal. No es una matriz original del anexo ni un cálculo de brecha. |
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
| Matrices de adecuación de competencias, adaptación de HU4 | `ADECUACION_1` (Nada adecuada), `ADECUACION_2` (Poco adecuada), `ADECUACION_3` (Moderadamente adecuada), `ADECUACION_4` (Adecuada), `ADECUACION_5` (Muy adecuada), `NO_PUEDE_VALORAR` (No puedo valorar) |

`NO_SABE` es una categoría nominal válida: no significa cero, desacuerdo ni respuesta vacía. Se trata como categoría propia y se excluye de cualquier futura puntuación ordinal. Las escalas originales no se reemplazan por una escala 1–5: los nuevos grados de adecuación se aplican exclusivamente a T39/T40, con referente diferente. `NO_PUEDE_VALORAR` también es nominal y carece de puntaje. Los diez criterios tienen identificadores `criterio_01` a `criterio_10` y cada uno aporta una sola respuesta a sus futuras frecuencias.

## Obligatoriedad y saltos propuestos

Los campos obligatorios están señalados en el formulario. Un campo condicional solo se exige cuando su pregunta de control tiene el valor indicado; las respuestas de preguntas ocultas no deben guardarse. Los saltos son propuestas de usabilidad, ya que el PDF muestra los enunciados y las opciones sin definir toda la lógica del formulario.

- `formacion_complementaria=SI` muestra nivel, institución, financiamiento y nombre del programa cursado, requeridos en esa rama.
- `interes_posgrado=SI` muestra área, institución, nivel y financiamiento futuros, requeridos en esa rama. La opinión general de posgrado FCyT permanece visible.
- `estado_laboral=EMPRENDIMIENTO` muestra las cinco preguntas de negocio, requeridas en esa rama.
- `estado_laboral=EMPLEADO` muestra la pregunta de primer empleo actual.
- Para titulados se proponen como obligatorias las preguntas de control, año de egreso, ocupación, sector, valoraciones de formación y red de contactos. La exportación del anexo marca «Obligatorio» sin establecer de manera inequívoca la obligatoriedad individual de todas estas preguntas: esta propuesta debe revisarse.
- Vida profesional y desempleo se registran opcionalmente como meses enteros entre 0 y 960; ese tope es técnico provisional. Si se proporciona desempleo, se exige el tiempo de vida profesional y aquel no puede superarlo. No se calculan estos tiempos a partir del año de egreso.
- El filtro obligatorio `experiencia_laboral` distingue `RELACIONADA`, `OTRAS_AREAS` y `SIN_EXPERIENCIA`. Tiempo al primer empleo relacionado solo aplica a `RELACIONADA`; número de empleos a las dos clases de experiencia. Son opcionales y no se fuerza una cantidad mayor que cero a quien nunca trabajó. `SIN_EXPERIENCIA` es incompatible con empleo o emprendimiento actual. Los intervalos del primer empleo se conservan, aunque sus límites se solapan en 4, 8 y 12 meses: necesitan una definición metodológica antes de recolección definitiva.
- Las doce competencias son obligatorias cuando el filtro indica `RELACIONADA` y permiten «No puedo valorar». El referente es la adecuación de la formación a demandas de trabajo actual o previo relacionado con Sistemas. La concordancia T24 también se aplica a esta rama, con redacción de trabajo propuesta descrita abajo.
- El servidor verifica la coherencia del sector actual: `DESEMPLEADO` exige `NO_APLICA`; quienes trabajan o emprenden deben seleccionar un sector de actividad.
- Aspectos útiles, aspectos a mejorar y asignaturas son opcionales. Si se responde la pregunta de asignaturas de ventaja se exigen exactamente cinco, conforme al enunciado y la indicación del PDF. Asignaturas de poca utilidad admite hasta dos, según la decisión del usuario para el borrador. Los textos de otra asignatura son complementarios y no cuentan entre las selecciones.
- En empleadores se conserva la obligatoriedad de los campos con asterisco. Las diez valoraciones se proponen requeridas y admiten «No sabe», porque no todos los empleadores tendrán información para valorar cada criterio. La convocatoria es opcional, sin asterisco en el anexo. Los cargos admiten selección múltiple; el texto para especificar otro cargo es opcional y solo aplica cuando la selección incluye `OTRO`. El backend descarta ese texto cuando no aplica. La obligatoriedad definitiva del texto complementario sigue pendiente. Se propone selección única del nivel académico demandado y del medio principal de convocatoria, ya que la extracción no establece su cardinalidad explícitamente; esta propuesta está pendiente de revisión.
- Correo, teléfono y LinkedIn de titulados permanecen opcionales aun cuando se indique interés en la red. No se deduce consentimiento para publicar esos datos ni para contactar a personas a partir del envío del formulario.

## Campos y decisiones pendientes

1. **Referencia temporal y rangos:** meses enteros para vida profesional y desempleo ya está resuelto para borrador. Sigue pendiente reconciliar egreso, titulación, inicio de vida profesional y año fijo del estudio. Los límites técnicos de 1960 para egreso, 0-120 para edad y 0-960 meses no se atribuyen a la fuente.
2. **Concordancia formación / requerimientos T24 (p. 11):** el original termina en «y los requerimientos». La adaptación de trabajo completa «y los requerimientos de su trabajo actual o anterior relacionado con Sistemas» y solo aplica a experiencia relacionada. Esa redacción y condición se documentan como propuestas del borrador, pendientes de revisión metodológica.
3. **Selección de nivel académico y convocatoria, empleadores (p. 16):** confirmar si las preguntas 7 y 8 exigen selección única o admiten varias opciones. La implementación usa la preferencia principal para ambas.
4. **Anonimato de empleadores:** la introducción del PDF promete anonimato, pero solicita correo y organización obligatorios. La pantalla explica que recoge datos identificables y que las respuestas se consultan en un panel protegido. Se necesita resolver la política final de identificación, uso y retención antes del trabajo de campo.
5. **Catálogos futuros de nivel y financiamiento, sector de titulados y obligatoriedad:** son propuestas de esta implementación, no decisiones metodológicas finales del usuario. Las matrices de competencias aprobadas para borrador no representan un método de cálculo de brechas.
6. **Elegibilidad, varias participaciones, autoempleo y emprendimiento paralelo:** el acceso público y el filtro de experiencia no resuelven automáticamente todas estas políticas. Los límites temporales de categorías de T21 y los nombres abreviados «Introducción» y «T.I.S.» conservan su revisión pendiente.

Edad/género, la cantidad de asignaturas de poca utilidad y la unidad/precisión de desempleo dejaron de estar omitidos en `2026-10-borrador-2`, según las respuestas del usuario. La matriz enumera cada adaptación para distinguirla de la fuente original.

Los campos que una versión anterior omitió por ambigüedad no se reconstruyen como respuestas vacías del participante. Antes de iniciar la toma de datos definitiva deben cerrarse los pendientes restantes, fijarse una versión aprobada y documentarse la comparabilidad entre instrumentos.

## Estados, conservación histórica y comprobante

La versión `2026-10-borrador-2` conserva los campos ya implementados y registra un estado por pregunta: `CON_VALOR`, `SIN_RESPUESTA` o `NO_APLICA`. `NO_SABE` es un valor explícito con estado `CON_VALOR`. Una pregunta aplicable opcional vacía se registra como `SIN_RESPUESTA`; un bloque excluido por las condiciones operativas del borrador se registra como `NO_APLICA`, sin guardar los valores enviados para ese bloque. Estos estados no añaden preguntas no habilitadas por ambigüedad.

La opción del catálogo `sector=NO_APLICA` permanece como valor observado, diferente del estado técnico de una pregunta oculta. No se convierten omisiones en cero ni se recalculan estados usando versiones futuras del catálogo.

Las respuestas de `2026-10-borrador-1` conservan sus valores, textos y versión, incluido el identificador histórico `anios_vida_profesional`. No se convierten automáticamente esos años observados en los nuevos campos de meses. Cuando no tienen un estado registrado, el detalle administrativo indica esa ausencia de metadatos; no se infiere si el participante omitió la pregunta o siguió un salto. El CSV de valores originales conserva su formato y no contiene una columna de estados ni filas inventadas para faltantes.

Tras guardar un envío válido, `/gracias` muestra su referencia UUID sin datos de respuestas. La referencia pertenece a la sesión que envió la encuesta; no se consulta una respuesta por un identificador público. Reintentar el mismo formulario no crea otro envío y devuelve la misma referencia. La política de varias participaciones diferentes de una persona o empresa continúa pendiente D13.

## Contrato de datos de fase 3

La preparación de datos se documenta en [DATOS_Y_WRANGLING.md](DATOS_Y_WRANGLING.md) y sus comprobaciones en [ACEPTACION_DATOS.md](ACEPTACION_DATOS.md), a partir de la referencia [03_DATOS_Y_DATA_WRANGLING.md](03_DATOS_Y_DATA_WRANGLING.md).

Cada envío nuevo conserva estudio y definición congelada de su versión con huellas de contenido y captura lógica aceptada. El instrumento actual permanece en `BORRADOR`: la clasificación de una respuesta como `REAL` no aprueba automáticamente el cuestionario. Los envíos parten de origen `NO_DECLARADO`; un administrador puede revisarlos y clasificarlos como reales o sintéticos con motivo e historial.

Reintentar la misma clave con el mismo contenido aceptado conserva su referencia. Cambiar ese contenido reutilizando la clave produce conflicto 409, sin reemplazar el original. Para corregir un envío existente se utiliza una revisión administrativa trazable, conservando captura y valores originales.

Los históricos se preparan únicamente con la información registrada. Una reconstrucción limitada no equivale a una definición congelada ni a una captura íntegra anterior. No se trasladan unidades, catálogos, estados, origen ni estudio desde la versión actual por defecto. Las matrices ordinarias omiten identificadores directos y texto abierto original, mantienen los estados y conservan trazabilidad bajo permisos.
