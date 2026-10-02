package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.CambiarPasswordRequest;
import com.escuderoseyner.wisp.dto.LoginRequest;
import com.escuderoseyner.wisp.dto.LoginResponse;
import com.escuderoseyner.wisp.dto.UsuarioActualResponse;
import com.escuderoseyner.wisp.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Público: no necesita token
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // @AuthenticationPrincipal Jwt: el token ya validado; getSubject() es el username
    @PostMapping("/cambiar-password")
    public LoginResponse cambiarPassword(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody CambiarPasswordRequest request) {
        return authService.cambiarPassword(jwt.getSubject(), request);
    }

    @GetMapping("/me")
    public UsuarioActualResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.usuarioActual(jwt.getSubject());
    }
}
