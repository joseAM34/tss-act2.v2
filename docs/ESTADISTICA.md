# Estadística del conjunto preparado

Estado: incremento ejecutable de fase 4 verificado con H2 y PostgreSQL 18.6. Fecha: 2026-10-13. Protocolo: `PROTOCOLO_ESTADISTICO_V1`, estado metodológico `BORRADOR`. La evidencia técnica y los límites se mantienen en `ACEPTACION_ESTADISTICA.md`.

La autorización del usuario para continuar permite desarrollar el motor descrito por la [referencia de fase 4](04_ESTADISTICA_UNIVARIADA_BIVARIADA_CHI_CUADRADO.md). Los ejemplos de esa referencia son datos sintéticos de comprobación. Los acuerdos de cuestionarios y preparación permanecen en [DECISIONES.md](DECISIONES.md), [ACEPTACION_CUESTIONARIOS.md](ACEPTACION_CUESTIONARIOS.md) y [DATOS_Y_WRANGLING.md](DATOS_Y_WRANGLING.md). La evidencia técnica se separa del alcance metodológico en [ACEPTACION_ESTADISTICA.md](ACEPTACION_ESTADISTICA.md).

## Entradas y trazabilidad

Cada análisis consume exclusivamente un bundle preparado de fase 3, verificado por sus huellas. No consulta respuestas actuales para completar valores ni vuelve a aplicar correcciones posteriores. Conserva el ID del conjunto, su `logical_hash`, su huella completa, origen, modo, corte, plan, versión del protocolo, resultado congelado y administrador que solicitó el cálculo.

La unidad de las matrices es **un envío**. Un UUID distinto, correo diferente o clasificación `REAL` no demuestra que dos envíos representen personas/empresas independientes. La elegibilidad UMSS, las participaciones distintas y la unidad empleadora permanecen pendientes; no se deduplican por nombre o correo ni se inventa un diseño muestral.

Los resultados se estratifican por tipo de encuesta, versión del instrumento y estudio. No se combinan versiones por compartir el mismo ID de pregunta. La interpretación es descriptiva y exploratoria del conjunto seleccionado; el motor no extrapola a toda la población UMSS ni infiere causalidad.

El plan queda registrado antes del cálculo. Incluye lista de contrastes, `alpha` (0,05 por defecto), unidad de independencia, evidencia de independencia y declaración de ceros estructurales. Unidad y evidencia pueden omitirse juntas; no se acepta solo una. La documentación administrativa permite verificar el requisito operativo del protocolo, pero no sustituye revisión metodológica o aprobación institucional. Todos los análisis publicados permanecen en `BORRADOR`, incluso si proceden de un conjunto preparado en modo `OFICIAL`.

## Cobertura y denominadores

La preparación nueva incorpora cobertura anterior a la exclusión por calidad y revisiones temáticas, incluidas revisiones válidas con cero temas. Los bundles anteriores conservan sus bytes y huellas: cuando no registraron esa información, el análisis declara el denominador desconocido como `null`, sin reconstruirlo desde el catálogo actual.

| Base | Significado |
| --- | --- |
| `N_base` | Envíos incluidos del estrato. |
| `N_presente` | Envíos cuya definición congelada contiene la variable. |
| `N_aplicable` | Variable presente y condición de aplicación verificable y satisfecha. |
| `N_con_valor` | Respuesta con valor antes de exclusiones adicionales por calidad. |
| `N_validos` | Valores finales utilizables para la variable y el resultado concreto. |
| `N_no_sabe` / no puede valorar | Categorías nominales observadas; no son faltantes ni puntajes. |
| `N_sustantivos` | Valores válidos sin las categorías nominales de no valoración para ese resumen. |
| `N_pares_validos` | Intersección de envíos con ambas variables utilizables del cruce; no el mínimo de dos conteos independientes. |

Cada proporción conserva numerador, denominador y estado. Una base cero produce `null` y `SIN_DATOS`, no 0 %. Se distinguen `CON_VALOR`, `SIN_RESPUESTA`, `NO_APLICA`, `NO_RECOLECTADA`, `SIN_ESTADO_HISTORICO` y `VALOR_INVALIDO`; las incidencias de calidad no se suman como si fueran personas o nuevos estados exclusivos.

Los porcentajes ordinarios usan valores válidos de la variable. El indicador de ocupación observado cuenta `EMPLEADO` o `EMPRENDIMIENTO` sobre estados laborales válidos; no se denomina tasa oficial de empleo. El acuerdo entre respuestas sustantivas y el acuerdo entre todas las respuestas válidas son resultados distintos: el segundo incluye `NO_SABE` en su denominador y el primero lo excluye.

