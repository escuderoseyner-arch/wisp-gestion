package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.PlanRequest;
import com.escuderoseyner.wisp.dto.PlanResponse;
import com.escuderoseyner.wisp.model.Plan;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.PlanRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Los planes nunca se borran: se desactivan. Un plan desactivado sigue valiendo para
// los clientes que ya lo tienen, pero no se puede asignar a clientes nuevos.
// Cada plan es de una red o de todas (red null). El nombre es único por red, y un plan
// "de todas las redes" no puede llamarse igual que uno de alguna red (saldrían dos iguales al elegir).
@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final SincronizacionService sincronizacionService;
    private final RedRepository redRepository;
    private final ClienteRepository clienteRepository;

    public PlanService(PlanRepository planRepository, SincronizacionService sincronizacionService,
                       RedRepository redRepository, ClienteRepository clienteRepository) {
        this.planRepository = planRepository;
        this.sincronizacionService = sincronizacionService;
        this.redRepository = redRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listar(boolean soloActivos) {
        List<Plan> planes = soloActivos
                ? planRepository.findByActivoTrueOrderByNombreAsc()
                : planRepository.findAllByOrderByActivoDescNombreAsc();
        return planes.stream().map(this::aResponse).toList();
    }

    @Transactional
    public PlanResponse crear(PlanRequest request) {
        String nombre = normalizarNombre(request.nombre());
        Red red = buscarRed(request.redId());
        validarNombre(nombre, red, null);
        Plan plan = new Plan();
        aplicarDatos(plan, request, nombre, red);
        return aResponse(planRepository.save(plan));
    }

    // Cambiar el precio afecta a todos los clientes con este plan desde el próximo cobro.
    // Los pagos ya registrados no cambian: cada pago guarda su propio monto.
    @Transactional
    public PlanResponse actualizar(Integer id, PlanRequest request) {
        Plan plan = buscar(id);
        String nombre = normalizarNombre(request.nombre());
        Red red = buscarRed(request.redId());
        validarNombre(nombre, red, id);
        // Pasar el plan a una red: ningún cliente vigente de otra red (o sin red) puede quedar con él
        if (red != null) {
            long fuera = clienteRepository.contarVigentesConPlanFueraDeRed(id, red.getId());
            if (fuera > 0) {
                throw new ReglaNegocioException("No se puede pasar el plan a la red " + red.getNombre() + ": "
                        + fuera + (fuera == 1 ? " cliente vigente de otra red (o sin red) lo usa."
                        : " clientes vigentes de otras redes (o sin red) lo usan.")
                        + " Cámbiales el plan primero o deja el plan para todas las redes.");
            }
        }
        aplicarDatos(plan, request, nombre, red);
        // Si cambió la velocidad, cambia el max-limit de las colas de sus clientes en el MikroTik
        sincronizacionService.recalcularTodas();
        return aResponse(plan); // JPA guarda los cambios al terminar la transacción
    }

    @Transactional
    public PlanResponse cambiarActivo(Integer id, boolean activo) {
        Plan plan = buscar(id);
        plan.setActivo(activo);
        return aResponse(plan);
    }

    // Para el futuro servicio de clientes: así ningún cliente nuevo recibe un plan desactivado
    @Transactional(readOnly = true)
    public Plan obtenerParaClienteNuevo(Integer id) {
        Plan plan = buscar(id);
        if (!plan.getActivo()) {
            throw new ReglaNegocioException("El plan \"" + plan.getNombre()
                    + "\" está desactivado y no se puede asignar a clientes nuevos.");
        }
        return plan;
    }

    private Plan buscar(Integer id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un plan con el id " + id + "."));
    }

    // "  Plan   Básico " -> "Plan Básico"
    private String normalizarNombre(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ");
    }

    private Red buscarRed(Integer redId) {
        if (redId == null) {
            return null;
        }
        return redRepository.findById(redId)
                .orElseThrow(() -> new ReglaNegocioException("No existe la red elegida para el plan."));
    }

    // Repetido = mismo nombre en la misma red, o cuando uno de los dos es "de todas las redes"
    private void validarNombre(String nombre, Red red, Integer idExcluir) {
        for (Plan otro : planRepository.findByNombreIgnoreCase(nombre)) {
            if (otro.getId().equals(idExcluir)) {
                continue;
            }
            Red redOtro = otro.getRed();
            if (red == null || redOtro == null || redOtro.getId().equals(red.getId())) {
                String donde = redOtro == null ? "para todas las redes" : "en la red " + redOtro.getNombre();
                throw new ReglaNegocioException("Ya existe un plan \"" + otro.getNombre() + "\" " + donde + ".");
            }
        }
    }

    private void aplicarDatos(Plan plan, PlanRequest request, String nombre, Red red) {
        plan.setRed(red);
        plan.setNombre(nombre);
        plan.setBajadaMbps(request.bajadaMbps());
        plan.setSubidaMbps(request.subidaMbps());
        plan.setPrecio(request.precio());
    }

    private PlanResponse aResponse(Plan plan) {
        Red red = plan.getRed();
        return new PlanResponse(plan.getId(), plan.getNombre(), plan.getBajadaMbps(), plan.getSubidaMbps(),
                plan.getPrecio(), plan.getActivo(), red == null ? null : red.getId(),
                red == null ? null : red.getNombre());
    }
}
