# Estudio de mercado UMSS

Aplicación monolítica de Ingeniería en Sistemas de la Universidad Mayor de San Simón. Reúne las encuestas a titulados y empleadores, el backend y las páginas en un único proyecto y repositorio.

Los entregables finales son la herramienta de recolección de datos y el documento de Análisis de Mercado para apoyar el Plan de Desarrollo Estratégico y la acreditación ARCUSUR.

## Estado actual

El **paso 7 del rediseño** verifica el recorrido del editor desde crear/publicar hasta recibir/importar, guardar un corte y exportar. Se conserva la versión elegida al activar otra. Pasaron **522 pruebas Java en H2 y 522 en la ejecución con PostgreSQL temporal**, **11 JavaScript** y **51 vistas en Chrome**. La [aceptación técnica del rediseño](docs/ACEPTACION_REDISENO.md) detalla cobertura, reproducción y límites.

El **paso 6 del rediseño** permite exportar los reportes generales a **PDF mediante el diálogo del navegador**, gráficos PNG/SVG y datos CSV/Excel/JSON. Las salidas conservan versión, origen, período, corte y referencias del reporte guardado. Abre el reporte y usa **Exportar este reporte**. La [guía de exportaciones](docs/EXPORTACIONES_REPORTES.md) detalla formatos, seguridad, recorrido y límites. El **paso 5** añadió [ayudas para entender resultados](docs/AYUDAS_RESULTADOS.md), bases y aplicabilidad de chi-cuadrado.

El **paso 4 del rediseño** incorpora **resultados actualizados cada 30 segundos**, filtros por origen/recepción, gráficos, cruces descriptivos y guardado del corte mostrado para versiones publicadas desde el editor. Las consultas no crean conjuntos; el guardado explícito conserva fuentes y reutiliza un corte ya guardado. Abre **Mis encuestas → encuesta → versión → Resultados**. La [guía de resultados actualizados](docs/RESULTADOS_ACTUALIZADOS.md) explica configuración inicial, avisos, integridad y el alcance separado de los cuestionarios originales UMSS.

El **paso 3 del rediseño** simplifica la importación: detección automática de separador y columnas, reconocimiento inequívoco de opciones y dos casillas de revisión con trazabilidad. Las discrepancias muestran fila, pregunta y valor original. Abre **Mis encuestas → encuesta → versión → Importar**. La [guía del asistente](docs/ASISTENTE_IMPORTACION.md) explica automatizaciones, consentimiento, referencias opcionales y conservación de lotes anteriores.

El **paso 2 del rediseño** incorpora **Mis encuestas** como inicio administrativo. Abre una encuesta, elige versión y navega por Resumen, Versiones, Respuestas, Resultados, Importar y Reportes y plantillas. El contexto se conserva al entrar en las herramientas existentes; las variantes empiezan sin respuestas. La [guía del espacio de encuestas](docs/ESPACIO_ENCUESTAS.md) explica el recorrido y sus límites. CSV y Excel `.xlsx` están disponibles mediante sus importadores actuales. Automatización del asistente, resultados en vivo y nuevas exportaciones corresponden a los siguientes pasos.

Los párrafos siguientes registran los incrementos anteriores y su alcance en el momento de su entrega.

El séptimo incremento local añade **importación CSV con correspondencias revisadas, vista previa y confirmación completa**. Abre **Panel → Importar respuestas CSV**. El original se conserva en base de datos, el consentimiento no se inventa y los reintentos no duplican filas. La [guía de importación](docs/IMPORTACION_CSV.md) explica V14, límites, privacidad y requisitos. XLSX y reconciliación de lotes confirmados siguen pendientes. No se realizó push ni despliegue.

El sexto incremento local incorpora **plantillas con revisiones y reportes HTML protegidos**. Abre **Panel → Reportes y plantillas** para configurar título, introducción, secciones/orden y gráficos; elige un análisis y sus cruces para generar el documento con comentarios. Las cifras y fuentes se congelan y las revisiones anteriores se conservan. La [guía de reportes](docs/REPORTES_HTML.md) explica V13, uso y límites. PDF/Excel y exportación autónoma siguen pendientes; no se realizó push ni despliegue.

El incremento local de **cruces generales y chi-cuadrado condicionado** permite elegir dos preguntas nominales de selección única desde un conjunto. Incluye tabla con bases y porcentajes, mapa de calor, aplicabilidad y V de Cramér cuando corresponde. La [guía de cruces](docs/CRUCES_GENERALES.md) documenta V12 y los requisitos; por defecto no se asume independencia. Las plantillas de reportes siguen pendientes.