En preguntas múltiples se cuenta cada opción una vez por envío. Los porcentajes de participantes pueden sumar más de 100 %; los porcentajes de menciones usan el total de selecciones como otra base. Las columnas de opciones no ofrecidas por una versión mantienen `null`; no se tratan como una respuesta negativa de esa persona.

La codificación temática cuenta temas por envío y libro de códigos, sin copiar textos originales. Una revisión válida sin temas sigue siendo una revisión y aporta al denominador de textos revisados del libro. También se informa el total de textos válidos y el total sin revisión válida en ningún libro cuando esa metadata es conocida. Si hay textos sin libro de códigos se informa `PENDIENTE_CODIFICACION`; no se inventan temas. La codificación obsoleta por corrección del texto no aporta temas vigentes. Los conjuntos antiguos sin inventario de revisiones de cero temas no permiten afirmar que todo texto sin tema fue revisado y sus bases temáticas faltantes permanecen `null`/`METADATA_DESCONOCIDA`.

## Plan univariado

El tratamiento se decide con el diccionario congelado de la versión. Los contactos T32–T34/E00, el nombre de organización E01 y los textos abiertos originales no se publican en tablas analíticas ni se convierten en categorías de ranking.

| Plan | Variables y tratamiento del borrador |
| --- | --- |
| U01 | Edad, género y año de egreso: descriptivos según tipo. Antigüedad derivada pendiente de referencia aprobada; no se deriva del año técnico del estudio. |
| U02 | Estado laboral y sector: frecuencias e indicador observado de ocupación; sin inventar grupos junior/consolidado. |
| U03 | Formación complementaria cursada T03–T06; nivel y financiamiento pasado conservados. |
| U04 | Interés y formación futura T08–T10/T37–T38; nivel y financiamiento futuro separados del pasado. |
| U05 | Emprendimiento T15–T19: frecuencias de quienes tengan preguntas aplicables. |
| U06 | Trayectoria T12/T13/T20–T22/T41; tiempos actuales en meses, categorías laborales conservadas. |
| U07 | Satisfacción, acuerdo, concordancia y competencias T11/T23/T24/T39/T40: distribuciones ordinales y categorías sin valoración separadas. |
| U08 | Asignaturas, cargos, tecnologías y medios T25/T26/T27/T29: participantes y menciones para selecciones múltiples. |
| U09 | Red profesional T31: distribución y escala declarada. |
| U10 | Organización E02–E04: categorías; sin nombre organizacional ni unidad independiente supuesta. |
| U11 | Contratación y selección E05–E09: distribuciones según tipo. `MAS_TRES` permanece categoría, no número cuatro. |
| U12 | E13–E22: los diez criterios únicos, distribuciones y `NO_SABE` propios. |
| U13 | Temas manuales de T07/T28/T30/E09_OTRO/E10–E12: libro de códigos, revisiones y denominadores, sin texto original. |
| U14 | Cobertura y calidad por variable: bases conocidas/desconocidas, faltantes, no aplicación e incidencias. |

Las variables nominales conservan frecuencias absolutas, relativas y porcentuales, sus bases y todas las modas empatadas. Las categorías del catálogo se muestran aunque tengan cero observaciones; con base cero las frecuencias relativas y porcentajes son `null` y no hay una moda observada. Si una columna de estados de una matriz carece de definición verificable en ese estrato, se conserva su cobertura y `PENDIENTE_DEFINICION`, sin crear frecuencias interpretadas o puntajes desde el catálogo actual.

Para variables numéricas utilizables se calculan n, media, mediana, mínimo, máximo, varianza muestral, desviación estándar muestral, Q1, Q3 e IQR. La dispersión usa divisor n−1. Con n=1 se mantienen las medidas de localización y la dispersión es `null` con `MUESTRA_MENOR_2`; una muestra constante con n≥2 tiene dispersión cero. Los cuantiles usan interpolación tipo 7. Los resultados no finitos no se serializan como NaN/Infinity.

Las distribuciones ordinales son el resultado principal. Las categorías centrales se expresan con sus etiquetas; no se inventa una categoría intermedia entre dos respuestas. El orden académico del nivel de interés y el temporal del primer empleo se declaran sin asignarles puntajes; la posibilidad de contratación permanece nominal porque sus alternativas no forman un orden inequívoco. Un resumen numérico ordinal es complementario y declara un mapa de puntajes de borrador y el n puntuado; no demuestra distancias iguales entre categorías.

