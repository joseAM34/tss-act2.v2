# Paso 02: requisitos de los cuestionarios y criterios de aceptación

**Uso:** entregar este archivo a Codex después de `01_ESTABLECER_REPOSITORIO.md`. Conservarlo en `docs/02_REQUISITOS_CUESTIONARIOS.md`.

**Estado:** especificación inicial para revisión. Las preguntas originales están inventariadas; las propuestas y ambigüedades están identificadas. Este documento todavía no constituye un instrumento aprobado para recoger respuestas reales.

**Resultado de este paso:** requisitos de los dos cuestionarios, opciones, reglas de validación, correspondencia con las historias de usuario y criterios comprobables de aceptación. El diseño de datos y la implementación se desarrollarán después.

**Integración en el repositorio (2026-10-13):** este archivo conserva la especificación recibida como referencia. El usuario ya autorizó desarrollar la aplicación y continuar la fase 2; la instrucción documental del apartado 1 no limita ese trabajo autorizado. El acceso público con administración protegida, los diez criterios sin repetición y las adaptaciones de fase 2 del registro siguiente están aprobados por el usuario para el borrador. Esto no constituye aprobación institucional para trabajo de campo. La correspondencia con el código, la matriz de reglas vigente y la evidencia de aceptación se mantienen en [ACEPTACION_CUESTIONARIOS.md](ACEPTACION_CUESTIONARIOS.md).

**Resoluciones de fase 2:** edad como años enteros; género Hombre/Mujer/Otro/Prefiero no responder; vida profesional y desempleo como meses enteros; hasta dos asignaturas de poca utilidad, con texto complementario de otra asignatura que no cuenta entre las selecciones; filtro de experiencia relacionada con Sistemas, experiencia solo en otras áreas o sin experiencia. Se aprueban para el borrador seis competencias técnicas y seis transversales, con referente de adecuación de la formación a demandas del trabajo relacionado actual o previo, escala de cinco grados de adecuación y opción nominal «No puedo valorar». La sección de fuente se conserva para distinguir las decisiones de la copia original; el registro D01-D15 indica lo resuelto y lo pendiente.

## 1. Instrucción para Codex

Revisa el documento del paso 01, las instrucciones del repositorio y la guía fuente. Incorpora esta especificación al repositorio y actualiza las referencias del README y, cuando corresponda, de AGENTS. Conserva el trabajo existente.

En esta etapa trabaja sobre los requisitos. No generes todavía la aplicación, el esquema de base de datos, migraciones ni el motor estadístico. Los casos de aceptación de la sección 10 describen comprobaciones para cuando se implemente cada función; no son pruebas ya ejecutadas.

Si una decisión del usuario resuelve una ambigüedad, registra la decisión y actualiza su requisito y criterio de aceptación. Los puntos sin resolver conservan su estado pendiente. Avanza con la documentación que no dependa de ellos.

## 2. Fuente, alcance y estados de los requisitos

Fuente principal: `JOSE ARMANDO MAREÑO MAMANI - EstudioMercadoHerramientaColeccionDatos.pdf`, titulado **Guía Metodológica y Técnica Integral: Análisis de Mercado y Herramienta de Recolección de Datos (Enfoque ARCUSUR)**, 20 páginas. Las páginas citadas corresponden al orden del PDF.

- Historias de usuario: páginas 3 a 5.
- Cuestionario de titulados: páginas 6 a 13.
- Cuestionario de empleadores: páginas 14 a 20.

El Excel de candidatos sirve para organizar el contacto y verificar participantes. Los cuestionarios deben obtener respuestas de personas con el vínculo requerido por el estudio; una empresa incluida en el directorio no demuestra ese vínculo por sí sola.

| Estado | Significado |
| --- | --- |
| **FUENTE** | Contenido o requisito presente en la guía. Puede tener una regla operativa pendiente. |
| **PROPUESTA** | Solución de trabajo sugerida para convertir la guía en una aplicación coherente. Requiere definición antes de modificar el instrumento. |
| **PENDIENTE** | Dato, catálogo o regla que la fuente no permite cerrar de manera inequívoca. |

Los IDs de preguntas y requisitos son convenciones propuestas para mantener trazabilidad. No son la numeración original del PDF. Los títulos de las preguntas se abrevian en las tablas; antes de implementar, cotejar el texto visible con el original y registrar cualquier modificación sustantiva.

## 3. Participantes y recorridos

| Participante | Recorrido y propósito |
| --- | --- |
| Titulado de Ingeniería en Sistemas UMSS | Conocer el propósito del estudio, responder sobre formación y trayectoria profesional, valorar la carrera y proporcionar contactos según la regla que se apruebe. |
| Empleador con experiencia laboral respecto de titulados de la carrera | Caracterizar su organización, describir contratación y demandas, y valorar formación y desempeño. |
| Administrador o comité de evaluación | Consultar la recolección y posteriormente obtener análisis, gráficos y reportes automatizados desde el backend. |

**FUENTE:** el estudio y los anexos definen esos perfiles. **PROPUESTA:** mostrar dos entradas claras, «Encuesta a titulados» y «Encuesta a empleadores», y restringir la administración.

