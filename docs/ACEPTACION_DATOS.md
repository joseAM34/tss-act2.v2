# Fase 3: aceptación del modelo y Data Wrangling

Estado: modelo y preparación implementados y verificados técnicamente el 2026-10-13. Fuente: [03_DATOS_Y_DATA_WRANGLING.md](03_DATOS_Y_DATA_WRANGLING.md). Contrato efectivo y diccionario: [DATOS_Y_WRANGLING.md](DATOS_Y_WRANGLING.md). La tabla distingue la cobertura técnica de los pendientes metodológicos y funcionales.

La suite final pasó 159 casos en la ejecución H2 y 159 en la ejecución con PostgreSQL 18.6, sin fallos, errores ni omisiones. Los nombres de prueba de la tabla pertenecen a `PhaseThreeDataIntegrationTest`, salvo indicación explícita de la suite de fase 2. «Cubierto técnicamente» se refiere al contrato ejecutable; no aprueba institucionalmente el instrumento, la elegibilidad, la independencia ni la unidad de análisis.

| Caso | Cobertura del incremento | Evidencia requerida / límite |
| --- | --- | --- |
| CDA01 | Cubierto técnicamente | `instrumentDefinitionIsHashedAndCannotChangeWithoutANewVersion` y `aCapturedSubmissionStartsWithUndeclaredOriginAndHasVerifiedFrozenProvenance` comprueban definición/captura verificables; persistencia transaccional conserva un tipo por envío. |
| CDA02 | Cubierto técnicamente | `multipleChoiceIndicatorsDistinguishAnOmittedQuestionFromAnUnselectedOptionWithoutMultiplyingParticipants` y pruebas de estados de fase 2 comprueban null/estado frente a un No o cero observados; códigos `SI`/`NO` permanecen categorías. |
| CDA03 | Cubierto técnicamente | `aQuestionAbsentFromAKnownOlderDefinitionIsNotCollectedRatherThanAnUnansweredQuestion` comprueba `NO_RECOLECTADA` solo con definición verificable. `historicalResponsesWithoutASnapshotNeverBorrowTheCurrentCatalogOrInventStates` comprueba la limitación si no existe snapshot. |
| CDA04 | Cubierto técnicamente | `noSabeAndCannotRateRemainNominalValuesWithoutAgreementScores` conserva `NO_SABE`/`NO_PUEDE_VALORAR`, `CON_VALOR`, sin puntajes; no sustituye por el código propuesto `NS`. |
| CDA05 | Cubierto técnicamente | `multipleChoiceIndicatorsDistinguishAnOmittedQuestionFromAnUnselectedOptionWithoutMultiplyingParticipants` comprueba opción distinta, una fila principal y estado de la pregunta. |
| CDA06 | Cubierto técnicamente | La misma prueba contrasta matriz y relaciones de selecciones sin producto cartesiano de preguntas múltiples ni aumento de participantes. |
| CDA07 | Cubierto técnicamente | La misma prueba conserva indicadores null ante omisión y 0 solo ante una opción no seleccionada de respuesta válida. La unión de opciones entre versiones conserva null si esa versión no ofrecía la opción. |
| CDA08 | Cubierto técnicamente | `invalidImportedCategoriesAndTemporalRelationsBecomeIncidencesWithoutImputation` e `incoherentImportedStatesAndMissingRequiredAnswersRemainVisibleAsQualityIssues` usan entradas inconsistentes construidas en pruebas y registran incidencias, sin adivinar valores. **No hay importador externo implementado.** |
| CDA09 | Cubierto técnicamente | `invalidImportedCategoriesAndTemporalRelationsBecomeIncidencesWithoutImputation` comprueba relación temporal compatible y registra los campos afectados sin decidir arbitrariamente cuál corregir. |
| CDA10 | Cubierto técnicamente | `retryWithAChangedAnswerIsAConflictAndCannotRewriteTheFirstCapture` comprueba 409; `retryWithTheSameCheckboxSetInAnotherOrderReturnsTheOriginalReference` conserva UUID para el mismo conjunto de selección. |
| CDA11 | Parcial; D13/D16 pendientes | No deduplicar automáticamente por correo/nombre. La política de varias participaciones y unidad empleador requiere definición antes de afirmar deduplicación semántica. |
| CDA12 | Parcial | Diez criterios únicos de la versión digital, comprobados por la suite de fase 2. Importación/reconciliación de dos apariciones originales conflictivas no está implementada ni se resuelve con promedio. |
| CDA13 | Cubierto técnicamente | `manualTextCodingCountsEachParticipantAndThemeOnceAndNeverCopiesTheSourceText` y `textCorrectionRequiresReviewingItsThemesAgainWithoutChangingThePreviousDataset` comprueban temas únicos, libro de códigos, hash del texto y revisión. Semántica institucional pendiente D18. |
| CDA14 | Pendiente de referencia D02 | No generar antigüedad usando el año actual de ejecución ni transformar vida profesional en antigüedad. Hace falta referencia aprobada y reconciliación de egreso/titulación. |
| CDA15 | Cubierto técnicamente | `theEntireAnalyticalBundleOmitsContactIdentificationAndUnreviewedFreeText` y `jsonCsvAndZipDownloadsContainTheFrozenDatasetAndOmitPrivateSourceValues` comprueban privacidad. Pruebas de autorización protegen cada descarga; CSV original mantiene su alcance separado. |
| CDA16 | Cubierto técnicamente | `repeatedPreparationHasDifferentRunIdentifiersButTheSameLogicalContentHash` comprueba contenido lógico reproducible; `aUtcCutoffIncludesOnlyTheExactCapturedInputsAvailableBeforeIt` comprueba inventario del corte. UUID/hora técnica y corte sin cambio de miembros no alteran SHA lógico. |
| CDA17 | Cubierto técnicamente | `revisionsAppendWithoutRewritingRawDataOrPreviouslyPreparedResults` y `newResponsesAndNewClassificationsCannotChangeAnAlreadyPreparedDataset` conservan originales/conjunto previo y distinguen revisiones futuras. |
| CDA18 | Cubierto técnicamente | `failureWhileFreezingMembersDoesNotPublishAPartialDataset` comprueba rollback de publicación; `aCaptureWhoseHashNoLongerMatchesCannotProduceAnAnalyticalDataset` y `alteringThePersistedDatasetInvalidatesItsHashAndPreventsDelivery` comprueban integridad de entrada y entrega. |
| CDA19 | Pendiente de reportes | HU5 debe identificar el conjunto y consumir salidas coherentes. No hay reportes automatizados, Excel ni reconstrucción estadística implementados en este incremento. |
| CDA20 | Parcial; estadística pendiente | T38/T37 preparados separados de T06/T04 con estados; contingencias, pares elegibles por protocolo y chi-cuadrado se definirán después. |

