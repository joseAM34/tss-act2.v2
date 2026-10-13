# Paso 01: establecer el repositorio del estudio de mercado UMSS

**Uso:** entregar este archivo a Codex como instrucción de la primera tarea. Conservarlo después en `docs/01_ESTABLECER_REPOSITORIO.md` dentro del repositorio.

**Resultado de este paso:** un repositorio organizado, con documentación inicial y reglas de trabajo. La implementación de la aplicación corresponde a los pasos siguientes.

## 1. Instrucción principal para Codex

Prepara el repositorio del proyecto de análisis de mercado y herramienta de recolección de datos de Ingeniería en Sistemas de la Universidad Mayor de San Simón (UMSS).

Antes de modificar archivos, revisa el estado del directorio, las instrucciones existentes y, si ya hay un repositorio, sus ramas y remotos. Conserva el trabajo previo. Inicializa Git únicamente cuando el directorio del proyecto no pertenezca ya a un repositorio; evita crear un repositorio anidado por accidente.

En esta tarea, crea o ajusta solo la estructura documental inicial, las reglas de versionado y la documentación mínima para trabajar en equipo. No generes todavía el proyecto Spring Boot, formularios, entidades, migraciones, análisis, reportes, simulaciones ni configuraciones de despliegue. Tampoco produzcas de una vez todos los documentos de los pasos siguientes.

La guía completa determina el alcance futuro. Los requisitos de las secciones 5 a 7 de este archivo deben quedar registrados desde el comienzo, aunque su implementación se programe después.

## 2. Organización y base técnica prevista

| Decisión | Base para este proyecto |
| --- | --- |
| Organización del código | Un repositorio común para la aplicación y su documentación. Nombre sugerido: `umss-estudio-mercado`. |
| Arquitectura de la aplicación | Monolito modular por capas. |
| Backend | Java, con Java 21 como base propuesta, y Spring Boot. La versión concreta de Spring Boot se verificará al iniciar la aplicación. |
| Interfaz propuesta | Thymeleaf, HTML, CSS y JavaScript dentro de la misma aplicación. |
| Base de datos | PostgreSQL. |
| Construcción prevista | Maven; incorporar Maven Wrapper cuando se cree el proyecto Java. |
| Control de versiones y colaboración | Git y un repositorio remoto previsto en GitHub. |
| Alojamiento previsto | Render para la aplicación y Neon para PostgreSQL; configurar ambos en un paso posterior. |
| Visibilidad sugerida para un repositorio nuevo | Privada para el trabajo del equipo; conservar la configuración del repositorio existente. |

Estas son decisiones de trabajo procedentes de la conversación, no exigencias tecnológicas del PDF. Java responde a la indicación del usuario. El documento exige la herramienta y menciona expresamente un backend en la historia de usuario 5.

Backend, páginas web y recursos estáticos se mantendrán juntos. Con la interfaz propuesta basta un repositorio y un proyecto de aplicación. Si posteriormente el equipo elige un frontend independiente, se podrá conservar el repositorio común y documentar esa decisión antes de reorganizarlo.

No se fijan en este paso versiones de bibliotecas estadísticas, gráficos, Excel, servicios ni cuotas gratuitas.

## 3. Archivos que debe dejar preparados Codex

| Ruta dentro del repositorio | Contenido de esta primera etapa |
| --- | --- |
| `README.md` | Propósito, dos entregables, alcance funcional futuro, base técnica prevista, estado actual y forma de colaboración. |
| `AGENTS.md` | Instrucciones generales para Codex: trabajo por pasos, respeto de la guía, conservación de archivos, tratamiento de ambigüedades y límites de la tarea actual. |
| `.gitignore` | Exclusiones para secretos, archivos locales, datos de campo y futuros resultados de construcción. |
| `.gitattributes` | Convenciones de texto y reconocimiento de archivos binarios. |
| `docs/01_ESTABLECER_REPOSITORIO.md` | Este documento, conservado como referencia de la primera tarea. |

El `README.md` debe indicar que el repositorio está en preparación documental. Todavía no hay una aplicación ejecutable; no inventar comandos de ejecución ni anunciar funcionalidades terminadas.

El `AGENTS.md` debe mantener explícitos los requisitos completos: dos cuestionarios, Data Wrangling, estadística univariada y bivariada con pruebas chi-cuadrado, visualización computacional, automatización de reportes en el backend, exportaciones y experimentación por simulación. Estos requisitos orientan los pasos futuros; no ordenan implementarlos todos en esta tarea.

