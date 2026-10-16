# Comprobación del recorrido completo — paso 7

Fecha: 2026-10-16. Estado: recorrido técnico del espacio de encuestas del editor verificado. Las siete etapas del rediseño quedan implementadas y comprobadas dentro del alcance documentado; esto no constituye aceptación con usuarios finales, aprobación institucional ni finalización de la simulación o del documento de Análisis de Mercado.

## Prueba del recorrido

`LiveResultsIntegrationTest.completeHttpJourneyKeepsVersionAndCutFromCreationThroughPublicReceptionImportAndExport` encadena acciones HTTP con Spring MockMvc y persistencia real en la base de pruebas, sin una transacción envolvente que oculte los límites de guardado:

1. Crea una encuesta desde el espacio administrativo, añade sección y pregunta, abre la vista previa y publica v1.
2. Abre el formulario como participante anónimo, conserva sesión/token y envía una respuesta. Repite el envío para comprobar que no se duplica.
3. Configura el tratamiento nominal mediante el formulario de preparación.
4. Sube un CSV, abre sus correspondencias propuestas y verifica que la navegación conserva v1. Acepta las dos casillas de revisión, comprueba la vista previa y confirma el lote dos veces sin duplicarlo.
5. Consulta resultados actuales de v1: hay exactamente dos envíos, uno público y uno importado, con origen sin declarar. Guarda la huella y el corte que recibió la pantalla.
6. Crea una plantilla, genera el reporte desde ese análisis, comprueba contexto y controles PDF, y descarga CSV y Excel.
7. Publica y activa v2. Vuelve explícitamente a respuestas y resultados de v1: conserva sus dos envíos y sus enlaces. Comprueba que el CSV del reporte anterior permanece idéntico y que la descarga Excel exige autenticación.

La prueba nueva pasó tanto en H2 como en la ejecución con PostgreSQL 18 temporal. No se detectó un fallo de producción que requiriera modificar controladores, cálculos o esquema en este paso. El incremento añade la prueba y esta documentación.

## Evidencia complementaria

| Área | Comprobación |
| --- | --- |
| Edición y publicación | Revisiones, validación, versiones congeladas, previsualización, activación/cierre, variantes sin respuestas y enlaces con contexto en las pruebas existentes de editor/publicación/espacio. |
| Recepción e importación | Validación en servidor, tokens/reintentos, consentimiento, estados, orígenes y capturas verificables. Las pruebas específicas de importación cubren además Excel, discrepancias, casillas y conservación de originales. |
| Resultados actualizados | Bases, origen/fechas, selección vacía, actualización sin escrituras, guardado idempotente y concurrente, corte exacto e integridad. Chrome verifica error/recuperación y pausa por filtros pendientes. |
| Estadística y ayudas | Los cálculos siguen en Java. Se mantienen bases, categorías de desconocimiento, diagnóstico de chi-cuadrado y ausencia de p cuando no corresponde. La suite incluye el protocolo original y sus gráficos, además del flujo general. |
| Reportes y exportaciones | Fuentes compatibles y congeladas, huellas, comentarios escapados, CSV/Excel sin fórmulas ejecutables, conservación tras nuevos envíos y rechazo de contenido alterado. Chrome genera PNG/SVG y PDF del reporte de prueba. |
| Acceso | ADMIN para consultas privadas y exportaciones; CSRF en formularios; encuestas públicas. La suite comprueba anónimos, roles insuficientes y acciones sin CSRF. |

## Resultados de la ejecución

- **522 pruebas Java correctas en H2** y **522 correctas en la ejecución con PostgreSQL 18**, sin fallos, errores ni omisiones. Las pruebas especializadas de migración/reinicio que fijan H2 mantienen ese alcance incluso dentro de la ejecución configurada para PostgreSQL.
- **11 pruebas JavaScript correctas** de gráficos, valores ausentes, modos y control de solicitudes concurrentes.
- **51 vistas en Chrome**, con HTML real de Thymeleaf, datos ficticios y recursos locales: 18 del espacio de encuestas, 15 del asistente de importación, 12 de resultados actuales y 6 de reportes. Tamaños 360, 768 y 1280 píxeles; sin desbordamientos de página ni excepciones JavaScript.
- **Diez imágenes**: PNG y SVG de cinco gráficos, con comprobación de contexto/huella y etiquetas escapadas en SVG. **Un PDF de prueba** generado mediante la impresión de Chrome, conservando gráficos y tablas. También se comprobaron plantilla sin gráficos y fallo de carga.
- `git diff --check` correcto. PostgreSQL temporal detenido al finalizar. No se utilizó la base de la aplicación ni Neon; no hubo dependencias, migraciones, commit, push o despliegue nuevos.

Las pruebas de navegador sirven fixtures HTTP locales de HTML/JSON producido por el backend. No equivalen a un recorrido íntegro de clics contra una aplicación desplegada: el encadenamiento de mutaciones y la autorización se comprueban en Java. No se realizó una auditoría completa con lector de pantalla ni una prueba de comprensión con usuarios reales.

La salida PDF usa **Guardar como PDF** del navegador, no un motor PDF del servidor. El recorrido nuevo corresponde a encuestas publicadas desde el editor; los originales UMSS mantienen su adaptador y dashboard del estudio. La aprobación institucional, comparabilidad y evidencia metodológica siguen sus requisitos documentados.

## Reproducción y archivos

Prueba focalizada:

```powershell
mvn -o "-Dmaven.repo.local=.codex-tmp/m2" "-Dtest=LiveResultsIntegrationTest#completeHttpJourneyKeepsVersionAndCutFromCreationThroughPublicReceptionImportAndExport" test
```

Suite H2 usada para regenerar también las fixtures locales:

```powershell
mvn -o "-Dmaven.repo.local=.codex-tmp/m2" "-Dlive.capture=true" "-Dassistant.capture=true" test
```

El repositorio de Maven local de esas órdenes es el caché disponible en esta máquina. Para PostgreSQL se utilizó exclusivamente el clúster temporal de pruebas, con mínimo de conexiones ociosas 0 en la orden de Maven. Las pruebas vacían sus tablas: no deben apuntarse a una base con datos que se quieran conservar.

Evidencia local excluida del historial:

- `.codex-tmp/journey-targeted.log`, `journey-full-h2.log`, `journey-full-pg.log` y `journey-js.log`.
- `.codex-tmp/workspace-browser/verification.json`.
- `.codex-tmp/import-assistant-browser/verification.json`.
- `.codex-tmp/live-browser/verification.json`.
- `.codex-tmp/report-browser/exports-verification.json`, imágenes `export-*` y `export-report.pdf`.

El uso diario empieza en **Mis encuestas → encuesta → versión**. Las [ayudas de resultados](AYUDAS_RESULTADOS.md), el [asistente de importación](ASISTENTE_IMPORTACION.md) y la [guía de exportaciones](EXPORTACIONES_REPORTES.md) describen las acciones disponibles.
