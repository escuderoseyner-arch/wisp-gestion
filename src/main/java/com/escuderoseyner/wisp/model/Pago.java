package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Getter
@Setter
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT de MySQL
    private Integer id;

    // Un cliente tiene muchos pagos (uno por mes)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // Primer día del mes que se paga (ej: 2026-10-01 = octubre)
    @Column(nullable = false)
    private LocalDate periodo;

    // BigDecimal para dinero: double tiene errores de redondeo
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPago metodo;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    // El admin que registró el pago
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por", nullable = false)
    private Usuario registradoPor;

    @Column(length = 255)
    private String observacion;

    // Lo llena MySQL solo (DEFAULT CURRENT_TIMESTAMP), Java no lo toca
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
