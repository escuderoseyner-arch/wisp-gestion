package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ComandoTerminalResponse;
import com.escuderoseyner.wisp.model.ComandoTerminal;
import com.escuderoseyner.wisp.model.EstadoComando;
import com.escuderoseyner.wisp.model.ModoRed;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.ComandoTerminalRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

// Terminal remota: un ADMIN encola un comando de RouterOS y el MikroTik lo ejecuta en su próxima consulta.
//  - Solo en modo CONTROL (en Solo lectura la web no envía nada al router).
//  - Cada comando se envía UNA sola vez. Si no se recoge en 10 minutos, expira (no se ejecuta tarde).
//  - Si el MikroTik lo recibió pero no devuelve la salida en 10 minutos, queda en ERROR.
//  - La salida se guarda con un tope de 16 KB.
@Slf4j
@Service
public class TerminalService {

    public static final Duration VIGENCIA = Duration.ofMinutes(10);
    public static final Duration ESPERA_SALIDA = Duration.ofMinutes(10);
    public static final int MAX_SALIDA = 16 * 1024;
    public static final int MAX_PENDIENTES_POR_RED = 5;
    private static final int TAMANO_HISTORIAL = 50;

    private final ComandoTerminalRepository comandoRepository;
    private final RedRepository redRepository;
    private final UsuarioRepository usuarioRepository;

