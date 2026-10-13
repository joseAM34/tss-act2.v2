# Estudio de mercado UMSS

Aplicación monolítica de Ingeniería en Sistemas de la Universidad Mayor de San Simón. Reúne las encuestas a titulados y empleadores, el backend y las páginas en un único proyecto y repositorio.

Los entregables finales son la herramienta de recolección de datos y el documento de Análisis de Mercado para apoyar el Plan de Desarrollo Estratégico y la acreditación ARCUSUR.

## Estado actual

Primera implementación para revisión del instrumento. Incluye:

- Dos encuestas públicas con opciones controladas, respuestas múltiples, saltos condicionales y validación en el servidor.
- Los diez criterios de empleadores una sola vez, conservando la escala y la categoría «No sabe».
- Persistencia transaccional y prevención de envíos duplicados al reenviar el mismo formulario.
- Panel con inicio de sesión, conteos, las últimas 100 respuestas, detalle y exportación CSV.
- Registro de versión, fecha, consentimiento, enunciado y etiquetas de cada respuesta para trazabilidad.
- Protección del panel y exportación en el backend; formularios con CSRF y contenido escapado.

Las encuestas son borradores. Antes de iniciar la recolección definitiva hay que resolver los campos ambiguos y aprobar el instrumento descrito en [docs/INSTRUMENTO.md](docs/INSTRUMENTO.md). La protección por contraseña no convierte en anónimos los datos de contacto que los formularios solicitan.

## Tecnologías

Java 21, Spring Boot 4.0.8, Spring Security, Thymeleaf, JDBC, Flyway y PostgreSQL. Maven Wrapper fija Maven 3.9.11. Backend, plantillas y recursos estáticos se distribuyen juntos en un JAR.

