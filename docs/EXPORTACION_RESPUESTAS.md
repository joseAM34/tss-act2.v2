# Descargar respuestas desde Resultados

Resultados ofrece CSV y Excel de respuestas originales, con una fila por envío y una columna por pregunta. Es una exportación administrativa separada de las matrices analíticas y de los resultados agregados de los reportes. Utiliza exclusivamente la captura de la sesión o corte que terminó de cargar; no incorpora nuevos envíos hasta Actualizar todos. Respeta las versiones y fechas de recepción seleccionadas, con fechas inclusivas en America/La_Paz.

Se incluyen todas las preguntas, incluso las que no tienen gráficas activadas. Las opciones usan las etiquetas de la definición congelada que produjo cada respuesta; las respuestas abiertas se conservan. Las selecciones múltiples se unen con ` | `. Las preguntas incompatibles entre versiones mantienen columnas distintas. Una columna de estado por pregunta distingue CON_VALOR, SIN_RESPUESTA, NO_APLICA y NO_PERTENECE_VERSION (pregunta ausente en esa versión); No sabe sigue siendo una categoría nominal propia.

La recepción exportada corresponde a esta aplicación, no a la fecha externa de un archivo importado. Se agregan referencia del envío, versión, origen registrado en el corte, huella de captura, referencia del corte y fecha de corte UTC. No se recalculan estadísticas, no se usan diccionarios temáticos ni se sustituyen los originales por revisiones analíticas.

La descarga exige ADMIN, POST con CSRF y confirmación de que el archivo puede contener contactos, organización y textos personales. Verifica las capturas en lote frente a las huellas del corte y usa `Cache-Control: no-store`. Ninguna descarga crea respuestas ni modifica datos. Excel conserva cadenas como texto, sin fórmulas; CSV antepone apóstrofo a textos que una hoja de cálculo podría ejecutar como fórmula. Los datos originales nunca se redondean.

El formato tiene la estructura tabular del ejemplo de respuestas de Google Forms proporcionado por el usuario; no reproduce el archivo fuente ni su estilo. Reimportarlo exige configurar correspondencias, etiquetas, separador múltiple y estados con el importador. No constituye una restauración automática de IDs, recepción, origen, consentimiento o estados. Los textos que contengan el separador múltiple requieren revisar la correspondencia antes de importar. Se aplican los límites existentes del importador (tamaño, filas y columnas).

El archivo privado de ejemplo se inspeccionó solo para conocer sus encabezados. No se copia al repositorio ni se utiliza como fixture de pruebas.
