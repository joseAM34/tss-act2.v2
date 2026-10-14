# Fase 2: cobertura y aceptación de los cuestionarios

Fecha de integración: 2026-10-13. Especificación de referencia: [02_REQUISITOS_CUESTIONARIOS.md](02_REQUISITOS_CUESTIONARIOS.md). Instrumento y adaptaciones: [INSTRUMENTO.md](INSTRUMENTO.md). Decisiones del usuario: [DECISIONES.md](DECISIONES.md).

El usuario autorizó continuar el desarrollo. Esta matriz permite revisar qué recoge la aplicación y qué sigue sin definición; no convierte el borrador en un instrumento aprobado para trabajo de campo. Las preguntas originales se distinguen de las variables exigidas por las historias de usuario y de las propuestas adicionales.

## Cómo leer la matriz

- **Implementado en borrador:** existe un campo o comportamiento ejecutable; su aprobación metodológica puede seguir pendiente.
- **Pendiente:** no se habilita un campo cuyo catálogo, unidad, enunciado o regla no está definido. No se registra como si el participante lo hubiera omitido.
- **Obligatorio aplicable:** se exige únicamente cuando se cumple su condición. La fuente no define todos los saltos de titulados; las condiciones operativas de este incremento se conservan como propuestas.
- **Opcional:** una pregunta aplicable puede quedar vacía; no se rellena con cero, «No» ni con una categoría del catálogo.

La numeración T/E pertenece a la especificación, mientras los identificadores ASCII corresponden al catálogo y a la persistencia. Esta matriz describe `2026-10-borrador-2`. Los textos y opciones exactos están en `SurveyCatalog`; la tabla abrevia los enunciados sin sustituirlos.

## Titulados: correspondencia, obligatoriedad y condiciones

