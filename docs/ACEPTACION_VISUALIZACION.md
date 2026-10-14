# Aceptación de visualización de fase 5

Fecha de actualización: 2026-10-14. Estado: incremento implementado con 288 pruebas Java correctas en H2 y 288 correctas en la ejecución con PostgreSQL, nueve pruebas JavaScript y comprobaciones en Chrome de siete vistas a 360, 768 y 1280 píxeles. La evidencia técnica no constituye aprobación metodológica/institucional ni conformidad WCAG completa.

La [referencia de fase 5](05_VISUALIZACION_COMPUTACIONAL.md) conserva CVE01–CVE25 y vectores sintéticos. [VISUALIZACION.md](VISUALIZACION.md) documenta el contrato implementado, su separación de datos/presentación y los pendientes. Ninguna fixture se presenta como resultado de campo UMSS.

## Matriz CVE01–CVE25

La columna de evidencia distingue comprobaciones automáticas ejecutadas y límites de revisión manual. La revisión del código no sustituye una prueba con navegador, teclado o lector de pantalla.

| ID | Criterio | Evidencia y límites | Estado |
| --- | --- | --- | --- |
| CVE01 | Vista autorizada con estudio, selección y alcance. | `theDashboardPageAndJsonAreNonCacheableAndDoNotExposePrivateTextOrSubmissionIds` y `noFilterUsesTheFrozenAnalysisAndRepeatedSelectionsHaveIdenticalHashes`; ADMIN, no-store, siete vistas y contexto. Chrome abrió las siete vistas sin excepciones. | Verificado técnicamente. |
| CVE02 | Aplicar filtro actualiza resultados coherentes. | `anEmploymentFilterChangesTheBaseAndEveryStatisticUsesOnlyItsOwnSubset`: base, frecuencias, media y selección del subconjunto. Chrome comprueba controles, selección aplicada sin aviso falso de cambios pendientes y contexto de error; los filtros se calculan en Java. | Backend verificado; navegador comprobado con fixture sintética. |
| CVE03 | Respuesta anterior no sobrescribe selección vigente. | Prueba JavaScript del control de solicitudes y Chrome con dos consultas de formación consecutivas: la respuesta antigua retrasada no reemplaza la selección `training=NO`; `staleResponseGuard=true`. | Verificado automáticamente en JavaScript y Chrome. |
| CVE04 | Cambiar orden o serie visible mantiene bases/decisiones. | El orden de catálogo/semántico se conserva. Prueba JavaScript de modo/tabla y cambio real de modo en Chrome; la leyenda informa series ocultas y conserva la tabla. No se incorpora ordenación libre. | Modos verificados; ocultación de leyenda revisada en código. |
| CVE05 | Cero categórico sigue siendo cero. | Prueba JavaScript de tooltip/series/format: valor cero permanece 0 y null se presenta como ausencia. Los catálogos y las tablas conservan ceros. | Verificado automáticamente. |
| CVE06 | Sin datos y no recolectada conservan estados propios. | `anEmptyPeriodSelectionReportsNoDataInsteadOfZeroPercentages` y `historicalBundlesWithoutReceptionDatesRemainReadableAndCannotInferADateFilter`; sin bases inventadas. Estados por versión provienen del protocolo. | Bases y ausencias verificadas; sin reconstrucción histórica. |
| CVE07 | Ocupación 6/8=75 % con base 10. | `theEmploymentReferenceHasSixOfEightValidDeclarationsAndRetainsTenBaseRows`: fixture analítica explícita con dos estados sin respuesta; no altera obligatoriedad pública. | Valores, bases y tabla verificados. |
| CVE08 | Selección múltiple 75/50/50 sobre cuatro envíos. | `theExactMultipleSelectionReferenceKeepsParticipantAndMentionBasesInChartAndTable`: 3/2/2, cuatro participantes y siete menciones. | Valores, bases y tabla verificados. |
| CVE09 | No sabe tiene categoría y base propia. | `noSabeIsOutsideTheSubstantiveDistributionAndRadarButInsideTheCompleteTable`: E13 con 6/2/1/2, N sustantivo 9 y N válido 11. | Valores, bases y tabla verificados. |
| CVE10 | Matriz asimétrica no intercambia ejes. | `anAsymmetricHeatmapPreservesCategoryOrientationAndBackendModes`: [[18,12],[6,24]], filas 60/40 y 20/80. JavaScript verifica [[0,0,18],[1,0,12],[0,1,6],[1,1,24]]. | Matriz, tablas y mapeo verificados. |
| CVE11 | Mapa usa modo y denominador correspondiente. | Java conserva pares, porcentajes y tabla por modo; JavaScript no recalcula. Chrome cambia a porcentaje por fila y comprueba unidad, tabla y escala máxima 100. | Verificado técnicamente. |
| CVE12 | Cero y null tienen lecturas distintas. | `aZeroObservedCellAndUndefinedPercentageHaveDifferentValuesAndAnUnexecutedTestHasNoP` y prueba JavaScript de coordenadas: cero observado disponible y null separado. | Verificado automáticamente. |
| CVE13 | Esperadas insuficientes conservan diagnóstico, sin p. | Cuatro pares en tabla activa 2×2 producen `FRECUENCIAS_INSUFICIENTES`, p/decisión ausentes y mapa descriptivo disponible; prueba JavaScript conserva ese mapa. | Verificado automáticamente. |
| CVE14 | Ajuste identifica familia, p y decisión original. | Matriz asimétrica compara p original/ajustada y familia con análisis congelado; se conservan M y n ejecutado. Prueba JavaScript limita p y decisión a presentación. | Contrato y presentación automática verificados. |
| CVE15 | Radar tiene orden y escala 0–100. | R01/R02 con cinco ejes, mínimo 0/máximo 100 y tabla numerador/bases/NS. | Contrato y tablas verificados. |
| CVE16 | Eje faltante no completa serie artificialmente. | `anAxisWithOnlyNoSabeDisablesItsRadarPanelWithoutFillingZero`: sin serie y con tabla/motivo, segundo panel disponible. | Contrato y tablas verificados. |
| CVE17 | Acuerdo cero con base positiva es válido. | `zeroAgreementWithPositiveBasesIsACompleteValidRadarAndEveryCriterionAppearsOnce`: panel válido con cinco ceros y diez criterios únicos. | Contrato y tablas verificados. |
| CVE18 | Dos grupos tienen escalas/bases comparables. | `twoEmployerGroupsKeepTheirOwnRadarBasesAndAnIncompleteGroupDisablesComparison`: bases 1/2, valores 100/50 y grupo incompleto sin radar. Chrome comprueba opciones dinámicas del selector de grupos. | Backend y catálogo de controles verificados. |
| CVE19 | Caja conserva cinco números y n=1. | `boxplotUsesTheFiveNumberBackendSummaryAndASingleValueIsNotABox`: vector [2,4,4,4,5,5,7,9], cinco números [2,4,4.5,5.5,9] y valor único cero. | Contrato y tabla verificados. |
| CVE20 | Teclado y lector acceden a controles/descripciones. | Chrome comprueba navegación de pestañas por teclado, tablas y descripciones ARIA en los gráficos disponibles; revisión del foco y controles HTML. | Teclado/ARIA verificados; lector real pendiente. |
| CVE21 | Móvil/ampliación/redimensionado mantienen legibilidad. | Chrome abre siete vistas a 360/768/1280: sin desbordamiento horizontal de la página, gráficos inicializados y tablas disponibles. | Estructura adaptable verificada; zoom/texto ampliado y legibilidad manual pendientes. |
| CVE22 | Navegación no acumula instancias/listeners. | Navegación de siete vistas en tres anchos sin excepciones; gráficos visibles tienen instancias. Código usa dispose/unobserve/desconexión al sustituir gráficos. | Recreación y navegación verificadas; sin perfil prolongado de memoria/listeners. |
| CVE23 | Etiquetas HTML se presentan como texto seguro. | `dynamicHtmlLabelsAreJsonTextAndThePageDoesNotEmbedExecutableSourceMarkup` y prueba JavaScript de tooltip richText; DOM usa textContent. No se interpreta como HTML. | Contrato HTTP y opciones seguras verificados; fixture maliciosa no ejecutada en Chrome. |
| CVE24 | Demostración conserva identificación sintética. | `syntheticOriginCannotBeHiddenOrChangedByAnAnalyticFilter`: contexto SINTETICO/BORRADOR inalterable mediante filtros. Aviso persistente implementado y revisión del código. | Origen verificado; fixture de Chrome conserva NO_DECLARADO. |
| CVE25 | Gráfico/tabla/filtros conservan contexto de reporte. | Misma selección repite hashes/valores; `laterCorrectionsAndNewResponsesCannotChangeAnExistingVisualSelection` y verificaciones de integridad. | Contrato verificado; reporte fase siguiente. |

