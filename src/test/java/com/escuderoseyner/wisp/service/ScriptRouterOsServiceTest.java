package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.Red;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptRouterOsServiceTest {

    private static final String TOKEN = "TokenDePrueba_1234567890abc";

    private final ScriptRouterOsService servicio = new ScriptRouterOsService("", "# marcador-ok");

    private Red red() {
        Red red = new Red();
        red.setId(1);
        red.setNombre("Red Ñandú");
        red.setIntervaloSegundos(60);
        return red;
    }

    @Test
    @DisplayName("Reemplaza todos los datos de la plantilla")
    void reemplazaDatos() {
        String script = servicio.generar(red(), TOKEN, "https://mi-app.example.com/");

        assertThat(script).doesNotContain("{{");
        assertThat(script).contains(":local url \"https://mi-app.example.com/api/mikrotik\"");
        assertThat(script).contains("X-Red-Token: " + TOKEN);
        assertThat(script).contains("interval=60s");
        assertThat(script).contains("Red Nandu");
        assertThat(script).doesNotContain("\r");
    }

    @Test
    @DisplayName("Solo borra o reemplaza su propio script y su propia tarea (convive con el puente)")
    void soloTocaLoSuyo() {
        String script = servicio.generar(red(), TOKEN, "https://mi-app.example.com");

        Matcher borrados = Pattern.compile("remove \\[find where name=\"([^\"]+)\"]").matcher(script);
        int cantidad = 0;
        while (borrados.find()) {
            assertThat(borrados.group(1)).isEqualTo("wisp-sync");
            cantidad++;
        }
        assertThat(cantidad).isEqualTo(2);
        // Nada de firewall, fasttrack, reinicios ni borrado de colas
        assertThat(script.toLowerCase()).doesNotContain("fasttrack", "/ip firewall", "/system reboot",
                "queue simple remove", "/system reset");
        assertThat(script).contains("check-certificate=yes-without-crl");
        assertThat(script).doesNotContain("check-certificate=no");
    }

    @Test
    @DisplayName("Las llaves y los paréntesis de la plantilla están balanceados")
    void balanceado() {
        String script = servicio.generar(red(), TOKEN, "https://mi-app.example.com");
        // Se quitan los textos entre comillas para no contar llaves dentro de ellos
        String sinTextos = script.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
        assertThat(contar(sinTextos, '{')).isEqualTo(contar(sinTextos, '}'));
        assertThat(contar(sinTextos, '(')).isEqualTo(contar(sinTextos, ')'));
        assertThat(contar(sinTextos, '[')).isEqualTo(contar(sinTextos, ']'));
    }

    @Test
    @DisplayName("Para el puente, la primera línea es su marcador")
    void marcadorDelPuente() {
        String script = servicio.generarParaPuente(red(), TOKEN, "https://mi-app.example.com");
        assertThat(script).startsWith("# marcador-ok\n");
    }

    @Test
    @DisplayName("Rechaza URLs o tokens que podrían inyectar código en el script")
    void rechazaInyeccion() {
        assertThatThrownBy(() -> servicio.generar(red(), TOKEN, "https://x.com\"; /system reset"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.generar(red(), "token\"$malo-000000000000", "https://x.com"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    private static long contar(String texto, char c) {
        return texto.chars().filter(ch -> ch == c).count();
    }
}
