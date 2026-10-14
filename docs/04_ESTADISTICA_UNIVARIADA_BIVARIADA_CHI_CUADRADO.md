# Paso 04: estadística univariada, bivariada y pruebas chi-cuadrado

**Uso:** entregar este archivo a Codex después de `03_DATOS_Y_DATA_WRANGLING.md`. Conservarlo en `docs/04_ESTADISTICA_UNIVARIADA_BIVARIADA_CHI_CUADRADO.md`.

**Estado:** protocolo estadístico inicial propuesto para la aplicación. Conserva preguntas, códigos, estados y decisiones pendientes de los pasos anteriores. Las convenciones de cálculo se documentan aquí; no representan resultados del trabajo de campo.

**Resultado de esta etapa:** indicadores, denominadores, medidas descriptivas, cruces, condiciones y resultados de chi-cuadrado, contrato de salidas y casos numéricos de referencia. El motor Java se implementará posteriormente.

## 1. Instrucción para Codex

Revisa los pasos 01-03 y las instrucciones del repositorio. Incorpora este protocolo y actualiza sus referencias si estás trabajando efectivamente en el repositorio. Conserva los archivos y decisiones existentes.

En esta tarea documenta los cálculos y sus condiciones. No implementes todavía servicios, consultas, dashboard, exportaciones ni simulación. Los ejemplos numéricos son sintéticos: permiten comprobar una implementación futura y no se presentan como datos de titulados o empleadores UMSS.

El motor estadístico consumirá los conjuntos preparados por Data Wrangling y realizará los cálculos en el backend Java. La visualización computacional y los reportes automatizados de HU5 consumirán estos mismos resultados, con sus denominadores y trazabilidad.

## 2. Fuente, alcance y protocolo de trabajo

Fuente del proyecto: `JOSE ARMANDO MAREÑO MAMANI - EstudioMercadoHerramientaColeccionDatos.pdf`, **Guía Metodológica y Técnica Integral: Análisis de Mercado y Herramienta de Recolección de Datos (Enfoque ARCUSUR)**, de 20 páginas.

| Exigencia de la guía | Correspondencia en este documento |
| --- | --- |
| Marco metodológico, página 1 | Estadística univariada, bivariada con chi-cuadrado y resultados para visualización. |
| HU1, página 3 | Perfil temporal, media, mediana y desviación estándar de antigüedad; ocupación y segmentación por cohorte. |
| HU2, páginas 3-4 | Frecuencias absolutas y relativas de formación continua y áreas de interés. |
| HU3, página 4 | Contingencia financiamiento futuro × nivel de posgrado deseado y contraste de independencia. |
| HU4, página 4 | Valoraciones y brechas; puntajes medios y dispersión compatibles con el instrumento definido. |
| HU5, página 5 | Matrices cruzadas y resultados trazables preparados por el backend para gráficos y reportes automatizados. |

Las fórmulas y convenciones se contrastaron con las fuentes técnicas de la sección 15. Las siguientes decisiones son la base propuesta `PROTOCOLO_ESTADISTICO_V1`; registrarlas y versionarlas. Codex puede resolver ajustes técnicos rutinarios sin convertirlos en cambios del instrumento.

| Convención | Base propuesta |
| --- | --- |
| Unidad de análisis | Titulados: participante/envío elegible sin reintentos duplicados. Empleadores: según D16; el ID distinto de envío no demuestra una organización distinta ni independencia. |
| Porcentajes | Sobre respuestas válidas de la variable, salvo otro denominador explícito. Mostrar numerador y denominador. |
| Ausencias | Sin imputación por defecto; conservar estados y exclusiones por resultado. |
| Desviación estándar | Muestral, con divisor n-1; para n < 2, devolver NULL con motivo. |
| Cuantiles numéricos | Interpolación lineal tipo 7, con regla definida en 5.2. |
| Chi-cuadrado | Pearson de independencia sobre frecuencias absolutas enteras, sin corrección de continuidad en esta versión. |
| Frecuencias esperadas | Regla operativa conservadora: todas las celdas activas con esperada ≥ 5. |
| Significación | Alpha = 0,05 por defecto; comparar valores sin redondear usando p < alpha. Registrar el valor aplicado. |
| Magnitud de asociación | V de Cramér sin corrección de sesgo como complemento propuesto de un contraste válido. |
| Varios contrastes | Familia declarada antes de revisar resultados; ajuste de Holm según sección 9. |
| Presentación | Precisión completa para cálculo; redondear únicamente al mostrar o exportar. |

El diseño muestral determina el alcance inferencial. Cuando no se documente una selección probabilística o un modelo que justifique la inferencia, presentar los hallazgos como exploratorios de los participantes observados, con sus supuestos. Los mínimos de organizaciones del trabajo de campo no garantizan condiciones para chi-cuadrado.

## 3. Contrato de entrada y elegibilidad

Recibir `matriz_titulados` o `matriz_empleadores`, diccionario y manifiesto del paso 03. Cada solicitud identifica conjunto, huella, estudio, versiones compatibles, filtros y protocolo.

Para cada variable, conservar los estados CON_VALOR, SIN_RESPUESTA, NO_APLICA, NO_RECOLECTADA y VALOR_INVALIDO. Una incidencia relacional puede bloquear un grupo de cálculos sin eliminar toda la respuesta de otros resultados.