No asignar una licencia sin una decisión del equipo. No añadir carpetas de código vacías, archivos de despliegue ni una configuración de integración continua que suponga una aplicación ya existente.

## 4. Versionado y colaboración

1. Si el proyecto es nuevo, utilizar `main` como rama principal. En un repositorio existente, respetar su rama principal y sus convenciones.
2. Registrar el trabajo posterior en ramas con nombres descriptivos, por ejemplo `docs/requisitos` o `feat/encuesta-titulados`, y revisar los cambios mediante pull requests.
3. Si ya existe un remoto, conservarlo. Si el usuario proporciona la URL del nuevo repositorio, documentar o configurar `origin` según la tarea autorizada. Si falta el destino remoto, dejar preparados los comandos con `<URL_DEL_REPOSITORIO>` como marcador.
4. Revisar los archivos seleccionados antes de cualquier commit. No incluir fuentes o datos de campo automáticamente. Un commit inicial, si se solicita o forma parte de la tarea de inicialización, puede usar el mensaje `chore: preparar repositorio y documentación inicial`.
5. Al terminar, informar del estado de Git, los archivos preparados y la situación del remoto. Solo declarar un commit, push o repositorio remoto creado cuando se haya realizado.

Para un directorio nuevo, estos comandos sirven como referencia, no como una orden de ejecutarlos sobre un repositorio existente:

```bash
git init -b main
git status --short
git add README.md AGENTS.md .gitignore .gitattributes docs/01_ESTABLECER_REPOSITORIO.md
git diff --cached --stat
git diff --cached
```

El `.gitignore` debe cubrir, como mínimo:

```gitignore
# Credenciales y configuración privada
.env
.env.*
!.env.example
application-local.properties
application-local.yml
application-local.yaml

# Datos de campo y archivos privados, cuando se incorporen localmente
data/private/
data/raw/
data/processed/
exports/
docs/fuentes-locales/

# Construcción, registros y configuración local de herramientas
target/
*.log
.idea/
*.iml
.vscode/
.DS_Store
Thumbs.db
```

Las carpetas anteriores son convenciones para futuras incorporaciones; no hace falta crearlas ahora. Revisar también los archivos ya rastreados: `.gitignore` no elimina del historial lo que se hubiera versionado antes. No excluir de manera global todos los PDF o XLSX, porque más adelante podría haber entregables aprobados para versionarse.

Usar en `.gitattributes` una configuración mínima como:

```gitattributes
* text=auto
*.md text eol=lf
*.java text eol=lf
*.html text eol=lf
*.css text eol=lf
*.js text eol=lf
*.sh text eol=lf
*.cmd text eol=crlf
*.pdf binary
*.xlsx binary
```

## 5. Fuente principal y lectura completa de la guía

Fuente: **Guía Metodológica y Técnica Integral — Análisis de Mercado y Herramienta de Recolección de Datos (Enfoque ARCUSUR)**, contenida en `JOSE ARMANDO MAREÑO MAMANI - EstudioMercadoHerramientaColeccionDatos.pdf`, de 20 páginas. Las referencias siguientes corresponden al orden de páginas del PDF.

Conservar el documento original. Si se necesita una copia local para trabajar, utilizar `docs/fuentes-locales/` y mantenerla fuera del versionado automático. El archivo `candidatos a entrevista.xlsx` es la fuente auxiliar prevista para organizar candidatos al trabajo de campo; sus registros requieren verificación y no constituyen respuestas de encuesta ni acreditan por sí solos una relación laboral con titulados UMSS.

### 5.1. Presentación y enunciado director — página 1

El estudio analiza el perfil, las expectativas de formación y la empleabilidad de titulados de Ingeniería en Sistemas. Proporcionará evidencia para el Plan de Desarrollo Estratégico y el proceso de acreditación ARCUSUR.

El enunciado director integra análisis estadístico descriptivo y exploratorio, ingeniería de software y experimentación por simulación de sistemas. Su finalidad es cuantificar tendencias de formación continua, identificar patrones demográficos y de preferencia académica, y aportar métricas para la actualización curricular y las alianzas institucionales.

### 5.2. Estructura oficial del informe — páginas 1 y 2

El informe final debe conservar estas siete partes. Preparar el repositorio no equivale a redactarlas ahora.