| Requisito | Variable | Identificador en aplicación | Regla operativa del borrador | Estado / definición pendiente |
| --- | --- | --- | --- | --- |
| T01 | Edad actual | `edad` | Opcional; entero en años completos, 0-120 como límite técnico provisional. | Implementado; adaptación aprobada para borrador D01, no rango de la fuente. |
| T02 | Género | `genero` | Opcional; Hombre/Mujer/Otro/Prefiero no responder. | Implementado; adaptación aprobada para borrador D01. |
| T03 | Formación complementaria realizada o en curso | `formacion_complementaria` | Obligatoria, Sí/No; controla T04-T07. | Implementado; obligatoriedad y salto propuestos. |
| T04 | Mayor nivel complementario realizado o en curso | `nivel_cursado` | Obligatoria si T03=Sí; de lo contrario no aplica. | Implementado; catálogo original. |
| T05 | Institución del programa realizado | `institucion_cursada` | Obligatoria si T03=Sí; de lo contrario no aplica. | Implementado; catálogo original. |
| T06 | Financiamiento del programa realizado | `financiamiento_cursado` | Obligatoria si T03=Sí; de lo contrario no aplica. | Implementado; se conserva separado de T38. |
| T07 | Nombre del programa | `programa_cursado` | Texto obligatorio si T03=Sí; de lo contrario no aplica. | Implementado. |
| T08 | Interés en futuro posgrado | `interes_posgrado` | Obligatoria, Sí/No; controla T09/T10/T37/T38. | Implementado; obligatoriedad y salto propuestos. |
| T09 | Área de interés | `area_interes` | Selección única obligatoria si T08=Sí; de lo contrario no aplica. | Implementado; catálogo original, pendiente reconciliar ejemplos HU2 en D04. |
| T10 | Institución preferida para futuro posgrado | `institucion_interes` | Obligatoria si T08=Sí; de lo contrario no aplica. | Implementado. |
| T11 | Opinión sobre posgrado FCyT | `opinion_posgrado_fcyt` | Obligatoria y visible sin depender de T08; escala original de acuerdo. | Implementado; D08/D11: revisar aplicación a quienes no conocen la oferta. |
| T12 | Tiempo de vida profesional | `meses_vida_profesional` | Opcional; meses enteros 0-960; debe indicarse si se responde T13. | Implementado; meses enteros aprobados para borrador D02; rango técnico provisional, definición temporal definitiva pendiente. No se deriva de T35. |
| T13 | Tiempo total desempleado | `meses_desempleado` | Opcional; meses enteros 0-960, no superiores a T12. | Implementado; unidad/precisión aprobadas para borrador D02; no inferir cero si se omite. |
| T14 | Situación laboral actual | `estado_laboral` | Obligatoria; empleado, desempleado o emprendimiento a tiempo completo. | Implementado; conserva categorías originales. |
| T15 | Origen del emprendimiento | `origen_emprendimiento` | Obligatoria si T14=emprendimiento; de lo contrario no aplica. | Implementado; salto propuesto. |
| T16 | Entregable del negocio | `entregable_emprendimiento` | Obligatoria si T14=emprendimiento; de lo contrario no aplica. | Implementado; Producto/Servicio/Ambos. |
| T17 | Financiamiento externo del negocio | `financiamiento_emprendimiento` | Obligatoria si T14=emprendimiento; de lo contrario no aplica. | Implementado; categorías originales. |
| T18 | Satisfacción con el negocio | `satisfaccion_emprendimiento` | Obligatoria si T14=emprendimiento; de lo contrario no aplica. | Implementado; escala original de tres categorías. |
| T19 | Importancia de formación para el negocio | `importancia_formacion_negocio` | Obligatoria si T14=emprendimiento; de lo contrario no aplica. | Implementado; escala original de cuatro categorías. |
| T20 | El empleo actual es el primero | `primer_empleo_actual` | Obligatoria si T14=empleado; de lo contrario no aplica. | Implementado; **D09 pendiente:** aplicación a autoempleo. |
| T21 | Tiempo hasta primer empleo relacionado | `tiempo_primer_empleo` | Opcional y aplicable solo si T41=`RELACIONADA`, aunque actualmente esté desempleado. | Implementado; filtro aprobado D09; límites temporales sin solapamiento pendientes D10. |
| T22 | Cantidad de empleos desde titulación | `cantidad_empleos` | Opcional; aplica a T41=`RELACIONADA` u `OTRAS_AREAS`; categorías 1/2/3/Más de 3. | Implementado; para `SIN_EXPERIENCIA` no aplica, sin forzar una categoría mayor que cero. Experiencia anterior a la titulación no obliga a responder. |
| T23 | Satisfacción con formación | `satisfaccion_formacion` | Obligatoria, incluso sin empleo actual; conserva escala de acuerdo. | Implementado; **D11**: coherencia del enunciado con la escala. |
| T24 | Concordancia formación/requerimientos | `concordancia_formacion` | Obligatoria solo con T41=`RELACIONADA`; escala original de acuerdo. | Implementado; adaptación propuesta del fragmento original (p. 11) para referir trabajo actual o anterior relacionado con Sistemas, pendiente revisión D11. |
| T25 | Aspectos útiles | `aspectos_utiles` | Opcional; selección múltiple distinta, hasta las seis opciones del catálogo. | Implementado. |
| T26 | Aspectos mejorables | `aspectos_mejorar` | Opcional; selección múltiple distinta, hasta las seis opciones del catálogo. | Implementado. |
| T27 | Asignaturas de ventaja | `asignaturas_ventaja` | Opcional; si se responde, exactamente cinco opciones distintas. | Implementado; cantidad conforme al original. |
| T28 | Otra asignatura de ventaja | `otra_asignatura_ventaja` | Texto opcional y complementario, sin sumar a las cinco selecciones de T27. | Implementado; conteo aprobado para borrador D06. |
| T29 | Asignaturas de poca utilidad | `asignaturas_poca_utilidad` | Opcional; hasta dos selecciones distintas del catálogo original. | Implementado; máximo de dos aprobado para borrador D06, resuelve contradicción título/instrucción. |
| T30 | Otra asignatura de poca utilidad | `otra_asignatura_poca_utilidad` | Texto opcional complementario, no suma al máximo de dos selecciones. | Implementado; conteo aprobado para borrador D06. |
| T31 | Interés en red de contactos | `interes_red_contactos` | Obligatoria, Sí/No. | Implementado; obligatoriedad propuesta. |
| T32 | Correo de contacto | `email_contacto` | Opcional y visible sin depender de T31; formato de correo si se proporciona. | Implementado; **D12 pendiente:** autorización específica de contacto y retención. |
| T33 | Teléfono de contacto | `telefono_contacto` | Opcional; texto, hasta 30 caracteres y al menos cinco dígitos; admite prefijo y separadores. | Implementado; conserva texto, no medida numérica. |
| T34 | LinkedIn | `linkedin` | Opcional; URL absoluta HTTP/HTTPS con host; sin consulta a servicios externos. | Implementado; **D12** para política definitiva de dominios y contactos. |
| T35 | Año de egreso, HU1 | `anio_egreso` | Obligatorio; entero entre 1960 y año actual en `America/La_Paz`. | Implementado; **D02 pendiente:** egreso/titulación, límite inferior y año fijo del estudio. |
| T36 | Sector laboral, HU1 | `sector` | Obligatorio; `NO_APLICA` solo si T14=desempleado; empleado/emprendedor necesita sector. | Implementado; reutilización del catálogo de empleadores propuesta, **D03 pendiente**. |
| T37 | Nivel de futuro posgrado, HU2 | `nivel_interes` | Preferencia principal única, obligatoria si T08=Sí; de lo contrario no aplica. | Implementado con Maestría/Especialidad/Doctorado; **D04 pendiente**: catálogo/cardinalidad definitivos. |
| T38 | Financiamiento futuro, HU3 | `financiamiento_futuro` | Fuente principal única, obligatoria si T08=Sí; de lo contrario no aplica. | Implementado; **D04 pendiente:** principal o ponderado. No sustituir por T06. |
| T39 | Competencias técnicas, adaptación HU4 | `competencia_tecnica_01` a `competencia_tecnica_06` | Seis valoraciones obligatorias aplicables solo si T41=`RELACIONADA`; escala de adecuación y «No puedo valorar». | Implementado; ítems, referente y escala aprobados para borrador D05. No es matriz original del anexo ni cálculo de brecha. |
| T40 | Competencias transversales, adaptación HU4 | `competencia_transversal_01` a `competencia_transversal_06` | Seis valoraciones obligatorias aplicables solo si T41=`RELACIONADA`; misma escala y referente. | Implementado; adaptación aprobada para borrador D05. |
| T41 | Filtro de experiencia laboral | `experiencia_laboral` | Obligatorio; `RELACIONADA`, `OTRAS_AREAS` o `SIN_EXPERIENCIA`. Este último no es compatible con empleo o emprendimiento actual. | Implementado; filtro aprobado para borrador D09; conserva situación actual separada de experiencia previa. |

