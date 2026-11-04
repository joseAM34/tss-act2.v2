# Recuperación local de la personalización de reportes

Fecha: 2026-10-27. Implementada a solicitud del usuario ante pérdidas de edición por fallos del servidor.

El personalizador de reportes y plantillas respalda automáticamente en `localStorage` los títulos, introducción, comentarios, selección de preguntas, representaciones, comparaciones y gráficos avanzados. También conserva el nombre de plantilla y la comparación que todavía se está preparando. El estado del respaldo aparece en el editor; no depende de que funcione la vista previa.

Al recargar se recupera la copia del mismo navegador, cuenta administrativa, análisis congelado, corte y plantilla/revisión. Tras el primer respaldo, la dirección del navegador pasa a `/admin/reportes/recuperar` con referencias a esa fuente. Esa dirección permite reabrir el editor aunque se haya perdido la sesión de edición del servidor; el acceso sigue requiriendo ADMIN e inicio de sesión. No se crea otro corte ni se sustituye la fuente por respuestas nuevas. Conviene conservar esa pestaña o su dirección para volver al trabajo pendiente.

La recuperación de una plantilla existente comprueba su revisión actual. Una revisión modificada por otra operación produce conflicto y no sobrescribe la plantilla; su copia local permanece separada. Sin una copia recuperable, la dirección de recuperación devuelve al selector de fuente, evitando guardar una plantilla vacía por accidente.

## Qué se guarda y cuándo se elimina

- Solo configuración y textos administrativos, identificadores y etiquetas de comparaciones. No se persisten respuestas originales, resultados, cifras, HTML de vista previa, credenciales, CSRF ni huellas que habiliten generar.
- La copia caduca a los siete días desde el último respaldo; se descarta al intentar abrirla después del plazo. El navegador también puede eliminarla al borrar datos del sitio.
- Después de guardar o generar correctamente en el servidor se elimina la copia correspondiente. Un error de red, sesión o validación no la elimina.
- «Descartar cambios locales y reiniciar» elimina esa copia y recarga. No elimina reportes ni plantillas de la base de datos.
- Si otra pestaña modificó la misma copia, el editor informa del conflicto y evita sobrescribirla o borrarla. Los cambios de la pestaña en conflicto siguen en memoria y se pueden guardar en el servidor.
- Si el navegador bloquea el almacenamiento o se agota su cuota, el editor avisa y conserva la copia anterior. La edición en memoria continúa.

La copia pertenece al navegador y origen de la aplicación; no se sincroniza con otro equipo, perfil, navegador o dominio. Los textos administrativos se almacenan localmente y no están cifrados por este mecanismo. La aplicación no recupera una copia bajo otra cuenta ni la aplica a otro análisis o versión.

## Verificación y límites

La recuperación no convierte el navegador en fuente de estadísticas. Se descartan vistas previas y autorizaciones anteriores, se vuelve a consultar el backend y solo una vista previa verificada permite generar o guardar. Las advertencias de textos sensibles deben confirmarse nuevamente; sus representaciones sensibles se desactivan al recuperar. Se mantienen autenticación, autorización, CSRF, protocolo y huellas en el backend.

Verificación: seis pruebas JavaScript cubren recuperación, separación de contextos, exclusión de datos y tokens, caducidad, corrupción, cuota y concurrencia entre pestañas. Pasaron las 102 pruebas de integración de `ReportTemplateManagementIntegrationTest`, `GenericPreparationIntegrationTest` y `WorkspaceResultsIntegrationTest`; incluyen recuperación sin sesión de edición, revisión de plantilla, ADMIN y rechazo de otros roles. La comprobación en Chrome utiliza fixtures y HTTP 503 simulado: edición de textos y gráficos, recarga en la dirección de recuperación, conservación del nombre de plantilla y comentarios, generación bloqueada sin vista previa, cuota agotada sin reemplazar la copia anterior y descarte explícito.

También se corrige una referencia a variables de comparaciones fuera de su ámbito en el listado de gráficos avanzados, que podía interrumpir el editor al abrir gráficos existentes. No se modifica el motor estadístico ni se atribuyen a este cambio mejoras de disponibilidad del servidor. Sin migraciones ni cambios en datos de campo.
