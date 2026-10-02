package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.PlanRequest;
import com.escuderoseyner.wisp.dto.PlanResponse;
import com.escuderoseyner.wisp.model.Plan;
import com.escuderoseyner.wisp.repository.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Los planes nunca se borran: se desactivan. Un plan desactivado sigue valiendo para
// los clientes que ya lo tienen, pero no se puede asignar a clientes nuevos.
@Service
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
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
        if (planRepository.existsByNombreIgnoreCase(nombre)) {
            throw nombreRepetido(nombre);
        }
        Plan plan = new Plan();
        aplicarDatos(plan, request, nombre);
        return aResponse(planRepository.save(plan));
    }

    // Cambiar el precio afecta a todos los clientes con este plan desde el próximo cobro.
    // Los pagos ya registrados no cambian: cada pago guarda su propio monto.
    @Transactional
    public PlanResponse actualizar(Integer id, PlanRequest request) {
        Plan plan = buscar(id);
        String nombre = normalizarNombre(request.nombre());
        if (planRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw nombreRepetido(nombre);
        }
        aplicarDatos(plan, request, nombre);
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

    private ReglaNegocioException nombreRepetido(String nombre) {
        return new ReglaNegocioException("Ya existe un plan con el nombre \"" + nombre + "\".");
    }

    private void aplicarDatos(Plan plan, PlanRequest request, String nombre) {
        plan.setNombre(nombre);
        plan.setBajadaMbps(request.bajadaMbps());
        plan.setSubidaMbps(request.subidaMbps());
        plan.setPrecio(request.precio());
    }

    private PlanResponse aResponse(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getNombre(), plan.getBajadaMbps(), plan.getSubidaMbps(),
                plan.getPrecio(), plan.getActivo());
    }
}
