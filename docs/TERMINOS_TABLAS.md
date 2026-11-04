# Términos de las tablas de frecuencias

Fecha: 2026-11-04. A petición del usuario se adoptan los nombres del documento de referencia, con notas breves para explicar siglas y denominadores.

| Término mostrado | Significado |
| --- | --- |
| Frecuencia absoluta (fi) | Cantidad de envíos que aportan esa opción o estado; en una tabla numérica, veces que aparece ese valor. |
| Base de cálculo (n) | Denominador de envíos registrado en la tabla. No atribuye independencia ni equivale necesariamente a personas o empresas únicas. |
| Frecuencia relativa (hi) | fi / n, expresada como proporción, con cuatro decimales. |
| Porcentaje (%) | 100 × hi, con dos decimales; se presenta el porcentaje congelado del backend, sin recalcularlo en el navegador. |
| Frecuencia acumulada (Fi) | Suma de frecuencias absolutas hasta un valor numérico ordenado. |
| Porcentaje acumulado (%) | 100 × Fi / n. |

Las tablas categóricas de Resultados, resultados actualizados, análisis generales y reportes HTML/PDF comparten un fragmento. Word utiliza las mismas definiciones. En selección única y cobertura se muestran opción/estado, fi, n, hi y porcentaje, sin columnas repetidas de menciones.

En selección múltiple se añaden **Total de selecciones (m)**, **Frecuencia relativa por selección** y **Porcentaje por selección (%)**. Se explica que una selección es una opción marcada y que cada envío puede aportar varias. La proporción por selección divide fi entre m; la proporción sobre envíos divide fi entre n. Por ello los porcentajes sobre envíos pueden sumar más de 100%, mientras las selecciones tienen su propio denominador. No se fusionan ambas bases.

Las tablas numéricas de valores e intervalos muestran los nombres completos de fi, hi y Fi, conservando límites, marca de clase y unidades. La acumulación no se añade a categorías nominales ni se infiere orden en los rangos de edad. Los resúmenes por origen conservan sus estratos y muestran las medidas numéricas con nombres en español.

`FrequencyPresentation` obtiene hi del conteo y denominador congelados en el servidor, sin modificar resultados almacenados, sus huellas, estados o reglas de cálculo. Cero se conserva como cero; la división sin denominador válido se representa como «Sin base». Word y HTML redondean las proporciones a cuatro decimales y los porcentajes a dos; ambas se redondean por separado. Los nombres técnicos de campos de JSON/CSV/Excel permanecen compatibles.

## Verificación

Pasaron 123 pruebas Java: 77 de preparación general, 22 de Resultados, 15 de resultados actualizados, dos de frecuencias, cinco de Word y dos de exportación de datos. Se comprueban bases distintas en selección múltiple, cero frente a ausencia, redondeo del ejemplo 13/123, vocabulario y presencia condicional de columnas en Word y HTML, conservación de datos y huellas. Se actualizó una expectativa antigua del editor de resultados actualizados para comprobar su encabezado actual «Datos del reporte», manteniendo las comprobaciones del corte y cruce seleccionados.

Chrome verificó las tablas de Resultados e informe y generó un PDF con los nuevos nombres; comprobó ausencia de las columnas de selecciones en preguntas de selección única. Artefactos sintéticos en `.codex-tmp/univariate-browser`, ignorado por Git. Sin migraciones, conexión a datos de campo, commit, push ni despliegue.
