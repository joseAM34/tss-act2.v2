# Visualización computacional de fase 5

Estado al 2026-10-14: incremento implementado, con 288 pruebas Java correctas en H2 y en la ejecución con PostgreSQL, nueve pruebas JavaScript y comprobaciones del dashboard en Chrome a 360, 768 y 1280 píxeles. El alcance y los límites de esa evidencia se registran en [ACEPTACION_VISUALIZACION.md](ACEPTACION_VISUALIZACION.md). La implementación fue autorizada el 2026-10-13; su funcionamiento no constituye aprobación metodológica ni institucional del instrumento, los conjuntos o el protocolo.

La instrucción del usuario «vamos», después de revisar el documento, permite construir el dashboard. Prevalece sobre el alcance exclusivamente documental de [05_VISUALIZACION_COMPUTACIONAL.md](05_VISUALIZACION_COMPUTACIONAL.md); las decisiones vinculantes siguen en [DECISIONES.md](DECISIONES.md), [INSTRUMENTO.md](INSTRUMENTO.md), [DATOS_Y_WRANGLING.md](DATOS_Y_WRANGLING.md) y [ESTADISTICA.md](ESTADISTICA.md).

## Flujo y fuentes

El dashboard abre un análisis guardado y su conjunto preparado exacto. Ambos se verifican antes de consultar o filtrar. No completa datos desde respuestas actuales, no toma el catálogo actual para interpretar una versión histórica y no incorpora correcciones posteriores al conjunto.

El flujo de administración es **Preparar datos → abrir conjunto → Analizar este conjunto → abrir análisis → Visualización**. Las rutas de página y datos requieren `ADMIN`; las encuestas públicas conservan su recorrido. El acceso administrativo y la creación de conjuntos/análisis mantienen la protección CSRF existente. Consultar o filtrar mediante GET no crea otro análisis persistido ni modifica el original.

| Operación | Ruta protegida |
| --- | --- |
| Abrir dashboard | GET `/admin/estadisticas/{id}/visualizacion` |
| Obtener selección y componentes | GET `/admin/estadisticas/{id}/visualizacion.json` |

Las dos respuestas usan `Cache-Control: no-store`. El contrato ordinario excluye contactos, nombres de organizaciones, textos originales e identificadores de encuestados. La dispersión publica exclusivamente pares numéricos autorizados, sin UUID, nombre o contacto; esas coordenadas siguen siendo información administrativa protegida y no se presentan como datos garantizados anónimos.

Los cálculos provienen del backend. Sin filtros se utiliza el resultado congelado del análisis. Con filtros se seleccionan filas del mismo bundle preparado y se aplica su plan estadístico: intersecciones, frecuencias, denominadores, cuantiles y diagnósticos se calculan en Java. El navegador representa los resultados y cambia modo, tamaño o visibilidad de series; conserva el orden de catálogo o semántico recibido y no calcula pruebas ni porcentajes desde respuestas crudas. Las consultas filtradas son descriptivas y exploratorias del subconjunto seleccionado, sin control global del error de todas las selecciones interactivas.

## Siete vistas

| ID | Vista | Contenido |
| --- | --- | --- |
| V01 | Resumen del estudio | Bases separadas de ambos cuestionarios, origen, modo y tarjetas de indicadores disponibles. |
| V02 | Titulados: perfil y trayectoria | Edad, género, egreso, empleo, sector, emprendimiento y duración profesional/desempleo. |
| V03 | Formación continua | Formación cursada, interés futuro, áreas, instituciones, nivel y financiamiento; pasado y futuro separados. |
| V04 | Empleadores y demanda | Tipo, tamaño y sector de organización; contratación, selección y temas revisados. |
| V05 | Valoración de la formación | Escalas de titulados, doce competencias de adecuación del borrador y diez criterios únicos de empleadores. |
| V06 | Cruces y contrastes | B01–B08 registrados; matrices y mapas de calor, diagnóstico y resultados inferenciales disponibles, cajas y pares numéricos. |
| V07 | Calidad y cobertura | Bases y estados por variable e incidencias agregadas, sin sumar incidencias solapadas como personas distintas. |

Las competencias T39/T40 del borrador ya tienen ítems y referente aprobados por el usuario en fase 2; la frase pendiente de la referencia no revoca esa decisión. Se presentan sus distribuciones de adecuación con cinco grados y `NO_PUEDE_VALORAR` sin puntaje. Esa aprobación de borrador no autoriza índices compuestos, brechas o aprobación institucional.

## Filtros y comparaciones