## Comprobaciones adicionales del incremento

- Nuevos envíos parten de origen `NO_DECLARADO`; parámetros públicos no pueden asignar `REAL`/`SINTETICO` ni aprobar versiones.
- Una revisión de origen real no demuestra elegibilidad. El modo `OFICIAL` requiere origen real, estudio y versiones aprobados; no aprueba borradores al ejecutar.
- Históricos sin captura/definición verificable o estados muestran `SIN_ESTADO_HISTORICO`/incidencias, sin usar el catálogo actual para reconstruirlos.
- La migración conserva `anios_vida_profesional` original sin actualizarlo como meses.
- Correcciones de campos inexistentes, sin definición verificable o fuera del dominio/aplicabilidad se rechazan; las incoherencias relacionales se identifican durante preparación sin corrección inventada. Los textos se escapan en el panel y los CSV se protegen frente a fórmulas sin alterar el original.
- Descargas y mutaciones requieren autorización ADMIN; los POST requieren CSRF. Consultas públicas no revelan captura, contactos, manifiestos ni microdatos.
- Los IDs estructurados no revisados quedan restringidos por defecto con incidencia; no se exponen por inferir privacidad de su tipo de control.
- La codificación identifica la huella del texto revisado; corregir ese texto vuelve inaplicables los temas anteriores hasta una revisión nueva, conservando el conjunto previo.
- Revisiones simultáneas se ordenan por identidad secuencial `revision_order`; instantes UTC conservan precisión de microsegundos.
- La lectura y descarga de un conjunto valida `bundle_hash` y contenido lógico; un bundle alterado no se presenta como válido. Corte distinto con mismos miembros/productos conserva `logical_hash` según el contrato efectivo.
- Un control inválido o no verificable bloquea campos dependientes con null/`VALOR_INVALIDO` y `APLICABILIDAD_NO_VERIFICABLE`, conservando captura original y campos independientes. Un texto fuente inválido no aporta temas codificados a la salida.

