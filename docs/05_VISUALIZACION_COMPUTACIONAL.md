# Paso 05: visualización computacional y dashboard analítico

**Uso:** entregar este archivo a Codex después de `04_ESTADISTICA_UNIVARIADA_BIVARIADA_CHI_CUADRADO.md`. Conservarlo en `docs/05_VISUALIZACION_COMPUTACIONAL.md`.

**Estado:** especificación de las vistas y sus comportamientos. Las elecciones de presentación son propuestas de implementación; no modifican cuestionarios, cálculos, hipótesis o reglas pendientes de los pasos anteriores.

**Resultado de esta etapa:** estructura del dashboard, catálogo de gráficos, mapas de calor, radar, filtros, estados, contratos de datos y criterios de aceptación. El dashboard se construirá en una etapa de implementación posterior.

## 1. Instrucción para Codex

Revisa los pasos 01-04 y las instrucciones del repositorio. Incorpora este documento y actualiza sus referencias si trabajas efectivamente en el repositorio, conservando el trabajo existente.

En este paso documenta las vistas y su conexión con el backend. No generes todavía la interfaz, endpoints, servicios, archivos de gráficos, una aplicación frontend separada o configuraciones de alojamiento. Los ejemplos son sintéticos y no se presentan como resultados reales UMSS.

Mantén el monolito Java + Spring Boot y la interfaz prevista en Thymeleaf, HTML, CSS y JavaScript. Apache ECharts es la biblioteca propuesta para representar gráficos en el navegador. Su versión y forma de incorporar recursos se fijarán al implementar; esto no introduce un segundo backend ni una obligación de usar Node.js.

Los cálculos, porcentajes, denominadores y decisiones estadísticas llegan del backend. El navegador adapta formato, orden visual y coordenadas de dibujo, y gestiona interacción y tamaño. No reconstruye el protocolo estadístico a partir de respuestas crudas.

## 2. Fuente y conexión con las etapas anteriores

Fuente: **Guía Metodológica y Técnica Integral: Análisis de Mercado y Herramienta de Recolección de Datos (Enfoque ARCUSUR)**, contenida en `JOSE ARMANDO MAREÑO MAMANI - EstudioMercadoHerramientaColeccionDatos.pdf`, de 20 páginas.

| Requisito de la guía | Correspondencia visual |
| --- | --- |
| Visualización computacional del marco metodológico, página 1 | Gráficos de distribuciones, valoraciones y relaciones, con tablas y filtros. |
| HU1, página 3 | Perfil, trayectoria temporal y ocupación declarada por cohorte. |
| HU2, páginas 3-4 | Formación continua, áreas de interés e instituciones preferidas. |
| HU3, página 4 | Matriz financiamiento futuro × nivel deseado, mapa de calor y resultado de chi-cuadrado cuando sea aplicable. |
| HU4, página 4 | Distribuciones de valoraciones y competencias definidas, sin inventar índices o brechas. |
| HU5, página 5 | Dashboard, matrices cruzadas, gráficos interactivos, mapas de calor y radar preparados para reportes trazables. |

El dashboard recibe conjuntos y resultados de los pasos 03 y 04. La automatización de reportes y las exportaciones CSV/Excel tendrán su siguiente documento. Esta especificación define qué cifras, etiquetas y contexto deben conservar al consumir los mismos resultados.

La sección analiza datos observados. Los resultados de simulación necesitarán una sección y una identificación propias cuando se defina su modelo.

## 3. Estructura del dashboard

El acceso corresponde al administrador o comité autorizado del estudio. Los participantes de las encuestas siguen sus recorridos específicos; no necesitan acceso al dashboard para responder.

### 3.1. Vistas principales

| ID | Nombre visible | Contenido y correspondencia |
| --- | --- | --- |
| V01 | Resumen del estudio | Datos seleccionados, respuestas por cuestionario, cobertura y tarjetas de indicadores disponibles. U02/U03/U04/U11/U14. |
| V02 | Titulados: perfil y trayectoria | Edad/grupo, género, egreso, antigüedad, situación laboral, sector, trayectoria y emprendimiento. U01/U02/U05/U06; B07/B08. |
| V03 | Formación continua | Formación realizada/en curso e interés futuro, áreas, instituciones, niveles y financiamiento. U03/U04. |
| V04 | Empleadores y demanda | Tipo, tamaño, sector, contratación, expectativas, formación demandada, medios, cargos y temas revisados. U10/U11/U13. |
| V05 | Valoración de la formación | Valoraciones de titulados y diez criterios de empleadores; radar y tablas. T39/T40 solo cuando existan ítems aprobados. U07/U08/U12. |
| V06 | Cruces y contrastes | Selector de B01-B08, matrices, mapas de calor, resúmenes por grupo o pares numéricos y resultado del contraste disponible. |
| V07 | Calidad y cobertura | Faltantes, no aplicables, variables ausentes de versión, invalidaciones y exclusiones con ámbito claro. U14. |

