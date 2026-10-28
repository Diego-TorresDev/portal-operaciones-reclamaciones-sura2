# Portal de Reclamaciones de Salud — Backend

Backend REST para evaluacion y seguimiento de reclamaciones. Incluye autenticacion JWT, motor de reglas, revision humana, lotes de pago simulados, seguimiento, auditoria, reportes y exportacion CSV.

## Tecnologias

- Java 17 y Spring Boot 4.1.2.
- Spring WebFlux, Spring Security y JWT Bearer.
- Spring Data JPA / Hibernate, PostgreSQL y Flyway.
- Maven y Springdoc OpenAPI.
- Docker Compose para ejecutar la aplicacion y PostgreSQL.

## Requisitos

- Docker Desktop con el motor de Docker en ejecucion, o Java 17 y Maven Wrapper para trabajar fuera de Docker.
- Configuracion local de variables de entorno.
- Para el despliegue con Compose, usar `.env.example` como plantilla y crear un `.env` local con valores propios. No compartir ni versionar `.env`.

## Ejecutar con Docker Compose

Desde la raiz de este proyecto, crear la configuracion local a partir de la plantilla:

```powershell
Copy-Item .env.example .env
notepad .env
```

Antes de iniciar, sustituir los valores de ejemplo por una contrasena de base de datos y una clave JWT aleatorias y seguras. No utilizar los marcadores de ejemplo como credenciales reales.

Iniciar los servicios:

```powershell
docker compose up -d --build
docker compose ps
docker compose logs --tail 100 app
```

La aplicacion se publica por defecto en `http://localhost:8081`; PostgreSQL se mantiene en la red interna de Compose.

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

Para detener los servicios sin eliminar los datos persistentes:

```powershell
docker compose stop
```

No usar `docker compose down -v` en el entorno de demostracion: puede eliminar los volumenes persistentes.

## Ejecucion y pruebas con Maven

Con Java 17 instalado, y las variables de entorno y la base de datos configuradas segun `src/main/resources/application.properties`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Para una verificacion completa de compilacion y pruebas:

```powershell
.\mvnw.cmd clean verify
```

La ultima ejecucion registrada de la suite reporto **21 pruebas, 0 fallos, 0 errores y 0 omitidas**. Si se vuelve a ejecutar la suite, algunas pruebas pueden requerir Docker para Testcontainers.

## API principal

| Funcion | Ruta | Acceso |
|---|---|---|
| Inicio de sesion | `POST /api/auth/login` | Publico; requiere credenciales validas |
| Listado de casos | `GET /api/casos` | JWT |
| Detalle de caso | `GET /api/casos/{identificador}` | JWT |
| Seguimiento | `GET /api/casos/{identificador}/tracking` | JWT |
| Tomar caso | `POST /api/casos/{codigoCaso}/tomar` | ANALISTA |
| Cambiar estado | `PATCH /api/casos/{codigoCaso}/estado` | JWT y validaciones de negocio |
| Administrar parametros | `PUT /api/parametros/**` | ADMIN |
| Crear lote | `POST /api/lotes` | ANALISTA o ADMIN |
| Confirmar pago simulado | `PATCH /api/lotes/{id}/pagar` | ADMIN |
| Resumen | `GET /api/reportes/resumen` | JWT |
| Exportar CSV | `GET /api/reportes/exportar` | JWT |

Consultar Swagger/OpenAPI para los esquemas y parametros exactos de cada operacion.

## Escenario de demostracion

- Insumo de prueba: 60 casos.
- Motor de reglas: 17 reglas, con 1.020 evaluaciones individuales.
- Resultado registrado: 30 casos AUTOMATICO y 30 MANUAL.
- `FECHA_REFERENCIA`: `2026-10-14`.
- Lote de pago simulado: 31 casos cerrados y 29 casos en bandeja.
- No se realiza ninguna transferencia bancaria real.

Estos datos describen el escenario existente de demostracion. No reimportar el Excel ni reiniciar la base de datos para preparar la entrega.

## Exportacion CSV en la demostracion

El CSV se descargara en vivo desde la aplicacion durante la demostracion. Por ese motivo, el archivo de resultados no se incluye en esta entrega.

La exportacion prevista contiene las columnas `ID_Caso`, `Decision`, `Reglas_Incumplidas`, `Motivo`, `Prioridad`, `Estado_Actual`, `Fecha_Limite_ANS` y `Semaforo_ANS`.

## Seguridad y limitaciones conocidas

- JWT sin sesion de servidor y clave configurada externamente.
- La aplicacion aplica controles de acceso por rol y registra eventos de seguimiento/auditoria.
- El pago es simulado.
- La documentacion de seguridad identifica aspectos pendientes de endurecimiento antes de produccion, como ciclo de vida y revocacion de JWT, HTTPS, limitacion de solicitudes, gestion/rotacion de secretos, CORS, monitorizacion y continuidad.
- Swagger/OpenAPI se encuentra habilitado para facilitar la evaluacion; revisar su exposicion antes de un despliegue productivo.
- No incluir `.env`, claves, contrasenas ni tokens en el repositorio o en la entrega.

## Documentacion incluida

- `ARQUITECTURA_TECNICA.pdf` y `ARQUITECTURA_TECNICA.docx`.
- `INFORME_SEGURIDAD.pdf` y `INFORME_SEGURIDAD.docx`.
- `README_CONTENEDORES.md`: instrucciones complementarias para Docker Compose.
