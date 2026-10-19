# Recepción histórica de respuestas externas

La importación admite dos recorridos explícitos. La recepción habitual requiere una columna de aceptación académica por participante. **Archivo histórico: consentimiento no registrado en la fuente** permite incorporar una entrega administrativa sin inventar esa aceptación ni afirmar rechazo. El administrador selecciona el modo y marca su declaración; se conserva el archivo original, su huella, la revisión, cuenta y fecha. Una referencia adicional permite citar una entrega o autorización si corresponde.

Este modo no clasifica automáticamente los datos como reales, no aprueba el instrumento y no resuelve requisitos institucionales pendientes. Tampoco modifica los formularios públicos: estos siguen requiriendo aceptación del participante.

## Recorrido

1. Seleccionar una versión publicada cuya definición corresponda a las preguntas, opciones, unidades y condiciones de la fuente. Si difiere del instrumento vigente, crear otra versión conservando las anteriores.
2. Cargar el CSV UTF-8 o XLSX y revisar las correspondencias propuestas.
3. Seleccionar el tipo de recepción. Para un histórico sin columna de consentimiento, elegir el modo histórico y marcar su declaración administrativa. Registrar una referencia documental cuando sea necesaria.
4. Revisar opciones, campos faltantes y condiciones en la vista previa. La recepción histórica conserva las demás validaciones; un lote con errores no se incorpora parcialmente.
5. Confirmar la vista previa exacta. Los reintentos no duplican el lote; sus revisiones y originales permanecen disponibles.

## Lectura de selecciones y encabezados

`JSON` interpreta exclusivamente listas de textos como `["Opción A","Opción B"]`. La lectura nueva `MIXTO_V1` permite listas JSON y, en otras celdas, opciones con un separador inequívoco. Reconoce un único punto y coma final como terminador de Microsoft Forms, normaliza espacios no separables al cotejar opciones y conserva las comas dentro de paréntesis. No elimina opciones vacías interiores ni varios terminadores. Se propone cuando hay estos formatos en una pregunta de selección múltiple; requiere revisión. Una lista mal formada no se convierte en una selección parcial. Los textos originales no se modifican; el modo previo `AUTO` y las revisiones existentes conservan su interpretación.

Los encabezados pueden corresponder al código o enunciado completo. Se admite quitar únicamente un prefijo numérico inicial con punto/paréntesis y espacio (`12) ` o `12. `), o los corchetes que envuelven todo un encabezado de matriz, como `[Comunicación]`. Las coincidencias ambiguas permanecen pendientes; no se extraen fragmentos de enunciados largos.

## Contrato de conservación

- `survey_response.consent` queda en `NULL` exclusivamente para representar ausencia de registro en esta recepción. No equivale a `FALSE` ni a revocación. La captura lógica utiliza el indicador `false` acompañado obligatoriamente por un recibo verificado con estado `NO_REGISTRADO_EN_FUENTE`.
- La migración V19 permite ese valor nulo sin modificar las respuestas anteriores ni sus huellas. Las capturas habituales conservan consentimiento verdadero y su representación original.
- No se cambia la estructura serializada de `Mapping`. La revisión histórica se identifica mediante columna reservada `-2`, valor `RECEPCION_HISTORICA_V1` y declaración `RECEPCION_HISTORICA_DOCUMENTADA_V1`; una combinación incompleta no se acepta.
- El recibo verificado vincula versión, archivo original, fila, revisión y captura. El lector exige que recibo, captura y columna de consentimiento coincidan; un recibo borrado o una bandera alterada invalida la integridad.
- `submitted_at` representa la recepción/importación. La fecha original de la plataforma se conserva separadamente y solo se interpreta si tiene formato ISO 8601 con zona. No se fabrican fechas históricas desde seriales Excel ni zonas desconocidas.
- Todas las preguntas deben tener una columna de origen. Las categorías por intervalos no se convierten en magnitudes exactas; los campos no recolectados no reciben respuestas ni estados históricos inventados.

Este documento describe el contrato técnico y no acredita autorización institucional para usar datos de campo. Las pruebas utilizan únicamente ejemplos sintéticos, sin archivos oficiales ni datos privados versionados.

## Categorías observadas que faltan en la versión

En la lectura `MIXTO_V1`, una selección múltiple puede contener opciones reconocidas y una etiqueta nueva. La vista previa distingue las etiquetas ausentes de los problemas de separadores y permite seleccionar cuáles añadir mediante **Añadir opciones en un borrador**. Las comas entre paréntesis se conservan. Las listas ambiguas, mal formadas o compuestas únicamente por valores desconocidos requieren revisión manual; tampoco se proponen códigos desconocidos como etiquetas nuevas.

La acción prepara o reutiliza el borrador sucesor de la misma encuesta, conservando sus ediciones. Añade únicamente categorías observadas seleccionadas, con códigos estables propios; no las declara opciones oficiales originales, ni las convierte en «Otro». Si el borrador cambió el tipo o eliminó/modificó opciones originales de esa pregunta, pide revisar esos cambios antes de continuar. Un límite explícito de selecciones se conserva; solo se amplía si anteriormente permitía elegir todo el catálogo.

Después de revisar y publicar el borrador, abre el lote anterior y despliega **Usar este archivo en otra versión**. **Reutilizar archivo original** abre un lote para la publicación elegida de la misma encuesta, con los mismos bytes, plataforma y hoja XLSX. El original, las publicaciones y revisiones anteriores se conservan. Se vuelven a revisar las correspondencias, la recepción histórica y las coincidencias antes de confirmar; las declaraciones no se copian automáticamente. Repetir la reutilización para el mismo destino abre su lote existente.

Los textos de recepción histórica se conservan hasta el límite de celda de 8000 caracteres, para no truncar exportaciones que superan el límite de un formulario público. Los límites de entrada pública (1000/2000 según el campo) permanecen vigentes. Campos de contacto de las plantillas oficiales se conservan como texto y quedan excluidos de las matrices ordinarias.


## Cuando aparece «Falta columna y evidencia de consentimiento»

Ese aviso corresponde a una revisión de correspondencias en modo individual que no tiene columna de consentimiento o su revisión documental. Para un archivo histórico sin ese registro, pulsa **Revisar como archivo histórico** desde el aviso (o vuelve a correspondencias y elige el tipo de recepción histórico). El acceso solo preselecciona la pantalla: no guarda correspondencias, copia declaraciones anteriores ni marca la confirmación.

Revisa la recepción y compatibilidad, marca las casillas correspondientes y pulsa **Validar y continuar**. Se crea otra revisión y se abre su nueva vista previa. Una vista previa anterior conserva su aviso; no se altera su huella ni se convierte automáticamente en válida. Las pruebas de este acceso comprueban también que una declaración de consentimiento anterior no se reutilice como referencia de entrega histórica. Verificación: 22 pruebas de integración de recepción histórica y asistente correctas.
