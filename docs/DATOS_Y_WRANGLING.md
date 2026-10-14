# Fase 3: datos y preparación reproducible

Estado: modelo y pipeline de fase 3 implementados y verificados técnicamente el 2026-10-13. La ejecución original pasó 159 casos por ejecución H2/PostgreSQL, según los límites y evidencia de [ACEPTACION_DATOS.md](ACEPTACION_DATOS.md); la ampliación de fase 4 se registra en [ACEPTACION_ESTADISTICA.md](ACEPTACION_ESTADISTICA.md). La metadata temporal de fase 5 fue verificada en las ejecuciones de 288 pruebas en H2 y PostgreSQL el 2026-10-14, según [ACEPTACION_VISUALIZACION.md](ACEPTACION_VISUALIZACION.md). Este documento concreta la referencia [03_DATOS_Y_DATA_WRANGLING.md](03_DATOS_Y_DATA_WRANGLING.md) y conserva las decisiones de [DECISIONES.md](DECISIONES.md).

El pipeline prepara las respuestas recibidas por la aplicación. No importa el directorio de candidatos ni inventa encuestas respondidas. La estadística pertenece al módulo de fase 4, que consume estos conjuntos; reportes automatizados y simulaciones mantienen sus etapas propias. La captura y la preparación pertenecen al backend Java del monolito; PostgreSQL es la base principal y H2 se limita a desarrollo y pruebas.

## Captura, estudio y versión

Una respuesta nueva conserva su UUID, estudio, tipo de encuesta, versión del instrumento, instante de envío, aceptación de uso académico, preguntas y opciones de esa versión, valores originales aceptados y estados. La definición del instrumento se congela: una modificación posterior del catálogo no reinterpreta un envío existente.

La huella SHA-256 de una representación canónica identifica la captura aceptada. «Canónica» significa orden y serialización estables, sin depender de la posición accidental de claves de un mapa. La captura no representa los bytes de una solicitud HTTP completa: contiene los valores del instrumento aceptados, no cookies, contraseña, CSRF ni datos de sesión. La metadata y la captura originales están protegidas porque pueden contener contactos y texto abierto.

Los componentes lógicos `respuesta_titulado` y `respuesta_empleador` se implementan mediante las respuestas vinculadas a un único `survey_slug`, sus valores y su definición congelada. No se crean dos respuestas tipadas para el mismo envío. La relación `survey_answer` conserva una fila por valor y orden de selección; `survey_question_state` conserva un estado por pregunta de la versión que lo registró.

El estudio admite estados `BORRADOR` o `APROBADA`; la versión admite además `RETIRADA`. Se mantienen separados del origen de los datos. La migración registra `umss-mercado-2026-borrador`, con nombre y año técnico de referencia 2026, estado `BORRADOR`. Ese año no constituye por sí solo una referencia metodológica aprobada para derivar antigüedad. El sistema no interpreta una clasificación real, una migración ni una ejecución como aprobación institucional.

`InstrumentRegistry` registra la definición del catálogo vigente al arrancar y vuelve a comprobarla al guardar. Una misma pareja encuesta/versión no puede cambiar de huella de definición: el servidor rechaza esa reutilización y exige asignar una versión nueva. El registro no asigna retrospectivamente la definición vigente a respuestas históricas.

`CaptureStore` distingue `CAPTURA_LOGICA`, metadata completa nueva con valores/estados/consentimiento aceptados, de `RECONSTRUIDA_HISTORICA`, representación limitada formada solo con valores y estados realmente almacenados. La reconstrucción histórica no inventa estudio, definición congelada ni contenido HTTP anterior; usa el marcador `SIN_ESTUDIO` y una definición ausente para expresar esas limitaciones. No se inserta una captura nueva completa para aparentar metadata histórica.

## Origen, modos y participación