| Parte | Contenido requerido |
| --- | --- |
| 1. Introducción y Justificación | Antecedentes, objetivos general y específicos, pertinencia y relación con ARCUSUR. |
| 2. Marco Metodológico | Diseño cuantitativo y exploratorio, población, recolección y pipeline: Data Wrangling, estadística univariada, bivariada con chi-cuadrado y visualización computacional. |
| 3. Análisis de la Demanda Educativa y Perfil de los Titulados | Caracterización demográfica y temporal, media y desviación estándar de antigüedad de egreso, matrices, formación continua y financiamiento. |
| 4. Análisis de la Oferta Laboral y Requerimientos del Sector Productivo | Áreas tecnológicas demandadas, competencias técnicas y transversales y ejemplos de modelamiento analítico. |
| 5. Diagnóstico y Síntesis de Resultados | Cruces de variables, matrices de frecuencia cruzada y hallazgos para la Dirección de Carrera. |
| 6. Conclusiones y Propuestas Estratégicas | Acciones sustentadas en resultados: becas, posgrados modulares, vinculación empresarial B2B y observatorio permanente. |
| 7. Referencias Bibliográficas | Normativa APA, séptima edición. |

### 5.3. Historias de usuario — páginas 3 a 5

| Historia | Captura o función requerida | Resultado analítico previsto |
| --- | --- | --- |
| HU1. Registro de Antigüedad y Empleabilidad, página 3 | Año de egreso numérico obligatorio, ocupación actual y sector mediante opciones cerradas. | Media, mediana y desviación estándar de antigüedad; perfil temporal e inserción laboral por cohorte. Los umbrales de segmentación profesional se definirán después. |
| HU2. Preferencias de Formación Continua y Áreas de Interés, páginas 3 y 4 | Nivel de posgrado de interés y área prioritaria; selección limitada o jerárquica según la regla que se apruebe. | Frecuencias absolutas y relativas y concentración de preferencias para orientar la oferta académica. El ejemplo del 70 % es ilustrativo, no una regla automática. |
| HU3. Identificación de Mecanismos de Financiamiento, página 4 | Financiamiento estimado para estudios futuros: recursos propios, becas, créditos o patrocinio empresarial; categorías excluyentes o ponderadas por definir. | Contingencias financiamiento × posgrado y prueba de independencia chi-cuadrado, cuando sea aplicable. |
| HU4. Evaluación de Brechas en Competencias Profesionales, página 4 | Valoración de competencias técnicas y transversales; la historia sugiere Likert de 1 a 5 y texto estructurado opcional. | Diagnóstico de satisfacción y brechas, con puntajes y medidas de dispersión compatibles con el instrumento aprobado. |
| HU5. Automatización de Reportes y Matrices de Frecuencia Cruzada, página 5 | Exportación tabular limpia CSV/Excel, matrices cruzadas, gráficos interactivos, mapas de calor y radar, y dashboard automatizado en el backend. | Reportes generados a partir del procesamiento y análisis, con trazabilidad y transparencia para la gestión académica. |

La HU5 debe conservarse como **automatización de reportes**, además de la exportación de archivos. La periodicidad, los disparadores y las plantillas se definirán en su etapa; el PDF no exige expresamente envíos por correo ni tareas programadas.

### 5.4. Producto final — página 5

Hay dos entregables: el documento final de Análisis de Mercado y la herramienta de software de recolección de datos. La herramienta también debe permitir experimentar y analizar escenarios para apoyar decisiones académicas. El modelo de simulación, sus entradas y sus salidas todavía deben definirse.

### 5.5. Recomendaciones y plazos — páginas 5 y 6

El trabajo requiere evidencia verificable de toma de datos en campo. La guía pide individualizar al menos tres empresas de desarrollo de software o del ámbito tecnológico y menciona una del ámbito público y otra del privado. Confirmar si estas últimas se incluyen en el mínimo o se agregan a él. Registrar posteriormente las organizaciones, el vínculo con titulados y la evidencia de participación. El tamaño de la muestra estadística se definirá aparte; ese mínimo de organizaciones no garantiza suficiencia para pruebas inferenciales.

La guía señala entrega el **13 de septiembre de los corrientes**. Conservar esa referencia documental y confirmar con el docente el plazo vigente del trabajo; no convertirla en una nueva fecha de entrega ni bloquear por ello la preparación del repositorio.

### 5.6. Anexo: encuesta a titulados — páginas 6 a 13

