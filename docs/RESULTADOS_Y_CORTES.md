# Resultados y cortes de datos

El apartado **Resultados** reúne las tablas y representaciones del total de envíos de la versión elegida. **Ver envíos** conserva la consulta individual protegida. La pestaña **Reportes** permite generar el reporte del total actual sin guardar primero un corte.

## Actualización y representaciones

La primera consulta de cada selección carga los datos y conserva el resultado en la sesión administrativa. Solo **Actualizar todos** incorpora nuevos envíos o revisiones. Cambiar o activar varias representaciones utiliza las mismas cifras, sin volver a consultar respuestas. Cada pregunta indica su carga; un fallo conserva la vista anterior y permite reintentar.

Un inicio de sesión exitoso invalida esta caché. La sesión conserva hasta doce selecciones y veinticuatro vistas recientes para que actualizar otra pestaña no cambie el conjunto que se envía a un reporte. La caché es temporal, en memoria; no publica contactos ni respuestas de texto. Las preferencias visuales se conservan separadamente en el navegador.

La lectura del servidor verifica capturas, definiciones y recibos de importación. Agrupa consultas por versión y decodifica/verifica el archivo original una vez por lote durante esa lectura transaccional. Este almacenamiento auxiliar se libera al terminar: una actualización vuelve a verificar las fuentes.

## Cortes

**Generar corte** permite seleccionar fechas de recepción inclusivas en `America/La_Paz` y una, varias o todas las versiones publicadas/cerradas de la misma encuesta. El corte guardado conserva exactamente los datos mostrados. Actualizar su vista permite guardar otro corte; no sobrescribe el original.

En una selección de todas las versiones, las nuevas versiones publicadas se incorporan únicamente al actualizar. Las preguntas se combinan solo cuando coinciden sus definiciones, dependencias, tratamiento y unidad declarada. Las variantes incompatibles aparecen separadas con sus versiones de procedencia; no se presume equivalencia institucional.

La eliminación de un corte exige confirmar los productos asociados que muestra la pantalla. El servidor verifica nuevamente esa relación antes de borrar el corte, sus análisis, cruces y reportes derivados. Conserva los envíos originales y otros cortes. El respaldo integral de una encuesta incluye los cortes.

## Alcance de las cifras y reportes

El total incluye todos los orígenes registrados y conserva tablas separadas por origen. Su alcance es descriptivo: reunir envíos no demuestra independencia, elegibilidad ni aprobación metodológica. Los estados `SIN_RESPUESTA`, `NO_APLICA` y las categorías `NO_SABE` permanecen distintos. Una pregunta que no existía en una versión no se convierte en respuesta faltante.

Los contactos y textos libres se muestran como cobertura de respuesta, sin divulgar su contenido en estas matrices. Los porcentajes mantienen sus denominadores. Las magnitudes requieren unidad declarada; no se deducen unidades de textos ambiguos.

El reporte utiliza la vista cargada y sus representaciones seleccionadas. Al guardarlo se materializa internamente un conjunto congelado verificable, sin exigir al usuario crear previamente un corte. Continúa disponible la personalización y el guardado opcional como plantilla.

## Persistencia y compatibilidad

La migración Flyway `V21__result_cuts.sql` incorpora los cortes. El protocolo `RESULTADOS_TOTALES_V1` identifica los nuevos conjuntos; los protocolos y huellas de conjuntos anteriores se mantienen. Los análisis históricos continúan accesibles en las herramientas avanzadas. Todas estas consultas y operaciones requieren administración; las mutaciones conservan protección CSRF.

## Diccionario universal con alimentación manual

La extracción es exclusivamente local y no consulta Wikidata, otras APIs ni IA. Los endpoints anteriores de búsqueda y sinónimos externos están desactivados. Sus consultas históricas se conservan, sin intervenir en el flujo actual.

El engranaje de una pregunta permite extraer palabras y frases, generar un prompt copiable con la pregunta y los términos, y pegar equivalencias preparadas fuera de la aplicación. Las respuestas originales solo se incluyen en el prompt mediante una casilla opcional desmarcada inicialmente. Nada se envía automáticamente a terceros. El enlace discreto Alimentar diccionario en el footer administrativo permite alimentar la misma base universal sin depender de una encuesta.

El formato de entrada es un grupo por línea: nombre=variante|sinónimo. La validación combina lo pegado con el diccionario existente y muestra los grupos nuevos o modificados antes de guardar. El guardado exige revisión explícita y controla la revisión concurrente. Se conservan grupos y variantes anteriores; los alias compartidos fusionan grupos y la normalización unifica minúsculas y tildes. Los formatos inválidos no modifican la base.

El diccionario se reutiliza entre preguntas, encuestas y versiones. V23 conserva sus revisiones con actor, fecha y huella; los diccionarios anteriores de V22 siguen recuperándose hasta su primer guardado universal. Los datos compartidos permanecen al eliminar una encuesta. No se reescriben respuestas ni conjuntos congelados.

