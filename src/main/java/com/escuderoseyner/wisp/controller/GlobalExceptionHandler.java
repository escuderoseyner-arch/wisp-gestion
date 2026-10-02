package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.ErrorResponse;
import com.escuderoseyner.wisp.service.CredencialesInvalidasException;
import com.escuderoseyner.wisp.service.ReglaNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

// Atrapa las excepciones de TODOS los controladores y las convierte en JSON en español
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CredencialesInvalidasException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse credencialesInvalidas(CredencialesInvalidasException e) {
        return ErrorResponse.de(e.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse reglaNegocio(ReglaNegocioException e) {
        return ErrorResponse.de(e.getMessage());
    }

    // Falló alguna validación de @Valid (@NotBlank, @Size, ...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse datosInvalidos(MethodArgumentNotValidException e) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .toList();
        return new ErrorResponse("Hay datos inválidos en la petición.", detalles);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse jsonInvalido(HttpMessageNotReadableException e) {
        return ErrorResponse.de("El cuerpo de la petición no es un JSON válido.");
    }
}
