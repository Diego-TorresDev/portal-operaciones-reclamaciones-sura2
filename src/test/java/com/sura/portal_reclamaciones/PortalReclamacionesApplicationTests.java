package com.sura.portal_reclamaciones;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class PortalReclamacionesApplicationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("sura_reclamaciones_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configurarBaseDePruebas(DynamicPropertyRegistry registry) {
        // La prueba de contexto siempre usa PostgreSQL efÃ­mero de Testcontainers,
        // no localhost ni la base persistente de Compose o del desarrollador.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.bootstrap-users.enabled", () -> "false");
        registry.add("app.bootstrap-users.reset-passwords", () -> "false");
    }

    @Test
    void contextLoads() {
        // Si el contexto arranca, Hibernate valida el esquema y Flyway aplica
        // las migraciones contra la base efÃ­mera, que se destruye al terminar.
    }
}