No mostrar una métrica pendiente como si valiera cero. Las vistas con parte de su contenido definido pueden presentar esos resultados y explicar qué indicadores todavía no tienen una definición utilizable.

### 3.2. Organización de la pantalla

| Zona | Contenido |
| --- | --- |
| Encabezado | «Estudio de mercado de Ingeniería en Sistemas UMSS», campaña, sección activa y acceso a la sesión administrativa. |
| Contexto de consulta | Período de respuestas, tipo de cuestionario, filtros aplicados y actualización de los resultados. |
| Indicadores | Tarjetas con valor, nombre preciso, numerador/denominador y estado cuando corresponda. |
| Gráficos | Título, unidades, leyenda, base muestral, controles y acceso a tabla de datos. |
| Detalle | Tabla, definiciones breves, calidad y explicación del cálculo. |
| Trazabilidad ampliada | Referencia del conjunto/protocolo y versiones usadas, dentro de «Detalle del cálculo». |

La vista ordinaria usa etiquetas comprensibles; códigos de variables, huellas y detalles de ejecución quedan disponibles en el detalle cuando ayuden a revisar el resultado. El menú de reportes se incorporará junto con su funcionalidad, conforme al paso 06.

En escritorio, usar navegación lateral o superior y como máximo dos gráficos principales por fila. En teléfono, navegación compacta, filtros plegables y gráficos apilados. Una matriz amplia puede desplazarse horizontalmente dentro de su contenedor, conservando encabezados y una tabla accesible.

## 4. Contexto de consulta y filtros

### 4.1. Filtros propuestos

| Filtro | Alcance y regla |
| --- | --- |
| Campaña/estudio | Selecciona el conjunto institucional de trabajo. |
| Tipo de cuestionario | TITULADOS o EMPLEADORES para vistas específicas; el resumen mantiene ambas bases separadas. |
| Período de envío | Fecha de recepción de respuestas, distinta del año de egreso o de la fecha de referencia del estudio. |
| Versiones comparables | Solo versiones con correspondencia semántica aprobada para el resultado. |
| Año/cohorte de egreso | T35 y agrupaciones conforme a D02/D22. |
| Situación laboral/sector de titulados | T14/T36, con catálogos y aplicabilidad definidos. |
| Formación realizada o interés futuro | T03/T08, preservando bases condicionales de cada pregunta. |
| Tipo/tamaño/sector de organización | E02/E03/E04, con unidad de análisis conforme a D16. |
| Variable, cruce o criterio | Selector de un resultado definido, no un campo libre para consultar cualquier columna. |

Los contactos T32-T34/E00 y el nombre E01 no son filtros ordinarios de exploración analítica. Las funciones de consulta de contactos pertenecen a administración restringida.

El origen REAL/SINTETICO se fija según el modo de trabajo. Si se habilita una demostración, presentar «Demostración: datos sintéticos» de forma persistente y mantenerla separada de los datos reales.

### 4.2. Aplicación de filtros y coherencia

1. Editar filtros prepara una selección; «Aplicar filtros» solicita resultados nuevos. Mostrar cuáles siguen sin aplicar.
2. El backend valida valores y alcance, identifica el conjunto/protocolo y devuelve el contexto efectivo de cada resultado.
3. Mientras carga la selección nueva, usar un estado de actualización o placeholders que no presenten cifras antiguas bajo etiquetas nuevas.
4. Si una respuesta anterior llega después de la consulta actual, descartarla. Usar un identificador de consulta/selección para evitar sobrescrituras fuera de orden.
5. Los componentes visibles comparten el contexto aplicado. Una tarjeta o gráfico pendiente conserva un estado explícito; no mezcla otro conjunto sin indicarlo.
6. «Restablecer filtros» recupera la selección base documentada, no cambia la campaña sin advertirlo.

Ocultar una serie en la leyenda cambia la presentación, no los datos, denominadores o pruebas. Indicar series ocultas y mantener la tabla completa. Un cambio de conteos a porcentajes usa la salida correspondiente del mismo resultado.

Cuando se compare un grupo A con B, mostrar sus definiciones y tamaños separados. Los cambios de filtros y contrastes conservan el carácter exploratorio y las familias definidas en D23; no eliminan la necesidad de ajuste múltiple.

## 5. Catálogo de componentes visuales