**DECISIÓN DEL USUARIO:** las encuestas son públicas y no requieren cuenta del participante; las respuestas y la administración se protegen con contraseña en el backend. La identificación o verificación del vínculo con UMSS, las invitaciones y la política de varias participaciones conservan su estado pendiente. El acceso público no resuelve la elegibilidad de una persona ni impide por sí solo participaciones diferentes.

La respuesta «No» a contratación en los últimos cinco años no implica automáticamente falta de relación con titulados: puede existir una relación anterior. Precisar el criterio de elegibilidad por separado.

## 4. Cuestionario de titulados

### 4.1. Inventario completo del anexo

Todas las preguntas de esta tabla son **FUENTE**. Las condiciones de visualización de la sección 7 son propuestas porque la copia del formulario no documenta sus saltos. El marcador general «Obligatorio» de la página 6 no permite determinar inequívocamente la obligatoriedad de cada pregunta: definirla en una matriz antes de habilitar el formulario real.

| ID | Pregunta o variable | Tipo y opciones de la fuente | Página |
| --- | --- | --- | --- |
| T01 | Edad actual | La copia muestra selección única y solo «35 años o más». **Adaptación aprobada para borrador:** edad entera en años. | 6 |
| T02 | Género | La copia muestra selección única y solo «Hombre». **Catálogo aprobado para borrador:** Hombre/Mujer/Otro/Prefiero no responder. | 6 |
| T03 | Ha realizado o realiza formación complementaria posterior al pregrado | Selección única: Sí / No. | 7 |
| T04 | Programa complementario de mayor nivel concluido o en curso | Selección única; catálogo NIVEL_REALIZADO. La pregunta agrupa concluido y en curso. | 7 |
| T05 | Institución donde cursó el programa | Selección única; catálogo INSTITUCION. | 7 |
| T06 | Financiamiento del programa realizado | Selección única; catálogo FINANCIAMIENTO_REALIZADO. | 7 |
| T07 | Nombre del programa | Texto de una línea. | 7 |
| T08 | Interés en realizar un posgrado en áreas de Ingeniería en Sistemas | Selección única: Sí / No. | 8 |
| T09 | Área de interés para el futuro programa | Selección única; catálogo AREA_INTERES. | 8 |
| T10 | Organización educativa preferida para el futuro posgrado | Selección única; catálogo INSTITUCION. | 8 |
| T11 | Opinión sobre la afinidad de los programas de posgrado FCyT con las especialidades de Sistemas | Una afirmación valorada con escala ACUERDO_4. | 8 |
| T12 | Vida profesional hasta la fecha | La fuente pregunta años en un campo de texto numérico. **Adaptación aprobada para borrador:** meses enteros. Definición temporal definitiva pendiente. | 9 |
| T13 | Tiempo total desempleado dentro de la vida profesional indicada | La fuente usa texto numérico sin repetir unidad. **Unidad aprobada para borrador:** meses enteros, compatibles con T12. | 9 |
| T14 | Situación laboral actual | Selección única; catálogo SITUACION_LABORAL. | 9 |
| T15 | Origen del emprendimiento | Selección única; catálogo ORIGEN_EMPRENDIMIENTO. | 9 |
| T16 | Entregable del negocio | Selección única: Producto / Servicio / Ambos. | 9 |
| T17 | Financiamiento externo del negocio | Selección única: préstamos bancarios / inversores / financiamiento propio. | 9 |
| T18 | Satisfacción con el rendimiento actual del negocio | Escala SATISFACCION_3. | 10 |
| T19 | Importancia de la formación en Sistemas para iniciar y gestionar el negocio | Escala IMPORTANCIA_4. | 10 |
| T20 | El trabajo que ejerce actualmente es su primer empleo | Selección única: Sí / No. | 10 |
| T21 | Tiempo para conseguir el primer trabajo relacionado con la formación después de titularse | Selección única; catálogo TIEMPO_PRIMER_EMPLEO. | 10 |
| T22 | Cantidad de empleos desempeñados desde la titulación | Selección única: 1 / 2 / 3 / Más de 3. La opción cero no aparece. | 10 |
| T23 | Satisfacción global con la formación recibida en la carrera | Escala ACUERDO_4, aunque el enunciado habla de satisfacción. Revisar coherencia del texto y escala. | 11 |
| T24 | Concordancia entre formación recibida y requerimientos | Escala ACUERDO_4. El texto termina en «los requerimientos»; confirmar la redacción completa. | 11 |
| T25 | Aspectos de la carrera que resultaron útiles | Selección múltiple; catálogo ASPECTOS_UTILES. No se indica límite. | 11 |
| T26 | Aspectos de la carrera que pueden mejorarse | Selección múltiple; catálogo ASPECTOS_MEJORABLES. No se indica límite. | 11-12 |
| T27 | Cinco asignaturas que brindaron ventaja competitiva | Selección múltiple; catálogo ASIGNATURAS_UTILES. La instrucción dice seleccionar cinco. | 12 |
| T28 | Otra asignatura útil | Texto de una línea. Relación con el conteo de T27 pendiente. | 12 |
| T29 | Asignaturas de poca utilidad profesional | Selección múltiple; catálogo ASIGNATURAS_POCO_UTILES. El título pide cinco y la instrucción pide dos. **Resolución para borrador:** hasta dos. | 12-13 |
| T30 | Otra asignatura de poca utilidad | Texto de una línea. **Resolución para borrador:** complementario, no cuenta entre las dos selecciones. | 13 |
| T31 | Interés en una red oficial de contactos de la carrera | Selección única: Sí / No. | 13 |
| T32 | Correo de contacto | Texto de una línea; validación de formato propuesta. | 13 |
| T33 | Teléfono de contacto, preferentemente con WhatsApp | Texto de una línea. Conservar como texto, con prefijo internacional cuando se proporcione. | 13 |
| T34 | Enlace de LinkedIn | Texto de una línea; validación como URL propuesta. | 13 |