El quinto incremento local añade **descriptivos, tablas y gráficos generales** desde conjuntos congelados. Abre un conjunto y pulsa **Guardar análisis descriptivo**. Incluye frecuencias, bases de participantes/menciones y resúmenes numéricos; la [guía de análisis general](docs/ANALISIS_GENERAL.md) explica V11, cálculos y límites. Cruces y reportes generales siguen pendientes. No se realizó push ni despliegue.

El cuarto incremento local incorpora **selección y preparación de datos por encuesta/versión**. Abre **Panel → Preparar datos de encuestas nuevas**, revisa privacidad y tratamiento de cada pregunta, guarda la configuración y prepara un conjunto por origen y período. La [guía de preparación general](docs/PREPARACION_GENERAL.md) explica V10, los estados, las bases y las descargas JSON/CSV. Descriptivos, gráficos y reportes configurables siguen pendientes. Los cambios permanecen locales.

El tercer incremento local incorpora **recepción pública por versión, elección de versión activa y consulta administrativa por encuesta/versión**. Publicar habilita el enlace propio; **Usar esta versión en el enlace principal** elige qué formulario abre el enlace principal. Los formularios ya abiertos conservan su versión. La [guía de recepción](docs/RECEPCION_ENCUESTAS.md) explica cómo probarlo con H2 (`dev`) y la migración V9. Los análisis configurables de encuestas nuevas siguen pendientes. No se realizó commit, push ni despliegue de estos incrementos.

El segundo incremento incorporó **vista previa, publicación de versiones fijas, borradores sucesores y cierre**. La [guía de publicación](docs/PUBLICACION_ENCUESTAS.md) conserva su evidencia y la migración V8; sus límites de recepción se superan con el paso 3.

El primer incremento local añadió un **editor administrativo de borradores**: crear/copiar encuestas, editar secciones y preguntas, configurar opciones/condiciones y ordenar. Se abre desde **Panel → Abrir editor de borradores**. Para probar con H2 sin usar Neon: `mvn "-Dspring-boot.run.profiles=dev" spring-boot:run`. La [guía del editor](docs/EDITOR_BORRADORES.md) conserva la evidencia de V7; importación y reportes configurables siguen para las siguientes etapas del [plan](docs/ENCUESTAS_DINAMICAS_PLAN.md).

Cuestionarios, preparación, estadística y visualización implementados y verificados técnicamente para revisar el instrumento y los datos. Incluye:

- Dos encuestas públicas con opciones controladas, respuestas múltiples, saltos condicionales y validación en el servidor.
- Los diez criterios de empleadores una sola vez, conservando la escala y la categoría «No sabe».
- 51 campos de titulados y 24 de empleadores. Titulados incorpora edad, género, tiempos en meses, hasta dos asignaturas de poca utilidad, filtro de experiencia y doce valoraciones de competencias aprobadas por el usuario para el borrador.
- Persistencia transaccional y prevención de envíos duplicados al reenviar el mismo formulario.
- Panel con inicio de sesión, conteos, las últimas 100 respuestas, detalle y exportación CSV.
- Administrador guardado en la base de datos con hash BCrypt; creación inicial desde un formulario local.
- Registro de versión, fecha, consentimiento, enunciado y etiquetas de cada respuesta para trazabilidad.
- Estados explícitos por pregunta en nuevos envíos: con valor, sin respuesta y no aplica; conservación de los datos históricos sin inferir estados ausentes.
- Comprobante con referencia del envío después de guardarlo; reintentar el mismo formulario devuelve la misma referencia.
- Protección del panel y exportación en el backend; formularios con CSRF y contenido escapado.
- Estudio, definición del instrumento y captura lógica congelados con huellas; conflicto 409 si se reintenta una clave con contenido distinto.
- Revisión de origen real/sintético, correcciones y codificación manual con motivo e historial, conservando la captura original.
- Conjuntos preparados por origen con matrices, estados, indicadores, calidad, diccionario, inventario y manifiesto; descargas JSON, CSV y ZIP protegidas.
- Reproducibilidad de contenido lógico y verificación de integridad del conjunto al descargarlo.
- Descriptivos, cruces y chi-cuadrado condicionado al protocolo; análisis congelados con JSON y nueve productos CSV.
- Dashboard administrativo con siete vistas, tablas equivalentes, barras, cajas de cinco números, mapas de calor, radar y dispersión; su verificación de fase 5 se registra por separado.
- Selecciones validadas desde conjuntos congelados, bases explícitas y comparación de hasta dos grupos sin mezclar estudios/versiones.

