# Generador visual de reportes: plan y seguimiento

Estado al 2026-10-17: los ocho pasos están implementados y verificados técnicamente. Ver [aceptación del recorrido completo](ACEPTACION_GENERADOR_REPORTES.md). El apartado inicial conserva la descripción histórica de la maqueta.

Fecha: 2026-10-17. El usuario autorizó «luz verde» para el primer paso del nuevo recorrido. Se conserva la aplicación existente y se crea un prototipo HTML independiente, con datos sintéticos preparados. No es una nueva pantalla funcional del monolito.

Abre [generador-reportes.html](prototipos/generador-reportes.html) directamente en el navegador. Sus recursos son locales y funciona con `file://`. No necesita iniciar Java, usar credenciales ni conectarse a Neon.

## Recorrido propuesto

**Resultados → Crear reporte → Personalizar y revisar → Generar y descargar.**

La pantalla inicial muestra datos y generación a la izquierda; a la derecha, la vista previa del reporte y el botón **Personalizar**. Este botón abre otra pantalla con accesos a preguntas/gráficos, comparaciones y presentación. No se amontonan los editores junto al reporte completo. Tanto tablas como gráficos de cada pregunta están activados por defecto por indicación posterior del usuario; las comparaciones, comentarios y plantilla siguen siendo opcionales. En móvil las columnas se apilan.

Hay siete pantallas dentro del mismo archivo, con navegación por fragmentos URL: `#reporte`, `#personalizar`, `#preguntas`, `#pregunta/empleo` (o la pregunta elegida), `#cruces`, `#cruce/nuevo` (o el índice de una comparación existente) y `#presentacion`. Solo una es visible a la vez. El editor de pregunta muestra únicamente esa pregunta a la derecha; el editor de cruce, únicamente la comparación; presentación previsualiza únicamente sus textos. Volver conserva la configuración durante la sesión de esta maqueta.

| Área | Interacción de esta maqueta |
| --- | --- |
| Datos | Título editable, versión/origen visibles y fechas inclusivas. Solo el período del 1 al 15 de octubre de 2026 tiene fixture: otros períodos no se rellenan con ceros ni se presentan como datos reales. |
| Contenido | Listado de preguntas con casillas y acceso «Configurar gráfico». Cada pregunta abre su editor con título de presentación, representación y vista previa aislada. Barras o columnas iniciales para categorías; la pregunta numérica usa mínimo/mediana/máximo en meses, sin atribuirle una distribución. Las tablas equivalentes siguen disponibles si se ocultan las tablas principales. |
| Comparaciones establecidas | Pantalla propia con casillas para incluir cruces del conjunto ficticio y acceso para editar cada comparación en otra pantalla. |
| Crear comparación | Grupos desplegables con preguntas y checkboxes. La primera selección destaca las compatibles; una comparación categórica admite exactamente dos preguntas. Nombre sugerido editable, vista previa y botón para incluirla. |
| Representación del cruce | Tabla, mapa de calor, barras agrupadas o apiladas, con los mismos conteos ficticios. Las tablas se conservan. |
| Compatibilidad | Azul significa seleccionada, verde compatible y gris no disponible; todos llevan texto. Los iconos tienen nombres. Agrupar preguntas no restringe automáticamente las comparaciones entre grupos. La pregunta numérica permanece identificada como no disponible para cruces en esta entrega. |
| Presentación | Introducción y comentarios opcionales, representados como texto seguro separado de las cifras. |
| Plantilla | Casilla opcional y nombre editable. Sin nombre propio se propone el título del reporte. La plantilla futura guardará configuración, sin datos congelados ni comentarios administrativos del reporte. |
| Generación | Diálogo con una copia de la vista previa y explicación de qué se guardaría. No crea registros, archivos exportados ni plantillas reales. Rechaza un reporte sin preguntas/comparaciones o sin representación para las preguntas seleccionadas. |

Los controles usan datos ficticios de 30 envíos. Las distribuciones y matrices están declaradas en el JavaScript como fixtures, no calculadas desde respuestas de campo. El diagnóstico ilustrado mantiene chi-cuadrado no ejecutado por independencia sin documentar y frecuencias esperadas insuficientes; no inventa p, decisión ni V de Cramér.

