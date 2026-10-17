# Aceptación técnica del generador de reportes

Fecha: 2026-10-17. Los ocho incrementos del [plan del generador](GENERADOR_REPORTES_PROPUESTA.md) están implementados y verificados dentro del alcance descrito. La maqueta permanece como referencia visual; el recorrido real funciona en el monolito.

## Recorrido comprobado

`LiveResultsIntegrationTest.customizedReportJourneyReusesTemplateOnNewCutAndKeepsEveryExportFrozen` encadena solicitudes HTTP con Spring MockMvc y persistencia real en la base de pruebas, sin una transacción de prueba que oculte los límites de guardado:

1. Consulta Resultados y guarda su corte con el cruce seleccionado y su huella.
2. Abre el generador de ese análisis. Solicita la vista previa de una configuración con títulos, comentarios y varias representaciones por pregunta y cruce. La vista previa no crea reportes ni plantillas.
3. Genera mediante el endpoint de personalización, marcando guardar plantilla y especificando un nombre. Comprueba el escape de textos y la ausencia de contactos en el documento.
4. Descarga JSON, CSV y Excel. Comprueba autenticación, autorización ADMIN, CSRF en la generación y `Cache-Control: no-store`.
5. Reabre el reporte para generar otro y conserva su presentación.
6. Recibe una respuesta nueva y guarda otro corte de la misma versión.
7. Aplica la plantilla al corte nuevo: conserva representaciones, elimina comentarios particulares y utiliza cruces del nuevo conjunto. Genera sin guardar otra plantilla y comprueba dos envíos en el nuevo documento.
8. Descarga de nuevo JSON, CSV y Excel del primer reporte: sus bytes son idénticos a los anteriores.

Las pruebas de `GenericPreparationIntegrationTest` complementan este recorrido con compatibilidad de gráficos, fuentes y huellas congeladas, vista previa sin escrituras, creación concurrente de cruces sin duplicarlos, plantillas opcionales, integridad, XSS y formatos de reporte V1–V6. `ReportAdvancedProjectionTest` comprueba histogramas, cuartiles, meses vacíos, pares numéricos y bases del radar con `No sabe` separado.

## Comprobaciones de cierre

| Comprobación | Resultado |
| --- | --- |
| Suite Java completa con H2 | 547 pruebas, sin fallos, errores ni omisiones |
| `LiveResultsIntegrationTest` con PostgreSQL local | 15 pruebas, incluido el recorrido nuevo; sin fallos ni errores |
| Pruebas JavaScript de dashboard y gráficos de reportes | 16 correctas |
| Generador en Chrome a 360, 768 y 1280 px | 27 vistas, siete gráficos simultáneos, sin desbordamientos ni excepciones |
| Aplicación de plantillas y error de incompatibilidad en Chrome | Seis vistas; conserva configuración y aviso de error tras actualizar la vista previa |
| Documento y exportaciones en Chrome | Seis vistas, 14 descargas PNG/SVG y PDF real; sin excepciones |

Se corrigió el aviso de aplicación de plantilla: tiene su propia región de estado y no desaparece cuando se actualiza la vista previa. La generación permanece bloqueada mientras se aplica una plantilla.

Los navegadores usan HTML y respuestas de vista previa producidos por las pruebas Java, servidos localmente con los recursos y política de contenido del proyecto. Verifican interacción, representación y descargas; la persistencia y el control de acceso se verifican por HTTP en las pruebas Java. No se presenta esto como una prueba de navegador conectada al despliegue remoto.

Evidencia temporal excluida de Git: `.codex-tmp/step8-full.log`, `.codex-tmp/step8-postgres.log`, `.codex-tmp/report-step7-browser/` (incluye la comprobación visual del paso 8) y `.codex-tmp/report-step6-browser/`.

Para repetir las comprobaciones versionadas desde la raíz:

```powershell
mvn test
node --test src/test/js/dashboard.test.js src/test/js/report-charts.test.js
```

El perfil de pruebas usa H2 por defecto. PostgreSQL requiere la base local de pruebas configurada mediante `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME` y `TEST_DATABASE_PASSWORD`; no utilizar una base de producción para esta suite.

## Límites de la entrega

PDF se obtiene mediante impresión del navegador; PNG/SVG son descargas individuales y CSV/Excel contienen resultados agregados. Las representaciones dependen de los datos y la metadata disponibles. Una plantilla requiere la misma definición del instrumento; sus cruces se reconstruyen como exploratorios y no trasladan aprobaciones de independencia.

La revisión técnica no sustituye pruebas con usuarios finales, una auditoría completa de accesibilidad ni aprobación institucional del instrumento. Tampoco declara terminados el documento de Análisis de Mercado o la experimentación por simulación. No se realizó commit, push, despliegue ni modificación de Neon durante este cierre. Las migraciones V16 y V17 deben aplicarse mediante Flyway cuando se despliegue esta versión.
