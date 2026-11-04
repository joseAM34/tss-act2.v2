# Integración de tss2 en tss-act2.v2

Fecha: 2026-11-04. El usuario autorizó traer a esta copia los cambios que existían en `tss2` y probar el conjunto. Se comparó el ancestro `2848b9d` con `1cd78e0`; las mejoras proceden de `6cb0953` (`add: datos tecnicos`). Se integraron los 35 archivos afectados sin modificar la otra copia ni sus datos locales. El historial y el remoto no se reescribieron.

Se incorporan el detalle de chi-cuadrado (hipótesis, esperadas y residuos), la exportación administrativa de respuestas originales a CSV/Excel, la eliminación confirmada y auditada de análisis/reportes, el perfil descriptivo con moda categórica y diagnóstico de posibles atípicos, los gráficos XY editables en Word, Arial y la reutilización de gráficos de vista previa en memoria.

Se conservan las equivalencias numéricas revisadas por pregunta y sus decimales, el refresco individual, la recuperación local de personalización, la terminología de frecuencias, las tablas numéricas con fórmulas y los cambios de orientación para tablas grandes en Word/PDF.

Los perfiles de ambas copias tienen contratos diferentes. Se conservan sus marcadores históricos: `univariate_projection_version` y `descriptive_profile`. Abrir un informe anterior no añade proyecciones que no estuvieran congeladas. Para tablas numéricas nuevas se prioriza la presentación con fórmulas y se añade el diagnóstico de atípicos sin repetir las tablas de medidas, valores e intervalos. Las reglas de los protocolos almacenados no se cambian. Los gráficos aceptan las estructuras de ambos protocolos; Word utiliza XY con líneas y datos editables. Las modas categóricas mantienen sus propias bases y No sabe separado.

Las equivalencias numéricas se consultan en lote junto con los contextos de diccionarios: un lote de hasta 200 preguntas requiere una consulta temática, una numérica y, cuando corresponde, una global. Esto conserva un número acotado de lecturas y evita una consulta por pregunta.

La exportación de respuestas originales sigue separada de las exportaciones analíticas y exige confirmación de contenido personal, ADMIN y CSRF. El borrado de análisis conserva las respuestas y el conjunto preparado. La integración no accede a bases de campo ni cambia credenciales, archivos locales privados, dependencias o migraciones aplicadas.

La copia previa de los archivos afectados quedó en `.codex-tmp/integration-tss2-before`, ignorada por Git. No se hicieron commit, push ni despliegue.

## Verificación de la integración

La suite completa final pasó 685 pruebas Java, sin fallos, errores ni omisiones, y 51 pruebas JavaScript. La primera ejecución detectó la consulta por pregunta de reglas numéricas y una expectativa del formulario anterior de plantillas; se corrigió el acceso en lote y se actualizó esa expectativa al selector vigente de versión. La suite completa se volvió a ejecutar después de las correcciones.

Chrome verificó cantidades y refresco individual (incluido vaciado y rehabilitación), recuperación tras HTTP 503 y recarga, conservación de la copia ante cuota agotada, tablas y gráficos numéricos del backend y PDF de páginas mixtas verticales/horizontales. Los datos son ficticios. Los gráficos XY de Word, el detalle de chi-cuadrado, las exportaciones CSV/Excel, los permisos, CSRF, el borrado de análisis y la lectura de informes históricos están cubiertos por pruebas automatizadas. No se realizó una revisión visual nueva en Microsoft Word ni un despliegue.