## Reglas para la implementación posterior

- Heredar encuesta, versión, origen, fechas y selección; mostrar contexto aunque se omita su sección ampliada.
- Las vistas previas reales deben calcularse en Java desde una fuente consistente y verificada. No trasladar cálculos estadísticos al navegador.
- Guardar el corte mostrado al generar, o informar de que cambió y pedir revisar la vista actualizada. No sustituir fuentes silenciosamente.
- Proponer comparaciones compatibles sin ejecutar todos los contrastes ni asumir independencia por defecto. Mantener unidades, bases, estados y diagnósticos.
- Mostrar gráficos apropiados para los tipos y escalas. Un radar requiere varios criterios compatibles y bases explícitas; no es una alternativa universal a una tabla categórica.
- Proteger consultas y generación con ADMIN; mutaciones con CSRF. Los GET de vista previa no crean plantillas, análisis o reportes.
- Guardar la personalización del reporte sin modificar cifras ni reportes anteriores. La plantilla es una opción reutilizable, no un requisito de generación.
- Conservar los flujos actuales mientras se conecta el nuevo generador y reutilizar las exportaciones verificadas.

## Etapas acordadas

| Paso | Entrega | Estado |
| --- | --- | --- |
| 1 | Prototipo visual de configuración, comparaciones y vista previa | Implementado como maqueta; verificación técnica abajo. |
| 2 | Reporte directo desde Resultados y guardado del corte | Implementado y verificado; ver actualización al final. |
| 3 | Personalización y vista previa reales desde el backend | Implementado y verificado; ver actualización al final. |
| 4 | Comparaciones guardadas/nuevas integradas y nombres personalizados | Implementado y verificado; ver actualización al final. |
| 5 | Gráficos configurables para preguntas y comparaciones | Implementado y verificado; ver actualización al final. |
| 6 | Histogramas, cajas, dispersión, líneas y radares según compatibilidad | Implementado y verificado; reglas y datos requeridos en la guía. |
| 7 | Plantilla opcional y exportaciones con la personalización elegida | Implementado y verificado; plantilla opcional y exportaciones personalizadas. |
| 8 | Verificación del nuevo recorrido completo | Completada; ver aceptación técnica. |

Este plan es posterior al rediseño de navegación ya verificado en [ACEPTACION_REDISENO.md](ACEPTACION_REDISENO.md). No reabre ni declara incompletos esos incrementos; mejora el recorrido de generación señalado por el usuario.

## Verificación del prototipo

Chrome comprobó 27 vistas a 360, 768 y 1280 píxeles: las siete pantallas y los dos listados con una selección. Sin desbordamientos de página ni excepciones JavaScript. Verificó una sola pantalla visible, etiquetas de controles, cuatro gráficos y tablas predeterminados, listas inicialmente sin contenido de vista previa, selección y deselección independientes de inclusión, edición desde la vista previa, circular/anillo, restricciones por tipo numérico, comentarios por pregunta y cruce conservados en el reporte y texto escapado. También verificó compatibilidad dinámica, límite de dos preguntas, mapa de calor, alta/edición de comparación sin duplicarla, cambios conservados al navegar, plantilla opcional, generación y período sin fixture. No constituye auditoría completa de accesibilidad o prueba con usuarios finales.

`node --check` y `git diff --check` correctos. La evidencia y capturas están en `.codex-tmp/report-generator-browser/`, excluido de Git. No se ejecuta la suite Java porque este paso solo añade una maqueta independiente y documentación; no modifica código del monolito, migraciones, datos o exportaciones. Sin commit, push ni despliegue en este paso.

## Ajuste de selección, comentarios y gráficos

En los listados de preguntas y comparaciones, pulsar el nombre muestra exclusivamente ese elemento a la derecha. Al entrar desde Personalizar no hay selección ni contenido de vista previa; volver a pulsar el nombre quita la selección. Las casillas controlan la inclusión en el reporte por separado. «Editar presentación» abre el editor correspondiente; al regresar se conserva el elemento editado.

Cada pregunta y comparación admite un comentario opcional, vacío inicialmente, que aparece junto a sus resultados. Los comentarios generales siguen siendo opcionales. Todos se representan como texto escapado y no alteran las cifras. En la futura plantilla se guardará configuración, sin estos comentarios del reporte.

