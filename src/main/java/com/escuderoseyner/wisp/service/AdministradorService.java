package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.AdministradorResponse;
import com.escuderoseyner.wisp.dto.CrearAdministradorRequest;
import com.escuderoseyner.wisp.dto.CuentaTemporalResponse;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.ControlIntentosLogin;
import com.escuderoseyner.wisp.security.GeneradorPasswordTemporal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Administradores del sistema. Reglas:
//  - La contraseña inicial y la restablecida son temporales, se muestran UNA vez y obligan a cambiarla.
//  - Un admin no puede desactivarse ni restablecerse a sí mismo (para su propia clave usa "Cambiar mi contraseña").
//  - Siempre debe quedar al menos un admin activo.
@Service
public class AdministradorService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final GeneradorPasswordTemporal generadorPassword;
    private final ControlIntentosLogin controlIntentos;

    public AdministradorService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                GeneradorPasswordTemporal generadorPassword, ControlIntentosLogin controlIntentos) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.generadorPassword = generadorPassword;
        this.controlIntentos = controlIntentos;
    }

    @Transactional(readOnly = true)
    public List<AdministradorResponse> listar(String usernameActual) {
        return usuarioRepository.findByRolOrderByNombreMostrarAsc(Rol.ADMIN).stream()
                .map(u -> aResponse(u, usernameActual))
                .toList();
    }

    @Transactional
    public CuentaTemporalResponse crear(CrearAdministradorRequest request) {
        String username = request.username().trim();
        if (usuarioRepository.existsByUsername(username)) {
            throw new ReglaNegocioException("El usuario " + username + " ya lo usa otra persona.");
        }
        String password = generadorPassword.generar();
        Usuario admin = new Usuario();
        admin.setUsername(username);
        admin.setNombreMostrar(request.nombreMostrar().trim());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRol(Rol.ADMIN);
        admin.setDebeCambiarPassword(true); // la cambia en su primer ingreso
        usuarioRepository.save(admin);
        return new CuentaTemporalResponse(admin.getUsername(), password);
    }

    // Al desactivarlo, su sesión abierta se cierra en su próxima acción (UsuarioVigenteValidator)
    @Transactional
    public AdministradorResponse desactivar(Integer id, String usernameActual) {
        Usuario admin = buscarAdmin(id);
        if (esElMismo(admin, usernameActual)) {
            throw new ReglaNegocioException("No puedes desactivar tu propia cuenta.");
        }
        if (!admin.getActivo()) {
            return aResponse(admin, usernameActual);
        }
        List<Usuario> activos = usuarioRepository.findActivosPorRolBloqueando(Rol.ADMIN);
        if (activos.size() <= 1) {
            throw new ReglaNegocioException("Debe quedar al menos un administrador activo.");
        }
        admin.setActivo(false);
        return aResponse(admin, usernameActual);
    }

    @Transactional
    public AdministradorResponse reactivar(Integer id, String usernameActual) {
        Usuario admin = buscarAdmin(id);
        admin.setActivo(true);
        return aResponse(admin, usernameActual);
    }

    @Transactional
    public CuentaTemporalResponse restablecerPassword(Integer id, String usernameActual) {
        Usuario admin = buscarAdmin(id);
        if (esElMismo(admin, usernameActual)) {
            throw new ReglaNegocioException("Para tu propia contraseña usa \"Cambiar mi contraseña\".");
        }
        String password = generadorPassword.generar();
        admin.cambiarPasswordHash(passwordEncoder.encode(password)); // cierra sus sesiones abiertas
        admin.setDebeCambiarPassword(true);
        controlIntentos.desbloquearCuenta(admin.getUsername());
        return new CuentaTemporalResponse(admin.getUsername(), password);
    }

    // Solo usuarios con rol ADMIN: este endpoint no sirve para tocar cuentas de clientes
    private Usuario buscarAdmin(Integer id) {
        return usuarioRepository.findById(id)
                .filter(u -> u.getRol() == Rol.ADMIN)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un administrador con el id " + id + "."));
    }

    private boolean esElMismo(Usuario admin, String usernameActual) {
        return admin.getUsername().equalsIgnoreCase(usernameActual);
    }

    private AdministradorResponse aResponse(Usuario u, String usernameActual) {
        return new AdministradorResponse(u.getId(), u.getUsername(), u.getNombreMostrar(), u.getActivo(),
                u.getDebeCambiarPassword(), u.getUltimoAcceso(), esElMismo(u, usernameActual));
    }
}
