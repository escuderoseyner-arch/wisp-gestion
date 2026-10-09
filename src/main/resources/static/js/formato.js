// Funciones de formato que comparten varias páginas: dinero, fechas, meses en español,
// enlaces de WhatsApp y plantilla de recordatorio. No depende de ningún otro script.

const Formato = (() => {
  const formatoNumero = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  // dinero('S/', 79) -> "S/ 79.00" (separador decimal según el idioma del navegador)
  function dinero(moneda, monto) {
    return (moneda ? moneda + ' ' : '') + formatoNumero.format(Number(monto));
  }

  // "2026-10" -> "Octubre de 2026"
  function nombreMes(periodo) {
    const [anio, mes] = periodo.split('-').map(Number);
    const texto = new Date(anio, mes - 1, 1).toLocaleDateString('es', { month: 'long', year: 'numeric' });
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  // "2026-10-08" -> "08/10/2026"
  function fecha(iso) {
    if (!iso) return '';
    const [anio, mes, dia] = iso.split('-');
    return dia + '/' + mes + '/' + anio;
  }

  // Hoy en hora local, como "2026-10-03" (toISOString usaría la hora UTC y podría dar mañana)
  function hoy() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function mesActual() {
    return hoy().slice(0, 7);
  }

  // ["a"] -> "a"; ["a","b"] -> "a y b"; ["a","b","c"] -> "a, b y c"
  function listaConY(items) {
    if (items.length <= 1) return items.join('');
    return items.slice(0, -1).join(', ') + ' y ' + items[items.length - 1];
  }

  // ["2026-08", "2026-09"] -> "agosto y septiembre 2026"
  // ["2025-12", "2026-01", "2026-02"] -> "diciembre 2025, enero y febrero 2026"
  function mesesEnTexto(periodos) {
    const grupos = []; // [{ anio, meses: [...] }] en orden
    [...periodos].sort().forEach((periodo) => {
      const [anio, mes] = periodo.split('-').map(Number);
      const nombre = new Date(anio, mes - 1, 1).toLocaleDateString('es', { month: 'long' });
      const ultimo = grupos[grupos.length - 1];
      if (ultimo && ultimo.anio === anio) {
        ultimo.meses.push(nombre);
      } else {
        grupos.push({ anio, meses: [nombre] });
      }
    });
    return grupos.map((g) => listaConY(g.meses) + ' ' + g.anio).join(', ');
  }

  // Reemplaza {nombre}, {monto} y {mes} en la plantilla de recordatorio
  function rellenarPlantilla(plantilla, valores) {
    return plantilla.replace(/\{(nombre|monto|mes)\}/g, (_, variable) => valores[variable] ?? '');
  }

  // https://wa.me/51987654321?text=... ; null si no hay número.
  // encodeURIComponent codifica tildes, espacios, saltos de línea, & y ? para que el texto llegue completo.
  function enlaceWhatsApp(codigoPais, numero, texto) {
    const digitos = String(numero || '').replace(/\D/g, '');
    if (!digitos) return null;
    const pais = String(codigoPais || '').replace(/\D/g, '');
    return 'https://wa.me/' + pais + digitos + (texto ? '?text=' + encodeURIComponent(texto) : '');
  }

  // 1536000000 -> "1.5 GB" (consumo del MikroTik)
  function bytes(n) {
    const unidades = ['B', 'KB', 'MB', 'GB', 'TB'];
    let valor = Number(n) || 0;
    let i = 0;
    while (valor >= 1000 && i < unidades.length - 1) {
      valor /= 1000;
      i++;
    }
    return valor.toLocaleString(undefined, { maximumFractionDigits: i === 0 ? 0 : 1 }) + ' ' + unidades[i];
  }

  // 3400000 -> "3.4 Mbps" (velocidad actual de una cola)
  function velocidad(bps) {
    const unidades = ['bps', 'kbps', 'Mbps', 'Gbps'];
    let valor = Number(bps) || 0;
    let i = 0;
    while (valor >= 1000 && i < unidades.length - 1) {
      valor /= 1000;
      i++;
    }
    return valor.toLocaleString(undefined, { maximumFractionDigits: 1 }) + ' ' + unidades[i];
  }

  // "2026-10-08T14:05:00" -> fecha y hora cortas del navegador
  function fechaHora(iso) {
    if (!iso) return '';
    return new Date(iso).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
  }

  return { dinero, nombreMes, fecha, hoy, mesActual, mesesEnTexto, rellenarPlantilla, enlaceWhatsApp,
    bytes, velocidad, fechaHora };
})();