| Origen | Significado operativo | Decisión |
| --- | --- | --- |
| `NO_DECLARADO` | El origen no está confirmado administrativamente. | Estado inicial de los envíos; no inferir datos reales desde el acceso público. |
| `REAL` | Un administrador revisó y clasificó la captura como respuesta real. | Revisión trazable con motivo, actor e instante; no acredita por sí sola elegibilidad ni independencia. |
| `SINTETICO` | Datos de prueba o ejemplo identificados administrativamente. | Se mantienen separados de respuestas reales y no sustentan conclusiones del trabajo de campo. |

Los envíos históricos no reciben origen supuesto: la falta de metadata se conserva como limitación y la eventual revisión queda registrada después. Un participante público no puede asignar origen, aprobar versiones, corregir capturas ni ejecutar preparación.

| Modo de preparación | Restricciones |
| --- | --- |
| `BORRADOR` | Permite revisar el origen seleccionado manteniéndolo separado en el conjunto y manifiesto; no presenta resultados como oficiales. |
| `OFICIAL` | Solo admite origen `REAL`, estudio aprobado y versiones aprobadas. Las entradas que incumplen las condiciones no se convierten silenciosamente en datos oficiales. |

Este incremento no incorpora una pantalla para otorgar aprobación metodológica. Con el estudio y las versiones actuales en borrador, el modo oficial permanece bloqueado y comunica el motivo. No se necesita aprobar institucionalmente el instrumento para implementar y probar el modo de preparación de borrador.

La unidad operativa de las matrices es **un envío**, no una persona única ni una empresa única confirmadas. D13 y D16 siguen pendientes: elegibilidad UMSS, varias participaciones y unidad de análisis de empleadores. Coincidir en correo, organización o contenido no autoriza eliminar ni agregar respuestas automáticamente. No se afirma independencia de observaciones para una futura prueba estadística.

## Diccionario efectivo y privacidad

Los IDs ASCII existentes continúan siendo los nombres de las variables de captura. Los nombres propuestos `tNN_`/`eNN_` y códigos abreviados del documento fuente no sustituyen las opciones históricas. La correspondencia con T01-T41/E00-E22/E09_OTRO, los enunciados, obligatoriedad, condiciones y páginas está en [ACEPTACION_CUESTIONARIOS.md](ACEPTACION_CUESTIONARIOS.md).

Cada conjunto incluye su diccionario con identificador, ID de requisito, etiqueta, tipo, unidad, catálogo/escala, condición, privacidad y transformación. El diccionario se obtiene de las definiciones congeladas verificables; no se completa un histórico con la definición actual sin evidencia.