### 4.2. Catálogos de titulados

Conservar las categorías originales. Las correcciones de ortografía o presentación no deben cambiar su significado. Los códigos de opciones se definirán en el documento de datos.

| Catálogo | Opciones presentes |
| --- | --- |
| NIVEL_REALIZADO | Diplomado; Especialidad; Maestría; Doctorado; Posdoctorado. |
| INSTITUCION | Escuela Universitaria de Posgrado (EUPG); Dirección de Posgrado FCyT; IBNORCA; universidades internacionales; universidades públicas nacionales; universidades privadas nacionales; Otro. |
| FINANCIAMIENTO_REALIZADO | Recursos propios; financiamiento total mediante beca; financiamiento parcial mediante beca. |
| AREA_INTERES | IA; Robótica; Ingeniería de Software; Base de Datos; Redes de Datos y Seguridad. |
| SITUACION_LABORAL | Actualmente trabaja en una organización; actualmente no trabaja; dedica tiempo completo a un emprendimiento propio. |
| ORIGEN_EMPRENDIMIENTO | Idea propia; idea conjunta con compañeros; StartUp; Spin Off derivado de un proyecto universitario; Otro. |
| TIEMPO_PRIMER_EMPLEO | Ya trabajaba antes de titularse; menos de un mes; entre 1 y 4 meses; entre 4 y 8 meses; entre 8 y 12 meses; más de 12 meses. |
| ASPECTOS_UTILES | Aplicación práctica de conceptos teóricos en proyectos reales; pasantías o prácticas profesionales; distribución de horarios; habilidades analíticas y resolución de problemas; colaboración de equipos multidisciplinarios; herramientas y software de la industria. |
| ASPECTOS_MEJORABLES | Integración de teoría y práctica; relación con empresas mediante prácticas; horarios para trabajadores y módulos virtuales; actualización del plan de estudios; actividades extracurriculares de investigación o servicio social; más opciones de especialización. |
| ASIGNATURAS_UTILES | Introducción; Base de Datos; Inteligencia Artificial; Redes de Computadora; Electivas; T.I.S.; Básicas (Física, Cálculo, Álgebras); Planificación de Proyectos y Gestión; Tecnología Redes Avanzadas. Conservar «Introducción» y «T.I.S.» hasta confirmar sus nombres. |
| ASIGNATURAS_POCO_UTILES | Básicas (Física, Cálculo, Álgebras); Aplic. Interactivas para Televisión Digital; Telefonía IP; Web Semánticas; Graficación por Computadora; Robótica; Taller de Programación en Bajo Nivel; Teoría de Autómatas y Leng. Formales; Programación Funcional; Teoría de Grafos; Contabilidad Básica. |

Las opciones temporales solapan los extremos de 4 y 8 meses y dejan por aclarar la interpretación exacta de 12 meses. Mantener sus etiquetas mientras se aprueban intervalos inequívocos. «Más de 3 empleos» es una categoría abierta, no el número cuatro.

No agregar por iniciativa técnica una casilla «Otro» a T27/T29: el anexo muestra campos complementarios T28/T30, pero no explica cómo integrarlos en la selección.

### 4.3. Variables exigidas por las historias y cobertura adicional

Estos IDs se reservan para completar la correspondencia con las historias. Aún deben acordarse sus enunciados y reglas.

| ID reservado | Variable | Estado y definición pendiente |
| --- | --- | --- |
| T35 | Año de egreso | **FUENTE HU1**, página 3: campo numérico obligatorio. No sustituirlo por T12. Precisar egreso frente a titulación y el año de referencia del estudio. |
| T36 | Sector de desempeño del titulado | **FUENTE HU1**, página 3: categoría cerrada obligatoria. Aprobar el catálogo y su aplicación según situación laboral. Reutilizar el sector de empleadores es una propuesta. |
| T37 | Nivel de posgrado deseado | **FUENTE HU2**, página 3. Ejemplos: Maestría, Especialidad y Doctorado. Definir el catálogo completo y si es una preferencia principal o varias limitadas. T04 mide un programa realizado o en curso. |
| T38 | Financiamiento estimado de estudios futuros | **FUENTE HU3**, página 4. Recursos propios, becas estatales, créditos educativos o patrocinio empresarial. Elegir fuente principal única o mecanismo ponderado según el instrumento. T06 se conserva separado. |
| T39 | Valoración de competencias técnicas frente al puesto | **ADAPTACIÓN APROBADA PARA BORRADOR** de HU4: seis ítems de adecuación (desarrollo de software, bases de datos, redes, seguridad informática, análisis de datos, gestión de proyectos), cinco grados y opción nominal «No puedo valorar». No es matriz original del anexo. |
| T40 | Valoración de competencias transversales frente al puesto | **ADAPTACIÓN APROBADA PARA BORRADOR** de HU4: comunicación, resolución de problemas, pensamiento crítico, aprendizaje continuo, trabajo en equipo y ética profesional; misma escala y referente que T39. |
| T41 | Experiencia laboral previa y experiencia en trabajos relacionados con Sistemas | **ADAPTACIÓN APROBADA PARA BORRADOR:** filtro único obligatorio de experiencia relacionada, solo otras áreas o sin experiencia. Determina T21/T22/T39/T40, separado de la situación laboral actual. |