## Empleadores: correspondencia, obligatoriedad y condiciones

| Requisito | Variable | Identificador en aplicación | Regla operativa del borrador | Estado / definición pendiente |
| --- | --- | --- | --- | --- |
| E00 | Correo | `email_contacto` | Texto obligatorio con formato de correo. | Implementado; obligatoriedad marcada en fuente. |
| E01 | Nombre de empresa o institución | `nombre_organizacion` | Texto obligatorio con contenido distinto de espacios. | Implementado; fuente. |
| E02 | Tipo de organización | `tipo_organizacion` | Selección única obligatoria Público/Privado. | Implementado; fuente. |
| E03 | Tamaño de organización | `tamano_organizacion` | Selección única obligatoria; Grande/Mediana/Pequeña/Micro. | Implementado; fuente; no convertir categorías en número exacto de funcionarios. |
| E04 | Sector principal | `sector` | Selección única obligatoria del catálogo original. | Implementado; fuente. |
| E05 | Contratación en últimos cinco años | `contratacion_reciente` | Obligatoria, Sí/No; no oculta valoraciones al responder No. | Implementado; **D13 pendiente:** elegibilidad aparte de esta respuesta y fecha de referencia. |
| E06 | Posibilidad de futura contratación | `probabilidad_contratacion` | Selección única obligatoria de las cuatro categorías originales. | Implementado; horizonte pendiente para escenarios; no produce número de puestos. |
| E07 | Formación demandada | `nivel_formacion_demandado` | Nivel principal único obligatorio. | Implementado; **D08 pendiente:** cardinalidad definitiva. |
| E08 | Medio de convocatoria | `medio_convocatoria` | Medio principal único opcional. | Implementado; sin asterisco en fuente; **D08 pendiente:** cardinalidad/obligatoriedad definitivas. |
| E09 | Cargos desempeñados | `cargos_desempenados` | Múltiple obligatorio; entre una y ocho opciones distintas del catálogo. | Implementado; fuente admite varias selecciones. |
| E09_OTRO | Descripción de otro cargo | `otro_cargo` | Texto opcional; aplicable únicamente cuando E09 incluye `OTRO`. | Implementado en este incremento; condición propuesta y obligatoriedad definitiva pendiente D08. |
| E10 | Áreas y tendencias emergentes | `areas_emergentes` | Texto largo obligatorio con contenido distinto de espacios. | Implementado; fuente. |
| E11 | Herramientas tecnológicas | `herramientas_tecnologicas` | Texto largo obligatorio con contenido distinto de espacios. | Implementado; fuente. |
| E12 | Competencias y destrezas requeridas | `competencias_requeridas` | Texto largo obligatorio con contenido distinto de espacios. | Implementado; fuente; no equivale a puntuaciones T39/T40. |
| E13 | Confianza en carrera UMSS | `criterio_01` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; obligatoriedad propuesta D08, criterio único aprobado D07. |
| E14 | Consistencia de título y competencias | `criterio_02` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; mismas reglas. |
| E15 | Consulta regular a empleadores | `criterio_03` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; mismas reglas. |
| E16 | Conocimiento del perfil de egreso | `criterio_04` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; mismas reglas. |
| E17 | Coherencia del perfil y desempeño | `criterio_05` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; mismas reglas. |
| E18 | Desempeño destacado e integral | `criterio_06` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; mismas reglas. |
| E19 | Necesidades empresariales en formación práctica | `criterio_07` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; mismas reglas. |
| E20 | Competencias laborales | `criterio_08` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; mismas reglas. |
| E21 | Ética y responsabilidad social | `criterio_09` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; mismas reglas. |
| E22 | Participación en evaluación/retroalimentación | `criterio_10` | Una valoración obligatoria de escala original; admite `NO_SABE`. | Implementado; mismas reglas. |

