

package com.sura.portal_reclamaciones.dto.parametros;
// Valida que el motivo tenga contenido, además de respetar su longitud.
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos que se reciben cuando ADMIN modifica un parámetro.
 *
 * El usuario que realiza el cambio no se recibe en el JSON:
 * ese dato lo obtendremos del JWT validado por Spring Security.
 */
public record ActualizarParametroRequest(

        // Admitimos texto vacío porque FECHA_REFERENCIA puede quedar sin definir.
        @NotNull(message = "Debes enviar el nuevo valor.")
        @Size(max = 4000, message = "El valor supera el tamaño permitido.")
        String valor,

        // Cada cambio debe explicar por qué se modifica la configuración.
        @NotBlank(message = "El motivo del cambio es obligatorio.")
        @Size(min = 10, max = 500,
                message = "El motivo debe tener entre 10 y 500 caracteres.")
        String motivo

) {
}