Las matrices T39/T40 miden adecuación percibida de la formación recibida a las demandas del trabajo relacionado con Sistemas actual o anterior, según la adaptación aprobada para borrador. No calcular una «brecha» mediante una resta de nivel adquirido y requerido: no se capturan dos puntuaciones con esos referentes. Las respuestas abiertas de empleadores no equivalen a puntuaciones comparables.

## 5. Cuestionario de empleadores

### 5.1. Inventario de preguntas iniciales

Todas las preguntas de esta tabla son **FUENTE**. El correo y E01-E07 y E09-E12 aparecen con asterisco de obligatoriedad. E08 no lo muestra. Para E08, la matriz de valoraciones y las condiciones de «Otro», confirmar la regla final.

La copia no muestra de manera inequívoca todos los controles de selección. Donde no se indica expresamente selección múltiple, se propone selección única para el catálogo principal, sujeta a la definición del instrumento.

| ID | Correspondencia en el PDF | Pregunta o variable | Tipo y opciones |
| --- | --- | --- | --- |
| E00 | Correo, página 14 | Correo del participante | Texto; obligatorio según asterisco. |
| E01 | Pregunta 1, página 14 | Nombre de empresa o institución | Texto; obligatorio. |
| E02 | Pregunta 2, página 14 | Tipo de organización | Público / Privado; obligatorio. |
| E03 | Pregunta 3, página 15 | Tamaño de organización | Grande: 100 o más funcionarios; mediana: 31-99; pequeña: 11-30; micro: hasta 10; obligatorio. |
| E04 | Pregunta 4, página 15 | Rubro o sector principal | Catálogo SECTOR_ORGANIZACION; obligatorio. |
| E05 | Pregunta 5, página 16 | Ha contratado ingenieros de Sistemas en los últimos cinco años | Sí / No; obligatorio. Fijar la fecha de referencia de ese período. |
| E06 | Pregunta 6, página 16 | Posibilidad de contratación en los próximos años | Catálogo CONTRATACION_FUTURA; obligatorio. El horizonte temporal debe precisarse si se cuantifican escenarios. |
| E07 | Pregunta 7, página 16 | Formación demandada para contratar | Licenciatura; Diplomado; Especialidad; Maestría; Doctorado; Posdoctorado; obligatorio. Cardinalidad por confirmar. |
| E08 | Pregunta 8, página 16 | Medios de convocatoria | Catálogo MEDIOS_CONVOCATORIA. Cardinalidad y obligatoriedad pendientes. |
| E09 | Pregunta 9, página 17 | Cargos de titulados en la organización | Selección múltiple expresamente permitida; catálogo CARGOS; obligatorio. Incluye «Otro», con texto complementario E09_OTRO. |
| E10 | Pregunta 10, página 17 | Áreas, tecnologías o tendencias emergentes para la formación | Texto abierto; obligatorio. |
| E11 | Pregunta 11, página 17 | Herramientas, software, plataformas o entornos que deberían manejar | Texto abierto; obligatorio. |
| E12 | Pregunta 12, páginas 17-18 | Habilidades, competencias y destrezas fundamentales | Texto abierto; obligatorio. |

### 5.2. Catálogos de empleadores

| Catálogo | Opciones presentes |
| --- | --- |
| SECTOR_ORGANIZACION | Desarrollo de software; servicios TI; telecomunicaciones y redes; soporte técnico y mantenimiento de sistemas; consultoría en sistemas / transformación digital; banca y servicios financieros; instituciones públicas / gobierno; educación e investigación; industria y manufactura; comercio y comercio electrónico; salud; seguridad informática / ciberseguridad; análisis de datos / inteligencia de negocios; startups o emprendimientos tecnológicos; Otros. |
| CONTRATACION_FUTURA | Alta probabilidad de contratación; posibilidad según crecimiento o nuevos proyectos; no tiene prevista contratación actualmente; no requiere profesionales de Sistemas. |
| MEDIOS_CONVOCATORIA | Medios escritos y audiovisuales; Internet y redes sociales; postulación competitiva mediante convocatorias públicas y privadas; oferta profesional personal; recomendación de terceros; Otros. |
| CARGOS | Apoyo / asistencia tecnológica; técnico en desarrollo, sistemas, redes, bases de datos o soporte TI; analista de sistemas, datos, procesos o soluciones; especialista en ciberseguridad, arquitectura o infraestructura; supervisor / coordinador tecnológico; jefatura o gerencia; consultor tecnológico; Otro. |

