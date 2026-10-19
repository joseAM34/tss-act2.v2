# Encuestas predefinidas opcionales

Fecha: 2026-10-18. Estas estructuras crean encuestas ordinarias del editor. No se importan respuestas ni se publican encuestas al iniciar la aplicación.

## Uso

1. Inicia sesión como administrador y abre **Opciones de encuestas**, al pie de una pantalla administrativa.
2. Entra en **Establecer encuestas predefinidas**, selecciona titulados, empleadores o ambas y pulsa establecer.
3. Abre el borrador. Edita preguntas, opciones, secciones y condiciones con el editor habitual. Las notas de revisión de las fuentes están en la pantalla de plantillas; las ayudas del formulario público se limitan a lo que necesita el participante.
4. Publica cuando la definición esté lista. La publicación habilita su ruta ordinaria de importación CSV/Excel. Activar permite además ofrecerla en la portada pública.
5. Para los archivos históricos sin columna de consentimiento selecciona **Archivo histórico** al revisar el lote y confirma su recepción administrativa. Revisa las correspondencias y después la vista previa. Ver [IMPORTACION_HISTORICA.md](IMPORTACION_HISTORICA.md).

Repetir la instalación no duplica una copia original disponible. Si editaste su borrador o próxima versión, la recuperación crea una nueva encuesta con la receta original y conserva tus cambios en la anterior. Si archivaste la anterior, puedes restaurar su espacio o crear una copia original desde el pie. Las plantillas de base permanecen como recurso del proyecto; no cambian cuando editas una encuesta creada desde ellas.

La receta de empleadores `2026-10-fuente-oficial-2` incluye **Docentes** en los cargos desde la creación inicial, por solicitud del usuario, como categoría observada en el archivo entregado. No es necesario preparar otra versión para añadir ese cargo al importar en una encuesta recién creada con esta receta: establece la encuesta, publica e importa. Una instalación previa conserva su definición; volver a establecer empleadores crea una encuesta con la receta actualizada sin sobrescribir la anterior.

## Estructuras y límites

| Plantilla | Campos | Características |
| --- | ---: | --- |
| Titulados | 49 | Edad, antigüedad e ingresos por categorías; empleo y formación; múltiples con límite de cinco asignaturas; textos de contacto y complementos |
| Empleadores | 30 | Doce preguntas principales y dieciocho criterios distintos con acuerdo total/parcial, desacuerdo total y No sabe |

No incluyen columnas auxiliares de puntos/comentarios de Microsoft Forms, nombres de participantes, organizaciones, contactos ni ninguna respuesta precargada. Los nombres de organización y datos de contacto que se reciban al importar quedan protegidos; los textos se excluyen de las matrices ordinarias y no se convierten automáticamente en estadísticas.

Las recetas reproducen encabezados y categorías de fuente conocidas. La exportación no muestra opciones nunca seleccionadas, obligatoriedad o todos los saltos originales; por eso se crean borradores editables sin inventar reglas de aplicación a vacíos históricos. Algunos enunciados exportados están truncados y se señalan en las notas administrativas para confirmar contra el formulario. Antes de nueva recolección, revisar estos puntos.

La etiqueta oficial identifica la fuente entregada por el usuario. No marca el estudio como aprobado ni las respuestas como REAL, ni supone independencia para chi-cuadrado. Los dieciocho criterios permiten análisis individuales; el radar actual conserva sus paneles de cinco criterios 01–05 y 06–10. Este incremento no amplía sus paneles ni crea índices.

## Gestión de encuestas

Desde el espacio de una encuesta abre **Gestionar encuesta: archivar o eliminar**.

- Una encuesta sin datos se elimina con una casilla de confirmación.
- Una encuesta con datos permite **Descargar respaldo ZIP** y, después de guardarlo, **Eliminar definitivamente**. Se incluyen versiones, respuestas, revisiones, archivos originales, preparación, análisis y reportes. Ver [Eliminación con respaldo](ELIMINACION_CON_RESPALDO.md).
- Archivar retira la encuesta del listado habitual, cierra sus publicaciones y conserva sus datos. El listado permite mostrar las archivadas.
- Restaurar recupera el espacio; las publicaciones cerradas siguen cerradas. Edita y publica una nueva versión para nueva recepción.
- Cada operación verifica una huella del estado para evitar aplicar una confirmación sobre cambios concurrentes.