Las preguntas comienzan con tabla y columnas verticales por petición del usuario. Las categorías exclusivas permiten también barras horizontales, circular y anillo. El resumen numérico admite tabla y barras/columnas de mínimo, mediana y máximo; no se presenta como distribución. El selector muestra líneas, áreas, histograma, caja, dispersión y radar deshabilitados con la condición que falta. No se ofrece un gráfico engañoso por semejanza con Excel. Los cruces conservan tabla, mapa de calor y barras agrupadas/apiladas.

El PDF de referencia menciona mapas de calor y radar en HU5; no establece columnas verticales como representación universal. El radar necesita varios criterios y escalas compatibles: su disponibilidad en otro dashboard del sistema no significa que esta maqueta ya lo integre en cualquier pregunta o cruce.

## Representaciones simultáneas por botones

Los desplegables se sustituyen por botones con `aria-pressed`, agrupados por categoría (datos, comparar cantidades, partes de un total y otros; en cruces, datos y comparaciones). Cada representación puede activarse o desactivarse de forma independiente. Las seleccionadas muestran color, marca y brillo; las disponibles permanecen sin resaltar y las incompatibles están deshabilitadas en gris con explicación visible. Se permite combinar todas las compatibles y se conservan al navegar y guardar una comparación en la maqueta.

La vista previa y el reporte muestran cada representación elegida. Las tablas equivalentes siguen accesibles cuando solo se muestran gráficos; el mapa de calor conserva su tabla coloreada. Los controles generales de tablas/gráficos siguen actuando sobre las preguntas. Se impide generar cuando un elemento incluido queda sin representaciones activas. Los listados resumen la selección de cada pregunta. Verificación: 27 vistas de Chrome a tres anchos, selección simultánea de cuatro gráficos descriptivos y tabla, y de mapa de calor, tabla y dos gráficos de cruce; sin excepciones ni desbordamientos. Se comprobó el rechazo de preguntas incluidas sin representación.

Ajuste visual compacto: preguntas y cruces usan botones pequeños con icono y nombre, conservando categorías y selección múltiple. Las razones de incompatibilidad se agrupan en un desplegable por categoría y también están en el título del botón. Se mantiene el estado mediante color, marca y `aria-pressed`. Revisión en Chrome de 27 vistas a 360, 768 y 1280 píxeles, sin desbordamientos ni excepciones.

Ajuste de espacio y desplazamiento: categorías contiguas que se acomodan por filas, botones de aproximadamente 65 × 50 píxeles y campos/títulos de configuración más compactos. El panel derecho de pregunta y de cruce tiene desplazamiento vertical independiente, altura limitada al área visible y acceso por teclado; en escritorio permanece visible al desplazar la página. Verificado en las 27 vistas y mediante desplazamiento del panel sin cambiar la posición de la página, tanto en preguntas con varias gráficas como en cruces. La maqueta conserva datos ficticios y no cambia el backend.

## Paso 2 implementado: generación directa