Las categorías de tamaño no proporcionan el número exacto de funcionarios. Las expectativas de contratación tampoco indican cuántos puestos se abrirán. No convertir esas respuestas en conteos numéricos supuestos.

### 5.3. Formación y perfil profesional: diez criterios distintos

El PDF repite estos diez criterios: primera aparición en páginas 18-19, repetición en páginas 19-20. La siguiente lista única es una **DECISIÓN APROBADA POR EL USUARIO** para la versión digital: presentar y analizar cada criterio una sola vez. Se conserva la correspondencia de ambas apariciones para trazabilidad.

| ID | Criterio abreviado | Primera aparición / repetición |
| --- | --- | --- |
| E13 | Confianza de la organización en la carrera UMSS como formadora de profesionales tecnológicos | 18 / 19 |
| E14 | Consistencia del título profesional con formación y competencias demostradas | 18 / 19 |
| E15 | Consulta regular de las autoridades a empleadores sobre perfil y competencias | 18 / 19 |
| E16 | Conocimiento del perfil profesional y las competencias de egreso declaradas | 18 / 19 |
| E17 | Coherencia del perfil declarado con el desempeño laboral de titulados | 18 / 20 |
| E18 | Formación que permite desempeño destacado e integral | 18 / 20 |
| E19 | Consideración de necesidades y opiniones empresariales en prácticas, pasantías y modalidades de titulación | 19 / 20 |
| E20 | Comunicación, resolución de problemas, pensamiento crítico, aprendizaje, trabajo en equipo, iniciativa y manejo de TI | 19 / 20 |
| E21 | Ética, responsabilidad social, inclusión, diversidad, derechos humanos y compromiso ambiental | 19 / 20 |
| E22 | Participación del empleador en recopilación, evaluación o retroalimentación del desempeño | 19 / 20 |

Cada criterio utiliza la escala EMPLEADORES del apartado 6. La obligatoriedad de cada valoración debe confirmarse; no aparece un asterisco por criterio en esta copia.

## 6. Escalas y significado de las respuestas

| Escala | Etiquetas y preguntas |
| --- | --- |
| ACUERDO_4 | Totalmente en desacuerdo; En desacuerdo; De acuerdo; Totalmente de acuerdo. T11, T23 y T24. |
| SATISFACCION_3 | Insatisfecho; Algo satisfecho; Satisfecho. T18. |
| IMPORTANCIA_4 | No es importante; Poco importante; Importante; Muy importante. T19. |
| EMPLEADORES | Totalmente de acuerdo; Parcialmente de acuerdo; Totalmente en desacuerdo; No sabe. E13-E22. |
| COMPETENCIAS_HU4 | La historia sugiere 1 a 5. **Adaptación aprobada para borrador:** Nada adecuada / Poco adecuada / Moderadamente adecuada / Adecuada / Muy adecuada, más «No puedo valorar» como categoría nominal sin puntaje. Los ítems y el referente están en la matriz de aceptación; no se cambia la escala de las valoraciones originales. |

Conservar las etiquetas, el orden de presentación y el significado de cada escala. La codificación para los cálculos se definirá después; ordenar categorías o cambiar números no autoriza modificar el instrumento.

**«No sabe» es una respuesta explícita**, diferente de un campo vacío y de una pregunta no aplicable. Conservarla como categoría, sin asignarle cero o una posición dentro del grado de acuerdo. Su inclusión en denominadores debe explicarse en cada resultado.

Para los demás campos, el sistema deberá distinguir al menos:

| Estado de respuesta | Interpretación |
| --- | --- |
| CON_VALOR | Una categoría, selección o texto válido fue proporcionado; incluye «No sabe» cuando existe esa opción. |
| SIN_RESPUESTA | Pregunta aplicable que quedó vacía según la regla de obligatoriedad. |
| NO_APLICA | Pregunta excluida por una condición aprobada del cuestionario. |

Estos estados describen el requisito; aún no determinan el esquema físico de almacenamiento. No transformar campos vacíos o saltos en «No», cero o la categoría menos favorable.

## 7. Reglas de visualización y validación

### 7.1. Condiciones propuestas

Las siguientes reglas son **PROPUESTA**, no saltos comprobados del formulario original. Se revisarán al aprobar el instrumento.

