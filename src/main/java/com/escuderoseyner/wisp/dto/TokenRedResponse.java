package com.escuderoseyner.wisp.dto;

// ÚNICA vez que el token sale del servidor: al crear la red o al cambiar el token.
// Si el admin escribió su propio token, aquí va null (no se devuelve lo que él ya sabe).
public record TokenRedResponse(RedResponse red, String token) {
}