| Recuento | Definición |
| --- | --- |
| `N_base` | Unidades/envíos elegibles del conjunto filtrado, sin mezclar tipos de cuestionario ni datos reales y sintéticos. |
| `N_presente` | Unidades cuya versión contiene la variable. |
| `N_aplicable` | Unidades de N_presente para las que la pregunta aplica según la regla aprobada. |
| `N_con_valor` | Unidades con CON_VALOR, antes de aplicar bloqueos adicionales de calidad. |
| `N_validos` | Unidades con valor utilizable para el resultado concreto tras controles de calidad. |
| `N_no_sabe` | Unidades válidas con código NS cuando existe esa opción. |
| `N_sustantivos` | Respuestas válidas de una valoración excluyendo NS, para resumen del grado de acuerdo. |
| `N_pares_validos` | Unidades que aportan valores utilizables para ambas variables del cruce. |

Mostrar además cantidades por estado y por exclusión de calidad, con ámbito identificado. Los bloqueos adicionales de calidad no se suman como si fueran un sexto estado independiente de los anteriores: pueden solaparse con ellos.

Para cruces, N_pares_validos se obtiene de la intersección de IDs elegibles para las dos variables. No usar el mínimo de sus tamaños separados. Mantener reglas de comparación entre versiones; compartir un nombre de columna no garantiza comparabilidad.

Si un denominador es cero, devolver porcentaje NULL y estado SIN_DATOS. Un cero observado válido sigue siendo cero; no sustituir una ausencia por ese valor.

## 4. Plan de indicadores univariados

| ID | Bloque y variables | Resultados previstos |
| --- | --- | --- |
| U01 | Perfil: T01/T02; T35 y antigüedad derivada | Frecuencias de grupos demográficos y año de egreso; resumen numérico de antigüedad cuando D01/D02 estén resueltas. |
| U02 | Situación laboral: T14/T36 y cohortes de T35 | Frecuencias, porcentajes y proporción de ocupación declarada; distribución por cohorte sin inventar umbrales junior/consolidado. |
| U03 | Formación realizada/en curso: T03-T06 | Proporción con formación complementaria y distribuciones de nivel, institución y financiamiento bajo sus condiciones. T04 no distingue por sí solo programa concluido de programa en curso. |
| U04 | Formación futura: T08/T09/T10/T37/T38 | Interés, áreas, institución, nivel deseado y financiamiento futuro con bases explícitas y cardinalidad aprobada. |
| U05 | Emprendimiento: T15-T19 | Frecuencias de origen, entregable y financiamiento; valoraciones de satisfacción e importancia, dentro de la población aplicable. |
| U06 | Trayectoria: T12/T13/T20/T21/T22/T41 | Resúmenes de duraciones aprobadas; frecuencias de primer empleo, tiempos categóricos y cantidad categórica de empleos. |
| U07 | Formación y competencias: T11/T23/T24/T39/T40 | Distribuciones ordinales, categorías centrales y resúmenes de puntajes según sección 5.3; matrices por ítem cuando D05 esté resuelta. |
| U08 | Aspectos/asignaturas: T25/T26/T27/T29 | Conteos por opción y porcentajes de participantes; cantidad de menciones como resultado separado. |
| U09 | Red de contactos: T31 | Frecuencia y porcentaje de interés. No interpretar esta respuesta como autorización de cualquier comunicación. |
| U10 | Organización: E02/E03/E04 | Distribuciones por tipo, tamaño y sector; categorías de tamaño no son conteos exactos de funcionarios. |
| U11 | Empleabilidad: E05-E09 | Contratación declarada, expectativas, nivel requerido, medios y cargos; cardinalidad según D08. |
| U12 | Perfil de titulados según empleadores: E13-E22 | Distribución por criterio, NS identificado, acuerdo sustantivo y resumen de puntajes; diez criterios únicos conforme a D07. |
| U13 | Textos: T07/T28/T30/E09_OTRO/E10/E11/E12 | Frecuencias de temas revisados, con libro de códigos, pregunta de origen y denominador propio. |
| U14 | Calidad y cobertura | Recuentos por estado, exclusión y versión, incluidos resultados SIN_DATOS o PENDIENTE_DEFINICION. |

T32-T34 y E00/E01 son contactos/identificación restringidos. No calcular medias, frecuencias de direcciones o rankings de nombres en la salida analítica ordinaria.

### 4.1. Indicadores concretos y sus bases

| Indicador | Numerador / denominador | Interpretación |
| --- | --- | --- |
| Formación complementaria declarada | T03 = true / N_validos(T03) | Formación concluida o en curso según el enunciado; no equivale a porcentaje con posgrado concluido. |
| Interés en posgrado | T08 = true / N_validos(T08) | Interés entre quienes respondieron válidamente esa pregunta. |
| Ocupación declarada actual | T14 = TRABAJA_ORGANIZACION o EMPRENDIMIENTO_TIEMPO_COMPLETO / N_validos(T14) | Ocupación declarada de la muestra. No llamarla tasa oficial de empleo o desempleo ni inserción acumulada. |
| Situación de no trabajo | T14 = NO_TRABAJA / N_validos(T14) | El instrumento no determina búsqueda activa o disponibilidad laboral; conservar ese límite. |
| Área preferida | Conteo de cada T09 / N_validos(T09) | Preferencias del subconjunto al que se aplicó la pregunta. |
| Financiamiento futuro principal | Conteo de cada T38 / N_validos(T38) | Solo si D04 define fuente principal única. Pesos monetarios no son conteos de personas. |
| Contratación reciente declarada | E05 = true / N_validos(E05) | Organizaciones/participantes según D16 y referencia de cinco años definida. |
| Acuerdo entre respuestas sustantivas | E13-E22 con TAC o PAC / N_sustantivos del criterio | Grado de acuerdo entre quienes dieron una valoración sustantiva. |
| Acuerdo sobre todas las respuestas válidas | TAC o PAC / N_validos del criterio | Incluye NS en el denominador; tiene nombre y significado distintos del indicador anterior. |
| Opción de pregunta múltiple | Participantes que seleccionan opción / N_validos de la pregunta | Porcentaje de participantes; puede sumar más de 100 % entre opciones. |