| ID | Componente | Uso / fuente | Regla principal |
| --- | --- | --- | --- |
| G01 | Tarjetas de indicadores | V01 y resultados U02/U03/U04/U11/U14 | Valor y nombre preciso con n/base; no confundir respuestas empleadoras con empresas distintas. |
| G02 | Barras de categorías | U01-U06/U09-U11, según variable | Conteo o porcentaje válido; eje de barras empieza en cero; orden semántico para categorías ordinales. |
| G03 | Barras por cohorte o grupo | U02 y descripciones de cruces categóricos | Año/cohorte definido, N_pares_validos y tamaños por grupo visibles. |
| G04 | Barras de selección múltiple | U08 y E09/E08 si corresponde | Porcentaje de participantes; la suma puede superar 100 %. No convertirlo en partes de un único total. |
| G05 | Distribución de valoraciones | U07/U12 | Categorías originales; base sustantiva y NS tratados según sección 7. |
| G06 | Mapa de calor con tabla | B01-B06 y HU5 | Observadas o porcentajes claramente identificados; índices de filas/columnas y ceros coherentes. |
| G07 | Resumen numérico y caja de cinco números | Antigüedad/T12/T13 y B07 | Cuartiles y extremos del backend; bigotes mínimo/máximo declarados; n pequeño con tratamiento propio. |
| G08 | Radar de criterios | E13-E22 y HU5 | Porcentaje de acuerdo sustantivo 0-100; paneles y bases definidos en sección 8. |
| G09 | Dispersión de pares | B08 | X/Y numéricos con unidades compatibles; sin identidades en etiquetas; no inferir causalidad. |
| G10 | Barras de temas revisados | U13 | Libro de códigos y pregunta fuente; contar participantes por tema, sin multiplicarlos por menciones repetidas. |
| G11 | Cobertura/calidad | U14 y V07 | Estados de respuesta y exclusiones diferenciados; evitar sumar incidencias solapadas como categorías disjuntas. |
| G12 | Tabla y resumen del contraste | V06, B01-B06 | Estado, diagnóstico, chi-cuadrado, gl, p/ajuste y V solo según disponibilidad del paso 04. |

Cada gráfico tendrá una tabla equivalente. Evitar efectos 3D, áreas decorativas que cambien la lectura y gráficos circulares de selecciones múltiples. En esta base se prefieren barras para comparar categorías; la prioridad es identificar cifras y bases.

### 5.1. Orden, ejes y etiquetas

- Nominales: orden del catálogo o ranking descendente solicitado, con desempate estable. La selección de orden no cambia conteos ni porcentajes.
- Ordinales: preservar el orden semántico, no reordenar niveles por frecuencia.
- Años/cohortes: orden temporal aprobado. Diferenciar egreso de fecha de recolección.
- Comparaciones: mismas categorías, unidades y límites de eje cuando deban compararse magnitudes.
- Porcentajes: rango habitual 0-100 por opción/celda; una suma superior a 100 % entre opciones múltiples no exige extender el eje individual más allá de 100.
- Etiquetas largas: abreviación comprensible en el gráfico y texto completo en tooltip, tabla y descripción accesible.
- Valores cero: posición cero válida, con valor visible en tabla/tooltip; no convertir en vacío por una evaluación booleana.

G07 usa `[minimo, Q1, mediana, Q3, maximo]` recibido del backend. Mostrar «Bigotes: mínimo y máximo». No interpretar esos bigotes como límites de 1,5 IQR ni etiquetar puntos como atípicos sin una regla específica. Con n = 1, presentar el único valor y resumen; la desviación muestral permanece no disponible. Con n = 0, mostrar SIN_DATOS.

En G09, el servicio debe autorizar la salida de pares numéricos de B08. La tabla no incorpora nombres, contactos ni IDs directos. Si el contrato aún no proporciona pares autorizados, mostrar los resúmenes disponibles y dejar ese componente pendiente.

## 6. Mapas de calor: conteos y porcentajes

### 6.1. Matriz y métricas

Para una matriz O con fila i y columna j, mantener esas categorías visibles y el mismo orden en la tabla. En la representación cartesiana, el dato visual corresponde a `[j, i, valor]`: columna en X, fila en Y.

| Modo | Valores del backend | Leyenda y base |
| --- | --- | --- |
| Conteos observados | O_ij | «Número de respuestas» o unidad aprobada; N_pares_validos. |
| Porcentaje por fila | Porcentajes del paso 04 | «% dentro de cada fila»; margen de cada fila. |
| Porcentaje por columna | Porcentajes del paso 04 | «% dentro de cada columna»; margen de cada columna. |
| Porcentaje del total | Porcentajes del paso 04 | «% de pares válidos»; N_pares_validos. |
| Frecuencias esperadas, diagnóstico | E_ij, solo si está disponible | «Frecuencia esperada bajo independencia»; valores decimales, no observaciones reales. |