| Requisito | Variable efectiva | Tipo y unidad | Tratamiento ordinario |
| --- | --- | --- | --- |
| T01 | `edad` | Número entero; años completos. | Variable estructurada; rango técnico 0-120, sin deducir elegibilidad. |
| T02 | `genero` | Categoría nominal. | Códigos Hombre/Mujer/Otro/Prefiero no responder del catálogo versionado. |
| T03 | `formacion_complementaria` | Sí/No nullable con estado. | Un No válido se distingue de ausencia. |
| T04 | `nivel_cursado` | Categoría ordenable de nivel. | No constituye años de educación ni puntaje. |
| T05 | `institucion_cursada` | Categoría nominal. | Se conserva el código versionado. |
| T06 | `financiamiento_cursado` | Categoría nominal. | Financiamiento pasado, separado de T38. |
| T07 | `programa_cursado` | Texto original restringido. | No aparece como texto en matriz ordinaria; admite codificación manual revisada. |
| T08 | `interes_posgrado` | Sí/No nullable con estado. | Separado de las preferencias condicionales. |
| T09 | `area_interes` | Categoría nominal. | No unir automáticamente con ejemplos de otra versión. |
| T10 | `institucion_interes` | Categoría nominal. | Preferencia futura. |
| T11 | `opinion_posgrado_fcyt` | Escala ordinal de acuerdo original. | Categoría, sin convertirla automáticamente en 1-5. |
| T12 | `meses_vida_profesional` | Entero no negativo; meses completos. | Rango técnico 0-960; no derivar desde egreso. |
| T13 | `meses_desempleado` | Entero no negativo; meses completos. | No superar T12; cero observado se conserva. |
| T14 | `estado_laboral` | Categoría nominal. | Situación actual, diferente de experiencia previa. |
| T15 | `origen_emprendimiento` | Categoría nominal. | Estado y aplicabilidad de la versión. |
| T16 | `entregable_emprendimiento` | Categoría nominal. | Producto/Servicio/Ambos. |
| T17 | `financiamiento_emprendimiento` | Categoría nominal. | No se confunde con financiamiento de posgrado. |
| T18 | `satisfaccion_emprendimiento` | Escala ordinal original de tres grados. | No homologar con acuerdo o adecuación. |
| T19 | `importancia_formacion_negocio` | Escala ordinal original de cuatro grados. | Código original, sin puntuación automática. |
| T20 | `primer_empleo_actual` | Sí/No nullable. | Aplicabilidad según situación actual. |
| T21 | `tiempo_primer_empleo` | Categoría temporal. | No convertir intervalos solapados en duración puntual. |
| T22 | `cantidad_empleos` | Categoría ordinal abierta. | `MAS_TRES` no significa exactamente cuatro. |
| T23 | `satisfaccion_formacion` | Escala de acuerdo original. | Mantener diferencia entre texto y escala. |
| T24 | `concordancia_formacion` | Escala de acuerdo original. | Referente adaptado del borrador, solo experiencia relacionada. |
| T25 | `aspectos_utiles` | Selección múltiple. | Relación larga e indicadores 1/0/null con estado. |
| T26 | `aspectos_mejorar` | Selección múltiple. | Relación larga e indicadores 1/0/null con estado. |
| T27 | `asignaturas_ventaja` | Selección múltiple. | Cinco selecciones si se responde; no sumar texto complementario. |
| T28 | `otra_asignatura_ventaja` | Texto original restringido. | Fuera de matriz ordinaria; categorías revisadas por separado. |
| T29 | `asignaturas_poca_utilidad` | Selección múltiple. | Hasta dos; no sumar texto complementario. |
| T30 | `otra_asignatura_poca_utilidad` | Texto original restringido. | Fuera de matriz ordinaria; categorías revisadas por separado. |
| T31 | `interes_red_contactos` | Sí/No nullable. | No equivale a autorización específica de publicación/contacto. |
| T32 | `email_contacto` | Contacto privado. | Excluido de matrices ordinarias. |
| T33 | `telefono_contacto` | Contacto privado como texto. | Excluido; conserva prefijos/ceros en la captura original. |
| T34 | `linkedin` | Contacto privado como URL textual. | Excluido. |
| T35 | `anio_egreso` | Año entero. | No generar antigüedad sin referencia aprobada y significado reconciliado. |
| T36 | `sector` | Categoría nominal. | `NO_APLICA` categórico observado se distingue de un estado de pregunta. |
| T37 | `nivel_interes` | Nivel principal de futuro posgrado. | Separado de T04. |
| T38 | `financiamiento_futuro` | Fuente principal nominal. | Separado de T06. |
| T39 | `competencia_tecnica_01` a `_06` | Adecuación ordinal y categoría nominal sin puntaje. | Seis ítems de la versión; `NO_PUEDE_VALORAR` no puntúa. |
| T40 | `competencia_transversal_01` a `_06` | Adecuación ordinal y categoría nominal sin puntaje. | Seis ítems de la versión; sin resta de nivel adquirido/requerido. |
| T41 | `experiencia_laboral` | Categoría nominal de experiencia. | Relacionada/Otras áreas/Sin experiencia. |
| Histórico | `anios_vida_profesional` | Años originales de `2026-10-borrador-1`. | No convertir ni reutilizar como meses; documentar limitación de comparación. |

