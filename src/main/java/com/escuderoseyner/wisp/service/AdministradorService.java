package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.AdministradorResponse;
import com.escuderoseyner.wisp.dto.CrearAdministradorRequest;
import com.escuderoseyner.wisp.dto.CuentaResponse;
import com.escuderoseyner.wisp.dto.RedVisibleResponse;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.ControlIntentosLogin;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Cuentas del personal: administradores y operadores. Reglas:
//  - La contraseña inicial y la restablecida las escribe el admin y obligan a cambiarla en el próximo ingreso.
//  - Nadie puede desactivarse ni restablecerse a sí mismo (para su propia clave usa "Cambiar mi contraseña").
//  - El rol se elige al crear la cuenta y no se cambia después.
//  - Siempre debe quedar al menos un ADMIN activo (los operadores no cuentan).
//  - El OPERADOR solo ve las redes que tiene asignadas (el ADMIN ve todas, no se le asignan).
@Service
public class AdministradorService {

    private static final List<Rol> ROLES_PERSONAL = List.of(Rol.ADMIN, Rol.OPERADOR);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ControlIntentosLogin controlIntentos;
    private final RedRepository redRepository;

    public AdministradorService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                ControlIntentosLogin controlIntentos, RedRepository redRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.controlIntentos = controlIntentos;
        this.redRepository = redRepository;
    }

    @Transactional(readOnly = true)
    public List<AdministradorResponse> listar(String usernameActual) {
        return usuarioRepository.findByRolInOrderByNombreMostrarAsc(ROLES_PERSONAL).stream()
                .map(u -> aResponse(u, usernameActual))
                .toList();
    }

    @Transactional
    public CuentaResponse crear(CrearAdministradorRequest request) {
        if (!request.rol().esPersonal()) {
            throw new ReglaNegocioException("El rol debe ser administrador u operador.");
        }
        String username = request.username().trim();
        if (usuarioRepository.existsByUsername(username)) {
            throw new ReglaNegocioException("El usuario " + username + " ya lo usa otra persona.");
        }
        ReglasPassword.validar(request.password());
        Set<Red> redes = Set.of();
        if (request.rol() == Rol.OPERADOR) {
            redes = buscarRedes(request.redIds());
        } else if (request.redIds() != null && !request.redIds().isEmpty()) {
            throw new ReglaNegocioException("Un administrador ve todas las redes: no se le asignan redes.");
        }
        Usuario cuenta = new Usuario();
        cuenta.setRedes(new HashSet<>(redes));
        cuenta.setUsername(username);
        cuenta.setNombreMostrar(request.nombreMostrar().trim());
        cuenta.setPasswordHash(passwordEncoder.encode(request.password()));
        cuenta.setRol(request.rol());
        cuenta.setDebeCambiarPassword(true); // la cambia en su primer ingreso
        usuarioRepository.save(cuenta);
        return new CuentaResponse(cuenta.getUsername());
    }

    // Al desactivarlo, su sesión abierta se cierra en su próxima acción (UsuarioVigenteValidator)
    @Transactional
    public AdministradorResponse desactivar(Integer id, String usernameActual) {
        Usuario cuenta = buscarPersonal(id);
        if (esElMismo(cuenta, usernameActual)) {
            throw new ReglaNegocioException("No puedes desactivar tu propia cuenta.");
        }
        if (!cuenta.getActivo()) {
            return aResponse(cuenta, usernameActual);
        }
        if (cuenta.getRol() == Rol.ADMIN) {
            List<Usuario> activos = usuarioRepository.findActivosPorRolBloqueando(Rol.ADMIN);
            if (activos.size() <= 1) {
                throw new ReglaNegocioException("Debe quedar al menos un administrador activo.");
            }
        }
        cuenta.setActivo(false);
        return aResponse(cuenta, usernameActual);
    }

    // Reemplaza las redes de un OPERADOR. Lo nota al instante: sus redes se leen en cada petición.
    @Transactional
    public AdministradorResponse cambiarRedes(Integer id, List<Integer> redIds, String usernameActual) {
        Usuario cuenta = buscarPersonal(id);
        if (cuenta.getRol() != Rol.OPERADOR) {
            throw new ReglaNegocioException("Solo a los operadores se les asignan redes: un administrador ve todas.");
        }
        cuenta.getRedes().clear();
        cuenta.getRedes().addAll(buscarRedes(redIds));
        return aResponse(cuenta, usernameActual);
    }

    @Transactional
    public AdministradorResponse reactivar(Integer id, String usernameActual) {
        Usuario cuenta = buscarPersonal(id);
        cuenta.setActivo(true);
        return aResponse(cuenta, usernameActual);
    }

    @Transactional
    public CuentaResponse restablecerPassword(Integer id, String password, String usernameActual) {
        Usuario cuenta = buscarPersonal(id);
        if (esElMismo(cuenta, usernameActual)) {
            throw new ReglaNegocioException("Para tu propia contraseña usa \"Cambiar mi contraseña\".");
        }
        ReglasPassword.validar(password);
        cuenta.cambiarPasswordHash(passwordEncoder.encode(password)); // cierra sus sesiones abiertas
        cuenta.setDebeCambiarPassword(true);
        // El bloqueo real vive en memoria (ControlIntentosLogin); las columnas se dejan limpias
        cuenta.setIntentosFallidos(0);
        cuenta.setBloqueadoHasta(null);
        controlIntentos.desbloquearCuenta(cuenta.getUsername());
        return new CuentaResponse(cuenta.getUsername());
    }

    // Solo cuentas del personal: este endpoint no sirve para tocar cuentas de clientes
    private Usuario buscarPersonal(Integer id) {
        return usuarioRepository.findById(id)
                .filter(u -> u.getRol().esPersonal())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una cuenta del personal con el id " + id + "."));
    }

    private Set<Red> buscarRedes(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<Integer> unicos = new HashSet<>(ids);
        List<Red> redes = redRepository.findAllById(unicos);
        if (redes.size() != unicos.size()) {
            throw new ReglaNegocioException("Alguna de las redes elegidas no existe.");
        }
        return new HashSet<>(redes);
    }

    private boolean esElMismo(Usuario cuenta, String usernameActual) {
        return cuenta.getUsername().equalsIgnoreCase(usernameActual);
    }

    private AdministradorResponse aResponse(Usuario u, String usernameActual) {
        List<RedVisibleResponse> redes = u.getRedes().stream()
                .sorted(Comparator.comparing(Red::getNombre))
                .map(r -> new RedVisibleResponse(r.getId(), r.getNombre(), r.getPrefijoCodigo()))
                .toList();
        return new AdministradorResponse(u.getId(), u.getUsername(), u.getNombreMostrar(), u.getRol(), u.getActivo(),
                u.getDebeCambiarPassword(), u.getUltimoAcceso(), esElMismo(u, usernameActual), redes);
    }
}
