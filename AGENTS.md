# Instrucciones del proyecto

## Alcance y fuentes

Aplicación monolítica modular de estudio de mercado de Ingeniería en Sistemas UMSS. El usuario autorizó comenzar el desarrollo, superando el alcance documental del archivo de referencia `docs/01_ESTABLECER_REPOSITORIO.md`.

Consultar `docs/DECISIONES.md` y `docs/INSTRUMENTO.md`. Preservar los archivos existentes y el PDF original. No incorporar al historial datos de campo, fuentes privadas ni secretos.

Mantener los dos entregables finales: documento de Análisis de Mercado y herramienta de recolección de datos. El alcance completo comprende dos cuestionarios, Data Wrangling, estadística univariada y bivariada, pruebas chi-cuadrado con comprobación de aplicabilidad, visualización computacional (incluidos mapas de calor y radar), automatización de reportes en el backend, exportaciones CSV/Excel y experimentación por simulación con un modelo definido.

## Decisiones vinculantes

- Encuestas públicas; respuestas, reportes, exportaciones y administración protegidos por autenticación en el backend.
- Los diez criterios de empleadores aparecen una sola vez en el instrumento y en los análisis.
- Conservar las escalas observadas. `No sabe` es una categoría propia, no un puntaje de acuerdo.
- Documentar las ambigüedades sin afirmar que una propuesta está aprobada.
- Distinguir datos observados, valores derivados y simulaciones. Cada porcentaje debe tener un denominador explícito.

## Desarrollo

Java 21, Spring Boot, Thymeleaf, Maven y PostgreSQL, con módulos por responsabilidad. H2 se utiliza exclusivamente para desarrollo local y pruebas.

Trabajar por incrementos ejecutables. Verificar validación en servidor, autorización, CSRF, persistencia y ausencia de duplicaciones. Las plantillas deben escapar los datos proporcionados por encuestados.

Usar Flyway para cambios de esquema. No modificar una migración ya aplicada en un entorno compartido. Mantener trazabilidad del instrumento que produjo cada respuesta.

No anunciar análisis, reportes o simulación terminados antes de implementarlos y verificarlos. No añadir licencia sin decisión del equipo.

Revisar `git status`, diferencias y archivos seleccionados antes de cada commit. Conservar `origin` y el historial remoto; no hacer push forzado. La rama principal es `main`.