| Requisito | Variable efectiva de empleadores | Tipo | Tratamiento ordinario |
| --- | --- | --- | --- |
| E00 | `email_contacto` | Contacto privado. | Excluido de matrices. |
| E01 | `nombre_organizacion` | Identificación privada. | Excluido; no se usa como clave universal de empresa. |
| E02 | `tipo_organizacion` | Categoría nominal. | Código versionado. |
| E03 | `tamano_organizacion` | Categoría ordinal. | No asignar número exacto de funcionarios. |
| E04 | `sector` | Categoría nominal. | Catálogo de la versión. |
| E05 | `contratacion_reciente` | Sí/No nullable. | No demuestra elegibilidad por sí solo; período de referencia pendiente. |
| E06 | `probabilidad_contratacion` | Expectativa categórica. | No asignar porcentaje de probabilidad ni cantidad de plazas. |
| E07 | `nivel_formacion_demandado` | Nivel principal. | Cardinalidad del borrador; no transformar en años. |
| E08 | `medio_convocatoria` | Medio principal nominal. | Conservar opción, vacío y estado. |
| E09 | `cargos_desempenados` | Selección múltiple. | Una fila principal por envío, relación larga sin multiplicar participantes. |
| E09_OTRO | `otro_cargo` | Texto restringido condicional. | Fuera de matriz ordinaria, codificación revisada posible. |
| E10 | `areas_emergentes` | Texto restringido. | Original fuera de matriz; temas revisados por separado. |
| E11 | `herramientas_tecnologicas` | Texto restringido. | Original fuera de matriz; temas revisados por separado. |
| E12 | `competencias_requeridas` | Texto restringido. | No convertir a puntuaciones de competencias. |
| E13-E22 | `criterio_01` a `criterio_10` | Escala original de empleadores. | Diez valores únicos; `NO_SABE` es nominal explícito sin puntaje. |

La matriz conserva códigos de catálogo, incluido `SI`/`NO` para las preguntas de ese dominio; no convierte ausencias en `false`. Los campos numéricos enteros se convierten sin separadores ni redondeo y las selecciones se ordenan de forma estable. Cualquier indicador o código temático se documenta como transformación distinta del original. Los campos de texto no se publican por defecto, aunque no incluyan un contacto explícito: pueden identificar a personas u organizaciones.

La inclusión ordinaria usa una lista explícita de IDs estructurados revisados por tipo de encuesta. Un campo desconocido no se considera público por ser numérico o tener opciones: queda restringido con clasificación `PRIVACIDAD_NO_REVISADA` y genera `PENDIENTE_REVISION_PRIVACIDAD`/`PENDIENTE_REGLA`. El diccionario puede describir su existencia sin publicar el valor original. Agregar variables al instrumento exige revisar su privacidad y correspondencia, además de registrar una versión nueva.

## Estados y calidad

| Estado de salida | Cuándo corresponde | Valor analítico |
| --- | --- | --- |
| `CON_VALOR` | Valor verificable del dominio de su versión. | Categoría/número válido; incluye No, cero, `NO_SABE` y `NO_PUEDE_VALORAR`. |
| `SIN_RESPUESTA` | Pregunta aplicable vacía con estado registrado. | `null`; no false ni cero. |
| `NO_APLICA` | Condición no cumplida en la definición de su versión. | `null`; no se cuentan valores de un bloque inactivo. |
| `NO_RECOLECTADA` | La definición congelada verificable no contiene esa variable. | `null`; no se exige retroactivamente. |
| `SIN_ESTADO_HISTORICO` | No hay definición o estados registrados suficientes para identificar omisión/salto. | `null` para preparación que necesite esa metadata; preservar captura original y limitación. |
| `VALOR_INVALIDO` | Un error de dominio comprobable impide usar el dato. | `null` y una incidencia, sin adivinar corrección. |

Las incidencias pueden ser `ERROR_DOMINIO`, `ERROR_RELACION`, `ADVERTENCIA` o `PENDIENTE_REGLA`, con regla, campo/grupo, motivo y envío. El alcance se expresa por variable o grupo cuando corresponde; un error no autoriza excluir silenciosamente todo el envío. Una entrada sin metadata verificable genera limitaciones explícitas, no una definición reconstruida del catálogo actual.