Para ocupación por cohorte, intersectar T14 válido con T35 válido y la agrupación aprobada. Mostrar n de cada cohorte. No interpretar el ejemplo del 70 % de preferencias de la guía como un umbral obligatorio de decisión académica.

## 5. Fórmulas y reglas univariadas

### 5.1. Frecuencias, porcentajes y moda

Para una variable categórica de selección única y sus n = N_validos:

$$
f_k = \#\{u : X_u = k\},\qquad
fr_k = \frac{f_k}{n},\qquad
porcentaje_k = 100\frac{f_k}{n}.
$$

Verificar que la suma de f_k sea n. Devolver todas las categorías con frecuencia máxima como modas; en caso de empate no elegir una arbitrariamente. Si n = 0, devolver lista de modas vacía y porcentajes NULL.

El porcentaje sobre N_base, si se solicita, se etiqueta como otro resultado; no reemplaza el porcentaje sobre N_validos. La suma de porcentajes mostrados puede diferir ligeramente de 100 % por redondeo, sin que se alteren los conteos.

En preguntas múltiples o temas revisados, distinguir:

$$
porcentaje\_participantes_k = 100\frac{f_k}{n},\qquad
porcentaje\_menciones_k = 100\frac{f_k}{\sum_j f_j}.
$$

Cada f_k cuenta participantes distintos por pregunta/tema, no repeticiones de palabras ni filas duplicadas. Para textos, n es el número de textos válidos y revisados de esa pregunta según el libro de códigos; informar los no codificados y no mezclar preguntas.

### 5.2. Variables numéricas

Para valores utilizables x_1,...,x_n, con la misma unidad:

$$
\bar{x} = \frac{1}{n}\sum_{i=1}^n x_i,
\qquad
s^2 = \frac{1}{n-1}\sum_{i=1}^n (x_i-\bar{x})^2,
\qquad
s = \sqrt{s^2}.
$$

La media usa n ≥ 1 y la varianza/desviación muestral n ≥ 2. La denominación «muestral» describe esta convención; no convierte la muestra en probabilística. Devolver n, media, mediana, desviación, mínimo, máximo, Q1, Q3 y rango intercuartílico.

Para cuantiles tipo 7, ordenar x_(1) ≤ ... ≤ x_(n), fijar h = 1 + (n-1)p, j = floor(h) y g = h-j. Para 1 ≤ j < n:

$$
Q(p) = (1-g)x_{(j)} + g x_{(j+1)}.
$$

En p = 1, Q = x_(n); con n = 1, todos los cuantiles son ese valor. Q1 = Q(0,25), mediana = Q(0,5), Q3 = Q(0,75), IQR = Q3-Q1. Esta definición evita diferencias por convenciones de cuartiles entre herramientas (RT3).

| Tamaño utilizable | Salida |
| --- | --- |
| n = 0 | Todas las medidas NULL; estado SIN_DATOS. |
| n = 1 | Media, mediana, extremos y cuantiles iguales al único valor; desviación/varianza muestral NULL con motivo MUESTRA_MENOR_2. |
| n ≥ 2 y valores constantes | Desviación cero válida; no confundir con ausencia de datos. |

La antigüedad derivada usa el año de referencia del conjunto y T35, no el año de ejecución. T12/T13 requieren unidades definidas. T21/T22 y E03 siguen siendo categorías: no calcularles una media usando códigos o extremos de intervalos.

### 5.3. Valoraciones ordinales y puntajes

La salida principal presenta frecuencias, porcentajes, modas y categorías centrales en su orden semántico. Para n impar, mostrar la categoría central; para n par, las dos categorías centrales, agrupadas si coinciden. No inventar una etiqueta intermedia promediando códigos.

Para satisfacer la solicitud de puntajes medios y dispersión de HU4, se propone la siguiente codificación de resumen. Es una convención analítica explícita; las etiquetas y escalas del formulario permanecen iguales.

| Escala | Codificación propuesta para resumen numérico |
| --- | --- |
| ACUERDO_4 | TDES = 1; DES = 2; AC = 3; TAC = 4. |
| SATISFACCION_3 | INSAT = 1; ALGO_SAT = 2; SAT = 3. |
| IMPORTANCIA_4 | NO_IMPORTA = 1; POCO_IMPORTA = 2; IMPORTA = 3; MUY_IMPORTA = 4. |
| EMPLEADORES | TDES = 1; PAC = 2; TAC = 3; NS sin puntaje. |
| Matrices T39/T40 | Escala por ítem conforme a D05; la sugerencia 1-5 aún requiere anclajes. |

Calcular media y desviación muestral de puntajes junto con la distribución, n de valoraciones puntuadas, rango de la escala y regla de NS. Etiquetar «media de puntajes codificados», sin presentarla como una magnitud física o demostración de distancias iguales entre categorías.

No promediar automáticamente escalas de tres, cuatro y cinco niveles, ni todos los criterios para crear un índice general. No calcular una brecha por resta de variables que no miden referentes compatibles. Los ítems, los índices compuestos y la fórmula de brecha permanecen sujetos a D05.

## 6. Análisis bivariado y matrices cruzadas

### 6.1. Registro inicial de cruces

