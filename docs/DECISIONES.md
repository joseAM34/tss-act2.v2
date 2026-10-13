# Decisiones del proyecto

Estado: decisiones registradas y primer incremento implementado y verificado. El instrumento definitivo y el pipeline analítico siguen pendientes.

## Encuesta de empleadores: criterios únicos

Fecha: 2026-10-13.

El usuario indicó: «si no las repitas».

En la sección «Formación y perfil profesional» del PDF, los diez criterios aparecen dos veces entre las páginas 18 y 20. La aplicación mostrará cada criterio una sola vez y almacenará una respuesta por criterio en cada encuesta. Los análisis contarán cada criterio una sola vez.

La siguiente tabla resume los criterios para identificarlos y documentar sus ubicaciones. Al implementar la encuesta se utilizará el texto de su primera aparición en el PDF.

| Criterio resumido | Primera aparición | Repetición que se omitirá |
| --- | --- | --- |
| Confianza en la UMSS como formadora de profesionales | 18 | 19 |
| Correspondencia del título con la formación y competencias | 18 | 19 |
| Consulta regular a empleadores sobre el perfil y competencias | 18 | 19 |
| Conocimiento del perfil profesional y las competencias de egreso | 18 | 19 |
| Coherencia del perfil declarado con el desempeño laboral | 18 | 20 |
| Formación para un desempeño profesional destacado e integral | 18 | 20 |
| Incorporación de la opinión de empleadores en prácticas, pasantías y titulación | 19 | 20 |
| Competencias laborales demostradas por los titulados | 19 | 20 |
| Valores y actitudes profesionales demostrados por los titulados | 19 | 20 |
| Participación del empleador en evaluación o retroalimentación | 19 | 20 |

El PDF original se conserva como fuente documental.

## Acceso a las encuestas y al panel

El usuario indicó que cualquiera puede acceder a las encuestas, y que las respuestas y las demás funciones deben requerir contraseña.

- Las encuestas a titulados y empleadores serán públicas y podrán responderse sin iniciar sesión.
- La consulta de respuestas, estadísticas, reportes, exportaciones y administración requerirá autenticación.
- El backend verificará la autorización de acceso a los datos y operaciones del panel.

## Arquitectura

La aplicación es monolítica con backend, conforme a la solicitud del usuario. Al autorizar el comienzo del desarrollo se adoptó la base técnica recomendada: Java 21, Spring Boot, Thymeleaf, Maven y PostgreSQL. H2 se utiliza para pruebas y desarrollo local.

El primer incremento comprende cuestionarios de borrador, persistencia, autenticación administrativa, consulta de respuestas y exportación CSV. Las propuestas del instrumento se documentan en `docs/INSTRUMENTO.md`.

## Repositorio

El usuario proporcionó `https://github.com/joseAM34/tss-act2.v2.git` como destino remoto. Se utiliza un único repositorio para backend, interfaz y documentación, con `main` como rama principal y `origin` como remoto.