Cuando el valor resuelto de una pregunta de control es inválido o carece de estado histórico verificable, el pipeline no utiliza su valor bruto para decidir que una rama no aplica. Los campos dependientes quedan con valor analítico `null` y estado `VALOR_INVALIDO`, con `APLICABILIDAD_NO_VERIFICABLE`/`PENDIENTE_REGLA`; la restricción se propaga a las dependencias encadenadas. La captura conserva sus valores/estados originales, y los campos independientes pueden prepararse. Los temas codificados se incluyen solo si el texto fuente termina con estado válido `CON_VALOR` en esa ejecución.

No se imputan valores ni se convierte un desconocido a «Otro». No se generan edad exacta desde rangos, cuatro empleos desde `MAS_TRES`, funcionarios desde tamaño, plazas desde expectativa ni puntajes desde categorías nominales. La antigüedad de egreso y la conversión de años históricos quedan pendientes de reglas temporales y comparabilidad aprobadas.

## Revisiones y procesamiento

1. Seleccionar origen, modo y corte; congelar el inventario de entradas y registrar estudio, tipo y versiones de sus capturas. La interfaz de este incremento no ofrece un filtro adicional por estudio o versión.
2. Verificar definición, captura, huellas y estados de cada entrada sin reinterpretar históricos con el catálogo actual.
3. Aplicar correcciones versionadas, conservando original y motivo. La ejecución identifica exactamente las revisiones utilizadas.
4. Validar dominios, tipos, unidades y relaciones; conservar las incidencias y los estados.
5. Generar indicadores por opción y relaciones largas. Una pregunta múltiple válida produce 1 para seleccionadas y 0 para no seleccionadas **entre las opciones que esa versión ofrecía**; una opción agregada solo en otra versión produce `null` para la versión anterior, aunque la pregunta tuviera una respuesta válida. Una pregunta vacía/no aplicable/no recolectada/no verificable produce `null`, acompañado de estado.
6. Incorporar categorías temáticas revisadas, asociadas a una versión del libro de códigos y al texto original restringido. Repetir una etiqueta no aumenta el número de participantes que la mencionan.
7. Construir una matriz por tipo, con una fila por envío incluido; comprobar IDs distintos contra el inventario. Las relaciones de opciones/temas no se cruzan mediante un producto que multiplique filas de personas.
8. Publicar atómicamente salidas, diccionario, incidencias y manifiesto con huella lógica. Un fallo no publica una ejecución completa parcial ni reemplaza un conjunto anterior válido.

Las correcciones, clasificación de origen y codificación temática son revisiones posteriores trazables: no sobrescriben el valor observado. La codificación es manual; no se introduce un clasificador de IA ni servicio externo por defecto. Todavía no existe un libro de códigos temático institucional aprobado; un código registrado identifica el trabajo revisado, no una validación automática de su semántica.

Corregir y codificar exige una definición congelada verificable de la pregunta. Una captura histórica sin esa definición puede recibir revisión de origen, pero no una corrección/codificación cuya regla se invente del catálogo vigente. Los valores corregidos respetan dominio, longitud, cardinalidad y aplicabilidad; las relaciones entre variables se comprueban en preparación y producen incidencias si quedan incoherentes. El motivo y actor de la revisión permanecen en el historial privado, separados de la matriz ordinaria.

Una codificación guarda `source_value_hash`, huella del texto efectivo que el administrador revisó. Si una corrección posterior cambia ese texto, los temas anteriores se omiten de nuevas ejecuciones y aparece `CODIFICACION_TEXTO_CAMBIO` con `PENDIENTE_REGLA`; hay que revisar/codificar el nuevo contenido. No se reasigna una interpretación antigua a un texto distinto. Los conjuntos ya publicados conservan la codificación que congelaron y su contexto de entrada.

