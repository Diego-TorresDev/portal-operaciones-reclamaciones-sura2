package com.sura.portal_reclamaciones.config;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final String jwtSecret;

    public SecurityConfig(
            @Value("${app.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            CorsConfigurationSource corsConfigurationSource) {

        return http
                // API sin sesión: autenticación mediante JWT.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance()
                )

                // Se aplica la política CORS explícita definida abajo.
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource)
                )

                .authorizeExchange(exchanges -> exchanges

                        // Archivos estáticos necesarios para mostrar el login.
                        .pathMatchers(
                                "/",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico"
                        ).permitAll()

                        // Documentación OpenAPI.
                        .pathMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/webjars/**"
                        ).permitAll()

                        // Las solicitudes preflight de CORS no necesitan JWT.
                        // La política CORS sigue determinando qué origen se acepta.
                        .pathMatchers(
                                HttpMethod.OPTIONS,
                                "/api/**"
                        ).permitAll()

                        // El login se puede consultar sin token.
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        ).permitAll()

                        // Solo ADMIN puede modificar parámetros.
                        .pathMatchers(
                                HttpMethod.PUT,
                                "/api/parametros",
                                "/api/parametros/**"
                        ).hasAuthority("SCOPE_ADMIN")

                        // Solo ADMIN puede confirmar pagos simulados.
                        .pathMatchers(
                                HttpMethod.PATCH,
                                "/api/lotes/*/pagar"
                        ).hasAuthority("SCOPE_ADMIN")

                        // Solo ANALISTA puede tomar un caso.
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/casos/*/tomar"
                        ).hasAuthority("SCOPE_ANALISTA")

                        // Las decisiones corresponden al analista asignado.
                        .pathMatchers(
                                HttpMethod.PATCH,
                                "/api/casos/*/estado"
                        ).hasAuthority("SCOPE_ANALISTA")

                        // ADMIN y ANALISTA pueden generar lotes.
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/lotes"
                        ).hasAnyAuthority(
                                "SCOPE_ANALISTA",
                                "SCOPE_ADMIN"
                        )

                        // El resto de los endpoints requiere JWT válido.
                        .anyExchange().authenticated()
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(Customizer.withDefaults())
                )

                .build();
    }

    /**
     * CORS restrictivo para la API.
     *
     * El origen se configura mediante APP_CORS_ALLOWED_ORIGIN.
     * Por defecto, el portal servido por Spring Boot usa localhost:8080.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin:http://localhost:8080}")
            String allowedOrigin) {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept"
        ));

        // Permite que el navegador lea el nombre del archivo exportado.
        configuration.setExposedHeaders(List.of("Content-Disposition"));

        // Se usan tokens Bearer, no cookies de sesión.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {

        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "La clave JWT debe tener al menos 32 bytes para HS256."
            );
        }

        SecretKey secretKey = new SecretKeySpec(
                keyBytes,
                "HmacSHA256"
        );

        return NimbusReactiveJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}