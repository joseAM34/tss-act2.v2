# Espacio por encuesta · Rediseño, paso 2

El paso 3 ya incorpora el [asistente de importación simplificado](ASISTENTE_IMPORTACION.md). Este documento conserva el alcance y la verificación de la entrega del paso 2.

El paso 4 añade [resultados actualizados y guardado de cortes](RESULTADOS_ACTUALIZADOS.md) para versiones del editor; sustituye allí la consulta exclusivamente manual descrita en esta entrega inicial.

Fecha: 15 de octubre de 2026. Implementación autorizada por «paso 2 luz verde», después de la [propuesta visual](REDISENO_EXPERIENCIA.md). Este paso reorganiza el trabajo real por encuesta y versión; la automatización de importación, actualización automática y nuevas exportaciones conservan sus etapas posteriores.

## Cómo usarlo

Inicia o reinicia el monolito con el comando Maven habitual. Para una base H2 de desarrollo:

```powershell
mvn "-Dspring-boot.run.profiles=dev" spring-boot:run
```

1. Inicia sesión y entra a **Panel de gestión** (`/admin`). Ahora muestra **Mis encuestas**. También puede abrirse en `/admin/espacio`.
2. Abre una tarjeta. Verás una encuesta original UMSS o una encuesta creada con el editor, incluidos los casos que solo tienen borrador.
3. Usa **Versión → Abrir** para cambiar de versión manteniendo la sección. Los enlaces conservan encuesta y versión en su URL; dos pestañas pueden trabajar con versiones distintas.
4. En **Versiones**, selecciona una publicación o abre el borrador. Desde una publicación puedes crear/retomar la próxima versión y gestionar publicación, activación y cierre mediante las operaciones existentes.
5. **Crear variante como otra encuesta** copia la estructura y referencia de origen a un borrador independiente. No copia respuestas, análisis ni aprobaciones. Para originales UMSS se copia explícitamente el formulario actual, no una versión histórica sustituyéndola por el catálogo presente.
6. **Respuestas** muestra solo los envíos de la versión elegida y permite entrar en su detalle/revisión.
7. **Resultados** enlaza los conjuntos y análisis guardados de esa versión y abre la preparación ya seleccionada. El análisis conduce a sus gráficos y cruces existentes.
8. **Importar** permite cargar CSV/XLSX directamente para la versión publicada elegida y consultar sus lotes/libros. La selección se conserva al elegir hoja, revisar correspondencias o corregir un error de carga. La simplificación del asistente corresponde al paso 3.
9. **Reportes y plantillas** muestra reportes de esa versión, ofrece generar desde un análisis concreto y permite crear una plantilla con regreso al mismo espacio.

## Organización y contexto

Las secciones son Resumen, Versiones, Respuestas, Resultados, Importar y Reportes y plantillas. Una barra común las mantiene disponibles en editor, detalle/revisión de respuestas, publicaciones, preparación, conjuntos, análisis, cruces, importación y reportes. El contexto de una herramienta se obtiene de su referencia persistida, no de una selección global de sesión. La vista previa de cuestionario conserva su regreso al borrador/publicación correspondiente.

La selección inicial usa versión activa, después publicación más reciente y, si solo hay borrador, ese borrador. No cambia la versión activa por visitar una página. Cambiar la selección no mezcla respuestas ni publica nada.

El resumen cuenta envíos de la versión elegida y su última clasificación de origen, separando reales, sintéticos y sin verificar. Visitar una pantalla no clasifica respuestas, completa snapshots históricos ni modifica conjuntos congelados.

Los listados del espacio filtran por versión en SQL antes de aplicar el límite de 100 entradas. Los cruces del generador se consultan por conjunto antes de limitar; un análisis seleccionado explícitamente permanece en el formulario aunque no esté entre los últimos análisis globales.

## Casos distintos que se muestran expresamente

| Caso | Comportamiento |
| --- | --- |
| Encuesta solo con borrador | Se ofrece editar; no se presentan resultados ni carga de respuestas |
| Publicada sin versión activa | Aparece su publicación más reciente; no se activa automáticamente |
| Cerrada | Conserva respuestas, resultados y archivos; no ofrece carga nueva |
| Versión sin respuestas o sin análisis | Mensaje de estado vacío; no se rellenan cifras analíticas con ceros |
| Cuestionarios originales UMSS | Respuestas y versiones propias; acceso identificado al protocolo del estudio |
| Conjuntos del protocolo UMSS | Pueden incluir encuestas/versiones distintas; no se atribuyen a la versión seleccionada |
| Plantillas compartidas | Se identifican como biblioteca común; no se les asigna pertenencia exclusiva a una encuesta |
| Encuesta o versión inexistente | 404, sin cambiar silenciosamente a otra versión |

El panel anterior permanece en `/admin/herramientas`, accesible desde la biblioteca general de Mis encuestas. Los enlaces públicos y rutas administrativas anteriores siguen funcionando. Las importaciones del protocolo actual requieren publicaciones del editor; no se migran las respuestas originales a variantes nuevas.

## Implementación y límites

`SurveyWorkspaceService` reúne lecturas por encuesta/versión. `SurveyWorkspaceController` sirve listado, espacio y creación de variantes. `SurveyWorkspaceAdvice` añade contexto a herramientas mediante sus referencias persistidas. Plantillas Thymeleaf y CSS local representan datos escapados, con navegación y selector que funcionan sin JavaScript. No se añaden dependencias ni migraciones.

ADMIN, CSRF, BCrypt y validación del servidor se conservan. Las salidas administrativas llevan `Cache-Control: no-store`. La publicación continúa inmutable y las variantes registran un evento con actor y huella usando el esquema existente.

Los resultados son los guardados y conservan su corte. Este paso no implementa recepción de datos en vivo, preparación automática, eliminación de justificaciones, proyecciones, PDF, imágenes ni exportación Excel. El uso de CSV/XLSX reutiliza el importador existente. No se conecta a Neon, cambia configuración privada, realiza commit, push ni despliegue.

## Verificación

- Suite completa: **480 pruebas Java en H2 y 480 en la ejecución con PostgreSQL 18 temporal**, sin fallos ni omisiones. Incluye 14 pruebas nuevas del espacio, autenticación/autorización, CSRF, escapes, separación por versión, última revisión de origen, variantes sin respuestas, contexto de herramientas, errores de importación y regreso desde plantillas.
- **9 pruebas JavaScript** existentes pasaron.
- Chrome verificó las seis secciones con HTML real generado por Thymeleaf desde fixtures ficticias, recursos locales y la CSP del backend, a **360, 768 y 1280 píxeles**: 18 vistas sin desbordamiento de página ni excepciones JavaScript; campos etiquetados y formularios POST con CSRF. No equivale a una auditoría completa de accesibilidad.
- `git diff --check` sin errores. No se alteraron migraciones ni datos del entorno del usuario.

La ejecución PostgreSQL usa una base temporal en loopback, sin Neon ni credenciales privadas. Se fijó `spring.datasource.hikari.minimum-idle=0` únicamente en la orden de pruebas para no agotar el límite de conexiones al mantener varios contextos de Spring; no se cambia la configuración del monolito.
