package com.escuderoseyner.wisp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Bytes que usó un cliente en un mes, sumando lo que reporta el MikroTik
@Entity
@Table(name = "consumo_mensual")
@Getter
@Setter
public class ConsumoMensual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // Primer día del mes
    @Column(nullable = false)
    private LocalDate periodo;

    @Column(name = "bytes_subida", nullable = false)
    private Long bytesSubida = 0L;

    @Column(name = "bytes_bajada", nullable = false)
    private Long bytesBajada = 0L;

    @Column(name = "actualizado_en", insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;
}
