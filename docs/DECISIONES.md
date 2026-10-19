# Decisiones del proyecto

## Generador visual de reportes: prototipo

Fecha: 2026-10-17. El usuario autorizó el paso 1 del nuevo [generador con personalización y vista previa](GENERADOR_REPORTES_PROPUESTA.md). Tras la corrección del usuario, la maqueta separa inicio, Personalizar, listado/editor de preguntas, listado/editor de comparaciones y presentación. Cada editor previsualiza solo su contenido. Cada pregunta tiene tabla y gráfico predeterminados; conserva preguntas agrupadas con casillas, compatibilidad indicada con texto/color, comparaciones nombradas y plantilla opcional. Usa fixtures identificadas y no modifica la aplicación ni Neon. La compatibilidad técnica no concede independencia o aprobación institucional. Se conservan las guías útiles y los prototipos en el repositorio; exportaciones, secretos y evidencia temporal siguen excluidos.

## Rediseño: verificación del recorrido, paso 7

Fecha: 2026-10-16. Autorizado por el usuario con «luz verde» y «sigue con el paso 7». Se incorpora una prueba HTTP encadenada de creación, edición, publicación, recepción pública, importación revisada, guardado y exportación, con reintentos y conservación de v1 tras activar v2. La [aceptación técnica](ACEPTACION_REDISENO.md) registra 522 pruebas Java en H2 y 522 en la ejecución con PostgreSQL temporal, 11 JavaScript y 51 vistas de Chrome. No se cambia el protocolo ni se otorgan aprobaciones institucionales. La verificación con fixtures no sustituye una prueba con usuarios o un despliegue. Sin cambios de esquema, commit, push o despliegue.

## Rediseño: exportaciones, paso 6

Fecha: 2026-10-15. Autorizado por el usuario con «luz verde». Se implementan [exportaciones del reporte general](EXPORTACIONES_REPORTES.md): PDF mediante el diálogo nativo del navegador, PNG/SVG con contexto del mismo corte y CSV/Excel generados en Java desde el reporte congelado verificado. Se mantienen ADMIN, ausencia de caché, fuentes y huellas; no se recalculan estadísticas en el navegador. Sin dependencias o migraciones nuevas. El paso 7 queda pendiente.

## Rediseño de experiencia: resultados actualizados, paso 4

Fecha: 2026-10-15. El usuario autorizó «luz verde» tras el asistente. Se implementa [RESULTADOS_ACTUALIZADOS.md](RESULTADOS_ACTUALIZADOS.md) para versiones del editor con configuración explícita de variables: lectura consistente y verificada cada 30 segundos, orígenes separados, fechas inclusivas en La Paz, gráficos/tablas del mismo cuadro y cruce descriptivo elegido. No se concede independencia estadística ni aprobación institucional automáticamente.

Los GET no crean conjuntos ni análisis. Guardar este corte conserva la selección y fecha vistas, con revisión de huella y serialización por versión para reutilizar cortes idénticos. Recibir nuevos envíos no modifica reportes guardados ni el corte solicitado. Un cambio de configuración o una fuente no verificable impide sustituir silenciosamente el resultado. Los originales UMSS mantienen el protocolo por conjuntos del estudio y su alcance distinto; no se les atribuye actualización automática no implementada. Sin migraciones, commit, push o despliegue.

## Rediseño de experiencia: asistente de importación, paso 3

Fecha: 2026-10-15. El usuario autorizó «luz verde» después del paso 2. Se implementa [ASISTENTE_IMPORTACION.md](ASISTENTE_IMPORTACION.md): propuestas automáticas revisables, lectura inequívoca y discrepancias por fila/pregunta/valor. La instrucción del usuario sustituye la exigencia rutinaria del importador de redactar dos justificaciones por lote: el formulario nuevo registra dos casillas con declaraciones versionadas, actor, fecha, huella y referencia documental opcional.

No se inventa consentimiento de participantes ni aprobación institucional. La aceptación se comprueba en la columna de la fuente, y una revisión sin marcar queda pendiente. Los formatos, metadatos y opciones ambiguos se informan sin atribuirles equivalencia automáticamente. Las lecturas exactas anteriores y sus revisiones/huellas se conservan; no hay cambios de esquema. Los siguientes pasos mantienen resultados actualizados y nuevas exportaciones. Se conserva el trabajo local sin commit, push o despliegue.

## Rediseño de experiencia: espacio por encuesta, paso 2

Fecha: 2026-10-15. Tras la maqueta del paso 1, el usuario autorizó «paso 2 luz verde». Se implementa el espacio real por encuesta y versión, documentado en [ESPACIO_ENCUESTAS.md](ESPACIO_ENCUESTAS.md). El inicio administrativo pasa a Mis encuestas; el panel anterior conserva acceso como herramienta general del estudio.

El contexto se transporta en enlaces y se obtiene de referencias persistidas al abrir herramientas, sin selección global en sesión. Versiones y orígenes se mantienen separados; borradores y publicaciones cerradas muestran las acciones que corresponden a su estado. Crear una variante copia estructura y referencia de origen a otra encuesta sin copiar respuestas ni conceder aprobaciones. Los originales y los conjuntos del protocolo UMSS no se convierten ni se presentan como resultados exclusivos de una versión nueva.

Este paso no elimina evidencia exigida en importaciones ni implementa actualización automática o nuevas exportaciones. Se reutilizan las operaciones existentes con ADMIN, CSRF, escapes, conservación de capturas y salidas sin caché. No hay migraciones ni cambios de datos por visitar las pantallas. Se mantiene el trabajo local sin commit, push ni despliegue.

## Segundo incremento local: vista previa y publicación

Fecha: 2026-10-14. El usuario autorizó «al paso 2 pasemos» después de probar el editor. Se implementan preview administrativa sin capturas, publicación fija, sucesora de la misma encuesta y cierre, conservando la instrucción de trabajar localmente sin push. La [guía](PUBLICACION_ENCUESTAS.md) registra migración V8, separación de aprobación institucional, enlaces informativos sin recepción todavía y verificación. La recepción dinámica, análisis configurables e informes mantienen su alcance posterior; no se modifican respuestas/capturas/huellas históricas ni la configuración privada.

## Incremento local de encuestas dinámicas

Fecha: 2026-10-14. Después de revisar el diagnóstico, el usuario autorizó cambios **primero en local, con push posterior y por pasos**. El alcance inicial es el editor de borradores: crear, copiar, editar y ordenar secciones/preguntas con los tipos existentes; no incluye publicación, nuevas respuestas, análisis genérico, importación o reportes. El [plan](ENCUESTAS_DINAMICAS_PLAN.md) y la [guía del incremento](EDITOR_BORRADORES.md) registran arquitectura, archivos, migración V7, validación y siguientes etapas. No se modifican definiciones, respuestas ni huellas históricas. Las comprobaciones usan H2 y PostgreSQL temporales, sin conectar a Neon ni hacer push/despliegue.