## Integridad y seguridad adicionales

`PhaseFiveVisualizationIntegrationTest` comprueba autenticación, rol ADMIN, exclusión de contactos/textos/UUID de encuestados, rechazo de filtros ajenos al catálogo, fechas inválidas, fuente/análisis alterados y estabilidad frente a correcciones/envíos posteriores. Se ejecutó junto con las suites previas, conservando comprobaciones de cuestionarios, persistencia, preparación y estadística.

La selección por período requiere metadata congelada de recepción en miembros nuevos. Se verificaron acceso ordinario sin período a un bundle histórico, rechazo explícito del filtro cuando falta metadata y conservación de sus hashes. `inclusivePeriodUsesBoliviaMidnightsRatherThanUtcCalendarDates` verifica ambos extremos inclusivos respecto de medianoche en Bolivia. Un año de egreso válido dentro del rango congelado pero sin observaciones produce una selección vacía, sin rechazarla como categoría desconocida.

La interfaz incluye tablas para lectura independiente del gráfico y guarda una selección aplicada distinta de la edición pendiente. No se afirma anonimización de pares, representatividad poblacional, causalidad, conformidad WCAG completa ni aprobación institucional a partir de pruebas técnicas.

## Registro de verificación

La primera ejecución dirigida pasó 48 casos de fase 5 en H2. La ejecución completa posterior pasó 288 pruebas Java, sin fallos, errores u omisiones: los 239 casos anteriores y 49 de fase 5 (37 de `PhaseFiveVisualizationIntegrationTest`, seis de `DashboardDataServiceTest` y seis de `DashboardSelectionTest`). Incluye el año de egreso sin observaciones, las bases propias de resúmenes B08 y el diagnóstico visual de frecuencias insuficientes.