Cada historial de revisión usa `revision_order`, identidad secuencial de base de datos, para seleccionar la revisión más reciente de forma estable. No se decide entre revisiones empatadas por el orden aleatorio de un UUID. Los instantes se registran en UTC con precisión de microsegundos, compatible con PostgreSQL/H2; la precisión técnica no reemplaza las fechas de referencia metodológica.

## Salidas y reproducibilidad

Las matrices ordinarias se descargan bajo rol ADMIN. Contienen ID interno, estudio, versión y origen para trazabilidad; omiten correo, teléfono, LinkedIn, nombre organizacional y texto original. Ese control no garantiza anonimato de los microdatos y todas las descargas permanecen protegidas.

Las variables usan `q.id`; el estado se identifica como `q.id__estado` y el indicador de una opción como `q.id__CODIGO_OPCION`. La definición, orden y etiquetas se incluyen en el diccionario. JSON mantiene `null` real; los CSV representan ausencias junto a su estado. El CSV original del panel sigue siendo un producto separado y puede contener contactos: no es la matriz analítica ordinaria.

La unión de columnas entre versiones conserva todos los códigos de opciones múltiples registrados en los snapshots incluidos, con catálogos separados por versión. Esta unión estructural no aprueba una homologación semántica: si el significado de una pregunta cambia, D19 requiere documentar su correspondencia y usar un identificador/versión que no confunda medidas diferentes.

Cada ejecución conserva matrices de titulados y empleadores, relaciones largas de selecciones y de temas revisados, incidencias, inventario de incluidos/excluidos con motivos, diccionario y manifiesto. El manifiesto identifica capturas y sus huellas, definiciones, filtros/corte, estudio, origen, modo, reglas y revisiones exactas. La publicación se almacena como un bundle congelado que no se vuelve a construir usando las revisiones actuales al descargarlo.

El manifiesto incluye ID, nombre, año de referencia y estado del estudio de cada entrada verificable. Un histórico sin estudio identificado conserva `SIN_ESTUDIO` y referencia temporal ausente; no hereda automáticamente el año 2026 del estudio nuevo.

La huella SHA-256 del contenido lógico excluye el UUID y el instante técnico de ejecución, pero incluye las entradas exactas, reglas y revisiones que afectan el conjunto. El corte se conserva en el manifiesto, pero cambiarlo sin cambiar miembros ni productos no cambia la huella lógica. Repetir entradas y revisiones debe producir la misma huella y valores. Un nuevo estado de origen, corrección o codificación no cambia un bundle ya publicado; una nueva ejecución obtiene contenido distinguible y trazable.

| Huella | Alcance | Comprobación |
| --- | --- | --- |
| `capture_hash` | Captura lógica aceptada nueva, o representación histórica limitada identificada como tal. | Verificar integridad de la entrada y no atribuir a un histórico contenido no registrado. |
| `definition_hash` | Definición congelada del instrumento. | Verificar que la versión no cambió de contenido. |
| `logical_hash` | Miembros exactos, reglas, metadata y productos lógicos; sin UUID/hora técnica ni corte que no cambie miembros. | Comparar ejecuciones reproducibles y verificar productos contra la huella publicada. |
| `bundle_hash` | Bundle completo publicado, incluido su manifiesto técnico. | Verificar integridad al leer/descargar la ejecución congelada. |

La descarga comprueba la huella del bundle y su contenido lógico antes de servir JSON, CSV o ZIP. Una alteración inconsistente se rechaza; no se publica como conjunto válido ni se regenera silenciosamente desde los datos actuales. Dos ejecuciones reproducibles pueden tener igual `logical_hash` y distinto `bundle_hash` debido a sus metadatos técnicos.

