# Portal de Reclamaciones: pruebas y contenedores

## Pruebas automatizadas

El nuevo `MotorReglasServiceTest` usa Mockito para simular todos los repositorios y RugIA. No abre una conexión a PostgreSQL ni hace solicitudes a Groq. La prueba de contexto existente (`PortalReclamacionesApplicationTests`) sí levanta Spring y puede usar la base configurada en `application.properties`; para mantener el aislamiento de las pruebas unitarias, ejecute primero:

```powershell
.\mvnw.cmd -Dtest=MotorReglasServiceTest test
```

Luego ejecute la suite completa cuando la base de pruebas/local esté disponible:

```powershell
.\mvnw.cmd test
```

## Arranque con Docker Compose

1. Copie `.env.example` a `.env` y cambie al menos `DB_PASSWORD` y `APP_JWT_SECRET` por secretos aleatorios, exclusivos para este entorno. No comparta el archivo `.env` ni lo suba a Git.
2. Confirme las variables exactas que necesita `UsuarioBootstrapRunner` para crear los usuarios iniciales. `APP_BOOTSTRAP_USERS_ENABLED` queda desactivado por defecto hasta confirmar esa configuración.
3. Construya e inicie:

```powershell
docker compose config
docker compose up --build -d
docker compose ps
docker compose logs -f app
```

El servicio `db` usa un volumen Docker nuevo y distinto a la base local. Flyway ejecutará automáticamente las migraciones versionadas en `src/main/resources/db/migration`. No monte ni copie la base de datos del equipo anfitrión.

La aplicación se publica por defecto en `http://localhost:8081`; el puerto de PostgreSQL no se publica hacia Windows. Cambie `APP_PORT` si ese puerto ya está ocupado.

Para detener los servicios sin borrar la base del contenedor:

```powershell
docker compose down
```

No use `docker compose down -v` salvo que quiera eliminar permanentemente el volumen de PostgreSQL del entorno de contenedores.

## Consideraciones

- En el Dockerfile se omiten las pruebas durante la construcción de la imagen porque la prueba de contexto actual requiere que una base de datos esté disponible. Ejecute las pruebas de Maven por separado.
- `.env` y archivos de datos de Excel/CSV quedan fuera del contexto de construcción mediante `.dockerignore`.
- El volumen de PostgreSQL 18 se monta en `/var/lib/postgresql`.
- La primera inicialización crea un esquema vacío, no importa Excel ni crea reglas/catálogos por defecto excepto lo que contengan las migraciones Flyway. Los usuarios de acceso inicial dependen de la configuración de `UsuarioBootstrapRunner`.
