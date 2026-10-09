package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// "Deja esta cola como dice la web". Los valores se leen de la cola al momento de enviarla,
// así el router siempre recibe el estado deseado más reciente.
@Entity
@Table(name = "acciones_cola")
@Getter
@Setter
public class AccionCola {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cola_id", nullable = false)
    private Cola cola;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoAccion motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAccion estado = EstadoAccion.PENDIENTE;

    @Column(length = 500)
    private String error;

    // null = la generó el sistema
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "enviada_en")
    private LocalDateTime enviadaEn;

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;
}
