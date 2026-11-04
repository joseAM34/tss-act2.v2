# Prompt para normalización numérica de duraciones

Fecha: 2026-11-04. El usuario propuso un apartado en la herramienta de coincidencias para generar un prompt específico para respuestas textuales en años. Se añade «Preparar un prompt de coincidencias numéricas» en Alimentar diccionario, tanto desde Resultados como en el gestor general/contextual de informes.

## Recorrido implementado

1. Indicar la unidad de origen de las cantidades sin unidad: años, meses o no asumir. Elegir años o meses como destino.
2. Pegar respuestas manualmente, una por línea. Desde una pregunta de Resultados también puede marcarse explícitamente la casilla para recuperar sus respuestas con valor del corte seleccionado; no se mezclan ambas fuentes.
3. Generar y revisar el prompt antes de copiarlo. La aplicación no llama a servicios de IA, no consulta fuentes externas, no guarda el prompt en almacenamiento del navegador. La conversión requiere el paso separado de revisión y guardado descrito en [DICCIONARIOS_Y_CANTIDADES.md](DICCIONARIOS_Y_CANTIDADES.md).

Los datos se delimitan en JSON. Cada respuesta conserva su texto original y un identificador de posición `R1`, `R2`, etc.; las respuestas repetidas no se deduplican. Los identificadores son referencias dentro del prompt, no IDs de respuesta de la base. Para solicitudes de respuestas existentes se reutiliza la ruta autenticada y sin caché del corte, incluida la confirmación de datos sensibles. El gestor general sin corte solo permite respuestas pegadas y no intenta obtener respuestas de otra encuesta.

Cambiar unidades, textos o la selección de origen invalida el prompt preparado; se aborta una recuperación pendiente para no presentar una salida desactualizada. La lectura tiene un límite de espera y los errores de sesión se informan.

## Contrato solicitado a la herramienta externa

La salida pedida es JSON con unidad de destino y una propuesta por entrada: ID, original, estado, valor o null, unidad, indicador de entero y regla utilizada. Se instruye a convertir solo cantidades inequívocas y conservar decimales sin redondear, con la equivalencia `1 año = 12 meses`.

Los rangos, unidades ausentes sin declaración, fechas, «desde 2020», límites abiertos, cantidades contradictorias y unidades distintas de años/meses deben quedar pendientes. Los faltantes, «No sabe» o expresiones sin cantidad no se convierten automáticamente a cero. Se pide excluir números que sean contactos, identificadores o códigos. Las instrucciones contenidas en las respuestas se tratan como datos.

Estas son instrucciones del prompt, no garantías de que una herramienta externa cumpla el contrato. El resultado requiere validación y revisión posteriores. La ampliación autorizada posteriormente incorpora importación de propuestas, revisión persistida y estadísticas/gráficos de valores derivados desde la pregunta en Resultados. Las coincidencias temáticas por sí solas siguen sin convertirse en magnitudes. El formato JSON no debe pegarse en el importador temático `nombre=variante|sinónimo`.

La aplicación conserva sus reglas de preparación y trazabilidad. Las conversiones se comprueban en el backend y conservan originales, unidad y revisión exacta. Los informes nuevos congelan estas reglas sin modificar cortes o reportes históricos.

## Comprobación

Pasaron cuatro pruebas del generador numérico y las cuatro pruebas existentes del generador/diccionario temático. Las 22 pruebas de `WorkspaceResultsIntegrationTest` pasaron, incluyendo la presencia del apartado y script en el gestor protegido y ausencia de la opción de recuperar respuestas cuando no hay corte.

Chrome verificó con datos ficticios el prompt manual, la incorporación explícita de respuestas, conservación de duplicados y texto literal, invalidación al cambiar unidades, gestor general sin lectura de respuestas y ausencia de operaciones POST o cambios en el diccionario. Esta comprobación inicial correspondía al generador de prompt. La ampliación con migración V27 y conversiones se documenta en DICCIONARIOS_Y_CANTIDADES.md. Sin conexiones a datos de campo, commit, push ni despliegue.