| Condición | Comportamiento propuesto |
| --- | --- |
| T03 = Sí | Mostrar T04-T07. Si es No, esos campos quedan NO_APLICA. |
| T08 = Sí | Mostrar T09, T10 y las variables futuras T37/T38 una vez definidas. Si es No, quedan NO_APLICA. |
| T11: opinión sobre posgrado FCyT | Su aplicación no depende necesariamente del interés personal en posgrado. Definir por separado si se admite desconocimiento o falta de información. |
| T14 = emprendimiento propio a tiempo completo | Mostrar T15-T19. Para otros estados, quedan NO_APLICA bajo esta propuesta; decidir cómo incluir emprendimientos paralelos. |
| T20: primer empleo actual | Aplicar a personas que actualmente ejercen un empleo; aclarar su aplicación a autoempleo. |
| T21/T22: historial de empleo | **Filtro aprobado para borrador:** T21 aplica con experiencia relacionada; T22 con experiencia relacionada o solo otras áreas. Una persona hoy desempleada puede tener trayectoria laboral. Para quien nunca trabajó ambos quedan NO_APLICA. |
| T23: satisfacción con formación | Puede responderse sin empleo actual. No ocultarla automáticamente por desempleo. |
| T24 y T39/T40: relación con requerimientos del puesto | T39/T40 aplican solo a experiencia relacionada, con referente de trabajo actual o previo aprobado para borrador. T24 también aplica a experiencia relacionada y completa como propuesta el texto original con «de su trabajo actual o anterior relacionado con Sistemas». Su revisión metodológica final sigue pendiente D11; el campo está implementado. |
| T31 = Sí: red de contactos | Definir si T32-T34 se solicitan solo en esta condición, cuáles son opcionales y qué autorización de contacto se registra. |
| E09 incluye Otro | Mostrar E09_OTRO y establecer si la descripción es obligatoria. |
| E05 = No | No borrar ni excluir automáticamente todas las valoraciones del empleador; usar el criterio de elegibilidad y experiencia que se apruebe. |

Si el participante cambia una respuesta que controla un bloque, revisar su aplicabilidad. Las respuestas de un bloque inactivo no deben contabilizarse como válidas en el envío final. Definir cómo conservar cambios de respuestas que ya se hubieran registrado.

### 7.2. Validaciones comunes propuestas

- Validar tanto en la interfaz como en el backend. Las restricciones de una pantalla deben mantenerse ante una solicitud HTTP directa.
- Comprobar la obligatoriedad solo en preguntas aplicables, conforme a la matriz aprobada.
- Para selección única, aceptar como máximo una opción del catálogo vigente. Para selección múltiple, aceptar opciones distintas del mismo catálogo y respetar sus límites.
- Para T27, la fuente establece cinco selecciones. **Resolución para borrador:** T29 admite hasta dos; T28/T30 son textos complementarios que no cuentan entre las selecciones.
- T35 debe ser un año entero, sin admitir años posteriores al año de referencia del estudio. Establecer un límite inferior documentado en vez de inventarlo.
- T12/T13 usan meses enteros no negativos conforme a la decisión para borrador. Si se proporciona tiempo desempleado debe indicarse vida profesional y aquel no puede superar esta.
- No calcular automáticamente T12 como «año de referencia menos T35» mientras egreso, titulación y vida profesional no estén reconciliados.
- Si se proporciona correo, comprobar su formato. El teléfono se conserva como texto; los textos de contacto no se convierten en números de medida.
- Si se proporciona LinkedIn, validar la URL conforme a la regla aprobada. Definir dominios y formatos admitidos sin depender de consultas externas para enviar la encuesta.
- Los textos obligatorios deben contener contenido distinto de espacios. Definir longitudes máximas en el paso de datos y conservar texto original para la futura codificación.
- Comunicar errores junto a la pregunta correspondiente y conservar las respuestas válidas para su corrección.

## 8. Requisitos funcionales y conexión con el pipeline

Estos requisitos registran comportamientos esperados; su implementación se dividirá entre los siguientes incrementos.

| ID | Requisito | Origen y etapa |
| --- | --- | --- |
| RF01 | Presentar propósito del estudio y distinguir ambos cuestionarios. | **FUENTE:** anexos. Texto de consentimiento y forma de aceptación: **PENDIENTE**. |
| RF02 | Capturar los campos y catálogos aprobados, con preguntas condicionales y validaciones. | Campos: **FUENTE**; comportamiento condicional: **PROPUESTA**. |
| RF03 | Registrar tipo y versión del instrumento, identificador de respuesta y fecha de envío; conservar códigos estables. | **PROPUESTA** para trazabilidad. |
| RF04 | Guardar un envío completo como una operación coherente y emitir comprobante solo después de persistirlo. | **PROPUESTA** técnica; diseño e implementación posteriores. |
| RF05 | Evitar que un reintento del mismo envío cree una segunda respuesta. Definir por separado múltiples participaciones de una persona o empresa. | **PROPUESTA** técnica y política de participación **PENDIENTE**. |
| RF06 | Restringir la consulta administrativa y el acceso a identificadores y contactos; omitirlos de las salidas analíticas por defecto. | Confidencialidad: **FUENTE**. Separación y control de acceso: **PROPUESTA**. |
| RF07 | Permitir revisión del avance y de las respuestas para gestión del estudio, con filtros pertinentes. | **PROPUESTA** administrativa; no ampliar a un constructor genérico de encuestas. |
| RF08 | Conservar captura original y obtener una matriz analítica limpia, con registro de transformaciones, faltantes, no aplicables y codificación. | Data Wrangling: **FUENTE**, páginas 1 y 5. Detalle de transformaciones: etapa de datos. |
| RF09 | Obtener frecuencias, porcentajes y medidas univariadas temporales y de valoración según cada escala. | **FUENTE:** HU1, HU2 y HU4. Método y denominadores: etapa estadística. |
| RF10 | Obtener contingencias y matrices cruzadas, y aplicar pruebas chi-cuadrado cuando sus condiciones se cumplan. | **FUENTE:** HU3 y marco metodológico. No confundir financiamiento pasado con futuro. |
| RF11 | Mostrar visualización computacional y gráficos interactivos, incluidos mapas de calor y radar. | **FUENTE:** marco metodológico y HU5. Diseño: etapa de visualización. |
| RF12 | Automatizar en el backend la preparación de reportes, matrices y resultados; permitir exportaciones limpias CSV/Excel y definir la salida de gráficos. | **FUENTE:** HU5. Plantillas, disparadores y formatos de gráficos: pendientes de la etapa de reportes. |
| RF13 | Permitir experimentación por simulación y comparación de escenarios académicos definidos. | **FUENTE:** enunciado director y producto final. Modelo: etapa de simulación. |