El selector de modo ofrece únicamente salidas presentes en el resultado. El navegador no calcula E ni reconstruye porcentajes. Configurar orientación para que la primera fila visible coincida con el orden de la tabla.

### 6.2. Color, ceros y valores ausentes

Usar una escala secuencial, leyenda explícita y etiquetas legibles; `visualMap` permite declarar dimensión, mínimo y máximo (RV2). Para porcentajes, fijar 0-100. Para conteos de una matriz, usar cero y un máximo declarado acorde con su rango; al comparar matrices, usar un rango común o advertir de manera visible el cambio de escala.

Una celda observada con cero es un dato válido. Un porcentaje NULL por margen cero, una celda no disponible o una combinación no aplicable tendrá representación y texto propios, sin asignarle el color de cero. No ocultar ceros mediante expresiones como `valor || '-'`.

Si solo existen ceros en una tabla sin pares válidos, mostrar SIN_DATOS en vez de construir una leyenda engañosa. Las celdas estructuralmente imposibles se señalan conforme al backend y al estado ESTRUCTURA_INCOMPATIBLE del contraste.

El tooltip contiene variables/categorías, valor, modo, base y estado. La tabla permite leer todos los valores aunque no se pueda interactuar con el gráfico.

### 6.3. Relación con chi-cuadrado

El mapa muestra relaciones descriptivas. El resumen estadístico G12 usa el resultado ya calculado por Java.

- CALCULADO: mostrar chi-cuadrado, gl, p original/ajustada, alpha, V y texto interpretativo del protocolo.
- FRECUENCIAS_INSUFICIENTES: conservar descripción y diagnóstico disponible; mostrar «No se ejecutó el contraste: frecuencias esperadas insuficientes».
- SIN_VARIACION, INDEPENDENCIA_NO_DOCUMENTADA o PENDIENTE_DEFINICION: mostrar el motivo correspondiente y la descripción que exista.
- Una prueba no ejecutada no recibe una etiqueta «sin asociación» ni un p de cero o uno.

No colorear las celdas del mapa como «significativas» por el resultado global: este protocolo no define pruebas por celda. Si existen valores p ajustados, la decisión mostrada procede del backend, no de comparar el p formateado en JavaScript.

## 7. Distribuciones de valoraciones y No sabe

G05 conserva ACUERDO_4, SATISFACCION_3, IMPORTANCIA_4 y EMPLEADORES como escalas distintas. El título y la tabla indican la escala utilizada. La versión V1 propone barras horizontales apiladas por pregunta/criterio con base declarada; las categorías mantienen sus etiquetas.

Para E13-E22:

1. La distribución sustantiva incluye TDES, PAC y TAC sobre N_sustantivos, con orden de desacuerdo a acuerdo.
2. NS aparece aparte como «No sabe: n de N_validos», con porcentaje sobre todas las respuestas válidas.
3. La tabla completa conserva las cuatro categorías y su porcentaje sobre N_validos, además del resumen sustantivo.
4. Si N_sustantivos = 0 y hay NS, indicar «Todas las respuestas válidas son No sabe»; conservar su conteo. No construir una barra sustantiva llena de ceros.

Para titulados, no agregar una categoría NS a una escala que no la contiene. Los estados SIN_RESPUESTA/NO_APLICA/NO_RECOLECTADA permanecen fuera de las categorías de valoración y se muestran en cobertura.

La media de puntajes codificados se acompaña de rango de escala, n puntuado y regla de NS. No unir medias de escalas diferentes en un único gráfico como si fueran la misma unidad. Los índices compuestos y brechas siguen D05.

## 8. Radar requerido por HU5

### 8.1. Métrica y paneles propuestos

Para presentar un radar disponible con el instrumento de empleadores, usar el **porcentaje de acuerdo sustantivo** definido en el paso 04:

$$
valor\_eje = 100\frac{n_{TAC}+n_{PAC}}{N_{sustantivos}}.
$$

El backend entrega ese porcentaje, su numerador, denominador y cantidad NS. El navegador lo representa en un eje con mínimo 0 y máximo 100, configurados explícitamente en `radar.indicator` (RV3).

| Panel | Ejes en orden fijo | Nombre visible |
| --- | --- | --- |
| R01 | E13, E14, E15, E16, E17 | «Valoraciones de empleadores: criterios 1-5». |
| R02 | E18, E19, E20, E21, E22 | «Valoraciones de empleadores: criterios 6-10». |