Las encuestas son borradores. La [especificación de fase 2](docs/02_REQUISITOS_CUESTIONARIOS.md) conserva la referencia recibida; la [matriz de cobertura y aceptación](docs/ACEPTACION_CUESTIONARIOS.md) relaciona T01-T41 y E00-E22/E09_OTRO con el código, la obligatoriedad, las condiciones y los criterios CA01-CA20. Antes de iniciar la recolección definitiva hay que resolver los campos ambiguos y aprobar el instrumento descrito en [docs/INSTRUMENTO.md](docs/INSTRUMENTO.md). La protección por contraseña no convierte en anónimos los datos de contacto que los formularios solicitan.

La [referencia de fase 3](docs/03_DATOS_Y_DATA_WRANGLING.md), el [modelo y diccionario efectivos](docs/DATOS_Y_WRANGLING.md) y la [aceptación de datos](docs/ACEPTACION_DATOS.md) distinguen captura original, preparación y funciones analíticas posteriores. El usuario autorizó implementar esta etapa; las instrucciones de limitarse a documentación del archivo fuente se conservan como referencia y no sustituyen esa autorización.

La fase 4 fue verificada técnicamente. Su [referencia](docs/04_ESTADISTICA_UNIVARIADA_BIVARIADA_CHI_CUADRADO.md), [contrato estadístico](docs/ESTADISTICA.md) y [matriz CEA01–CEA22](docs/ACEPTACION_ESTADISTICA.md) documentan descriptivos, denominadores, cruces, chi-cuadrado condicionado y Holm. `PROTOCOLO_ESTADISTICO_V1` sigue en borrador; los ejemplos de cálculo son sintéticos y no son resultados UMSS.

Después de revisar la [referencia de fase 5](docs/05_VISUALIZACION_COMPUTACIONAL.md), el usuario autorizó desarrollar («vamos»), superando su alcance exclusivamente documental. El [contrato visual](docs/VISUALIZACION.md) y la [aceptación visual](docs/ACEPTACION_VISUALIZACION.md) distinguen implementación, pruebas y pendientes metodológicos.

## Tecnologías

Java 21, Spring Boot 4.0.8, Spring Security, Thymeleaf, JDBC, Flyway y PostgreSQL. Maven Wrapper fija Maven 3.9.11. Backend, plantillas y recursos estáticos se distribuyen juntos en un JAR.

Apache ECharts 6.0.0 representa gráficos como recurso local del monolito. Sus avisos y licencia de dependencia se conservan en `src/main/resources/static/vendor/`; no asignan licencia al proyecto. El inicio continúa con Maven y no requiere un frontend separado ni npm.

## Despliegue provisional

La configuración para **Render Free + Neon Free** está preparada en `Dockerfile`, `.dockerignore`, `render.yaml` y el perfil `prod`. La [guía de despliegue](docs/DESPLIEGUE.md) explica el registro de cuentas, la conexión JDBC y la creación inicial del administrador desde el equipo local. Crear la cuenta en H2 no la copia a Neon.

El contenedor utiliza Java 21, un usuario sin privilegios, PostgreSQL obligatorio y cookies HTTPS. El perfil `prod` rechaza H2, perfiles locales y configuración inicial pública antes de crear la conexión. `/healthz` informa únicamente si el servicio y su conexión están disponibles. El inicio local con Maven conserva su funcionamiento habitual.

Preparar estos archivos no publica una URL: Render necesita el código en GitHub y las tres variables de conexión a la base del usuario. Las encuestas y el protocolo permanecen en borrador. Las fases siguientes se pueden desplegar sobre la misma base conservando datos y migraciones.