La versión de Spring Boot se contrastó con su [documentación oficial](https://docs.spring.io/spring-boot/4.0/system-requirements.html) y se resolvió desde Maven Central. No se utiliza una versión preliminar.

## Ejecutar en Windows

Requiere un JDK 21. Desde la raíz del proyecto, en PowerShell:

```powershell
.\scripts\iniciar.ps1
```

El script pide una contraseña de al menos 12 caracteres sin mostrarla en pantalla. El usuario predeterminado es `admin`. La contraseña solo se configura en el proceso de ejecución; vuelve a usar la misma en próximos inicios si quieres mantener tus credenciales.

Abre <http://localhost:8080>. El panel está en <http://localhost:8080/admin>. Detén la aplicación con `Ctrl+C`.

Este inicio activa el perfil `dev`, usa H2 en modo de compatibilidad con PostgreSQL y guarda datos locales en `data/private/`, fuera del versionado. Escucha exclusivamente en la máquina local. Es un modo de desarrollo; la base principal del proyecto es PostgreSQL.

Para ejecutar directamente con Maven Wrapper, configura `ADMIN_PASSWORD` como variable de entorno y utiliza:

```powershell
.\mvnw.cmd '-Dspring-boot.run.profiles=dev' spring-boot:run
```

En Linux/macOS, el equivalente es `./mvnw -Dspring-boot.run.profiles=dev spring-boot:run`, con Java 21 y `ADMIN_PASSWORD` definida en el entorno.

## PostgreSQL

Crea una base `estudio_mercado` y un usuario con permisos sobre ella usando tu instalación de PostgreSQL. Configura las siguientes variables de entorno:

| Variable | Uso |
| --- | --- |
| `DATABASE_URL` | URL JDBC, por ejemplo `jdbc:postgresql://localhost:5432/estudio_mercado` |
| `DATABASE_USERNAME` | Usuario de PostgreSQL |
| `DATABASE_PASSWORD` | Contraseña de PostgreSQL |
| `ADMIN_USERNAME` | Usuario del panel; predeterminado `admin` |
| `ADMIN_PASSWORD` | Contraseña del panel; obligatoria, sin valor predeterminado |
| `PORT` | Puerto HTTP; predeterminado `8080` |
| `SESSION_COOKIE_SECURE` | `true` cuando la aplicación se sirva mediante HTTPS |

El archivo `.env.example` enumera estas variables como referencia: Spring no lo carga automáticamente. Las migraciones Flyway crean las tablas en el primer inicio.

En PowerShell, inicia con PostgreSQL mediante:

```powershell
.\scripts\iniciar.ps1 -Perfil postgres
```

El administrador se configura mediante variables de entorno y su contraseña se compara usando BCrypt. La primera versión tiene una única cuenta administrativa; administración de usuarios y recuperación de contraseñas quedan para otro incremento.

## Verificación

```powershell
.\mvnw.cmd verify
```

Las pruebas de integración usan H2 en memoria, aplican las migraciones reales y verifican rutas públicas, acceso administrativo, login, CSRF, validación, persistencia, duplicación de envíos, saltos, criterios únicos, escape HTML y exportación CSV. El primer incremento pasó 43 pruebas tanto en H2 como en una instancia temporal de PostgreSQL 18.6. También se comprobó el arranque del JAR y la respuesta HTTP de las páginas públicas y del acceso al panel.

Para repetir las pruebas con PostgreSQL, utiliza una base creada exclusivamente para pruebas, configura `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME` y `TEST_DATABASE_PASSWORD` y ejecuta el mismo `verify`. La suite limpia las respuestas de esa base entre casos; no utilices una base con datos de campo.

Si Java muestra `PKIX path building failed` en este equipo Windows, Maven puede utilizar los certificados del sistema para ese proceso:

```powershell
$env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
.\mvnw.cmd verify
```

El script de inicio ya aplica esta configuración. La primera ejecución del Wrapper descarga Maven y las dependencias.

## Organización

```text
src/main/java/bo/edu/umss/mercado/
  security/   Autenticación y autorización
  survey/     Catálogo versionado y validación de cuestionarios
  response/   Persistencia y exportación de respuestas
  web/        Controladores públicos y administrativos
src/main/resources/
  db/migration/   Migraciones Flyway
  templates/     Páginas Thymeleaf
  static/        CSS y JavaScript locales
src/test/        Pruebas de integración
docs/            Decisiones, referencia y requisitos del instrumento
```

## CSV

La exportación es UTF-8 y tiene una fila por valor de respuesta. Las selecciones múltiples conservan filas separadas y su orden. Incluye identificador, encuesta, versión, fecha con zona horaria, pregunta, posición, valor original, enunciado y etiqueta.

Los valores que una hoja de cálculo podría interpretar como fórmulas se exportan con un apóstrofe inicial; el valor guardado en la base permanece intacto. Esta exportación contiene respuestas originales, no una matriz analítica depurada. El pipeline de Data Wrangling se desarrollará posteriormente.

## Próximos incrementos

1. Aprobar los campos, escalas, saltos y condiciones pendientes del instrumento.
2. Completar datos demográficos y preguntas omitidas por ambigüedad; configurar la base PostgreSQL del equipo.
3. Data Wrangling: originales, depuración, normalización, codificación, faltantes y duplicados con trazabilidad.
4. Estadística univariada y bivariada, matrices de contingencia y chi-cuadrado con condiciones de aplicabilidad y denominadores explícitos.
5. Visualización computacional interactiva, mapas de calor y radar.
6. Automatización de reportes en el backend y exportación Excel.
7. Definir e implementar simulación académica con entradas, reglas y supuestos; preparar el informe final.
8. Despliegue, usuarios y operación; Render y Neon son opciones previstas que se revisarán en esa etapa.

## Repositorio y colaboración

Un único repositorio Git, rama principal `main`, remoto `origin`: <https://github.com/joseAM34/tss-act2.v2>. Crear ramas descriptivas para siguientes incrementos y revisar cambios mediante pull requests.

```powershell
git switch -c feat/analisis-descriptivo
git status --short
```

Los secretos, datos locales, exportaciones y fuentes privadas están excluidos del versionado. No se ha asignado licencia. El PDF permanece en su ubicación original; el archivo de preparación se conserva en `docs/01_ESTABLECER_REPOSITORIO.md` como referencia del primer paso.
