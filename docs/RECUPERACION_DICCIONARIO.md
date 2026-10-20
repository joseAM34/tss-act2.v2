# Recuperación administrativa del diccionario global

Cuando el contenido de la última revisión global no coincide con su huella, abrir `/admin/diccionario` presenta una pantalla de recuperación. También se puede acceder desde `/admin/diccionario/recuperar`.

El administrador puede revisar el texto actual y confirmar expresamente que desea aceptarlo. El backend exige ADMIN, CSRF y una captura idéntica de la revisión, texto y huella observados al abrir la pantalla. Si cambian, se debe recargar y revisar. La recuperación valida y normaliza las reglas; un contenido inválido sigue bloqueado.

La operación transaccional crea una nueva revisión con huella válida y registra administrador, fecha, revisión afectada, nueva revisión, contenido observado y ambas huellas en `universal_dictionary_recovery` (Flyway V26). Conserva la fila anterior sin modificarla. No demuestra cuál era el contenido previo a la alteración, no restaura un respaldo ni modifica reportes congelados. No desactiva verificaciones de respuestas, conjuntos, reportes ni diccionarios privados.

Después de confirmar, volver a abrir o recargar la pantalla de trabajo original. Desplegar el código y aplicar V26 mediante el arranque habitual de Flyway antes de usar esta función.