| ID | Variables | Análisis y dependencia |
| --- | --- | --- |
| B01 | T38 financiamiento futuro × T37 nivel deseado | Cruce requerido por HU3. Contingencia y chi-cuadrado condicionado a categorías principales, D04 e independencia. |
| B02 | T06 financiamiento realizado × T04 nivel complementario | Cruce exploratorio distinto del anterior; personas con formación complementaria y pares válidos. No sustituye B01. |
| B03 | T14 situación laboral × T08 interés en posgrado | Frecuencias cruzadas y porcentajes por fila; chi-cuadrado si se cumplen condiciones. |
| B04 | E02 tipo público/privado × E06 expectativa de contratación | Exploración de la muestra empleadora; unidad y posible agrupación según D16. |
| B05 | E03 tamaño × E06 expectativa de contratación | Matriz descriptiva; contraste condicionado a frecuencias esperadas y unidad de análisis. |
| B06 | E02 tipo de organización × un criterio E13-E22 | Matriz por criterio; excluir NS del contraste de grado de acuerdo bajo la regla registrada, mostrando su conteo. Cada criterio contrastado pertenece a una familia declarada. |
| B07 | Antigüedad de egreso numérica × situación laboral o sector | Resúmenes numéricos por grupo, con tamaños, medias, medianas y dispersión. No convertir números en categorías improvisadas para aplicar chi-cuadrado. |
| B08 | T12 vida profesional × T13 tiempo desempleado | Exploración de pares numéricos con unidades y relaciones validadas. Preparar pares/resúmenes para visualización; no declarar causalidad ni una prueba no especificada. |

El registro es una propuesta de análisis. Un cruce pendiente puede conservar descripción parcial sin una prueba. No ejecutar automáticamente todos los pares posibles ni seleccionar solo los que den p pequeña.

### 6.2. Construcción de una contingencia

Para dos variables categóricas X e Y, cada unidad válida aporta una observación a una sola celda:

$$
O_{ij} = \#\{u : X_u=i,\;Y_u=j\},\qquad
R_i = \sum_j O_{ij},\qquad
C_j = \sum_i O_{ij},\qquad
N = \sum_i\sum_j O_{ij}.
$$

Comprobar N = N_pares_validos. Devolver las categorías y los tres tipos de porcentaje por separado:

$$
porcentaje\_fila_{ij}=100\frac{O_{ij}}{R_i},\quad
porcentaje\_columna_{ij}=100\frac{O_{ij}}{C_j},\quad
porcentaje\_total_{ij}=100\frac{O_{ij}}{N}.
$$

Si un margen es cero, sus porcentajes condicionados son NULL. La tabla descriptiva puede conservar categorías sin observaciones para reflejar el catálogo; la tabla activa del contraste se obtiene conforme a 7.2.

Los filtros y pares válidos forman parte del resultado. Comparar porcentajes de filas con distinto tamaño exige mostrar esos tamaños. No emparejar un titulado y un empleador por sector o contacto suponiendo una relación individual no capturada.

### 6.3. Selección múltiple, pesos y números

La contingencia estándar requiere una categoría por variable y unidad. Una selección múltiple completa no se introduce como varias personas, y pesos de financiamiento no se interpretan como frecuencias observadas enteras.

Si se decide estudiar una opción múltiple, usar un indicador seleccionado/no seleccionado por participante con estado válido. Cada opción constituye un contraste propio y entra en la familia de comparaciones. No concatenar todas las opciones en una tabla de independencia como si fueran observaciones independientes.

Para una variable numérica frente a un grupo, presentar resúmenes por grupo. Para dos numéricas, conservar pares válidos para exploración. Tests adicionales, ponderaciones muestrales, modelos o correlaciones inferenciales requerirán una extensión explícita del protocolo.

## 7. Chi-cuadrado de independencia

### 7.1. Hipótesis y cálculo

H0: las dos variables categóricas son independientes bajo el modelo del contraste. H1: existe asociación entre ellas.

Con r filas y c columnas activas y márgenes positivos:

$$
E_{ij} = \frac{R_i C_j}{N},\qquad
\chi^2 = \sum_{i=1}^{r}\sum_{j=1}^{c}\frac{(O_{ij}-E_{ij})^2}{E_{ij}},\qquad
gl=(r-1)(c-1).
$$

El valor p corresponde a la cola superior de la distribución chi-cuadrado con esos grados de libertad. Usar una función de supervivencia o el resultado de la biblioteca; no restar una CDF redondeada de uno. Las definiciones de Pearson, esperadas y grados de libertad se apoyan en RT1.

### 7.2. Comprobaciones previas

Aplicar esta secuencia antes de solicitar un valor p:

1. **Definiciones:** categorías, versiones, filtros, unidades de análisis y reglas de selección están resueltos para el contraste.
2. **Observaciones:** conteos enteros no negativos, con una contribución por unidad a la tabla y sin reintentos duplicados.
3. **Independencia:** el diseño o la regla de unidad documenta cómo se tratan participaciones repetidas, organizaciones con varios representantes y agrupaciones. No inferir independencia por IDs distintos.
4. **Márgenes:** si N = 0, devolver SIN_DATOS. Retirar de la tabla de cálculo filas o columnas con margen cero, conservando su lista y la tabla descriptiva original.
5. **Variación:** después de esa retirada debe haber al menos dos filas y dos columnas. De lo contrario, SIN_VARIACION.
6. **Estructura:** las combinaciones deben corresponder al modelo de independencia considerado. Un cero observado es una celda sin casos; una combinación imposible por diseño exige otra especificación. No tratar ceros estructurales como simples ceros de muestreo.
7. **Esperadas:** calcular E sin redondear y aplicar la política conservadora `TODAS_ESPERADAS_GE_5`.

La política GE_5 es una elección operativa conservadora del proyecto, no un umbral universal que el PDF imponga. RT1 explica que la aproximación asintótica puede ser inadecuada con esperadas pequeñas. Mostrar mínimo de esperadas y cuántas celdas quedan por debajo de 5.