Estado: cuestionarios, preparación de datos, motor estadístico y visualización de fase 5 implementados y verificados técnicamente. El instrumento institucional definitivo, reportes y simulación siguen pendientes. La evidencia y los límites se registran en `docs/ACEPTACION_DATOS.md`, `docs/ACEPTACION_ESTADISTICA.md` y `docs/ACEPTACION_VISUALIZACION.md`.

## Encuesta de empleadores: criterios únicos

Fecha: 2026-10-13.

El usuario indicó: «si no las repitas».

En la sección «Formación y perfil profesional» del PDF, los diez criterios aparecen dos veces entre las páginas 18 y 20. La aplicación mostrará cada criterio una sola vez y almacenará una respuesta por criterio en cada encuesta. Los análisis contarán cada criterio una sola vez.

La siguiente tabla resume los criterios para identificarlos y documentar sus ubicaciones. Al implementar la encuesta se utilizará el texto de su primera aparición en el PDF.

| Criterio resumido | Primera aparición | Repetición que se omitirá |
| --- | --- | --- |
| Confianza en la UMSS como formadora de profesionales | 18 | 19 |
| Correspondencia del título con la formación y competencias | 18 | 19 |
| Consulta regular a empleadores sobre el perfil y competencias | 18 | 19 |
| Conocimiento del perfil profesional y las competencias de egreso | 18 | 19 |
| Coherencia del perfil declarado con el desempeño laboral | 18 | 20 |
| Formación para un desempeño profesional destacado e integral | 18 | 20 |
| Incorporación de la opinión de empleadores en prácticas, pasantías y titulación | 19 | 20 |
| Competencias laborales demostradas por los titulados | 19 | 20 |
| Valores y actitudes profesionales demostrados por los titulados | 19 | 20 |
| Participación del empleador en evaluación o retroalimentación | 19 | 20 |

El PDF original se conserva como fuente documental.

## Acceso a las encuestas y al panel

El usuario indicó que cualquiera puede acceder a las encuestas, y que las respuestas y las demás funciones deben requerir contraseña.

- Las encuestas a titulados y empleadores serán públicas y podrán responderse sin iniciar sesión.
- La consulta de respuestas, estadísticas, reportes, exportaciones y administración requerirá autenticación.
- El backend verificará la autorización de acceso a los datos y operaciones del panel.

## Arquitectura

La aplicación es monolítica con backend, conforme a la solicitud del usuario. Al autorizar el comienzo del desarrollo se adoptó la base técnica recomendada: Java 21, Spring Boot, Thymeleaf, Maven y PostgreSQL. H2 se utiliza para pruebas y desarrollo local.

El primer incremento comprende cuestionarios de borrador, persistencia, autenticación administrativa, consulta de respuestas y exportación CSV. Las propuestas del instrumento se documentan en `docs/INSTRUMENTO.md`.

## Repositorio

El usuario proporcionó `https://github.com/joseAM34/tss-act2.v2.git` como destino remoto. Se utiliza un único repositorio para backend, interfaz y documentación, con `main` como rama principal y `origin` como remoto.

## Inicio de la aplicación

El usuario prefiere configurar e iniciar la aplicación mediante comandos explícitos. El procedimiento principal ejecuta `.\mvnw.cmd "-Dspring-boot.run.profiles=dev" spring-boot:run` tanto en CMD como en PowerShell. `scripts/iniciar.ps1` queda como atajo opcional que solo invoca Maven; no solicita credenciales ni modifica variables de entorno. La configuración de certificados se establece manualmente si resulta necesaria.

## Persistencia del administrador

Fecha: 2026-10-13.

El usuario señaló que el inicio resultaba extraño porque los datos del administrador no se guardaban en la base de datos. La cuenta administrativa pasa a guardarse en la tabla `administrator`, con usuario y hash BCrypt irreversible de la contraseña. Esta cuenta utiliza el rol `ADMIN`. No se guarda la contraseña original ni se proporciona una contraseña predeterminada.

- En desarrollo local, cuando no hay administrador, el login dirige a `/configuracion-inicial`. El formulario permite elegir usuario y confirmar la contraseña, de al menos 12 caracteres y hasta 72 bytes UTF-8. `admin` es una sugerencia editable.
- La creación inicial admite una sola cuenta. Cuando ya existe, se inicia sesión con las credenciales almacenadas y no se permite otra creación inicial.
- La cuenta persiste entre reinicios con la misma base de datos. Las variables anteriores `ADMIN_USERNAME` y `ADMIN_PASSWORD` ya no se utilizan ni se importan: la cuenta anterior estaba en memoria y debe crearse desde el formulario al actualizar.
- H2 se limita a desarrollo local y pruebas. La base de desarrollo y PostgreSQL mantienen cuentas y respuestas independientes.
- El inicio normal con PostgreSQL mantiene públicas las encuestas y deshabilita la configuración inicial web. Para crear la primera cuenta se utiliza explícitamente el perfil `setup`, que conecta a PostgreSQL, escucha en `127.0.0.1` y habilita la configuración únicamente desde la máquina local. Tras crearla se detiene la aplicación y se reinicia sin ese perfil.
- La configuración de la conexión a PostgreSQL continúa mediante variables de entorno. La contraseña de la conexión y la del administrador del panel tienen funciones distintas.

La administración de varias cuentas y la recuperación de contraseñas quedan pendientes de un incremento posterior.

## Fase 2: cuestionarios y criterios de aceptación

Fecha: 2026-10-13.

El usuario autorizó completar la fase 2 («entonces hazlo»). Las instrucciones documentales del archivo de requisitos se incorporan como referencia; la autorización previa para desarrollar sigue vigente. La correspondencia de preguntas, reglas y criterios se registra en `docs/ACEPTACION_CUESTIONARIOS.md`.

El usuario eligió expresamente las siguientes reglas para el nuevo borrador:

