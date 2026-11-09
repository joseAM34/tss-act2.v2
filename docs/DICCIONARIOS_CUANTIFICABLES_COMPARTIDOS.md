# Diccionarios cuantificables compartidos

## Referencia principal y otras referencias por renglón (flujo vigente)

El usuario precisó que no quiere escribir fórmulas de proporcionalidad. Arriba se indica la referencia principal, por ejemplo «Año» o «Kilogramo»; debajo, solo las otras referencias, una por renglón («Meses», «Días», «Décadas» o «Gramos», «Toneladas»). El campo histórico `proportions` conserva el texto para compatibilidad, pero su presentación y uso en el prompt son ahora una lista de referencias. Los valores existentes no se reescriben automáticamente.

El prompt pide calcular equivalencias habituales y exactas, incluidas las inversas, y señalar contradicciones. No exige que el usuario enumere factores numéricos. Días↔meses/años continúan pendientes cuando falta una convención, al igual que rangos e interpretaciones ambiguas. No se consulta una IA desde el sistema: se prepara el texto para copiar y se revisa la propuesta antes de registrar equivalencias.

Generar incorpora automáticamente las respuestas de la pregunta; no hay checkbox de inclusión. La opción avanzada de pegar otras respuestas, si contiene texto, sustituye las de la pregunta. El prompt incluye una sola estructura de datos de contexto sin repetir la referencia y un ejemplo JSON válido separado. Exige procesar todos y solo los IDs reales, conservando originales y duplicados; prohíbe tablas, Markdown y sustituir los datos por ejemplos. El importador rechaza el ID de ejemplo y estados no soportados como PROCESADO, en lugar de producir silenciosamente un diccionario vacío.

Tampoco hay checkbox para declarar cuantificable: guardar equivalencias revisadas activa ese modo. El botón «Desactivar cuantificables de esta pregunta» opera sobre la selección persistida con comprobación de revisión; no borra reglas compartidas ni depende de borradores sin guardar. Al pegar y cargar la respuesta de la IA se abre automáticamente la tabla de revisión. El checkbox de revisión se conserva y sigue siendo obligatorio para guardar.

Verificación: suite completa de 691 pruebas Java y 53 JavaScript sin fallos. Chrome comprobó nombres por renglón, respuestas automáticas, ausencia de checks redundantes, prompt con ejemplo JSON, carga/revisión automática, registro y desactivación sin vaciar el compartido. No se copió el listado de respuestas adjunto por el usuario a fixtures ni documentación. Sin commit, push o despliegue.

Las siguientes secciones documentan los incrementos anteriores y se interpretan con este flujo vigente.

## Contexto libre y proporciones (ampliación vigente)

El usuario autorizó generalizar la herramienta a peso, longitud, dinero, recuentos u otras magnitudes y quitar los selectores limitados al tiempo y la unidad repetida. Los nuevos diccionarios se crean con **nombre/contexto libre** (por ejemplo, «Peso en kilogramos») y **proporciones o indicaciones** («1 kg = 1000 g; conservar decimales»). Los privados tienen esos mismos campos en el alcance. En un compartido seleccionado, el nombre identifica su referencia y las proporciones se editan con revisión; para otro significado de referencia se crea otro diccionario.

El prompt incorpora pregunta, nombre del diccionario, contexto de interpretación, proporciones y muestras originales. No impone la magnitud tiempo ni un catálogo de unidades o equivalencias. Una cantidad sin referencia escrita se interpreta según el contexto cuando es inequívoco; si falta una proporción necesaria, hay rangos o la interpretación es ambigua, se pide PENDIENTE. Los textos de contexto/proporciones no se ejecutan como código ni como fórmulas del servidor. La IA propone; la revisión y el guardado administrativos son obligatorios.

La referencia que aparece como `unidad_destino` en el JSON es una etiqueta de resultados: en un grupo nuevo coincide con su nombre/contexto. La propuesta debe conservar esa etiqueta, que el importador comprueba para evitar cargar datos destinados a otro contexto. No hay un selector adicional de unidad. Se admiten valores finitos con signo entre −1000000000 y 1000000000; restricciones particulares, como empleados enteros o pesos no negativos, se expresan en el contexto y requieren revisión de las propuestas. El servidor valida tamaño, formato, valores finitos, conflictos y respuestas ambiguas evidentes; no afirma interpretar automáticamente toda proporción o restricción escrita en lenguaje natural.

Las definiciones genéricas usan versión 3 y congelan contexto y proporciones en cada revisión. Las versiones anteriores se conservan; sus informes siguen verificándose con sus reglas originales. Las estadísticas y gráficos se calculan desde los valores revisados. El contexto libre no prueba una escala de razón ni un cero significativo, por lo que no se declara CV automáticamente para estas nuevas normalizaciones. Media, mediana, frecuencias, dispersión, percentiles, histogramas y caja conservan su cálculo sobre las cantidades guardadas.

V29 amplía la etiqueta técnica de referencia a 120 caracteres sin modificar V28 ni migraciones aplicadas. La suite completa final pasó 690 pruebas Java y 53 JavaScript. Chrome verificó campos libres sin selectores de tiempo, creación de grupos de peso y recuentos, contexto/proporciones/muestras en el prompt, carga de propuestas, registro, invalidación al editar contexto y conservación de borradores por selección. Los datos de prueba son ficticios. Sin commit, push ni despliegue.

Las secciones siguientes documentan el incremento temporal inicial y quedan ampliadas por este apartado.

