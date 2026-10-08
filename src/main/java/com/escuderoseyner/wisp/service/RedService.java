package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.CrearRedRequest;
import com.escuderoseyner.wisp.dto.DatosRed;
import com.escuderoseyner.wisp.dto.RedRequest;
import com.escuderoseyner.wisp.dto.RedResponse;
import com.escuderoseyner.wisp.dto.TokenRedResponse;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.security.TokenRed;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// Las redes (MikroTik) no se borran. El token solo se muestra una vez: al crearla o al cambiarlo.
@Service
public class RedService {

    // Nombres de colas que ya existen en el router (cola padre y protegidas): letras, números,
    // espacio, punto, guion y guion bajo. Nada que rompa una línea de texto para RouterOS.
    private static final Pattern FORMATO_COLA_ROUTER = Pattern.compile("^[A-Za-z0-9._ -]{1,40}$");

    private final RedRepository redRepository;
    private final ClienteRepository clienteRepository;
    private final TokenRed tokenRed;

    public RedService(RedRepository redRepository, ClienteRepository clienteRepository, TokenRed tokenRed) {
        this.redRepository = redRepository;
        this.clienteRepository = clienteRepository;
        this.tokenRed = tokenRed;
    }

    @Transactional(readOnly = true)
    public List<RedResponse> listar() {
        return redRepository.findAllByOrderByNombreAsc().stream().map(this::aResponse).toList();
    }

    @Transactional(readOnly = true)
    public RedResponse detalle(Integer id) {
        return aResponse(buscar(id));
    }

    // Sin token propio, la web genera uno y lo devuelve UNA vez.
    // Con token propio (ej: la clave que ya está en el script "puente"), no se devuelve.
    @Transactional
    public TokenRedResponse crear(CrearRedRequest request) {
        List<String> problemas = new ArrayList<>();
        String tokenPropio = limpiar(request.token());
        if (tokenPropio != null) {
            validarToken(tokenPropio, problemas);
        }
        Red red = new Red();
        validarYAplicar(red, request, null, problemas);

        String token = tokenPropio != null ? tokenPropio : tokenRed.generar();
        red.setTokenHash(TokenRed.hash(token));
        red.setInstalacionPendiente(true);
        RedResponse guardada = aResponse(redRepository.save(red));
        return new TokenRedResponse(guardada, tokenPropio != null ? null : token);
    }

    @Transactional
    public RedResponse editar(Integer id, RedRequest request) {
        Red red = buscar(id);
        validarYAplicar(red, request, id, new ArrayList<>());
        return aResponse(red);
    }

    // OJO: el token anterior deja de valer al instante, también para el script "puente" del router.
    @Transactional
    public TokenRedResponse cambiarToken(Integer id, String tokenRecibido) {
        Red red = buscar(id);
        String tokenPropio = limpiar(tokenRecibido);
        if (tokenPropio != null) {
            List<String> problemas = new ArrayList<>();
            validarToken(tokenPropio, problemas);
            if (!problemas.isEmpty()) {
                throw new ReglaNegocioException("Revisa el token.", problemas);
            }
        }
        String token = tokenPropio != null ? tokenPropio : tokenRed.generar();
        red.setTokenHash(TokenRed.hash(token));
        return new TokenRedResponse(aResponse(red), tokenPropio != null ? null : token);
    }

    // Para ClienteService
    @Transactional(readOnly = true)
    public Red obtener(Integer id) {
        return buscar(id);
    }

    // true si el sistema NUNCA debe tocar esa cola (la cola padre o una protegida)
    public static boolean esColaProtegida(Red red, String nombreCola) {
        if (nombreCola == null) {
            return false;
        }
        String nombre = nombreCola.trim();
        return red.getColaPadre().equalsIgnoreCase(nombre)
                || separarProtegidas(red.getColasProtegidas()).stream().anyMatch(nombre::equalsIgnoreCase);
    }

    public static List<String> separarProtegidas(String colasProtegidas) {
        if (colasProtegidas == null || colasProtegidas.isBlank()) {
            return List.of();
        }
        return Arrays.stream(colasProtegidas.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ---------- Validación ----------

    private void validarYAplicar(Red red, DatosRed datos, Integer idExcluir, List<String> problemas) {
        String nombre = limpiar(datos.nombre());
        if (nombre != null && (idExcluir == null
                ? redRepository.existsByNombreIgnoreCase(nombre)
                : redRepository.existsByNombreIgnoreCaseAndIdNot(nombre, idExcluir))) {
            problemas.add("Ya existe una red con el nombre \"" + nombre + "\".");
        }

        String colaPadre = limpiar(datos.colaPadre());
        if (colaPadre != null && !FORMATO_COLA_ROUTER.matcher(colaPadre).matches()) {
            problemas.add("La cola padre solo puede tener letras, números, espacios, punto, guion y guion bajo.");
        }

        // Sin repetidos (sin importar mayúsculas) y en el orden en que se escribieron
        Set<String> protegidas = new LinkedHashSet<>();
        for (String cola : separarProtegidas(datos.colasProtegidas())) {
            String limpia = limpiar(cola);
            if (!FORMATO_COLA_ROUTER.matcher(limpia).matches()) {
                problemas.add("La cola protegida \"" + limpia + "\" tiene caracteres no permitidos.");
            } else if (protegidas.stream().noneMatch(limpia::equalsIgnoreCase)) {
                protegidas.add(limpia);
            }
        }

        // La cola de un cliente no puede pasar a ser padre ni protegida
        if (idExcluir != null) {
            List<String> colasClientes = clienteRepository.findNombresColaVigentes(idExcluir);
            List<String> intocables = new ArrayList<>(protegidas);
            if (colaPadre != null) {
                intocables.add(colaPadre);
            }
            for (String cola : intocables) {
                if (colasClientes.stream().anyMatch(cola::equalsIgnoreCase)) {
                    problemas.add("La cola \"" + cola + "\" es la cola de un cliente de esta red: no puede ser padre ni protegida.");
                }
            }
        }

        if (!problemas.isEmpty()) {
            throw new ReglaNegocioException("Revisa los datos de la red.", problemas);
        }

        red.setNombre(nombre);
        red.setColaPadre(colaPadre);
        red.setColasProtegidas(protegidas.isEmpty() ? null : String.join(",", protegidas));
        red.setIntervaloSegundos(datos.intervaloSegundos());
        red.setModo(datos.modo());
    }

    private void validarToken(String token, List<String> problemas) {
        if (!TokenRed.formatoValido(token)) {
            problemas.add("El token debe tener entre 20 y 128 caracteres: solo letras, números y . _ ~ + / = -");
        } else if (redRepository.existsByTokenHash(TokenRed.hash(token))) {
            problemas.add("Ese token ya lo usa otra red.");
        }
    }

    // ---------- Utilidades ----------

    private Red buscar(Integer id) {
        return redRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una red con el id " + id + "."));
    }

    // El token NO pasa por aquí con espacios internos: si los tiene, el formato lo rechaza
    private static String limpiar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim().replaceAll("\\s+", " ");
    }

    private RedResponse aResponse(Red red) {
        return new RedResponse(red.getId(), red.getNombre(), red.getColaPadre(),
                separarProtegidas(red.getColasProtegidas()), red.getIntervaloSegundos(), red.getModo(),
                red.getInstalacionPendiente(), red.getUltimaConexion(), red.getUltimoReporte(), red.getUltimaIp(),
                clienteRepository.countByRedIdAndEstadoNot(red.getId(), EstadoCliente.RETIRADO));
    }
}
