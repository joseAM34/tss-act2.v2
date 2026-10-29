# Exportación Word de reportes

Formato acordado el 29 de octubre de 2026: texto y tablas en Arial 11; todos los títulos en Arial 12 y negrita, también en la vista del reporte y su PDF. Los datos numéricos de las tablas Word se redondean a un máximo de dos decimales; las tablas HTML/PDF muestran dos decimales en medidas y porcentajes y conservan los conteos enteros. El redondeo es de presentación: no modifica fuentes congeladas, cálculos ni datos incorporados de los gráficos. Portada, logotipo, índice, encabezado institucional y saltos por sección quedan para una definición posterior.

El editor conserva hasta ocho vistas renderizadas en memoria de la página y seis respuestas de vista previa verificadas. Navegar entre preguntas o pantallas no vacía esas cachés. Reutiliza los nodos y gráficos cuando coinciden el HTML y sus datos asociados; los cambios de configuración siguen verificándose en el servidor. Volver del diccionario abierto desde el editor, reintentar o cambiar la confirmación de datos sensibles descarta las cachés. No se guardan datos en almacenamiento del navegador ni se precalculan todas las combinaciones. Al salir se liberan los gráficos; generar el reporte conserva la verificación del backend.

La descarga **Word editable (.docx)** está disponible en cada reporte generado, junto a PDF, Excel y CSV. Requiere ADMIN y utiliza `ReportService.get`: verifica el reporte guardado y sus fuentes congeladas, sin consultar respuestas nuevas ni el diccionario actual. No cambia el reporte ni su plantilla. La respuesta HTTP usa `no-store`.

El archivo contiene títulos, párrafos, tablas de Word y gráficos nativos OOXML, sin capturas de pantalla. Cada gráfico incorpora una hoja Excel dentro del documento con sus datos: se puede utilizar **Editar datos** en Word. No hay llamadas externas, dependencias nuevas, macros ni vínculos a archivos externos. La edición posterior del documento es independiente de la aplicación.

Se respetan las secciones, preguntas, títulos, comentarios y representaciones seleccionadas. Se conservan las bases de porcentajes, estados, estratos de origen, diagnósticos de chi-cuadrado, fecha del corte y huellas de referencia. Las tablas equivalentes que HTML ofrece plegadas se incluyen como tablas editables. Los originales y coincidencias de texto se incluyen únicamente cuando sus representaciones fueron seleccionadas; las restricciones y confirmaciones de datos sensibles son las del reporte verificado, con advertencia también en el documento.

| Representación | Salida Word |
| --- | --- |
| Columnas, barras, agrupadas y apiladas | Gráfico nativo y hoja de datos incorporada |
| Circular y anillo, incluidas coincidencias | Gráfico nativo; los sectores de coincidencias distribuyen menciones, no envíos |
| Histograma | Columnas contiguas sobre los intervalos ya calculados; tabla de intervalos |
| Línea y área | Gráfico nativo con meses y conteos del reporte; los faltantes se conservan como huecos |
| Radar | Gráfico nativo en escala 0–100 y bases por criterio |
| Dispersión | Gráfico XY nativo; coordenadas repetidas conservan frecuencia en su tabla |
| Mapa de calor | Tabla de conteos con celdas coloreadas editables, con aclaración |
| Caja | Tabla editable de mínimo, Q1, mediana, Q3 y máximo; no se fabrica un gráfico de caja |

La distribución entre páginas y los estilos de los gráficos pueden variar entre Word, otros lectores DOCX y el PDF del navegador. PDF conserva la presentación para lectura; DOCX conserva la estructura para edición. No se promete identidad visual entre ambos.

Pruebas automatizadas: estructura XML de DOCX y hojas incorporadas, referencias a series, faltantes sin ceros inventados, contenido seleccionado frente a enriquecimiento no seleccionado, autorización de descarga y conservación de bytes después de nuevos envíos. Los artefactos de prueba contienen exclusivamente datos ficticios en `.codex-tmp/word-export` (ignorado).

La comprobación adicional por automatización de Microsoft Word instalado no pudo completarse: la instancia de prueba quedó bloqueada y se cerró. La validación automatizada no sustituye una revisión visual del DOCX en el lector utilizado para la entrega.

Verificación del incremento (19 de octubre de 2026): siete pruebas específicas de exportación y gestión de reportes pasaron. La revisión ampliada ejecutó 99 pruebas: 96 pasaron; fallaron una confirmación CSV concurrente por bloqueo H2, una comprobación de origen y una expectativa del texto anterior «sin plantilla». Al repetir esas tres pruebas sobre una copia aislada con los archivos modificados de producción restaurados desde HEAD, las dos primeras pasaron y la expectativa del texto volvió a fallar. No se modificaron esos comportamientos ni sus pruebas en este incremento. Registros locales: `.codex-tmp/word-export-final-tests.log`, `word-export-regression.log` y `word-baseline-tests.log`.