Si alguna esperada activa es menor que 5, conservar observadas, esperadas, márgenes y porcentajes, y devolver FRECUENCIAS_INSUFICIENTES con p NULL. En este protocolo tampoco se publican chi-cuadrado o V como resultado de un contraste ejecutado. No fabricar p = 0 o p = 1.

La agrupación de categorías requiere fundamento semántico y una regla previa; no combinar categorías después de mirar p para obtener significación. Fisher u otros procedimientos para tablas pequeñas son posibles extensiones, no sustituciones automáticas ya implementadas.

### 7.3. Estados del contraste

| Estado | Situación | Salida inferencial |
| --- | --- | --- |
| CALCULADO | Se cumplen las condiciones y el cálculo finaliza. | Chi-cuadrado, gl, p, V y decisión disponibles. |
| SIN_DATOS | No hay pares válidos. | Medidas inferenciales NULL. |
| SIN_VARIACION | Menos de dos categorías activas en alguna variable. | Medidas inferenciales NULL; tabla descriptiva disponible. |
| PENDIENTE_DEFINICION | Falta dominio, cardinalidad, compatibilidad de versiones o regla relevante. | Sin contraste hasta resolver el significado. |
| INDEPENDENCIA_NO_DOCUMENTADA | El tratamiento de unidades no sustenta el supuesto requerido. | Sin contraste; descripción con alcance indicado. |
| FRECUENCIAS_INSUFICIENTES | Alguna esperada activa < 5 en la política V1. | Diagnóstico y tablas, sin p ni decisión H0. |
| ESTRUCTURA_INCOMPATIBLE | Ceros estructurales u otra estructura no contemplada. | Requiere especificación apropiada. |
| ENTRADA_INCOMPATIBLE | Pesos, porcentajes, datos fraccionarios o formato inválido usados como conteos. | Rechazar el contraste y explicar el motivo. |
| ERROR_CALCULO | Falla numérica o de biblioteca pese a la preparación. | Registrar error; no presentar resultado inferencial válido. |

Si el contraste no se ejecuta, `rechaza_h0` será NULL, no false. El motivo forma parte del resultado destinado al dashboard y a HU5.

### 7.4. Asociación e interpretación

Como complemento propuesto, para un contraste CALCULADO:

$$
V = \sqrt{\frac{\chi^2}{N\min(r-1,c-1)}}.
$$

Usar el mismo N y tabla activa del contraste. Etiquetar V de Cramér sin corrección de sesgo; su valor describe magnitud de asociación y no dirección o causalidad (RT6). No aplicar automáticamente etiquetas «débil/fuerte» con umbrales sin justificación contextual.

Comparar p, o p ajustada cuando corresponda, con alpha sin redondear. Rechazar H0 aporta evidencia de asociación bajo los supuestos; no demuestra causalidad. No rechazar H0 indica evidencia insuficiente para rechazarla, no prueba independencia. El valor p no es la probabilidad de que H0 sea verdadera ni una medida de magnitud o importancia práctica (RT7).

Para B01, una asociación entre financiamiento y posgrado no demuestra por sí sola vulnerabilidad económica. Las propuestas institucionales deben combinar distribuciones, magnitud, contexto y limitaciones de la muestra.

## 8. Implementación estadística prevista en Java

### 8.1. Biblioteca y controles del servicio

Usar Apache Commons Statistics como base propuesta para cálculo descriptivo e inferencia. Verificar y fijar versión compatible al implementar; no mezclar las APIs de Commons Statistics y Commons Math por coincidencia del nombre `ChiSquareTest`.

RT2 documenta la sobrecarga `test(long[][] counts)` para independencia. El siguiente fragmento ilustra su uso **después** de las comprobaciones de la sección 7; no constituye la implementación del servicio:

```java
SignificanceResult resultado = ChiSquareTest.withDefaults().test(conteos);
double chiCuadrado = resultado.getStatistic();
double p = resultado.getPValue();
```

`conteos` es una matriz rectangular `long[][]` de frecuencias absolutas, con filas/columnas identificadas. No usar la sobrecarga de bondad de ajuste de una lista de categorías para resolver un cruce de dos variables.

La biblioteca valida la forma y algunos valores de la tabla, pero no verifica el diseño de muestreo, la independencia de participantes o la política GE_5. Esos controles pertenecen al servicio.

RT5 documenta `StandardDeviation.of(valores).setBiased(false).getAsDouble()` como base para la desviación muestral. También indica que con un único valor finito puede devolver cero: el servicio debe aplicar la convención de este protocolo y devolver NULL para n < 2.

### 8.2. Reglas numéricas y organización

- Obtener del pipeline códigos, estados y unidades antes de calcular. Evitar interpretar directamente texto recibido del formulario.
- Usar conteos enteros exactos; detectar desbordamientos si se suman o convierten tamaños.
- Convertir valores numéricos a la representación de cálculo de forma explícita y comprobar que sean finitos.
- Usar un cálculo de dispersión estable; no basarlo en restas de números grandes casi iguales sin control numérico.
- Mantener objetos/servicios de cálculo sin compartir acumuladores mutables entre solicitudes simultáneas.
- Mantener precisión completa de p y E; nunca decidir con el valor formateado para pantalla.
- Serializar resultados indefinidos como NULL con estado/motivo; no emitir NaN o infinito en JSON.
- Separar obtención de datos, validación de condiciones, cálculo y preparación de resultados. La interfaz y las plantillas no recalculan el protocolo.

## 9. Comparaciones múltiples

