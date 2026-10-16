# Exportaciones del reporte — paso 6

Implementado el 15 de octubre de 2026 para los reportes generales de versiones del editor. El reporte se genera en el backend a partir de un análisis y cruces del mismo conjunto congelado; sus descargas mantienen esas fuentes. Este incremento no añade exportaciones al dashboard original UMSS ni convierte el reporte en el documento institucional final.

## Recorrido

1. En **Mis encuestas → encuesta → versión → Resultados**, aplica origen y fechas. Si consultas resultados actualizados, pulsa **Guardar este corte** y luego **Crear reporte y exportar**.
2. Elige la plantilla y los cruces del mismo conjunto que quieras incluir. Genera el reporte y revisa sus cifras, bases y diagnóstico.
3. En **Exportar este reporte**, elige el formato. Las opciones PNG y SVG aparecen debajo de cada gráfico con valores.

| Formato | Contenido y funcionamiento |
| --- | --- |
| PDF | **Guardar como PDF / imprimir** abre el diálogo del navegador. Elige **Guardar como PDF**. La hoja de impresión usa A4 horizontal, oculta navegación/controles y conserva gráficos, tablas, contexto, comentarios y referencias. Revisa la vista previa del navegador antes de guardar. |
| PNG | Imagen con fondo blanco y resolución doble, título y etiquetas completas, versión/origen, período, corte, protocolo, base, ID/huella del reporte y fuente. |
| SVG | Imagen vectorial con el mismo contexto; permite ampliar sin perder resolución. Las etiquetas se representan como texto escapado. |
| CSV | Tabla UTF-8 con BOM, valores agregados, bases y referencias. Incluye las secciones de resultados/comentarios elegidas en la plantilla. |
| Excel | Archivo `.xlsx` real, con una hoja **Resultados** y las mismas filas que el CSV; cifras numéricas y textos literales, sin fórmulas ni macros. |
| JSON | Contenido congelado completo, plantilla, análisis, cruces, contexto y huellas. Conserva `null` como ausencia. |

La generación y verificación del reporte y de CSV/Excel ocurren en Java. La representación de imágenes y la composición del PDF ocurren en el navegador con recursos locales; no se envían datos a un servicio externo. No se añadió un motor PDF al servidor ni una ruta de descarga directa `.pdf`. La aplicación no puede saber si se guardó o canceló el diálogo, por lo que no anuncia una descarga PDF completada.

Sin JavaScript, los enlaces CSV/Excel/JSON siguen disponibles y puede imprimirse con el menú del navegador; se conservan tablas y resúmenes, mientras los gráficos requieren JavaScript. Una plantilla sin gráficos permite usar el botón PDF cuando termina de verificar su contenido. Si falla la carga para dibujar gráficos, permanece el documento HTML y el estado informa del fallo; el botón integrado de PDF no se habilita.

## Contrato de los datos descargados

CSV y Excel contienen columnas `seccion`, `variable`, `fila`, `columna`, `medida`, `valor` y `estado_celda`. Las medidas conservan sus nombres del protocolo: por ejemplo `respondent_base`, `respondent_percent`, `mention_base`, `observed`, `expected` o `row_percentages`. Los totales de filas/columnas identifican su categoría. El contexto contiene estudio, versión, período y corte; las referencias incluyen ID y huellas del reporte, análisis y cruces.

Un cero es un valor numérico. Un valor ausente se exporta vacío con `estado_celda=SIN_VALOR`. Esta marca describe la celda exportada, no atribuye un estado de respuesta a un envío histórico. Los estados de preguntas y pares ya calculados se conservan por separado. Una p ausente no se sustituye por cero ni por una conclusión sobre asociación. No se recalculan estadísticas en JavaScript ni se mezclan versiones u orígenes.

Los textos CSV que podrían interpretarse como fórmulas llevan un apóstrofo protector. Las cifras negativas siguen siendo cifras. En Excel los textos, incluso los que empiezan por `=`, son celdas de texto, y las cifras son celdas numéricas. Para recuperar el texto lógico exacto sin la protección CSV utiliza JSON o Excel.

Las salidas ordinarias contienen agregados del reporte, no contactos, respuestas originales ni matrices de envíos. Los comentarios y etiquetas administrativos sí se exportan cuando corresponden: no deben contener información privada que no se quiera compartir. Todas las rutas del backend requieren ADMIN y usan `Cache-Control: no-store`; la integridad se verifica antes de enviar el archivo. Un reporte corrupto no produce una descarga parcial. Descargar crea una copia local bajo control del usuario.

PNG/SVG se generan desde el mismo contenido congelado y añaden su contexto. Las etiquetas se dividen en líneas, sin el truncamiento usado en la vista interactiva. Los límites de dimensiones/área evitan imágenes desmesuradas; en ese caso se informa que se utilicen las tablas CSV/Excel. No se promete igualdad byte a byte entre imágenes o PDF producidos por distintos navegadores. Los números y sus fuentes son el contrato conservado.

## Verificación

- 78 pruebas Java focalizadas correctas en H2: preparación/análisis/cruces/reportes, resultados actuales, ayudas y exportaciones.
- 11 pruebas JavaScript correctas de los controles de visualización y actualización.
- La prueba de integración de exportación comprueba ADMIN, ausencia de caché, archivos adjuntos, huellas/bases/ausencias, ausencia de contactos originales, igualdad de descargas tras nuevos envíos y rechazo de contenido alterado.
- Pruebas del formato comprueban UTF-8, comillas/saltos, textos de fórmula, ceros/negativos, celdas numéricas, caracteres XML escapados y validez XML de todas las partes OOXML.
- Chrome con HTML real de Thymeleaf y datos ficticios: seis vistas a 360/768/1280, diez imágenes PNG/SVG de cinco gráficos, contexto/huella y escape en SVG, PDF generado con gráficos/tablas, plantilla sin gráficos y manejo de fallo de carga. Sin excepciones JavaScript ni desbordamiento de página.

Evidencia local excluida del historial: `.codex-tmp/exports-verify.log`, `.codex-tmp/exports-final.log`, `.codex-tmp/report-browser/exports-verification.json`, imágenes `export-*` y `export-report.pdf`. Las comprobaciones no constituyen una auditoría de accesibilidad ni una validación manual en todas las versiones de Excel o navegadores. No se repitió PostgreSQL: no hay cambios de esquema ni consultas nuevas de persistencia. Sin dependencias ni migraciones nuevas, commit, push o despliegue.

El paso 7 corresponde a la prueba completa del recorrido desde creación/publicación hasta recepción/importación, análisis y exportación.