| Producto del bundle | Descarga protegida | Contenido |
| --- | --- | --- |
| Matriz titulados | `titulados.csv` | Una fila por envío incluido de ese tipo. |
| Matriz empleadores | `empleadores.csv` | Una fila por envío incluido de ese tipo; diez criterios únicos. |
| Selecciones largas | `selecciones.csv` | Una opción distinta por pregunta/envío. |
| Temas revisados | `codificacion.csv` | Tema, libro de códigos y revisión, sin texto original. |
| Incidencias | `incidencias.csv` | Regla, gravedad, pregunta y motivo técnico. |
| Calidad | `calidad.csv` | Recuentos por tipo/pregunta/estado; sin presentarlos como indicadores estadísticos finales. |
| Diccionario | `diccionario.csv` | Variables, unidades, privacidad, catálogos y condiciones de cada versión verificable. |
| Inventario | `miembros.csv` | Envíos incluidos/excluidos, motivo, huellas y referencias exactas de revisión. |
| Manifiesto | `manifiesto.json` dentro del ZIP | Identidad, reglas, corte, orígenes, versiones, conteos y huella lógica. |
| Bundle completo | `conjunto.json` | Manifiesto y todos los productos congelados, con `null` JSON real. |

El ZIP `conjunto.zip` contiene los ocho CSV, el manifiesto y el bundle JSON. CSV usa UTF-8 con BOM, comillas escapadas y valores de listas/metadata estructurada serializados como JSON. Las celdas que podrían ejecutarse como fórmulas reciben un apóstrofe de exportación sin alterar los originales. Los CSV vacíos pueden no tener columnas porque se derivan de las filas del producto; el bundle y diccionario conservan el contrato disponible.

### Ampliación para denominadores de fase 4

Las reglas nuevas `2026-10-wrangling-2` agregan dos inventarios al bundle JSON: `coverage` registra presencia en la definición, aplicabilidad verificable, existencia de valor anterior a calidad y estado final por envío/pregunta; `text_reviews` registra la revisión temática y su validez aun cuando se asignaron cero temas. Omiten valores privados y textos originales. Son entradas para distinguir denominadores de estadística; los ocho CSV ordinarios conservan los productos de la tabla anterior.

Los conjuntos anteriores mantienen sus huellas y contenido sin añadir inventarios retroactivos. Si carecen de esa metadata, la fase estadística utiliza `null` para la base desconocida y conserva las bases finales que sí pueden calcularse. Esta ampliación cambia la versión de reglas de las nuevas preparaciones; no modifica las versiones del cuestionario, las capturas originales ni una ejecución ya congelada. Su verificación integrada se registra en [ACEPTACION_ESTADISTICA.md](ACEPTACION_ESTADISTICA.md).

### Ampliación temporal para visualización de fase 5

Las reglas `2026-10-wrangling-3` conservan `coverage` y `text_reviews` y agregan `submitted_at` al objeto de cada miembro en nuevos bundles. Es el instante de recepción conservado por la entrada y verificado en las capturas completas; los históricos conservan la procedencia y limitaciones de su entrada reconstruida. La fecha del miembro forma parte del contenido lógico y de las huellas; no se presenta como una nueva respuesta ni un cambio del instrumento.

El dashboard utiliza exclusivamente el inventario de ese bundle para filtrar períodos, con inicio inclusivo y fin del día inclusivo en `America/La_Paz`. Una ejecución anterior sin fechas de miembros puede consultarse; aplicar ese filtro requiere preparar un conjunto nuevo. No se completa su metadata ni se regeneran sus resultados consultando las respuestas actuales. La preparación nueva constituye otra ejecución con sus miembros y revisiones exactos, sin sobrescribir la anterior.

Esta ampliación no modifica V1–V6 ni necesita una columna nueva: la fecha adicional está en el inventario JSON ya persistido. El contrato visual y su estado de verificación se documentan en [VISUALIZACION.md](VISUALIZACION.md) y [ACEPTACION_VISUALIZACION.md](ACEPTACION_VISUALIZACION.md).

## Modelo físico y migraciones