La versión de Spring Boot se contrastó con su [documentación oficial](https://docs.spring.io/spring-boot/4.0/system-requirements.html) y se resolvió desde Maven Central. No se utiliza una versión preliminar.

## Ejecutar en Windows

Requiere un JDK 21. Se inicia directamente con Maven Wrapper, el equivalente de un comando de desarrollo en este proyecto Java. Desde la raíz del proyecto, elige los comandos de tu terminal.

En **Símbolo del sistema (CMD)**, cuyo indicador suele ser `C:\ruta\al\proyecto>`:

```bat
.\mvnw.cmd "-Dspring-boot.run.profiles=dev" spring-boot:run
```

En **PowerShell**, cuyo indicador suele comenzar por `PS C:\ruta\al\proyecto>`:

```powershell
.\mvnw.cmd "-Dspring-boot.run.profiles=dev" spring-boot:run
```

El mismo comando funciona en ambas terminales: compila y arranca la aplicación con el perfil `dev`. `mvnw.cmd` usa la versión de Maven fijada en el repositorio y descarga lo necesario en la primera ejecución.

Usa comillas dobles alrededor de `-Dspring-boot.run.profiles=dev`. CMD conserva las comillas simples y Maven puede responder `Unknown lifecycle phase` si las recibe.

Abre <http://localhost:8080>. Para entrar al panel, visita <http://localhost:8080/admin>. Si todavía no hay administrador, el acceso al login te lleva a <http://localhost:8080/configuracion-inicial>. Elige un usuario y escribe dos veces tu contraseña, de al menos 12 caracteres y hasta 72 bytes UTF-8. El formulario propone `admin` como usuario, pero puedes cambiarlo; no hay una contraseña predeterminada.

La cuenta se crea una sola vez en la tabla `administrator`. Se guarda el usuario y un hash BCrypt de la contraseña; el hash es irreversible y no almacena la contraseña original. Después inicia sesión con las credenciales elegidas. Tanto la cuenta como las respuestas permanecen después de detener y volver a iniciar la aplicación con la misma base de datos. Detén la aplicación con `Ctrl+C`.

Las antiguas variables `ADMIN_USERNAME` y `ADMIN_PASSWORD` ya no configuran el panel ni se importan. La cuenta anterior estaba en memoria: al actualizar, crea la cuenta desde el formulario y puedes elegir las mismas credenciales.

Este inicio activa el perfil `dev`, usa H2 en modo de compatibilidad con PostgreSQL y guarda datos locales en `data/private/`, fuera del versionado. Escucha exclusivamente en la máquina local. Es un modo de desarrollo; la base principal del proyecto es PostgreSQL.

El script `scripts/iniciar.ps1` queda como atajo opcional al mismo comando, disponible desde PowerShell. Solo ejecuta Maven con el perfil elegido; no pide credenciales ni modifica variables de entorno.

```powershell
.\scripts\iniciar.ps1
```

En Linux/macOS, el equivalente es `./mvnw -Dspring-boot.run.profiles=dev spring-boot:run`, con Java 21. La cuenta se crea desde el mismo formulario local.

## PostgreSQL

Crea una base `estudio_mercado` y un usuario con permisos sobre ella usando tu instalación de PostgreSQL. Configura las siguientes variables de entorno:

| Variable | Uso |
| --- | --- |
| `DATABASE_URL` | URL JDBC, por ejemplo `jdbc:postgresql://localhost:5432/estudio_mercado` |
| `DATABASE_USERNAME` | Usuario de PostgreSQL |
| `DATABASE_PASSWORD` | Contraseña de PostgreSQL |
| `PORT` | Puerto HTTP; predeterminado `8080` |
| `SESSION_COOKIE_SECURE` | `true` cuando la aplicación se sirva mediante HTTPS |

El archivo `.env.example` enumera estas variables como referencia: Spring no lo carga automáticamente. Las migraciones Flyway crean las tablas en el primer inicio.

La contraseña de PostgreSQL permite que la aplicación se conecte a la base; es distinta de la contraseña del administrador del panel. Configura las variables de base de datos en la terminal o en el servicio que arranque la aplicación.

Para crear el primer administrador en PostgreSQL, con esas variables ya configuradas, ejecuta:

```powershell
.\mvnw.cmd "-Dspring-boot.run.profiles=setup" spring-boot:run
```

El perfil `setup` usa PostgreSQL, escucha en `127.0.0.1` y habilita la configuración inicial únicamente desde la máquina local. Abre <http://localhost:8080/configuracion-inicial>, crea la cuenta y detén la aplicación con `Ctrl+C`. Reinicia con las mismas variables de base de datos, sin el perfil `setup`:

```powershell
.\mvnw.cmd spring-boot:run
```

En el inicio normal con PostgreSQL, el formulario de configuración inicial está deshabilitado. Las encuestas siguen siendo públicas y el panel utiliza la cuenta guardada en la base de datos. La configuración inicial tampoco permite crear una segunda cuenta una vez que existe un administrador.

H2 de desarrollo y PostgreSQL son bases separadas: la cuenta y las respuestas no se copian entre ellas. Cada administrador pertenece a la base a la que se conectó la aplicación al crearlo. La primera versión tiene una única cuenta con rol `ADMIN`; administración de usuarios y recuperación de contraseñas quedan para otro incremento.

## Verificación

```powershell
.\mvnw.cmd verify
```

La suite de fase 3 pasó **159 pruebas en la ejecución H2 y 159 en la ejecución con PostgreSQL 18.6**, sin fallos, errores ni omisiones, el 2026-10-13. Incluye 57 casos nuevos de `PhaseThreeDataIntegrationTest` y los 102 casos anteriores. PostgreSQL se verificó en una instancia temporal y una base exclusiva de pruebas. Las pruebas de reinicio y de actualización de una base histórica utilizan H2 aislado incluso cuando el resto de la ejecución apunta a PostgreSQL. La revisión del JavaScript de fase 2 pasó 58 aserciones y la comprobación de sintaxis.

La fase 4 amplió la suite a **239 pruebas, todas correctas en H2 y en la ejecución con PostgreSQL 18.6**, el 2026-10-13. Incluye 46 casos de integración estadística, 34 comprobaciones matemáticas y los 159 casos anteriores. Verifica denominadores, escalas, textos revisados sin temas, pares válidos, chi-cuadrado, Holm, datos históricos, integridad, persistencia, permisos, CSRF y páginas/exportaciones de ambos cuestionarios. La instancia temporal de PostgreSQL se cerró al terminar. La [matriz de estadística](docs/ACEPTACION_ESTADISTICA.md) conserva las decisiones metodológicas y los entregables posteriores pendientes.

La fase 5 amplió la suite a **288 pruebas correctas en H2 y 288 en la ejecución con PostgreSQL 18.6**, el 2026-10-14: incorpora 37 casos de integración visual y 12 de selección/servicio. También pasaron nueve pruebas JavaScript. Chrome comprobó las siete vistas a 360, 768 y 1280 píxeles, modos de mapas, teclado, comparación, consultas fuera de orden y errores, usando HTML/JSON sintéticos de MockMvc y recursos reales del monolito. El [registro visual](docs/ACEPTACION_VISUALIZACION.md) distingue esta evidencia de la revisión pendiente con lector de pantalla, zoom y auditoría WCAG completa.

La preparación del despliegue pasó **316 pruebas en H2**, incluidos 28 casos nuevos de configuración, rechazo previo de conexiones inseguras y disponibilidad HTTP, el 2026-10-14. La imagen Docker se construyó correctamente; las comprobaciones del contenedor y el estado de publicación se registran en [DESPLIEGUE.md](docs/DESPLIEGUE.md). Esto no acredita conexión a una cuenta de Render o Neon todavía.

La fase 3 verifica snapshots, huellas, reintentos, orígenes separados, revisiones, codificación, aplicabilidad no verificable, privacidad, cardinalidad, rollback y descargas reproducibles. La [matriz de aceptación de datos](docs/ACEPTACION_DATOS.md) distingue criterios cubiertos, parciales y pendientes. Los 33 casos de `PhaseTwoAcceptanceIntegrationTest` siguen comprobando estados, rutas de experiencia, meses, asignaturas, competencias, comprobante y conservación histórica, según su [matriz de cuestionarios](docs/ACEPTACION_CUESTIONARIOS.md). Estas pruebas técnicas no aprueban metodológica o institucionalmente el instrumento.

La suite aplica las migraciones reales y verifica rutas públicas, acceso administrativo, login desde la base, CSRF, validación, persistencia, duplicación de envíos, saltos, criterios únicos, escape HTML y exportación CSV. La configuración inicial comprueba acceso local, prohibición de reemplazar una cuenta y creación concurrente. Una prueba arranca y cierra dos veces la aplicación usando H2 en archivo para comprobar que el administrador y su hash persisten entre reinicios.

Para repetir las pruebas con PostgreSQL, utiliza una base creada exclusivamente para pruebas, configura `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME` y `TEST_DATABASE_PASSWORD` y ejecuta el mismo `verify`. La suite limpia respuestas, cuentas, conjuntos y revisiones de esa base entre casos; no utilices una base con datos de campo.

Si Java muestra `PKIX path building failed` en este equipo Windows, Maven puede utilizar los certificados del sistema para ese proceso:

```powershell
$env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
.\mvnw.cmd verify
```

Este ajuste se configura explícitamente en la terminal cuando sea necesario. La primera ejecución del Wrapper descarga Maven y las dependencias.

## Organización

```text
src/main/java/bo/edu/umss/mercado/
  security/   Autenticación y autorización
  survey/     Catálogo versionado y validación de cuestionarios
  response/   Persistencia y exportación de respuestas
  data/       Contratos de captura, estudio e instrumento congelados
  wrangling/  Preparación reproducible, revisiones y conjuntos
  statistics/ Descriptivos, cruces, aplicabilidad y análisis congelados
  visualization/ Selección desde conjuntos y contrato de gráficos/tablas
  web/        Controladores públicos y administrativos
src/main/resources/
  db/migration/   Migraciones Flyway
  templates/     Páginas Thymeleaf
  static/        CSS y JavaScript locales
src/test/        Pruebas de integración
docs/            Decisiones, referencia y requisitos del instrumento
```

## CSV

La exportación de respuestas originales es UTF-8 y tiene una fila por valor observado. Las selecciones múltiples conservan filas separadas y su orden. Incluye identificador, encuesta, versión, fecha con zona horaria, pregunta, posición, valor original, enunciado y etiqueta. Los estados de las preguntas se guardan en la base y se consultan en el detalle administrativo; este CSV conserva su formato de valores originales y no constituye una matriz de estados ni una matriz analítica depurada.

Los valores que una hoja de cálculo podría interpretar como fórmulas se exportan con un apóstrofe inicial; el valor guardado en la base permanece intacto. Esta exportación contiene respuestas originales y permanece separada de las matrices preparadas de fase 3.

## Preparación de datos de fase 3

Abre `/admin/datos` desde el panel para generar conjuntos de borrador y descargarlos como JSON, productos CSV o ZIP con manifiesto. Las matrices ordinarias tienen una fila por envío, estados e indicadores de selecciones; omiten contactos, nombre organizacional y textos originales. Estos conjuntos son la entrada preparada para las etapas estadísticas y de reportes posteriores.

Los envíos comienzan como `NO_DECLARADO`. En el detalle protegido de una respuesta, el administrador revisa su origen real o sintético, registra correcciones con motivo y codifica textos mediante temas/libro de códigos. Cada ejecución congela esas revisiones y entradas; descargar una ejecución anterior conserva su contenido, aunque luego existan otras revisiones.

El instrumento y estudio actuales siguen en `BORRADOR`; preparar modo `OFICIAL` exige origen `REAL` y estudio/versiones `APROBADA`. Clasificar una respuesta real no otorga aprobación institucional ni verifica elegibilidad. Los históricos conservan sus valores y las limitaciones de metadata, sin inventar estados, origen o conversiones de años a meses.

Para probar el flujo, responde una encuesta y entra a su detalle administrativo. Usa el enlace de revisión para clasificarla como sintética con un motivo; luego crea un conjunto `SINTETICO` en modo `BORRADOR` desde `/admin/datos` y descarga sus productos. Reservar la clasificación real para respuestas de campo revisadas mantiene separados los datos de prueba. La cobertura y evidencia están en [ACEPTACION_DATOS.md](docs/ACEPTACION_DATOS.md). Los conjuntos preparados no resuelven la unidad de análisis de empleadores ni la política de varias participaciones.

## Análisis estadístico de fase 4

Con la aplicación iniciada manualmente con Maven, entra al panel y abre **Preparar datos → un conjunto → Analizar este conjunto**. Pulsa **Calcular y guardar análisis** para consultar frecuencias, resúmenes numéricos y ordinales, selecciones múltiples, temas codificados y cruces. Puedes reabrir los resultados desde **Análisis guardados** y descargarlos en JSON o en los nueve productos CSV.

Para probar sin mezclar datos de campo, clasifica las respuestas de prueba como `SINTETICO` desde su revisión administrativa y prepara un conjunto de ese origen en modo `BORRADOR`. Los análisis consumen ese conjunto guardado; una corrección posterior no modifica los resultados anteriores.

Puedes dejar vacíos los campos de independencia para consultar los descriptivos. Chi-cuadrado requiere documentar la unidad y la independencia de las observaciones, además de cumplir las condiciones de variación y frecuencias esperadas. Con pocas respuestas es normal obtener una prueba suspendida con su motivo. El protocolo permanece como borrador y no concede aprobación institucional. La antigüedad derivada sigue pendiente de definición.

El contrato está en [ESTADISTICA.md](docs/ESTADISTICA.md) y la evidencia en [ACEPTACION_ESTADISTICA.md](docs/ACEPTACION_ESTADISTICA.md). El dashboard de fase 5 permite consultar esos resultados mediante gráficos; los reportes automáticos mantienen su incremento posterior.

## Visualización de fase 5

Inicia la aplicación con el mismo comando manual de Maven. Desde **Panel → Análisis guardados**, abre un análisis y pulsa **Ver gráficos**. La ruta es `/admin/estadisticas/{id}/visualizacion`; el servidor protege también sus datos JSON mediante ADMIN.

Las siete vistas cubren resumen, perfil y trayectoria de titulados, formación continua, empleadores y demanda, valoraciones, cruces y calidad/cobertura. Selecciona filtros y pulsa **Aplicar filtros** para cambiar la selección efectiva; **Restablecer filtros** recupera la base de ese análisis. Cada gráfico conserva una tabla equivalente, unidades, estado y base. Los resultados son de borrador; origen sintético y origen sin verificar se identifican explícitamente.

Las cifras proceden del backend y del conjunto guardado, verificado frente a las huellas del análisis. Filtrar no incorpora correcciones posteriores ni consulta respuestas actuales. Los estratos de estudio/versión permanecen separados. La comparación de hasta dos grupos conserva sus bases y alcance exploratorio. El radar muestra acuerdo parcial o total sobre respuestas sustantivas, con `No sabe` separado; sus dos paneles de cinco criterios facilitan lectura sin crear índices ni completar ejes ausentes.

Para el filtro por período, prepara un conjunto nuevo con reglas `2026-10-wrangling-3` y crea su análisis. Los miembros nuevos conservan fecha de recepción; los días inicial/final son inclusivos en `America/La_Paz`. Los bundles antiguos se pueden consultar, pero si carecen de esas fechas el filtro pide un conjunto nuevo y no modifica sus históricos. El período de recepción es diferente del año de egreso. Antigüedad, cohortes y comparabilidad institucional mantienen sus definiciones pendientes.

## Próximos incrementos

1. Resolver y aprobar los campos, escalas, obligatoriedad, saltos y condiciones pendientes del instrumento, registrados en la matriz de fase 2.
2. Configurar la base PostgreSQL del equipo y fijar la versión para trabajo de campo; los campos antes omitidos y las matrices acordadas para borrador ya están incorporados.
3. Cerrar comparabilidad semántica, libro de códigos temático y unidad de análisis sobre el pipeline de preparación ya ejecutable; importaciones externas quedan fuera de este incremento.
4. Resolver diseño muestral, independencia, cohortes y alcance institucional sobre el motor estadístico de fase 4 ya verificado.
5. Completar la revisión con lector de pantalla, zoom y accesibilidad, y cerrar las definiciones metodológicas que restringen los indicadores visuales.
6. Automatización de reportes en el backend y exportación Excel.
7. Definir e implementar simulación académica con entradas, reglas y supuestos; preparar el informe final.
8. Despliegue, usuarios y operación; Render y Neon son opciones previstas que se revisarán en esa etapa.

## Repositorio y colaboración

Un único repositorio Git, rama principal `main`, remoto `origin`: <https://github.com/joseAM34/tss-act2.v2>. Crear ramas descriptivas para siguientes incrementos y revisar cambios mediante pull requests.

```powershell
git switch -c feat/analisis-descriptivo
git status --short
```

Los secretos, datos locales, exportaciones y fuentes privadas están excluidos del versionado. No se ha asignado licencia. El PDF permanece en su ubicación original; `docs/01_ESTABLECER_REPOSITORIO.md`, `docs/02_REQUISITOS_CUESTIONARIOS.md`, `docs/03_DATOS_Y_DATA_WRANGLING.md`, `docs/04_ESTADISTICA_UNIVARIADA_BIVARIADA_CHI_CUADRADO.md` y `docs/05_VISUALIZACION_COMPUTACIONAL.md` conservan las referencias de los cinco pasos. Sus instrucciones documentales se distinguen de la autorización posterior del usuario para implementar.