La selección acepta `study`, `survey`, `version`, `from`, `to`, `year`, `employment`, `graduateSector`, `training`, `interest`, `organizationType`, `organizationSize`, `organizationSector`, `compareBy`, `groupA` y `groupB`. Los valores se validan contra el estudio, versiones y catálogos congelados. Las opciones de año muestran los años observados utilizables; un año entero dentro del rango congelado puede producir una selección vacía aunque no tenga envíos. No se permiten nombres o contactos como filtros analíticos. Los filtros propios de titulados o empleadores fijan su ámbito y no se combinan entre ambos cuestionarios.

El período usa la fecha de recepción del envío en `America/La_Paz`, desde el inicio de `from` hasta el final de `to`, ambos inclusivos. No se confunde con el año de egreso, el corte del conjunto o la fecha técnica del análisis. Los nuevos conjuntos guardan `submitted_at` en su inventario de miembros y su huella. Un conjunto anterior sin esa metadata sigue siendo consultable sin período; solicitar un período se rechaza con una explicación para preparar un conjunto nuevo. No se completa desde la base actual ni se reescribe el bundle histórico.

El origen queda fijado por el conjunto preparado: `REAL`, `SINTETICO` o `NO_DECLARADO`. La demostración sintética conserva una identificación visible y los envíos sin revisar muestran «Origen sin verificar». Filtrar no cambia ese origen ni convierte un borrador en oficial.

Se conservan estratos por estudio, encuesta y versión. Seleccionar varios estratos no aprueba una correspondencia entre versiones ni mezcla sus frecuencias. El año de egreso se presenta directamente; antigüedad derivada y cohortes institucionales continúan pendientes D22.

La comparación admite dos grupos distintos de una variable registrada: situación laboral o sector laboral de titulados; tipo o tamaño de organización de empleadores. Usa etiquetas y tamaños propios para cada grupo. Los paneles comparables conservan categorías, unidades y escalas. La comparación de radar requiere los cinco criterios disponibles en ambos grupos; no se rellena un grupo incompleto. El alcance de las familias Holm permanece el del protocolo exploratorio.

## Gráficos y tablas equivalentes

Cada componente incluye un título, estado, unidad, base y tabla. La tabla del modo activo usa los mismos valores del gráfico; sus columnas adicionales se identifican como contexto. `null` significa ausencia y se presenta como tal; cero con base positiva es un valor observado.

Las barras categóricas mantienen categorías del catálogo, incluidas las de conteo cero. Las ordinales conservan su orden semántico. Las selecciones múltiples ofrecen porcentajes de participantes y porcentajes de menciones como modos distintos; la suma de porcentajes de participantes puede superar 100 %. Nunca se renormaliza a 100 % como si fueran partes exclusivas.

Las distribuciones de empleadores separan tres categorías sustantivas y `NO_SABE`. La barra sustantiva usa `N_sustantivos`; la tabla conserva las cuatro categorías sobre `N_validos` y muestra ambos denominadores. Si todas las respuestas válidas son `NO_SABE`, no se dibuja una barra sustantiva falsa. Las escalas de satisfacción, acuerdo, importancia y adecuación permanecen separadas.

Los mapas de calor conservan el orden de filas y columnas del backend. Cada celda se representa como `[columna, fila, valor]`. Los modos disponibles son observadas, porcentaje por fila, por columna, del total y esperadas diagnósticas. Los porcentajes usan escala 0–100; las esperadas se etiquetan como esperadas bajo independencia. Una celda cero tiene color y valor de cero; una celda `null` por margen cero muestra ausencia. Sin pares válidos se muestra `SIN_DATOS`. Las celdas no reciben etiquetas de significación a partir del contraste global.

Los paneles de radar R01 y R02 contienen respectivamente E13–E17 y E18–E22, una sola vez cada criterio. Cada eje muestra porcentaje de acuerdo sustantivo `(TOTALMENTE_ACUERDO + PARCIALMENTE_ACUERDO) / N_sustantivos × 100`, ya calculado por Java, con mínimo 0 y máximo 100. La tabla conserva numerador, `N_sustantivos`, `NO_SABE`, `N_validos` y porcentaje de cada eje. Un cero de acuerdo con base positiva es válido. Un eje sin respuesta sustantiva impide dibujar esa serie del panel, conservando su explicación y la tabla. Los paneles representan valoraciones institucionales, no diez habilidades ni dos índices.