En el conteo para gráficas y en el resaltado siempre tiene prioridad el compuesto más largo. Inteligencia artificial no cuenta adicionalmente como inteligencia y artificial en el mismo fragmento; una palabra que aparezca por separado puede contar en su propio tema. Cada tema cuenta una sola vez por respuesta, aunque haya varias menciones. Las opciones antiguas de separados o ambos ya no alteran esta prioridad. Las menciones no demuestran acuerdo ni competencia y las negaciones también pueden contenerlas.

La consulta individual Ver respuestas y las tablas Textos siguen protegidas por ADMIN. Los textos se escapan y se mantienen aparte de las matrices ordinarias. Las representaciones de texto son exploratorias y no se incorporan automáticamente a los reportes ordinarios.
Los reportes personalizados del total y de cortes que conservan preguntas abiertas ofrecen tabla, barras, circular y anillo de coincidencias. Son opciones independientes de la cobertura. El backend verifica las capturas del conjunto y aplica el diccionario universal; cada tema cuenta una vez por respuesta y los compuestos tienen prioridad. Los campos identificativos siguen excluidos. El reporte congela reglas, revisión y huella del diccionario y verifica sus conteos al abrirlo, sin consultar el diccionario actual. Las representaciones de coincidencias no incorporan respuestas originales. Las opciones quedan deshabilitadas sin menciones; los análisis antiguos que excluyeron las preguntas abiertas requieren generar un nuevo reporte desde Resultados para incluirlas. Circular y anillo distribuyen menciones de temas, no porcentajes de envíos.

En Personalizar, el grupo Respuestas abiertas incluye Textos (tabla de una columna con el contenido original, solo al seleccionarla expresamente) y engranajes bajo Textos y Tabla de coincidencias. Los engranajes abren el diccionario universal en otra pestaña, con pregunta y términos extraídos del conjunto congelado para generar el prompt. Al regresar se invalida la vista previa de presentación y se comprueba el diccionario; no se actualizan los envíos del corte. Los campos identificativos permanecen excluidos. Los textos seleccionados se escapan al renderizar y se conservan en el reporte protegido; se omiten cuando la representación no está activa.

Excepción explícita para campos sensibles en reportes personalizados: los campos de nombre, correo, teléfono, contacto y dirección siguen bloqueados por defecto. Una casilla por pregunta advierte de identificación y distribución y registra sensitiveAcknowledged en la configuración del reporte. Solo tras confirmarla el servidor permite textos, coincidencias y gestor del diccionario para ese campo. La advertencia se conserva en el reporte y en la pantalla del diccionario; las representaciones sensibles se retiran al desmarcar la casilla. Las plantillas guardadas no heredan esa autorización ni las selecciones sensibles. Las matrices de preparación y sus exportaciones ordinarias siguen sin incluir originales. Los reportes de texto anteriores mantienen su verificación mediante la proyección compatible.

Diccionarios por contexto (V24): desde el engranaje de Resultados o Reportes se elige Global o Privado. Global comparte reglas; Privado sustituye completamente al global en esa pregunta, comienza vacío la primera vez y conserva sus revisiones al volver a Global. La elección solo se aplica al revisar y guardar. El privado queda asociado a encuesta y definición de la pregunta, compartido entre versiones compatibles de esa misma pregunta; no se comparte con otras preguntas o encuestas. Los alias exclusivamente numéricos se omiten al aplicar reglas globales; se admiten en contexto privado para evitar interpretar cualquier 2 como 2 años. No se borran reglas históricas. El informe de incorporación compara contra el diccionario seleccionado. Los nuevos reportes congelan alcance, revisión, reglas y huella por pregunta (proyección 3), y conservan los conteos aunque se cambie el alcance posteriormente. Los diccionarios privados se incluyen en el respaldo y se eliminan junto a su encuesta; el global se conserva. Privado describe el alcance de las reglas, no un permiso diferente: todo sigue protegido para ADMIN. La clasificación de sensibilidad distingue nombre del programa/curso/asignatura de nombres identificativos de personas u organizaciones.

La advertencia por pregunta también se aplica en Resultados. Los campos identificativos de texto, correo y teléfono ofrecen una casilla desmarcada por defecto. Sin confirmación, la consulta de representación no devuelve textos originales ni coincidencias y el gestor de diccionario rechaza el acceso. La confirmación se transmite explícitamente a las consultas y al gestor; no modifica la captura, el corte o el protocolo. Al desmarcar se borran de la vista los textos y gráficos, se dispone de sus instancias y las respuestas pendientes no pueden reactivarlos. La autorización es solo para esa vista y no se hereda al reporte. La consulta individual protegida Ver respuestas mantiene su acceso administrativo separado.

La biblioteca de Reportes utiliza el personalizador actual para crear, editar, duplicar y aplicar plantillas. Al abrir una plantilla se elige el total actual de una versión o un corte guardado; guardar el diseño no genera un reporte. Eliminar la plantilla no elimina reportes existentes. Detalles y verificaciones: docs/PLANTILLAS_REPORTES.md.
