# Asistente de importación · Rediseño, paso 3

Fecha: 15 de octubre de 2026. El usuario autorizó continuar con «luz verde» tras finalizar el espacio de encuestas. Su instrucción es automatizar lo comprobable, mostrar discrepancias y usar casillas para las revisiones rutinarias, conservando requisitos documentales específicos cuando correspondan.

## Recorrido

Reinicia el monolito con el comando Maven habitual y abre **Mis encuestas → encuesta → versión publicada → Importar**.

1. **Archivo.** Carga un CSV UTF-8 o Excel `.xlsx`. La encuesta y versión ya están elegidas. Las opciones del archivo son desplegables; el separador CSV se detecta por defecto. Si hay varias lecturas posibles, el aviso pide elegir coma, punto y coma o tabulación. No se elige simplemente la lectura con más columnas.
2. **Revisar.** Con Excel de una sola hoja, se abre directamente la revisión; con varias hojas se solicita elegir una. El sistema propone correspondencias únicas por código o enunciado completo y muestra las pendientes. Las columnas ya relacionadas quedan en un desplegable para revisarlas o cambiarlas. Una misma columna no se asigna automáticamente a dos campos.
3. **Confirma tu revisión.** Dos casillas registran que revisaste la correspondencia del instrumento y el consentimiento académico que recoge la fuente. No necesitas redactar justificaciones rutinarias. El valor de aceptación se propone cuando existe un único valor afirmativo reconocido y se muestra para revisión.
4. **Validar y continuar.** Se valida todo el lote en el servidor. Los problemas muestran fila, pregunta, valor original y qué revisar. Se pueden cambiar correspondencias o representación; si el dato original es inválido, se corrige una copia del archivo y se vuelve a cargar, conservando el original anterior.
5. **Confirmar.** Si la vista previa es válida, confirma la incorporación. Las coincidencias con otros lotes exigen una casilla específica de revisión. Al terminar, aparecen accesos a las respuestas y resultados de la misma versión.

Guardar sin marcar las revisiones conserva el lote pendiente; no incorpora respuestas. Cargar un archivo, recibir propuestas o abrir la vista previa tampoco incorpora datos. Los nuevos envíos mantienen origen `NO_DECLARADO`.

## Qué se automatiza

| Elemento | Regla |
| --- | --- |
| Separador CSV | Solo una interpretación inequívoca; se ignoran lecturas de una columna cuando hay una lectura multicolumna válida |
| Excel | Una hoja se selecciona en el POST de carga; varias hojas requieren elección. GET no guarda selecciones |
| Encabezados | Código o enunciado completo, ignorando mayúsculas, tildes y separadores de palabras; sin coincidencias parciales |
| Columnas repetidas o disputadas | Permanecen pendientes; se conservan posiciones independientes |
| Consentimiento | Se propone la columna reconocida y un texto afirmativo único; el valor exacto se comprueba en cada fila |
| Fecha externa | Solo se propone una columna explícita con valores ISO 8601 con zona. Una fecha ambigua se conserva sin interpretar y se avisa |
| Opciones | Modo `AUTO`: código o etiqueta únicos, tolerando espacios, mayúsculas y tildes. Signos y operadores se conservan: `-1`, `1`, `<5`, `5`, `C++` y `C` no se confunden |
| Selección múltiple | Modo `AUTO`: primero intenta una opción completa; después solo divide si hay una interpretación válida única con `|`, `;` o `,` |
| Revisiones administrativas | Se guardan declaraciones estándar versionadas, actor, fecha, correspondencias y huella; las casillas nunca se marcan por la detección automática |

`NO_SABE` permanece categoría. El texto libre conserva sus espacios y contenido, cero sigue siendo un valor y una columna ausente no se convierte en un estado histórico inferido. Una etiqueta que contiene comas no se divide si corresponde a una opción completa. Las colisiones entre códigos y etiquetas se informan y pueden resolverse eligiendo el modo exacto.

## Confirmaciones y documentación

El requisito del instrumento vigente es contar con aceptación del uso académico en la fuente, según `docs/ACEPTACION_CUESTIONARIOS.md`. No se identificó en ese contrato una exigencia de redactar un ensayo administrativo por lote. La exigencia previa de dos textos no vacíos era una regla del importador: para el nuevo recorrido se sustituye por declaraciones explícitas registradas desde las casillas.