Estos paneles separan diez criterios para legibilidad; no crean dos índices. Mostrar el nombre completo del criterio en tooltip/tabla, además de una etiqueta corta para cada eje. La visualización representa opiniones institucionales diversas; no llamarla «mapa de diez habilidades», porque esos criterios no son diez competencias individuales.

Mantener eje, orden y escala idénticos entre grupos. Un cambio posterior de métrica o panel requiere una configuración identificable y el mismo contrato para tabla y reporte. T39/T40 podrán tener radares propios solo después de definir sus ítems y referentes conforme a D05.

### 8.2. Disponibilidad y comparación

- Cada serie requiere valores finitos 0-100 y N_sustantivos > 0 en los cinco criterios de su panel.
- Si algún eje carece de dato, no rellenarlo con cero, media o «No sabe», ni cerrar un polígono atravesando esa ausencia. Mostrar la razón y la tabla/distribuciones disponibles.
- Un cero de acuerdo con denominador positivo es válido y puede representarse.
- La tabla por eje muestra numerador, N_sustantivos, NS y N_validos. Esas bases pueden variar por criterio y deben ser visibles.
- Comparar como máximo dos grupos a la vez, con definiciones, unidad de análisis y criterios comparables. Mostrar la comparación solo cuando ambas series estén completas; los demás casos conservan su explicación y tablas.
- Usar líneas, marcadores y colores distinguibles, con relleno tenue opcional. El área del polígono no es un indicador de calidad ni una medida estadística del protocolo.

El radar se acompaña siempre de tabla. En teléfono, sus dos paneles aparecen uno después del otro; si no caben las etiquetas, reducir abreviaturas conservando detalle accesible, sin omitir criterios silenciosamente.

## 9. Estados, mensajes y datos parciales

La carga de la interfaz, la disponibilidad de la variable y el estado del contraste son dimensiones distintas. Una matriz puede estar lista aunque su prueba no se ejecute.

| Estado técnico/contexto | Presentación propuesta |
| --- | --- |
| CARGANDO | «Cargando resultados» o «Actualizando selección»; indicador de actividad y contexto solicitado. |
| LISTO | Gráfico y tabla con base, etiquetas y datos coherentes. |
| SIN_DATOS | «No hay respuestas válidas para esta selección»; conservar filtros y facilitar restablecerlos. |
| NO_APLICA | «Este bloque no aplica a los participantes de la selección actual». |
| NO_RECOLECTADA | «Esta variable no se recogió en la versión seleccionada». |
| VALOR_INVALIDO / bloqueo de calidad | «Hay valores que requieren revisión»; cantidad y ámbito afectados, sin revelar contactos. |
| PENDIENTE_DEFINICION | «El indicador aún no tiene una definición disponible»; detalle de la variable o regla. |
| FRECUENCIAS_INSUFICIENTES | «No se ejecutó el contraste: frecuencias esperadas insuficientes»; descripción disponible. |
| SIN_VARIACION | «La selección no tiene suficientes categorías para este contraste». |
| INDEPENDENCIA_NO_DOCUMENTADA | «El contraste requiere definir la unidad de análisis y las participaciones repetidas». |
| ESTRUCTURA_INCOMPATIBLE | «La estructura de este cruce requiere otro análisis». |
| ENTRADA_INCOMPATIBLE | «Los datos de este resultado no son conteos válidos para el contraste». |
| ERROR_CALCULO | «No se pudo completar el cálculo»; conservar contexto y referencia de revisión. |
| ERROR_CONSULTA | «No se pudieron obtener los resultados»; opción de reintento, sin cifras inventadas. |
| ACCESO_RESTRINGIDO | Explicación de acceso insuficiente o sesión vencida, sin contenido protegido. |

Un dato ausente se representa mediante texto/estado, no una barra o eje de cero. Los tooltips y tablas distinguen cero, NS y ausencia. No mostrar una predicción de población o un indicador de contratación que el backend no haya producido.

## 10. Legibilidad, accesibilidad y diseño adaptable

### 10.1. Reglas de diseño

