// Página pública de aviso por Wi-Fi (/aviso/<token>). No usa sesion.js: aquí nadie inicia sesión.
// El servidor devuelve solo los datos del cliente de ese token (su nombre, lo que debe y cómo pagar).

(() => {
  const $ = (id) => document.getElementById(id);

  const TITULOS = {
    SUAVE: 'Recordatorio de pago',
    VENCIDO: 'Tu pago está vencido',
    SUSPENDIDO: 'Servicio suspendido',
    AL_DIA: 'Estás al día',
  };
  const CLASES = { SUAVE: 'aviso-alerta', VENCIDO: 'aviso-error', SUSPENDIDO: 'aviso-error', AL_DIA: 'aviso-info' };

  // /aviso/<token> -> token; /aviso -> null
  const partes = location.pathname.split('/').filter(Boolean);
  const token = partes.length === 2 && partes[0] === 'aviso' ? partes[1] : null;

  $('boton-entendido').addEventListener('click', entendido);
  cargar();

  async function cargar() {
    try {
      if (!token) throw new Error('sin token');
      const aviso = await pedir('/api/public/aviso/' + encodeURIComponent(token));
      if (!aviso.nombre) {
        // La IP ya no es de un cliente: página general
        await mostrarGenerico();
        return;
      }
      dibujar(aviso);
      $('aviso').hidden = false;
    } catch {
      await mostrarGenerico();
    } finally {
      $('cargando').hidden = true;
    }
  }

  function dibujar(a) {
    const dinero = (monto) => Formato.dinero(a.moneda, monto);
    const mes = a.meses.length > 0 ? Formato.mesesEnTexto(a.meses) : '';
    empresa(a.empresa, a.logoUrl);

    const caja = $('caja-etapa');
    caja.className = 'aviso ' + CLASES[a.etapa];
    $('titulo-etapa').textContent = TITULOS[a.etapa];
    $('nombre').textContent = a.nombre;
    $('texto').textContent = a.texto
      ? Formato.rellenarPlantilla(a.texto, { nombre: a.nombre, monto: dinero(a.monto), mes })
      : 'No tienes pagos pendientes. ¡Gracias por pagar a tiempo!';
    $('texto').hidden = false;

    const debe = a.meses.length > 0;
    $('panel-deuda').hidden = !debe;
    $('al-dia').hidden = debe || a.etapa === 'SUSPENDIDO';
    if (debe) {
      $('deuda-total').textContent = dinero(a.monto);
      $('deuda-meses').textContent = (a.meses.length === 1 ? '1 mes: ' : a.meses.length + ' meses: ') + mes + '.';
    }

    // Cómo pagar
    const datosPago = [
      ['Yape', a.datosParaPagar.yapeNumero],
      ['Titular de Yape', a.datosParaPagar.yapeTitular],
      ['Cuenta bancaria', a.datosParaPagar.cuentaBancaria],
    ].filter(([, valor]) => valor);
    $('panel-pagar').hidden = !debe;
    $('datos-pago').replaceChildren(...datosPago.map(([etiqueta, valor]) => crearDato(etiqueta, valor)));
    $('datos-pago-vacio').hidden = datosPago.length > 0;

    const mensaje = 'Hola, soy ' + a.nombre + ' (código ' + a.codigo + ').'
      + (debe ? ' Ya pagué ' + mes + ' (' + dinero(a.monto) + ').' : '') + ' Te envío mi comprobante.';
    enlazar($('boton-comprobante'), $('whatsapp-texto'), a.soporte, debe ? mensaje : null);
    $('boton-entendido').hidden = !a.puedeEntendido;
  }

  async function entendido() {
    const boton = $('boton-entendido');
    boton.disabled = true;
    $('error').hidden = true;
    try {
      await pedir('/api/public/aviso/' + encodeURIComponent(token) + '/entendido', { method: 'POST' });
      boton.hidden = true;
      $('confirmacion').textContent = 'Listo. No volverás a ver este aviso hasta mañana a las 8:00 a. m. '
        + 'En un minuto tu internet sigue normal: ya puedes cerrar esta página.';
      $('confirmacion').hidden = false;
    } catch (error) {
      $('error').textContent = error.message;
      $('error').hidden = false;
      boton.disabled = false;
    }
  }

  // Sin datos de ningún cliente: solo la empresa y su WhatsApp
  async function mostrarGenerico() {
    $('aviso').hidden = true;
    try {
      const c = await pedir('/api/public/configuracion');
      empresa(c.nombreEmpresa, c.logoUrl);
      enlazar($('boton-contacto'), $('contacto-texto'), { codigoPais: c.codigoPais, whatsapp: c.whatsappSoporte },
        'Hola, me salió un aviso de pago en el Wi-Fi. ¿Me pueden ayudar?');
    } catch {
      // sin conexión con la web: queda el texto general
    }
    $('generico').hidden = false;
  }

  function empresa(nombre, logoUrl) {
    if (!nombre) return;
    document.querySelectorAll('[data-empresa]').forEach((el) => { el.textContent = nombre; });
    document.title = 'Aviso · ' + nombre;
    const img = document.querySelector('img[data-logo]');
    if (logoUrl && /^(https:\/\/|\/)/.test(logoUrl)) {
      img.src = logoUrl;
      img.alt = 'Logo de ' + nombre;
      img.addEventListener('error', () => { img.hidden = true; });
      img.hidden = false;
    }
  }

  // Botón de WhatsApp y, debajo, el número escrito (por si el celular no abre WhatsApp desde esta ventana)
  function enlazar(enlace, texto, soporte, mensaje) {
    const url = Formato.enlaceWhatsApp(soporte.codigoPais, soporte.whatsapp, mensaje);
    enlace.hidden = !url;
    texto.hidden = !url;
    if (!url) return;
    enlace.href = url;
    texto.textContent = 'Si el botón no abre WhatsApp, escríbenos al +' + soporte.codigoPais + ' ' + soporte.whatsapp + '.';
  }

  async function pedir(url, { method = 'GET' } = {}) {
    let respuesta;
    try {
      respuesta = await fetch(url, { method, headers: { Accept: 'application/json' } });
    } catch {
      throw new Error('No se pudo conectar. Inténtalo de nuevo en un momento.');
    }
    const datos = await respuesta.json().catch(() => null);
    if (!respuesta.ok) throw new Error((datos && datos.mensaje) || 'Ocurrió un error. Inténtalo de nuevo.');
    return datos;
  }

  function crearDato(etiqueta, valor) {
    const fila = document.createElement('div');
    const dt = document.createElement('dt');
    dt.textContent = etiqueta;
    const dd = document.createElement('dd');
    dd.textContent = valor;
    fila.append(dt, dd);
    return fila;
  }
})();
