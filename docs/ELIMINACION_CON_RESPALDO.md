# Eliminación de encuestas con respaldo

Desde **Gestionar encuesta**, una encuesta vacía se elimina con confirmación. Cuando tiene datos, se ofrecen dos acciones:

1. **Descargar respaldo ZIP** prepara y descarga una copia completa de sus datos propios. La encuesta sigue disponible.
2. Después de comprobar que el archivo se guardó completo, marcar la confirmación y pulsar **Eliminar definitivamente**.

No se pide escribir una justificación. El botón de confirmación aparece cuando el navegador recibió el archivo; el administrador confirma que se guardó. El servidor no puede comprobar la escritura en el disco del usuario. Sin JavaScript, descargar el ZIP y volver a cargar la pantalla permite continuar con la misma confirmación.

## Contenido del ZIP

- `manifest.json`: formato `RESPALDO_ENCUESTA_V1`, fecha, identidad de la encuesta, cantidades, huella completa y lista de originales con sus huellas y nombres de entrada.
- `datos.json`: filas originales de la encuesta y sus versiones, estructura, eventos, instrumentos congelados, respuestas, estados, capturas, correcciones, clasificación de origen, codificaciones, lotes y correspondencias, recibos de importación, libros Excel, configuraciones, conjuntos preparados, análisis, cruces y reportes. Incluye el contexto de estudio y las revisiones exactas de plantillas utilizadas.
- `respuestas.csv`: copia de lectura larga, con una fila por valor o estado sin respuesta, sin aplanar selecciones múltiples. Los textos se protegen contra fórmulas al abrirlos en una hoja de cálculo. Los valores originales sin esa adaptación siguen íntegros en JSON.
- `originales/`: bytes exactos de los CSV/XLSX recibidos, incluidos lotes pendientes y libros con hojas no importadas. Los nombres internos usan identificadores; el manifiesto conserva el nombre original y la selección de hoja está en los datos del lote.

Se comprueban las huellas de los originales antes de entregar el ZIP. El conjunto de entradas y el ZIP tienen un límite de 64 MiB; si lo superan se rechaza la preparación sin borrar datos. Este es un respaldo para consulta y recuperación técnica; el importador de respuestas no restaura automáticamente toda la encuesta, sus productos e historial.

## Confirmación y concurrencia

Las dos acciones requieren ADMIN y CSRF. Una constancia preparada sirve solo para la cuenta que la solicitó, la encuesta y la revisión exactas, durante una hora y una única eliminación. No se confía únicamente en cantidades: se vuelve a calcular la huella de las filas y del contexto incluidos. Una nueva correspondencia, revisión, clasificación, respuesta o producto obliga a descargar otro respaldo, aunque el número de envíos no cambie.

Se bloquea la raíz, sus versiones y los padres de respuestas, importaciones y productos mientras se prepara o elimina. Se comprueban las pertenencias y se rechazan referencias incoherentes entre encuestas. El borrado recorre las dependencias propias antes de quitar versiones y raíz. Toda la operación, incluida la constancia administrativa y el consumo del respaldo, se confirma en una transacción.

La descarga se completa en memoria antes de escribir la respuesta HTTP y de habilitar la confirmación en el navegador. Un error de red no elimina datos. Si el guardado del ZIP fue cancelado, hay que volver a descargarlo antes de confirmar.

## Recursos compartidos y constancia

No se eliminan el estudio, cuentas, plantillas reutilizables ni auditoría. Los conjuntos históricos de wrangling pueden contener miembros de varias encuestas; sus bundles, miembros y análisis estadísticos permanecen congelados. El ZIP enumera sus identificadores y huellas como referencias conservadas, sin exportar las respuestas ajenas. Esto evita alterar resultados de otras encuestas o romper su trazabilidad. Los conjuntos y productos generales propios de la encuesta eliminada sí se incluyen en el respaldo y se retiran de la base.

V20 crea `survey_deletion_backup` sin FK hacia la encuesta. Guarda cuenta, identificadores, fechas y huellas, sin almacenar el ZIP ni respuestas. El evento `ELIMINAR` conserva identificador/huella del respaldo y cantidades; no copia respuestas a la auditoría. La eliminación de encuestas vacías sigue usando el recorrido anterior.

Las verificaciones utilizan únicamente datos ficticios y bases de pruebas. Este incremento no elimina encuestas reales, no aplica migraciones en Neon y no realiza despliegue por sí mismo.

## Verificación

- 18 pruebas de integración correctas en H2 y las mismas 18 en PostgreSQL local: respaldo íntegro, borrado de dependencias, conservación de otra encuesta y recursos compartidos, cambios con los mismos conteos, referencias inconsistentes, archivos alterados, caducidad/cuenta, confirmación, autorización y CSRF. PostgreSQL aplica V20 sobre la base de pruebas existente.
- 3 pruebas JavaScript correctas: una respuesta de red incompleta, una redirección al login, un ZIP vacío o una referencia inválida no habilitan la eliminación.
- Chrome: 9 vistas a 360, 768 y 1280 píxeles, sin desbordamientos ni excepciones. Comprueba descarga pendiente, ZIP guardado completo, confirmación requerida y error de respaldo que conserva oculta la eliminación. Usa HTML Thymeleaf real y recursos locales contra un servidor de prueba; la persistencia se comprueba en las pruebas HTTP con base de pruebas.

Evidencia local ignorada por Git: `.codex-tmp/survey-deletion-tests.log`, `.codex-tmp/survey-deletion-postgres.log` y `.codex-tmp/survey-deletion-browser/verification.json`.
