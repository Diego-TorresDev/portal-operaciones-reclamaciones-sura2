package com.sura.portal_reclamaciones.dto.importacion;

import java.util.List;

// Resume cuántos casos evaluó el motor y cuáles fueron sus decisiones.
public record ResultadoEvaluacionLote(
        int totalEncontrados,
        int evaluados,
        int automaticos,
        int manuales,
        int omitidos,
        String mensaje,
        List<String> errores
) {
}