Las cajas usan `[mínimo, Q1, mediana, Q3, máximo]` del backend. Sus bigotes se declaran como mínimo y máximo; no aplican 1,5 IQR ni clasifican atípicos. Con n=1 se conserva el único valor y la tabla, sin caja ni desviación muestral inventada. B08 muestra vida profesional en X y desempleo en Y, ambos en meses enteros, con una tabla de coordenadas sin identificadores. Añade resúmenes de la muestra pareada y de cada duración por estado laboral/sector, cada uno con su n válido propio; no se añade correlación, causalidad o chi-cuadrado.

Los contrastes conservan el estado, motivo, pares, esperadas, familia, M planificado, número de contrastes ejecutados, p original/ajustada, alpha, V y decisión disponibles del protocolo. Una prueba no ejecutada sigue sin p ni decisión; no se anuncia «sin asociación». El formato visual de p no cambia la decisión recibida. B07 permanece `PENDIENTE_DEFINICION` mientras falte una referencia aprobada para antigüedad.

## Contrato y trazabilidad

`DashboardDataService` verifica fuente y análisis, valida la selección y obtiene resultados. `DashboardProjection` prepara una vista agregada con `context`, `filters`, siete `sections` y `visual_config_version=VISUALIZACION_V1`. Cada sección tiene `id`, `title`, `description` y `charts`; cada gráfico identifica tipo, disponibilidad, motivo, base, unidad, datos y tabla. Los modos visuales conservan sus propias tablas.

El contexto identifica análisis, conjunto, huellas de fuente, plan/protocolo, filtros aplicados, base original y seleccionada, origen, modo, disponibilidad de fechas, huella de selección y huella de resultado. Una selección repetida sobre la misma entrada produce el mismo contexto lógico y cifras. Esos identificadores permiten comprobar qué se mostró y preparar reportes futuros; la fase 5 no genera todavía los reportes automatizados ni Excel.

Los recursos gráficos se sirven desde el mismo monolito. Apache ECharts 6.0.0 se incorpora como recurso local con avisos de terceros registrados; no se carga desde CDN ni requiere un proceso Node.js para iniciar el proyecto. La licencia del recurso de terceros no asigna una licencia al proyecto.

## Interacción, accesibilidad y límites

Editar un filtro no cambia las cifras mostradas hasta pulsar «Aplicar filtros». La carga se anuncia, la selección aplicada permanece identificada y una consulta anterior no reemplaza una posterior. El aviso de cambios sin aplicar compara exclusivamente los dieciséis campos de selección permitidos, sin propiedades técnicas de serialización. Restablecer vuelve a la selección base del análisis. Cambiar modo o esconder una serie conserva el conjunto, las cifras y las decisiones; la tabla sigue disponible.

La interfaz dispone de filtros y navegación por teclado, foco visible, descripciones en español y tablas equivalentes. Las etiquetas dinámicas se presentan como texto seguro. El diseño apila componentes en pantallas estrechas y permite desplazar matrices. Los gráficos ajustan su tamaño al abrir secciones o cambiar contenedores y se liberan antes de sustituirlos. Se respeta movimiento reducido y se incorpora ARIA según la versión utilizada.

Chrome comprobó las siete vistas a 360, 768 y 1280 píxeles con HTML Thymeleaf y JSON sintéticos obtenidos por MockMvc, recursos gráficos reales y la política de contenido del backend. Se verificaron ausencia de desbordamiento horizontal de la página, instancias gráficas disponibles tras navegar, presencia de tablas y descripciones ARIA, cambio de modo de mapa de calor, navegación de pestañas por teclado, opciones de comparación y presentación de un error sin conservar cifras anteriores. También se probaron dos consultas consecutivas con la primera retrasada: la respuesta antigua no sustituyó la selección más reciente, y al aplicar la selección no quedó un aviso falso de cambios pendientes. El navegador no realizó un segundo ensayo de autenticación o recálculo de filtros contra una aplicación desplegada: esos contratos se comprueban en Java.

Estas medidas y comprobaciones no equivalen a conformidad WCAG completa ni a una prueba con lector de pantalla. Permanecen pendientes revisión asistida con lector real, ampliación de texto/zoom, auditoría completa de contraste y valoración manual de legibilidad en los tamaños de referencia. La aceptación distingue esa revisión pendiente de las comprobaciones técnicas ya ejecutadas.

Permanecen pendientes aprobación institucional, elegibilidad y participaciones distintas, unidad de análisis de empleadores, correspondencia entre versiones, codebook aprobado, antigüedad/cohortes, diseño muestral y alcance confirmatorio. Los reportes/Excel, el modelo de simulación y el documento final de Análisis de Mercado pertenecen a los incrementos siguientes.
