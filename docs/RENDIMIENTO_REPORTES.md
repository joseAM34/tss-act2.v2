# Lecturas agrupadas en reportes

El aviso «La consulta tardó demasiado» procedía del límite de 20 segundos de la vista previa en `report-customizer.js`; por sí solo no demuestra que PostgreSQL esté desconectado. A petición del usuario, la espera se amplía a **90 segundos** para la vista previa de reportes/plantillas y la preparación de cruces dentro de ese editor. El tiempo incluye consultas, verificación de fuentes, análisis y representación HTML. `/healthz` verifica únicamente que se pueda ejecutar `SELECT 1`, sin revelar credenciales ni datos del estudio.

Se identificaron y corrigieron lecturas repetidas en el análisis de respuestas abiertas:

- `CaptureStore.inputs(ids)` carga los miembros exactos del corte en bloques de hasta 200 identificadores. Reutiliza las verificaciones existentes de captura, definición, publicación, consentimiento y procedencia.
- `CsvImportEvidence.inResponseSnapshot` agrupa recibos y carga, verifica y procesa una vez cada archivo de origen durante esa lectura. Admite miembros de varias versiones. Los archivos y huellas se vuelven a comprobar en lecturas posteriores.
- `ReportTextProjection` utiliza esas capturas verificadas y un índice de miembros; conserva las advertencias y restricciones sensibles y compara cada captura utilizada con su huella congelada.
- `TextDictionaryService.selections` consulta los contextos en lote y obtiene el diccionario global una sola vez por selección, manteniendo las sustituciones privadas por pregunta y el filtro de alias numéricos globales.

Los ámbitos de lectura se liberan en `finally`, incluso ante errores; no hay caché permanente entre solicitudes ni cambios de definición, corte, origen, respuestas o reglas estadísticas. La espera ampliada acompaña estas optimizaciones; no corrige un 502 de infraestructura. La vista previa ofrece **Volver a intentar**, manteniendo la misma configuración y el mismo corte. Cambiar opciones sigue cancelando una petición anterior y salir de la página cancela la carga pendiente.

## Verificación local

Pruebas con datos ficticios y H2: para 20 envíos importados de dos versiones, las llamadas de lectura `JdbcTemplate.queryForList` medidas bajaron de 100 a 6; se verificó igualdad de todas las capturas antes y después. Son recuentos de consultas de esa operación, no tiempos ni una medición del servidor PostgreSQL del usuario. En 40 preguntas, la selección de diccionarios empleó dos consultas y conservó las reglas globales y privadas.

Las pruebas comprueban también capturas alteradas, archivos de origen alterados, huellas congeladas incorrectas y liberación del ámbito tras fallar. Pasaron las tres pruebas de rendimiento, tres de gestión de plantillas/reportes y 21 de resultados. Se verificó la sintaxis JavaScript y el reintento en navegador con un tiempo de espera simulado, misma configuración, CSRF y tamaños de 360 y 1280 píxeles.

Para aplicar los cambios es necesario reiniciar el proceso Java y recargar el navegador. Si persiste la demora, hace falta medir las peticiones del servidor en ejecución; estos resultados locales no establecen la latencia de su conexión real.
