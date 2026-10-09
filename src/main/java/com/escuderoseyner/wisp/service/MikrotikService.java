package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.AccionCola;
import com.escuderoseyner.wisp.model.Cola;
import com.escuderoseyner.wisp.model.ComandoTerminal;
import com.escuderoseyner.wisp.model.ConsumoMensual;
import com.escuderoseyner.wisp.model.EstadoAccion;
import com.escuderoseyner.wisp.model.ModoRed;
import com.escuderoseyner.wisp.model.MotivoAccion;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.repository.AccionColaRepository;
import com.escuderoseyner.wisp.repository.ColaRepository;
import com.escuderoseyner.wisp.repository.ConsumoMensualRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.security.ControlTokensRed;
import com.escuderoseyner.wisp.security.TokenRed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Lo que el MikroTik consulta y reporta. Formato de texto simple, fácil de leer con scripts de RouterOS:
// una línea por dato y campos separados por "|". El último campo de cada línea puede ser texto libre.
//
// Respuesta de GET /api/mikrotik/acciones:
//   # wisp-sync v1
//   M|CONTROL                     modo de la red
//   P|<cola padre>                el script nunca la modifica
//   I|<segundos>                  intervalo de consulta
//   X|<cola protegida>            el script nunca la modifica
//   R|<nombre>|<ip>               colas que debe reportar (y a qué IP hacer ping)
//   Q|<id>|<nombre>|<target>|<max-limit>|<parent>|<queue>|<yes/no disabled>|<comentario>   (solo en CONTROL)
//   C|<id>                        comando de la terminal (solo en CONTROL); el texto se pide aparte
//   # fin                         si no llega, el script descarta la respuesta
//
// Reporte (POST /api/mikrotik/reporte), una línea por cola:
//   Q|<nombre>|<existe 1/0>|<target>|<max-limit>|<parent>|<queue>|<disabled>|<rate>|<bytes>|<ping 1/0>|<comentario>
@Slf4j
@Service
public class MikrotikService {

    public static final String CABECERA = "# wisp-sync v1";
    public static final String FIN = "# fin";
    private static final int MAX_ACCIONES_POR_CONSULTA = 50;
    private static final int CAMPOS_REPORTE = 12;

    private final RedRepository redRepository;
    private final ColaRepository colaRepository;
    private final AccionColaRepository accionColaRepository;
    private final ConsumoMensualRepository consumoMensualRepository;
    private final SincronizacionService sincronizacionService;
    private final ControlTokensRed controlTokensRed;
    private final TerminalService terminalService;
    private final ScriptRouterOsService scriptRouterOsService;

    public MikrotikService(RedRepository redRepository, ColaRepository colaRepository,
                           AccionColaRepository accionColaRepository, ConsumoMensualRepository consumoMensualRepository,
                           SincronizacionService sincronizacionService, ControlTokensRed controlTokensRed,
                           TerminalService terminalService, ScriptRouterOsService scriptRouterOsService) {
        this.redRepository = redRepository;
        this.colaRepository = colaRepository;
        this.accionColaRepository = accionColaRepository;
        this.consumoMensualRepository = consumoMensualRepository;
        this.sincronizacionService = sincronizacionService;
        this.controlTokensRed = controlTokensRed;
        this.terminalService = terminalService;
        this.scriptRouterOsService = scriptRouterOsService;
    }

    // ---------- Autenticación ----------

    // Busca la red por el hash del token. Lanza TokenRedInvalidoException si no coincide.
    @Transactional
    public Red autenticar(String token, String ip) {
        if (controlTokensRed.ipBloqueada(ip)) {
            throw new TokenRedInvalidoException();
        }
        String limpio = token == null ? null : token.trim();
        Red red = TokenRed.formatoValido(limpio)
                ? redRepository.findByTokenHash(TokenRed.hash(limpio)).orElse(null)
                : null;
        if (red == null) {
            controlTokensRed.registrarFallo(ip);
            throw new TokenRedInvalidoException();
        }
        red.setUltimaConexion(LocalDateTime.now());
        red.setUltimaIp(ip);
        return red;
    }

    // ---------- Consulta ----------