Los diez criterios E13-E22 corresponden a una sola lista en la aplicación. Se conserva en la referencia la localización de ambas apariciones del PDF; no hay veinte variables distintas.

## Matrices de competencias aprobadas para el borrador

El referente de ambas matrices es **la adecuación de la formación recibida en la carrera a las demandas del trabajo relacionado con Sistemas, actual o anterior**. Se muestran únicamente con T41=`RELACIONADA`; cada ítem aplicable requiere una respuesta y admite «No puedo valorar». No se modifica la escala original de T11/T18/T19/T23/T24 ni la de E13-E22.

| Bloque | Identificador | Ítem |
| --- | --- | --- |
| T39 técnica | `competencia_tecnica_01` | Desarrollo de software |
| T39 técnica | `competencia_tecnica_02` | Bases de datos |
| T39 técnica | `competencia_tecnica_03` | Redes |
| T39 técnica | `competencia_tecnica_04` | Seguridad informática |
| T39 técnica | `competencia_tecnica_05` | Análisis de datos |
| T39 técnica | `competencia_tecnica_06` | Gestión de proyectos |
| T40 transversal | `competencia_transversal_01` | Comunicación |
| T40 transversal | `competencia_transversal_02` | Resolución de problemas |
| T40 transversal | `competencia_transversal_03` | Pensamiento crítico |
| T40 transversal | `competencia_transversal_04` | Aprendizaje continuo |
| T40 transversal | `competencia_transversal_05` | Trabajo en equipo |
| T40 transversal | `competencia_transversal_06` | Ética profesional |

| Código | Etiqueta | Interpretación futura |
| --- | --- | --- |
| `ADECUACION_1` | Nada adecuada | Primer grado de adecuación ordinal. |
| `ADECUACION_2` | Poco adecuada | Segundo grado. |
| `ADECUACION_3` | Moderadamente adecuada | Tercer grado. |
| `ADECUACION_4` | Adecuada | Cuarto grado. |
| `ADECUACION_5` | Muy adecuada | Quinto grado. |
| `NO_PUEDE_VALORAR` | No puedo valorar | Categoría nominal explícita, sin puntaje; estado `CON_VALOR`. |

