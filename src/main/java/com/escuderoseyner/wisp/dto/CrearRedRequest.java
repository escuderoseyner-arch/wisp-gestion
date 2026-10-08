package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.ModoRed;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Crear una red. "token" es opcional: vacío = la web genera uno nuevo;
// con valor = se usa ese (ej: la clave que ya está en el script "puente" del router).
public record CrearRedRequest(
        @NotBlank(message = "El nombre de la red es obligatorio")
        @Size(max = 60, message = "El nombre de la red no puede tener más de 60 caracteres")
        String nombre,

        @NotBlank(message = "La cola padre es obligatoria")
        @Size(max = 40, message = "La cola padre no puede tener más de 40 caracteres")
        String colaPadre,

        @Size(max = 500, message = "Las colas protegidas no pueden tener más de 500 caracteres")
        String colasProtegidas,

        @NotNull(message = "El intervalo de consulta es obligatorio")
        @Min(value = 30, message = "El intervalo de consulta debe estar entre 30 y 3600 segundos")
        @Max(value = 3600, message = "El intervalo de consulta debe estar entre 30 y 3600 segundos")
        Integer intervaloSegundos,

        @NotNull(message = "El modo es obligatorio")
        ModoRed modo,

        @Size(max = 128, message = "El token no puede tener más de 128 caracteres")
        String token
) implements DatosRed {
}