Si se evalúa un solo contraste planificado, no hay ajuste por varias pruebas en esa familia. Si se solicitan varios, declarar sus IDs y filtros antes de revisar p y conservar el tamaño M de la familia.

Propuesta: ajuste de Holm, respaldado por RT4. Ordenar las k p calculadas como p_(1) ≤ ... ≤ p_(k), usando M contrastes declarados:

$$
p^{Holm}_{(i)}=\min\left(1,\max_{1\le j\le i}\big[(M-j+1)p_{(j)}\big]\right).
$$

Restituir cada valor a su contraste original. Las pruebas no ejecutables conservan p NULL y motivo; mantenerlas en el inventario M en vez de reducir la familia después de ver resultados. RT4 permite usar un número de comparaciones mayor que el de valores observados en este método.

Mostrar p original, p ajustada, familia, M, número ejecutado y decisión según el ajuste cuando aplique. El ajuste no resuelve defectos del diseño muestral. Cruces añadidos tras observar resultados se etiquetan exploratorios; múltiples cambios de filtros no se presentan como un solo contraste confirmatorio.

## 10. Contrato de salida para visualización y HU5

| Resultado | Campos mínimos |
| --- | --- |
| Frecuencias | Variable, catálogo/escala, categoría, conteo, denominador, porcentaje, modas y cobertura/calidad. |
| Resumen numérico | Variable, unidad, n, media, mediana, desviación muestral, extremos, Q1/Q3/IQR, convención y estados de medidas indefinidas. |
| Valoración | Distribución completa, NS cuando existe, n_sustantivos, categorías centrales y resumen de puntajes con mapa/rango de escala. |
| Contingencia | IDs de variables, categorías, O, márgenes, N_pares_validos, porcentajes por fila/columna/total y exclusiones. |
| Contraste | Estado/motivo, tabla activa, categorías retiradas, E, diagnóstico GE_5, chi-cuadrado, gl, p, alpha, V, familia/ajuste y decisión. |
| Trazabilidad | Estudio, conjunto y huella, versiones, protocolo, filtros, unidades de análisis, fecha de referencia y momento de cálculo. |

Presentar porcentajes, conteos y unidades de forma inequívoca. Como base de formato, usar hasta dos decimales para porcentajes y medias/desviaciones, y tres o cuatro para p según la salida. Si p es positiva y menor que el umbral mostrado, usar «p < 0,001» o notación científica; conservar el valor interno. No mostrar «p = 0» por simple redondeo.

Los resultados no calculados deben mostrar su estado, conservando descripción disponible. La visualización no representa una ausencia como una barra de cero, ni una prueba no ejecutada como «sin asociación».

HU5 generará reportes desde un conjunto y protocolo identificados. Reportes, dashboard y exportaciones deberán coincidir en cifras, filtros y denominadores. Las propuestas estratégicas se redactan con evidencia y revisión, sin convertir automáticamente p < alpha en una acción institucional.

## 11. Ejemplos sintéticos comprobados

Los siguientes números se calcularon como referencia matemática con Python/NumPy/SciPy. Esa comprobación verifica los ejemplos; no demuestra que exista o funcione una implementación Java del proyecto. Los estados de no ejecución corresponden al protocolo propuesto, incluso si una biblioteca permite calcular un p sin validar sus supuestos.

### 11.1. Porcentajes y No sabe

| Escenario sintético | Resultado esperado |
| --- | --- |
| T14: 10 envíos base; 5 trabajan en organización, 1 emprendimiento, 2 no trabajan, 1 sin respuesta y 1 de versión sin T14 | N_validos = 8; ocupación declarada = 6/8 = 75 %. Mostrar base 10 y excluidos. |
| Un criterio de empleadores: TAC = 6, PAC = 2, TDES = 1, NS = 2 y vacío = 1 | N_validos = 11, N_sustantivos = 9; acuerdo sustantivo = 8/9 = 88,888889 %; acuerdo sobre válidos = 8/11 = 72,727273 %. |
| T25: cuatro participantes; selecciones A/B, A, B/C, A/C | A = 3, B = 2, C = 2; porcentajes de participantes 75/50/50 %, suma 175 %; porcentajes de menciones 42,857143/28,571429/28,571429 %, suma 100 %. |

En el último ejemplo, A/B/C representan tres opciones existentes de T25 con un mapeo didáctico, no opciones nuevas del instrumento.

### 11.2. Medidas numéricas

Para x = [2, 4, 4, 4, 5, 5, 7, 9]:

| Medida | Referencia |
| --- | --- |
| n | 8 |
| Media | 5 |
| Mediana | 4,5 |
| Desviación estándar muestral | 2,138089935299395 |
| Mínimo / máximo | 2 / 9 |
| Q1 / Q3, tipo 7 | 4 / 5,5 |
| IQR | 1,5 |

Para [4], la media/mediana/cuantiles son 4 y la desviación muestral es NULL. Para [4,4,4], la desviación es cero. Para una lista vacía, las medidas son NULL.

### 11.3. Contingencia válida 2 × 2

Supónganse dos categorías didácticas de financiamiento y dos niveles de posgrado. Son datos sintéticos; no fijan el catálogo pendiente de T38/T37.

| Financiamiento / posgrado | Maestría | Especialidad | Total |
| --- | --- | --- | --- |
| Recursos propios | 20 | 10 | 30 |
| Beca | 10 | 20 | 30 |
| Total | 30 | 30 | 60 |

Esperadas: 15 en cada celda. Pearson sin corrección de continuidad:

- Chi-cuadrado = 6,666666666666667.
- gl = 1; N = 60.
- p = 0,009823274507519235.
- V de Cramér = 0,3333333333333333.