- **Materias de poca utilidad:** hasta dos selecciones. La pregunta es opcional. El campo complementario «otra asignatura» no suma al conteo, igual que el complemento de materias útiles; esta última sigue admitiendo exactamente cinco selecciones cuando se responde.
- **Vida profesional y tiempo desempleado:** meses completos para ambos campos. El tiempo desempleado no puede superar la vida profesional; si se informa desempleo debe informarse también vida profesional. Se conservan los ceros como valores válidos y las preguntas siguen siendo opcionales.
- **Datos generales:** edad en años completos; género con Hombre, Mujer, Otro y Prefiero no responder. Ambos son opcionales en este borrador. Los topes de edad 0–120 y tiempo 0–960 meses son límites técnicos provisionales documentados, no catálogos ni límites de elegibilidad tomados del PDF.
- **Competencias:** incorporar el borrador propuesto con seis técnicas (desarrollo de software, bases de datos, redes, seguridad, análisis de datos y gestión de proyectos) y seis transversales (comunicación, resolución de problemas, pensamiento crítico, aprendizaje, trabajo en equipo y ética). Se mide la adecuación percibida de la formación a las demandas del trabajo actual o anterior relacionado con Sistemas, con cinco categorías de Nada adecuada a Muy adecuada y una categoría independiente «No puedo valorar». No se calcula automáticamente una brecha ni una puntuación.
- **Filtro de experiencia:** experiencia relacionada con Sistemas, experiencia solo en otras áreas o ninguna experiencia. Permite responder la trayectoria de quienes hoy están desempleados y excluye preguntas que requieren una experiencia inexistente.

La versión `2026-10-borrador-2` utiliza `meses_vida_profesional`, sin reutilizar el código anterior `anios_vida_profesional` con una unidad diferente. Las respuestas ya guardadas mantienen sus códigos, enunciados y unidades originales. Una eventual conversión analítica deberá ser explícita y trazable.

Los ajustes de obligatoriedad, de los campos condicionales y del enunciado incompleto de concordancia se documentan en la matriz como adaptaciones del borrador. Las competencias y la concordancia utilizan el mismo referente laboral. El acceso público a los formularios y la lista de diez criterios únicos de empleadores conservan las decisiones anteriores.

Los nuevos envíos registran por separado `CON_VALOR`, `SIN_RESPUESTA` y `NO_APLICA` para cada pregunta de su versión. «No sabe» y «No puedo valorar» son respuestas con valor nominal, independientes de una falta de respuesta. La migración no inventa estados de las respuestas históricas.

Tras guardar completamente un envío se muestra su referencia en la sesión del participante. Un reintento del mismo formulario conserva esa referencia. La referencia no permite consultar públicamente las respuestas ni los contactos.

Estas elecciones autorizan el borrador de la aplicación. La aprobación institucional del instrumento, la elegibilidad UMSS, el tratamiento de contactos y las reglas analíticas definitivas siguen registrados para la revisión metodológica.

## Fase 3: modelo de datos y Data Wrangling

Fecha: 2026-10-13.

El usuario autorizó implementar la fase 3. El documento `03_DATOS_Y_DATA_WRANGLING.md` se conserva como fuente de diseño; su instrucción de limitarse a documentación no reemplaza la autorización actual. Las decisiones técnicas y su correspondencia con el código se mantienen en `docs/DATOS_Y_WRANGLING.md`; la cobertura y las pruebas se registran en `docs/ACEPTACION_DATOS.md`.

El usuario aceptó que los envíos se reciban con origen `NO_DECLARADO` y que el administrador los clasifique después como `REAL` o `SINTETICO`. La clasificación registra quién revisó, cuándo y por qué; el servidor no toma como cierta una declaración de origen enviada por un participante público. No se atribuye retrospectivamente origen real o sintético a los históricos sin revisión.

El estudio y las versiones del cuestionario tienen un estado separado de esa clasificación. El instrumento actual continúa en `BORRADOR`; el modo `OFICIAL` exige estudio y versión `APROBADA`, y la aplicación no declara esa aprobación institucional por arrancar o ejecutar el pipeline. Una clasificación `REAL` no demuestra por sí sola elegibilidad UMSS ni independencia de observaciones.

Las entradas nuevas conservan metadata de estudio e instrumento, definición congelada, huella canónica y captura aceptada. Las anteriores conservan sus valores y unidades originales; cuando falta información para reconstruir la definición, los estados o la captura íntegra, el procesamiento registra esa limitación. No se rellena esa falta usando el catálogo actual.

Una operación de reintento con el mismo contenido aceptado devuelve el mismo envío. Si utiliza la misma clave con contenido diferente, devuelve conflicto HTTP 409 y conserva la respuesta previa, sin sobrescribirla. Las respuestas distintas con correo o nombre coincidente no se eliminan automáticamente; la política de varias participaciones y la unidad de análisis de empleadores siguen pendientes.

Las correcciones y las revisiones de origen conservan un historial. La codificación manual de textos se asocia a un libro de códigos y a una revisión; no convierte automáticamente un texto en puntaje ni reescribe el original. Cada ejecución identifica las revisiones, reglas, versiones y capturas que utilizó.

Los conjuntos de preparación mantienen separado el origen real, sintético y no declarado; las matrices ordinarias omiten contactos, nombre organizacional y texto abierto original. Los IDs de envío mantienen trazabilidad bajo permisos: excluir identificadores directos no garantiza anonimato. El CSV original protegido conserva su alcance de captura y no se confunde con las matrices preparadas.

Las ejecuciones congelan sus entradas y publican sus salidas con manifiesto y huellas. Repetir entradas, reglas y revisiones produce el mismo contenido lógico aunque cambien el ID o el instante técnico de ejecución. Las salidas preparadas son insumos para las etapas siguientes; no equivalen a estadística, reportes automatizados ni resultados de simulación.

## Fase 4: estadística univariada, bivariada y chi-cuadrado

Fecha: 2026-10-13.

El usuario autorizó continuar («ok continuemos») después de la revisión de fase 4. La referencia `04_ESTADISTICA_UNIVARIADA_BIVARIADA_CHI_CUADRADO.md` conserva su contenido; la autorización actual supera su alcance inicial limitado a documentación. El contrato y la cobertura se mantienen en `docs/ESTADISTICA.md` y `docs/ACEPTACION_ESTADISTICA.md`.

Se implementa `PROTOCOLO_ESTADISTICO_V1` como borrador: resultados descriptivos y cruces exploratorios sobre un conjunto preparado verificado. Cada análisis congela plan, fuente, huellas, resultados y protocolo; no consulta las respuestas actuales para completar o cambiar una ejecución anterior. Se separan siempre tipo de encuesta, versión y estudio, sin homologación silenciosa ni extrapolación poblacional.

Las convenciones técnicas iniciales son alpha 0,05, dispersión muestral con divisor n−1, cuantiles tipo 7, Pearson de independencia sin corrección de continuidad, política conservadora de todas las esperadas activas ≥5 y V de Cramér sin corrección de sesgo. Las distribuciones ordinales permanecen principales; los mapas de puntajes específicos se declaran como suplementos de borrador, sin inferir intervalos iguales o una brecha de competencias.

