# Perfil descriptivo compatible con la respuesta

Fecha: 29 de octubre de 2026. El usuario autorizó generar las medidas y gráficos compatibles del ejemplo de informe de calidad y validación. El ejemplo no se utiliza como base de datos ni como protocolo numérico: contiene cifras inconsistentes y mezcla tamaños de muestra. No se copia el PDF privado al repositorio.

## Aplicación y compatibilidad

El backend genera `PERFIL_DESCRIPTIVO_V1` desde los valores y estados del conjunto congelado verificado. Se incorpora a Resultados y a los reportes nuevos, con sus exportaciones Word, PDF del navegador y CSV/Excel de resultados agregados. Mantiene bases por pregunta y estratos por origen separados. El navegador representa los resultados recibidos sin recalcular el protocolo.

| Tratamiento | Resultados |
| --- | --- |
| MAGNITUD_ENTERA, con unidad declarada | Media, mediana, moda, mínimo/máximo, rango, varianza y desviación muestrales, Q1/Q2/Q3, RIC, P90, frecuencias absolutas/relativas/acumuladas, intervalos, marcas de clase y posibles atípicos. CV solo cuando aplica. |
| NOMINAL, selección única | Frecuencias por categoría y sus bases, frecuencia relativa y moda; No sabe es categoría propia. Sin media, percentiles ni acumulaciones nominales. |
| NOMINAL, selección múltiple | Opciones más seleccionadas; frecuencias relativas separadas por envíos y por menciones. Las primeras pueden sumar más de 1. Sin acumulaciones o media de códigos. |
| COBERTURA, contactos y textos libres | Conserva cobertura y el análisis temático existente mediante diccionarios; no transforma el contenido en números ni en puntajes. |

Una pregunta numérica sin unidad declarada conserva el tratamiento de cobertura existente; no se infiere su unidad por texto. Un año calendario puede tener descriptivos numéricos si se declara como magnitud, pero no CV.

## Convenciones

- Base numérica: n valores con estado CON_VALOR; se excluyen SIN_RESPUESTA y NO_APLICA. Una pregunta ausente en otra versión no aumenta su base.
- Varianza y desviación estándar muestrales: divisor n−1; requieren n ≥ 2. Varianza en unidad al cuadrado.
- Cuartiles y P90: interpolación tipo 7, índice (n−1)p, coincidente con las convenciones estadísticas existentes. No se sustituyen por los cuartiles del ejemplo.
- Moda: conserva todos los valores de frecuencia máxima. Si todos los valores distintos tienen la misma frecuencia, se informa sin moda; un valor constante conserva su moda. Sin observaciones no hay moda.
- fi = frecuencia absoluta; hi = fi/n; Fi = suma acumulada ordenada de fi. Porcentajes con el denominador indicado. El redondeo visual a dos decimales puede impedir que las hi mostradas sumen exactamente 1; el cálculo conserva su precisión.
- Intervalos: misma regla de Sturges del histograma existente, techo(1+log2(n)), máximo 20 intervalos iguales; [inferior, superior), último cerrado. Valor constante: intervalo puntual. Marca de clase: punto medio.
- Polígono: coordenadas (marca de clase, fi). Ojiva: (límite inferior inicial, 0) y (límite superior, Fi). Se muestran en ejes numéricos, sin suavizado ni extrapolación. Para intervalos abiertos al final, Fi acumula valores anteriores al límite; el máximo del último intervalo se incluye.
- CV = 100·s/media: exige n ≥ 2, media positiva, valores no negativos y unidad de razón reconocida. Se reconocen años/años completos, meses/meses completos, días, horas, minutos/min, segundos/s, kg/kilogramos, gramos/g, metros/m, centímetros/cm y bolivianos/bs/usd. Las unidades desconocidas, los grados Celsius y los años calendario no lo calculan automáticamente.
- Posibles atípicos: valores fuera de Q1−1,5·RIC y Q3+1,5·RIC, con frecuencia. No son errores confirmados y no se eliminan. La caja existente sigue representando cinco valores con bigotes mínimo/máximo, no bigotes de Tukey.
- No se impone un límite de 60 minutos, se inventa el tipo de muestreo ni se declara representatividad, servicio aceptable, estabilidad temporal o aprobación institucional. El comentario administrativo permite interpretar los resultados con contexto.

## Historial y verificación

No cambia `DESCRIPTIVOS_GENERALES_V1`, `StatisticalMath.numeric`, reglas de preparación ni resultados de cortes guardados. Resultados recibe una proyección de presentación adicional. Los reportes nuevos congelan `descriptive_profile=PERFIL_DESCRIPTIVO_V1` y su resultado de presentación; al abrir se verifica por recálculo desde el conjunto fuente. Los reportes sin ese marcador siguen verificándose con su contrato anterior. Una versión desconocida se rechaza.

La descarga de respuestas originales sigue siendo independiente y no se redondea. Las medidas nuevas se incluyen en CSV/Excel de resultados de reportes y en tablas Word editables; polígono y ojiva se exportan como gráficos XY nativos con líneas y hoja de datos incorporada. Se conservan ADMIN, CSRF y las confirmaciones de contenido sensible existentes. No hay migraciones ni dependencias nuevas.

Validación: 41 pruebas Java pasaron (matemáticas, límites, compatibilidad, resultados, reportes, exportaciones e historial) y 14 pruebas JavaScript pasaron (gráficos, coordenadas preparadas y compatibilidad). Pendiente la revisión visual manual del navegador, impresión PDF y Word.
