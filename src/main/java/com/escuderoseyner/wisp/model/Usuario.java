package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT de MySQL
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // Opcional: sirve para recuperar la contraseña por correo
    @Column(unique = true, length = 100)
    private String email;

    // Hash BCrypt, nunca la contraseña en texto plano
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // Último cambio o restablecimiento de contraseña (script 003). Los tokens emitidos
    // antes de esta fecha dejan de valer. null = nunca se cambió desde que existe la columna.
    @Column(name = "password_cambiado_en")
    private LocalDateTime passwordCambiadoEn;

    // Cambia el hash y anota cuándo, al segundo (los tokens guardan su hora de emisión en segundos)
    public void cambiarPasswordHash(String hash) {
        this.passwordHash = hash;
        this.passwordCambiadoEn = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    // Un usuario CLIENTE está ligado a un solo cliente, y un cliente a un solo usuario.
    // Para un ADMIN queda en null.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", unique = true)
    private Cliente cliente;

    @Column(name = "nombre_mostrar", nullable = false, length = 100)
    private String nombreMostrar;

    @Column(nullable = false)
    private Boolean activo = true;

    // Obliga a cambiar la contraseña inicial en el primer login
    @Column(name = "debe_cambiar_password", nullable = false)
    private Boolean debeCambiarPassword = true;

    // Para bloquear la cuenta tras varios intentos fallidos
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    // Lo llena MySQL solo (DEFAULT CURRENT_TIMESTAMP), Java no lo toca
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