Autorizado el 2026-10-17. Resultados permite guardar el corte mostrado y abrir el generador con su análisis y cruce; el formulario genera sin plantilla previa. La presentación directa queda congelada en el reporte (`REPORTE_HTML_V2`) sin crear plantillas. V16 permite referencia de revisión nula y conserva las referencias existentes; `REPORTE_HTML_V1` sigue verificándose. Instrucciones y evidencia: [REPORTES_HTML.md](REPORTES_HTML.md#paso-2-del-nuevo-generador-reporte-directo).

El paso 3 integrará la personalización y vista previa reales. Los botones múltiples y el resto del editor visual siguen como maqueta hasta sus incrementos correspondientes.

## Paso 3 implementado: personalización y vista previa reales

Autorizado el 2026-10-17. El reporte directo abre el generador con pantallas separadas, selección de preguntas, título/comentario por pregunta, introducción/contexto/comentario general y opciones de tablas/gráficos. Vista previa desde el backend y fragmentos compartidos con el documento final; la generación exige la huella revisada. La configuración se congela como REPORTE_HTML_V3 y se recupera al generar otro con las mismas fuentes. Los cruces guardados mantienen selección y vista previa; creación y nombres en el generador siguen en el paso 4, las representaciones múltiples en el paso 5. Contrato y verificación: [REPORTES_HTML.md](REPORTES_HTML.md#paso-3-personalización-y-vista-previa-conectadas-al-backend).

## Paso 4 implementado: comparaciones integradas

Creación desde Personalizar → Comparaciones, con grupos congelados del instrumento, dos preguntas compatibles, vista previa calculada en backend y guardado condicionado a su huella. La creación concurrente reutiliza un cruce idéntico. Los cruces guardados pueden incluirse, renombrarse y comentarse dentro del reporte sin modificar su fuente. REPORTE_HTML_V4 conserva y verifica esa presentación; V1–V3 permanecen disponibles. Tablas y mapas de calor actuales; representaciones múltiples siguen en el paso 5. Guía y aceptación técnica: [REPORTES_HTML.md](REPORTES_HTML.md#paso-4-crear-y-personalizar-comparaciones-en-el-generador).

## Paso 5 implementado: múltiples representaciones

Botones compactos agrupados en preguntas, cruces guardados y cruces nuevos. Tabla/columnas/barras según tipo, circular/anillo solo para selección única, y mapa de calor/barras agrupadas/apiladas en cruces. Validación en servidor de compatibilidad y representación activa por elemento incluido; tablas equivalentes siempre disponibles. REPORTE_HTML_V5 conserva las selecciones y mantiene V1–V4. Se verifican doce gráficos simultáneos y 24 descargas PNG/SVG, además de PDF por impresión. Las representaciones avanzadas siguen en el paso 6. Guía y evidencia: [REPORTES_HTML.md](REPORTES_HTML.md#paso-5-selección-de-varias-representaciones-compatibles).

## Paso 6 implementado: representaciones avanzadas

Histogramas y cajas numéricas, líneas/áreas de recepción mensual con metadata congelada, dispersión entre dos magnitudes y radar de paneles de cinco criterios de empleadores con escala exacta. Cálculos en Java, tablas equivalentes, bases explícitas y serie incompleta sin relleno. Gráficos avanzados tienen su propio listado/editor con selección, título y comentario. REPORTE_HTML_V6 conserva y verifica la proyección; V1–V5 permanecen compatibles. Sin migración adicional. Guía, reglas y aceptación técnica: [REPORTES_HTML.md](REPORTES_HTML.md#paso-6-gráficos-avanzados-desde-fuentes-congeladas).


## Paso 7 implementado: plantilla opcional y exportaciones

El generador permite marcar «Guardar también como plantilla» y escribir un nombre opcional; vacío usa el título del reporte. El guardado de reporte y plantilla comparte transacción. Sin marcar la casilla no se crea ninguna plantilla. Desde el mismo generador se aplica una plantilla guardada y se revisa antes de generar.

Se conservan selección de preguntas, títulos, introducción, representaciones simultáneas, opciones generales, recetas de cruces y gráficos avanzados. No se guardan respuestas, cifras, identificadores de análisis/conjunto/cruces ni comentarios particulares. La aplicación exige la misma definición congelada del instrumento; incompatibilidades se informan y revierten toda la operación. Las comparaciones se recrean o reutilizan en el corte de destino como exploratorias, sin trasladar evidencia de independencia ni aprobaciones.

V17 añade una tabla independiente de plantillas de presentación; las revisiones anteriores y los formatos V1–V6 conservan su contrato. Ver reglas, alcance y comprobaciones en [REPORTES_HTML.md](REPORTES_HTML.md#paso-7-plantillas-opcionales-y-exportaciones).


## Paso 8 completado: recorrido completo

Verificación HTTP del corte, personalización, generación con plantilla opcional, exportación, recepción de nuevas respuestas y reutilización en otro corte. Los archivos del reporte original permanecen idénticos. Revisión en Chrome de pantallas y descargas a tres anchos; se corrigió la persistencia del aviso de plantilla incompatible. Evidencia, pruebas y límites en [ACEPTACION_GENERADOR_REPORTES.md](ACEPTACION_GENERADOR_REPORTES.md).
