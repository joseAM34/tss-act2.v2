# Editor local de borradores: primer incremento

Fecha: 2026-10-14. Autorización: «empecemos con los cambios en local primero, luego hacemos push; vamos paso por paso».

Este documento conserva el alcance y la evidencia del primer incremento. La vista previa, publicación y sucesoras añadidas después se describen en [PUBLICACION_ENCUESTAS.md](PUBLICACION_ENCUESTAS.md); las limitaciones de publicación indicadas abajo corresponden al estado anterior.

Este incremento implementa la etapa 2 del [plan](ENCUESTAS_DINAMICAS_PLAN.md): crear, duplicar y editar borradores. Las etapas de publicación, recolección dinámica, análisis configurable, importación y reportes mantienen su alcance posterior. No se realizó commit, push ni despliegue.

## Qué puedes probar

1. Crear una encuesta con título y descripción.
2. Añadir, renombrar y ordenar secciones; eliminar las que estén vacías.
3. Añadir, editar, mover entre secciones, ordenar y eliminar preguntas.
4. Configurar obligatoriedad, ayuda, opciones, límites de selección, rango de enteros y la condición simple sobre una selección anterior.
5. Duplicar un borrador o crear una copia editable de titulados/empleadores. La copia es otra encuesta y no copia respuestas ni aprobación institucional.
6. Consultar los borradores después de reiniciar con la misma base.

Tipos disponibles: selección única, selección múltiple, desplegable, texto corto, texto largo, entero no negativo, correo, teléfono y enlace web.

Actualización 2026-10-27: las opciones se editan como casillas de texto con botones «Añadir respuesta» y «Quitar», hasta 100 opciones. El editor conserva los códigos existentes al cambiar textos y genera códigos internos para las nuevas opciones. Los cambios se aplican al guardar la pregunta; el servidor sigue comprobando límites y condiciones dependientes. Al cambiar a un tipo sin opciones, estas no se envían; antes de guardar se pueden recuperar volviendo al tipo de selección. Sin JavaScript se conserva el campo de una línea por opción, por ejemplo `SI | Sí` (código estable y texto mostrado). Verificación: 28 pruebas de integración del editor y tres pruebas JavaScript de añadir/quitar, códigos, cambio de tipo y límite de opciones.

Una condición puede activar una pregunta con uno o varios códigos de una pregunta de selección anterior. No se permite eliminar su control, moverlo detrás de la dependiente o cambiar sus opciones de forma que deje una condición inválida. Las reglas entre campos específicas de titulados todavía no se convierten a un lenguaje declarativo general; las encuestas actuales siguen usando su validador original.

Los límites de selecciones aparecen solo para selección múltiple y el rango de valores solo para número entero. Con JavaScript activo, los campos que no corresponden se ocultan y deshabilitan para no enviarlos ni bloquear el guardado; sus valores se conservan mientras se cambia de tipo en el editor. La condición de aparición sigue disponible para cualquier tipo de pregunta, porque depende de una pregunta anterior. Una cuarta prueba JavaScript verifica visibilidad, deshabilitación y conservación de los valores al cambiar entre todos los tipos.

No hay botones que aparenten publicar o generar reportes. Los nuevos borradores no aparecen como encuestas públicas y no ingresan al pipeline estadístico.

## Cómo arrancarlo solo en tu equipo

Para probar sin tocar Neon, abre PowerShell en la raíz del proyecto y ejecuta:

```powershell
mvn "-Dspring-boot.run.profiles=dev" spring-boot:run
```

El perfil `dev` usa H2 en `data/private/dev.mv.db`, escucha en `127.0.0.1` y mantiene la configuración inicial local. No activa `local`, por lo que no carga `application-local.properties` ni su conexión a Neon.

Abre `http://localhost:8080/admin`. Si esa base H2 todavía no tiene administrador, créalo desde la configuración inicial local. El administrador de H2 puede ser distinto del que creaste en Neon.

Desde el panel pulsa **Abrir editor de borradores**; también puedes ir directamente a `http://localhost:8080/admin/encuestas` después de iniciar sesión.

Prueba sugerida: crea «Encuesta de prueba local», añade «Datos generales», agrega una pregunta de selección con `SI | Sí` y `NO | No`, guarda y vuelve a abrirla. Duplica el borrador, cambia el título de la copia y comprueba que el original se conserva. Detén con Ctrl+C, arranca otra vez y verifica ambos borradores.

No necesitas cambiar credenciales, scripts, puerto o perfil en ningún archivo para ese recorrido. Si tu terminal tiene un `PORT` distinto, utiliza el puerto indicado al arrancar.

**Importante para los siguientes pasos:** si eliges arrancar con `local` o `prod` conectado a Neon, Flyway aplicará también V7 allí. Durante este incremento las comprobaciones utilizaron únicamente bases temporales; no se arrancó contra Neon.

## Archivos que hacen funcionar el editor

| Archivo | Responsabilidad |
| --- | --- |
| `src/main/resources/db/migration/V7__survey_drafts.sql` | Añade seis tablas, sin modificar las migraciones V1–V6. |
| `src/main/java/bo/edu/umss/mercado/survey/DraftSurveyService.java` | Lee y guarda borradores; valida su estructura, conserva revisiones y registra eventos. |
| `src/main/java/bo/edu/umss/mercado/web/DraftSurveyController.java` | Conecta los formularios administrativos con el servicio. |
| `src/main/resources/templates/survey-drafts.html` | Listado, creación y copias de encuestas actuales. |
| `src/main/resources/templates/survey-draft.html` | Título, secciones, orden y preguntas del borrador. |
| `src/main/resources/templates/survey-question-editor.html` | Campos para editar cada pregunta. |
| `src/main/resources/templates/survey-editor-error.html` | Motivo cuando una operación no se guarda y enlace para volver. |
| `src/main/resources/static/css/survey-editor.css` | Presentación del editor, incluida la adaptación móvil. |
| `src/main/resources/templates/admin.html` | Añade el acceso al editor desde el panel. |
| `src/main/resources/templates/fragments/layout.html` | Sirve la hoja de estilos nueva desde el monolito. |

