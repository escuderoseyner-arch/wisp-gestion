package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Dinero acumulado de una red. El saldo NO se guarda: es la suma de sus movimientos.
@Entity
@Table(name = "cajas")
@Getter
@Setter
public class Caja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "red_id", nullable = false, unique = true)
    private Red red;

    // Pago mensual (ej: Starlink) que se descuenta solo
    @Column(name = "descuento_monto", nullable = false, precision = 8, scale = 2)
    private BigDecimal descuentoMonto = BigDecimal.ZERO;

    // Día del mes (1 a 28) en que se descuenta
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "descuento_dia", nullable = false)
    private Integer descuentoDia = 1;

    @Column(name = "descuento_activo", nullable = false)
    private Boolean descuentoActivo = false;

    // Primer día del primer mes que se descuenta
    @Column(name = "descuento_desde", nullable = false)
    private LocalDate descuentoDesde;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