| Escala | Mapa de puntajes de borrador, excluyendo no valoración |
| --- | --- |
| Acuerdo de cuatro grados | Totalmente en desacuerdo=1; Desacuerdo=2; Acuerdo=3; Totalmente de acuerdo=4. |
| Satisfacción de tres grados | Insatisfecho=1; Algo satisfecho=2; Satisfecho=3. |
| Importancia de cuatro grados | No importante=1; Poco importante=2; Importante=3; Muy importante=4. |
| Diez criterios de empleadores | Total desacuerdo=1; Parcial acuerdo=2; Total acuerdo=3; `NO_SABE` sin puntaje. |
| Competencias de adecuación | `ADECUACION_1`…`ADECUACION_5`=1…5; `NO_PUEDE_VALORAR` sin puntaje. |

El mapa se aplica únicamente a la variable/versión con definición verificable y compatible. No se promedian escalas heterogéneas en un índice global ni se calcula una brecha de competencias no definida.

Un código válido de una versión futura puede carecer de regla de orden o puntaje en V1. Se conserva en frecuencias y en `n_unordered`; el resumen ordinal declara `PENDIENTE_REGLA`/`CATEGORIAS_SIN_REGLA_DE_ORDEN` y no publica categorías centrales de una muestra ordenada parcial. `n_ordered` y `ordering_complete` declaran esa cobertura. Si existen puntajes reconocidos, `score_summary` se limita a ese subconjunto con `n_scored` explícito. No se añade automáticamente un nuevo código al orden o mapa; `institutional_approval=false` conserva el alcance de borrador.

## Plan bivariado y familias

| Plan | Cruce | Inferencia registrada |
| --- | --- | --- |
| B01 | `financiamiento_futuro` × `nivel_interes` (T38/T37) | Familia individual M=1 si se selecciona. |
| B02 | `financiamiento_cursado` × `nivel_cursado` (T06/T04) | Familia individual M=1 si se selecciona. |
| B03 | `estado_laboral` × `interes_posgrado` (T14/T08) | Familia individual M=1 si se selecciona. |
| B04 | `tipo_organizacion` × `probabilidad_contratacion` (E02/E06) | Familia individual M=1 si se selecciona. |
| B05 | `tamano_organizacion` × `probabilidad_contratacion` (E03/E06) | Familia individual M=1 si se selecciona. |
| B06_01…B06_10 | `tipo_organizacion` × cada `criterio_01`…`criterio_10` | Familia completa de diez por estrato, M=10. `NO_SABE` se informa y se excluye del contraste de acuerdo. |
| B07 | Antigüedad derivada × estado/sector | `PENDIENTE_DEFINICION`: no hay referencia/metodología aprobada para derivar antigüedad. |
| B08 | `meses_vida_profesional` × `meses_desempleado` | Pares numéricos con ambas duraciones válidas y sus resúmenes. Añade resúmenes de cada duración por estado laboral/sector con su n propio; no se inventa correlación ni prueba de asociación. |

La selección de cualquier B06 obliga a registrar los diez criterios como familia completa. Los no ejecutables permanecen en M, con p y decisión `null`. B07/B08 no se añaden a una familia de p valores. Las familias individuales B01–B05 y la familia B06 no ofrecen control conjunto del error de todas las pruebas de la aplicación: la salida declara `global_familywise_control=false`. La lista y el carácter exploratorio del plan quedan congelados; el alcance confirmatorio institucional sigue pendiente D23.

Una tabla categórica conserva conteos enteros, totales, porcentajes por fila, columna y total, N de pares y exclusiones. Cada envío elegible aporta a una celda del cruce, no una fila independiente por selección. No se ingresan pesos o porcentajes como conteos de Pearson.

## Aplicabilidad de chi-cuadrado

El protocolo utiliza Pearson de independencia, sin corrección de continuidad. Antes de inferir exige definición compatible, pares válidos, documentación administrativa de independencia, ausencia de ceros estructurales declarados, al menos dos filas y dos columnas activas y **todas las esperadas activas ≥5**. Es una política conservadora del borrador, no una regla universal atribuida al PDF.

Se eliminan márgenes de total cero solo de la tabla activa para inferencia; la tabla observada completa y las categorías retiradas se conservan. Las esperadas son producto de márgenes/N; gl=(r−1)(c−1). Se utiliza `commons-statistics-inference` 1.3, fijada en Maven, para Pearson y su p de cola superior, sin obtenerla de p redondeadas. La dispersión y los cuantiles se calculan con las convenciones declaradas por `StatisticalMath`. V de Cramér se calcula sin corrección de sesgo únicamente cuando el contraste es aplicable.