La unidad de una matriz sigue siendo un envío. El plan registra unidad y evidencia de independencia desde una operación protegida administrativa; el valor por defecto carece de esa documentación y no ejecuta inferencia. Registrar esa evidencia no representa aprobación institucional ni demuestra automáticamente elegibilidad. Un contraste no aplicable conserva tablas/diagnósticos, pero p y decisión son `null`, sin confundirse con no rechazar H0.

B01–B05 tienen familias individuales por cruce/estrato; B06 registra los diez criterios completos como familia M=10 por estrato y aplica Holm, conservando en M los no ejecutables. Esto no declara control global de multiplicidad entre todas las pruebas de la aplicación. B01 usa financiamiento/nivel futuros y B02 los cursados. B07 conserva `PENDIENTE_DEFINICION` porque no existe referencia/metodología aprobada para antigüedad; B08 describe pares de tiempos en meses y cada duración por estado laboral/sector con sus bases propias, sin añadir una correlación o prueba de asociación no definida.

Los conjuntos nuevos agregan cobertura previa a exclusiones por calidad y el inventario de revisiones temáticas, incluidas las de cero temas. Los anteriores mantienen sus huellas y limitaciones: no se reconstruye un denominador que no registraron. Los números y decisiones analíticos conservan precisión completa; el formato de la página o CSV no cambia las bases ni la decisión de significación.

D21 (diseño muestral y alcance poblacional), D22 (cohortes/segmentación) y D23 (plan institucional de contrastes) se añaden a las decisiones metodológicas pendientes. Se mantienen D13, D16, D18 y D19. La implementación visual se registra en la fase 5 siguiente; reportes automáticos, Excel y simulación continúan pendientes.

## Fase 5: visualización computacional

Autorización: 2026-10-13. Verificación técnica: 2026-10-14.

El usuario pidió primero revisar y explicar `05_VISUALIZACION_COMPUTACIONAL.md` y después autorizó desarrollar («vamos»). Esa autorización supera la instrucción documental de posponer la interfaz. La referencia se conserva; el contrato efectivo y la evidencia se registran en [VISUALIZACION.md](VISUALIZACION.md) y [ACEPTACION_VISUALIZACION.md](ACEPTACION_VISUALIZACION.md). Las propuestas visuales no otorgan aprobación institucional ni resuelven decisiones metodológicas pendientes.

El dashboard se incorpora al monolito Thymeleaf/HTML/CSS/JavaScript, con Apache ECharts 6.0.0 servido localmente y sus avisos/licencia de dependencia. No se añade un segundo backend, inicio mediante npm ni licencia al proyecto. El inicio principal continúa manualmente con Maven. Desde un análisis guardado, **Ver gráficos** abre `/admin/estadisticas/{id}/visualizacion`; sus resultados `/admin/estadisticas/{id}/visualizacion.json` requieren ADMIN y no se almacenan en caché.

Las siete vistas presentan resumen, perfil/ trayectoria de titulados, formación continua, empleadores/demanda, valoraciones, cruces y calidad/cobertura. Los gráficos conservan tablas equivalentes, estados, unidades y bases. El navegador organiza dibujo y filtros; cifras y decisiones llegan de Java. La matriz muestra observadas, porcentajes por fila/columna/total y esperadas identificadas como diagnóstico, sin interpretar una prueba suspendida como ausencia de asociación.

El backend verifica el análisis y el bundle preparado frente a las huellas de origen. Sin selección utiliza el resultado guardado; al aplicar filtros recorta miembros y productos congelados y calcula con el plan registrado, sin consultar respuestas actuales. Conserva huellas de selección/resultado, carácter `BORRADOR`, familias y estratos separados. La comparación de hasta dos grupos usa categorías congeladas y bases propias; no concede homologación entre estudios/versiones ni control global de multiplicidad.

Los nuevos conjuntos con reglas `2026-10-wrangling-3` incorporan `submitted_at` al inventario de miembros. El período de recepción usa fechas inclusivas en `America/La_Paz`, separado del año de egreso y del corte del conjunto. Los bundles anteriores mantienen contenido y huellas; si no registraron fechas por envío, ese filtro pide preparar un conjunto nuevo. `NO_DECLARADO` permanece «Origen sin verificar»; no se transforma en dato real o demostración. El origen sintético se identifica de forma persistente.

El radar de empleadores usa porcentaje de acuerdo parcial/total sobre `N_sustantivos`, con mínimo 0 y máximo 100. R01 representa criterios 1–5 y R02 criterios 6–10, cada uno una sola vez por panel; la división facilita lectura y no define índices. Cada eje conserva numerador, denominador y `NO_SABE`. Un eje sin base impide dibujar la serie completa; acuerdo cero con base positiva es válido. Las competencias T39/T40 ya tienen ítems, referente y escala aprobados por el usuario para el borrador; se mantienen sus distribuciones de adecuación y `NO_PUEDE_VALORAR`, sin inventar radar propio, brecha o índice.

Antigüedad, cohortes, comparabilidad institucional, unidad de análisis, elegibilidad y alcance inferencial conservan sus pendientes. La suite ampliada pasó 288 pruebas en H2 y 288 en la ejecución con PostgreSQL, además de nueve pruebas JavaScript. Chrome verificó las siete vistas en tres anchos y sus controles sobre fixtures sintéticas. El registro de aceptación mantiene pendientes la revisión con lector de pantalla, zoom y la auditoría WCAG completa. Reportes automáticos/Excel y simulación corresponden a las etapas siguientes.

## Despliegue provisional antes de la fase 6

Fecha: 2026-10-14. El usuario pidió desplegar lo desarrollado y eligió Render Free para el monolito y Neon Free para PostgreSQL. Indicó que todavía no tiene cuentas y solicitó una guía para crearlas. La preparación del despliegue y el estado de publicación se documentan en [DESPLIEGUE.md](DESPLIEGUE.md); no se declara una URL pública ni conexión a cuentas externas por crear archivos locales.

El contenedor conserva Java 21, la aplicación monolítica y el inicio local manual con Maven. `prod` exige PostgreSQL y credenciales de conexión, cookies seguras y configuración inicial deshabilitada; impide combinar perfiles de desarrollo o setup antes de crear la conexión. Los perfiles locales ignoran cabeceras de proxy para la configuración inicial. El administrador se crea una vez desde el equipo del usuario contra la base remota, usando el formulario existente; su contraseña no se configura mediante variables de entorno.

