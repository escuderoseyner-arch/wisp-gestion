package com.escuderoseyner.wisp.security;

import com.escuderoseyner.wisp.controller.AdministradorController;
import com.escuderoseyner.wisp.controller.ClienteController;
import com.escuderoseyner.wisp.controller.PagoController;
import com.escuderoseyner.wisp.controller.PlanController;
import com.escuderoseyner.wisp.controller.PortalClienteController;
import com.escuderoseyner.wisp.controller.RedController;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.service.AdministradorService;
import com.escuderoseyner.wisp.service.ClienteService;
import com.escuderoseyner.wisp.service.CuentaClienteService;
import com.escuderoseyner.wisp.service.PagoService;
import com.escuderoseyner.wisp.service.PlanService;
import com.escuderoseyner.wisp.service.PortalClienteService;
import com.escuderoseyner.wisp.service.RedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Prueba las reglas de acceso con tokens JWT REALES (firmados con JwtService y validados por
// el JwtDecoder de SecurityConfig). Los servicios son simulados: aquí solo importa quién entra.
@WebMvcTest(controllers = {AdministradorController.class, ClienteController.class, PagoController.class, PlanController.class,
        PortalClienteController.class, RedController.class})
@Import({SecurityConfig.class, RespuestasSeguridad.class, JwtService.class})
@TestPropertySource(properties = {
        // Clave SOLO para pruebas (32 bytes en Base64). La real viene de JWT_SECRET.
        "app.jwt.secret=cHJ1ZWJhcy1zb2xvLXBhcmEtdGVzdHMtMzItYnl0ZXM=",
        "app.jwt.expiracion-minutos=60"
})
class SeguridadRutasTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    // Consulta la base de datos en cada petición: aquí se simula
    @MockitoBean
    private UsuarioVigenteValidator usuarioVigenteValidator;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private ClienteService clienteService;

    @MockitoBean
    private CuentaClienteService cuentaClienteService;

    @MockitoBean
    private PagoService pagoService;

    @MockitoBean
    private PlanService planService;

    @MockitoBean
    private PortalClienteService portalClienteService;

    @MockitoBean
    private AdministradorService administradorService;

    @MockitoBean
    private RedService redService;

    @BeforeEach
    void usuarioVigente() {
        when(usuarioVigenteValidator.validate(any())).thenReturn(OAuth2TokenValidatorResult.success());
    }

    private String token(String username, Rol rol, boolean debeCambiarPassword) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setRol(rol);
        usuario.setDebeCambiarPassword(debeCambiarPassword);
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private String tokenCliente() {
        return token("900000001", Rol.CLIENTE, false);
    }

    private String tokenAdmin() {
        return token("admin", Rol.ADMIN, false);
    }

    @Test
    @DisplayName("Sin token: 401")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/admin/clientes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cliente/resumen")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token con firma inválida: 401")
    void tokenFalsificado() throws Exception {
        String falso = tokenAdmin() + "x";
        mockMvc.perform(get("/api/admin/clientes").header("Authorization", falso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un CLIENTE no puede entrar a ninguna ruta de /api/admin/**")
    void clienteNoEntraAAdmin() throws Exception {
        String cliente = tokenCliente();
        for (String ruta : new String[]{"/api/admin/clientes", "/api/admin/clientes/2", "/api/admin/pagos/cliente/2",
                "/api/admin/pagos/mes", "/api/admin/planes", "/api/admin/administradores", "/api/admin/redes"}) {
            mockMvc.perform(get(ruta).header("Authorization", cliente)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(clienteService, pagoService, planService, administradorService, redService);
    }

    @Test
    @DisplayName("Un ADMIN sí entra a /api/admin/**, pero no al portal del cliente")
    void adminEntraAAdmin() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(get("/api/admin/clientes").header("Authorization", admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/cliente/resumen").header("Authorization", admin)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El portal usa el cliente del token aunque se mande otro id en la URL")
    void portalIgnoraIdDeOtroCliente() throws Exception {
        mockMvc.perform(get("/api/cliente/resumen").param("clienteId", "2").header("Authorization", tokenCliente()))
                .andExpect(status().isOk());
        verify(portalClienteService).resumen("900000001");
    }

    @Test
    @DisplayName("Con contraseña temporal sin cambiar, solo puede cambiarla: el portal da 403")
    void debeCambiarPassword() throws Exception {
        mockMvc.perform(get("/api/cliente/resumen").header("Authorization", token("900000001", Rol.CLIENTE, true)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(portalClienteService);
    }

    @Test
    @DisplayName("Usuario desactivado o con contraseña cambiada (validador falla): 401")
    void usuarioYaNoVigente() throws Exception {
        when(usuarioVigenteValidator.validate(any())).thenReturn(
                OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "La sesión ya no es válida", null)));
        mockMvc.perform(get("/api/cliente/resumen").header("Authorization", tokenCliente()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(portalClienteService);
    }
}