Las encuestas públicas continúan leyendo `SurveyCatalog`, y su persistencia no usa las tablas de borradores todavía.

## Tablas nuevas y conservación

| Tabla | Qué conserva |
| --- | --- |
| `survey` | Identidad de la nueva encuesta, estudio, slug y creación administrativa. |
| `survey_version` | Su primer borrador, título, descripción, revisión y referencia de procedencia cuando se copia un catálogo. |
| `survey_section` | Secciones y orden dentro de la versión. |
| `survey_question` | Preguntas, sección, orden, tipo, límites y condición simple. |
| `survey_question_option` | Códigos, textos y orden de opciones por pregunta y versión. |
| `survey_definition_event` | Operación, actor, revisión y estructura guardada con su huella. Incluye secciones vacías. |

V7 lleva de 13 a 19 tablas de aplicación al aplicarse. No altera `survey_response`, su CHECK de las dos encuestas, capturas, snapshots ni análisis anteriores. El nuevo ciclo solo admite `BORRADOR`; la publicación y su vínculo con snapshots requieren otra migración en la etapa siguiente.

Todas las escrituras del servicio se ejecutan en una transacción. La revisión enviada debe coincidir con la actual: si dos pestañas guardan la misma revisión, una guarda y la otra recibe HTTP 409. Un error de validación no añade revisiones ni eventos. La lectura verifica que no cambie la revisión mientras reconstruye la estructura.

Los títulos, enunciados, ayudas y opciones se muestran escapados. Editor y operaciones requieren ADMIN; los POST conservan CSRF. No se añadió otra cuenta administrativa ni se modificó la configuración inicial.

## Límites de este incremento

- Hasta 40 secciones, 200 preguntas y 100 opciones por pregunta; límites técnicos del editor, no requisitos institucionales.
- El estudio sigue siendo el borrador UMSS existente. No hay editor de estudios ni asignación institucional automática.
- Los identificadores de pregunta se generan al crear y se conservan al editar. Las copias de los instrumentos actuales mantienen códigos, opciones, rangos y condiciones por pregunta.
- El orden permite mover preguntas dentro de una sección y cambiar su sección desde el formulario; toda la estructura debe seguir respetando sus dependencias.
- La revisión de edición protege contra sobrescrituras. No constituye todavía un historial de versiones publicadas ni un selector analítico nuevo.
- Siguen pendientes decimales, fechas, matrices agrupadas, ranking, NPS, adjuntos, ramas complejas, publicación y enlaces nuevos, importaciones, análisis general y reportes.
- No se modificó la configuración privada ni se intentó validar credenciales o disponibilidad de Neon/Render.

## Evidencia de verificación

La implementación añade `DraftSurveyIntegrationTest` y `DraftMigrationPersistenceTest`: 29 casos nuevos contando parámetros. Cubren tipos actuales, formularios, validación, ADMIN/CSRF, escapes, copias, dependencias, orden, aislamiento del catálogo, auditoría, conflicto entre pestañas y dos escrituras concurrentes reales.

La prueba de migración inicia una H2 temporal con V1–V6, guarda un histórico y un snapshot, aplica V7, compara valores/JSON/huellas y vuelve a conectar para verificar el borrador. No modifica una base compartida. Las suites completas pasaron 345 pruebas en H2 y en la ejecución con PostgreSQL 18 local; las pruebas que construyen su propia H2 conservan ese alcance incluso en la ejecución principal PostgreSQL.

La inspección visual posterior detectó opciones del selector de tipos sin texto; se corrigió el renderizado y se añadieron aserciones de etiqueta visible para los nueve tipos. Tras esa corrección pasaron nuevamente los 29 casos del incremento en H2 y en la ejecución con PostgreSQL, sin fallos ni errores. Los registros finales son `.codex-tmp/draft-final-h2.log` y `.codex-tmp/draft-final-postgres.log`. La comprobación de Chrome se repitió con etiquetas visibles y mantuvo cero excepciones y ausencia de desbordamiento en las tres anchuras.

Chrome comprobó listado, borrador copiado y edición de pregunta a 360/768/1280 píxeles con HTML real de Thymeleaf obtenido por MockMvc de borradores sintéticos, recursos locales y CSP. Se revisaron desbordamiento de página, formularios con CSRF, etiquetas, foco, estilos y excepciones JavaScript. No se presenta como ensayo de CRUD en un servicio desplegado ni auditoría WCAG completa; las mutaciones y autorización se verifican en Java.

Registros y capturas excluidos de Git: `.codex-tmp/draft-verify-h2.log`, `.codex-tmp/draft-verify-postgres.log`, `.codex-tmp/draft-browser/`. El PostgreSQL temporal utiliza exclusivamente `127.0.0.1:55441` y la base `draft_editor_tests`; se detiene al finalizar la revisión. Los registros no son datos de campo.

## Siguiente incremento

Después de revisar este editor local: vista previa, publicación transaccional, versión fija y cierre, conservando separación de aprobación metodológica. El primer circuito de recolección dinámica se completa al agregar después la recepción validada contra esa versión. No se hace push como parte del editor local.
