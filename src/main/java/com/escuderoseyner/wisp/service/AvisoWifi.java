package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.Cliente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Reglas del aviso de pago por Wi-Fi. Sin base de datos ni Spring: recibe todo lo que necesita,
// así se puede probar sola (ver AvisoWifiTest).
//
//  - Aviso SUAVE: tiene un mes impago y ya llegó su día de pago.
//  - Aviso VENCIDO: además, ese mes ya pasó el día de pago + los días de tolerancia.
//  - "Entendido" lo saca del aviso hasta mañana a las 8:00.
//  - Las IP terminadas en .1 a .10 (MikroTik e infraestructura) nunca reciben aviso.
public final class AvisoWifi {

    public enum Etapa { NINGUNA, SUAVE, VENCIDO }

    // .1 a .10 son del MikroTik y la infraestructura
    public static final int ULTIMO_OCTETO_INFRAESTRUCTURA = 10;
    // Después de "Entendido", el aviso vuelve al día siguiente a esta hora
    public static final LocalTime HORA_VUELVE = LocalTime.of(8, 0);

    private AvisoWifi() {
    }

    // false si la IP termina en .1 a .10 o no es una IPv4 (acepta "192.168.10.25" o "192.168.10.25/32")
    public static boolean ipPermitida(String ip) {
        if (ip == null) {
            return false;
        }
        String sinMascara = ip.trim().replace("/32", "");
        String[] partes = sinMascara.split("\\.");
        if (partes.length != 4) {
            return false;
        }
        try {
            int ultimo = Integer.parseInt(partes[3]);
            return ultimo > ULTIMO_OCTETO_INFRAESTRUCTURA && ultimo <= 255;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Meses impagos cuyo día de pago ya llegó, del más antiguo al más reciente
    public static List<YearMonth> mesesEnAviso(Cliente cliente, Set<YearMonth> pagados, LocalDate hoy) {
        List<YearMonth> meses = new ArrayList<>();
        if (cliente.getFechaInicio() == null || cliente.getDiaPago() == null) {
            return meses;
        }
        YearMonth actual = YearMonth.from(hoy);
        for (YearMonth m = YearMonth.from(cliente.getFechaInicio()); !m.isAfter(actual); m = m.plusMonths(1)) {
            if (!pagados.contains(m) && CalculadoraEstadoMes.aplica(cliente, m)
                    && !hoy.isBefore(m.atDay(cliente.getDiaPago()))) {
                meses.add(m);
            }
        }
        return meses;
    }

    public static Etapa etapa(Cliente cliente, Set<YearMonth> pagados, int diasTolerancia, LocalDate hoy) {
        List<YearMonth> meses = mesesEnAviso(cliente, pagados, hoy);
        if (meses.isEmpty()) {
            return Etapa.NINGUNA;
        }
        boolean vencido = meses.stream()
                .anyMatch(m -> hoy.isAfter(CalculadoraEstadoMes.vencimiento(cliente, m, diasTolerancia)));
        return vencido ? Etapa.VENCIDO : Etapa.SUAVE;
    }

    // ¿Tocó "Entendido" y todavía no llega la hora de volver a mostrarle el aviso?
    public static boolean pospuesto(Cliente cliente, LocalDateTime ahora) {
        return cliente.getAvisoPospuestoHasta() != null && ahora.isBefore(cliente.getAvisoPospuestoHasta());
    }

    // Filas [id del cliente, periodo] de PagoRepository -> meses pagados de cada cliente
    public static Map<Integer, Set<YearMonth>> agruparPagados(List<Object[]> filas) {
        Map<Integer, Set<YearMonth>> pagados = new HashMap<>();
        for (Object[] fila : filas) {
            pagados.computeIfAbsent((Integer) fila[0], id -> new HashSet<>()).add(YearMonth.from((LocalDate) fila[1]));
        }
        return pagados;
    }

    // Mañana a las 8:00 (si lo toca a las 23:50, no vuelve a medianoche)
    public static LocalDateTime pospuestoHasta(LocalDateTime ahora) {
        return ahora.toLocalDate().plusDays(1).atTime(HORA_VUELVE);
    }
}
