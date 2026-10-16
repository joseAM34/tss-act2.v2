# Resultados actualizados · Rediseño, paso 4

Fecha: 15 de octubre de 2026. El usuario autorizó continuar con «luz verde» después del asistente de importación. Este incremento actualiza resultados desde el backend y permite conservar el corte que se está viendo, sin crear conjuntos por cada consulta automática.

## Cómo usarlo

Reinicia la aplicación con tu comando Maven habitual y entra a **Mis encuestas → encuesta creada con el editor → versión publicada o cerrada → Resultados**.

1. Si todavía no hay variables configuradas, pulsa **Elegir variables y unidades**. Revisa una vez qué variables pueden analizarse y sus unidades. Guarda la configuración. No hace falta preparar y guardar un conjunto en cada consulta posterior.
2. Elige **Origen** y, si corresponde, fechas de recepción inclusivas en `America/La_Paz`. Por defecto se muestran los envíos **sin verificar**, separados de los reales y sintéticos.
3. Opcionalmente elige dos preguntas nominales de selección única para un cruce. Pulsa **Aplicar selección**.
4. Se muestran tablas, gráficos, bases, protocolo y fecha de corte. La consulta se repite cada **30 segundos** mientras la pestaña esté visible. También puedes **Actualizar ahora** o desmarcar la actualización automática.
5. Al cambiar un filtro, la vista mantiene la selección anterior y pausa consulta/guardado hasta aplicar la nueva selección. El navegador no suma porcentajes ni recalcula estadísticas.
6. Pulsa **Guardar este corte** para conservar las cifras que estás viendo. Aparecerán enlaces al análisis guardado y, si lo elegiste, al cruce. Desde el análisis puedes generar un reporte con las herramientas existentes.

Una versión sin envíos muestra un estado vacío; los porcentajes sin base y las medidas ausentes no se rellenan con cero. Los datos sintéticos mantienen su etiqueta. Si falla una consulta, siguen visibles las últimas tablas y su corte, con aviso de **Vista desactualizada**; el guardado se deshabilita hasta verificar de nuevo.

La hora de verificación puede avanzar aunque no haya cambios. Si las huellas no cambiaron, se conservan el contenido y corte mostrados. Las consultas automáticas se suspenden al ocultar la pestaña y se reanudan al volver si la opción sigue activa. Las respuestas fuera de orden no sustituyen una consulta más nueva.

## Qué se calcula

- Descriptivos de las variables configuradas: frecuencias, bases por pregunta, menciones en selección múltiple y medidas numéricas con unidad declarada.
- Gráficos de conteos o de mínimo/mediana/máximo, con tablas/resúmenes equivalentes. Son representaciones de cifras calculadas por Java.
- Para dos preguntas elegidas: tabla de observadas, porcentajes por fila con su base y mapa de calor. Los pares excluidos conservan su diagnóstico.

El cruce actualizado **no declara independencia automáticamente**. Por ello no ejecuta inferencia ni muestra una p o V de Cramér artificial. Guardar el corte permite abrir los cruces del conjunto y registrar/revisar la evidencia metodológica usando el flujo existente. No se reutiliza sin revisión evidencia de independencia de otros conjuntos. La explicación visual y estadística ampliada corresponde al paso 5.

`NO_SABE` y `NO_PUEDE_VALORAR` conservan su tratamiento nominal cuando están incluidos; no se convierten en puntajes. Una fila sigue siendo un envío, sin afirmar independencia de personas/empresas ni representatividad poblacional. Esta vista siempre es exploratoria `BORRADOR`, incluso con origen real.

## Captura, integridad y guardado

`GenericPreparationService.current` construye una preparación transitoria en una transacción de lectura `REPEATABLE_READ`. Usa la configuración vigente, definición/publicación, capturas verificadas, revisiones de origen y referencias de importación. Mantiene una selección exacta de versión, origen y fechas; no reconstruye históricos ni consulta respuestas actuales para modificar conjuntos guardados.

El resultado incluye huellas de fuente, descriptivos, cruce y cuadro completo, configuración, protocolo y corte. La API devuelve el HTML escapado de las tablas y las cifras de los gráficos procedentes de ese mismo cálculo. No se realizan dos lecturas independientes para dibujar tabla y gráfico.

