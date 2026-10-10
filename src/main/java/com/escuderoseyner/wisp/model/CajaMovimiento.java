package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Entrada o salida de dinero de una caja. La columna generada periodo_descuento (solo MySQL)
// impide dos descuentos del mismo mes; Java no la necesita.
@Entity
@Table(name = "caja_movimientos")
@Getter
@Setter
public class CajaMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caja_id", nullable = false)
    private Caja caja;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimientoCaja tipo;

    // Siempre positivo
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 255)
    private String descripcion;

    // Solo INGRESO_PAGO: el pago que lo originó
    @Column(name = "pago_id", unique = true)
    private Integer pagoId;

    // Solo DESCUENTO_MENSUAL: primer día del mes descontado
    private LocalDate periodo;

    // null = lo hizo el sistema (automático)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