Las actualizaciones conservan la misma base y las migraciones Flyway nuevas. El despliegue no importa H2, modifica históricos, clasifica orígenes ni aprueba instrumento o protocolo. No se contratan recursos de pago ni se activan despliegues automáticos. La preparación técnica pasó 316 pruebas en H2 y la imagen Docker se construyó; publicar requiere que el usuario cree y conecte las cuentas elegidas.

## Encuestas dinámicas: recepción por versión, paso 3 local

Fecha: 2026-10-14. El usuario autorizó continuar con «luz verde», manteniendo el trabajo local antes de hacer push. La guía vigente es [RECEPCION_ENCUESTAS.md](RECEPCION_ENCUESTAS.md).

Publicar permite responder el enlace específico de versión. La selección administrativa de versión activa controla el enlace principal y la tarjeta del inicio; publicar una sucesora no la activa automáticamente. Los formularios abiertos envían a su versión fija. Cerrar impide nuevos envíos y retira esa versión del enlace principal si estaba activa; los reintentos idénticos de envíos ya aceptados conservan su referencia.

V9 añade referencias verificadas por claves foráneas de encuesta, versión y huella a los nuevos envíos dinámicos. No modifica las migraciones aplicadas V7/V8 ni rellena referencias históricas. Se conservan estados por pregunta, captura original y origen inicial NO_DECLARADO. La revisión administrativa de origen mantiene motivo y trazabilidad. Activar/cerrar no cambia la definición congelada ni representa aprobación institucional.

La consulta protegida permite seleccionar encuesta y versión. Los análisis/reportes configurables de instrumentos nuevos siguen pendientes. Las reglas 2026-10-wrangling-4 registran su exclusión explícita ANALISIS_DINAMICO_PENDIENTE en el inventario, evitando atribuirles el protocolo de empleadores. Los bundles históricos conservan contenido y huellas; las fechas registradas desde wrangling-3 también se conservan en la nueva regla.

Verificación: 387 pruebas Java pasaron en H2 y 387 en la ejecución con PostgreSQL 18 local, incluidos 24 casos nuevos de recepción dinámica. Chrome comprobó cinco pantallas en tres anchos, condiciones y envío público, sin desbordamientos ni excepciones JavaScript. Las verificaciones usaron datos ficticios y una base temporal; no se conectó a Neon ni se realizó commit, push o despliegue.
## Encuestas dinámicas: preparación general, paso 4 local

Fecha: 2026-10-14. El usuario autorizó «paso 4». El alcance implementado corresponde a selección y preparación por encuesta/versión, después del circuito de editor, publicación y recepción. Se conserva la instrucción de trabajar en local antes de commit/push. La guía vigente es [PREPARACION_GENERAL.md](PREPARACION_GENERAL.md).

Cada versión publicada o cerrada tiene revisiones inmutables de configuración de preparación. Todas las preguntas comienzan excluidas; incluir opciones controladas como categorías nominales o enteros como magnitudes requiere revisión administrativa de ausencia de identificadores y unidad explícita para números. Contactos y texto libre permanecen excluidos. No se asigna puntaje a NO_SABE ni se infiere ordinalidad, índice o escala por presentación.

PREPARACION_GENERAL_V1 conserva una versión exacta, estudio/estado de aprobación observado, definición, configuración, origen revisado, período inclusivo en America/La_Paz, corte, miembros y revisiones exactas de origen. Conserva CON_VALOR/SIN_RESPUESTA/NO_APLICA, cero como valor y null con estado para ausencias. La fila principal sigue siendo un envío; selecciones múltiples mantienen una tabla de menciones separada. La cobertura registra total, con valor, sin respuesta, no aplica y base aplicable. No se deduplica por datos coincidentes.

V10 añade survey_preparation_config y survey_preparation_run. No modifica migraciones aplicadas ni respuestas, snapshots o bundles anteriores. Las descargas verifican huellas y utilizan el contenido congelado; requieren ADMIN y no se almacenan en caché. Guardar una nueva revisión, cambiar origen, activar otra versión o recibir otros envíos no cambia un conjunto guardado. Oficial exige datos reales, estudio/instrumento aprobados y al menos un envío incluido; no se aprueba desde esta pantalla.

El adaptador UMSS original permanece separado y excluye encuestas dinámicas; el servicio general prepara estas mediante su propio contrato. Correcciones/codificación temática generales y descriptivos, cruces, gráficos, reportes, importación y Excel continúan pendientes; la preparación no los declara implementados.
## Encuestas dinámicas: descriptivos y gráficos generales, paso 5 local

Fecha: 2026-10-14. El usuario autorizó «luz verde» después de explicar el siguiente paso. Se implementa el primer incremento del análisis general: descriptivos y gráficos desde conjuntos congelados, sin commit, push ni despliegue. La guía es [ANALISIS_GENERAL.md](ANALISIS_GENERAL.md).

DESCRIPTIVOS_GENERALES_V1 conserva frecuencias nominales, bases por pregunta y bases distintas de participantes/menciones en selección múltiple. No asigna puntajes a NO_SABE ni ordinalidad a las opciones. Las magnitudes declaradas usan las funciones existentes: media, mediana, cuartiles tipo 7 y dispersión muestral n−1. Sin base se conserva null; cero observado es valor. Una sola observación no tiene dispersión muestral.

V11 añade survey_general_analysis con referencia al conjunto exacto, actor, fecha y huellas de fuente/resultado. Se verifica el bundle congelado y la correspondencia de cálculo al abrir el análisis. Cambios posteriores en origen, recepción o versión activa no cambian resultados anteriores. ADMIN, CSRF, escapes y respuestas sin caché permanecen obligatorios. ECharts local dibuja conteos y medidas calculados por Java con tablas/resúmenes equivalentes; no recalcula el protocolo ni representa tres medidas numéricas como observaciones.

Cruces y chi-cuadrado general con supuestos, filtros/comparaciones adicionales y reportes/Excel/PDF continúan pendientes. El protocolo original UMSS y su dashboard se conservan.
## Encuestas dinámicas: cruces y chi-cuadrado generales

Fecha: 2026-10-14. El usuario autorizó «ok luz verde» después de explicar el incremento. La guía vigente es [CRUCES_GENERALES.md](CRUCES_GENERALES.md). Trabajo local, sin commit/push ni conexión a Neon.

CRUCE_GENERAL_V1 usa dos preguntas nominales distintas de selección única incluidas en un mismo conjunto congelado. Conserva todas las categorías, observadas, porcentajes con bases explícitas, esperadas como diagnóstico y pares excluidos por combinación de estados. NO_SABE es categoría, no faltante. Selección múltiple, números, preguntas ajenas/excluidas y cruces de más de 2500 celdas se rechazan.

