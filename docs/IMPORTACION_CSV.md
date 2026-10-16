# Paso 7 local: importación CSV con revisión y trazabilidad

**Recorrido vigente:** el [asistente del paso 3 del rediseño](ASISTENTE_IMPORTACION.md) incorpora propuestas automáticas y casillas de revisión, conservando originales, validación y trazabilidad. La descripción siguiente registra el incremento inicial; su exigencia rutinaria de textos escritos queda sustituida por las confirmaciones documentadas en esa guía.

Fecha: 2026-10-14. El usuario autorizó desarrollar después de explicar el paso 7. Trabajo local antes de commit, push o despliegue.

## Cómo probarlo

**Correspondencias rápidas:** en el lote pulsa **Relacionar columnas por código**. Completa únicamente encabezados que coinciden exactamente y una sola vez con el código de pregunta; también propone `consentimiento`, `fecha_externa` e `id_externo`. Conserva selecciones manuales y evita reutilizar columnas. La propuesta queda en el formulario para revisar: no guarda, no confirma, no interpreta separadores de múltiples ni marca consentimiento/compatibilidad como verificados. Después configura las opciones y evidencia y pulsa **Guardar revisión y abrir vista previa**.

Reinicia desde la raíz:

```powershell
mvn "-Dspring-boot.run.profiles=dev" spring-boot:run
```

Usa `dev` para H2 local. No combines `local` si conecta a Neon. Flyway agrega V14; no cambia credenciales ni ejecuta importaciones automáticamente.

1. Panel → **Importar respuestas CSV**.
2. Elige una encuesta/versión publicada, plataforma declarada y separador del archivo. Carga un CSV UTF-8 con encabezados.
3. Revisa el original y relaciona cada pregunta con su columna. La posición distingue encabezados repetidos: `1: Dato` y `2: Dato` son columnas distintas.
4. Asigna la columna que contiene consentimiento académico y el texto exacto que significa aceptación, por ejemplo `SI` o `Sí`. Documenta qué consentimiento recogió el formulario original; no basta una aceptación ficticia del administrador.
5. Si hay fecha externa ISO 8601 con zona, puedes asignarla. Ejemplo: `2026-10-14T12:30:00-04:00`. Si no puedes interpretar su formato, deja **No interpretar fecha externa**; el dato original sigue en el archivo conservado. Puedes asignar un identificador proporcionado por la plataforma, sin inventarlo.
6. Indica si las opciones vienen como códigos o etiquetas exactas. Para selecciones múltiples elige explícitamente el separador dentro de la celda; no se adivina. El separador de la celda es independiente del separador del archivo.
7. Documenta y marca la revisión de equivalencia de preguntas, opciones, unidades, obligatoriedad y condiciones con la versión elegida.
8. **Guardar revisión y abrir vista previa** valida todas las filas, sin crear respuestas. Corrige errores mediante otra revisión de correspondencias.
9. Si hay candidatos a solapamiento, abre sus lotes enlazados y revisa el motivo. Una coincidencia no identifica por sí sola a la misma persona/empresa; confirmar exige autorización explícita para incorporarlas como participaciones distintas.
10. Pulsa **Confirmar importación completa**. Aparecerán enlaces a las respuestas incorporadas, inicialmente con **Origen sin verificar**.

## Requisitos y límites

Se admiten UTF-8 con BOM opcional, coma/punto y coma/tabulación declarados, comillas dobles escapadas y saltos de línea dentro de celdas entre comillas. Máximos: 1 MiB por archivo, 500 registros de datos, 200 columnas, 8000 caracteres por celda. Las reglas del cuestionario pueden ser más estrictas; por ejemplo, texto corto hasta 1000 y texto largo hasta 2000 caracteres.

Todos los registros deben tener la misma cantidad de columnas; no se descartan filas vacías o irregulares silenciosamente. El número de registro cuenta el encabezado como 1; una celda multilínea sigue siendo un registro CSV, no varias respuestas.

Los códigos/etiquetas controlados y números recortan espacios exteriores; el texto libre se conserva. Las etiquetas deben coincidir exactamente y ser inequívocas. Códigos inexistentes, selecciones repetidas, límites incumplidos, identificadores externos repetidos dentro del lote o fechas asignadas con formato no admitido bloquean la incorporación.

Todas las preguntas, incluso opcionales, requieren columna declarada para confirmar. Un campo sin columna no demuestra «Sin respuesta» ni «No aplica». Un blanco solo recibe estado al aplicar reglas de una definición compatible cuya revisión quedó documentada. Si una pregunta no aplica pero el original contiene un valor, se exige resolver la contradicción; no se descarta automáticamente.

Sin consentimiento registrado en la fuente o sin documentación de compatibilidad, el lote permanece en revisión. No se crea `consent=TRUE` para resolver un faltante. La plataforma y compatibilidad son declaraciones administrativas, no verificaciones de Microsoft/Google ni aprobación institucional.