RF12 no se satisface únicamente agregando un botón que descargue respuestas sin procesar. Debe enlazar el conjunto de datos seleccionado con sus transformaciones, cálculos, matrices y resultados de reporte. El PDF no fija reportes programados por fecha ni distribución por correo; esos mecanismos requieren definición adicional.

## 9. Registro de decisiones pendientes

Los siguientes puntos afectan distintas funciones; se resolverán cuando corresponda sin frenar la documentación independiente.

| ID | Decisión | Afecta |
| --- | --- | --- |
| D01 | **RESUELTA PARA EL BORRADOR:** edad entera en años y género Hombre/Mujer/Otro/Prefiero no responder. Se conserva como antecedente el catálogo incompleto de la copia; campos opcionales. | T01/T02. |
| D02 | **UNIDAD Y PRECISIÓN RESUELTAS:** T12/T13 usan meses enteros no negativos; desempleo no supera vida profesional y requiere indicarla si se proporciona. **PENDIENTE:** egreso frente a titulación, definición temporal y año de referencia; los límites técnicos no son límites de la fuente. | T12/T13/T35, HU1. |
| D03 | Catálogo de sector de titulados y tratamiento de emprendimiento o falta de empleo. | T36, HU1. |
| D04 | Nivel deseado único o múltiple y financiamiento futuro principal o ponderado. Revisar también diferencias entre áreas del anexo y ejemplos de HU2. | T09/T37/T38, HU2/HU3. |
| D05 | **ÍTEMS, REFERENTE Y ESCALA RESUELTOS PARA BORRADOR:** seis competencias técnicas y seis transversales; adecuación de formación al trabajo relacionado con Sistemas actual o previo; cinco grados más «No puedo valorar». **PENDIENTE:** análisis y método de brechas; no existe resta de nivel adquirido y requerido. | T39/T40, HU4. |
| D06 | **RESUELTA PARA BORRADOR:** hasta dos asignaturas de poca utilidad; T28/T30 son textos complementarios y no cuentan entre las selecciones de T27/T29. T27 conserva cinco selecciones si se responde. | T27-T30. |
| D07 | **RESUELTA POR EL USUARIO:** diez criterios únicos de empleadores, una sola valoración por criterio; conservar el registro de su duplicación en el original. | E13-E22. |
| D08 | Matriz de obligatoriedad de titulados; cardinalidad de catálogos de empleadores, E08 y valoraciones. | Formularios y validación. |
| D09 | **FILTRO Y RUTAS RESUELTOS PARA BORRADOR:** experiencia relacionada / solo otras áreas / sin experiencia. T21 y competencias aplican solo a relacionada; T22 a ambas clases de experiencia. Sin experiencia no es compatible con empleado o emprendimiento actual. **PENDIENTE:** referente T24, autoempleo y emprendimiento paralelo. | T14-T22/T24/T39-T41. |
| D10 | Intervalos temporales sin solapamiento y tratamiento de cero empleos. | T21/T22. |
| D11 | Enunciados incompletos o poco específicos: «Introducción», «T.I.S.», T24 y satisfacción expresada como acuerdo en T23. | Presentación y análisis. |
| D12 | Identificación, consentimiento, confidencialidad y acceso a contactos. La introducción de empleadores declara anonimato y el formulario pide nombre y correo. | E00/E01, T31-T34, RF01/RF06. |
| D13 | **ACCESO RESUELTO POR EL USUARIO:** encuestas públicas sin cuenta, administración y respuestas protegidas. **PENDIENTE:** elegibilidad UMSS, invitaciones y política de varias participaciones distintas. | Todos los envíos, RF05/RF06. |
| D14 | Reportes: filtros, plantillas, disparadores, formatos de gráficos y alcance de automatización. | HU5/RF12. |
| D15 | Escenarios: decisiones a explorar, entradas, reglas, supuestos y salidas. No derivar cantidades de puestos de una respuesta cualitativa. | RF13. |

Documentar cada resolución con fecha, decisión y campos afectados. Si el instrumento cambia después de comenzar la recolección, asignar una nueva versión y definir la comparabilidad de las respuestas anteriores.

## 10. Criterios de aceptación

Estos casos establecen evidencia esperada para la futura implementación. Los casos que dependen de D01-D15 se consideran condicionados a su resolución, no aprobados anticipadamente.