| Contrato adicional | Pruebas verificadas del incremento |
| --- | --- |
| Separación de orígenes | `realSyntheticAndUndeclaredResponsesAreKeptInSeparateDatasets` |
| Permisos de lectura | `anonymousUsersCannotReadDatasetsOrAnyOfTheirDownloads`, `anAuthenticatedUserWithoutTheAdminRoleCannotReadDatasets` |
| Permisos y CSRF de escritura | `stateChangingDataOperationsRequireCsrf`, `anonymousUsersCannotCreateDatasetsOrChangeResponseMetadataEvenWithCsrf` |
| Flujo administrativo | `administratorsCanClassifyCorrectCodeAndPrepareThroughTheProtectedPanel` |
| Bloqueo oficial de borrador | `aDraftInstrumentCannotBePublishedAsAnOfficialDataset` |
| Revisión válida y sin sobrescritura | `invalidRevisionsDoNotEnterTheAuditOrRewriteTheCapture`, con siete casos de error de campo/categoría/rango/cardinalidad/motivo/actor. |
| Restricción de variables desconocidas o que cambian de privacidad | `anUnknownVariableDoesNotBecomeAnalyticalMerelyBecauseItsTypeIsNumeric`, `aFieldThatBecomesPrivateTextInAnotherVersionCannotBorrowTheEarlierNumericPrivacyRule` |
| Aplicabilidad con control inválido | `anInvalidControlCannotConfirmThatItsDependentQuestionsDoNotApply` |
| Migración histórica sin inferencias | `migratingAnExistingDatabaseDoesNotInventHistoricalStatesOrChangeRawValues`, prueba aislada H2 de `PhaseTwoAcceptanceIntegrationTest` conservada en la suite. |

## Registro de evidencia

El 2026-10-13, `verify` terminó con `BUILD SUCCESS` en ambas ejecuciones: 159 casos, cero fallos, errores y omisiones. `PhaseThreeDataIntegrationTest` aporta 57 casos y las suites anteriores 102. La ejecución H2 terminó a las 06:22:36 y la ejecución PostgreSQL 18.6 a las 06:23:26, zona UTC-04.

La ejecución PostgreSQL utilizó una instancia aislada en `127.0.0.1:55435` y la base exclusiva `phase3_verification`; después se detuvo. Las pruebas de reinicio administrativo y migración de un histórico utilizan H2 aislado incluso cuando las suites de aplicación apuntan a PostgreSQL. No se atribuyen a PostgreSQL esos dos escenarios específicos.

Las pruebas finales comprueban captura lógica, definición congelada, conflicto de reintento, separación de orígenes, cardinalidad, faltantes, dependencias no verificables, codificación vinculada al texto, revisiones, reproducibilidad, integridad, rollback y autorización/CSRF de panel y descargas. Las entradas inconsistentes que algunas pruebas llaman «importadas» se construyen exclusivamente dentro de la suite: no se ha incorporado un importador de archivos o fuentes externas.

Esta evidencia no resuelve D13/D16/D18/D19 ni implementa CDA14/CDA19 o la estadística de CDA20. Las matrices y sus recuentos técnicos no equivalen a un análisis de mercado concluido.

## Completitud documental

- [x] Referencia incorporada y autorización actual distinguida de sus instrucciones documentales.
- [x] Diccionario efectivo relacionado con T01-T41 y E00-E22/E09_OTRO, separado de nombres/códigos propuestos.
- [x] Captura original, estados, contactos, selecciones, revisiones y conjuntos distinguidos.
- [x] Origen no declarado, real y sintético separados; límites del modo oficial documentados.
- [x] Cardinalidad, privacidad, trazabilidad y reproducibilidad especificadas.
- [x] Nombres físicos V4/V5 y rutas/productos documentados y contrastados con el incremento verificado.
- [x] Pruebas del incremento ejecutadas y evidencia registrada, con los límites de cada entorno explícitos.

Las marcas documentales no declaran completados modelos estadísticos, reportes, simulación ni aprobación del instrumento.
