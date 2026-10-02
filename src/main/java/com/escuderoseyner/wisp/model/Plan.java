package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// @Entity: esta clase representa una tabla de la base de datos
@Entity
@Table(name = "planes")
@Getter
@Setter
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT de MySQL
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(name = "bajada_mbps", nullable = false)
    private Integer bajadaMbps;

    @Column(name = "subida_mbps", nullable = false)
    private Integer subidaMbps;

    // BigDecimal para dinero: double tiene errores de redondeo
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private Boolean activo = true;

    // Lo llena MySQL solo (DEFAULT CURRENT_TIMESTAMP), Java no lo toca
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}