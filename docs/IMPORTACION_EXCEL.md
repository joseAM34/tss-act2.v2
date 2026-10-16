# Paso 8 local: importar Excel con selección de hoja

El [asistente del rediseño](ASISTENTE_IMPORTACION.md) ahora abre directamente la revisión cuando el libro tiene una sola hoja y simplifica correspondencias/confirmaciones. Los límites, conservación y validaciones descritos aquí siguen vigentes.

Implementado el 15 de octubre de 2026. Complementa la [importación CSV](IMPORTACION_CSV.md); no añade exportaciones Excel/PDF ni reconciliación de lotes confirmados.

## Recorrido para el administrador

1. Reinicia la aplicación local con tu comando Maven habitual. Flyway aplicará V15 al arrancar; no borres la base.
2. Entra al panel y abre **Importar respuestas CSV o Excel**.
3. Escoge la encuesta y versión publicada, declara la plataforma y carga el archivo `.xlsx`. El separador del CSV no se usa para Excel.
4. Escoge la hoja que contiene las respuestas y pulsa **Abrir columnas de esta hoja**. La primera fila debe contener encabezados.
5. Usa **Relacionar columnas por código** si los encabezados coinciden; revisa las asignaciones restantes, consentimiento, opciones, separador múltiple y compatibilidad documentada.
6. Guarda la revisión, comprueba todos los errores de la vista previa y confirma el lote. Cargar el libro o seleccionar una hoja no crea respuestas.
7. Abre **Preparar encuestas nuevas**, elige la misma versión y el origen **Sin declarar** mientras no hayas clasificado los envíos. Incluye las variables compatibles y guarda el conjunto.
8. Genera el análisis desde ese conjunto para ver sus gráficas. En **Reportes y plantillas**, elige ese análisis y genera el reporte. Las pruebas sintéticas deben clasificarse como tales antes de utilizarlas en conjuntos destinados a distinguir origen; no sirven para un análisis oficial.

Los libros conservados permiten volver a escoger una hoja sin cargar de nuevo. Cada hoja genera un lote independiente. El mismo libro, versión y hoja reutiliza el lote: reconfirmarlo devuelve las respuestas existentes. Las coincidencias entre hojas/lotes se presentan para revisión; no se deduplica por persona, correo u organización.

## Lectura y límites

Se utiliza el lector OOXML acotado `XlsxFile` con ZIP y XML de Java, sin aplicación frontend ni biblioteca adicional. Lee hojas estándar SpreadsheetML, texto compartido/en línea, números, booleanos y fechas almacenadas como texto. Conserva encabezados repetidos, blancos por posición, espacios del texto y filas intermedias vacías. Los números se normalizan a decimal sin formato visual; los códigos con ceros iniciales deben guardarse como texto. Las selecciones múltiples siguen necesitando separador declarado.

Máximos: 1 MiB comprimido, 16 MiB descomprimido, 300 partes, 30 hojas, 500 filas de datos, 200 columnas y 8000 caracteres por celda. El encabezado ocupa la primera fila y debe cubrir las columnas utilizadas. Hojas con formato aplicado a filas/columnas fuera del límite deben limpiarse antes de cargar.

Se rechazan `.xls`, macros, fórmulas incluso con resultados en caché, celdas con error, celdas combinadas en la hoja seleccionada, XML inválido y entidades externas. No se accede a vínculos externos ni se ejecuta contenido del libro. No se reconstruyen estilos, formatos personalizados o equivalencia institucional con Forms.

Los números de fecha de Excel se conservan como números; no se adivinan calendario, hora o zona. Para asignar la fecha externa, usa texto ISO 8601 con zona, por ejemplo `2026-10-15T09:30:00-04:00`. También puedes elegir **No interpretar fecha externa**: el original permanece conservado sin inventar una fecha. La recepción en el sistema y la fecha externa son conceptos distintos.

## Persistencia y trazabilidad

V15 añade `xlsx_import_workbook` para conservar el libro antes de elegir hoja, y `source_sheet` en `csv_import_batch` (0 para CSV histórico). Amplía la unicidad a versión, huella original y hoja sin modificar V14 ni sustituir registros existentes.

Los bytes íntegros del XLSX y su SHA-256 se conservan en la base, tanto en staging como en el lote seleccionado. El original descargable mantiene los bytes y extensión `.xlsx`. Cada fila confirmada vincula original, índice y nombre de hoja, correspondencias, fila lógica y captura. La verificación comprueba la hoja y sus celdas frente al original. La procedencia es `IMPORTACION_XLSX`; el origen comienza en `NO_DECLARADO`. Los nuevos conjuntos congelan la referencia; no se completan bundles históricos con información actual.

Se mantienen ADMIN, CSRF, escapes, ausencia de caché, publicación inmutable, validación completa del servidor y confirmación transaccional. El índice de fila identifica la fila de la hoja, no una persona o empresa independiente. No se modifica la aprobación metodológica o institucional.

## Verificación

Suite completa: **466 pruebas en H2 y 466 en la ejecución con PostgreSQL 18 temporal**, sin fallos ni omisiones. Incluye lectura XLSX acotada, textos compartidos, posiciones vacías, números, rechazo de fórmulas/entidades/archivos excesivos, selección por hoja, reintentos, candidatos, manipulación de procedencia, permisos y recorrido hasta análisis/reporte. La prueba de persistencia cierra y reabre H2 en archivo para recuperar el libro, lote, evidencia y respuestas sin duplicar.

Chrome comprobó cuatro pantallas reales de Thymeleaf con datos sintéticos en 360, 768 y 1280 píxeles: campos etiquetados, formularios POST con CSRF, sin desbordamiento ni excepciones JavaScript. No constituye una auditoría completa de accesibilidad.

Trabajo local, sin conexión a Neon, commit, push ni despliegue.
