# Paso 2 local: vista previa y versiones publicadas

**Actualización del paso 3:** la [recepción por versión y selección activa](RECEPCION_ENCUESTAS.md) ya está implementada en local. Este documento conserva el alcance y la evidencia del paso 2; las frases sobre recepción pendiente describen ese incremento anterior.

Fecha: 2026-10-14. El usuario autorizó continuar después de probar el editor: «al paso 2 pasemos». Se conserva su instrucción de trabajar primero en local y hacer push después. Esta entrega amplía el [editor de borradores](EDITOR_BORRADORES.md), siguiendo las etapas de publicación del [plan](ENCUESTAS_DINAMICAS_PLAN.md).

## Qué está implementado

- Vista previa administrativa del borrador, con los mismos controles Thymeleaf y condiciones del formulario existente.
- Comprobación de respuestas de prueba en el servidor, sin crear envíos, capturas, correcciones ni conjuntos analíticos.
- Revisión y publicación transaccional: conserva una definición fija identificada como `v1`, `v2`, etc.
- Consulta y vista previa de cada versión publicada, verificando contenido y huellas.
- Creación de un borrador sucesor de la misma encuesta, manteniendo sus preguntas y opciones; una versión publicada no es editable.
- Un borrador activo por encuesta administrado mediante una referencia y bloqueo transaccional. Solicitudes concurrentes de sucesora devuelven el mismo borrador.
- Cierre con fecha y evento administrativo, sin cambiar el contenido congelado ni sus huellas.
- Enlace público específico de versión. En este incremento es una página informativa: **todavía no recibe respuestas**. Una versión cerrada responde HTTP 410.

La recepción dinámica corresponde al siguiente paso. No se anuncian reportes, importaciones, homologación o análisis configurable terminados. Las encuestas actuales de titulados/empleadores siguen disponibles por sus rutas y catálogo originales.

## Cómo probarlo localmente

Detén la aplicación que esté ejecutándose con **Ctrl+C**. En PowerShell, desde la raíz del proyecto:

```powershell
mvn "-Dspring-boot.run.profiles=dev" spring-boot:run
```

Ese perfil utiliza H2 local. `application-local.properties` permanece privado y no se activa. Si eliges `local` conectado a Neon, la migración se ejecutará en Neon; para este recorrido conserva `dev`.

1. Abre `http://localhost:8080/admin` e inicia sesión con el administrador de la base local.
2. Entra a **Abrir editor de borradores** y abre el borrador que quieras probar.
3. Pulsa **Vista previa**. Marca opciones y comprueba las preguntas condicionales. **Comprobar prueba** valida sin guardar una participación.
4. Vuelve al borrador y pulsa **Revisar publicación**. Revisa título, descripción y cantidades; publica solo cuando estés conforme.
5. Pulsa **Publicar esta versión**. El editor muestra la versión fija y su enlace. Se exige al menos una pregunta y ninguna sección vacía.
6. Pulsa **Vista previa de esta versión** para explorarla. No hay botón de envío en esa vista congelada.
7. Pulsa **Crear o abrir nueva versión en borrador** para editar una sucesora. Si ya hay un borrador activo, abre ese mismo; no crea varios por repetir la acción.
8. Publica la sucesora y comprueba que aparecen `v1` y `v2` por separado, con sus propios títulos y definiciones.
9. En una versión publicada pulsa **Cerrar versión**. Su definición permanece consultable desde el panel y el enlace informa del cierre.

Las acciones de publicar y cerrar no se ejecutan por abrir una pantalla. Todos los cambios requieren POST administrativo con CSRF y revisión vigente.

## Qué cambia en la base

No se modifica V7 ni las migraciones anteriores. Se añade la migración Java **V8__SurveyPublication**, en `src/main/java/db/migration/`, que Flyway ejecuta durante el arranque.

V7 creó un CHECK anónimo que solo permitía `BORRADOR`; sus nombres generados son distintos en H2/PostgreSQL. V8 consulta los metadatos del esquema, identifica de forma única **ese CHECK**, sustituye únicamente esa restricción por los tres estados y agrega columnas. Si no puede identificarlo inequívocamente, falla en lugar de eliminar restricciones arbitrarias.

| Tabla | Adición |
| --- | --- |
| `survey_version` | Definición congelada y hash, número de publicación, fechas de publicación/cierre y referencia a la versión predecesora. |
| `survey` | Próximo número de publicación y referencia al borrador activo; los borradores existentes quedan vinculados sin reescribirlos. |
| `instrument_snapshot` | Al publicar, se registra una **pareja nueva** de slug/versión con el formato existente y estado metodológico `BORRADOR`. Los snapshots anteriores no se reemplazan. |
| `survey_definition_event` | Nuevos eventos `PUBLICAR`, `CREAR_SUCESORA` y `CERRAR`; se conservan los eventos anteriores y sus JSON/huellas. |

Se mantienen 19 tablas de aplicación. No se cambia `survey_response`, su CHECK de titulados/empleadores, la persistencia de respuestas ni los conjuntos/análisis existentes. El vínculo de recepción dinámica se implementará con su propia migración y contrato.

## Estados y garantías

| Estado operativo | Uso |
| --- | --- |
| `BORRADOR` | Editable; vista previa de prueba. |
| `PUBLICADA` | Estructura fija, enlace propio y opción de crear sucesora/cerrar. |
| `CERRADA` | Conserva estructura e historial; enlace no admite participaciones. |

