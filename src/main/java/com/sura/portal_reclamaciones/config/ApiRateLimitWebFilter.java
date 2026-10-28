package com.sura.portal_reclamaciones.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Limitador en memoria para solicitudes HTTP de la API.
 *
 * Límites predeterminados:
 * - Login: 10 solicitudes por minuto y dirección IP.
 * - Resto de /api/**: 120 solicitudes por minuto y dirección IP.
 *
 * No confía en X-Forwarded-For para evitar suplantaciones de la IP.
 * Para varias instancias de producción se recomienda un limitador distribuido.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitWebFilter implements WebFilter {

    private static final long VENTANA_MS = 60_000L;

    private final int limiteApi;
    private final int limiteLogin;

    private final Map<String, ContadorVentana> contadores =
            new ConcurrentHashMap<>();

    private volatile long proximaLimpieza;

    public ApiRateLimitWebFilter(
            @Value("${app.rate-limit.api-per-minute:120}") int limiteApi,
            @Value("${app.rate-limit.login-per-minute:10}") int limiteLogin) {

        this.limiteApi = Math.max(1, limiteApi);
        this.limiteLogin = Math.max(1, limiteLogin);
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        String ruta = exchange.getRequest().getURI().getPath();

        // El limitador se aplica a las rutas de la API, no a los recursos estáticos.
        if (!ruta.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        boolean esLogin = "/api/auth/login".equals(ruta);
        int limite = esLogin ? limiteLogin : limiteApi;

        String ip = obtenerDireccionCliente(exchange);
        String clave = ip + (esLogin ? "|LOGIN" : "|API");

        long ahora = System.currentTimeMillis();
        limpiarVentanasVencidas(ahora);

        boolean[] permitido = new boolean[] { false };

        contadores.compute(clave, (key, actual) -> {

            if (actual == null
                    || ahora - actual.inicioVentana >= VENTANA_MS) {

                permitido[0] = limite >= 1;
                return new ContadorVentana(ahora);
            }

            int cantidad = actual.cantidad.incrementAndGet();
            permitido[0] = cantidad <= limite;

            return actual;
        });

        if (permitido[0]) {
            return chain.filter(exchange);
        }

        return responderLimiteExcedido(exchange, esLogin);
    }

    private Mono<Void> responderLimiteExcedido(
            ServerWebExchange exchange,
            boolean esLogin) {

        var respuesta = exchange.getResponse();

        respuesta.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        respuesta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        respuesta.getHeaders().set("Retry-After", "60");
        respuesta.getHeaders().setCacheControl("no-store");

        String mensaje = esLogin
                ? "Demasiados intentos de inicio de sesión. Intenta más tarde."
                : "Se alcanzó el límite de solicitudes. Intenta más tarde.";

        byte[] cuerpo = (
                "{\"mensaje\":\"" + mensaje + "\"}"
        ).getBytes(StandardCharsets.UTF_8);

        return respuesta.writeWith(Mono.just(
                respuesta.bufferFactory().wrap(cuerpo)
        ));
    }

    private String obtenerDireccionCliente(ServerWebExchange exchange) {

        InetSocketAddress remota =
                exchange.getRequest().getRemoteAddress();

        if (remota == null) {
            return "IP_DESCONOCIDA";
        }

        InetAddress direccion = remota.getAddress();

        if (direccion == null) {
            return remota.getHostString();
        }

        return direccion.getHostAddress();
    }

    private void limpiarVentanasVencidas(long ahora) {

        if (ahora < proximaLimpieza) {
            return;
        }

        synchronized (this) {

            if (ahora < proximaLimpieza) {
                return;
            }

            contadores.entrySet().removeIf(
                    entrada ->
                            ahora - entrada.getValue().inicioVentana
                                    >= VENTANA_MS
            );

            proximaLimpieza = ahora + VENTANA_MS;
        }
    }

    private static final class ContadorVentana {

        private final long inicioVentana;
        private final AtomicInteger cantidad;

        private ContadorVentana(long inicio) {
            this.inicioVentana = inicio;
            this.cantidad = new AtomicInteger(1);
        }
    }
}