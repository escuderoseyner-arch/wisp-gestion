package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.AdministradorResponse;
import com.escuderoseyner.wisp.dto.CrearAdministradorRequest;
import com.escuderoseyner.wisp.dto.CuentaResponse;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.ControlIntentosLogin;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Cuentas del personal: administradores y operadores. Reglas:
//  - La contraseña inicial y la restablecida las escribe el admin y obligan a cambiarla en el próximo ingreso.
//  - Nadie puede desactivarse ni restablecerse a sí mismo (para su propia clave usa "Cambiar mi contraseña").
//  - El rol se elige al crear la cuenta y no se cambia después.
//  - Siempre debe quedar al menos un ADMIN activo (los operadores no cuentan).
@Service
public class AdministradorService {

    private static final List<Rol> ROLES_PERSONAL = List.of(Rol.ADMIN, Rol.OPERADOR);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ControlIntentosLogin controlIntentos;

    public AdministradorService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                ControlIntentosLogin controlIntentos) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.controlIntentos = controlIntentos;
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
        Usuario cuenta = new Usuario();
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

    private boolean esElMismo(Usuario cuenta, String usernameActual) {
        return cuenta.getUsername().equalsIgnoreCase(usernameActual);
    }

    private AdministradorResponse aResponse(Usuario u, String usernameActual) {
        return new AdministradorResponse(u.getId(), u.getUsername(), u.getNombreMostrar(), u.getRol(), u.getActivo(),
                u.getDebeCambiarPassword(), u.getUltimoAcceso(), esElMismo(u, usernameActual));
    }
}
