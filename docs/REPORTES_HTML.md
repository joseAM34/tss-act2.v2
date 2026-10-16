# Paso 6 local: plantillas y reportes HTML

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
