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

    // Código de país para los enlaces wa.me (ej: "51" = Perú). Agregada en el script 004.
    @Column(name = "codigo_pais", nullable = false, length = 4)
    private String codigoPais = "51";

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

    // Textos de la página de aviso por Wi-Fi (script 010), con {nombre}, {monto} y {mes}
    @Column(name = "aviso_texto_suave", nullable = false, length = 500)
    private String avisoTextoSuave = TEXTO_SUAVE;

    @Column(name = "aviso_texto_vencido", nullable = false, length = 500)
    private String avisoTextoVencido = TEXTO_VENCIDO;

    @Column(name = "aviso_texto_suspendido", nullable = false, length = 500)
    private String avisoTextoSuspendido = TEXTO_SUSPENDIDO;

    // Los mismos valores por defecto que en 010_aviso_wifi.sql
    public static final String TEXTO_SUAVE = "Hola {nombre}, te recordamos con cariño que ya llegó tu fecha de pago "
            + "de internet ({mes}): {monto}. Si ya pagaste, envíanos tu comprobante. ¡Gracias por tu preferencia!";
    public static final String TEXTO_VENCIDO = "Hola {nombre}, tu pago de internet de {mes} ya venció. Debes {monto}. "
            + "Por favor paga pronto para que tu servicio no se suspenda. Si ya pagaste, envíanos tu comprobante.";
    public static final String TEXTO_SUSPENDIDO = "Hola {nombre}, tu servicio de internet está suspendido. "
            + "Debes {monto} ({mes}). Paga y envíanos tu comprobante por WhatsApp para volver a activarlo.";

    // Lo actualiza MySQL solo (ON UPDATE CURRENT_TIMESTAMP), Java no lo toca
    @Column(name = "actualizado_en", insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;
}