| Estado | Resultado |
| --- | --- |
| `CALCULADO` | Pearson, gl, p, V y decisión disponibles, con diagnósticos. |
| `SIN_DATOS` | Sin pares válidos; porcentajes de base cero e inferencia `null`. |
| `SIN_VARIACION` | Menos de dos niveles activos en alguna dimensión; sin inferencia. |
| `PENDIENTE_DEFINICION` | Falta definición, equivalencia o referencia exigida por el análisis. |
| `INDEPENDENCIA_NO_DOCUMENTADA` | Falta unidad/evidencia; IDs distintos no satisfacen el requisito. |
| `FRECUENCIAS_INSUFICIENTES` | Alguna esperada activa <5; se conservan observadas, esperadas y diagnósticos. |
| `ESTRUCTURA_INCOMPATIBLE` | Se declararon ceros estructurales incompatibles con el Pearson ordinario. |
| `ERROR_CALCULO` | Resultado no finito o fallo de cálculo, sin publicar p válida. |

Un contraste no ejecutado tiene chi-cuadrado, gl, p, V y decisión `null`: no equivale a no rechazar H0. Las entradas malformadas, conteos negativos, alpha inválida o IDs de envío repetidos se rechazan antes de publicar un resultado; la etiqueta propuesta `ENTRADA_INCOMPATIBLE` de la referencia no se presenta como un resultado válido de esas excepciones. No se agrupan categorías después de mirar p ni se reemplaza automáticamente por Fisher. La interfaz conserva el motivo de no ejecución.

Holm ajusta p calculadas dentro de la familia planificada usando su M original, incluso con contrastes no ejecutables. Conserva orden original, p sin ajustar, p ajustada, M, número calculado y decisión ajustada. Se compara p completa con `alpha`; el redondeo visual no modifica esa decisión. Un p no representa probabilidad de H0, causalidad ni magnitud; no rechazar no prueba independencia. V no recibe etiquetas automáticas de asociación débil/fuerte.

## Persistencia, acceso y salidas

`AnalysisService` congela plan y resultado desde `WranglingService`; `StatisticalEngine` produce los resultados por estrato; `StatisticalMath` aplica las convenciones numéricas. Las plantillas presentan resultados del backend y no implementan un segundo motor.

La migración nueva V6, `V6__statistical_analysis.sql`, crea `statistical_analysis` con ID, FK al conjunto preparado, huella de fuente, versión del protocolo, instante UTC, actor, huella lógica, huella del bundle y JSON congelado. No modifica V1–V5 ni rellena análisis o cobertura en conjuntos históricos. Las reglas de preparación `2026-10-wrangling-2` agregan `coverage` y `text_reviews` a los nuevos bundles; la lectura mantiene el cálculo de huellas de las ejecuciones anteriores que carecen de esos productos.

El manifiesto conserva la definición operativa del protocolo y su propia huella: cuantiles, dispersión, método Pearson, política de esperadas, familias, estratificación y biblioteca Apache Commons Statistics Inference 1.3. La huella lógica identifica esa definición, la huella lógica del conjunto, el plan y los resultados; excluye el UUID, fecha y actor técnicos. La huella del bundle completo protege además el manifiesto publicado. Cada lectura/descarga verifica ambas; la alteración se rechaza, sin regenerar desde respuestas actuales. Repetir entrada y plan produce el mismo contenido lógico aunque cambie la identidad de ejecución.

| Operación protegida | Ruta |
| --- | --- |
| Listar análisis guardados | GET `/admin/estadisticas` |
| Registrar plan de un conjunto y generar análisis | GET/POST `/admin/datos/{runId}/analisis` |
| Consultar análisis congelado | GET `/admin/estadisticas/{id}` |
| Descargar bundle completo | GET `/admin/estadisticas/{id}/analisis.json` |
| Descargar producto CSV | GET `/admin/estadisticas/{id}/{producto}.csv` con uno de los nueve nombres siguientes. |

| CSV | Contenido |
| --- | --- |
| `frecuencias` | Categorías, conteos, bases, porcentajes y modas. |
| `numericos` | Medidas de variables numéricas, unidades y convenciones. |
| `ordinales` | Orden, categorías centrales y resumen de puntajes explícitos. |
| `multiples` | Opciones por envío y menciones, con ambas bases. |
| `textos` | Temas revisados por variable/libro de códigos y base conocida o desconocida. |
| `indicadores` | Numeradores, denominadores, proporciones y estado. |
| `cobertura` | Bases, estados y limitaciones de metadata por variable. |
| `cruces` | Tablas, diagnósticos, estado, p/V y decisión cuando aplica. |
| `familias` | M planificado, número calculado, p ajustadas y decisiones Holm. |

