# Ayudas para entender resultados — paso 5

Implementado el 15 de octubre de 2026 en el espacio de encuestas del editor: resultados actualizados, análisis generales guardados, cruces generales y reportes HTML. Los cuestionarios originales conservan el dashboard del estudio y sus explicaciones existentes. No se modifica su protocolo ni se añaden pronósticos.

Cada pregunta incluye un recuadro «Cómo leer este resultado». Explica qué representan las barras, las bases de los porcentajes y la diferencia entre envíos y menciones. Las preguntas numéricas explican media, mediana, extremos y desviación estándar, con ayuda desplegable. Los estados sin respuesta, no aplica y sin datos se distinguen de cero; las categorías de desconocimiento no se convierten en faltantes. La unidad sigue siendo un envío, sin suponer personas o empresas únicas.

Los cruces incluyen lectura del mapa de calor, porcentajes de fila/columna/total y frecuencias esperadas. Una lista textual muestra datos disponibles, variación, evidencia de independencia, ceros estructurales y suficiencia de frecuencias. Cada condición se lee directamente del resultado del backend: no se ejecutan cálculos estadísticos nuevos en el navegador ni se alteran huellas o fuentes congeladas.

Cuando no se ejecuta chi-cuadrado, se explica el requisito pendiente sin afirmar ausencia de asociación. Cuando se ejecuta, la explicación sigue la decisión registrada: rechazar independencia no demuestra causalidad; no rechazarla no demuestra independencia. La ayuda define p, alpha, grados de libertad y V de Cramér, sin etiquetas universales de intensidad. Las restricciones metodológicas siguen vigentes; se informa cómo revisarlas en vez de forzar una prueba.

El componente compartido es `templates/fragments/statistical-help.html`; usa HTML del servidor, funciona sin JavaScript y conserva tablas equivalentes. El estilo tiene texto explícito y no depende solo del color. Las ayudas de la aplicación no pasan a formar parte del contenido estadístico congelado de los reportes: las cifras y decisiones conservadas no cambian.

## Verificación

- 71 pruebas de integración de resultados actuales, preparación, análisis, cruces y reportes correctas en H2.
- Cuatro pruebas adicionales del renderizado de explicaciones: ausencia de evidencia, tablas vacías/escasas, ambas decisiones de una prueba ejecutada y ceros estructurales.
- Chrome: 12 vistas de resultados, cruce, selección vacía y configuración pendiente a 360, 768 y 1280 píxeles, sin desbordamientos de página ni excepciones. También se verificaron actualización, recuperación de errores, guardado del corte y pausa por filtros pendientes.
- `git diff --check` correcto. No se repitió PostgreSQL: este paso cambia presentación y pruebas, sin cambios de esquema o cálculo.

Evidencia local: `.codex-tmp/help-targeted.log`, `.codex-tmp/help-explanations.log` y `.codex-tmp/live-browser/verification.json`. La comprobación técnica no sustituye una prueba de comprensión con usuarios ni una auditoría de accesibilidad con lector de pantalla.

El paso 6 queda pendiente: exportaciones PDF e imágenes del resultado previsualizado. No hay commit, push ni despliegue.