- Usar texto oscuro sobre fondos claros, jerarquía simple y tipografía legible; mantener las cifras cerca de su indicador.
- Verificar contraste de texto normal ≥ 4,5:1 y texto grande ≥ 3:1 según RV6. Etiquetas de ejes, tooltip y estados también requieren legibilidad.
- Usar etiquetas, marcadores, bordes o patrones además de color; conservar tabla y descripción de cada gráfico (RV5/RV7).
- Elegir colores por significado o código estable. La misma categoría no cambia de color al filtrar; «No sabe» tiene un tratamiento distinto de ausencia.
- Controles y gráficos necesarios para comprender información deben distinguirse visualmente; aplicar las pautas de contraste no textual, bordes y etiquetas donde corresponda (RV8).
- Todos los filtros, botones, selectores, pestañas y acceso a tablas se operan por teclado con foco visible. La información importante no depende exclusivamente de hover.
- Describir en español título, tipo, variables, base y modo. Evitar descripciones automáticas que lean interminablemente todos los puntos de una dispersión.
- Activar y verificar ARIA según la versión real de ECharts. Para ECharts moderno, usar la configuración `aria.enabled` y asegurar que el componente esté incluido; RV5 documenta también patrones `aria.decal`. No suponer que esto proporciona por sí solo navegación por todos los puntos.
- Respetar preferencia de movimiento reducido y mantener transiciones breves. El cambio de filtros debe poder identificarse sin depender de una animación.

### 10.2. Tamaños y organización

Usar como puntos de comprobación al implementar anchos de 360, 768 y 1280 píxeles, además de ampliación de texto. Son referencias técnicas, no dispositivos obligatorios.

En pantalla estrecha, apilar tarjetas y gráficos, mantener controles táctiles utilizables y ofrecer tabla desplazable para matrices amplias. No reducir todo el dashboard como una imagen ilegible.

Antes de iniciar un gráfico, el contenedor tiene dimensiones válidas. Al cambiar tamaño, abrir una pestaña o expandir una sección, ajustar la instancia mediante `resize`; cuando se retire el contenedor, liberar la instancia con `dispose` (RV4). Comprobar también cambios de tamaño por CSS y no solo los del navegador.

## 11. Contrato de datos y presentación

Cada componente recibe un objeto de resultado de los pasos 03/04 y una configuración visual identificada.

| Información | Uso |
| --- | --- |
| Contexto/ID de consulta | Evitar respuestas fuera de orden y mezclar selecciones. |
| Estudio, conjunto, huella y protocolo | Coherencia y reproducción de cifras; detalle ampliado. |
| Tipo/versiones y unidad de análisis | Etiquetas y comparación válida. |
| ID de indicador, variables y etiquetas | Correspondencia con U01-U14/B01-B08 y diccionario. |
| Categorías, valores y estados | Series, ejes, celdas y tablas; conservar orden y ceros. |
| Numeradores, denominadores y cobertura | Subtítulos, tooltip, detalle y casos sin datos. |
| Unidad, escala y modo | Interpretación de longitud, color y radar. |
| Resultado de contraste | G12 y mensajes, sin decisiones recalculadas. |
| Configuración visual | Tipo, orden de ejes, leyenda, límites y grupos comparados; útil para reproducir el gráfico. |

La separación entre datos y opciones de ECharts evita mezclar formatos de dibujo con reglas del estudio; RV1 ofrece conceptos de `dataset` y mapeo explícito. Para cada tipo de gráfico, utilizar `dataset` o `series.data` según la API compatible, sin asumir que todos admiten exactamente la misma estructura.

Los títulos y etiquetas proceden de catálogos/control de contenido. Escapar textos dinámicos y evitar insertar respuestas o etiquetas como HTML sin tratamiento seguro. Los tooltips no incorporan contactos ni respuestas abiertas originales.

Mostrar conteos enteros, porcentajes y unidades según el paso 04. Mantener valores precisos internamente. La decisión sobre H0 llega del backend, incluso cuando un p se muestre redondeado cerca de alpha.

La tabla refleja exactamente el mismo conjunto y modo que el gráfico, o identifica claramente las columnas adicionales. Una ordenación o serie oculta no cambia la base de análisis.

## 12. Preparación para reportes y exportaciones de HU5

El siguiente paso definirá cómo generar y exportar reportes. Desde esta especificación se conserva:

- Conjunto/protocolo, filtros aplicados y versiones de instrumento.
- ID del gráfico, variables, categorías y configuración visual.
- Título, unidades, leyenda, numeradores/denominadores y estados.
- Tabla equivalente y resultado de contraste correspondiente.
- Orden de criterios, escala fija y definición del radar.
- Fecha de referencia y fecha de generación diferenciadas.

El reporte toma la selección aplicada, no filtros editados todavía sin aplicar. Un gráfico guardado debe ir acompañado del contexto necesario para interpretarlo. Los formatos, el renderizado fuera del navegador y la preservación de interactividad se decidirán en el paso 06; no se introduce aquí otra tecnología de backend.

## 13. Ejemplos sintéticos de presentación

Estos ejemplos comprueban cómo deben verse cifras conocidas; no son resultados de campo ni decisiones nuevas del instrumento.