| Bloque revisado | Variables o temas presentes |
| --- | --- |
| Presentación y datos generales, página 6 | Contexto de autoevaluación, edad y género. La copia muestra únicamente «35 años o más» y «Hombre» como opciones visibles; el catálogo completo requiere confirmación. |
| Formación complementaria realizada, página 7 | Realización o participación en programas, nivel más alto, institución, financiamiento del programa y nombre. |
| Interés en formación futura, página 8 | Interés en posgrado, área, organización educativa preferida y opinión sobre afinidad de la oferta FCyT. |
| Trayectoria y situación laboral, página 9 | Años de vida profesional, tiempo desempleado y situación laboral actual. |
| Emprendimiento, páginas 9 y 10 | Origen, producto o servicio, financiamiento externo, satisfacción con el negocio e importancia de la formación universitaria. |
| Acceso y trayectoria de empleo, página 10 | Primer empleo actual, tiempo para conseguir el primer trabajo relacionado con la formación y cantidad de empleos. |
| Valoración de la carrera, páginas 11 y 12 | Satisfacción global, concordancia con requerimientos laborales, aspectos útiles y aspectos que pueden mejorarse. |
| Asignaturas, páginas 12 y 13 | Asignaturas útiles y de poca utilidad profesional, con opción de otra asignatura. Hay una contradicción de cantidad en la segunda pregunta. |
| Vinculación y contacto, página 13 | Interés en una red oficial, correo, teléfono preferentemente con WhatsApp y LinkedIn. |

El texto sobre Lector inmersivo es una referencia a Microsoft Forms en el instrumento original, no una decisión de desarrollar en esa plataforma. Los saltos condicionales deberán especificarse después, tomando en cuenta formación realizada, interés en posgrado, empleo y emprendimiento.

### 5.7. Anexo: encuesta a empleadores — páginas 14 a 20

| Bloque revisado | Variables o temas presentes |
| --- | --- |
| Introducción e identificación, página 14 | Fines académicos e institucionales, confidencialidad y declaración de anonimato; correo, nombre de organización y tipo público/privado. |
| Caracterización organizacional, página 15 | Tamaño y sector de actividad de la organización. |
| Empleabilidad, página 16 | Contratación de ingenieros en los últimos cinco años, posibilidad de contratación futura, nivel de formación demandado y medios de convocatoria. |
| Puestos y tendencias, páginas 17 y 18 | Cargos con selección múltiple; preguntas abiertas sobre áreas emergentes, herramientas y competencias requeridas. |
| Formación y perfil profesional, páginas 18 a 20 | Valoraciones sobre confianza institucional, coherencia entre título y competencias, consulta a empleadores, conocimiento y coherencia del perfil, formación integral, incorporación de necesidades laborales, competencias, valores y participación en retroalimentación. |

El último bloque contiene **diez criterios distintos que aparecen dos veces**. Su escala visible es: totalmente de acuerdo, parcialmente de acuerdo, totalmente en desacuerdo y «No sabe». No asignar dos veces el peso de un criterio ni tratar «No sabe» como un nivel de acuerdo.

## 6. Pipeline completo que debe quedar registrado

Estas responsabilidades se documentan ahora y se implementarán por incrementos posteriores:

| Etapa requerida | Alcance que debe conservarse |
| --- | --- |
| Recolección | Dos cuestionarios, validaciones, opciones controladas, selecciones múltiples y preguntas condicionales. |
| Data Wrangling | Conservar datos originales; validar, depurar, normalizar y codificar; tratar faltantes, no aplicables y duplicados conforme a reglas aprobadas; derivar variables y generar una matriz analítica limpia con registro de transformaciones. Los procedimientos concretos son una propuesta para desarrollar la etapa exigida por la guía. |
| Estadística univariada | Frecuencias, porcentajes y resúmenes demográficos, temporales y de valoraciones. Media, mediana y desviación estándar donde corresponda. |
| Estadística bivariada | Tablas de contingencia, matrices cruzadas y análisis de relaciones entre variables, incluido financiamiento × posgrado. |
| Pruebas chi-cuadrado | Contraste de independencia con hipótesis, tamaño de muestra, frecuencias observadas y esperadas, estadístico, grados de libertad, valor p y comprobación de aplicabilidad. Definir criterios estadísticos en el documento correspondiente. |
| Visualización computacional | Gráficos interactivos para comunicar distribuciones y cruces, incluidos mapas de calor y radar. |
| Automatización de reportes — HU5 | Cálculos y preparación de matrices/reportes en el backend; exportación CSV/Excel y gráficos. Registrar filtros, muestra, versión del instrumento y reglas analíticas para reproducir resultados. |
| Experimentación por simulación | Escenarios basados en parámetros, reglas y supuestos explícitos; comparación de resultados para decisiones académicas. Definir el modelo antes de implementarlo. |

