package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Una cola (simple queue) de un cliente en el MikroTik: lo que la web quiere (estado deseado)
// y lo último que informó el router (estado reportado, columnas rep_*).
@Entity
@Table(name = "colas")
@Getter
@Setter
public class Cola {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "red_id", nullable = false)
    private Red red;

    @Column(nullable = false, length = 40)
    private String nombre;

    // Dueño actual. Si el código pasa a otra persona, la cola cambia de dueño.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // ---------- Estado deseado ----------

    @Column(nullable = false, length = 18)
    private String target;

    @Column(name = "max_limit", nullable = false, length = 20)
    private String maxLimit;

    @Column(nullable = false, length = 40)
    private String parent;

    @Column(name = "tipo_cola", nullable = false, length = 60)
    private String tipoCola;

    @Column(nullable = false, length = 100)
    private String comentario;

    @Column(nullable = false)
    private Boolean deshabilitada;

    @Column(name = "deseado_en", nullable = false)
    private LocalDateTime deseadoEn;

    // ---------- Último reporte del MikroTik (null = todavía no reporta) ----------

    @Column(name = "rep_existe")
    private Boolean repExiste;

    @Column(name = "rep_target", length = 60)
    private String repTarget;

    @Column(name = "rep_max_limit", length = 40)
    private String repMaxLimit;

    @Column(name = "rep_parent", length = 60)
    private String repParent;

    @Column(name = "rep_tipo_cola", length = 80)
    private String repTipoCola;

    @Column(name = "rep_comentario", length = 150)
    private String repComentario;

    @Column(name = "rep_deshabilitada")
    private Boolean repDeshabilitada;

    @Column(name = "rate_subida_bps")
    private Long rateSubidaBps;

    @Column(name = "rate_bajada_bps")
    private Long rateBajadaBps;

    // Contadores acumulados del MikroTik (se reinician al reiniciar el router o la cola)
    @Column(name = "bytes_subida")
    private Long bytesSubida;

    @Column(name = "bytes_bajada")
    private Long bytesBajada;

    @Column(name = "ping_ok")
    private Boolean pingOk;

    @Column(name = "reportado_en")
    private LocalDateTime reportadoEn;
}