    public TerminalService(ComandoTerminalRepository comandoRepository, RedRepository redRepository,
                           UsuarioRepository usuarioRepository) {
        this.comandoRepository = comandoRepository;
        this.redRepository = redRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ---------- ADMIN ----------

    @Transactional
    public ComandoTerminalResponse encolar(Integer redId, String comandoRecibido, String usernameAdmin) {
        Red red = buscarRed(redId);
        if (red.getModo() != ModoRed.CONTROL) {
            throw new ReglaNegocioException("La red \"" + red.getNombre()
                    + "\" está en modo Solo lectura: la web no envía comandos al MikroTik.");
        }
        String comando = limpiarComando(comandoRecibido);
        if (comando.isEmpty()) {
            throw new ReglaNegocioException("Escribe un comando.");
        }
        // Regla del sistema: nunca activar fasttrack (rompe las colas de los clientes)
        if (comando.toLowerCase(Locale.ROOT).contains("fasttrack")) {
            throw new ReglaNegocioException("Por seguridad, la terminal no acepta comandos que mencionen fasttrack.");
        }
        expirarVencidos(red.getId());
        if (comandoRepository.countByRedIdAndEstado(red.getId(), EstadoComando.PENDIENTE) >= MAX_PENDIENTES_POR_RED) {
            throw new ReglaNegocioException("Ya hay " + MAX_PENDIENTES_POR_RED
                    + " comandos esperando al MikroTik. Espera a que se ejecuten o cancela alguno.");
        }

        Usuario admin = usuarioRepository.findByUsername(usernameAdmin)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró tu usuario."));
        ComandoTerminal nuevo = new ComandoTerminal();
        nuevo.setRed(red);
        nuevo.setComando(comando);
        nuevo.setCreadoPor(admin);
        nuevo.setExpiraEn(LocalDateTime.now().plus(VIGENCIA));
        comandoRepository.save(nuevo);
        // Queda en los registros del servidor además de la base de datos
        log.info("Comando de terminal {} encolado por {} para la red {}", nuevo.getId(), usernameAdmin, red.getId());
        return aResponse(nuevo);
    }

    @Transactional
    public List<ComandoTerminalResponse> historial(Integer redId) {
        buscarRed(redId);
        expirarVencidos(redId);
        return comandoRepository.findHistorial(redId, PageRequest.of(0, TAMANO_HISTORIAL)).stream()
                .map(this::aResponse).toList();
    }

    @Transactional
    public ComandoTerminalResponse cancelar(Long id) {
        ComandoTerminal comando = buscarComando(id);
        if (comando.getEstado() != EstadoComando.PENDIENTE) {
            throw new ReglaNegocioException("Solo se puede cancelar un comando que aún no recibe el MikroTik.");
        }
        comando.setEstado(EstadoComando.CANCELADO);
        comando.setFinalizadoEn(LocalDateTime.now());
        return aResponse(comando);
    }

    // ---------- MikroTik ----------

    // Comandos para la consulta actual: se marcan ENVIADO y nunca se vuelven a entregar
    @Transactional
    public List<ComandoTerminal> tomarParaEnviar(Red red) {
        if (red.getModo() != ModoRed.CONTROL) {
            return List.of();
        }
        expirarVencidos(red.getId());
        LocalDateTime ahora = LocalDateTime.now();
        List<ComandoTerminal> pendientes = comandoRepository.findByRedIdAndEstadoOrderByIdAsc(red.getId(), EstadoComando.PENDIENTE);
        for (ComandoTerminal comando : pendientes) {
            comando.setEstado(EstadoComando.ENVIADO);
            comando.setEnviadoEn(ahora);
        }
        return pendientes;
    }

    // Texto del comando, solo para la red dueña y solo mientras está ENVIADO (esperando su salida)
    @Transactional(readOnly = true)
    public String textoParaEjecutar(Red red, Long id) {
        ComandoTerminal comando = comandoDeRed(red, id);
        if (comando.getEstado() != EstadoComando.ENVIADO) {
            throw new RecursoNoEncontradoException("Ese comando ya no está disponible.");
        }
        return comando.getComando();
    }

    @Transactional
    public void recibirResultado(Red red, Long id, boolean exito, String salida) {
        ComandoTerminal comando = comandoDeRed(red, id);
        if (comando.getEstado() != EstadoComando.ENVIADO) {
            return; // ya tenía resultado (o pasó el tiempo de espera): se ignora
        }
        String texto = salida == null ? "" : salida.replace("\r\n", "\n");
        boolean truncada = texto.length() > MAX_SALIDA;
        comando.setSalida(truncada ? texto.substring(0, MAX_SALIDA) : texto);
        comando.setSalidaTruncada(truncada);
        comando.setEstado(exito ? EstadoComando.EJECUTADO : EstadoComando.ERROR);
        comando.setFinalizadoEn(LocalDateTime.now());
    }

    // ---------- Internos ----------

    // PENDIENTE vencido -> EXPIRADO; ENVIADO sin salida a tiempo -> ERROR
    private void expirarVencidos(Integer redId) {
        LocalDateTime ahora = LocalDateTime.now();
        for (ComandoTerminal c : comandoRepository.findByRedIdAndEstadoOrderByIdAsc(redId, EstadoComando.PENDIENTE)) {
            if (!ahora.isBefore(c.getExpiraEn())) {
                c.setEstado(EstadoComando.EXPIRADO);
                c.setFinalizadoEn(ahora);
            }
        }
        for (ComandoTerminal c : comandoRepository.findByRedIdAndEstadoOrderByIdAsc(redId, EstadoComando.ENVIADO)) {
            if (c.getEnviadoEn() != null && !ahora.isBefore(c.getEnviadoEn().plus(ESPERA_SALIDA))) {
                c.setEstado(EstadoComando.ERROR);
                c.setSalida("El MikroTik recibió el comando pero no devolvió la salida a tiempo. "
                        + "Pudo haberse ejecutado o no: revisa el router antes de repetirlo.");
                c.setFinalizadoEn(ahora);
            }
        }
    }

    // Sin caracteres de control (salvo salto de línea y tabulación) y sin espacios sobrantes al inicio y al final
    private static String limpiarComando(String comando) {
        if (comando == null) {
            return "";
        }
        return comando.replace("\r\n", "\n").replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").trim();
    }

    private ComandoTerminal comandoDeRed(Red red, Long id) {
        return comandoRepository.findById(id)
                .filter(c -> c.getRed().getId().equals(red.getId())) // solo comandos de SU red
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe ese comando."));
    }

    private ComandoTerminal buscarComando(Long id) {
        return comandoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe ese comando."));
    }

    private Red buscarRed(Integer id) {
        return redRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una red con el id " + id + "."));
    }

    private ComandoTerminalResponse aResponse(ComandoTerminal c) {
        return new ComandoTerminalResponse(c.getId(), c.getComando(), c.getEstado(), c.getSalida(),
                c.getSalidaTruncada(), c.getCreadoPor().getNombreMostrar(), c.getCreadoEn(), c.getExpiraEn(),
                c.getEnviadoEn(), c.getFinalizadoEn());
    }
}
