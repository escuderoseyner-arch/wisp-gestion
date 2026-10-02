package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// Datos de la empresa que usa el sistema. La tabla tiene UNA sola fila (id = 1).
@Entity
@Table(name = "configuracion")
@Getter
@Setter
public class Configuracion {

    // Sin @GeneratedValue: el id siempre es 1, no es AUTO_INCREMENT
    @Id
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer id = 1;

    @Column(name = "nombre_empresa", nullable = false, length = 100)
    private String nombreEmpresa;

    @Column(length = 11)
    private String ruc;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(name = "whatsapp_soporte", nullable = false, length = 15)
    private String whatsappSoporte;

    @Column(name = "yape_numero", length = 15)
    private String yapeNumero;

    @Column(name = "yape_titular", length = 100)
    private String yapeTitular;

    @Column(name = "cuenta_bancaria", length = 150)
    private String cuentaBancaria;

    // En MySQL es TINYINT (0 a 15), no un booleano
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "dias_tolerancia", nullable = false)
    private Integer diasTolerancia = 3;

    @Column(nullable = false, length = 5)
    private String moneda = "S/";

    // Mensaje de WhatsApp con {nombre}, {monto} y {mes}
    @Column(name = "plantilla_recordatorio", nullable = false, length = 500)
    private String plantillaRecordatorio;

    // Lo actualiza MySQL solo (ON UPDATE CURRENT_TIMESTAMP), Java no lo toca
    @Column(name = "actualizado_en", insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;
}
