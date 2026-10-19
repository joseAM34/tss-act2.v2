# Instrucciones del proyecto

## Alcance y fuentes

Aplicación monolítica modular de estudio de mercado de Ingeniería en Sistemas UMSS. El usuario autorizó comenzar el desarrollo, superando el alcance documental del archivo de referencia `docs/01_ESTABLECER_REPOSITORIO.md`.

Consultar `docs/DECISIONES.md`, `docs/INSTRUMENTO.md`, `docs/02_REQUISITOS_CUESTIONARIOS.md`, `docs/ACEPTACION_CUESTIONARIOS.md`, `docs/03_DATOS_Y_DATA_WRANGLING.md`, `docs/DATOS_Y_WRANGLING.md`, `docs/ACEPTACION_DATOS.md`, `docs/04_ESTADISTICA_UNIVARIADA_BIVARIADA_CHI_CUADRADO.md`, `docs/ESTADISTICA.md`, `docs/ACEPTACION_ESTADISTICA.md`, `docs/05_VISUALIZACION_COMPUTACIONAL.md`, `docs/VISUALIZACION.md` y `docs/ACEPTACION_VISUALIZACION.md`. La autorización del usuario para desarrollar y continuar las fases 2, 3, 4 y 5 prevalece sobre el alcance documental de los archivos de referencia. En fase 5 el usuario autorizó implementar después de revisar la especificación («vamos»). Preservar los archivos existentes y el PDF original. No incorporar al historial datos de campo, fuentes privadas ni secretos.

Mantener los dos entregables finales: documento de Análisis de Mercado y herramienta de recolección de datos. El alcance completo comprende dos cuestionarios, Data Wrangling, estadística univariada y bivariada, pruebas chi-cuadrado con comprobación de aplicabilidad, visualización computacional (incluidos mapas de calor y radar), automatización de reportes en el backend, exportaciones CSV/Excel y experimentación por simulación con un modelo definido.

## Decisiones vinculantes

- Encuestas públicas; respuestas, reportes, exportaciones y administración protegidos por autenticación en el backend.
- La cuenta administrativa se guarda en la base de datos con hash BCrypt. La configuración inicial se realiza una sola vez desde la máquina local; no crear cuentas en memoria ni configurar sus credenciales mediante variables de entorno.
- El inicio principal es manual con Maven. Los scripts opcionales no deben solicitar credenciales ni modificar variables de entorno.
- Los diez criterios del catálogo histórico de empleadores aparecen una sola vez. Las plantillas oficiales recibidas en octubre de 2026 conservan dieciocho criterios distintos (los diez anteriores y ocho adicionales), por instrucción del usuario; no eliminar los ocho nuevos ni duplicar observaciones.
- Conservar las escalas observadas. `No sabe` es una categoría propia, no un puntaje de acuerdo.
- Documentar las ambigüedades sin afirmar que una propuesta está aprobada.
- Mantener la correspondencia T01-T41/E00-E22/E09_OTRO y la matriz de obligatoriedad/condiciones en `docs/ACEPTACION_CUESTIONARIOS.md`. Acceso público y criterios sin repetición están aprobados; elegibilidad y varias participaciones distintas siguen pendientes.
- Las adaptaciones aprobadas para el borrador de fase 2 incluyen edad entera, género con opción de no responder, vida profesional/desempleo en meses enteros, hasta dos asignaturas de poca utilidad y textos de otra asignatura complementarios. El filtro de experiencia distingue Sistemas, otras áreas y sin experiencia; competencias aplican solo a experiencia relacionada. Las matrices de seis competencias técnicas y seis transversales miden adecuación de formación al trabajo actual o previo, con cinco grados y `NO_PUEDE_VALORAR` nominal sin puntaje. Esta aprobación del usuario no sustituye aprobación institucional del instrumento.
- Conservar `CON_VALOR`, `SIN_RESPUESTA` y `NO_APLICA` para preguntas de los nuevos envíos. No inferir estados de respuestas históricas que no los registraron ni confundir `NO_SABE` con un faltante.
- Los envíos nuevos comienzan como `NO_DECLARADO`. Solo una revisión administrativa trazable puede clasificarlos como `REAL` o `SINTETICO`; no aceptar una clasificación de origen suministrada por participantes públicos ni inferirla en históricos.
- Congelar estudio, versión, definición, captura lógica y huellas en los nuevos envíos. Una misma encuesta/versión no puede reutilizarse con distinta definición; las respuestas antiguas mantienen metadata limitada sin copiarles el catálogo actual.
- Distinguir preparación de borrador y modo `OFICIAL`: este último requiere origen real y estudio/versiones aprobados. No otorgar aprobación metodológica o institucional automáticamente.
- Conservar originales al registrar correcciones, origen o codificación temática; cada conjunto identifica revisiones exactas y conserva su bundle y huella lógica. Las matrices ordinarias omiten contactos, nombre de organización y texto original; todas las salidas requieren ADMIN.
- La unidad de cada fila principal es un envío. No eliminar respuestas por correo/nombre coincidente ni afirmar que cada envío representa una empresa/persona independiente; elegibilidad y unidad de análisis de empleadores siguen pendientes.
- Distinguir datos observados, valores derivados y simulaciones. Cada porcentaje debe tener un denominador explícito.
- El dashboard conserva estudio, versión, origen, selección aplicada, protocolo y huellas. El backend calcula resultados desde el conjunto congelado verificado; el navegador representa cifras y no recalcula el protocolo. Mantener separados los estratos y no atribuir comparabilidad institucional automáticamente.
- Los nuevos conjuntos con reglas `2026-10-wrangling-3` registran `submitted_at` en miembros para filtrar recepción con fechas inclusivas en `America/La_Paz`. No completar conjuntos históricos desde respuestas actuales; cuando falte esa metadata, el filtro temporal exige un conjunto nuevo.
- El radar de empleadores representa acuerdo parcial/total sobre respuestas sustantivas, con `NO_SABE` separado y bases por criterio. Los paneles de cinco criterios no crean índices ni duplican observaciones; una serie incompleta no se rellena. Conservar tablas equivalentes y distinguir cero de ausencia.

## Desarrollo

Java 21, Spring Boot, Thymeleaf, Maven y PostgreSQL, con módulos por responsabilidad. H2 se utiliza exclusivamente para desarrollo local y pruebas.

Apache ECharts 6.0.0 se sirve como recurso local del monolito junto con sus avisos y licencia de dependencia; esto no asigna licencia al proyecto ni requiere una aplicación frontend separada. Mantener recursos y datos protegidos compatibles con la política de contenido del backend.

Trabajar por incrementos ejecutables. Verificar validación en servidor, autorización, CSRF, persistencia y ausencia de duplicaciones. Las plantillas deben escapar los datos proporcionados por encuestados.

Usar Flyway para cambios de esquema. No modificar una migración ya aplicada en un entorno compartido. Mantener trazabilidad del instrumento que produjo cada respuesta.

No anunciar análisis, reportes o simulación terminados antes de implementarlos y verificarlos. No añadir licencia sin decisión del equipo.

Revisar `git status`, diferencias y archivos seleccionados antes de cada commit. Conservar `origin` y el historial remoto; no hacer push forzado. La rama principal es `main`.