Cada GET es de lectura: no guarda conjuntos, análisis, cruces ni configuraciones. La configuración tampoco se aprueba por visitar la pantalla. Los contactos y texto original siguen excluidos de los resultados ordinarios según la configuración validada y las reglas existentes.

El POST de guardado recibe selección, huella y corte mostrados. Reconstruye y verifica ese corte, incluyendo las revisiones de origen vigentes hasta esa fecha. Las respuestas recibidas después del corte no se incorporan al documento que se estaba viendo. Si cambia la configuración o no coincide la huella, rechaza la operación sin sustituir silenciosamente el resultado.

Se guarda un bundle inmutable con corte de datos y fecha técnica de creación diferenciados. Un bloqueo de versión serializa guardados simultáneos. Repetir el mismo corte/selección reutiliza conjunto, análisis y cruce ya verificados; no crea duplicados por doble clic. Un corte explícitamente distinto puede producir otra copia conservada. Los reportes anteriores siguen leyendo sus fuentes congeladas y no cambian al llegar nuevas respuestas.

## Alcance y acceso

Este incremento trabaja sobre versiones publicadas desde el editor, que ya tienen configuración de preparación general. Las versiones cerradas permiten consultar sus datos y revisiones sin admitir nuevos envíos. Los borradores no se analizan.

Los cuestionarios originales UMSS conservan su adaptador y dashboard por conjuntos del estudio, que pueden abarcar varias encuestas/versiones. No se atribuye a una versión una actualización automática que ese adaptador aún no implementa. La pantalla mantiene sus accesos identificados al protocolo del estudio. No se migran ni se duplican respuestas originales para habilitar este flujo.

Rutas nuevas, todas ADMIN y sin caché:

- `GET /admin/espacio/{slug}/resultados/actuales`: cuadro actualizado y HTML verificado.
- `POST /admin/espacio/{slug}/resultados/guardar`: conservación del cuadro mostrado, con CSRF.

La vista HTML inicial y la aplicación de filtros también funcionan sin JavaScript; la consulta periódica, gráficos interactivos y botón de guardado integrado usan JavaScript. Los flujos manuales de preparación y análisis continúan disponibles. Un fallo de sesión, red o integridad no autoriza a mostrar otra selección ni reemplazar el último cuadro por una respuesta parcial.

No hay dependencias ni migraciones nuevas. Se mantienen originales, huellas y formatos históricos. No se implementan pronósticos/simulaciones, aprobación oficial ni exportaciones PDF/PNG/SVG/Excel nuevas. El paso 6 conserva esas exportaciones. Trabajo local, sin commit, push, despliegue ni conexión a Neon.

## Verificación

Suite completa: **514 pruebas Java en H2 y 514 en la ejecución con PostgreSQL 18 temporal**, sin fallos ni omisiones. Incluye 12 pruebas nuevas: consultas sin escrituras, cambios al recibir respuestas, orígenes separados, fechas inclusivas, ceros, corte exacto, reintentos y guardado concurrente sin duplicados, cambios de configuración, cruces sin independencia inferida, variables pendientes, integridad y ADMIN/CSRF. Pasaron **11 pruebas JavaScript**, incluidas dos nuevas de respuestas obsoletas/invalidadas.

Chrome comprobó los estados con resultados, cruce, selección vacía y configuración pendiente en 360, 768 y 1280 píxeles: **12 vistas**, sin desbordamiento ni excepciones JavaScript. Usó HTML real generado por Thymeleaf, recursos locales y CSP del backend. Verificó sustitución por un cuadro nuevo, conservación de tablas tras un error, recuperación, envío de huella/corte/CSRF al guardar y pausa al editar filtros. La prueba de navegador usa fixtures ficticias y respuestas controladas; las operaciones reales se prueban en Java. No es una auditoría completa de accesibilidad.

`git diff --check` sin errores. PostgreSQL usa una base temporal en loopback y mínimo de conexiones ociosas 0 solo para la orden de pruebas; no se modifica la configuración de la aplicación ni la base del usuario.
