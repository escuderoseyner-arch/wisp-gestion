package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Getter
@Setter
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT de MySQL
    private Integer id;

    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombres;

    // Opcional desde el script 002: hay clientes sin número
    @Column(length = 15)
    private String celular;

    @Column(length = 200)
    private String referencia;

    // Caserío, sector o barrio (ej: "Zona Norte"). Agregada en el script 002.
    @Column(length = 50)
    private String zona;

    // Muchos clientes pueden tener el mismo plan.
    // LAZY: el plan solo se consulta a la base de datos cuando se usa.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // En MySQL es TINYINT (1 a 28), no un booleano
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "dia_pago", nullable = false)
    private Integer diaPago;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    // Solo tiene valor cuando el cliente está RETIRADO
    @Column(name = "fecha_retiro")
    private LocalDate fechaRetiro;

    @Column(length = 15)
    private String ip;

    // MikroTik al que pertenece (script 005). null = no se gestiona desde la web.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "red_id")
    private Red red;

    // Nombre de su cola en el MikroTik. Por defecto, el código (ej: C-07).
    @Column(name = "nombre_cola", length = 40)
    private String nombreCola;

    // Corte manual desde la web: su cola queda deshabilitada aunque esté ACTIVO
    @Column(name = "corte_manual", nullable = false)
    private Boolean corteManual = false;

    // STRING: guarda el texto "ACTIVO" y no un número (0, 1, 2)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCliente estado = EstadoCliente.ACTIVO;

    // Lo llena MySQL solo (DEFAULT CURRENT_TIMESTAMP), Java no lo toca
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    // Columna calculada por MySQL: vale el código si no está retirado, o NULL.
    // Solo se lee; Java nunca la escribe.
    @Column(name = "codigo_vigente", length = 10, insertable = false, updatable = false)
    private String codigoVigente;
}
