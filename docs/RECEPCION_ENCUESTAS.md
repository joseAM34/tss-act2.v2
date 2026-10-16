# Paso 3 local: recepción por encuesta y versión

**Actualización del paso 4:** la [preparación general por encuesta y versión](PREPARACION_GENERAL.md) está implementada mediante un servicio separado. Este documento conserva el alcance y evidencia del paso 3; sus límites sobre preparación corresponden a ese incremento anterior.

Fecha: 2026-10-14. Autorizado por el usuario con «luz verde». Continúa el editor y la publicación; todos los cambios permanecen locales, sin commit, push ni despliegue.

## Cómo probarlo

Detén la aplicación con **Ctrl+C** y ejecútala desde la raíz:

```powershell
mvn "-Dspring-boot.run.profiles=dev" spring-boot:run
```

Usa solamente `dev` para este recorrido: utiliza H2 local. El perfil `local` puede conectar a Neon según tu archivo privado; no lo combines para esta prueba. Flyway aplica V9 al arrancar; no necesitas ejecutar SQL manualmente.

1. Abre `http://localhost:8080/admin`, inicia sesión y entra en **Abrir editor de borradores**.
2. Abre una versión publicada, o publica un borrador desde **Revisar publicación**.
3. Su **Enlace de esta versión** ya recibe respuestas públicas mientras permanezca publicada. No exige cuenta al participante.
4. Pulsa **Usar esta versión en el enlace principal**. El enlace principal y la tarjeta del inicio abrirán esa versión. Publicar otra versión no cambia automáticamente esta selección.
5. Abre el enlace público en una ventana privada, responde y guarda. Recibirás una referencia.
6. En el panel pulsa **Consultar respuestas por encuesta y versión**. Selecciona la encuesta y aplica el filtro; después elige la versión y aplica de nuevo. También puedes abrir **Consultar respuestas de esta versión** desde su ficha.
7. Crea una sucesora, modifica y publica `v2`. Actívala para cambiar el enlace principal. Las respuestas de `v1` siguen identificadas como `v1`.
8. **Cerrar versión** impide nuevas participaciones en sus enlaces. Si era la activa, el enlace principal queda sin versión seleccionada hasta que actives otra publicada.

Los resultados muestran el total de la selección y hasta sus 100 envíos más recientes. Detalles, filtros, clasificación de origen y CSV requieren ADMIN.

## Qué conserva cada envío

El formulario abierto apunta directamente a `/encuestas/{slug}/versiones/{version}`. Si alguien abrió `v1` antes de activar `v2`, su envío sigue perteneciendo a `v1`, siempre que `v1` permanezca abierta.

V9 añade `active_version_id` a `survey`, y `publication_version_id`, `dynamic_survey_id` y `publication_hash` a `survey_response`. Las claves foráneas comprueban que encuesta, versión, código y huella corresponden entre sí. La definición y captura lógica permanecen congeladas; las respuestas históricas conservan sus datos y no reciben referencias inferidas.

Validación, condiciones, obligatoriedad, límites y consentimiento se comprueban en el servidor. Las preguntas registran `CON_VALOR`, `SIN_RESPUESTA` o `NO_APLICA`. El origen inicial es `NO_DECLARADO`, mostrado como **Origen sin verificar**; solo el administrador puede clasificarlo con motivo e historial.

Activar y cerrar quedan auditados con usuario, fecha y revisión. No modifican la definición publicada ni otorgan aprobación institucional.

Un reintento del mismo formulario y contenido devuelve la referencia original, incluso si entretanto se cerró la versión. Un reintento con contenido diferente produce conflicto. El token pertenece a la sesión y versión abiertas: otra sesión no puede reutilizarlo. Se conservan como máximo 64 tokens por sesión; al expirar la sesión o retirar un token antiguo hay que abrir el formulario de nuevo. La prevención de reintentos duplicados no impide varias participaciones distintas ni deduplica por correo o nombre.

## Alcance del análisis

La consulta y clasificación de respuestas dinámicas están disponibles. Su preparación analítica configurable y reportes por definición corresponden al siguiente incremento.

Las reglas nuevas `2026-10-wrangling-4` conservan esas respuestas en el inventario con exclusión explícita `ANALISIS_DINAMICO_PENDIENTE`; no las interpretan como empleadores ni las mezclan con el protocolo original. No se habilitan correcciones o codificación analítica genéricas todavía. Los conjuntos históricos congelados mantienen sus reglas, contenido y huellas. Los dos cuestionarios originales siguen funcionando con sus rutas y análisis existentes.

## Verificación

La suite completa pasó **387 pruebas Java en H2 y 387 en la ejecución con PostgreSQL 18 local**, sin fallos ni omisiones. Los 24 casos nuevos de recepción también pasaron después del ajuste final de la pantalla de versión cerrada.

Se comprobaron selección activa, formularios abiertos antes del cambio, captura fija, condiciones y límites, estados, origen, privacidad, CSRF, filtros, contenido escapado, claves foráneas, reintentos y carreras entre recepción y cierre. La migración es aditiva; V7 y V8 permanecen intactas.

Las pantallas del formulario, versión, listado de respuestas, detalle y revisión se verificaron con HTML real y datos ficticios en Chrome a 360, 768 y 1280 píxeles, sin desbordamiento ni excepciones JavaScript. Esta comprobación no constituye una auditoría completa de accesibilidad.