La independencia no se asume por cantidad de envíos. Su declaración exige unidad y evidencia registradas, sin conceder aprobación o demostrar elegibilidad. La prueba existente de Pearson exige variación activa, ausencia de ceros estructurales declarados y todas las esperadas activas >=5. Retira categorías cero solamente para la prueba y conserva sus índices. Sin aplicabilidad, p, decisión y V de Cramér permanecen null; no se interpreta como ausencia de asociación. Alpha y declaraciones quedan congelados; no se afirma ajuste global de multiplicidad entre cruces exploratorios.

V12 añade survey_general_cross. Fuente, especificación, resultado, actor y huellas quedan persistidos y verificados al abrir. Tablas y mapa de calor usan resultados calculados en Java; ECharts local no recalcula. ADMIN/CSRF, escapes y ausencia de caché se conservan. Las versiones y protocolos anteriores no se reescriben. Reportes editables y salidas estadísticas adicionales continúan pendientes.
## Reportes: plantillas con revisiones y HTML protegido, paso 6 local

Fecha: 2026-10-14. El usuario autorizó «luz verde» después de revisar el alcance. La guía vigente es [REPORTES_HTML.md](REPORTES_HTML.md). Se conserva el trabajo local antes de commit/push y no se conecta a Neon.

Las plantillas generales configuran título, introducción, secciones/orden y representación gráfica mediante una lista cerrada; no admiten código ejecutable. Cada edición agrega una revisión inmutable con actor y huella, y el control de revisión/bloqueo rechaza ediciones simultáneas u obsoletas. Los comentarios se guardan por reporte, separados de cifras calculadas.

El reporte exige un análisis general guardado. Puede incluir hasta diez cruces distintos del mismo conjunto exacto; no mezcla encuestas/versiones o conjuntos aunque tengan huellas lógicas equivalentes. Las incompatibilidades entre secciones y contenido seleccionado producen diagnóstico antes de guardar. Las cifras, bases, pruebas y decisiones no se editan manualmente.

REPORTE_HTML_V1 congela revisión/definición/huella de plantilla, análisis, cruces y especificaciones, comentarios, conjunto, filtros/corte, configuración, estudio/estado observado y huellas de origen/resultados. V13 añade report_template, report_template_revision, generated_report y generated_report_cross sin modificar migraciones aplicadas. Generar otro documento produce otra instancia y conserva el anterior; no se afirma jerarquía de revisiones de reportes.

Al abrir se verifica el contenido y las fuentes congeladas. El navegador representa la copia registrada sin recalcular estadísticas. Tablas y resúmenes permanecen disponibles si falla la carga gráfica. ADMIN, CSRF, escapes y salidas sin caché se conservan. El HTML se consulta dentro de la aplicación autenticada; hay JSON de contenido/referencias, pero todavía no exportación HTML autónoma, PDF o Excel. El protocolo legado UMSS y el documento final institucional no se declaran incorporados a esta primera entrega de reportes generales.
## Importación CSV: paso 7 local

Fecha: 2026-10-14. El usuario autorizó desarrollar después de explicar el paso 7. La guía vigente es [IMPORTACION_CSV.md](IMPORTACION_CSV.md). Se conserva el trabajo local antes de commit/push y no se conecta a Neon.

El recorrido administrativo carga un original UTF-8, declara separador/plataforma y versión publicada, conserva columnas por posición, guarda correspondencias con revisiones, presenta validación y confirma el lote completo. El original se conserva como bytes base64 en la base con SHA-256; no depende del disco efímero de Render. V14 añade csv_import_batch, csv_import_mapping, csv_import_confirmation y csv_import_row sin modificar migraciones aplicadas.

La importación no inventa consentimiento académico ni equivalencia institucional. Confirmar requiere columna/aceptación y evidencia del consentimiento de fuente, y revisión documentada de preguntas, opciones, unidades, obligatoriedad y condiciones. Todas las preguntas requieren columna para incorporar; campos no recolectados no reciben estados históricos inferidos. Valores en campos no aplicables, opciones/etiquetas ambiguas, identificadores externos repetidos en el lote y fechas asignadas inválidas exigen resolución; no se descartan silenciosamente.

Repetir los mismos bytes y versión reutiliza lote; reconfirmar la revisión devuelve referencias existentes. Confirmaciones concurrentes se serializan y una escritura fallida revierte todo el intento. Nuevas correspondencias de lotes confirmados se conservan para revisión, pero otra incorporación requiere reconciliación pendiente. No se deduplica por correo/nombre. Candidatos por archivo/fila exactos se presentan con lote y motivo; la revisión explícita y los candidatos se conservan en evidencia. Cambiar candidatos invalida la vista previa de una importación nueva.

Las respuestas incorporadas mantienen origen NO_DECLARADO. La captura lógica anterior conserva su formato, y la procedencia verificada se identifica como IMPORTACION_CSV mediante referencias al original, correspondencias, fila y hashes. Fecha externa/precisión e identificador proporcionado se distinguen de recepción y de campos sin columna declarada; no se inventan IDs externos. Conjuntos nuevos conservan referencia y procedencia sin exponer contactos o identificador externo; bundles anteriores mantienen sus huellas y metadata limitada.

Se mantienen ADMIN, CSRF, escapes y ausencia de caché. El original se descarga como texto conservando bytes; no se evalúan fórmulas o HTML del CSV. Máximos: 1 MiB, 500 registros de datos, 200 columnas y 8000 caracteres por celda, sujetos también al instrumento. XLSX, reconciliación, fechas adicionales y mapeos específicos por proveedor continúan pendientes.

## Paso 8: Excel con selecci?n de hoja

Fecha: 2026-10-15. El usuario autoriz? implementar el siguiente incremento local. La gu?a es [IMPORTACION_EXCEL.md](IMPORTACION_EXCEL.md). V15 conserva originales XLSX en la base y separa lotes por versi?n, huella del libro e ?ndice de hoja. CSV conserva su lectura y usa hoja 0. Seleccionar una hoja permite reutilizar correspondencias, vista previa, confirmaci?n, preparaci?n, an?lisis y reportes existentes. No se inventan consentimiento, origen real, equivalencia institucional o fecha/zona a partir de un n?mero Excel. F?rmulas, macros, celdas combinadas y archivos fuera de l?mites se rechazan. Los nuevos conjuntos conservan procedencia IMPORTACION_XLSX y referencia verificable al libro/hoja; los anteriores no se completan retrospectivamente.