Los análisis y reportes deberán distinguir datos observados, valores derivados y resultados simulados. Cada porcentaje tendrá un denominador explícito. Los saltos de encuesta y «No sabe» requieren tratamiento propio. Las respuestas múltiples deben conservarse estructuradas; su tratamiento inferencial se decidirá posteriormente.

Aplicar chi-cuadrado exige verificar las condiciones del cruce; con pocos casos no basta con disponer de una tabla. Documentar asociaciones y limitaciones de la muestra sin atribuir causalidad o representatividad no demostrada.

## 7. Decisiones pendientes para los siguientes pasos

| Punto | Diferencia o definición pendiente |
| --- | --- |
| Egreso y vida profesional | La HU1 pide año de egreso; el anexo pregunta años de vida profesional. Definir ambos conceptos y las unidades del tiempo desempleado. |
| Formación futura y realizada | HU2 y HU3 requieren nivel y financiamiento de estudios futuros. El anexo detalla nivel y financiamiento de estudios realizados y solo parte de las preferencias futuras. Completar la correspondencia antes de diseñar el cruce. |
| Sector de titulados | HU1 requiere sector de desempeño; el anexo de titulados no presenta un catálogo equivalente al de empleadores. |
| Escalas | HU4 propone 1 a 5; en titulados hay escalas de tres y cuatro categorías y en empleadores una escala con «No sabe». Conservar lo observado hasta aprobar su definición. |
| Competencias | Las historias piden brechas técnicas y transversales; definir cómo las preguntas de los anexos permitirán medirlas. |
| Cantidad de asignaturas | En la página 12, la pregunta sobre asignaturas de poca utilidad pide cinco, pero la instrucción dice seleccionar dos. |
| Criterios duplicados | Aprobar una lista única de los diez criterios de empleadores, sin modificar el PDF original. |
| Edad y género | Confirmar si la copia muestra respuestas seleccionadas o un catálogo incompleto. |
| Identificación y confidencialidad | El formulario de empleadores declara anonimato y pide nombre y correo. Precisar consentimiento, uso de identificadores y acceso a contactos. |
| Respuestas y condiciones | Definir obligatorias, saltos, límites, respuestas repetidas y acceso a los cuestionarios. |
| Reportes y simulación | Acordar plantillas, filtros, disparadores de generación y escenarios académicos con entradas, reglas y salidas. |
| Trabajo de campo | Precisar el conteo de organizaciones, los criterios de participación, la muestra y el plazo vigente; verificar las organizaciones candidatas. |

Registrar estas decisiones cuando se resuelvan. La preparación del repositorio puede completarse sin cerrar ahora el instrumento, el modelo de datos o el modelo de simulación.

## 8. Criterios de finalización de este paso

- [ ] Se revisó el directorio y se conservó el trabajo existente.
- [ ] Hay un solo repositorio de proyecto, inicializado o reutilizado correctamente.
- [ ] Están preparados los cinco archivos de la sección 3, con estado documental explícito.
- [ ] README y AGENTS registran todos los requisitos del pipeline, especialmente Data Wrangling, análisis univariado/bivariado, chi-cuadrado, visualización computacional y automatización de reportes.
- [ ] Están documentados los dos cuestionarios y los dos entregables finales.
- [ ] La guía y los datos de campo no se incorporaron automáticamente al historial.
- [ ] Se revisó el contenido seleccionado y el estado de Git; no se incluyeron secretos ni resultados inventados.
- [ ] La situación del remoto está documentada y no se declaró una publicación que no se realizó.
- [ ] La aplicación y los documentos detallados de los pasos siguientes siguen pendientes.

La comprobación de esta etapa consiste en revisar archivos, coherencia documental y estado del repositorio. Las pruebas de compilación, base de datos, cálculos o interfaz corresponderán a las etapas de implementación.

## 9. Siguiente paso previsto

Definir y documentar los requisitos de los dos cuestionarios, las ambigüedades del instrumento y los criterios de aceptación. Después se desarrollarán, paso a paso, los documentos de datos y Data Wrangling, estadística, visualización, reportes, simulación, arquitectura y despliegue. Este orden es una propuesta de trabajo; dichos documentos todavía no se crean en esta tarea.