| Ejemplo | Presentación esperada |
| --- | --- |
| T14 del paso 04: 6 ocupados declarados entre 8 válidos, de 10 base | Tarjeta «Ocupación declarada: 75 %», «6 de 8 respuestas válidas» y cobertura base 10. |
| T25: cuatro participantes, conteos A/B/C = 3/2/2 | Barras 75/50/50 %, nota de selección múltiple y tabla. No gráfico circular que fuerce una suma de 100 %. |
| E13: TAC = 6, PAC = 2, TDES = 1, NS = 2, vacío = 1 | Barra sustantiva con N = 9; NS aparte 2 de 11 válidos; radar 88,888889 % si los demás ejes del panel están disponibles. |
| Un eje de radar tiene cinco respuestas NS y ninguna sustantiva | Valor del eje no disponible; no generar esa serie completa del radar ni representarla como cero. |
| Cinco ejes de un panel tienen porcentaje de acuerdo 0 con denominadores positivos | Serie válida en cero; conservar los tamaños y su significado. |
| Matriz asimétrica O = [[18,12],[6,24]] | Índices visuales [0,0,18], [1,0,12], [0,1,6], [1,1,24]; fila 1: 60/40 %, fila 2: 20/80 %. |
| Matriz de esperadas pequeñas del paso 04 | Mapa/tabla descriptivos y aviso FRECUENCIAS_INSUFICIENTES; p no calculado. |
| Resumen numérico [2,4,4,4,5,5,7,9] | Caja de cinco números `[2, 4, 4.5, 5.5, 9]`; tabla con etiquetas locales: mediana 4,5 y Q3 5,5. |
| p interna = 0,0496, mostrada como 0,050 | La decisión conserva el resultado del backend; el frontend no decide con el texto mostrado. |

En datos JSON usar números con punto decimal; la presentación puede usar coma decimal conforme al idioma.

La matriz asimétrica es deliberada para detectar intercambio de ejes. Sus porcentajes ya llegan preparados del backend; el ejemplo valida el mapeo visual.

## 14. Criterios de aceptación visual

Los siguientes casos se ejecutarán al implementar la interfaz. La revisión de este documento no equivale a tener un dashboard construido.

| Caso | Situación | Evidencia esperada |
| --- | --- | --- |
| CVE01 | Se abre una vista autorizada | Muestra campaña, sección, selección aplicada y alcance de los datos. |
| CVE02 | Se cambia un filtro y se aplica | Tarjetas, tablas y gráficos usan el contexto nuevo de forma coherente. |
| CVE03 | Una consulta anterior termina después de la actual | Sus resultados no sobrescriben la selección vigente. |
| CVE04 | Se ordenan categorías o se oculta una serie | No cambian cifras, denominadores o decisiones; se indica el alcance visual. |
| CVE05 | Categoría con conteo cero | Aparece como cero válido y sigue distinguida de NULL. |
| CVE06 | Resultado SIN_DATOS/NO_RECOLECTADA | Muestra el mensaje específico, sin gráficos llenos de ceros ficticios. |
| CVE07 | Indicador de ocupación del ejemplo | Se muestran 75 % y 6/8, con base 10; el título no dice tasa oficial de empleo. |
| CVE08 | Pregunta múltiple del ejemplo | Barras 75/50/50 %, nota y tabla consistentes, sin renormalizar a 100 %. |
| CVE09 | Distribución de empleadores con NS | NS se presenta aparte y conserva su base; no figura como desacuerdo, neutralidad o puntaje cero. |
| CVE10 | Matriz asimétrica de la sección 13 | Valores y categorías correctos en [columna,fila,valor]; fila/columna coinciden con la tabla. |
| CVE11 | Se cambia modo del mapa | Cambian unidad, leyenda y valores al modo correspondiente del backend; N_pares_validos permanece coherente. |
| CVE12 | Celda cero y celda NULL | Tienen lectura y estado diferentes; NULL no toma el color de cero. |
| CVE13 | Contraste con esperadas insuficientes | Conserva descripción, muestra motivo y no presenta p ni «sin asociación». |
| CVE14 | Contraste con ajuste múltiple | Se identifican p original/ajustada, familia y decisión del backend. |
| CVE15 | Se muestra un radar | Ejes del panel en orden, mínimos 0/máximos 100 y métrica explícita. |
| CVE16 | Falta un eje del radar | No se dibuja una serie completada artificialmente; tabla y razón disponibles. |
| CVE17 | Eje con acuerdo cero y base positiva | El cero es válido y se representa sin confundirlo con ausencia. |
| CVE18 | Se comparan dos grupos | Mismos criterios/escala, nombres y tamaños por eje visibles; comparación solo con series completas. |
| CVE19 | Se presenta G07 | Los cinco números coinciden con backend y se declaran bigotes mínimo/máximo; n = 1 tiene tratamiento especial. |
| CVE20 | Usuario usa teclado o lector de pantalla | Accede a filtros, pestañas y tablas; dispone de foco visible y descripción en español. |
| CVE21 | Vista móvil, ampliada o pestaña recién abierta | No corta controles/etiquetas esenciales; gráfico se redimensiona y tabla sigue utilizable. |
| CVE22 | Se navega entre vistas repetidas veces | Instancias y listeners no se acumulan; los gráficos vuelven a mostrarse correctamente. |
| CVE23 | Etiqueta dinámica con caracteres de HTML | Se presenta como texto seguro, sin ejecutar código ni exponer contenido restringido. |
| CVE24 | Datos de demostración | Identificación sintética persistente y separación respecto de resultados reales. |
| CVE25 | Preparación de una salida de reporte | Gráfico, tabla, filtros y metadatos pertenecen a la selección aplicada y conservan el contrato HU5. |

