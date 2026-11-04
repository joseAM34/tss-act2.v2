# Resultados e informes: análisis ampliado de una variable

Fecha: 2026-11-04. El usuario solicitó mostrar en Resultados e informes las tablas y medidas ilustradas en su PDF de calidad y validación. El documento es una referencia de presentación; no concede aprobación metodológica ni cambia las preguntas, escalas o reglas de campo del estudio. No se copiaron sus datos de ejemplo a la base.

## Alcance

Los nuevos resultados del espacio por encuesta y las nuevas vistas previas/informes personalizados incluyen un resumen ampliado para variables configuradas como `MAGNITUD_ENTERA` con unidad. Las categorías y coberturas mantienen sus distribuciones originales: no se calculan medias, percentiles o acumulados sobre códigos nominales. Los textos originales y contactos mantienen sus restricciones.

El resumen contiene mínimo, máximo, media, mediana/Q2/P50, moda o modas, varianza y desviación estándar muestrales, rango, Q1/P25, Q3/P75, rango intercuartílico, P90 y coeficiente de variación cuando procede. Cada medida indica unidad; la varianza usa unidad al cuadrado. Los valores no definidos aparecen como ausentes, no como cero.

Por indicación posterior del usuario, las medidas se presentan en tres tablas: tendencia central, dispersión y posición, con columnas **Medida, Fórmula, Resultado y Unidad**, sin columna de interpretación. Las fórmulas se muestran como texto compatible con HTML/PDF y Word editable; la interpolación tipo 7 se explica en una nota común. `UnivariatePresentation` organiza y formatea los valores ya congelados, sin recalcular estadísticas ni cambiar la proyección guardada. Las tablas de frecuencias e intervalos se conservan. Esta presentación requiere que la variable tenga la proyección numérica compatible; las categorías de rangos de edad no se convierten en edades exactas.

Se incluyen dos tablas: frecuencias por valor observado (`xi`, `fi`, `hi`, porcentaje, `Fi`, porcentaje acumulado) y frecuencias por intervalos (límites, marca de clase y las mismas frecuencias). La base de ambas es el número de valores numéricos con respuesta, excluyendo faltantes y no aplica. Los intervalos conservan la regla de Sturges existente: techo de `1+log2(n)`, máximo 20, amplitud igual, cierre `[inferior, superior)` y último intervalo cerrado; un valor constante genera un intervalo puntual. Los valores y las marcas de clase se ordenan numéricamente.

## Convenciones

- Varianza y desviación muestrales: divisor `n−1`; no se estiman con menos de dos datos.
- Cuartiles y P90: interpolación tipo 7. Q2 corresponde a la mediana.
- Moda: todos los valores de frecuencia máxima cuando esa frecuencia es mayor que uno. Sin repeticiones se informa que no hay moda; no se selecciona arbitrariamente un valor.
- CV: `100·s/media`, solo con `n≥2`, media positiva y unidades reconocidas de duración/edad con cero significativo: segundos, minutos, horas, días/dias, meses, años, años completos, meses completos o días/dias completos. Año calendario y unidades no reconocidas quedan sin CV. No se deduce una escala de razón de cualquier texto de unidad ni se añaden umbrales de calidad o variabilidad.
- El resumen no determina representatividad, calidad del servicio, causalidad o atípicos. La caja existente conserva bigotes mínimo/máximo y su aclaración; no se convierte en una regla de detección de atípicos del ejemplo PDF.

Se añaden polígono de frecuencias (marca de clase y `fi`) y ojiva agrupada (límite superior y `Fi`, con punto inicial de acumulado cero). Ambos usan exclusivamente puntos calculados en el backend. No se confunden con la línea temporal de recepción. Histograma y caja continúan disponibles según sus condiciones.

## Fuentes congeladas y exportaciones

La ampliación utiliza `univariate_projection_version=1`, separada del protocolo descriptivo histórico. `ReportAdvancedProjection.enrich` conserva su variante anterior y ofrece una variante explícita para la ampliación. Los nuevos cortes registran el marcador y se verifican con la misma variante; los cortes antiguos conservan la anterior. Los análisis persistidos y sus huellas no cambian.

Los nuevos reportes personalizados guardan el marcador y su `display_result` ampliado. La lectura verifica el marcador y reconstruye la misma proyección desde el conjunto congelado. Los reportes antiguos no reciben campos nuevos ni se recalculan con reglas nuevas. Recibir más encuestas no altera las frecuencias ni medidas del reporte guardado. Para obtener la ampliación desde un informe histórico se genera otro con sus fuentes.

La pantalla de Resultados y el HTML/PDF del informe comparten un fragmento de tablas. Word contiene medidas y tablas editables, además de líneas nativas con datos incorporados para polígono y ojiva. CSV/Excel/JSON conservan los escalares y filas de la proyección con denominadores y referencias. El ancho/orientación de las tablas exportadas utiliza las reglas de impresión ya existentes.

No se cambian migraciones, credenciales, datos de campo ni aprobaciones institucionales. Los comentarios y conclusiones siguen siendo textos administrativos revisables; no se fabrican conclusiones a partir de las cifras del ejemplo.

## Verificación

La presentación posterior con fórmulas pasó 30 pruebas Java de resumen, Resultados/informes y Word. La comprobación en Chrome verificó los tres grupos, la fórmula de media y ausencia de la cabecera Interpretación, y volvió a generar el PDF. Los valores congelados permanecen iguales; la agrupación se realiza al representar el documento.

Pasaron 118 pruebas Java de las suites `UnivariateSummaryTest`, `ReportAdvancedProjectionTest`, `ReportExportTest`, `ReportWordExportTest`, `ReportTemplateManagementIntegrationTest`, `GenericPreparationIntegrationTest` y `WorkspaceResultsIntegrationTest`. Cubren valores conocidos, frecuencias y acumulados, muestra vacía/un dato, ceros, modas empatadas, rechazo de no finitos, exclusión de no aplica, CV no definido para año calendario, presentación HTML, Word y exportaciones, conservación tras nuevos envíos y lectura del contrato anterior de reportes V6.

Pasaron 15 pruebas JavaScript de gráficos y resultados, con comprobaciones específicas de los puntos recibidos para polígono y ojiva y su disponibilidad por tipo. Una ejecución en Chrome con fixtures comprobó las tablas de Resultados e informe, la ojiva con base congelada, el polígono con cifras del backend y la generación PDF sin errores de JavaScript. Los artefactos sintéticos se encuentran en `.codex-tmp/univariate-browser`, ignorado por Git. El DOCX se verifica estructuralmente y por contenido; no se realizó una revisión visual nueva en Microsoft Word. Sin commit, push ni despliegue.