Estos estados son independientes de la aprobación metodológica del estudio/instrumento. Publicar no cambia el estudio a `APROBADA`, no clasifica respuestas como `REAL` y no habilita el modo analítico `OFICIAL`.

La definición publicada utiliza el contrato nuevo `PUBLICACION_V1`: estudio, encuesta, definición con versión, secciones/ítems y procedencia cuando se copió un catálogo. Mantiene una huella propia; el snapshot compatible de `SurveyDefinition` mantiene otra huella para su formato. Ambas se verifican antes de presentar, cerrar o crear sucesora. No se cambia la serialización de capturas históricas.

La vista previa fija la revisión que presenta. Si cambia antes de comprobar una prueba, devuelve 409 y pide abrirla nuevamente. No valida respuestas de una revisión antigua contra preguntas nuevas. El resultado de la prueba se dibuja con la misma definición que se validó.

Publicar bloquea la fila de encuesta para asignar número y comprueba la revisión del borrador. Las escrituras de versión, snapshot, contador y evento forman una transacción: un conflicto o error deja todo sin publicar. Editar una estructura que ya es `PUBLICADA/CERRADA` se rechaza también en el servicio, aunque se invoque el endpoint directamente.

Cerrar cambia estado y metadata, no el JSON/hash de definición. Los análisis anteriores tampoco se recalculan. Crear sucesora conserva la identidad de encuesta y los IDs de sus preguntas; editar esa sucesora no cambia el contenido de la versión fuente. La separación analítica y la eventual homologación entre versiones mantienen su etapa posterior.

## Archivos principales

| Archivo | Función |
| --- | --- |
| `src/main/java/db/migration/V8__SurveyPublication.java` | Cambio de esquema compatible con V7 aplicada y ambos motores. |
| `survey/PublishedSurvey.java` | Contrato de definición publicada, separado de formatos históricos. |
| `survey/SurveyPublicationService.java` | Publicar, consultar, verificar, crear sucesora y cerrar. |
| `survey/DraftSurveyService.java` | Códigos de borrador/sucesora, bloqueo de estructuras publicadas y borrador activo. |
| `web/SurveyPublicationController.java` | Vista previa, validación de prueba y operaciones de publicación. |
| `web/DraftSurveyController.java` | Selector de borradores/versiones y redirección de una versión que dejó de ser editable. |
| `templates/survey.html` | Controles compartidos para formularios existentes y vista previa. |
| `templates/survey-publish-confirm.html` | Revisión antes de publicar. |
| `templates/survey-publication.html` | Versión fija, enlace, sucesora y cierre. |
| `templates/survey-publication-link.html` | Estado público informativo; sin recepción de envíos. |
| `static/js/app.js` | Conserva condiciones y evita enviar una vista previa congelada. |

Los paths abreviados corresponden a `src/main/java/bo/edu/umss/mercado/` o `src/main/resources/`, según el archivo.

## Verificación ejecutada y límites

Las suites completas pasaron **363 pruebas en H2 y en la ejecución con PostgreSQL 18 local**, sin fallos/errores. Incluyen 17 casos de publicación y una prueba adicional de migración desde V7 con un borrador existente; los casos que construyen H2 propia conservan ese alcance durante la ejecución PostgreSQL. La migración desde V7 también se comprobó con `DATABASE_TO_LOWER=TRUE`, como en el perfil `dev`.

Se verificaron vista previa sin respuestas/eventos, obligatoriedad y condiciones, campos extra, revisión obsoleta, publicación sin aprobación institucional, rechazo de edición posterior, sucesora y numeración, cierre conservando hash, enlace y POST no disponible, ADMIN/CSRF, rollback ante actor inválido, manipulación de huellas, publicación concurrente y solicitudes concurrentes de sucesora.

Chrome comprobó seis pantallas (borrador sucesor, preview, confirmación, versión, preview congelada y enlace público) a 360/768/1280 píxeles. Usó HTML Thymeleaf real obtenido de MockMvc con definiciones sintéticas, los recursos locales y CSP. Se probaron ramas Sí/No, obligatoriedad/disabled, validación nativa y prevención de submit de la vista congelada; se conservaron cero excepciones JavaScript, etiquetas y ausencia de desbordamiento. Un título largo detectado en la revisión móvil se corrigió y la comprobación se repitió. Esta evidencia no sustituye un ensayo de envío en un despliegue público ni acredita WCAG completa.

Registros privados de pruebas, excluidos de Git: `.codex-tmp/publication-verify-h2.log`, `publication-verify-postgres.log`, `publication-final-views.log`, `publication-dev-migration.log` y `publication-browser/report.json`. El PostgreSQL temporal usó `127.0.0.1:55441/publication_tests`; no se utilizó Neon ni se leyeron credenciales locales. El servidor temporal se cierra al terminar.

## Qué sigue

Recepción pública validada contra una versión fija: apertura y envío vinculados al mismo instrumento, captura original/versionada, estados, reintentos, origen inicial `NO_DECLARADO`, comportamiento tras cierre y consulta de respuestas por encuesta/versión. Esa entrega mantendrá los análisis de instrumentos diferentes separados y no convertirá automáticamente una encuesta nueva en empleadores.

Este paso permanece local, sin commit, push ni despliegue.