| Responsabilidad | Implementación física del incremento |
| --- | --- |
| Envío y valores originales | `survey_response` y `survey_answer`, conservadas desde V1. |
| Cuenta administrativa | `administrator`, creada en V2, independiente de los datos analíticos. |
| Estados por pregunta | `survey_question_state`, creada en V3; no se rellena retrospectivamente. |
| Estudio | `survey_study`, V4: ID, nombre, año de referencia, estado e instante de creación. |
| Definición congelada del instrumento | `instrument_snapshot`, V4: clave encuesta/versión, estudio, estado, JSON de definición y su SHA-256. |
| Captura nueva y huella | `response_capture`, V4: FK al envío y al snapshot, estudio, SHA de definición, JSON de captura, SHA de captura y procedencia. `CAPTURA_LOGICA` identifica la captura aceptada nueva. |
| Ejecución y bundle publicado | `wrangling_run`, V5, con huella lógica, huella del bundle completo y JSON congelado. |
| Inventario incluido/excluido y revisiones exactas | `wrangling_member`, V5. |
| Correcciones con motivo/actor | `wrangling_correction`, V5, historial con `revision_order` sin sobrescribir originales. |
| Revisiones de origen | `wrangling_origin_classification`, V5, historial con `revision_order`. |
| Codificación manual, libro de códigos y revisión | `wrangling_text_coding`, V5, historial con `revision_order` y `source_value_hash`. |

V4/V5 agregan responsabilidades; no se modifica V1-V3 ni se inventa metadata pasada para sus respuestas. La FK de captura a envío conserva su vinculación; el snapshot verifica que encuesta, versión y estudio sean coherentes. Las pruebas deben comprobar actualización desde una base anterior con valores conservados, huellas verificables en nuevos envíos y limitaciones explícitas de históricos.

## Uso del panel y pendientes

`/admin/datos` permite preparar y descargar conjuntos protegidos. El detalle `/admin/respuestas/{id}` enlaza a `/admin/respuestas/{id}/revision` para revisar origen y registrar correcciones/codificación con motivo. Las mutaciones requieren autenticación ADMIN, CSRF y validación del backend; leer un ID de respuesta no concede permiso de consulta.

| Operación | Ruta protegida |
| --- | --- |
| Listar o crear conjunto | GET/POST `/admin/datos` |
| Consultar ejecución | GET `/admin/datos/{id}` |
| Descargar JSON o ZIP | GET `/admin/datos/{id}/conjunto.json` o `/conjunto.zip` |
| Descargar producto CSV | GET `/admin/datos/{id}/{producto}.csv`, con los nombres de la tabla anterior sin extensión repetida. |
| Revisar respuesta | GET `/admin/respuestas/{id}/revision` |
| Clasificar origen | POST `/admin/respuestas/{id}/origen` |
| Registrar corrección | POST `/admin/respuestas/{id}/correcciones` |
| Registrar temas | POST `/admin/respuestas/{id}/codificacion` |

La fecha de corte vacía significa el instante de creación; un instante con zona se interpreta como UTC equivalente y una fecha/hora local de formulario usa `America/La_Paz`. El corte se aplica a recepción de respuestas; las revisiones utilizadas se congelan según las registradas al crear la ejecución, no se presentan como una reconstrucción retrospectiva de revisión al momento del envío.

Las reglas metodológicas pendientes permanecen visibles: elegibilidad UMSS y varias participaciones D13; unidad de análisis y varios representantes de una organización D16; libro de códigos temático D18; comparabilidad semántica entre versiones D19; referencia de egreso y decisiones estadísticas. El cruce T38×T37 debe mantener financiamiento/nivel futuro separado de T06×T04.

La fase 4 desarrolla estadística univariada/bivariada, denominadores y condiciones de chi-cuadrado desde conjuntos congelados; su contrato está en [ESTADISTICA.md](ESTADISTICA.md). La fase 5 incorpora gráficos desde esos resultados y selecciones verificadas; su ejecución técnica se distingue de la preparación en [VISUALIZACION.md](VISUALIZACION.md). Los reportes automatizados de HU5 deberán consumir un conjunto, análisis y selección identificados; Excel y simulación mantienen su alcance futuro y no se declaran implementados por generar un bundle o mostrar un gráfico.
