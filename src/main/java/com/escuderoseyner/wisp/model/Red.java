package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// Un MikroTik que consulta a la web (modelo "pull": la web nunca se conecta al router).
@Entity
@Table(name = "redes")
@Getter
@Setter
public class Red {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 60)
    private String nombre;

    // Cola que agrupa a los clientes (ej: "Total-Clientes"). El sistema NUNCA la modifica.
    @Column(name = "cola_padre", nullable = false, length = 40)
    private String colaPadre;

    // Otras colas del router que el sistema nunca toca (ej: "Oficina"), separadas por coma
    @Column(name = "colas_protegidas", length = 500)
    private String colasProtegidas;

    @Column(name = "intervalo_segundos", nullable = false)
    private Integer intervaloSegundos = 60;

    // SHA-256 del token del MikroTik. El token real nunca se guarda.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModoRed modo = ModoRed.SOLO_LECTURA;

    // true: el script "puente" del router descarga la instalación completa en su próxima consulta
    @Column(name = "instalacion_pendiente", nullable = false)
    private Boolean instalacionPendiente = true;

    @Column(name = "ultima_conexion")
    private LocalDateTime ultimaConexion;

    @Column(name = "ultimo_reporte")
    private LocalDateTime ultimoReporte;

    @Column(name = "ultima_ip", length = 45)
    private String ultimaIp;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;
}