Este primer adaptador trabaja por correspondencias manuales. No conecta APIs, descarga respuestas desde enlaces ni promete interpretar automáticamente cualquier exportación de Forms. CSV de otra codificación, fechas locales ambiguas, archivos XLSX y proveedores con estructuras especiales necesitan preparación o adaptadores posteriores.

## Idempotencia y conservación

El original se guarda íntegro como bytes codificados en base64 en PostgreSQL, con SHA-256 de los bytes originales. No depende del disco efímero de Render. H2 conserva lo mismo para pruebas locales. El nombre de archivo sirve para presentación; no determina una ruta del servidor.

Subir los mismos bytes a la misma versión reutiliza el lote; otro separador/plataforma declarado para ese original produce conflicto. El mismo archivo destinado a otra versión queda como otro lote y se presenta como candidato a solapamiento.

Las correspondencias tienen revisiones inmutables con actor, fecha y huella. La confirmación queda ligada a una revisión y a la huella de vista previa; usa una transacción para todo el lote. Si falla cualquier escritura, se revierten todas las respuestas del intento y se conserva el original para revisión.

Reconfirmar el mismo lote/revisión/vista previa devuelve las mismas referencias, incluso si después se cerró la versión. Los reintentos concurrentes no duplican respuestas. Una versión cerrada no recibe nuevas importaciones. Una nueva revisión de correspondencias de un lote confirmado se puede revisar, pero su confirmación se bloquea hasta definir reconciliación con respuestas existentes; no se duplican mediante otro mapeo.

Registros distintos del archivo permanecen distintos aunque tengan el mismo correo, organización o contenido. Se presentan candidatos por original idéntico o valores completos de registro coincidentes; no se deduplica por persona. La autorización de solapamiento y sus candidatos se conservan en la evidencia del registro importado.

V14 añade `csv_import_batch`, `csv_import_mapping`, `csv_import_confirmation` y `csv_import_row`. Cada respuesta importada conserva original/lote, posición, revisión, hashes de fila y captura, plataforma declarada, fecha externa y su precisión cuando se interpreta, e identificador externo cuando existe. Un campo sin columna declarada se distingue de uno vacío en la fuente. La fecha de recepción es la del ingreso al sistema, separada de la fecha externa.

La captura lógica mantiene su formato anterior. Su procedencia administrativa se verifica mediante los vínculos CSV y se muestra como `IMPORTACION_CSV`; no se presenta como participación web. El detalle de respuesta enlaza lote y original. Los conjuntos generales nuevos conservan referencia de importación y procedencia, sin contactos/identificador externo; los conjuntos anteriores conservan sus contenidos y huellas. Todos los nuevos envíos siguen en `NO_DECLARADO`, sujetos a clasificación administrativa trazable.

## Seguridad y archivos

Todas las rutas, vistas previas, originales y respuestas requieren ADMIN. Las operaciones POST requieren CSRF y las salidas privadas no se almacenan en caché. Los textos del archivo se escapan; no se ejecutan fórmulas, HTML o enlaces del CSV. El original se descarga como texto `.txt`, conservando exactamente los bytes; no es una exportación normalizada para ejecutar en una hoja de cálculo.

`CsvFile`, `CsvImportService`, `CsvImportEvidence`, `CsvImportController` y las tres plantillas `csv-imports.html`, `csv-import-batch.html`/`csv-import-preview.html` implementan el recorrido. El tamaño de carga se limita también mediante configuración multipart de Spring. No se modifican migraciones aplicadas, respuestas históricas ni credenciales privadas.

XLSX, reconciliación de lotes confirmados, formatos de fecha adicionales, mapeo detallado de opciones por proveedor y aprobación institucional continúan pendientes.

## Verificación

La suite completa pasó **455 pruebas Java en H2 y 455 en la ejecución con PostgreSQL 18 local**, sin fallos ni omisiones. Se usaron datos ficticios y una base temporal; no se conectó a Neon ni se realizó commit, push o despliegue.

Pruebas de comillas, saltos internos, encabezados repetidos, separadores, UTF-8, límites y hash de bytes. Pruebas de carga sin respuestas, consentimiento, correspondencias, condiciones, estados, fechas/precisión, origen, múltiples, etiquetas, candidatos, reintentos, concurrencia, cierre, cambios de revisión, huellas, rollback real tras fallo en la segunda fila, ADMIN/CSRF y escapes. La prueba en archivo recupera original, evidencia y respuestas tras cerrar/reabrir la base, sin duplicar al reconfirmar.

Chrome verificó carga, correspondencias y vista previa con HTML real y datos ficticios a 360, 768 y 1280 píxeles, sin desbordamiento ni excepciones JavaScript. No es una auditoría completa de accesibilidad.
