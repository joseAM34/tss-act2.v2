# Despliegue provisional

Guía para publicar el monolito antes de la fase de reportes. La aplicación conserva sus cuestionarios y protocolo en `BORRADOR`; publicar una URL no aprueba el instrumento ni convierte las respuestas en datos reales. Cada nuevo envío comienza como `NO_DECLARADO` hasta su revisión administrativa.

El usuario eligió **Render Free para el monolito Java 21 con Docker** y **Neon Free para PostgreSQL**. Esta guía permite crear las cuentas y configurar el despliegue; no acredita que se hayan conectado servicios ni publicado una URL. La documentación de los proveedores se consultó el **2026-10-14**; sus planes y límites pueden cambiar.

## Qué se publica

Render construye el `Dockerfile` de la raíz y ejecuta el JAR con el perfil `prod`. Backend, encuestas, panel, plantillas y ECharts permanecen en una sola aplicación. Render recomienda Docker para aplicaciones JVM. [Docker en Render](https://render.com/docs/docker).

PostgreSQL conserva administrador, respuestas, revisiones, conjuntos y análisis. H2 y los archivos locales de desarrollo quedan separados; no se copian automáticamente a la base remota. El sistema de archivos de Render es efímero, por lo que la persistencia del proyecto debe residir en PostgreSQL. [Persistencia y planes gratuitos de Render](https://render.com/docs/free).

## 1. Crear la base de PostgreSQL

1. Abre [Neon Console](https://console.neon.tech/) y elige **Sign up**. Puedes usar tu cuenta de GitHub cuando el formulario lo permita. Completa el alta y selecciona el plan **Free**; no necesitas activar otros productos de Neon para esta aplicación.
2. Crea un proyecto, por ejemplo `umss-sistemas-mercado`. Elige una región cercana a la que usarás en Render, preferentemente compartida por ambos proveedores; la disponibilidad depende de sus formularios actuales.
3. Conserva una rama y una base exclusivas para esta aplicación; el proyecto puede empezar con la base `neondb` que crea Neon. No uses una base de otra aplicación.
4. Abre **Connect**, selecciona la rama, la base y el rol. Para este incremento utiliza una conexión **directa**, desactivando **Connection pooling**. Neon añade `-pooler` al hostname de las conexiones agrupadas; el hostname directo se obtiene desde el mismo diálogo. [Conexiones de Neon](https://neon.com/docs/connect/connect-from-any-app).
5. Selecciona **Java** para consultar el formato JDBC. En este proyecto el usuario y la contraseña se proporcionan por separado. [Conexión Java de Neon](https://neon.com/docs/guides/java).

La URL propuesta usa `sslmode=verify-full` para comprobar certificado y hostname, con `sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` para utilizar los certificados de confianza de la JVM. No requiere descargar certificados privados de la base ni desactivar la validación. [TLS y fábricas SSL del driver PostgreSQL](https://jdbc.postgresql.org/documentation/ssl/).

Configura estos tres valores, sustituyendo los ejemplos por los datos de tu proyecto:

| Variable | Valor de ejemplo |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://ep-tu-endpoint.tu-region.aws.neon.tech:5432/neondb?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` |
| `DATABASE_USERNAME` | El rol seleccionado en Neon, por ejemplo `neondb_owner` |
| `DATABASE_PASSWORD` | La contraseña de ese rol, guardada como secreto |

`DATABASE_URL` debe empezar por `jdbc:postgresql://`. No pegues directamente una URI `postgresql://usuario:contraseña@...`; tampoco incluyas `user` o `password` en la URL del proyecto. La contraseña de PostgreSQL conecta la aplicación con la base y es distinta de la contraseña del administrador del panel.

## 2. Crear el administrador desde tu equipo

Antes de publicar el servicio, conecta la aplicación local a **la misma base de Neon** y crea su primer administrador. Requiere JDK 21 y acceso de red a esa base.

En PowerShell, desde la raíz del repositorio, configura manualmente las variables de PostgreSQL en esa terminal:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://ep-tu-endpoint.tu-region.aws.neon.tech:5432/neondb?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory'
$env:DATABASE_USERNAME = 'tu_rol_neon'
$env:DATABASE_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Contraseña de PostgreSQL' -AsSecureString)).Password
$env:SESSION_COOKIE_SECURE = 'false'
```

La última asignación permite usar el formulario local mediante HTTP; el perfil `prod` del servicio publicado exige cookies seguras. El comando de contraseña solicita únicamente la credencial de PostgreSQL sin escribirla en el historial de comandos. No envíes credenciales por el chat ni las guardes en archivos versionados. `.env.example` es una referencia y Spring no lo carga automáticamente.

Arranca de forma manual con Maven:

```powershell
.\mvnw.cmd "-Dspring-boot.run.profiles=setup" spring-boot:run
```

Flyway aplica las migraciones pendientes a la base configurada antes de habilitar la aplicación. El perfil `setup` escucha en `127.0.0.1`; abre <http://localhost:8080/configuracion-inicial> desde ese mismo equipo y elige el usuario y la contraseña del administrador. No existe contraseña predeterminada: se guarda un hash BCrypt en la tabla `administrator` y no se obtiene la credencial administrativa desde variables de entorno.

Si tu terminal tiene `PORT` definido con otro valor, utiliza ese puerto para la URL local o configura explícitamente `PORT=8080` antes del inicio. Cuando termine la creación, detén la aplicación con **Ctrl+C**. La cuenta permanece en PostgreSQL y no puede crearse una segunda mediante ese formulario.

Si ya existe un administrador en esa base, conserva esa cuenta y pasa al siguiente paso. Una cuenta creada en H2 no aparece en Neon: pertenece a una base distinta.

## 3. Publicar la versión revisada del repositorio

Render construye lo que existe en GitHub, no los archivos que siguen solamente en este equipo. La rama utilizada debe contener el `Dockerfile`, `.dockerignore`, configuración `prod`, endpoint de salud y cambios funcionales que quieras publicar.

Revisa `git status`, diferencias y archivos seleccionados antes de registrar cambios. Conserva `origin`, el historial y la rama principal `main`; los datos de campo, exportaciones, bases locales y secretos permanecen fuera del historial. El repositorio del proyecto es [joseAM34/tss-act2.v2](https://github.com/joseAM34/tss-act2.v2). Esta guía no ejecuta commits ni push.

## 4. Crear el servicio en Render

1. Abre [Render Dashboard](https://dashboard.render.com/) y elige **Sign up** para crear tu cuenta. Puedes utilizar GitHub cuando se ofrezca esa opción. Tras el alta, elige **New → Web Service**.
2. Conecta tu cuenta de GitHub y selecciona `joseAM34/tss-act2.v2`, rama `main`. También existe la conexión por URL de repositorio público; el proveedor explica sus diferencias. [Servicios web de Render](https://render.com/docs/web-services).
3. Selecciona **Language: Docker**, raíz del repositorio y **Dockerfile Path: ./Dockerfile**. Conserva el comando del contenedor; no necesitas configurar `npm`, un frontend separado ni un comando de inicio con Maven en Render.
4. Elige el plan **Free** acordado para la demostración provisional. Revisa sus límites en el formulario; esta preparación no contrata recursos de pago.
5. En **Environment**, configura `DATABASE_URL`, `DATABASE_USERNAME` y `DATABASE_PASSWORD` con la misma base usada para crear el administrador. Introduce el secreto desde el panel de Render.
6. En **Health Check Path**, indica `/healthz`. No añadas `/admin` ni el formulario de configuración inicial como comprobación de salud. [Comprobaciones HTTP de Render](https://render.com/docs/health-checks).
7. Para controlar cada publicación mientras seguimos desarrollando, configura **Auto-Deploy: Off**. Render permite activar despliegues desde **Manual Deploy → Deploy latest commit** cuando la versión esté revisada. [Despliegues manuales de Render](https://render.com/docs/deploys#manual-deploys).
8. Crea el servicio y consulta **Deploys** y **Logs** hasta que finalice el arranque. Copia la URL HTTPS que Render asigne al servicio; todavía no existe una URL del proyecto indicada por esta guía.

Render proporciona `PORT`, normalmente `10000`, y requiere que el servicio escuche en `0.0.0.0`. La configuración `prod` usa ese puerto. Render termina TLS en su proxy y entrega la solicitud a la aplicación por HTTP; el perfil publicado procesa las cabeceras reenviadas para conservar HTTPS y exige cookies de sesión seguras. [Puerto y HTTPS en Render](https://render.com/docs/web-services).

En el servicio público utiliza el perfil `prod` del contenedor. La configuración inicial queda deshabilitada: no publiques con `dev` ni `setup`, no actives `app.setup.local-enabled` y no crees variables `ADMIN_USERNAME` o `ADMIN_PASSWORD`.

El perfil `prod` interpreta las cabeceras de HTTPS con el soporte nativo de Tomcat. Los perfiles locales `dev` y `setup` las ignoran. Antes de abrir conexiones, el arranque rechaza H2, credenciales ausentes, cookies inseguras, configuración inicial habilitada o la combinación de `prod` con perfiles locales.

## 5. Comprobar la URL publicada

Después de obtener la URL real del servicio:

1. Abre `/healthz` y verifica un estado HTTP `200`; el endpoint devuelve únicamente un estado técnico y no revela credenciales, respuestas ni configuración interna.
2. Abre el inicio y ambas encuestas sin sesión. Completa un envío de prueba y revisa que aparezca en el panel; su origen inicial debe ser `NO_DECLARADO`. Clasifícalo como `SINTETICO` desde la revisión administrativa con un motivo.
3. Abre `/admin` sin sesión y verifica que solicita autenticación. Entra con la cuenta creada localmente contra Neon, comprueba respuestas y gráficos y cierra sesión.
4. Reinicia el servicio de Render y comprueba que la cuenta y el envío siguen presentes. Las sesiones pueden terminar al reiniciar: iniciar sesión nuevamente no significa perder los datos guardados.
5. Conserva separadas las respuestas de prueba y las de campo al preparar conjuntos. No conviertas pruebas en `REAL` ni apruebes estudio, instrumento o protocolo como parte del despliegue.

Un build correcto y una respuesta de `/healthz` no sustituyen la comprobación de encuestas, autenticación y persistencia en la URL real. Las pruebas locales previas no acreditan por sí solas que Render y Neon estén conectados correctamente.

## Límites del despliegue provisional

Render Free suspende el servicio tras **15 minutos sin tráfico**, y la primera visita después puede tardar aproximadamente un minuto mientras arranca. Su almacenamiento local es efímero y no admite disco persistente. El PostgreSQL gratuito de Render **expira a los 30 días**; por eso esta guía propone una base externa en Neon. También existen límites de horas, transferencia y compilación: revisa el uso del panel. Render presenta este plan para pruebas y demostraciones. [Límites oficiales de Render Free](https://render.com/docs/free).

Al consultar las condiciones, Neon Free incluía **1 GB de PostgreSQL por proyecto**, **100 CU-horas de cómputo por proyecto al mes** y **5 GB de transferencia pública al mes**. Su cómputo se suspende tras cinco minutos de inactividad; los límites pueden impedir consultas o nuevas escrituras cuando se agotan. Revisa los valores de tu cuenta al crear el proyecto. [Planes de Neon](https://neon.com/docs/introduction/plans), [precios y cuotas](https://neon.com/pricing).

**Duración:** los planes actuales de Render Web Service Free y Neon Free funcionan con cuotas de uso, sin una caducidad de 30 días indicada para estos dos servicios. Puedes mantener la demostración mientras continúen disponibles y respetes sus condiciones. La fecha de 30 días corresponde al **PostgreSQL gratuito de Render**, que esta guía no utiliza. La suspensión por inactividad no borra las filas guardadas en Neon. [Condiciones de Render Free](https://render.com/docs/free), [condiciones de Neon Free](https://neon.com/docs/introduction/plans).

Este incremento mantiene una instancia del monolito. Antes de una recolección de campo sostenida, define disponibilidad, presupuesto, copias de seguridad y recuperación acordes al estudio; los reinicios afectan sesiones y una demostración gratuita no ofrece por sí sola continuidad de servicio.

## Verificación de la preparación

El 2026-10-14 pasó `Maven verify` con 316 pruebas correctas en H2, incluidos 28 casos nuevos de configuración y disponibilidad. Se construyó la imagen Docker y se inició con usuario `10001:10001`, 512 MB y PostgreSQL 18 en una red temporal de pruebas. El servicio respondió `200` en `/healthz`, en una encuesta pública y en ECharts; administración redirigió al login HTTPS con cookie segura, y la configuración inicial pública devolvió `403`. Los registros están en `.codex-tmp/deploy-verify.log`, `deploy-docker-build.log` y `deploy-container-smoke.json`, excluidos del historial.

La comprobación utilizó exclusivamente una base temporal. No confirma aún conexión a Neon, publicación en Render, rendimiento con datos de campo o alta de una cuenta administrativa del usuario en la base remota.

En este equipo Norton inspecciona HTTPS y la compilación Docker necesitó una CA pública ya confiable localmente, mediante un secreto opcional `local_ca` de BuildKit. Esa CA se usa solo en la etapa de compilación; no se incorpora al repositorio, al contexto ni a la imagen de ejecución. Render construye sin ese secreto utilizando sus certificados originales. Se conserva la verificación TLS.

## Actualizaciones y conservación de datos

Para la fase siguiente, conserva la misma base de PostgreSQL y publica el código revisado mediante un despliegue manual. Flyway aplica migraciones nuevas; no modifiques una migración ya aplicada ni elimines la base para actualizar. Antes de una actualización de esquema, guarda una copia privada de PostgreSQL y verifica que el procedimiento de restauración funciona; una reversión de código no deshace automáticamente una migración.

Los conjuntos y análisis anteriores conservan sus bundles, revisiones y huellas. El despliegue no los reconstruye desde respuestas actuales, no migra datos H2 y no clasifica orígenes automáticamente. Los datos privados y respaldos se almacenan fuera del repositorio.

## Problemas frecuentes

| Síntoma | Qué revisar |
| --- | --- |
| Render no detecta el puerto | Perfil `prod`, escucha en `0.0.0.0` y variable `PORT`; no usar el perfil `setup` en el contenedor. |
| El servicio no conecta a PostgreSQL | Formato JDBC, hostname directo, rol/contraseña, base seleccionada, parámetros TLS y estado/cuotas de Neon. |
| Usuario del desarrollo rechazado | H2 y Neon son bases distintas; usa el administrador creado con `setup` contra esa misma base remota. |
| Login se pierde en cada navegación | Acceder mediante la URL HTTPS y conservar la configuración de proxy y cookies seguras del perfil `prod`. |
| Falta un cambio publicado | Verificar que el commit existe en GitHub y que Render desplegó ese commit de la rama elegida. |
| La primera visita tarda | Comprobar si Render Free y el cómputo de Neon estaban suspendidos por inactividad. |
| Configuración inicial no disponible en la URL pública | Es el comportamiento previsto; se realiza una sola vez desde la aplicación local con el perfil `setup`. |
