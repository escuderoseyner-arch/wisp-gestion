package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Un comando de RouterOS que un ADMIN encola y el MikroTik ejecuta en su próxima consulta
@Entity
@Table(name = "comandos_terminal")
@Getter
@Setter
public class ComandoTerminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "red_id", nullable = false)
    private Red red;

    @Column(nullable = false, length = 2000)
    private String comando;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoComando estado = EstadoComando.PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String salida;

    @Column(name = "salida_truncada", nullable = false)
    private Boolean salidaTruncada = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    @Column(name = "finalizado_en")
    private LocalDateTime finalizadoEn;
}
