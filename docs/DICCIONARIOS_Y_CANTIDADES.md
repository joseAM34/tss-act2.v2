# Diccionarios y cantidades revisadas

Ampliación vigente del 2026-11-09: [diccionarios cuantificables compartidos](DICCIONARIOS_CUANTIFICABLES_COMPARTIDOS.md), con alcance común y unidades de tiempo explícitas. Las secciones siguientes documentan el incremento inicial.

## Pantalla por modos (2026-11-04)

La pantalla se organiza en las pestañas «Agrupación de términos» y «Datos cuantificables». Cada pestaña reúne su prompt, la carga de propuestas y, debajo, la edición y revisión del diccionario correspondiente. Alternar pestañas no elimina los campos escritos ni vuelve a cargar la página. La selección visual no registra un modo por sí sola.

Una pregunta solo puede registrar un modo activo: agrupaciones no vacías o cantidades habilitadas con equivalencias no vacías. El otro modo queda bloqueado en la interfaz y el servidor rechaza su registro, incluso desde otra pestaña del navegador. El modo activo sigue siendo editable. Para pasar de términos a cantidades se usa «Desactivar agrupación para esta pregunta» o se vacía y guarda el privado. La desactivación selecciona un privado vacío; no borra el global. Para pasar de cantidades a términos se desmarca la casilla cuantificable o se vacían las equivalencias y se revisa y guarda. Ninguna de estas operaciones modifica los originales o informes guardados.

El global aplicado por defecto, sin una elección registrada para la pregunta, no bloquea el primer registro de cantidades. Al registrarlas se selecciona explícitamente un privado vacío para dejar de aplicar términos globales a esa pregunta. En contextos anteriores con ambas clases de reglas, las cantidades habilitadas tienen prioridad; las revisiones anteriores se conservan. No se añade otra tabla ni se modifican migraciones existentes.

El editor numérico también está disponible desde el gestor contextual de una pregunta de Reportes. El gestor global del footer conserva la preparación de prompts, pero no registra cantidades sin una pregunta concreta. Todas las escrituras mantienen ADMIN, CSRF y comprobación de revisiones. Cambiar el modo requiere guardar la operación correspondiente; borrar textos pendientes o cambiar de pestaña no altera lo persistido.

Implementado el 2026-11-04 por autorización del usuario. Los diccionarios privados se administran desde su pregunta; el global desde «Alimentar diccionario» en el pie de página. El gestor permite quitar una variante, quitar un grupo o vaciar las reglas. La eliminación queda pendiente hasta previsualizar, confirmar la revisión y guardar. Se conservan las revisiones anteriores.

## Duraciones escritas

En Resultados, abrir el engranaje de Coincidencias. En «Cantidades de esta pregunta», marcar «Esta pregunta contiene cantidades (cuantificable)», declarar años o meses y preparar equivalencias de respuestas completas:

```text
1=1 año|un año
1.5=1,5 años|18 meses
```

El valor a la izquierda está expresado en la unidad seleccionada. Se admite coma o punto decimal. No se aplica la normalización temática que elimina puntuación: `1`, `10`, `1,5` y `1 5` son respuestas diferentes. Solo se normalizan mayúsculas, espacios y composición Unicode; las variantes de acentos o escritura deben declararse explícitamente. Una regla se aplica una vez por envío, manteniendo respuestas repetidas como observaciones distintas.

El generador de prompt numérico sigue siendo local y opcional. Con la casilla cuantificable marcada, el botón general de generar prompt dirige al generador numérico. La propuesta JSON puede cargarse en el apartado de cantidades: sustituye las equivalencias mostradas, sin guardarlas. Solo se incorporan entradas CONVERTIBLE con valor finito, unidad compatible y original completo. Las propuestas requieren revisión humana. También pueden escribirse equivalencias sin utilizar otra herramienta.

La vista previa muestra cada respuesta original y su valor o estado pendiente. Los rangos y cantidades imprecisas se rechazan como valores exactos; las respuestas sin equivalencia se excluyen, nunca se convierten en cero. Para respuestas explícitas con cifras y años/meses se comprueba la conversión `1 año = 12 meses`; equivalencias en palabras requieren revisión administrativa. El límite técnico es 2000 variantes y duraciones entre cero y mil millones de unidades. Solo se admiten preguntas textuales no identificativas. No se cambia el instrumento ni el estado original CON_VALOR/SIN_RESPUESTA/NO_APLICA.

Después de previsualizar, revisar y guardar, volver a Resultados y pulsar «Actualizar esta pregunta». Se actualizan sus coincidencias, tablas y gráficos sobre los mismos envíos/capturas del corte. «Actualizar todos» obtiene una nueva carga completa. Histogramas, polígono y ojiva requieren al menos una cantidad válida; caja requiere dos. Las tablas incluyen medidas, fórmulas, frecuencias y bases numéricas. Las cantidades pendientes tienen un recuento explícito separado de los faltantes originales.

## Borrado e informes

«Quitar respuesta» y «Vaciar equivalencias numéricas» modifican las reglas numéricas pendientes; se requiere su revisión y guardado. En el gestor de una pregunta de Resultados, «Vaciar diccionario seleccionado» en modo privado vacía también las equivalencias numéricas al guardar el diccionario. Ambas revisiones se guardan en una transacción, con comprobación de las revisiones esperadas. Un privado vacío sigue siendo privado; no reaparecen las reglas globales automáticamente.

Al actualizar la pregunta, las representaciones sin datos se deshabilitan y se retiran sus gráficos. El borrado no modifica respuestas originales ni informes anteriores. Los informes nuevos incorporan las equivalencias numéricas vigentes, unidad, revisión y huella en sus selecciones congeladas; recalculan desde las capturas verificadas del conjunto. La vista previa y las exportaciones Word/PDF conservan la base convertida y las pendientes. No se añaden cruces, correlaciones ni simulaciones a partir de estas conversiones.

La migración V27 añade el historial privado `numeric_dictionary_revision`, con actor, fecha, contexto de encuesta/pregunta/definición y huella. Los cambios concurrentes se rechazan. Los accesos requieren ADMIN, las escrituras CSRF y las respuestas no se almacenan en caché. El respaldo y borrado de encuestas incluyen el historial nuevo. No se modifica ninguna migración previa ni se conectan servicios externos.

## Verificación

Pruebas de normalización: decimales, duplicados, coincidencia completa, conflictos, unidades, rangos, límites y propuestas pendientes. Integración: autorización CSRF, revisión obligatoria, persistencia, conflicto de revisiones, vaciado privado completo e informe congelado que sigue abriendo después del borrado. Chrome: edición y guardado, prompt cuantificable, eliminación privada, histograma real y refresco individual que deshabilita y vuelve a habilitar gráficos sin solicitar la actualización total. Datos ficticios en las pruebas; no se usaron datos de campo.