Verificaci?n: 466 pruebas completas en H2 y 466 en ejecuci?n con PostgreSQL 18 temporal, sin fallos u omisiones; navegador en cuatro pantallas y tres anchos. Se conserva el alcance local: sin Neon, commit, push ni despliegue. Exportaciones PDF/Excel y reconciliaci?n de lotes confirmados permanecen pendientes.

Ajuste del prototipo (2026-10-17): selección de vista previa independiente de las casillas de inclusión en preguntas y cruces; comentarios opcionales por elemento; columnas verticales iniciales y alternativas circular/anillo para categorías exclusivas. Las opciones avanzadas informan la condición necesaria. Verificación en Chrome: 27 vistas a tres anchos, sin excepciones ni desbordamientos. Continúa siendo una maqueta sin persistencia ni integración con datos reales.

Ajuste de representaciones (2026-10-17): el usuario pidió botones agrupados con selección múltiple para preguntas y cruces. La maqueta permite combinar las representaciones compatibles; indica selección con color y marca, disponibilidad sin resaltar e incompatibilidad en gris con explicación. La generación exige una representación activa por elemento incluido. No cambia el backend ni las opciones de exportación actuales.

## Generador directo de reportes: paso 2

Fecha: 2026-10-17. Autorizado con «luz verde paso 2». Generación sin plantilla previa desde análisis guardado y acceso desde Resultados mediante guardado del corte mostrado. El cruce actual se transfiere como selección al formulario. Presentación directa congelada y verificada como REPORTE_HTML_V2, sin crear plantillas; los reportes previos mantienen REPORTE_HTML_V1 y sus revisiones. V16 permite referencia nula conservando claves foráneas. Se mantienen autenticación ADMIN, CSRF, huellas, fuentes exactas y exportaciones. Verificado con 526 pruebas H2 y 79 focalizadas en PostgreSQL local; revisión de Chrome del formulario/reporte y Resultados. La personalización visual real continúa en el paso 3. Sin cambios en Neon, commit, push o despliegue. Detalles en REPORTES_HTML.md.

## Personalización real del reporte: paso 3

Fecha: 2026-10-17. Autorizado con «paso 3 luz verde». La generación directa abre pantallas separadas con vista previa en servidor, selección de preguntas, títulos/comentarios por pregunta y presentación general. La vista previa no persiste; generar exige que su huella coincida con contenido reconstruido desde fuentes congeladas. REPORTE_HTML_V3 conserva y verifica configuración y resultado de presentación sin alterar las cifras originales. Reapertura conserva opciones; V1/V2 siguen verificándose. Cruces nuevos/nombres y representaciones múltiples continúan en pasos 4/5. Verificado con 530 pruebas H2, 83 focalizadas en PostgreSQL y 24 vistas Chrome. Sin migración adicional a V16, conexión a Neon, commit, push o despliegue. Guía: REPORTES_HTML.md.

## Comparaciones integradas al reporte: paso 4

Fecha: 2026-10-17. Autorizado con «luz verde» tras la entrega del paso 3. Se integran creación y vista previa de cruces del conjunto congelado, preguntas agrupadas por sección con compatibilidad explícita, nombres y comentarios propios del reporte. La vista previa no escribe; añadir exige su huella y reutiliza cruces idénticos mediante bloqueo del conjunto, probado con solicitudes simultáneas. No se concede independencia ni se exige justificación para la descripción. REPORTE_HTML_V4 congela presentación de cruces y preserva originales; V1–V3 siguen verificándose. Se mantiene ADMIN/CSRF, escapes y no-store. Verificado con 534 pruebas H2, 87 focalizadas PostgreSQL y 21 vistas Chrome. Gráficos múltiples siguen en el paso 5. Sin nueva migración, conexión a Neon, commit, push o despliegue. Detalles en REPORTES_HTML.md.

## Representaciones múltiples en reportes: paso 5

Fecha: 2026-10-17. Autorizado con «luz verde» tras entregar el paso 4. Se conectan botones compactos por categoría para seleccionar varias representaciones en preguntas y cruces. La compatibilidad se valida en backend; selección única permite circular/anillo, selección múltiple los excluye, magnitudes conservan resumen con unidades y los cruces usan observadas con calor/agrupadas/apiladas. Se conservan tablas equivalentes y no se fabrican frecuencias/porcentajes ni pruebas. REPORTE_HTML_V5 conserva listas y verifica presentación; V1–V4 siguen disponibles. Exportación visual adaptada a gráficos sin ejes/horizontales y series. Verificado con 538 pruebas H2, 91 focalizadas PostgreSQL, cuatro pruebas JS nuevas, 24 vistas Chrome con doce gráficos y seis vistas de exportación con 24 descargas más PDF de impresión. Sin nueva migración ni dependencia, Neon, commit, push o despliegue. Avanzados continúan en el paso 6. Detalles en REPORTES_HTML.md.

## Gráficos avanzados del generador: paso 6

Fecha: 2026-10-17. Autorizado con «luz verde» después de entregar el paso 5. Histogramas Sturges, cajas tipo 7 con bigotes min/max, recepción mensual en La Paz, dispersión de pares numéricos y radar de los paneles originales de cinco criterios con escala exacta. Los cálculos usan la fuente congelada en backend; no se presentan pronósticos, índices globales ni correlaciones automáticas. Se mantienen tablas, unidades, bases, No sabe y estados por separado; no se rellenan series incompletas ni metadata histórica ausente. REPORTE_HTML_V6 conserva configuración y proyecciones verificadas sin modificar fuentes ni V1–V5. Añadir pares/paneles no duplica entradas; vista previa sin persistencia y generación con huella revisada. Verificado con 545 pruebas H2, 98 focalizadas en la ejecución con PostgreSQL, siete pruebas JS de gráficos y 27 vistas Chrome; además 14 descargas y PDF de impresión. Sin nueva dependencia/migración, Neon, commit, push o despliegue. El paso 7 continúa con plantilla opcional y recorrido de exportación. Detalles en REPORTES_HTML.md.


### 2026-10-17 — Plantillas opcionales del generador (paso 7)

Autorizado con «luz verde». Guardar plantilla es opcional al generar; el nombre vacío usa el título del reporte. Plantilla y reporte se guardan atómicamente. La configuración reutilizable excluye datos, cifras, referencias a cortes y comentarios particulares, y requiere la misma definición del instrumento. Aplicar es una acción administrativa explícita que reemplaza la configuración y reconstruye cruces exploratorios en el conjunto destino sin trasladar aprobación de independencia. V17 incorpora almacenamiento separado, con huella verificable, y conserva las plantillas históricas y los reportes V1–V6. Las exportaciones siguen el reporte congelado; PDF continúa mediante impresión del navegador. No implica aprobación institucional ni despliegue remoto.


