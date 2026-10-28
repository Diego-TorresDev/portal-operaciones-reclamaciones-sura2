package com.sura.portal_reclamaciones.service;

import com.sura.portal_reclamaciones.entity.AuditLog;
import com.sura.portal_reclamaciones.entity.HolidayCache;
import com.sura.portal_reclamaciones.entity.ParameterConfig;
import com.sura.portal_reclamaciones.repository.AuditLogRepository;
import com.sura.portal_reclamaciones.repository.HolidayCacheRepository;
import com.sura.portal_reclamaciones.repository.ParameterConfigRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Este servicio consulta los festivos nacionales de Colombia y los conserva
// en PostgreSQL para no depender de una llamada externa por cada reclamación.
@Service
public class FestivosColombiaService {

    private final HolidayCacheRepository festivos;
    private final ParameterConfigRepository parametros;
    private final AuditLogRepository auditoria;
    private final ObjectMapper objectMapper;

    public FestivosColombiaService(
            HolidayCacheRepository festivos,
            ParameterConfigRepository parametros,
            AuditLogRepository auditoria,
            ObjectMapper objectMapper
    ) {
        this.festivos = festivos;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.objectMapper = objectMapper;
    }

    // Entregamos tanto las fechas como el estado de validación para que
    // el motor pueda advertir cuando tuvo que calcular sin festivos.
    public record ResultadoFestivos(
            Set<LocalDate> fechas,
            boolean validados,
            String fuente,
            String alerta
    ) {
    }

    // Obtenemos una única colección de festivos para el año solicitado.
    // Si ya existe en la base, usamos la caché y no llamamos a la API.
    @Transactional
    public ResultadoFestivos obtenerFestivos(int anio) {
        if (anio < 2000 || anio > 2100) {
            return resultadoSinFestivos(
                    anio,
                    "El año solicitado está fuera del rango permitido."
            );
        }

        List<HolidayCache> guardados =
                festivos.findByCountryCodeAndApiYearOrderByHolidayDateAsc(
                        "CO", anio
                );

        if (!guardados.isEmpty()) {
            Set<LocalDate> fechasGuardadas = new LinkedHashSet<>();

            for (HolidayCache festivo : guardados) {
                fechasGuardadas.add(festivo.getHolidayDate());
            }

            return new ResultadoFestivos(
                    fechasGuardadas,
                    true,
                    "CACHE",
                    null
            );
        }

        // Solo consultamos el servicio externo cuando todavía no hay
        // festivos almacenados para el año requerido.
        try {
            return consultarYGuardar(anio);
        } catch (Exception error) {
            // No exponemos la URL completa, respuestas del proveedor ni
            // detalles técnicos al usuario que consulta el portal.
            registrarIncidente(anio);

            return resultadoSinFestivos(
                    anio,
                    "ANS sin validar festivos"
            );
        }
    }

    // Descargamos y validamos toda la respuesta antes de guardar registros.
    // De esta forma no dejamos una caché parcial si la API responde mal.
    private ResultadoFestivos consultarYGuardar(int anio) throws Exception {
        String plantilla = textoParametro(
                "URL_API_FESTIVOS",
                "https://date.nager.at/api/v3/PublicHolidays/{anio}/CO"
        );

        String url = plantilla
                .replace("{anio}", String.valueOf(anio))
                .replace("{year}", String.valueOf(anio));

        int segundos = enteroParametro("TIMEOUT_API_SEGUNDOS", 5);

        // El timeout tiene un límite razonable, incluso si el parámetro
        // se configura accidentalmente con un valor demasiado grande.
        segundos = Math.max(1, Math.min(segundos, 30));

        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(segundos))
                .build();

        HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(segundos))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> respuesta;

        try {
            respuesta = cliente.send(
                    solicitud,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException error) {
            // Respetamos la interrupción del hilo en vez de ignorarla.
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "La consulta de festivos fue interrumpida."
            );
        }

        if (respuesta.statusCode() != 200) {
            throw new IllegalStateException(
                    "El proveedor de festivos devolvió un estado no exitoso."
            );
        }

        JsonNode raiz = objectMapper.readTree(respuesta.body());

        // Nager.Date debe responder una lista, no un objeto u otro formato.
        if (raiz == null || !raiz.isArray() || raiz.isEmpty()) {
            throw new IllegalStateException(
                    "La respuesta de festivos no contiene una lista válida."
            );
        }

        Set<LocalDate> fechas = new LinkedHashSet<>();

        // Validamos primero cada fecha. Si una no es válida, rechazamos
        // la respuesta completa para no guardar una caché incompleta.
        for (JsonNode elemento : raiz) {
            String fechaTexto = elemento.path("date").asText("");

            if (fechaTexto.isBlank()) {
                throw new IllegalStateException(
                        "La respuesta contiene un festivo sin fecha."
                );
            }

            LocalDate fecha = LocalDate.parse(fechaTexto);

            if (fecha.getYear() != anio) {
                throw new IllegalStateException(
                        "La API devolvió una fecha de otro año."
                );
            }

            fechas.add(fecha);
        }

        // Ya están validadas todas las fechas. Ahora guardamos los registros
        // sin duplicar un festivo que ya hubiera sido insertado previamente.
        for (JsonNode elemento : raiz) {
            LocalDate fecha = LocalDate.parse(
                    elemento.path("date").asText()
            );

            if (festivos.findByCountryCodeAndHolidayDate("CO", fecha).isPresent()) {
                continue;
            }

            HolidayCache nuevo = new HolidayCache();
            nuevo.setCountryCode("CO");
            nuevo.setHolidayDate(fecha);
            nuevo.setApiYear(anio);
            nuevo.setLocalName(textoJson(elemento, "localName"));
            nuevo.setEnglishName(textoJson(elemento, "name"));
            nuevo.setSourceUrl(url);

            festivos.save(nuevo);
        }

        return new ResultadoFestivos(
                fechas,
                true,
                "API_NAGER_DATE",
                null
        );
    }

    // Lee un campo opcional de la respuesta sin producir errores si no existe.
    private String textoJson(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);

        if (valor == null || valor.isNull()) {
            return null;
        }

        String texto = valor.asText();

        // Evitamos insertar cadenas vacías como si fueran nombres válidos.
        return texto.isBlank() ? null : texto;
    }

    // Si no tenemos datos locales y la API falla, seguimos calculando el ANS
    // sin festivos, pero dejamos una alerta explícita en el caso y en auditoría.
    private ResultadoFestivos resultadoSinFestivos(
            int anio,
            String alerta
    ) {
        return new ResultadoFestivos(
                Set.of(),
                false,
                "SIN_FESTIVOS_VALIDOS",
                alerta
        );
    }

    // La bitácora registra el año y el tipo de incidente, no datos personales.
    private void registrarIncidente(int anio) {
        AuditLog registro = new AuditLog();
        registro.setActorUsername("SISTEMA");
        registro.setActionCode("FALLO_API_FESTIVOS");
        registro.setResourceType("API_FESTIVOS");
        registro.setResourceId(String.valueOf(anio));
        registro.setResult("ERROR");
        registro.setMessage(
                "No fue posible validar los festivos colombianos."
        );

        registro.setDetails(java.util.Map.of(
                "anio", anio,
                "pais", "CO",
                "respaldo", "CALCULO_SIN_FESTIVOS"
        ));

        auditoria.save(registro);
    }

    private String textoParametro(String clave, String valorPorDefecto) {
        return parametros.findById(clave)
                .map(ParameterConfig::getParameterValue)
                .filter(valor -> !valor.isBlank())
                .orElse(valorPorDefecto);
    }

    private int enteroParametro(String clave, int valorPorDefecto) {
        try {
            return Integer.parseInt(
                    textoParametro(
                            clave,
                            String.valueOf(valorPorDefecto)
                    ).trim()
            );
        } catch (Exception error) {
            return valorPorDefecto;
        }
    }
}