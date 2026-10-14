package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.Red;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
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
        assertThat(cuerpo(script)).contains(":local url \"https://mi-app.example.com/api/mikrotik\"");
        // El proxy redirige a <host>/aviso/<token> (sin https://: la web lo pasa a HTTPS)
        assertThat(cuerpo(script)).contains(":local hostWeb \"mi-app.example.com\"");
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
        // Nada de reglas de firewall, fasttrack, reinicios ni borrado de colas
        String minusculas = script.toLowerCase();
        assertThat(minusculas).doesNotContain("fasttrack", "/ip firewall filter", "/ip firewall nat",
                "/ip firewall mangle", "/ip firewall raw", "/system reboot", "queue simple remove", "/system reset");
        // Del firewall solo toca sus propias address-list (wisp-cortados y wisp-aviso) y las conexiones del cortado
        String cuerpo = cuerpo(script);
        Matcher firewall = Pattern.compile("/ip firewall (\\S+) (\\S+)([^\\n]*)").matcher(cuerpo);
        int usos = 0;
        while (firewall.find()) {
            assertThat(firewall.group(1)).isIn("address-list", "connection");
            if (firewall.group(1).equals("address-list")) {
                assertThat(firewall.group(3)).containsAnyOf("list=\"wisp-cortados\"", "list=\"wisp-aviso\"");
            }
            usos++;
        }
        assertThat(usos).isPositive();
        // Solo borra las entradas que él mismo creó (comentario wisp-gestion:<cola>)
        assertThat(cuerpo).contains("address-list remove [find where list=\"wisp-cortados\" and comment=$etiqueta]");
        assertThat(cuerpo).contains("address-list remove [find where list=\"wisp-aviso\" and comment=$etiqueta]");
        // Del proxy solo borra sus propias reglas, y las agrega antes de la regla final "wisp-gestion: fin"
        Matcher proxy = Pattern.compile("/ip proxy access (\\S+)([^\\n]*)").matcher(cuerpo);
        int reglas = 0;
        while (proxy.find()) {
            assertThat(proxy.group(1)).isIn("add", "remove", "find");
            if (proxy.group(1).equals("remove")) {
                assertThat(proxy.group(2)).startsWith(" [find where comment=$etiqueta]");
            }
            if (proxy.group(1).equals("add")) {
                assertThat(proxy.group(2)).contains("src-address=$ipc", "action=redirect", "action-data=$destino", "comment=$etiqueta")
                        .doesNotContain("redirect-to"); // RouterOS 7: "bad parameter redirect-to"
            }
            reglas++;
        }
        assertThat(reglas).isEqualTo(4);
        assertThat(minusculas).doesNotContain("/ip proxy set");
        assertThat(script).contains("check-certificate=yes-without-crl");
        assertThat(script).doesNotContain("check-certificate=no");
    }

    @Test
    @DisplayName("Cada comando del instalador ocupa una sola línea con comillas y llaves balanceadas")
    void instaladorEnLineasCompletas() {
        String script = servicio.generar(red(), TOKEN, "https://mi-app.example.com");
        for (String linea : script.split("\n")) {
            if (linea.isBlank() || linea.startsWith("#")) continue;
            validarLinea(linea);
        }
        // Nada de source={ ... } en varias líneas (daba "syntax error" en RouterOS 7.24)
        assertThat(script).doesNotContain("source={");
        assertThat(script.lines().filter(l -> l.contains("source=\"")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Dentro de source=\"...\" están escapados \\, \", $, ? y los saltos de línea")
    void escapesDentroDeSource() {
        String script = servicio.generar(red(), TOKEN, "https://mi-app.example.com");
        String crudo = sourceCrudo(script);

        for (int i = 0; i < crudo.length(); i++) {
            char c = crudo.charAt(i);
            assertThat(c).as("salto de línea sin escapar en la posición " + i).isNotIn('\n', '\r');
            if (c == '\\') {
                assertThat(i + 1).as("\\ al final de source").isLessThan(crudo.length());
                assertThat(crudo.charAt(i + 1)).as("escape desconocido en la posición " + i).isIn('\\', '"', '$', '?', 'n', 't');
                i++;
            } else {
                assertThat(c).as("carácter sin escapar en la posición " + i).isNotIn('"', '$', '?');
            }
        }
        // Al desescaparlo queda exactamente el cuerpo de la plantilla, con la URL y el token
        String cuerpo = desescapar(crudo);
        assertThat(cuerpo).startsWith(":local url \"https://mi-app.example.com/api/mikrotik\"\n");
        assertThat(cuerpo).contains("\"\\n# fin\\n\"", ":set res ([/tool fetch url=($url . \"/acciones\")");
    }

    @Test
    @DisplayName("El código del script wisp-sync tiene comillas, llaves, corchetes y paréntesis balanceados")
    void cuerpoBalanceado() {
        String cuerpo = cuerpo(servicio.generar(red(), TOKEN, "https://mi-app.example.com"));
        int llaves = 0;
        int numero = 0;
        for (String linea : cuerpo.split("\n")) {
            numero++;
            String sinTextos = sinTextos(linea, numero);
            llaves += contar(sinTextos, '{') - contar(sinTextos, '}');
            assertThat(llaves).as("llave cerrada de más en la línea " + numero).isGreaterThanOrEqualTo(0);
            assertThat(contar(sinTextos, '[')).as("corchetes en la línea " + numero).isEqualTo(contar(sinTextos, ']'));
            assertThat(contar(sinTextos, '(')).as("paréntesis en la línea " + numero).isEqualTo(contar(sinTextos, ')'));
        }
        assertThat(llaves).as("llaves sin cerrar al final").isZero();
        // Sin funciones locales (:local x do={...} + [$x a=...]): ahí se detenía RouterOS 7.24
        assertThat(cuerpo).doesNotContainPattern(":local \\w+ do=");
        assertThat(cuerpo).doesNotContain("[$split");
    }

    @Test
    @DisplayName("El comando corto descarga con el token en el header, importa y borra el archivo")
    void comandoCorto() {
        String comando = servicio.comandoInstalacion(TOKEN, "https://mi-app.example.com/");
        List<String> lineas = comando.lines().toList();

        assertThat(lineas).hasSize(2);
        assertThat(lineas.get(0)).startsWith("/tool fetch url=\"https://mi-app.example.com/api/mikrotik/instalar\"")
                .contains("http-header-field=\"X-Red-Token: " + TOKEN + "\"", "check-certificate=yes-without-crl",
                        "dst-path=wisp-instalar.rsc")
                .doesNotContain("?"); // el token nunca va en la URL
        assertThat(lineas.get(1)).contains("/import file-name=wisp-instalar.rsc", "/file remove [find where name=\"wisp-instalar.rsc\"]");
        lineas.forEach(ScriptRouterOsServiceTest::validarLinea);
    }

    @Test
    @DisplayName("Script puente: 4 líneas completas, solo crea wisp-puente y consulta el bootstrap cada 2 min")
    void scriptPuente() {
        String puente = servicio.generarPuente(red(), TOKEN, "https://mi-app.example.com/");
        List<String> lineas = puente.lines().toList();

        assertThat(lineas).hasSize(4);
        lineas.forEach(ScriptRouterOsServiceTest::validarLinea);
        assertThat(puente).doesNotContain("{{", "source={", "\r");
        // Solo borra o reemplaza su propio script y su propia tarea (nunca wisp-sync ni otro)
        Matcher borrados = Pattern.compile("remove \\[find where name=\"([^\"]+)\"]").matcher(puente);
        int cantidad = 0;
        while (borrados.find()) {
            assertThat(borrados.group(1)).isEqualTo("wisp-puente");
            cantidad++;
        }
        assertThat(cantidad).isEqualTo(2);
        assertThat(puente).contains("interval=2m", "on-event=\"/system script run wisp-puente\"");

        String cuerpo = cuerpo(puente);
        assertThat(cuerpo).contains("/tool fetch url=\"https://mi-app.example.com/api/mikrotik/bootstrap\"",
                "http-header-field=\"X-Red-Token: " + TOKEN + "\"", "check-certificate=yes-without-crl");
        // Solo ejecuta si la respuesta empieza con el marcador seguido de un salto de línea
        assertThat(cuerpo).contains(":local marca \"# marcador-ok\\n\"",
                "([:pick $res 0 [:len $marca]] = $marca)", ":local codigo [:parse $res]");
        assertThat(cuerpo).doesNotContainPattern(":local \\w+ do=");
        assertThat(cuerpo.toLowerCase()).doesNotContain("/ip firewall", "/system reset", "/system reboot", "queue simple");
    }

    @Test
    @DisplayName("Lo que descarga el puente cabe en output=user (máx. 64 KB en RouterOS 7)")
    void instalacionCabeEnElPuente() {
        String script = servicio.generarParaPuente(red(), TOKEN, "https://una-app-con-nombre-largo.onrender.com");
        assertThat(script.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThan(60_000);
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

    // Una línea de RouterOS: comillas cerradas y llaves, corchetes y paréntesis balanceados fuera de los textos
    private static void validarLinea(String linea) {
        String sinTextos = sinTextos(linea, 0);
        assertThat(contar(sinTextos, '{')).as(linea).isEqualTo(contar(sinTextos, '}'));
        assertThat(contar(sinTextos, '[')).as(linea).isEqualTo(contar(sinTextos, ']'));
        assertThat(contar(sinTextos, '(')).as(linea).isEqualTo(contar(sinTextos, ')'));
    }

    // Reemplaza cada texto "..." por "" respetando los escapes; falla si una comilla queda abierta
    private static String sinTextos(String linea, int numero) {
        StringBuilder fuera = new StringBuilder();
        boolean enTexto = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (enTexto) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    enTexto = false;
                    fuera.append('"');
                }
            } else {
                if (c == '"') {
                    enTexto = true;
                }
                fuera.append(c);
            }
        }
        assertThat(enTexto).as("comilla sin cerrar en la línea " + numero + ": " + linea).isFalse();
        return fuera.toString();
    }

    // El valor de source="..." tal como está en el instalador (escapado)
    private static String sourceCrudo(String script) {
        int inicio = script.indexOf("source=\"") + "source=\"".length();
        int i = inicio;
        while (script.charAt(i) != '"') {
            i += script.charAt(i) == '\\' ? 2 : 1;
        }
        return script.substring(inicio, i);
    }

    private static String cuerpo(String script) {
        return desescapar(sourceCrudo(script));
    }

    private static String desescapar(String crudo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < crudo.length(); i++) {
            char c = crudo.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char siguiente = crudo.charAt(++i);
            sb.append(switch (siguiente) {
                case 'n' -> '\n';
                case 't' -> '\t';
                default -> siguiente;
            });
        }
        return sb.toString();
    }
}
