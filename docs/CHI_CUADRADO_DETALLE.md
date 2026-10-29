# Hipótesis y cálculo visible de chi-cuadrado

Fecha: 29 de octubre de 2026. Autorizado por el usuario para mostrar las esperadas en tabla separada, los aportes y residuos por celda, las fórmulas y las hipótesis.

## Presentación

El detalle del cruce general y los cruces incluidos en vistas previas y reportes muestran:

- H₀: las dos variables categóricas son independientes en la población de referencia.
- H₁: existe asociación entre las dos variables en la población de referencia.
- Regla de decisión: solo si la prueba es aplicable, rechazar H₀ cuando p < α, usando el nivel registrado sin redondearlo para comparar.
- Frecuencias esperadas bajo H₀ en una tabla propia, además de las observadas y porcentajes ya disponibles.
- Una fila por celda con las categorías, observada O, esperada E, diferencia O−E, aporte (O−E)²/E y residuo de Pearson (O−E)/√E.
- Suma diagnóstica de aportes calculables, número de celdas calculables/no calculables y explicación de la decisión.
- Fórmulas E = total fila × total columna / n, χ² = suma de aportes, gl = (filas activas−1)(columnas activas−1) y V de Cramér = √[χ²/(n·min(filas activas−1, columnas activas−1))]. n es el número de pares con valor incluidos.

Las categorías con márgenes cero permanecen visibles, pero no integran la prueba. Si E=0 o no existe base, aporte y residuo quedan ausentes, no se rellenan con cero. Si E es positivo, su diagnóstico puede calcularse aunque la prueba esté bloqueada: esa suma no se presenta como una prueba ejecutada ni produce p, decisión o V de Cramér.

El residuo se identifica como **de Pearson**, sin confundirlo con un residuo ajustado. Su signo indica exceso o defecto respecto a la esperada. No se generan pruebas por celda ni se atribuye significación a un umbral de residuos. No rechazar H₀ no demuestra independencia; rechazarla no establece causalidad. La independencia entre observaciones es un supuesto previo diferente de la independencia entre variables planteada en H₀.

## Trazabilidad y exportación

`ChiSquareDetails` deriva esta presentación en el backend desde la tabla del cruce ya verificado (`DETALLE_CHI_CUADRADO_V1`). No consulta respuestas nuevas, no cambia el protocolo estadístico ni las hipótesis registradas de aplicabilidad y no modifica el resultado persistido o sus huellas. Los cálculos inferenciales originales y sus condiciones permanecen en `StatisticalMath.contingency`.

Los reportes Word incluyen hipótesis, esperadas separadas y detalle por celda; HTML e impresión PDF utilizan el fragmento compartido. CSV/Excel de resultados agregados añaden la sección `detalle_chi_cuadrado`, con etiquetas de filas y columnas, fórmulas y cifras completas. Se mantienen ADMIN y las protecciones existentes. Las tablas de lectura redondean a dos decimales; la suma de valores redondeados puede diferir del total. El cálculo conserva precisión completa. No se agregan dependencias ni migraciones.

Verificación: 50 pruebas Java pasaron, incluyendo la suma de aportes contra el estadístico conocido, signos y cuadrados de residuos, esperadas nulas/cero, prueba bloqueada, no rechazo de H₀, pantallas, vista previa, Word/CSV y conservación de la huella del cruce. La revisión visual de paginación en Word/PDF sigue pendiente.
