# Paso 4 local: selección y preparación de encuestas nuevas

**Actualización del paso 5:** los [descriptivos, tablas y gráficos generales](ANALISIS_GENERAL.md) ya se pueden generar desde un conjunto guardado. Las referencias a análisis pendiente en este documento conservan el alcance del paso 4.

Fecha: 2026-10-14. El usuario autorizó continuar con «paso 4». Se mantiene el trabajo local antes de commit, push o despliegue. Este incremento corresponde a la etapa «Selector y preparación» del plan original, cuya numeración incluía el diagnóstico documental.

## Cómo probarlo

**Selección rápida:** después de abrir una versión, pulsa **Incluir todos los datos compatibles**. Guarda una revisión con las categorías seleccionadas y las unidades numéricas ya declaradas. Para preguntas idénticas al cuestionario original de titulados completa edad en años, egreso en año calendario y tiempos en meses. Contactos, texto libre y números sin unidad conocida permanecen excluidos. El botón lleva a **Preparar conjunto**; después puedes abrir **Guardar análisis descriptivo**. Se exige ADMIN/CSRF y se conserva el control de revisión; no cambia conjuntos anteriores.

Detén la aplicación con **Ctrl+C**, y desde la raíz ejecuta:

```powershell
mvn "-Dspring-boot.run.profiles=dev" spring-boot:run
```

`dev` utiliza H2 local. Para este recorrido no combines `local`, ya que tu configuración privada puede conectar a Neon. Al arrancar, Flyway aplica V10; no hay comandos para cambiar credenciales ni configuración.

1. Entra a `http://localhost:8080/admin` con el administrador de tu base local.
2. Pulsa **Preparar datos de encuestas nuevas**. También puedes abrir una versión desde el editor y pulsar **Configurar preparación de esta versión**.
3. Selecciona la encuesta y versión exactas y pulsa **Abrir configuración**. Se admiten versiones publicadas o cerradas.
4. Revisa cada pregunta. Todas comienzan excluidas. Incluye únicamente las variables que no contengan identificadores:
   - Opciones controladas: **Categorías sin identificadores**. Conserva códigos y etiquetas sin asignar orden o puntajes.
   - Número: **Magnitud entera sin identificadores**, indicando su unidad; por ejemplo, `meses completos`.
   - Correos, teléfonos, enlaces y textos: permanecen excluidos. No hay codificación temática genérica en este incremento.
5. Pulsa **Guardar nueva revisión de configuración**. Guardar otra configuración crea una revisión nueva; no cambia la anterior.
6. En **Preparar conjunto**, elige el origen. Los envíos sin revisión administrativa están en **Origen sin verificar**; no aparecerán al seleccionar **Real**.
7. Conserva **Borrador** para pruebas. Puedes filtrar fechas de recepción; ambos días son inclusivos en `America/La_Paz`. Dejar fechas vacías usa lo recibido hasta preparar el conjunto.
8. Pulsa **Guardar conjunto congelado**. Consulta diccionario, cobertura, valores y estados; descarga el conjunto JSON o la matriz CSV.

Para clasificar un envío, abre su detalle en el panel y registra su origen con motivo. Preparar de nuevo produce otro conjunto; el anterior conserva la clasificación que utilizó.

## Conservación y privacidad

Cada conjunto pertenece a una sola encuesta y versión. Conserva estudio y estado de aprobación observado, definición y huellas, revisión exacta de configuración, origen, período, corte, miembros, revisiones de origen, actor y reglas `PREPARACION_GENERAL_V1`.

La fila principal es un envío; no se eliminan participaciones por correo, organización o respuestas iguales. Los filtros no conceden elegibilidad ni independencia estadística. Publicar o activar una versión no aprueba institucionalmente el instrumento.

Las matrices omiten valores de preguntas excluidas, contactos, organización y textos originales. Sus códigos excluidos y la definición del cuestionario permanecen como metadata; no se copian sus respuestas al conjunto. La inclusión administrativa de una variable es una declaración de que no contiene identificadores, no una garantía de anonimización. Revisa también categorías y magnitudes que puedan identificar a alguien.

`CON_VALOR`, `SIN_RESPUESTA` y `NO_APLICA` se conservan por variable. Cero sigue siendo un valor. Los faltantes se guardan como `null` con su estado, y no se convierten en cero. `NO_SABE` es una categoría nominal conservada, no un faltante ni un puntaje. Selecciones múltiples conservan su lista de códigos y una tabla separada de menciones; no se duplican filas principales por opción.

La cobertura distingue total de envíos incluidos, con valor, sin respuesta, no aplica y base aplicable. Esta última es total menos no aplica. Un conjunto borrador vacío conserva bases cero; no se inventan observaciones o porcentajes.

`OFICIAL` exige origen `REAL`, estudio e instrumento aprobados y al menos un envío incluido. Esta pantalla no registra esas aprobaciones automáticamente.

Al descargar se verifican las huellas del bundle y su contenido lógico. Las descargas utilizan exclusivamente el conjunto congelado, sin consultar respuestas o configuraciones actuales. El CSV tiene formato largo, una fila por pregunta seleccionada de cada envío; `value_json` conserva números, categorías, listas y `null` sin separadores ambiguos. Todas las rutas exigen ADMIN y los formularios POST conservan CSRF; las salidas privadas no se almacenan en caché y los textos se escapan.

## Archivos y esquema

- `V10__generic_preparation.sql`: añade `survey_preparation_config` y `survey_preparation_run`, con referencias a versiones y configuraciones. No modifica V1–V9.
- `GenericPreparationService`: valida metadata, prepara una versión, congela selección/productos y verifica integridad.
- `GenericPreparationController`: selector, configuración, preparación y descargas protegidas.
- `generic-preparation.html` y `generic-dataset.html`: pantallas administrativas del incremento.

El protocolo original UMSS conserva sus tablas, formato, preparación, estadística y dashboard. Las encuestas nuevas siguen excluidas de ese adaptador con `ANALISIS_DINAMICO_PENDIENTE`; se preparan mediante el servicio general separado. Los bundles históricos no se recalculan ni reciben metadata inferida.

## Límites y siguiente paso

Este paso prepara datos, no calcula descriptivos, cruces, chi-cuadrado, gráficos o reportes generales. No interpreta automáticamente ordinalidad, índices, NPS o competencias. Las correcciones y codificación temática genéricas, tipos adicionales, importación, Excel y reportes siguen pendientes.

El siguiente incremento podrá analizar estos conjuntos congelados con operaciones compatibles y bases explícitas, conservando versiones separadas.

## Verificación

La suite completa pasó **408 pruebas Java en H2 y 408 en la ejecución con PostgreSQL 18 local**, sin fallos ni omisiones. Incluye 20 pruebas nuevas de preparación y una de migración/persistencia en archivo. Se utilizaron datos ficticios y una base PostgreSQL temporal; no se conectó a Neon ni se realizó commit, push o despliegue.

Pruebas de exclusión por defecto, privacidad, tipos y unidades, revisiones y concurrencia, selección de origen/versión, condiciones y estados, cero, `NO_SABE`, menciones, cobertura, fechas inclusivas en La Paz, oficialidad, integridad, rollback, ADMIN/CSRF, escapes y descargas. Una prueba en archivo migra desde V9, conserva una publicación existente y verifica configuración/conjunto tras cerrar y reabrir la base.

Chrome comprobó las dos pantallas con HTML real y datos ficticios a 360, 768 y 1280 píxeles: etiquetas, formularios, CSRF y ausencia de desbordamiento o excepciones JavaScript. No es una auditoría completa de accesibilidad.