Implementación autorizada el 2026-11-09. El alcance se muestra arriba y es común a las dos pestañas. Para términos se mantienen el global único y los privados por pregunta. Para cantidades se puede elegir un privado o crear/seleccionar uno de varios diccionarios compartidos. No se crean diccionarios predefinidos.

## Crear y alimentar

1. Abrir el engranaje de una pregunta desde Resultados o su gestor contextual desde Reportes y elegir «Datos cuantificables».
2. En «Alcance del diccionario», seleccionar uno existente o desplegar «Crear diccionario compartido». Indicar nombre libre y unidad principal: años, meses, días, horas, minutos o segundos. La unidad del compartido queda fija; para otra unidad se crea otro diccionario.
3. Generar el prompt. Incluye por escrito la pregunta, el nombre del diccionario, la magnitud tiempo, la unidad principal, la interpretación de cantidades sin unidad y las respuestas de muestra del conjunto seleccionado. Pueden pegarse muestras manuales en lugar de recuperar las actuales. Se conservan duplicados y originales, con identificadores R1, R2…; no se mandan datos a servicios externos automáticamente.
4. Cargar la propuesta JSON o escribir equivalencias, previsualizar, revisar y guardar. Cambiar la selección no aplica reglas hasta guardar. Los borradores de cada selección se conservan en memoria mientras la página permanezca abierta. Cambiar de diccionario o unidad invalida el prompt preparado.

En «Tiempo en años», `1`, `2` y `3` significan años. Si la respuesta especifica meses se convierte: `1 mes → 1/12 de año`, `18 meses → 1,5 años`. El nombre no sustituye al campo de unidad explícito. El backend comprueba las cantidades con cifras y unidades reconocidas y exige conservar el valor de las cantidades sin unidad en la unidad principal. Las cantidades expresadas con palabras necesitan revisión administrativa. La salida de la herramienta externa no se considera una validación aprobada.

Se admiten años↔meses y días↔horas↔minutos↔segundos. No se supone que un mes tenga 30 días ni que un año tenga 365: conversiones entre esos dos grupos quedan pendientes, también cuando la unidad está escrita con palabras. Los rangos, fechas y cantidades imprecisas no se convierten en un valor exacto. Las propuestas pendientes no se incorporan como cero; los originales y la base pendiente se conservan.

## Revisión compartida y selección de pregunta

Guardar un compartido habilitado registra una revisión de sus equivalencias y la selección de la pregunta. Otras preguntas vinculadas reciben esa revisión al actualizar sus resultados o generar otra vista previa. Se comprueban la revisión de la pregunta y la revisión compartida para rechazar cambios concurrentes. Desactivar la casilla cuantificable desactiva solo la pregunta; no vacía el compartido. Vaciar sus reglas con la casilla activa sí afecta a las preguntas vinculadas, con advertencia y revisión previa.

Las reglas de exclusión entre agrupación y cantidades siguen vigentes. Si el compartido queda vacío y una pregunta registra términos, esa pregunta se desvincula del modo numérico activo: alimentar de nuevo el compartido no reactiva cantidades sobre la agrupación recién registrada. Ninguna operación elimina las revisiones anteriores.

Los informes nuevos congelan las equivalencias resueltas, nombre, identificador, unidad, revisión compartida, revisión de selección y huella. Abrir un informe guardado utiliza ese contenido, no las reglas compartidas actuales. Se conservan los informes y diccionarios privados anteriores. La migración V28 crea el catálogo y sus revisiones; no modifica migraciones aplicadas. Las definiciones contextualizadas usan versión 2, mientras las anteriores conservan su contenido.

## Acceso y verificación

Todas las rutas requieren ADMIN y los POST mantienen CSRF y `Cache-Control: no-store`. Se permiten únicamente preguntas de texto no identificativas; no se copian contactos a prompts numéricos. El navegador representa los cálculos del backend. Los diccionarios compartidos se consultan por lotes al congelar las selecciones, sin una consulta por pregunta vinculada.

La suite completa pasó 688 pruebas Java y 53 JavaScript. La integración verifica creación autenticada, muestras, propagación entre preguntas, desactivación local, conflictos de revisión, unidad fija, informes congelados y conservación del modo de términos tras realimentar un compartido vacío. Las pruebas de unidades cubren fracciones, decimales, conversiones directas y conversiones pendientes. Chrome verificó el alcance común, creación de años/minutos sin grupos iniciales, prompt con pregunta/contexto/muestras, unidad fija, registro, borradores por selección y conflicto de revisión. Los datos son ficticios; sin despliegue, commit ni push.


### Equivalencias cuantificables en texto

El prompt ahora solicita una línea por valor: `1.5=1,5 años|18 meses`, igual que el formato de términos. La referencia principal y las referencias a convertir siguen contextualizando el prompt. La carga combina las equivalencias con las del editor, rechaza valores contradictorios para una misma respuesta y abre la previsualización antes del guardado explícito. Las respuestas omitidas quedan pendientes; una propuesta vacía no borra el diccionario. Se conserva la importación JSON anterior por compatibilidad. Este flujo se aplica a Resultados y Reportes.

### Dispersión con cantidades derivadas

La dispersión admite magnitudes nativas y equivalencias cuantificables revisadas. Se construye una vista numérica temporal por envío, verificando la huella de cada captura y definición, sin alterar la matriz ni sus originales. Los pares pendientes o sin valor se excluyen. La verificación de reportes guardados utiliza exclusivamente sus diccionarios congelados en `text_dictionaries`; nunca resuelve grupos vigentes al reabrirlos.
