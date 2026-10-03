package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.ClienteDetalleResponse;
import com.escuderoseyner.wisp.dto.ClienteRequest;
import com.escuderoseyner.wisp.dto.ClienteResumenResponse;
import com.escuderoseyner.wisp.dto.CuentaTemporalResponse;
import com.escuderoseyner.wisp.dto.ReasignarCodigoRequest;
import com.escuderoseyner.wisp.dto.SiguienteCodigoResponse;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.service.ClienteService;
import com.escuderoseyner.wisp.service.CuentaClienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// No hay DELETE: los clientes se retiran, nunca se borran
@RestController
@RequestMapping("/api/admin/clientes") // /api/admin/**: solo ADMIN (ver SecurityConfig)
public class ClienteController {

    private final ClienteService clienteService;
    private final CuentaClienteService cuentaClienteService;

    public ClienteController(ClienteService clienteService, CuentaClienteService cuentaClienteService) {
        this.clienteService = clienteService;
        this.cuentaClienteService = cuentaClienteService;
    }

    // GET /api/admin/clientes?estado=RETIRADO&zona=Zona Norte&q=maría
    // Sin "estado" devuelve solo ACTIVO y SUSPENDIDO
    @GetMapping
    public List<ClienteResumenResponse> listar(@RequestParam(required = false) EstadoCliente estado,
                                               @RequestParam(required = false) String zona,
                                               @RequestParam(required = false) String q) {
        return clienteService.listar(estado, zona, q);
    }

    @GetMapping("/zonas")
    public List<String> zonas() {
        return clienteService.zonas();
    }

    @GetMapping("/siguiente-codigo")
    public SiguienteCodigoResponse siguienteCodigo() {
        return clienteService.siguienteCodigo();
    }

    @GetMapping("/{id}")
    public ClienteDetalleResponse detalle(@PathVariable Integer id) {
        return clienteService.detalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteDetalleResponse crear(@Valid @RequestBody ClienteRequest request) {
        return clienteService.crear(request);
    }

    @PutMapping("/{id}")
    public ClienteDetalleResponse editar(@PathVariable Integer id, @Valid @RequestBody ClienteRequest request) {
        return clienteService.editar(id, request);
    }

    @PostMapping("/{id}/suspender")
    public ClienteDetalleResponse suspender(@PathVariable Integer id) {
        return clienteService.suspender(id);
    }

    @PostMapping("/{id}/reactivar")
    public ClienteDetalleResponse reactivar(@PathVariable Integer id) {
        return clienteService.reactivar(id);
    }

    @PostMapping("/{id}/retirar")
    public ClienteDetalleResponse retirar(@PathVariable Integer id) {
        return clienteService.retirar(id);
    }

    // Devuelve el cliente NUEVO que quedó con el código
    @PostMapping("/{id}/reasignar")
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteDetalleResponse reasignar(@PathVariable Integer id,
                                            @Valid @RequestBody ReasignarCodigoRequest request) {
        return clienteService.reasignarCodigo(id, request);
    }

    @PostMapping("/{id}/cuenta")
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaTemporalResponse crearCuenta(@PathVariable Integer id) {
        return cuentaClienteService.crearCuenta(id);
    }

    @PostMapping("/{id}/cuenta/restablecer-password")
    public CuentaTemporalResponse restablecerPassword(@PathVariable Integer id) {
        return cuentaClienteService.restablecerPassword(id);
    }
}
