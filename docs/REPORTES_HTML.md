# Plantillas, generador y reportes HTML

Recorrido vigente: [reporte directo (paso 2)](#paso-2-del-nuevo-generador-reporte-directo) y [personalización con vista previa (paso 3)](#paso-3-personalización-y-vista-previa-conectadas-al-backend). También está disponible la [creación y personalización de comparaciones (paso 4)](#paso-4-crear-y-personalizar-comparaciones-en-el-generador). También está disponible la [selección de varias representaciones (paso 5)](#paso-5-selección-de-varias-representaciones-compatibles). También están disponibles los [gráficos avanzados (paso 6)](#paso-6-gráficos-avanzados-desde-fuentes-congeladas). También están disponibles las [plantillas opcionales (paso 7)](#paso-7-plantillas-opcionales-y-exportaciones). El [recorrido completo está verificado (paso 8)](ACEPTACION_GENERADOR_REPORTES.md). El siguiente apartado conserva el flujo de plantillas original.

## Entrega inicial de plantillas y reportes

Fecha: 2026-10-14. El usuario autorizó «luz verde» después de revisar el siguiente paso. La entrega continúa los análisis generales y conserva el trabajo local antes de commit, push o despliegue.

## Cómo probarlo

Detén la aplicación con **Ctrl+C** y, desde la raíz, ejecuta:

```powershell
mvn "-Dspring-boot.run.profiles=dev" spring-boot:run
```

`dev` utiliza H2 local. Para este recorrido no combines `local` si está configurado para conectar a Neon. Flyway aplica V13 al arrancar; no cambia credenciales o configuración.

1. Panel → **Reportes y plantillas** → **Crear plantilla**.
2. Escribe título e introducción. Elige el orden de las secciones `CONTEXTO`, `DESCRIPTIVOS`, `CRUCES` y `COMENTARIOS`. Puedes omitir secciones, pero no repetirlas.
3. Decide si mostrar gráficos junto a las tablas y pulsa **Guardar nueva revisión**.
4. Pulsa **Generar reporte**, elige un análisis descriptivo general guardado y pulsa **Abrir sus fuentes**. También puedes abrir un análisis y pulsar **Generar reporte de este análisis**.
5. Selecciona la revisión exacta de plantilla. Marca los cruces que incluirás, hasta diez, y añade comentarios si quieres.
6. Pulsa **Generar reporte HTML**. Se muestra el documento protegido con sus fuentes, tablas, gráficos y huella.
7. Para cambiar la presentación, vuelve a **Reportes y plantillas** → **Editar última revisión**. Guardar crea otra revisión; la anterior sigue disponible mediante **Usar esta revisión**.
8. Para cambiar comentarios o selección de cruces, abre el reporte y pulsa **Generar otro con estas fuentes**. El formulario conserva análisis, plantilla, cruces y comentarios para que puedas ajustarlos; guardar genera otra instancia y conserva el reporte original.

## Qué puede editarse

La plantilla contiene título, introducción, secciones, orden y opción de gráficos. Son reglas declarativas de presentación; no admite HTML ejecutable, JavaScript, SQL o plantillas arbitrarias. Los comentarios se escriben por reporte y se muestran como texto administrativo separado de los resultados.

Las cifras, porcentajes, bases, pruebas y decisiones proceden del análisis/cruces guardados. No hay campos para editarlos manualmente. Cambiar datos exige preparar otro conjunto y generar otro análisis.

Los cruces deben pertenecer al mismo conjunto exacto que el análisis, incluso si otro conjunto tiene contenido equivalente. No se mezclan encuestas/versiones, orígenes, períodos o selecciones silenciosamente. Un cruce seleccionado exige sección `CRUCES`; un comentario no vacío exige `COMENTARIOS`. Las incompatibilidades se muestran antes de guardar, sin ocultar contenido solicitado.

## Conservación y acceso

Cada reporte conserva formato `REPORTE_HTML_V1`, revisión/huella/definición de plantilla, análisis y huellas, cruces seleccionados en su orden con especificación y huellas, comentarios, conjunto, estudio/estado observado, versión, configuración, origen, fechas y corte. Actor y fecha de generación quedan en la fila del reporte.

V13 añade `report_template`, `report_template_revision`, `generated_report` y `generated_report_cross`. Las claves foráneas conservan las referencias. No modifica V1–V12 ni datos existentes. Editar una plantilla agrega una revisión y usa control de concurrencia; nunca sobrescribe revisiones usadas. Generar otro reporte crea una instancia distinta; no declara una revisión jerárquica del documento anterior.

El contenido lógico completo se congela en JSON y se verifica con SHA-256. Al abrir un reporte se comprueban su huella, revisión de plantilla, análisis, cruces y conjunto fuente. Se representa la copia congelada; la comprobación no la reemplaza con datos actuales. Recibir nuevas respuestas, cambiar origen o activar otra versión no altera el reporte anterior.

Entradas iguales —misma revisión, análisis, cruces ordenados y comentario— producen la misma huella lógica, aunque ID, actor y fecha técnica de generación difieran. No se promete igualdad byte a byte del HTML renderizado o de gráficos; el formato lógico, sus referencias y cifras son el contrato conservado.

Todas las pantallas y el JSON del contenido requieren ADMIN; las operaciones POST requieren CSRF y las salidas privadas no se almacenan en caché. Los textos se escapan, incluidos título, comentarios y evidencia. No se copian contactos ni respuestas de texto originales desde las capturas; evita introducirlos en textos administrativos. Esto no garantiza anonimización de las categorías ni de los comentarios.

Los gráficos usan ECharts local y las cifras del reporte congelado. Se conservan tablas/resúmenes equivalentes; el navegador no recalcula estadísticas. Selección múltiple mantiene bases distintas de participantes/menciones y no asigna puntaje a `NO_SABE`. Los cruces suspendidos conservan p/decisión/V ausentes y su motivo, sin afirmar ausencia de asociación. Aprobación institucional, independencia, elegibilidad y alcance poblacional no se conceden por generar un documento.

## Alcance y archivos

Esta primera entrega admite análisis y cruces **generales** de las etapas anteriores. No incorpora el protocolo legado UMSS como otra fuente de reporte, comparaciones entre conjuntos ni filtros nuevos sobre resultados. Tampoco convierte el reporte en el documento final institucional de Análisis de Mercado.

- `ReportService`: revisiones, compatibilidad, conservación y verificación.
- `ReportController`: editor, selección de fuentes, generación y consulta protegidas.
- `reports.html`, `report-template-editor.html`, `report-generator.html`, `generated-report.html` y `fragments/report-results.html`: presentación.
- `generated-report.js`: representación de gráficos desde el contenido verificado.

El HTML se consulta dentro de la aplicación autenticada; `contenido.json` permite consultar el contenido congelado y sus referencias. Exportación HTML autónoma, PDF, Excel, exportación gráfica y editor de estilos libres siguen pendientes. No se instalaron dependencias nuevas ni se creó otro frontend.

## Verificación

La suite completa pasó **434 pruebas Java en H2 y 434 en la ejecución con PostgreSQL 18 local**, sin fallos ni omisiones. Se usaron datos ficticios y una base temporal; no se conectó a Neon ni se realizó commit, push o despliegue.

Se probaron revisiones inmutables, ediciones simultáneas y obsoletas, plantillas inválidas, dos presentaciones del mismo análisis, reproducción lógica, conservación ante cambios posteriores, compatibilidad exacta de conjuntos, rechazo de selecciones repetidas o contenido oculto, huellas, ADMIN/CSRF, escapes y ausencia de contactos originales. La prueba de persistencia en archivo recupera el reporte después de cerrar y reabrir la base.

Chrome comprobó listado, editor, generador y reporte con HTML real y datos ficticios a 360, 768 y 1280 píxeles, incluyendo cinco gráficos reales, etiquetas y CSRF, sin desbordamiento ni excepciones JavaScript. No constituye una auditoría completa de accesibilidad.

## Actualización: exportaciones del paso 6

Los formatos que esta entrega inicial dejaba pendientes ya incluyen PDF mediante el diálogo de impresión del navegador, PNG/SVG y CSV/Excel de resultados generales. Consulta [EXPORTACIONES_REPORTES.md](EXPORTACIONES_REPORTES.md) para el contrato y las comprobaciones actuales. El HTML autónomo y el PDF generado directamente por el servidor siguen fuera de este incremento.

## Propuesta posterior: generador con personalización integrada

[GENERADOR_REPORTES_PROPUESTA.md](GENERADOR_REPORTES_PROPUESTA.md) contiene el nuevo recorrido y un prototipo visual independiente: tablas predeterminadas, vista previa, comparaciones dentro de Personalizar y plantilla opcional. Por ahora es una maqueta; no sustituye el formulario funcional descrito en esta guía.

## Paso 2 del nuevo generador: reporte directo

Implementado el 2026-10-17, autorizado con «luz verde paso 2».

1. En **Encuesta → versión → Resultados**, aplica los filtros y revisa el corte.
2. Pulsa **Generar reporte de este corte**. Se conserva el conjunto y análisis exactos mediante el guardado verificado existente. Si hay un cruce seleccionado, queda disponible y marcado en el formulario.
3. Ajusta el título si quieres, selecciona los cruces guardados del mismo conjunto y añade un comentario opcional.
4. Pulsa **Generar reporte HTML**. No necesitas crear ni elegir una plantilla. La presentación predeterminada contiene contexto, descriptivos con tablas/gráficos, cruces y comentarios.

También puedes entrar desde un análisis guardado o desde Reportes. **Usar una plantilla guardada** permanece como opción plegable; si eliges una revisión, se utilizan su título y presentación. **Generar otro con estas fuentes** conserva el análisis, selección de cruces, título directo y comentario, creando una nueva instancia.

La migración **V16** permite que un reporte no tenga revisión de plantilla; conserva la clave foránea y todas las referencias anteriores. Los reportes directos usan `REPORTE_HTML_V2`, con presentación, huella, análisis, cruces y contexto congelados en su propio contenido. No crean plantillas ocultas. Los reportes con revisión continúan usando `REPORTE_HTML_V1`, sin cambiar su contenido ni huella. Las consultas verifican ambas variantes. La fecha del reporte es distinta del corte de sus fuentes; recibir nuevos envíos no amplía ese corte.

ADMIN, CSRF, texto escapado y `no-store` siguen vigentes. Se verificaron JSON, CSV/Excel y representación con ECharts; PDF mediante impresión y descarga gráfica mantienen el recorrido existente. El guardado de fuentes se hace antes de abrir el formulario, incluso si después cancelas la generación. No se genera un reporte ni se guardan fuentes mediante GET.

Verificación: **526 pruebas Java en H2** correctas; **79 pruebas de reportes, preparación, resultados y exportación en PostgreSQL 18 local** correctas, aplicando V1–V16 en una base aislada. Dos pruebas JavaScript de coordinación correctas. Chrome comprobó seis vistas del formulario/reporte real y doce de Resultados a 360/768/1280 píxeles, sin desbordamientos ni excepciones; comprobó guardar hash/corte/CSRF y abrir el generador con análisis y cruce. La prueba de conservación añadió respuestas después del corte y comprobó cifras y huellas originales.

La personalización visual completa, vistas previas configurables, múltiples representaciones y comentarios por pregunta siguen en los pasos posteriores del nuevo plan. Este incremento conecta la generación directa con el formulario funcional actual. Sin conexión a Neon, commit, push o despliegue.

## Paso 3: personalización y vista previa conectadas al backend

Implementado el 2026-10-17, autorizado con «paso 3 luz verde». Al abrir un reporte directo desde Resultados o un análisis, el generador muestra los datos a la izquierda y el reporte a la derecha. **Personalizar** abre una pantalla aparte con preguntas, comparaciones guardadas y presentación. El flujo antiguo de revisiones de plantilla sigue disponible.

- **Preguntas:** casillas de inclusión independientes de la selección de vista previa. Al entrar desde Personalizar no se preselecciona una pregunta. Pulsar un nombre muestra solo sus resultados; repetir el clic limpia la vista. **Editar presentación** abre el editor de título y comentario opcional. Volver conserva los cambios, sin cambiar el instrumento ni las cifras. Los enunciados originales admiten hasta 2000 caracteres; se conserva el original en el contenido fuente.
- **Presentación:** título, introducción, contexto ampliado, comentario general y activación de tablas/gráficos. Aunque se omita el contexto ampliado, versión, origen y corte siguen visibles. Cuando se ocultan tablas, se conservan como equivalentes desplegables. Las columnas verticales siguen siendo la representación actual.
- **Comparaciones:** selección de cruces guardados del mismo conjunto exacto, con vista previa aislada, incluso antes de incluirlos. Crear cruces y personalizar sus nombres dentro del generador corresponde al paso 4; botones de representaciones múltiples y tipos de gráficos corresponden al paso 5.
- **Desplazamiento:** paneles de vista previa con scroll vertical propio y acceso por teclado. Solo se dibujan los gráficos de la pantalla visible; se liberan al navegar.

La vista previa usa `POST /admin/reportes/personalizar/vista-previa` con ADMIN, CSRF y `no-store`. Es una consulta sin persistencia: reconstruye la presentación desde análisis y cruces congelados y verificados, nunca desde cifras enviadas por el navegador. Devuelve HTML escapado generado con el mismo fragmento Thymeleaf que el reporte final, datos para ECharts y huella del contenido. La selección de pregunta/cruce no modifica la inclusión. La vista previa de elementos excluidos usa un catálogo de presentación separado, que no se convierte automáticamente en contenido del reporte.

Cada cambio invalida la confirmación anterior y bloquea **Generar** hasta recibir la vista previa correcta. Respuestas antiguas de consultas se descartan y los errores mantienen la generación bloqueada. Generar reconstruye el contenido en servidor y exige coincidencia con la huella revisada. Se rechazan preguntas ajenas/repetidas, selecciones sin contenido, preguntas incluidas sin representación y cruces de otro conjunto.

Los nuevos reportes personalizados conservan `REPORTE_HTML_V3`: configuración, títulos/comentarios, selección, resultado de presentación, fuentes originales, corte y huellas. Al consultar se comprueba la presentación contra la fuente y la configuración, además de las verificaciones anteriores. **Generar otro con estas fuentes** recupera la configuración y crea otra instancia. V1 y V2 se conservan sin reescribirlos. Este paso no requiere otra migración: usa el almacenamiento JSON y V16 del paso anterior.

CSV/Excel respetan la selección y los títulos personalizados; el JSON protegido conserva además las fuentes originales para trazabilidad. El guardado opcional de una plantilla y las nuevas representaciones siguen en sus incrementos posteriores.

Verificación: **530 pruebas Java en H2**, sin fallos ni omisiones; **83 pruebas focalizadas en PostgreSQL 18 local**, correctas. Cubren coincidencia entre vista previa y contenido guardado, ausencia de escrituras al previsualizar, cambios posteriores de respuestas, selección/títulos/comentarios, validación, huellas alteradas, ADMIN/CSRF, textos escapados, reapertura y compatibilidad con V1/V2. Chrome comprobó **24 vistas** a 360/768/1280 píxeles, sin desbordamientos ni excepciones, incluyendo navegación, selección/deselección, comentario seguro, error de vista previa, recuperación y envío de la configuración con huella/CSRF. Las comprobaciones visuales usan HTML y respuestas de vista previa producidos por Java con datos ficticios; la generación/persistencia real se verifica mediante HTTP en las pruebas de integración.

La evidencia temporal está en `.codex-tmp/custom-report-browser/` y los logs locales excluidos de Git. Sin conexión a Neon, commit, push o despliegue.

Comprobación adicional de compatibilidad visual: Chrome verificó seis vistas de reportes existentes, diez descargas gráficas y PDF por impresión (211080 bytes en la ejecución de prueba), sin excepciones. Se conserva el módulo común de exportación; las vistas previas no ofrecen descargas de un documento aún no generado.

## Paso 4: crear y personalizar comparaciones en el generador

Implementado el 2026-10-17, autorizado con «luz verde» después de entregar el paso 3.

1. En el generador, abre **Personalizar → Comparaciones guardadas**.
2. Para usar un cruce existente, marca **Incluir**. Pulsar su nombre muestra solo ese cruce; **Editar presentación** abre otra pantalla para cambiar su nombre y añadir un comentario opcional. Volver conserva la selección y los cambios.
3. Para crear otro, pulsa **Crear comparación**. Los grupos desplegables proceden de las secciones del instrumento congelado. Marca dos preguntas: la primera define filas y la segunda columnas. Seleccionadas en azul, compatibles en verde e incompatibles en gris, con explicación. Las preguntas no incluidas en los descriptivos del reporte pueden participar si pertenecen al mismo conjunto y son compatibles.
4. Revisa la tabla, mapa de calor y diagnóstico calculados en servidor. Puedes escribir un nombre y comentario opcional. **Añadir al reporte** se habilita tras verificar la vista previa.
5. Al añadir se guarda o reutiliza el cruce fuente y queda marcado en el listado. El reporte admite hasta diez cruces. Los nombres y comentarios son propios de este reporte y se congelan al generarlo; no sobrescriben el cruce fuente ni otros reportes.

Los cruces nuevos de esta pantalla son exploratorios: preguntas nominales de selección única, distintas y del conjunto exacto, hasta 2500 celdas. No se cruzan directamente números ni selección múltiple. No se declara independencia automáticamente ni se pide una justificación para crear una tabla descriptiva. Chi-cuadrado conserva el diagnóstico del motor y no ejecuta la prueba cuando no corresponde. Los cruces ya guardados mantienen su especificación y diagnóstico originales.

`ReportCrossComposer` calcula desde el bundle congelado del análisis. La consulta `POST /admin/reportes/personalizar/cruces/vista-previa` no persiste. `POST /admin/reportes/personalizar/cruces` exige la huella revisada; bloquea el conjunto durante el guardado y reutiliza un cruce con especificación/resultado idénticos. Dos solicitudes simultáneas no crean duplicados. Invertir filas/columnas sigue siendo una especificación distinta. Añadir un cruce sí conserva esa fuente aunque después se abandone el reporte.

Los nuevos reportes con configuración de cruces usan `REPORTE_HTML_V4`: conservan fuentes originales, nombres/comentarios por ID, presentación derivada y huellas verificadas. Se valida que incluso los ajustes de cruces no incluidos pertenezcan al conjunto del análisis. V1, V2 y V3 permanecen compatibles. No se requiere otra migración. Al generar otro con estas fuentes se recuperan nombres y comentarios, y se incluyen referencias usadas aunque ya no estén entre los cien cruces recientes.

La vista previa y el reporte usan los mismos fragmentos Thymeleaf con contexto web, por lo que también funcionan los enlaces protegidos cuando se muestra contexto ampliado o un cruce. Los textos se escapan; los datos siguen sujetos a ADMIN, CSRF y `no-store`. CSV/Excel usan los nombres y comentarios del reporte conservando referencias, cifras y diagnósticos. Las imágenes de cruces usan el título de presentación elegido.

Verificación: **534 pruebas Java en H2**, sin fallos ni omisiones, y **87 pruebas focalizadas en PostgreSQL 18 local**, correctas. Incluyen compatibilidad, grupos del instrumento, vista previa sin escrituras, rechazo de huella incorrecta, creación simultánea sin duplicados, nombres/comentarios escapados, fuentes intactas, reapertura, exportación y detección de presentación alterada, además de ADMIN/CSRF. Chrome comprobó **21 vistas** a 360/768/1280 píxeles, sin desbordamientos ni excepciones: listado inicialmente vacío de vista previa, creación, compatibilidad dinámica, previsualización, inclusión sin duplicar fila, edición y recuperación al volver, y envío del reporte con su configuración/huella. Las pruebas visuales usan HTML y respuestas producidos por Java con datos ficticios; las operaciones reales se verifican en integración.

La selección de varias representaciones y nuevos tipos de gráficos continúa en el paso 5. Sin conexión a Neon, commit, push o despliegue. PostgreSQL temporal se detiene al terminar; evidencia local excluida de Git en `.codex-tmp/report-step4-browser/` y logs de pruebas.

## Paso 5: selección de varias representaciones compatibles

Implementado el 2026-10-17, autorizado con «luz verde» después de entregar el paso 4. En **Personalizar → Preguntas → Editar presentación** y **Comparaciones → Editar presentación**, los botones compactos permiten activar varias representaciones a la vez. El editor de cruce nuevo ofrece los mismos botones. Las categorías se acomodan por filas, con icono y nombre; seleccionadas con color/marca/brillo, disponibles sin resaltar e incompatibles en gris. Los motivos están en el título del botón y en un desplegable por categoría. Las opciones globales de tablas/gráficos siguen aplicándose al reporte.

| Datos | Representaciones de esta entrega |
| --- | --- |
| Categorías de selección única | Tabla, columnas verticales, barras horizontales, circular y anillo. |
| Selección múltiple | Tabla, columnas y barras de conteos por opción; mantiene bases de envíos/menciones. No admite circular/anillo porque las categorías no son exclusivas. |
| Magnitud numérica | Tabla/resumen y columnas/barras del mínimo, mediana y máximo, con unidad. No se presentan como histograma ni distribución de frecuencias. |
| Cruce nominal de selección única | Tabla, mapa de calor, barras agrupadas y apiladas de conteos absolutos. La selección visual no cambia su diagnóstico estadístico. |

Las preguntas comienzan con tabla y columnas; los cruces con tabla y mapa de calor. Es posible activar todas las compatibles o dejar solo tablas. Al ocultar la tabla principal, sigue disponible como equivalente desplegable. Con base cero se conservan tablas/estados sin dibujar gráficos de resultados inexistentes. Cada representación añade su propia imagen y encabezado al documento, sin duplicar observaciones ni recalcular porcentajes. `NO_SABE` conserva su categoría.

La compatibilidad y la selección se validan en Java. Se rechazan representaciones desconocidas, repetidas, incompatibles o elementos incluidos sin una representación activa. Las elecciones de elementos excluidos pueden quedar vacías; consultarlos no los incluye automáticamente. Líneas, áreas, histogramas, cajas, dispersión y radar permanecen en gris con sus condiciones, para el paso 6. La presencia de un radar en otro dashboard no lo convierte en una opción universal del reporte general.

Los reportes con esta selección conservan `REPORTE_HTML_V5`: listas de representaciones, títulos/comentarios, resultados de presentación, fuentes y huellas. Al abrir se verifica la selección contra los tipos de la fuente y se reconstruye la presentación para comprobar su huella. V1–V4 mantienen sus reglas y defaults históricos; no se reescriben. **Generar otro con estas fuentes** recupera las listas. No requiere migración adicional a V16 del paso 2.

La vista previa y el reporte generan los contenedores desde Thymeleaf y usan un renderer común de ECharts. El navegador representa conteos/resúmenes preparados; no calcula estadísticas ni pruebas. El mapa de calor, agrupado y apilado usan la misma matriz observada. Circular/anillo muestran conteos y sus bases; los porcentajes exactos siguen en las tablas del backend.

Los gráficos elegidos pueden descargarse como PNG/SVG con título de representación, contexto, corte y huellas. Se adapta el exportador para gráficos sin ejes, barras horizontales y leyendas de series, manteniendo límites de tamaño y equivalentes CSV/Excel. PDF conserva la impresión del reporte guardado; no se añade un generador PDF en servidor. CSV/Excel conservan una sola copia de las cifras por pregunta/cruce.

Verificación: **538 pruebas Java en H2**, sin fallos ni omisiones, y **91 focalizadas en PostgreSQL 18 local**, correctas. Cuatro pruebas JavaScript nuevas verifican orientación, conservación de ceros/conteos/unidades, circular/anillo sin porcentajes fabricados y correspondencia de agrupadas/apiladas con la matriz. Chrome comprobó **24 vistas** del generador/reporte a 360/768/1280 píxeles, con doce gráficos simultáneos, controles compatibles, selección múltiple, solo tablas, rechazo de selección vacía y recuperación; sin desbordamientos ni excepciones. Otras seis vistas comprobaron reportes con/sin gráficos, **24 descargas PNG/SVG** y PDF de impresión (267600 bytes en la ejecución), sin excepciones. La evidencia visual usa HTML/respuestas generados por Java con datos ficticios; la persistencia, validación, huellas y ADMIN/CSRF se verifican en integración.

Sin dependencias nuevas, conexión a Neon, commit, push o despliegue. Evidencia local excluida de Git en `.codex-tmp/report-step5-browser/` y logs de pruebas. El siguiente incremento es el paso 6: representaciones avanzadas con reglas y datos adecuados.

## Paso 6: gráficos avanzados desde fuentes congeladas

Implementado el 2026-10-17, autorizado con «luz verde» después de entregar el paso 5. **Preguntas → Editar presentación** habilita histograma, caja, línea y área cuando hay datos y metadata compatibles. **Personalizar → Gráficos avanzados** permite añadir dispersión entre dos magnitudes y los paneles compatibles de radar de empleadores. También hay accesos desde los editores de preguntas y cruces. Cada gráfico avanzado tiene casilla de inclusión, selección de vista previa, título y comentario opcional en otra pantalla; volver conserva la configuración. Volver a añadir el mismo par/panel reutiliza la entrada de configuración.

| Representación | Regla y lectura |
| --- | --- |
| Histograma | Magnitud numérica con al menos un valor. Sturges: techo(1 + log2(n)), hasta 20 intervalos de igual anchura. [inferior, superior), excepto el último cerrado. Si todos los valores coinciden, un intervalo puntual. Conteos absolutos, no densidad; límites y frecuencias en tabla. |
| Caja | Al menos dos valores numéricos. Cuartiles tipo 7 mediante el motor existente; bigotes mínimo/máximo. No clasifica atípicos ni inventa dispersión a partir de un valor. Tabla con los cinco valores y unidad. |
| Línea y área | Envíos **con valor por mes de recepción**, America/La_Paz, desde metadata congelada completa. No representan la evolución del valor de una respuesta ni una predicción. Incluyen meses con cero dentro del período verificado; los extremos pueden ser parciales por filtros/corte. Si falta metadata de recepción en históricos, quedan deshabilitadas; no se completa desde registros actuales. |
| Dispersión | Dos magnitudes distintas del mismo conjunto. Solo pares con ambos valores, con unidades propias de X/Y. Coordenadas iguales agrupadas con frecuencia, sin eliminar envíos. Muestra pares válidos y excluidos. Sin UUID, contacto, regresión, correlación automática o declaración de independencia. |
| Radar | Paneles disjuntos de criterios 01–05 y 06–10, identificados por sus IDs y la escala nominal exacta de empleadores: total acuerdo, parcial acuerdo, total desacuerdo y No sabe. Por criterio: (total + parcial acuerdo) / respuestas sustantivas. No sabe fuera de esa base, Sin respuesta y No aplica separados. Ejes 0–100%; no es un índice y no completa series incompletas con ceros. Si algún criterio carece de base, conserva su tabla y omite el polígono. |

El radar no es una alternativa para cualquier cruce nominal ni para magnitudes arbitrarias. Mantiene los diez criterios sin duplicarlos en un contraste ni atribuir comparabilidad institucional. Una serie con acuerdo cero y base positiva conserva cero; una base ausente conserva ausencia. La selección de un gráfico no aprueba el estudio, la elegibilidad ni la independencia de envíos.

`ReportAdvancedProjection` recibe únicamente el bundle verificado del análisis. Usa sus valores/estados, unidades, definición y recepción; nunca consulta respuestas actuales ni interpreta un histórico con el catálogo vigente. Los cálculos avanzados se hacen en Java. ECharts dibuja intervalos, cuartiles, meses, coordenadas y porcentajes ya preparados por el backend, sin completar datos ni calcular el protocolo. Las tablas equivalentes se mantienen en pantalla y exportación.

Los reportes que usan estas opciones conservan `REPORTE_HTML_V6`, con reglas/métodos, configuración, resultados avanzados, tablas, corte y huellas. Al consultar se reconstruye la proyección desde la fuente congelada para verificarla. V1–V5 mantienen sus contratos. No requiere migración adicional. Añadir un gráfico avanzado modifica la configuración en el navegador; no crea un análisis o reporte persistido antes de Generar. La generación sigue exigiendo la huella de la vista previa y ADMIN/CSRF; las consultas/salidas usan `no-store`. Una sesión que devuelve HTML en lugar de datos verificados mantiene bloqueada la generación y muestra un mensaje de recuperación.

CSV/Excel incluyen los intervalos, cuartiles, serie mensual y tablas de gráficos avanzados elegidos, conservando bases, unidades y ausencia. PNG/SVG incluyen título, método, contexto, corte y huellas. El exportador admite radar sin ejes cartesianos y mantiene etiquetas completas. PDF utiliza la impresión del documento guardado; no se añade generación de PDF en servidor.

Verificación: **545 pruebas Java en H2**, sin fallos ni omisiones, y **98 pruebas focalizadas en la ejecución con PostgreSQL 18 local**, correctas. Incluyen seis pruebas de proyección para intervalos/valores constantes y negativos, cuartiles tipo 7, recepción en el límite mensual de La Paz, ceros, metadata histórica ausente, pares agregados/excluidos y bases/estados del radar; una integración verifica construcción desde respuestas ficticias, generación V6, fuentes, HTML, CSV, reapertura y detección de proyección alterada. Siete pruebas JavaScript de gráficos correctas (tres nuevas para avanzados). Chrome comprobó **27 vistas** a 360/768/1280 píxeles, sin desbordamientos ni excepciones, con siete gráficos simultáneos, selección/deselección, editores, reutilización de pares/paneles y rechazo/recuperación de sesión vencida. Otras seis vistas verificaron **14 descargas PNG/SVG** y PDF de impresión, con tablas y metadatos. Las pruebas visuales usan HTML/respuestas de Java con datos ficticios; persistencia, cálculos, autenticación e integridad se comprueban en integración.

Sin dependencias nuevas, conexión a Neon, commit, push o despliegue. Evidencia temporal excluida de Git en `.codex-tmp/report-step6-browser/` y logs de pruebas. El paso 7 integrará el guardado opcional de esta configuración como plantilla y completará su recorrido de exportación.


## Paso 7: plantillas opcionales y exportaciones

1. Abrir el generador desde el corte guardado de Resultados.
2. Personalizar preguntas, representaciones, comparaciones, gráficos avanzados y textos; revisar la vista previa.
3. Opcionalmente marcar **Guardar también como plantilla**. El nombre puede quedar vacío: se utiliza el título del reporte.
4. Generar. El reporte se congela con su personalización y la plantilla, si se solicitó, se guarda en la misma transacción.
5. En otro corte compatible, abrir **Aplicar una plantilla personalizada**, escogerla y pulsar **Aplicar y revisar**. Esta acción reemplaza la configuración actual y crea o reutiliza los cruces necesarios en ese corte. Revisar antes de generar.

Las plantillas guardan presentación, no datos ni comentarios de una ejecución. Incluyen introducción, títulos de presentación, preguntas seleccionadas, opciones generales, representaciones, recetas de los cruces incluidos y configuración de gráficos avanzados. No contienen identificadores de análisis, preparación o cruces fuente ni resultados estadísticos. Se exige la misma huella de definición del instrumento. Si el corte no admite una representación (por ejemplo, caja sin suficientes valores), se informa y se revierte la aplicación completa; no se omiten elementos silenciosamente. Los cruces nuevos son exploratorios con alfa 0,05 e independencia sin documentar; una plantilla no transfiere justificaciones ni habilita automáticamente chi-cuadrado.

La tabla `report_presentation_template` de V17 conserva definición y huella verificable. Estas plantillas son independientes de las revisiones de plantillas históricas. Los reportes mantienen formatos V1–V6 y referencias congeladas. Aplicar requiere POST, ADMIN y CSRF; la vista previa sigue sin persistir reportes o plantillas. El nombre admite 200 caracteres y los textos se escapan en pantalla.

Las exportaciones respetan la configuración congelada: HTML con tablas equivalentes, PDF mediante impresión del navegador, PNG/SVG por gráfico y CSV/Excel de resultados agregados. No se añade generación PDF en servidor ni se exportan contactos. El PDF se obtiene desde el documento generado, no desde el editor.

Verificación local: 546 pruebas Java en H2, 99 en PostgreSQL local con V17 aplicada y siete pruebas JavaScript correctas. Chrome comprobó aplicar y guardar plantilla a 360, 768 y 1280 píxeles, sin desbordamiento ni excepciones; otras 27 vistas del generador y seis del documento/exportaciones conservaron siete gráficos simultáneos, 14 descargas PNG/SVG y un PDF real. Las pruebas de integración cubren ausencia de plantillas sin selección, nombre automático, huella revisada, omisión de datos/comentarios, reutilización en otro conjunto, no duplicación de cruces, CSRF e integridad. Evidencia temporal en `.codex-tmp/report-step7-browser/`, excluida de Git. Sin push, despliegue o modificación de Neon en este incremento.
