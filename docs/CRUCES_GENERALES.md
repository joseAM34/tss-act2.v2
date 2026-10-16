# Cruces y chi-cuadrado generales: incremento local

**Actualización:** las [plantillas y reportes HTML](REPORTES_HTML.md) ya permiten incorporar cruces guardados del mismo conjunto que el análisis. Los límites sobre reportes en este documento corresponden al incremento anterior.

Fecha: 2026-10-14. Autorizado mediante «ok luz verde» después de explicar el alcance. Continúa los descriptivos generales, sin commit, push ni despliegue.

## Cómo probarlo

Reinicia desde la raíz con `mvn "-Dspring-boot.run.profiles=dev" spring-boot:run`. Este recorrido utiliza H2 local; no combines `local` si conecta a Neon.

1. Panel → **Preparar datos de encuestas nuevas** → abre un conjunto guardado.
2. Pulsa **Configurar cruce de preguntas**.
3. Elige dos preguntas distintas de selección única incluidas como nominales. Si hay menos de dos, prepara otro conjunto con esas variables.
4. Conserva alpha `0.05` o establece el nivel decidido para ese contraste.
5. Para revisar solo el cruce, deja sin marcar independencia: se guardará la tabla y se explicará por qué no se ejecuta inferencia.
6. Solo si tienes documentación metodológica, marca independencia e indica unidad y evidencia, sin contactos ni identificadores. Marca ceros estructurales si existen combinaciones imposibles por diseño.
7. Pulsa **Guardar cruce**. Consulta tabla, mapa de calor, diagnóstico, pares excluidos y resultado JSON. El enlace **Conjunto fuente** identifica los datos exactos usados.

## Contrato estadístico

`CRUCE_GENERAL_V1` utiliza exclusivamente una encuesta/versión y configuración de un conjunto general congelado. Solo admite preguntas nominales de selección única (`radio` o `select`); rechaza números, selecciones múltiples, preguntas excluidas, códigos ajenos, la misma pregunta en ambos ejes y cruces mayores de 2500 celdas.

Cada par entra cuando ambas preguntas registran `CON_VALOR`. Los demás quedan contabilizados por combinación de `CON_VALOR`, `SIN_RESPUESTA` y `NO_APLICA`. `NO_SABE` permanece una categoría propia. No se infieren estados históricos, elegibilidad ni independencia por cantidad de envíos o coincidencias de contactos.

La tabla conserva todas las categorías, incluidos conteos cero. Presenta observadas, totales, porcentajes por fila, columna y total de pares con valor, cada uno con su base; base cero produce `null`, mostrado como «Sin base». Las esperadas se identifican como diagnóstico. El mapa de calor representa conteos observados calculados en Java y mantiene la tabla equivalente.

Chi-cuadrado usa el cálculo existente de Pearson, sin corrección de continuidad, con alpha registrado. Retira filas y columnas sin observaciones únicamente para la prueba, registrando sus índices. Requiere dos categorías activas por eje, documentación de independencia, ausencia de ceros estructurales declarados y todas las frecuencias esperadas activas ≥5.

Si no corresponde ejecutar, se conserva el diagnóstico y p, decisión y V de Cramér permanecen `null`. Esto no es «no rechazar H0» ni evidencia de ausencia de asociación. Si corresponde, conserva chi-cuadrado, grados de libertad, p, decisión `p < alpha` y V de Cramér sin corrección de sesgo. No se transforma la asociación en causalidad ni alcance poblacional.

Cada cruce constituye un contraste individual. No se declara ajuste global de multiplicidad entre cruces exploratorios. Registrar una declaración administrativa de independencia no prueba su validez ni otorga aprobación institucional; la evidencia requiere revisión metodológica.

## Conservación y archivos

V12 añade `survey_general_cross` con conjunto fuente, actor, fecha, especificación y huella, resultado y huella. No modifica V1–V11 ni formatos anteriores. Al abrir se verifica el conjunto congelado, ambas huellas y la correspondencia del cálculo. Cambios posteriores en respuestas, origen o versión activa no alteran cruces guardados.

`GenericCrossService`, `GenericCrossController`, las plantillas `generic-crosses.html`/`generic-cross.html` y `generic-cross.js` implementan el recorrido. Todas las rutas requieren ADMIN; POST conserva CSRF; JSON y pantallas no se almacenan en caché. Etiquetas y evidencia se escapan; ECharts local utiliza tooltips sobre canvas sin interpretar HTML proporcionado. Si el mapa falla, la tabla sigue disponible.

Las plantillas de reportes editables, exportaciones estadísticas CSV/Excel/PDF, filtros adicionales y familias de contrastes configurables continúan pendientes. El protocolo original UMSS y los descriptivos generales se conservan.

## Verificación

La suite completa pasó **424 pruebas Java en H2 y 424 en la ejecución con PostgreSQL 18 local**, sin fallos ni omisiones. Se utilizaron datos ficticios y una base temporal; no se conectó a Neon ni se realizó commit, push o despliegue.

Pruebas con tabla conocida `[[10,20],[20,10]]`: chi-cuadrado ≈6.666667, p≈0.009823 y V≈0.333333. Casos sin datos, sin variación, sin evidencia, esperadas insuficientes, ceros estructurales, categorías cero, pares incompletos y selecciones incompatibles. También se verifican conservación ante cambios posteriores, huellas, ADMIN/CSRF, escapes y acceso a resultados.

Chrome comprobó selector y resultado con HTML real y datos ficticios a 360, 768 y 1280 píxeles, incluyendo el mapa de calor real, sin desbordamiento ni excepciones JavaScript. No constituye una auditoría completa de accesibilidad.