    @Transactional
    public String consultar(String token, String ip) {
        Red red = autenticar(token, ip);
        // Red de seguridad: si algo cambió sin pasar por la web (o antes de existir esta parte), se corrige aquí
        sincronizacionService.recalcularRed(red, MotivoAccion.CAMBIO, null);

        StringBuilder texto = new StringBuilder();
        linea(texto, CABECERA);
        linea(texto, "M|" + red.getModo());
        linea(texto, "P|" + red.getColaPadre());
        linea(texto, "I|" + red.getIntervaloSegundos());
        // Colas protegidas: el script del router se niega a tocarlas aunque llegue una acción
        for (String protegida : RedService.separarProtegidas(red.getColasProtegidas())) {
            linea(texto, "X|" + protegida);
        }

        for (Cola cola : colaRepository.findByRedId(red.getId())) {
            if (!RedService.esColaProtegida(red, cola.getNombre())) {
                linea(texto, "R|" + cola.getNombre() + "|" + cola.getTarget().replace("/32", ""));
            }
        }

        if (red.getModo() == ModoRed.CONTROL) {
            LocalDateTime ahora = LocalDateTime.now();
            List<AccionCola> acciones = accionColaRepository.findPorRedYEstados(red.getId(), SincronizacionService.POR_ENVIAR);
            for (AccionCola accion : acciones.stream().limit(MAX_ACCIONES_POR_CONSULTA).toList()) {
                Cola cola = accion.getCola();
                if (RedService.esColaProtegida(red, cola.getNombre())) {
                    // No debería pasar (la validación lo impide); si pasa, nunca se envía
                    accion.setEstado(EstadoAccion.ERROR);
                    accion.setError("La cola es la cola padre o una cola protegida: no se envía.");
                    accion.setResueltaEn(ahora);
                    continue;
                }
                linea(texto, String.join("|", "Q", String.valueOf(accion.getId()), cola.getNombre(), cola.getTarget(),
                        cola.getMaxLimit(), cola.getParent(), cola.getTipoCola(),
                        cola.getDeshabilitada() ? "yes" : "no", cola.getComentario()));
                if (accion.getEstado() == EstadoAccion.PENDIENTE) {
                    accion.setEstado(EstadoAccion.ENVIADA);
                    accion.setEnviadaEn(ahora);
                }
            }
        }

        // Terminal remota: cada comando se anuncia una sola vez (tomarParaEnviar lo marca ENVIADO)
        for (ComandoTerminal comando : terminalService.tomarParaEnviar(red)) {
            linea(texto, "C|" + comando.getId());
        }

        linea(texto, FIN);
        return texto.toString();
    }

    // ---------- Instalación por el script "puente" ----------

    // El puente consulta cada 2 minutos y SOLO ejecuta la respuesta si empieza con su marcador.
    // Token inválido o nada pendiente -> respuesta vacía (el puente no hace nada).
    // La instalación se entrega hasta que llega el primer reporte del script instalado.
    @Transactional
    public String bootstrap(String token, String ip, String urlDePeticion) {
        Red red;
        try {
            red = autenticar(token, ip);
        } catch (TokenRedInvalidoException e) {
            return "";
        }
        if (!red.getInstalacionPendiente()) {
            return "";
        }
        try {
            return scriptRouterOsService.generarParaPuente(red, token.trim(), urlDePeticion);
        } catch (ReglaNegocioException e) {
            // Ej: APP_URL_PUBLICA mal configurada. Mejor no entregar nada que un script roto.
            log.warn("No se pudo generar la instalación para la red {}: {}", red.getId(), e.getMessage());
            return "";
        }
    }

    // ---------- Terminal remota ----------

    @Transactional
    public String textoComando(String token, String ip, Long comandoId) {
        return terminalService.textoParaEjecutar(autenticar(token, ip), comandoId);
    }

    @Transactional
    public void resultadoComando(String token, String ip, Long comandoId, boolean exito, String salida) {
        terminalService.recibirResultado(autenticar(token, ip), comandoId, exito, salida);
    }

    // ---------- Confirmación de una acción ----------

    @Transactional
    public void confirmarAccion(String token, String ip, Long accionId, boolean aplicada, String mensaje) {
        Red red = autenticar(token, ip);
        AccionCola accion = accionColaRepository.findById(accionId)
                .filter(a -> a.getCola().getRed().getId().equals(red.getId())) // solo acciones de SU red
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe esa acción."));
        if (!SincronizacionService.POR_ENVIAR.contains(accion.getEstado())) {
            return; // ya resuelta o reemplazada: se ignora
        }
        accion.setEstado(aplicada ? EstadoAccion.APLICADA : EstadoAccion.ERROR);
        accion.setError(aplicada ? null : textoError(mensaje));
        accion.setResueltaEn(LocalDateTime.now());
    }

    // ---------- Reporte ----------