| Caso | Dado / cuando | Resultado comprobable |
| --- | --- | --- |
| CA01 | Un participante abre la herramienta. | Puede distinguir el cuestionario de titulados del de empleadores y leer el propósito correspondiente. |
| CA02 | Se carga una versión aprobada del cuestionario. | Sus preguntas, opciones, escalas y obligatoriedad coinciden con la especificación de esa versión. |
| CA03 | T03 cambia de Sí a No bajo la regla aprobada. | T04-T07 dejan de ser exigibles; sus valores anteriores no entran en análisis como respuestas aplicables. |
| CA04 | T08 = No bajo la regla aprobada. | T09/T10/T37/T38 quedan NO_APLICA; el resto de preguntas aplicables sigue disponible. |
| CA05 | Un titulado indica emprendimiento a tiempo completo. | Puede responder T15-T19 conforme a las reglas aprobadas. |
| CA06 | Un titulado desempleado tiene experiencia previa, o declara que nunca trabajó. | Cada caso dispone de una ruta válida; el sistema no exige un empleo actual inexistente ni obliga a escoger un número de empleos mayor que cero sin sustento. |
| CA07 | Se intenta enviar una pregunta aplicable obligatoria sin respuesta. | El envío se rechaza con un error claro y se conservan los demás valores válidos. |
| CA08 | Se envía una categoría ajena al catálogo o se supera un límite de selección por HTTP. | El backend rechaza la entrada, aunque la validación de la pantalla se haya eludido. |
| CA09 | Se envían T12/T13 con unidades aprobadas y T13 supera T12. | El sistema solicita corregir la inconsistencia y no guarda un envío final válido. |
| CA10 | Se selecciona «No sabe» para E13-E22. | La respuesta se conserva como categoría y se distingue de vacío y NO_APLICA; no se convierte en cero de acuerdo. |
| CA11 | Se implementa la lista única de criterios aprobada. | Se presentan diez criterios distintos y cada uno aporta una sola valoración por respuesta. |
| CA12 | E09 incluye «Otro» según la regla aprobada. | Se procesa E09_OTRO sin perder las demás selecciones ni duplicar cargos. |
| CA13 | El participante envía una respuesta válida y luego se reintenta la misma operación. | Se conserva un único envío y se devuelve su referencia; una participación distinta se trata según RF05/D13. |
| CA14 | Ocurre un error durante el guardado. | No se anuncia recepción exitosa y no queda una respuesta final incompleta presentada como válida. |
| CA15 | Se consulta una respuesta registrada. | Se identifica su cuestionario, versión, fecha y código de respuesta. |
| CA16 | Un usuario sin acceso administrativo intenta consultar contactos o exportarlos. | La operación se restringe; las salidas analíticas ordinarias omiten identificadores. |
| CA17 | Se prepara el cruce financiamiento futuro × nivel de posgrado deseado. | Se usan T38 y T37 de respuestas comparables; T06/T04 no se sustituyen silenciosamente. |
| CA18 | Se produce una salida del pipeline. | Se identifica su conjunto de datos, versión, filtros y reglas; las respuestas faltantes, no aplicables y múltiples reciben el tratamiento documentado. |
| CA19 | Se solicita un reporte automatizado, una vez definido RF12. | El backend obtiene o utiliza resultados coherentes del procesamiento y análisis, prepara matrices y entrega las salidas aprobadas sin exigir su reconstrucción manual. |
| CA20 | Se ejecuta una comparación de escenarios, una vez definido RF13. | Se muestran parámetros y supuestos y se distinguen resultados observados de simulados. |

## 11. Finalización de esta etapa y siguiente documento

La lista siguiente comprueba **completitud documental** del paso. No declara completados los análisis, reportes, pipeline ni simulación, y no aprueba institucionalmente el instrumento. La evidencia técnica ejecutada y los casos todavía pendientes se distinguen en la matriz de aceptación.

- [x] El inventario cubre las 34 preguntas/campos del anexo de titulados, con los IDs T01-T34.
- [x] Se documentan siete IDs adicionales T35-T41 para requisitos de historias y adaptaciones, sin presentarlos como preguntas originales del anexo.
- [x] Se cubren correo y preguntas 1-12 de empleadores, el texto complementario E09_OTRO y los diez criterios distintos de formación/perfil.
- [x] Se conservan los catálogos y las escalas originales, con sus diferencias, dudas y adaptaciones explícitas del borrador.
- [x] Cada regla adicional se identifica como aprobada para borrador, propuesta o pendiente.
- [x] Las cinco historias de usuario tienen correspondencia y HU5 conserva el requisito de automatización de reportes en el backend.
- [x] Se conserva el alcance de Data Wrangling, estadística univariada y bivariada, chi-cuadrado, visualización computacional y simulación.
- [x] Los criterios de aceptación están definidos; sus pruebas técnicas ejecutadas y sus pendientes se registran por separado en `ACEPTACION_CUESTIONARIOS.md`.
- [x] Se actualizaron las referencias del repositorio local; la incorporación documental por sí sola no afirma que el repositorio remoto haya sido modificado.

**Siguiente parte propuesta:** `03_DATOS_Y_DATA_WRANGLING.md`, para definir el diccionario de variables, estados de respuesta, captura original, matriz analítica, codificación y trazabilidad de las transformaciones. Las decisiones todavía pendientes se mantendrán como tales hasta que el usuario o el docente las resuelvan.
