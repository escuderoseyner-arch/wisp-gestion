package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.ErrorResponse;
import com.escuderoseyner.wisp.service.CredencialesInvalidasException;
import com.escuderoseyner.wisp.service.RecursoNoEncontradoException;
import com.escuderoseyner.wisp.service.ReglaNegocioException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
        return new ErrorResponse(e.getMessage(), e.getDetalles());
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

    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse noEncontrado(RecursoNoEncontradoException e) {
        return ErrorResponse.de(e.getMessage());
    }

    // Ej: /api/admin/planes/abc cuando se esperaba un número
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse parametroInvalido(MethodArgumentTypeMismatchException e) {
        return ErrorResponse.de("El valor de \"" + e.getName() + "\" no es válido.");
    }

    // La base de datos rechazó el cambio (ej: dos admins crean el mismo nombre a la vez
    // y el UNIQUE de MySQL frena al segundo). No se muestra el error técnico.
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflictoDeDatos(DataIntegrityViolationException e) {
        return ErrorResponse.de("No se pudo guardar porque choca con datos existentes (por ejemplo, un nombre repetido).");
    }
}