La escala permite ordenar grados de adecuación, pero su presencia no implementa todavía cálculo de brechas, estadística ni radar. No se captura una pareja «nivel adquirido/nivel requerido» que pueda restarse. Cada futuro porcentaje deberá indicar si incluye las respuestas «No puedo valorar» en su denominador.

## Correspondencia con las historias de usuario

| Historia | Captura que la respalda | Cobertura actual y etapa siguiente |
| --- | --- | --- |
| HU1: situación profesional de titulados | T12-T14/T35/T36; T21/T22/T41 como trayectoria. | Captura disponible en borrador; tiempo en meses enteros con validación cruzada. Definiciones definitivas D02/D03/D10 pendientes. Frecuencias y cruces se implementarán en estadística. |
| HU2: preferencias de formación continua | T08-T11/T37; T03-T07 distinguen programas realizados. | Captura disponible, con cardinalidad/catálogos futuros pendientes D04. Distribuciones y análisis siguientes. |
| HU3: financiamiento y nivel futuro | T38 × T37, separados de financiamiento/nivel realizado T06/T04. | Campos disponibles en la rama de interés; cruce, contingencias y chi-cuadrado pendientes de instrumento comparable y etapa analítica. |
| HU4: adecuación y competencias | T23/T24, E13-E22, respuestas abiertas E10-E12 y doce ítems T39/T40. | Valoraciones originales y matrices de adecuación aprobadas para borrador disponibles; método de análisis/brecha pendiente D05. No se infieren puntajes de los textos abiertos. |
| HU5: visualización y reportes automatizados | Conjunto de respuestas versionadas, estados y futura matriz depurada. | Panel y CSV originales disponibles; visualización computacional, gráficos, matrices, Excel y automatización de reportes en backend pendientes RF11/RF12/D14. |

## Reglas comunes y estados de respuesta

La validación del servidor comprueba catálogos, cardinalidad, opciones repetidas, valores numéricos enteros/rango, campos desconocidos, formatos de contacto y longitud. Los textos de una línea admiten hasta 1000 caracteres; los textos largos hasta 2000. Los valores originales válidos se conservan para la codificación posterior. Los errores aparecen junto a las preguntas y el formulario conserva los valores introducidos para corregirlos.

El envío requiere aceptación del uso académico (`consent=SI`), CSRF válido y el token del formulario asociado a la sesión. Ese consentimiento general es una adaptación documentada; no permite publicar contactos ni acredita elegibilidad. Un token identifica una operación: previene duplicar un reintento, pero no identifica de forma inequívoca a una persona/empresa ni resuelve la política de participación D13.

En los nuevos envíos se registra un estado para cada pregunta de la versión presentada:

| Estado | Cuándo se registra | Tratamiento |
| --- | --- | --- |
| `CON_VALOR` | Pregunta aplicable con uno o varios valores válidos. | Se conservan códigos, etiquetas y selección múltiple; `NO_SABE` tiene este estado. |
| `SIN_RESPUESTA` | Pregunta aplicable opcional que quedó vacía. | No se transforma en cero ni en una opción negativa. Una obligatoria vacía impide el envío final. |
| `NO_APLICA` | No se cumple la condición operativa de la pregunta. | Se descartan valores enviados para el bloque inactivo y no se cuentan como respuestas aplicables. |

Los estados siguen las condiciones del **borrador** de esta tabla, pendientes de aprobación metodológica cuando corresponda. Las preguntas no habilitadas por ambigüedad no forman parte de esa versión y no producen filas de «sin respuesta». La opción categórica `sector=NO_APLICA` es un valor explícito seleccionado por el participante; no debe confundirse con el estado técnico de una pregunta oculta.

Las respuestas anteriores que no almacenaron estos metadatos conservan su información original. La ausencia histórica de una fila no permite decidir entre omisión y salto: se presenta como ausencia de estado registrado, sin reconstruir `NO_APLICA` a partir del catálogo actual. La versión permite distinguir ambos instrumentos.

El histórico `anios_vida_profesional` se conserva como valor de su versión: no se sustituye por `meses_vida_profesional` ni se convierte a meses mediante una actualización de captura original. El CSV continúa exportando valores originales; los nuevos estados se consultan en el detalle administrativo y servirán de insumo para la futura matriz depurada.

## Criterios CA01-CA20 y evidencia

