package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.AlcanceRedes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Códigos de cliente: prefijo de la red + letras o números (CP-W2 marca clientes sin antena).
// El "siguiente código" solo cuenta los numéricos.
@ExtendWith(MockitoExtension.class)
class ClienteServiceCodigoTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PlanService planService;
    @Mock private RedRepository redRepository;
    @Mock private SincronizacionService sincronizacionService;
    @Mock private AlcanceRedes alcanceRedes;

    private ClienteService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ClienteService(clienteRepository, usuarioRepository, planService, redRepository,
                sincronizacionService, alcanceRedes);
    }

    @Test
    @DisplayName("Acepta el prefijo seguido de letras y números")
    void formatosValidos() {
        assertThat(Stream.of("C-07", "CP-10", "CP-W2", "CP-OMADA1", "C-1")).allMatch(ClienteService::codigoValido);
    }

    @Test
    @DisplayName("Rechaza códigos sin prefijo, sin guion o con caracteres raros")
    void formatosInvalidos() {
        assertThat(Stream.of("CP10", "07", "CP-", "-W2", "CP-W 2", "CP-W_2", "CPXYZ-1", "cp-w2", ""))
                .noneMatch(ClienteService::codigoValido);
        assertThat(ClienteService.codigoValido(null)).isFalse();
    }

    @Test
    @DisplayName("El siguiente código de Cuypirmarca ignora CP-W2 y los de otros prefijos: CP-10")
    void siguienteSoloNumericos() {
        Red cuypirmarca = new Red();
        cuypirmarca.setId(2);
        cuypirmarca.setPrefijoCodigo("CP-");
        when(redRepository.findById(2)).thenReturn(Optional.of(cuypirmarca));
        when(clienteRepository.findAllCodigos()).thenReturn(List.of(
                "CP-01", "CP-02", "CP-03", "CP-04", "CP-05", "CP-06", "CP-07", "CP-08", "CP-09", "CP-W2", "C-18"));

        assertThat(servicio.siguienteCodigo(2).codigo()).isEqualTo("CP-10");
        assertThat(servicio.siguienteCodigo(null).codigo()).isEqualTo("C-19");
    }

    @Test
    @DisplayName("En las listas, CP-W2 va después de los códigos numéricos de su prefijo")
    void orden() {
        List<String> ordenados = Stream.of("CP-W2", "CP-10", "C-2", "CP-02", "C-10")
                .map(ClienteServiceCodigoTest::cliente)
                .sorted(ClienteService.POR_CODIGO)
                .map(Cliente::getCodigo)
                .toList();
        assertThat(ordenados).containsExactly("C-2", "C-10", "CP-02", "CP-10", "CP-W2");
    }

    private static Cliente cliente(String codigo) {
        Cliente c = new Cliente();
        c.setId(codigo.hashCode());
        c.setCodigo(codigo);
        return c;
    }
}