Con independencia documentada, esta tabla supera GE_5. En una familia de un solo contraste y alpha = 0,05, se rechaza H0; la conclusión se limita a asociación bajo los supuestos. En otra familia debe aplicarse el ajuste correspondiente.

### 11.4. Referencias adicionales

| Caso | Entrada | Referencia esperada |
| --- | --- | --- |
| Tabla 2 × 3 válida | [[10,20,30],[15,15,30]] | N = 120; E = [[12,5;17,5;30],[12,5;17,5;30]]; chi-cuadrado = 1,7142857142857144; gl = 2; p = 0,42437284567695; V = 0,11952286093343938. |
| Observadas coinciden con esperadas | [[10,10],[10,10]] | Chi-cuadrado = 0, gl = 1, p = 1, V = 0; CALCULADO. |
| Esperadas pequeñas | [[1,2],[2,1]] | N = 6; E = 1,5 en cada celda; FRECUENCIAS_INSUFICIENTES; p, chi-cuadrado publicado, V y decisión NULL. |
| Columna con margen cero | [[10,10,0],[10,10,0]] | Descripción conserva tercera categoría; contraste usa las primeras dos, registra retirada, gl = 1 y p = 1. |
| Una sola categoría activa | Una columna con todos los pares | SIN_VARIACION; no asignar p = 1 como si se hubiera contrastado. |
| Holm, familia M = 3 | p originales [0,01;0,04;0,03] | Ajustadas en el orden original [0,03;0,06;0,06]. |
| Decisión y formato | p interna = 0,0496 y alpha = 0,05 | Comparar internamente; un formato mostrado como 0,050 no cambia la decisión. |

No trasladar la etiqueta «Recursos propios» del ejemplo a pesos o probabilidades. Los conteos de las matrices de referencia son personas/unidades independientes supuestas para el ejemplo.

## 12. Criterios de aceptación estadística

Estos casos se implementarán y ejecutarán al desarrollar el motor. No son comprobaciones de una aplicación ya construida.

| Caso | Situación | Evidencia esperada |
| --- | --- | --- |
| CEA01 | Frecuencia de una selección única | Suma de conteos = N_validos; denominador y cobertura explícitos. |
| CEA02 | Variable sin valores utilizables | SIN_DATOS; medidas y porcentajes NULL, no cero. |
| CEA03 | Ejemplo de ocupación de 11.1 | Se obtiene 75 % sobre 8 válidos; no 60 % presentado como el mismo indicador. |
| CEA04 | Respuestas NS de un criterio | Distribución conserva NS; puntuación lo excluye y muestra ambos denominadores. |
| CEA05 | Selección múltiple de 11.1 | Conteos 3/2/2 sobre cuatro participantes; 175 % de suma es válido para porcentajes de participantes. |
| CEA06 | Vector numérico de 11.2 | Coinciden media, mediana, desviación muestral y cuantiles del tipo declarado. |
| CEA07 | Un único valor o valores constantes | n = 1 produce desviación NULL; n ≥ 2 constante produce cero. |
| CEA08 | Variable nominal o categoría abierta | No se calcula media de sus códigos ni se convierte MAS_TRES en cuatro. |
| CEA09 | Valoración ordinal | Etiquetas y escala permanecen intactas; resumen numérico declara mapa de puntajes y n puntuado. |
| CEA10 | Contingencia completa | Suma de celdas = N_pares_validos; porcentajes por fila/columna/total usan sus bases correctas. |
| CEA11 | Cruce futuro frente a pasado | B01 usa T38/T37 y B02 T06/T04, con nombres y población propios. |
| CEA12 | Tabla válida 2 × 2 de referencia | Pearson, gl, p y V coinciden dentro de la tolerancia numérica; no aparece una corrección de continuidad no declarada. |
| CEA13 | Tabla válida 2 × 3 | gl = 2 y medidas coinciden con 11.4; no se aplana como una bondad de ajuste. |
| CEA14 | Márgenes cero o una categoría activa | Se registra la reducción de tabla; SIN_VARIACION cuando corresponda. |
| CEA15 | Alguna esperada activa < 5 | FRECUENCIAS_INSUFICIENTES, sin p ni decisión, con diagnóstico descriptivo. |
| CEA16 | Independencia/unidad sin documentación | INDEPENDENCIA_NO_DOCUMENTADA; no basta con IDs distintos. |
| CEA17 | Pesos, porcentajes o filas repetidas como personas | Se rechaza la entrada incompatible o se exige la preparación apropiada. |
| CEA18 | Varias pruebas planificadas | Familia y M registrados; p ajustadas reproducen el ejemplo de Holm. |
| CEA19 | p cercana a alpha | Decisión con precisión completa, no con texto redondeado. |
| CEA20 | Regeneración con mismo conjunto y protocolo | Mismos resultados analíticos; fecha técnica distinta no altera cálculos. |
| CEA21 | Salida para gráfico o reporte | Coincide con los resultados del backend y conserva filtros, unidades, denominadores y estado del contraste. |
| CEA22 | Redacción interpretativa | No se atribuye causalidad, representatividad no documentada o certeza de independencia a partir del p. |

Para cálculos de referencia finitos, usar como base de comparación tolerancia absoluta 1e-10 o relativa 1e-10, eligiendo el mayor margen. Los conteos, estados y grados de libertad se comparan exactamente. No usar redondeo de presentación como tolerancia de cálculo.

## 13. Decisiones pendientes que se conservan y se añaden

D01-D20 permanecen vinculadas a sus campos. En particular: D02/D17 para tiempos, D04 para el cruce HU3, D05 para competencias, D07 para criterios duplicados, D08 para cardinalidad, D16 para unidad empleadora y D19 para comparabilidad.