### 2026-10-17 — Cierre técnico del generador (paso 8)

Autorizado con «luz verde». Verificado el recorrido HTTP desde Resultados hasta reportes personalizados, plantilla opcional, descargas y reutilización en otro corte; se conserva idéntico el reporte anterior tras respuestas nuevas. Se corrigió el aviso de error de aplicación de plantilla para que la actualización de la vista previa no lo oculte. Estado y alcance en [ACEPTACION_GENERADOR_REPORTES.md](ACEPTACION_GENERADOR_REPORTES.md). El cierre no implica despliegue remoto, aceptación con usuarios finales, aprobación institucional ni finalización de otros entregables del proyecto.


## 2026-10-18 — Encuestas predefinidas opcionales a partir de fuentes oficiales

El usuario pidió que una instalación nueva empiece sin encuestas obligatorias y que una opción discreta en el pie de administración cree las oficiales como cualquier otra encuesta. Se conservan estructuras reutilizables de titulados (49 preguntas) y empleadores (30 preguntas, incluidos 18 criterios distintos), sin respuestas de campo. La denominación de fuente oficial no concede aprobación del estudio/instrumento. Los criterios 01–10 coinciden con el catálogo previo; los ocho adicionales no son las repeticiones eliminadas del PDF original.

Las recetas se materializan mediante el editor ordinario. No se ejecutan al arrancar. Repetir una instalación conserva una copia original sin duplicarla; recuperar tras una edición crea otra encuesta y no sobrescribe la edición ni sus respuestas. Los borradores o encuestas sin datos se eliminan con confirmación concreta; aquellas con respuestas, importaciones o preparación se pueden archivar y restaurar, preservando sus originales. Archivar cierra recepción y no altera definiciones ni hashes.

Las exportaciones no documentan toda la obligatoriedad, saltos ni opciones nunca seleccionadas: las plantillas parten como borradores opcionales, con revisión administrativa separada de las ayudas a participantes. Se preservan rangos, cuatro categorías de escala y selecciones múltiples; los contactos históricos se reciben como texto sin fabricar formatos válidos. La creación automática de encuestas arbitrarias desde archivos queda fuera de este incremento: el botón es un armador de estas dos estructuras conocidas.

Se añade recepción histórica documentada para archivos sin columna de consentimiento, con revisión explícita, evidencia y ausencia de registro conservada (NULL en respuesta, recibo congelado verificable), independiente de la aceptación del participante. Los formularios públicos siguen exigiendo aceptación. El origen continúa NO_DECLARADO hasta revisión trazable. No se cargaron respuestas reales ni se migró Neon para esta entrega.

Guía: [ENCUESTAS_PREDEFINIDAS.md](ENCUESTAS_PREDEFINIDAS.md); reglas de recepción: [IMPORTACION_HISTORICA.md](IMPORTACION_HISTORICA.md).

## 2026-10-18 — Cargo observado incluido al crear empleadores

El usuario prefirió que la encuesta predefinida admita desde su creación el cargo «Docentes» presente en la exportación, evitando una adaptación posterior de esa opción en cada importación. La receta de empleadores avanza a `2026-10-fuente-oficial-2` y añade `DOCENTES`, conservando los ocho cargos anteriores y su selección múltiple. Las notas administrativas distinguen esta categoría observada de una opción predefinida confirmada del formulario original; no se atribuye aprobación institucional. Establecer la receta actual crea una encuesta ordinaria nueva cuando solo existe la receta anterior, sin alterar sus publicaciones, respuestas ni originales.

## 2026-10-19 — Eliminar encuestas con descarga previa de respaldo

El usuario autorizó agilizar la eliminación incluso cuando hay datos, mediante un respaldo descargable. La gestión ofrece descarga ZIP y confirmación explícita de que se guardó antes del borrado definitivo. La descarga por sí sola conserva la encuesta. Se exige la misma cuenta administrativa, una constancia de respaldo reciente y una huella completa idéntica; las modificaciones posteriores obligan a descargar otro respaldo. El borrado y su auditoría son una sola transacción.

El ZIP conserva filas originales, versiones, capturas, estados, revisiones, importaciones y archivos originales, preparación y productos propios. El estudio, las plantillas reutilizables y los conjuntos históricos compartidos se conservan para no alterar otras encuestas ni sus huellas. El respaldo enumera sus referencias. La constancia V20 guarda únicamente identificadores, fechas y huellas, sin respuestas ni copias de archivos. El ZIP permite recuperación técnica, no restauración automática desde el importador. Ver [ELIMINACION_CON_RESPALDO.md](ELIMINACION_CON_RESPALDO.md).

## 2026-10-19 — Resultados unificados y cortes por tiempo y versiones

El usuario autorizó reunir Respuestas y Resultados bajo Resultados, mantener Ver envíos para la consulta individual y renombrar Reportes y plantillas como Reportes. El total comprende todos los envíos de la versión seleccionada; se carga por sesión y se actualiza únicamente con el botón explícito. Cambiar representaciones conserva las cifras cargadas y permite varias simultáneas.

Los cortes se delimitan por recepción inclusiva en hora de Bolivia y por una, varias o todas las versiones de la misma encuesta. Las definiciones incompatibles permanecen separadas. Un corte guardado es inmutable; actualizar su vista permite crear otro. La generación directa del reporte congela internamente la vista mostrada sin exigir guardar un corte previamente. Los totales conservan estratos por origen y alcance descriptivo. La eliminación confirmada de un corte conserva los envíos originales. Especificación y límites: [RESULTADOS_Y_CORTES.md](RESULTADOS_Y_CORTES.md).

- 2026-10-19: el usuario autorizó diccionario global y diccionario privado por pregunta, comunes a Resultados y Reportes. El privado reemplaza al global en esa pregunta y conserva trazabilidad por encuesta/definición; no mezcla sus equivalencias con otras preguntas. Los alias solo numéricos se aplican únicamente en contexto privado. La elección se confirma al guardar y se congela en cada reporte. Se corrige el falso bloqueo de nombre del programa como campo identificativo.

- 2026-10-19: el usuario autorizó unificar creación y uso de plantillas con el personalizador actual de reportes. Al usarlas se elige total de una versión o corte guardado. Se implementan edición, duplicación editable y eliminación de plantillas, conservando reportes ya generados. Las plantillas no trasladan datos ni autorizaciones sensibles. Las anteriores se adaptan al editor actual sin alterar sus revisiones históricas. Véase docs/PLANTILLAS_REPORTES.md.
