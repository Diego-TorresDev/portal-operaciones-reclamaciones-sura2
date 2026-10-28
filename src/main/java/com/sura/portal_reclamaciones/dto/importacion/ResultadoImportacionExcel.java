package com.sura.portal_reclamaciones.dto.importacion;

import java.util.List;

/**
 * Resume el resultado de la importación de un archivo Excel.
 *
 * Permite identificar las filas con incidencias generales y los casos
 * que presentan errores monetarios, aunque hayan sido importados.
 */
public record ResultadoImportacionExcel(
        boolean exitoso,
        Long loteId,
        String estado,
        int totalFilas,
        int importadas,
        int filasConError,
        int casosConErrorMonetario,
        String mensaje,
        List<String> errores
) {
}