    // Devuelve cuántas colas se actualizaron
    @Transactional
    public int recibirReporte(String token, String ip, String cuerpo) {
        Red red = autenticar(token, ip);
        Map<String, Cola> porNombre = new HashMap<>();
        for (Cola cola : colaRepository.findByRedId(red.getId())) {
            porNombre.put(cola.getNombre().toLowerCase(Locale.ROOT), cola);
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDate periodo = ahora.toLocalDate().withDayOfMonth(1);
        int actualizadas = 0;
        for (String linea : cuerpo.split("\\r?\\n")) {
            if (!linea.startsWith("Q|")) {
                continue;
            }
            String[] c = linea.split("\\|", CAMPOS_REPORTE);
            if (c.length < CAMPOS_REPORTE) {
                continue;
            }
            Cola cola = porNombre.get(c[1].trim().toLowerCase(Locale.ROOT));
            // Solo colas gestionadas por la web: cualquier otra cosa se ignora
            if (cola == null || RedService.esColaProtegida(red, cola.getNombre())) {
                continue;
            }
            aplicarReporte(cola, c, periodo, ahora);
            actualizadas++;
        }

        red.setUltimoReporte(ahora);
        // El script completo ya está funcionando: el "puente" ya no necesita descargar la instalación
        red.setInstalacionPendiente(false);
        return actualizadas;
    }

    private void aplicarReporte(Cola cola, String[] c, LocalDate periodo, LocalDateTime ahora) {
        boolean existe = "1".equals(c[2].trim());
        cola.setRepExiste(existe);
        cola.setReportadoEn(ahora);
        cola.setPingOk(FormatoRouterOs.booleano(c[10]));
        if (!existe) {
            cola.setRepTarget(null);
            cola.setRepMaxLimit(null);
            cola.setRepParent(null);
            cola.setRepTipoCola(null);
            cola.setRepComentario(null);
            cola.setRepDeshabilitada(null);
            cola.setRateSubidaBps(null);
            cola.setRateBajadaBps(null);
            return;
        }
        cola.setRepTarget(FormatoRouterOs.textoRecibido(c[3], 60));
        cola.setRepMaxLimit(FormatoRouterOs.textoRecibido(c[4], 40));
        cola.setRepParent(FormatoRouterOs.textoRecibido(c[5], 60));
        cola.setRepTipoCola(FormatoRouterOs.textoRecibido(c[6], 80));
        cola.setRepDeshabilitada(FormatoRouterOs.booleano(c[7]));
        cola.setRepComentario(FormatoRouterOs.textoRecibido(c[11], 150));

        long[] rate = FormatoRouterOs.parSubidaBajada(c[8]);
        cola.setRateSubidaBps(rate == null ? null : rate[0]);
        cola.setRateBajadaBps(rate == null ? null : rate[1]);

        long[] bytes = FormatoRouterOs.parSubidaBajada(c[9]);
        if (bytes != null) {
            sumarConsumo(cola, bytes[0], bytes[1], periodo);
            cola.setBytesSubida(bytes[0]);
            cola.setBytesBajada(bytes[1]);
        }
    }

    // Los contadores del MikroTik son acumulados: se suma la diferencia con el reporte anterior.
    // Si el contador bajó, el router o la cola se reinició: todo lo actual es consumo nuevo.
    // En el primer reporte no se suma nada (no se sabe desde cuándo se acumuló).
    private void sumarConsumo(Cola cola, long subida, long bajada, LocalDate periodo) {
        if (cola.getBytesSubida() == null || cola.getBytesBajada() == null) {
            return;
        }
        long deltaSubida = subida >= cola.getBytesSubida() ? subida - cola.getBytesSubida() : subida;
        long deltaBajada = bajada >= cola.getBytesBajada() ? bajada - cola.getBytesBajada() : bajada;
        if (deltaSubida == 0 && deltaBajada == 0) {
            return;
        }
        ConsumoMensual consumo = consumoMensualRepository
                .findByClienteIdAndPeriodo(cola.getCliente().getId(), periodo)
                .orElseGet(() -> {
                    ConsumoMensual nuevo = new ConsumoMensual();
                    nuevo.setCliente(cola.getCliente());
                    nuevo.setPeriodo(periodo);
                    return nuevo;
                });
        consumo.setBytesSubida(consumo.getBytesSubida() + deltaSubida);
        consumo.setBytesBajada(consumo.getBytesBajada() + deltaBajada);
        consumoMensualRepository.save(consumo);
    }

    // ---------- Utilidades ----------

    private static void linea(StringBuilder texto, String linea) {
        texto.append(linea).append('\n');
    }

    private static String textoError(String mensaje) {
        String limpio = FormatoRouterOs.textoRecibido(mensaje, 500);
        return limpio == null || limpio.isEmpty() ? "El MikroTik no indicó el error." : limpio;
    }
}