| ID | Definición pendiente | Efecto |
| --- | --- | --- |
| D21 | Diseño muestral y población a la que se pretende extender los resultados; justificación del alcance inferencial. | Interpretación de pruebas y eventual estimación poblacional. |
| D22 | Agrupaciones de cohortes y umbrales de segmentación profesional, si se desean además de año de egreso. | Indicadores por cohorte y categorías junior/consolidado. |
| D23 | Lista de contrastes que se ejecutarán, familias y carácter exploratorio o confirmatorio del análisis. | Ajuste múltiple y transparencia de la selección. La regla operativa de Holm ya está propuesta en este documento. |

Alpha, cuartiles y otras convenciones técnicas de la versión V1 tienen una base de trabajo aquí declarada. Cambiarlas exige registrar otra versión del protocolo; no hace falta solicitar una decisión del usuario para cada nombre técnico o formato de implementación.

## 14. Finalización y siguiente parte

- [ ] La guía está cubierta por indicadores univariados, contingencias y chi-cuadrado donde corresponde.
- [ ] Cada resultado declara población aplicable, estados de calidad y denominador.
- [ ] Las variables numéricas, ordinales, nominales y múltiples reciben tratamientos distintos y coherentes.
- [ ] Se mantiene la separación entre formación/financiamiento pasado y futuro.
- [ ] La prueba verifica márgenes, variación, independencia, estructura y esperadas antes de calcular p.
- [ ] Los estados de no ejecución no se confunden con no rechazo de H0.
- [ ] Las convenciones numéricas, la corrección de continuidad y la multiplicidad están documentadas.
- [ ] Los ejemplos sintéticos aportan valores independientes de referencia para Java.
- [ ] La interpretación declara límites de muestra, asociación y magnitud.
- [ ] HU5 consume resultados de un conjunto y protocolo identificados, sin recalcular cifras distintas en la interfaz.
- [ ] La aplicación y sus pruebas funcionales siguen pendientes; la comprobación matemática de ejemplos no se presenta como validación del backend.

**Siguiente parte:** `05_VISUALIZACION_COMPUTACIONAL.md`, para definir el dashboard, gráficos de distribuciones, matrices y mapas de calor, radar, filtros y presentación de estados. La automatización de reportes y exportaciones de HU5 se desarrollará después en su documento específico.

## 15. Referencias técnicas consultadas

Estas referencias fundamentan fórmulas o APIs; las reglas operativas propias del proyecto están identificadas como propuestas. Consulta: 13 de octubre de 2026.

| ID | Fuente y uso |
| --- | --- |
| RT1 | [NIST — Chi-square independence test](https://itl.nist.gov/div898/software/dataplot/refman1/auxillar/chistest.htm). Pearson, esperadas, grados de libertad y límites de la aproximación. |
| RT2 | [Apache Commons Statistics — ChiSquareTest](https://commons.apache.org/proper/commons-statistics/commons-statistics-docs/apidocs/org/apache/commons/statistics/inference/ChiSquareTest.html) y [SignificanceResult](https://commons.apache.org/proper/commons-statistics/commons-statistics-inference/javadocs/api-1.3/org/apache/commons/statistics/inference/SignificanceResult.html). API de independencia y resultados. |
| RT3 | [R — Sample Quantiles](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/quantile.html). Convención tipo 7 de cuantiles. |
| RT4 | [R — Adjust P-values for Multiple Comparisons](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/p.adjust.html). Ajuste de Holm y número de comparaciones. |
| RT5 | [Apache Commons Statistics — StandardDeviation](https://commons.apache.org/proper/commons-statistics/commons-statistics-docs/apidocs/org/apache/commons/statistics/descriptive/StandardDeviation.html). Desviación muestral y tratamiento de la API para un valor. |
| RT6 | [SciPy — Contingency association](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.contingency.association.html). V de Cramér como medida de asociación y referencia de contraste numérico. |
| RT7 | [American Statistical Association — Statement on Statistical Significance and P-Values](https://www.amstat.org/asa/files/pdfs/P-ValueStatement.pdf). Interpretación de valores p, significación y contexto. |

## Nota de integración en el repositorio

Fecha: 2026-10-13. El contenido anterior conserva la referencia recibida. El usuario autorizó continuar el desarrollo de la fase 4 («ok continuemos»); esa autorización prevalece sobre las instrucciones de este archivo que limitaban la tarea a documentación o posponían el motor. El contrato implementado y su evidencia se registran en [ESTADISTICA.md](ESTADISTICA.md) y [ACEPTACION_ESTADISTICA.md](ACEPTACION_ESTADISTICA.md).

Los ejemplos numéricos anteriores son sintéticos y no describen trabajo de campo. `PROTOCOLO_ESTADISTICO_V1` sigue siendo un protocolo de borrador: la implementación técnica no otorga aprobación metodológica o institucional ni resuelve independencia, elegibilidad o diseño muestral.

Las decisiones aprobadas por el usuario en fase 2 se conservan: las competencias ya tienen el referente laboral y la escala de adecuación de cinco categorías con `NO_PUEDE_VALORAR` nominal; los tiempos actuales se expresan en meses y los diez criterios de empleadores aparecen una sola vez. Las alusiones previas a estos campos como pendientes corresponden al documento de referencia, no a su omisión en la aplicación. La antigüedad derivada, las cohortes profesionales y el alcance inferencial continúan pendientes.

El inventario de finalización de la sección 14 es el de la referencia original. El estado efectivo de cada criterio CEA01–CEA22 y los límites de visualización/reportes se mantienen en la matriz de aceptación; no se toma ese inventario como una certificación del backend.
