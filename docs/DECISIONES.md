# Decisiones del proyecto

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
