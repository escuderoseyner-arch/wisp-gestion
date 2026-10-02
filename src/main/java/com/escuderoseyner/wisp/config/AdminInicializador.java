package com.escuderoseyner.wisp.config;

import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

// ApplicationRunner: Spring ejecuta run() una vez, justo después de arrancar la app.
// Si no hay ningún ADMIN, lo crea con ADMIN_USERNAME y ADMIN_PASSWORD.
@Slf4j
@Component
public class AdminInicializador implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminInicializador(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                              @Value("${app.admin-inicial.username}") String username,
                              @Value("${app.admin-inicial.password}") String password) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByRol(Rol.ADMIN)) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            log.warn("No existe ningún ADMIN y faltan ADMIN_USERNAME / ADMIN_PASSWORD: no se creó el admin inicial.");
            return;
        }
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 8 || bytes > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD debe tener entre 8 y 72 caracteres");
        }

        Usuario admin = new Usuario();
        admin.setUsername(username.trim());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRol(Rol.ADMIN);
        admin.setNombreMostrar("Administrador");
        // La contraseña inicial queda escrita en la configuración de IntelliJ,
        // así que se obliga a cambiarla en el primer login
        admin.setDebeCambiarPassword(true);
        usuarioRepository.save(admin);

        // Nunca se escribe la contraseña en el log
        log.info("Admin inicial '{}' creado. Deberá cambiar su contraseña en el primer login.", admin.getUsername());
    }
}