Archivar conserva las definiciones y respuestas antiguas; eliminar con respaldo retira los datos propios de la encuesta tras confirmación. Los originales del catálogo con historial siguen accesibles para consultar su procedencia; ya no se inyectan como encuestas vacías en el listado de una instalación nueva. Sus enlaces históricos permanecen compatibles.

## Implementación

`surveys/official-templates.json` contiene estructura, opciones y notas de revisión. `OfficialSurveyTemplates` solo lee el recurso. `PredefinedSurveyService` crea borradores con `DraftSurveyService` y serializa solicitudes repetidas. No hay inicializador que inserte encuestas ni un tipo especial para las instaladas. Las modificaciones posteriores usan las mismas tablas, validaciones y rutas que cualquier encuesta creada manualmente.

V18 añade archivado/auditoría de gestión; V19 permite ausencia de registro de consentimiento exclusivamente para recepción histórica con recibo verificado. Se aplican por Flyway. Los archivos de Excel y las respuestas de campo no se versionan en Git.

La detección de nuevas encuestas arbitrarias desde Excel/CSV no forma parte de este botón. La lectura mixta versionada admite JSON, separadores inequívocos, espacios de Forms y un punto y coma final, sin reinterpretar revisiones antiguas.


## Verificación de este incremento

- Suite completa: 581 casos correctos con H2, sin fallos ni omisiones; incluye conservación de datos al aplicar migraciones antiguas y actuales.
- Ejecución focalizada con PostgreSQL local: 48 casos correctos, incluidas V18/V19, instalación concurrente sin duplicados, edición/recuperación, archivado/borrado con referencias, captura histórica, textos extensos y verificación de recibos.
- JavaScript: 18 casos de dashboard, resultados y gráficos correctos.
- Chrome: 18 vistas a 360, 768 y 1280 píxeles. Verifica pie discreto, notas administrativas, establecimiento, gestión con confirmación de borrado y cambio de recepción individual/histórica, sin desbordamientos ni excepciones.

La prueba visual usa HTML real generado por las pruebas de integración y los recursos locales; las acciones de persistencia, autorización y CSRF se comprueban por HTTP con base de pruebas. No se presenta como prueba contra el despliegue remoto.

La revisión en memoria de los dos libros entregados con las recetas actuales reconoce 49/49 columnas y 148 filas de titulados, y 30/30 columnas y 53 filas de empleadores, sin errores de validación histórica. La receta actual de empleadores incluye la categoría observada que faltaba en la primera receta. La vista previa mantiene la adaptación de otras categorías en un borrador sucesor para encuestas anteriores o futuras discrepancias; el procedimiento está en [Recepción histórica](IMPORTACION_HISTORICA.md#categorías-observadas-que-faltan-en-la-versión). No se escriben respuestas a una base durante esta revisión. Esto no equivale a una importación confirmada ni revisa independencia, autorización o calidad metodológica del trabajo de campo.

El ajuste de categorías y reutilización pasa 61 pruebas focalizadas de importación: preparación del borrador, conservación de ediciones, categorías equivalentes/ambiguas, archivo XLSX y hoja exactos, revisión nueva, ausencia de duplicación en reintentos, autorización, CSRF y escape de textos. La evidencia local es `.codex-tmp/import-catalog-tests.log`.

La receta actualizada con Docentes pasa 42 pruebas focalizadas de plantillas, establecimiento y lectura/importación. Incluyen regenerar desde una receta anterior publicada, importar ambos cargos sin adaptación posterior, conservar su publicación y archivo y reutilizar la instalación sin duplicarla. Evidencia: `.codex-tmp/preset-docentes-tests.log`.

Evidencia local excluida de Git: `.codex-tmp/preset-final-full.log`, `.codex-tmp/preset-postgres.log` y `.codex-tmp/predefined-browser/`. No se cargaron respuestas reales, no se aplicaron migraciones remotas ni se hizo commit, push o despliegue.
