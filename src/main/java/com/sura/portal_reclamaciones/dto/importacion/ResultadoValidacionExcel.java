package com.sura.portal_reclamaciones.dto.importacion;

import java.util.List;
import java.util.Map;

// Agrupa el resultado de la revisión para devolverlo al usuario como JSON.
public record ResultadoValidacionExcel(
        boolean valido,
        String nombreArchivo,
        String mensaje,
        Map<String, Integer> filasPorHoja,
        List<String> errores
) {
}