| Comprobación | Evidencia local excluida del historial | Resultado registrado |
| --- | --- | --- |
| Maven verify completo con H2 | `.codex-tmp/verify-phase5-h2-final.log` | 288 correctas, BUILD SUCCESS, 2026-10-14 04:41:20 America/La_Paz. |
| Maven verify en ejecución con PostgreSQL | `.codex-tmp/verify-phase5-postgres-final.log` | 288 correctas, BUILD SUCCESS, 2026-10-14 04:44:58 America/La_Paz. |
| Pruebas JavaScript `src/test/js/dashboard.test.js` | `.codex-tmp/phase5-js-final.log` | Nueve correctas, cero fallos/omisiones; incluye formato de p y último ajuste de radar. |
| Chrome con fixture sintética de MockMvc | `.codex-tmp/phase5-browser/report.json` y `browser.log` | Siete vistas × tres anchos, modos, teclado, opciones de comparación, consultas fuera de orden, contexto de error y cero excepciones; registro 2026-10-14. |

Los casos de reinicio y migración histórica que definen una base H2 aislada conservan ese alcance aunque la ejecución principal apunte a PostgreSQL. Las pruebas JavaScript verifican coordenadas, modos/tablas, radar incompleto/cero válido, mapa descriptivo sin inferencia, consulta obsoleta, caja de cinco números, richText/ausencias, presentación de p y colores/contextos estables.

Chrome utilizó HTML Thymeleaf y JSON sintéticos obtenidos por MockMvc, recursos reales y la política de contenido del backend. La prueba de navegador no reemplaza la verificación Java de autorización, CSRF o recálculo de filtros y no equivale a una prueba completa contra un despliegue. Las capturas locales V01/V05/V06 a 1280 píxeles acompañan esa evidencia. Se revisaron visualmente el radar y un mapa de calor; se ajustaron las etiquetas del radar y se repitieron las comprobaciones de navegador. No se incorporaron datos de campo ni secretos.

Una ejecución inicial de H2 detectó una fixture histórica que dependía de la fecha del día. Se fijó su fecha de referencia sintética en 2020 y se repitió la suite completa; el tratamiento de fechas históricas continúa sin completarlas desde respuestas actuales. El contrato de selección excluye la propiedad técnica `empty`, con comprobación unitaria, para que el aviso de cambios sin aplicar compare exclusivamente los filtros reales.

Permanecen pendientes lector de pantalla real, zoom/ampliación de texto, auditoría completa de contraste y legibilidad manual. Los reportes automáticos/Excel, la simulación con modelo definido y el documento final de Análisis de Mercado siguen fuera del incremento implementado.
