package com.sura.portal_reclamaciones.service;

import com.sura.portal_reclamaciones.dto.importacion.ResultadoValidacionExcel;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Este servicio comprueba que el Excel tenga las hojas y columnas esperadas.
// Todavía no importa los registros a PostgreSQL.
@Service
public class ExcelReclamacionesService {

    // Son las seis hojas operativas que utilizaremos para evaluar los casos.
    private static final Map<String, List<String>> ENCABEZADOS_REQUERIDOS =
            crearEncabezadosRequeridos();

    // Dejamos definidos los nombres de las columnas para detectar cambios
    // accidentales en el archivo que se vaya a cargar.
    private static Map<String, List<String>> crearEncabezadosRequeridos() {
        Map<String, List<String>> hojas = new LinkedHashMap<>();

        hojas.put("02_Casos", List.of(
                "ID_Caso", "Fecha_Radicacion", "Tipo_Documento",
                "Numero_Documento", "Nombre_Afiliado", "Numero_Poliza",
                "Tipo_Servicio", "Codigo_Diagnostico", "NIT_Prestador",
                "Fecha_Servicio", "Valor_Reclamado", "Factura_Adjunta",
                "Historia_Clinica_Adjunta", "Orden_Medica_Adjunta",
                "Canal", "Observaciones"
        ));

        hojas.put("03_Polizas", List.of(
                "Numero_Poliza", "Plan", "Estado",
                "Fecha_Inicio", "Fecha_Fin", "Valor_Consumido_Anio"
        ));

        hojas.put("04_Prestadores", List.of(
                "NIT_Prestador", "Nombre_Prestador", "En_Red", "Alerta_Fraude"
        ));

        hojas.put("05_Diagnosticos", List.of(
                "Codigo_CIE10", "Descripcion",
                "Requiere_Revision_Medica", "Excluido"
        ));

        hojas.put("06_Servicios", List.of(
                "Tipo_Servicio", "Requiere_Factura",
                "Requiere_Historia_Clinica", "Requiere_Orden_Medica"
        ));

        hojas.put("07_Planes", List.of(
                "Plan", "Monto_Max_Aprobacion_Automatica", "Tope_Anual"
        ));

        return hojas;
    }

    // Recibe el nombre y el contenido del archivo que envió el controlador.
    public ResultadoValidacionExcel validarEstructura(
            String nombreArchivo,
            byte[] contenido
    ) {
        List<String> errores = new ArrayList<>();
        Map<String, Integer> filasPorHoja = new LinkedHashMap<>();

        // Apache POI abre el libro directamente desde memoria.
        try (Workbook libro = WorkbookFactory.create(
                new ByteArrayInputStream(contenido))) {

            // DataFormatter permite leer celdas de distintos tipos como texto.
            DataFormatter formato =
                    new DataFormatter(Locale.forLanguageTag("es-CO"));

            for (Map.Entry<String, List<String>> entrada
                    : ENCABEZADOS_REQUERIDOS.entrySet()) {

                String nombreHoja = entrada.getKey();
                List<String> encabezadosEsperados = entrada.getValue();
                Sheet hoja = libro.getSheet(nombreHoja);

                // Si falta una hoja obligatoria, registramos el problema
                // y continuamos revisando las demás.
                if (hoja == null) {
                    errores.add("Falta la hoja obligatoria: " + nombreHoja);
                    continue;
                }

                Row filaEncabezados = hoja.getRow(0);

                if (filaEncabezados == null) {
                    errores.add("La hoja " + nombreHoja
                            + " no tiene una fila de encabezados.");
                    filasPorHoja.put(nombreHoja, 0);
                    continue;
                }

                // Comparamos cada columna con el nombre esperado.
                for (int columna = 0;
                     columna < encabezadosEsperados.size();
                     columna++) {

                    String esperado = encabezadosEsperados.get(columna);
                    String encontrado = formato.formatCellValue(
                            filaEncabezados.getCell(
                                    columna,
                                    Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                            )
                    ).trim();

                    if (!esperado.equals(encontrado)) {
                        errores.add("En la hoja " + nombreHoja
                                + ", columna " + (columna + 1)
                                + ": se esperaba '" + esperado
                                + "' y se encontró '"
                                + (encontrado.isBlank() ? "(vacío)" : encontrado)
                                + "'.");
                    }
                }

                // Contamos filas que tengan al menos una celda con contenido.
                // Así evitamos contar las filas que estén completamente vacías.
                int cantidadFilas = 0;

                for (int numeroFila = 1;
                     numeroFila <= hoja.getLastRowNum();
                     numeroFila++) {

                    Row fila = hoja.getRow(numeroFila);

                    if (fila == null) {
                        continue;
                    }

                    boolean tieneContenido = false;
                    short ultimaCelda = fila.getLastCellNum();

                    for (int columna = 0; columna < ultimaCelda; columna++) {
                        String valor = formato.formatCellValue(
                                fila.getCell(
                                        columna,
                                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                                )
                        ).trim();

                        if (!valor.isEmpty()) {
                            tieneContenido = true;
                            break;
                        }
                    }

                    if (tieneContenido) {
                        cantidadFilas++;
                    }
                }

                filasPorHoja.put(nombreHoja, cantidadFilas);
            }

            // Sin reclamaciones no tendría sentido continuar con la importación.
            if (filasPorHoja.getOrDefault("02_Casos", 0) == 0) {
                errores.add("La hoja 02_Casos no contiene reclamaciones.");
            }

            boolean valido = errores.isEmpty();

            return new ResultadoValidacionExcel(
                    valido,
                    nombreArchivo,
                    valido
                            ? "La estructura del Excel es válida."
                            : "El archivo tiene problemas de estructura.",
                    filasPorHoja,
                    errores
            );

        } catch (Exception excepcion) {
            // No enviamos detalles técnicos del error al usuario.
            return new ResultadoValidacionExcel(
                    false,
                    nombreArchivo,
                    "No se pudo leer el archivo. Comprueba que sea un Excel .xlsx válido.",
                    filasPorHoja,
                    List.of("El contenido no corresponde a un libro Excel legible.")
            );
        }
    }
}