# Paso 5 local: descriptivos y gráficos generales

**Actualización:** los [cruces y chi-cuadrado generales](CRUCES_GENERALES.md) se incorporaron en un incremento posterior separado. Los límites sobre cruces en este documento describen el alcance inicial de descriptivos.

Fecha: 2026-10-14. Autorizado por el usuario con «luz verde». Continúa la preparación por encuesta y versión; permanece local, sin commit, push ni despliegue.

## Cómo probarlo

Reinicia la aplicación desde la raíz:

```powershell
mvn "-Dspring-boot.run.profiles=dev" spring-boot:run
```

Este recorrido usa H2 local. No combines `local` si ese perfil conecta a Neon.

1. Abre el panel y entra a **Preparar datos de encuestas nuevas**.
2. Abre un conjunto guardado, o prepara uno siguiendo [PREPARACION_GENERAL.md](PREPARACION_GENERAL.md).
3. En **Analizar este conjunto**, pulsa **Guardar análisis descriptivo**.
4. Consulta sus tablas, bases, resúmenes numéricos y gráficos. **Conjunto fuente** vuelve a los datos exactos usados; **Resultado JSON** permite consultar el resultado completo.
5. **Ver análisis generales guardados**, desde un conjunto, abre el historial de análisis.

## Cálculos y bases

El protocolo `DESCRIPTIVOS_GENERALES_V1` analiza todas las variables incluidas en la configuración del conjunto. No consulta respuestas actuales ni mezcla versiones.

- Categorías nominales: conteos por opción y porcentaje sobre envíos con valor en esa pregunta. Incluye opciones con conteo cero. No asigna puntajes, orden ni medias a categorías como `NO_SABE`.
- Selección múltiple: conteo de participantes por opción y porcentaje sobre participantes que respondieron; puede sumar más de 100%. Por separado conserva total de menciones y porcentaje de cada opción sobre esas menciones.
- Magnitudes enteras: mínimo, máximo, media, mediana, cuartiles tipo 7, rango intercuartílico y dispersión muestral con divisor `n−1`, mediante las funciones numéricas existentes. La pantalla muestra mínimo, máximo, media, mediana y desviación; el JSON incluye el resto.
- Cobertura: total, con valor, sin respuesta y no aplica. Base aplicable = con valor + sin respuesta. Porcentaje de respuesta = con valor / base aplicable × 100.

Cero sigue siendo valor. Sin base, el porcentaje es `null`, mostrado como «Sin base»; no se convierte en 0%. Sin valores numéricos, las medidas quedan ausentes. Una sola observación permite media y mediana, pero no dispersión muestral.

Los gráficos de categorías muestran conteos de participantes. Los numéricos muestran mínimo, mediana y máximo con su unidad: son medidas de resumen, no tres observaciones. Las cifras están también en tablas/resúmenes HTML. El backend calcula; ECharts local dibuja. Las etiquetas se escapan y los tooltips usan texto enriquecido sobre canvas, sin HTML de respuestas.

## Conservación, acceso y límites

V11 añade `survey_general_analysis` sin cambiar V1–V10 ni datos existentes. Cada análisis guarda conjunto fuente exacto, huella lógica de origen, actor, fecha, resultado y huella. Al abrirlo se verifica el conjunto congelado, la integridad del resultado y su correspondencia con el cálculo del protocolo registrado.

El resultado conserva estudio, encuesta/versión, definición/configuración por huella, origen, modo y fechas. El conjunto fuente mantiene miembros, revisiones, estados y aprobación observada. Cambiar el origen de una respuesta, recibir nuevos envíos o activar otra versión no cambia un análisis anterior; para usar nuevos datos se prepara otro conjunto y otro análisis.

Todas las rutas requieren ADMIN; guardar exige CSRF y las salidas no se almacenan en caché. Si falla la carga del gráfico, las tablas siguen disponibles. La publicación técnica no otorga aprobación institucional, elegibilidad, independencia o comparabilidad poblacional.

Este incremento implementa descriptivos generales. Cruces, chi-cuadrado general con comprobación de aplicabilidad, filtros/comparaciones adicionales, CSV estadístico, plantillas de reportes y exportaciones Excel/PDF continúan pendientes. El protocolo original UMSS y su dashboard conservan sus rutas y formato.

## Verificación

La suite completa pasó **413 pruebas Java en H2 y 413 en la ejecución con PostgreSQL 18 local**, sin fallos ni omisiones. La prueba de persistencia también recupera el análisis después de cerrar y reabrir la base. Las verificaciones usaron datos ficticios y PostgreSQL temporal; no se conectó a Neon ni se realizó commit, push o despliegue.

Se comprobaron vectores numéricos conocidos, bases de selección múltiple, conservación de `NO_SABE`, cero, ausencia y una sola observación; resultados congelados ante cambios posteriores; rechazo de huellas alteradas; ADMIN, CSRF y escapes. Chrome cargó tres gráficos reales en 360, 768 y 1280 píxeles, con tablas equivalentes, sin desbordamiento ni excepciones JavaScript, usando fixtures ficticias. Esta comprobación no es una auditoría completa de accesibilidad.