## 15. Finalización y siguiente parte

- [ ] Las siete vistas y doce componentes cubren U01-U14 y B01-B08 del protocolo.
- [ ] Las cifras vienen del backend y conservan unidades, bases y estados.
- [ ] Los mapas de calor distinguen conteos, porcentajes, esperadas, ceros y ausencias.
- [ ] El radar exigido por HU5 tiene una métrica y paneles explícitos, sin completar datos faltantes.
- [ ] Se conserva el tratamiento de NS, preguntas múltiples y escalas distintas.
- [ ] Filtros, carga y respuestas fuera de orden no mezclan conjuntos.
- [ ] Cada gráfico tiene tabla equivalente y presentación legible en móvil y escritorio.
- [ ] Los estados del contraste usan el protocolo estadístico y textos comprensibles.
- [ ] Contactos, identificadores y texto original siguen bajo acceso restringido.
- [ ] Se documentan criterios futuros de verificación sin afirmar que la interfaz ya fue probada.

Las decisiones D01-D23 siguen su registro. Los paneles, colores y organización aquí propuestos son una base técnica que Codex puede ajustar con criterio, documentando cambios; alterar el significado de un eje, su denominador o el protocolo requiere correspondencia explícita con las etapas anteriores.

**Siguiente parte:** `06_AUTOMATIZACION_REPORTES_EXPORTACIONES.md`, para definir la generación de reportes desde el backend, matrices, tablas y gráficos, exportación CSV/Excel, trazabilidad y conservación del contexto seleccionado. La experimentación por simulación tendrá después su documento específico.

## 16. Referencias técnicas consultadas

Consulta: 13 de octubre de 2026. Estas referencias apoyan capacidades o pautas de presentación; las decisiones del dashboard son propuestas del proyecto.

| ID | Referencia |
| --- | --- |
| RV1 | [Apache ECharts — Dataset](https://echarts.apache.org/handbook/en/concepts/dataset/). Separación de datos y mapeo de dimensiones. |
| RV2 | [Apache ECharts — Visual Mapping](https://echarts.apache.org/handbook/en/concepts/visual-map/). Escalas y dimensión de color. |
| RV3 | [Apache ECharts — Radar, documentación del componente](https://apache.googlesource.com/echarts-doc/%2Bshow/9b0fa2470786f87a1fee90349c3d8d9af3a1f043/en/option/component/radar.md). Indicadores y límites por eje. |
| RV4 | [Apache ECharts — Chart Container and Size](https://echarts.apache.org/handbook/en/concepts/chart-size/). Dimensiones, resize y ciclo de vida. |
| RV5 | [Apache ECharts — Web Accessibility](https://echarts.apache.org/handbook/en/best-practices/aria/) y [documentación ARIA del proyecto](https://github.com/apache/echarts-doc/blob/master/en/tutorial/aria.md). Descripciones y patrones; verificar configuración según versión. |
| RV6 | [W3C — Contrast Minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html). Contraste de texto. |
| RV7 | [W3C — Use of Color](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html). Información adicional al color. |
| RV8 | [W3C — Non-text Contrast](https://www.w3.org/WAI/WCAG22/understanding/non-text-contrast.html). Distinción de controles y objetos gráficos. |


## Nota de integración del proyecto

El usuario autorizó continuar el desarrollo de la fase 5 el 2026-10-13 con «vamos», después de revisar su alcance. Esa autorización permite implementar el dashboard descrito y prevalece sobre la limitación documental de la sección 1. La copia conserva el contenido de referencia; sus propuestas metodológicas no se convierten en aprobaciones institucionales. El contrato efectivo y la evidencia se registran en VISUALIZACION.md y ACEPTACION_VISUALIZACION.md.