Las opciones avanzadas mantienen referencias documentales opcionales. Si el estudio o una institución exige respaldo específico, la pantalla indica registrarlo allí; este incremento no incorpora un catálogo de requisitos institucionales ni declara una aprobación automática. La revisión administrativa no sustituye el consentimiento de participantes, la independencia estadística o la aprobación institucional.

El formulario usa `reviewMode=CHECKBOX_V1`. Cada revisión aceptada guarda una declaración `CHECKBOX_COMPATIBILIDAD_V1` o `CHECKBOX_CONSENTIMIENTO_V1`, más la referencia opcional. Al desmarcar una revisión se guarda como pendiente; una referencia escrita no equivale por sí sola a marcar la nueva casilla. Las revisiones anteriores permanecen disponibles. Actor y fecha provienen de autenticación y persistencia, no de campos editables.

## Compatibilidad y conservación

No hay migraciones ni dependencias nuevas. Se conserva la estructura JSON de `Mapping`, evitando agregar campos que cambien las huellas de revisiones históricas. `AUTO` es un modo nuevo y su interpretación debe mantenerse estable; cambios futuros necesitan otro modo/protocolo explícito. Los modos anteriores `CODIGOS` y `ETIQUETAS` conservan su conversión; abrir un lote con revisiones utiliza la última revisión guardada en lugar de sustituirla por la nueva propuesta.

Las propuestas del GET no escriben revisiones ni clasifican orígenes. Confirmar sigue usando revisión y huella de vista previa exactas, transacción completa e idempotencia. La recepción de nuevos candidatos invalida una vista previa anterior. Los cambios posteriores de correspondencias de un lote ya confirmado siguen requiriendo reconciliación; no crean duplicados.

ADMIN, CSRF, validación del servidor, escapes, ausencia de caché, límites de archivo y conservación de originales se mantienen. No se ejecutan fórmulas Excel, se adivinan fechas locales ni se eliminan respuestas por correo/nombre. Las matrices ordinarias conservan sus exclusiones de contactos y texto original.

El funcionamiento principal no requiere JavaScript. `csv-mapping.js` conserva como ayuda opcional completar coincidencias exactas sobre campos aún pendientes, sin cambiar las selecciones ya hechas ni marcar aceptaciones.

## Archivos principales

- `ImportSuggestions`: propuestas y lectura inequívoca de opciones.
- `CsvFile`: detección acotada del separador.
- `CsvImportService`: modos nuevos de lectura con validación e integridad existentes.
- `CsvImportController`: propuestas, casillas, selección de hoja única y discrepancias legibles.
- `csv-import-batch.html`, `csv-import-preview.html`, `fragments/import-column.html`: revisión, avisos y confirmación.
- `workspace.html`, `csv-imports.html`, `xlsx-import-sheets.html`, `workspace.css`: entrada contextual y pasos visuales.

El paso 4 sigue siendo la actualización automática de resultados desde el backend. PDF, imágenes y exportación Excel corresponden al paso 6. Trabajo local, sin commit, push, despliegue, conexión a Neon o cambios en configuración privada.

## Verificación

Suite completa: **502 pruebas Java en H2 y 502 en la ejecución con PostgreSQL 18 temporal**, sin fallos ni omisiones. Incluye **22 pruebas nuevas**: 13 de detección/interpretación y 9 de integración. Verifican ambigüedades, signos, lectura múltiple, casillas sin ensayo, ausencia de aceptación automática, referencias opcionales y desmarcadas, consentimiento negativo, legado, actor/huellas, originales, idempotencia, ADMIN/CSRF, escapes y elección de hoja. Las 9 pruebas JavaScript existentes pasaron.

Chrome comprobó revisión, errores, vista previa, elección de hojas y finalización con HTML real generado por Thymeleaf y fixtures ficticias, recursos locales y CSP del backend a 360, 768 y 1280 píxeles: **15 vistas**, sin desbordamiento de página ni excepciones JavaScript; etiquetas, CSRF, casillas inicialmente desmarcadas y campos de columnas presentes en el formulario aun dentro de desplegables. No es una auditoría completa de accesibilidad.

`git diff --check` finalizó sin errores. PostgreSQL usó exclusivamente una base de pruebas en loopback con `spring.datasource.hikari.minimum-idle=0` en la orden de pruebas; no se modificó la configuración del proyecto ni se conectó a bases externas.