El JSON conserva `manifest`, `plan` y `result`; este último contiene `strata`, `contrasts`, `families` y limitaciones. Los CSV agregan tipo/versión/estudio a las filas por estrato; listas y objetos anidados se serializan como JSON en celdas, sin recalcular valores. Un CSV sin filas puede contener solo BOM, por lo que el JSON y el protocolo son el contrato de referencia de esa salida vacía.

Todas las operaciones requieren ADMIN en el servidor; crear análisis requiere CSRF. Las descargas no publican contactos ni textos originales. Los resultados incluyen IDs de trazabilidad protegidos; no se presentan como datos garantizados anónimos. Las exportaciones CSV conservan protección contra fórmulas y el JSON mantiene los números sin redondeo analítico.

El flujo del panel es **Preparar datos → abrir conjunto → Analizar este conjunto**. El formulario permite declarar unidad/evidencia y alpha antes de crear el análisis; con campos de independencia vacíos se generan descriptivos y cruces diagnosticados sin p inferenciales. El listado de análisis permite abrir y descargar una ejecución ya congelada.

## Conexión con visualización de fase 5

El usuario autorizó implementar la interfaz después de revisar su referencia («vamos»). El dashboard se abre desde **Ver gráficos** en un análisis guardado: GET `/admin/estadisticas/{id}/visualizacion`; su contrato protegido usa GET `/admin/estadisticas/{id}/visualizacion.json`. La descripción efectiva y la evidencia técnica están en [VISUALIZACION.md](VISUALIZACION.md) y [ACEPTACION_VISUALIZACION.md](ACEPTACION_VISUALIZACION.md). La implementación y verificación de esta ampliación se registran aparte de las 239 pruebas históricas de fase 4.

`DashboardDataService` comprueba el análisis y el bundle preparado frente a las huellas completas y lógicas de su fuente. Sin selección conserva el resultado guardado. Los filtros aceptados usan estudio, encuesta, versión, fechas o variables de catálogo explícitamente permitidas y estados `CON_VALOR`; no admiten contactos ni columnas libres. Para una selección nueva recorta matrices, cobertura, revisiones, temas, selecciones e incidencias por los miembros conservados y calcula con el `AnalysisPlan` registrado. No consulta respuestas actuales ni cambia el análisis guardado.

Los conjuntos con `2026-10-wrangling-3` agregan `submitted_at` a miembros para filtrar recepción. Las fechas se interpretan como días inclusivos en `America/La_Paz`; un histórico sin esa metadata requiere un conjunto nuevo para ese filtro y conserva su consulta ordinaria. Contexto, selección y resultados tienen identificadores/huellas explícitos; versiones y estudios siguen estratificados, con carácter `BORRADOR`. Comparar hasta dos grupos no aprueba su comparabilidad institucional ni elimina la multiplicidad exploratoria.

`DashboardProjection` adapta los resultados de Java a siete vistas y tablas equivalentes. Las barras múltiples mantienen sus porcentajes de participantes; los mapas de calor conservan observadas, esperadas y modos porcentuales. El radar R01/R02 usa los diez criterios una vez por panel y el acuerdo sobre `N_sustantivos`; muestra `NO_SABE` y bases por eje, conserva cero con base positiva y no completa una serie con ejes ausentes. Las competencias T39/T40 ya están aprobadas para el borrador y mantienen adecuación de cinco grados con `NO_PUEDE_VALORAR` nominal; no se define automáticamente un índice, brecha ni radar de competencias.

La dispersión recibe pares autorizados de B08 y proyecta coordenadas/unidades sin IDs, nombres, contactos o textos originales. El navegador usa Apache ECharts 6.0.0 como recurso local para representar datos y coordinar interacción; no recalcula porcentajes, esperadas ni decisiones sobre H0. Las tablas y avisos siguen disponibles con los estados y bases del resultado. Los avisos/licencia del recurso de terceros no asignan licencia al proyecto.

## Pendientes metodológicos y siguientes fases

D13/D16 conservan elegibilidad, participaciones y unidad empleadora; D18 el libro de códigos temático aprobado; D19 la comparabilidad de versiones; D21 el diseño muestral y alcance poblacional; D22 cohortes/segmentación; D23 el plan institucional de contrastes y familias. Las convenciones técnicas de V1 están declaradas para un borrador y cambiar su sentido requiere otra versión del protocolo.

Esta etapa prepara resultados de backend y su consulta/exportación; la implementación visual de fase 5 consume ese contrato y conserva sus pendientes metodológicos. Reportes automáticos/Excel de la fase siguiente y simulación continúan pendientes. Sus salidas deberán consumir un análisis, conjunto y selección identificados, con denominadores y estados, sin recalcular cifras independientes en la interfaz.