**Verificación técnica, 2026-10-13:** `verify` pasó 102 pruebas en la ejecución H2 y 102 en la ejecución con PostgreSQL 18.6, ambas con cero fallos, errores y omisiones. La ejecución PostgreSQL utilizó una instancia temporal y una base exclusiva de pruebas. `PhaseTwoAcceptanceIntegrationTest` aporta 33 casos de cada ejecución; los demás pertenecen a la suite existente de encuestas y administración. Las pruebas de reinicio y de migración de un histórico usan H2 aislado en las dos ejecuciones; no se atribuyen a PostgreSQL esos dos escenarios específicos. La revisión adicional del JavaScript pasó 58 aserciones y la comprobación de sintaxis.

**Interpretación de estados:** «Cubierto en borrador» indica código implementado con evidencia técnica; «Parcial» indica límites concretos; «Pendiente» indica falta de definición o de función. Las pruebas comprueban el comportamiento del borrador implementado; no sustituyen la aprobación metodológica ni institucional. Los nombres nuevos de la tabla pertenecen a `PhaseTwoAcceptanceIntegrationTest`; los originales a `SurveyApplicationIntegrationTest`.

| Caso | Estado | Cobertura y evidencia / límite |
| --- | --- | --- |
| CA01 | Cubierto en borrador | Inicio con dos entradas y propósito correspondiente; `publicPagesRenderWithoutAuthentication`. |
| CA02 | Parcial | Los 34 campos originales tienen correspondencia; titulados agrega T35-T38/T41 y doce ítems T39/T40, para 51 campos. `eachNewResponseStoresEveryQuestionStateAndItsHistoricalLabel` comprueba estados/textos de todas las preguntas de ambos catálogos; `competenceItemsAreDistinctAndCannotBeRatedRemainsAValueRatherThanANumericScore` comprueba doce ítems únicos. **No hay aprobación institucional ni cierre de todas las reglas metodológicas**. |
| CA03 | Cubierto en borrador; salto propuesto | T03=No desactiva T04-T07 y descarta sus valores. `emptyOptionalQuestionDiffersFromAnInactiveQuestionAfterItsControlChanges` comprueba `NO_APLICA` frente a `SIN_RESPUESTA`; `repositoryRejectsAClaimedValidSubmissionContainingAValueForAnInactiveQuestion` comprueba coherencia al persistir. |
| CA04 | Cubierto en borrador; salto propuesto | T08=No excluye T09/T10/T37/T38 y mantiene T11; `decliningFuturePostgraduateStudyDoesNotDisableTheGeneralFacultyOpinion` comprueba estados y conservación de opinión general. |
| CA05 | Cubierto en borrador; salto propuesto | T14=emprendimiento habilita y exige T15-T19; `businessOwnerHasAnApplicableBusinessBlockWithoutBeingForcedToDeclareACurrentFirstJob` comprueba negocio aplicable sin forzar empleo actual. |
| CA06 | Cubierto en borrador | `someoneWhoNeverWorkedCanSubmitWithoutChoosingAJobCountOrRatingWorkCompetencies` comprueba la ruta sin experiencia; `currentlyUnemployedGraduateWithPreviousUnrelatedWorkKeepsTheirJobHistory` conserva historial; `noWorkExperienceCannotBeCombinedWithCurrentEmploymentOrABusiness` rechaza incoherencias actuales. Autoempleo paralelo y revisión de T24 conservan pendiente D09/D11. |
| CA07 | Cubierto en borrador | Obligatoriedad aplicable y conservación para corrección: `invalidSubmissionShowsErrorsAndWritesNothing`, `validationErrorRetainsValuesAndAllowsCorrectedSubmission` y `validationFailureDoesNotIssueAReceiptOrSaveQuestionStates`. Matriz metodológica definitiva pendiente D08. |
| CA08 | Cubierto en borrador | Rechazo backend de categorías, cantidades, duplicados y campos desconocidos; pruebas originales de validación HTTP. `lowUtilitySubjectsAreOptionalAndAcceptAtMostTwoSelections` cubre cero, una y dos selecciones; `threeLowUtilitySubjectsAreRejectedByTheBackend` rechaza tres. T27 conserva cinco si se responde. |
| CA09 | Cubierto en borrador | T12/T13 usan meses enteros no negativos; `inconsistentOrNonIntegerProfessionalAndUnemploymentMonthsAreRejected` cubre seis errores de unidad/rango/coherencia; `zeroMonthsAreExplicitValuesAndAreNotTreatedAsMissing` conserva cero observado. Límites 0-960 son técnicos provisionales. |
| CA10 | Cubierto en borrador | `explicitNoSabeHasAValueAndIsNotAnOmissionOrAScore` comprueba código y `CON_VALOR`; la prueba original `validNoSabeIsStoredAsItsOwnCategory` conserva la categoría. No se calcula una puntuación de acuerdo. |
| CA11 | Cubierto; decisión aprobada | Diez IDs únicos y diez respuestas; `employersRenderEachOfTheTenCriteriaOnceWithNoSabeAsAnOption`, `repeatOfSameFormDoesNotCreateAnotherResponse`. |
| CA12 | Cubierto en borrador; condición propuesta | `otherJobDescriptionCannotBeStoredUnlessOtroWasSelected`, `selectedOtroKeepsTheOtherSelectedJobsAndTheDescription` y `applicableOptionalOtherJobDescriptionMayRemainUnanswered` comprueban condición, múltiples valores y omisión aplicable. Obligatoriedad definitiva pendiente D08. |
| CA13 | Cubierto para reintentos | `retryReturnsTheSameReferenceAndDoesNotDuplicateValuesOrStates` comprueba una respuesta y su misma referencia UUID; `referenceIsVisibleOnlyToTheSubmittingSessionAndCannotBeChosenInTheUrl` comprueba comprobante propio sin datos de respuesta. **Pendiente D13:** participación distinta de la misma persona/empresa. |
| CA14 | Cubierto técnicamente | `aLateDatabaseFailureRollsBackTheResponseItsAnswersAndItsStates` provoca fallo tardío y comprueba rollback de respuesta/valores/estados y ausencia de comprobante. `validationFailureDoesNotIssueAReceiptOrSaveQuestionStates` comprueba rechazo previo al guardado. |
| CA15 | Cubierto en borrador | `validSubmissionPersistsOriginalAnswersWithInstrumentVersion` comprueba ID, tipo, versión y fecha. `administratorsSeeMissingAndInapplicableStatesWhileTheRawCsvRemainsUnchanged`, `aLegacyResponseKeepsItsOriginalQuestionAndDoesNotReceiveInferredStates` y `migratingAnExistingDatabaseDoesNotInventHistoricalStatesOrChangeRawValues` verifican detalle, CSV y conservación histórica. |
| CA16 | Parcial | Panel, detalle y CSV requieren rol ADMIN; `privateRoutesRequireLogin`, `authenticatedUsersWithoutAdminRoleCannotReadResponses`, `responsesAreEscapedInAdminAndNeverExposedOnPublicPages`. Las salidas analíticas aún no existen; su omisión de contactos debe verificarse al implementarlas. |
| CA17 | Pendiente análisis / D04 | T37/T38 ya se capturan separadas de T04/T06, pero no existe cruce analítico ni instrumento definitivo comparable. |
| CA18 | Pendiente pipeline | La captura conserva versión, valores y nuevos estados, como insumos. No está implementada la matriz depurada ni el registro de transformaciones/filtros del pipeline. |
| CA19 | Pendiente RF12/D14 | CSV de respuestas originales disponible; reportes automatizados, matrices analíticas y Excel todavía no están implementados. |
| CA20 | Pendiente RF13/D15 | No existe modelo de simulación definido ni comparación de escenarios implementada. |

## Cierre de fase y siguiente incremento

La fase 2 incorpora la especificación, esta matriz de correspondencia, los 34 campos originales de titulados y las adaptaciones aprobadas por el usuario para el borrador. La encuesta de titulados tiene 51 campos y la de empleadores 24; los doce ítems de competencias corresponden a T39/T40, no a doce preguntas originales añadidas al anexo. El instrumento final requiere cerrar las reglas metodológicas todavía pendientes, confirmar la obligatoriedad y obtener la aprobación institucional que corresponda. Ningún resultado de pruebas técnicas sustituye esa aprobación.

La estadística, Data Wrangling, reportes automatizados, Excel y simulación mantienen su alcance en la referencia y en el README; no se declaran completados en este incremento.
