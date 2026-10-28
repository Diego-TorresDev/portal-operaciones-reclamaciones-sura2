
package com.sura.portal_reclamaciones.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.sura.portal_reclamaciones.entity.AuditLog;
import com.sura.portal_reclamaciones.entity.ParameterConfig;
import com.sura.portal_reclamaciones.repository.AuditLogRepository;
import com.sura.portal_reclamaciones.repository.HolidayCacheRepository;
import com.sura.portal_reclamaciones.repository.ParameterConfigRepository;

import tools.jackson.databind.ObjectMapper;

class FestivosColombiaServiceTest {

    @Test
    void siLaApiFallaYNoHayCacheDevuelveAlertaYRegistraAuditoria() {

        // Simulamos los repositorios para no acceder a la base de datos real.
        HolidayCacheRepository festivos =
                mock(HolidayCacheRepository.class);

        ParameterConfigRepository parametros =
                mock(ParameterConfigRepository.class);

        AuditLogRepository auditoria =
                mock(AuditLogRepository.class);

        ObjectMapper objectMapper = new ObjectMapper();

        int anioPrueba = 2099;

        // Simulamos que todavía no hay festivos guardados para este año.
        when(festivos.findByCountryCodeAndApiYearOrderByHolidayDateAsc(
                "CO",
                anioPrueba
        )).thenReturn(List.of());

        // Usamos un puerto local cerrado para provocar un fallo de conexión
        // sin depender de Internet ni llamar al proveedor real.
        ParameterConfig parametroUrl = mock(ParameterConfig.class);
        when(parametroUrl.getParameterValue())
                .thenReturn("http://127.0.0.1:1/api/{anio}/CO");

        when(parametros.findById("URL_API_FESTIVOS"))
                .thenReturn(Optional.of(parametroUrl));

        // Limitamos el tiempo de espera de la solicitud simulada a un segundo.
        ParameterConfig parametroTimeout = mock(ParameterConfig.class);
        when(parametroTimeout.getParameterValue()).thenReturn("1");

        when(parametros.findById("TIMEOUT_API_SEGUNDOS"))
                .thenReturn(Optional.of(parametroTimeout));

        FestivosColombiaService servicio = new FestivosColombiaService(
                festivos,
                parametros,
                auditoria,
                objectMapper
        );

        // El fallo de la API no debe propagarse al consumidor.
        FestivosColombiaService.ResultadoFestivos resultado =
                assertDoesNotThrow(
                        () -> servicio.obtenerFestivos(anioPrueba)
                );

        assertFalse(
                resultado.validados(),
                "El calendario debe quedar marcado como no validado."
        );

        assertTrue(
                resultado.fechas().isEmpty(),
                "Sin caché ni respuesta de la API, no deben inventarse festivos."
        );

        assertEquals(
                "SIN_FESTIVOS_VALIDOS",
                resultado.fuente()
        );

        assertEquals(
                "ANS sin validar festivos",
                resultado.alerta()
        );

        // Confirmamos que el incidente se intentó guardar en la auditoría.
        ArgumentCaptor<AuditLog> capturador =
                ArgumentCaptor.forClass(AuditLog.class);

        verify(auditoria).save(capturador.capture());

        AuditLog registro = capturador.getValue();

        assertEquals("FALLO_API_FESTIVOS", registro.getActionCode());
        assertEquals("API_FESTIVOS", registro.getResourceType());
        assertEquals(String.valueOf(anioPrueba), registro.getResourceId());
        assertEquals("ERROR", registro.getResult());
        assertEquals(
                "No fue posible validar los festivos colombianos.",
                registro.getMessage()
        );
    }
}
