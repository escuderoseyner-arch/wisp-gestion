// Código de pagos que comparten admin/pagos.html y admin/clientes.html:
// la ventana "Registrar pago" y funciones para mostrar meses, montos y estados.
// Se carga después de sesion.js y formato.js.

const Pagos = (() => {
  const URL_PAGOS = '/api/admin/pagos';

  const METODOS = { YAPE: 'Yape', PLIN: 'Plin', TRANSFERENCIA: 'Transferencia', EFECTIVO: 'Efectivo', OTRO: 'Otro' };
  const ESTADOS_MES = { PAGADO: 'Pagado', PENDIENTE: 'Pendiente', VENCIDO: 'Vencido', NO_APLICA: 'No aplica' };

  // Formatos: vienen de formato.js
  const { dinero, nombreMes, fecha, hoy, mesActual } = Formato;

  function crearEstadoMes(estado) {
    const span = document.createElement('span');
    span.className = 'estado estado-' + estado.toLowerCase();
    span.textContent = ESTADOS_MES[estado];
    return span;
  }

  // Botón "Recordar": enlace wa.me al celular del cliente con la plantilla de la empresa.
  //   {nombre} = nombre del cliente, {monto} = total que debe, {mes} = meses en español.
  // Devuelve null si el cliente no tiene celular o no hay meses que recordar.
  function crearBotonRecordar({ plantilla, codigoPais, moneda, celular, nombre, periodos, monto }) {
    if (!celular || !plantilla || periodos.length === 0) return null;
    const texto = Formato.rellenarPlantilla(plantilla, {
      nombre,
      monto: dinero(moneda, monto),
      mes: Formato.mesesEnTexto(periodos),
    });
    const url = Formato.enlaceWhatsApp(codigoPais, celular, texto);
    if (!url) return null;

    const enlace = document.createElement('a');
    enlace.className = 'boton boton-whatsapp boton-compacto';
    enlace.href = url;
    enlace.target = '_blank';
    enlace.rel = 'noopener noreferrer';
    enlace.textContent = 'Recordar';
    Iconos.en(enlace, 'whatsapp');
    enlace.setAttribute('aria-label', 'Enviar recordatorio por WhatsApp a ' + nombre);
    return enlace;
  }

  // Texto corto del vencimiento de un mes sin pagar
  function textoVence(mes) {
    if (mes.estado === 'VENCIDO') return 'Venció el ' + fecha(mes.vence);
    if (mes.periodo > mesActual()) return 'Adelantado · vence el ' + fecha(mes.vence);
    return 'Vence el ' + fecha(mes.vence);
  }

  // ---------- Ventana "Registrar pago" ----------

  // HTML fijo, sin ningún dato del usuario: por eso es seguro usar innerHTML aquí.
  // Los datos (nombres, meses) se ponen después con textContent.
  const PLANTILLA = `
    <form id="pago-form" novalidate>
      <h2 id="pago-titulo" class="panel-titulo">Registrar pago</h2>
      <p id="pago-cliente" class="saludo-sub"></p>
      <div id="pago-error" class="aviso aviso-error" role="alert" hidden></div>

      <fieldset class="grupo-meses">
        <legend>Meses que paga</legend>
        <div id="pago-meses" class="lista-meses"></div>
      </fieldset>

      <div class="fila-campos">
        <div class="campo">
          <label for="pago-monto">Monto por mes</label>
          <input id="pago-monto" type="number" inputmode="decimal" min="0.01" step="0.01" required>
        </div>
        <div class="campo">
          <label for="pago-fecha">Fecha de pago</label>
          <input id="pago-fecha" type="date" required>
        </div>
      </div>

      <div class="campo">
        <label for="pago-metodo">Método</label>
        <select id="pago-metodo" required></select>
      </div>

      <div class="campo">
        <label for="pago-observacion">Observación <span class="opcional">(opcional)</span></label>
        <input id="pago-observacion" type="text" maxlength="255" autocomplete="off"
               placeholder="Ej: descuento por corte de servicio">
      </div>

      <p class="total-pago">Total: <strong id="pago-total"></strong></p>

      <div class="acciones">
        <button type="submit" id="pago-guardar" class="boton boton-primario">Registrar pago</button>
        <button type="button" id="pago-cancelar" class="boton boton-secundario">Cancelar</button>
      </div>
    </form>`;

  let dialogo = null;
  let estadoCuenta = null;
  let guardado = false;
  const $ = (id) => document.getElementById(id);

  function crearDialogo() {
    dialogo = document.createElement('dialog');
    dialogo.className = 'dialogo dialogo-pago';
    dialogo.setAttribute('aria-labelledby', 'pago-titulo');
    dialogo.innerHTML = PLANTILLA;
    document.body.appendChild(dialogo);

    $('pago-metodo').replaceChildren(new Option('Elige el método', ''),
      ...Object.entries(METODOS).map(([valor, texto]) => new Option(texto, valor)));

    $('pago-form').addEventListener('submit', registrar);
    $('pago-cancelar').addEventListener('click', () => dialogo.close());
    $('pago-monto').addEventListener('input', actualizarTotal);
    $('pago-meses').addEventListener('change', actualizarTotal);
  }

  // Abre la ventana para un cliente. periodoPreferido: el mes que se marca (si se puede pagar).
  // Devuelve una promesa: true si se registró el pago, false si se canceló.
  // Si el estado de cuenta no se puede cargar, la promesa falla con el error.
  async function abrirFormulario(clienteId, periodoPreferido) {
    if (!dialogo) crearDialogo();
    estadoCuenta = await Sesion.api(URL_PAGOS + '/cliente/' + clienteId);
    if (estadoCuenta.mesesPorPagar.length === 0) {
      throw new Error('Este cliente no tiene meses por pagar.');
    }

    const c = estadoCuenta.cliente;
    $('pago-cliente').textContent = c.codigo + ' · ' + c.nombres + ' · ' + c.planNombre;
    Sesion.ocultar($('pago-error'));

    const marcar = estadoCuenta.mesesPorPagar.some((m) => m.periodo === periodoPreferido)
      ? periodoPreferido
      : estadoCuenta.periodoSugerido;
    $('pago-meses').replaceChildren(...estadoCuenta.mesesPorPagar.map((mes) => crearCasillaMes(mes, mes.periodo === marcar)));

    $('pago-monto').value = Number(estadoCuenta.montoSugerido).toFixed(2);
    $('pago-fecha').value = hoy();
    $('pago-fecha').max = hoy();
    $('pago-metodo').value = '';
    $('pago-observacion').value = '';
    $('pago-guardar').disabled = false;
    actualizarTotal();

    guardado = false;
    dialogo.showModal();
    return new Promise((resolver) => {
      dialogo.addEventListener('close', () => resolver(guardado), { once: true });
    });
  }

  function crearCasillaMes(mes, marcado) {
    const etiqueta = document.createElement('label');
    etiqueta.className = 'check-mes';

    const casilla = document.createElement('input');
    casilla.type = 'checkbox';
    casilla.value = mes.periodo;
    casilla.checked = marcado;

    const texto = document.createElement('span');
    texto.className = 'check-mes-texto';
    const nombre = document.createElement('strong');
    nombre.textContent = nombreMes(mes.periodo);
    const detalle = document.createElement('small');
    detalle.textContent = textoVence(mes);
    texto.append(nombre, detalle);

    etiqueta.append(casilla, texto, crearEstadoMes(mes.estado));
    return etiqueta;
  }

  function mesesMarcados() {
    return [...$('pago-meses').querySelectorAll('input:checked')].map((c) => c.value);
  }

  function actualizarTotal() {
    const monto = Number($('pago-monto').value) || 0;
    const cantidad = mesesMarcados().length;
    $('pago-total').textContent = dinero(estadoCuenta.moneda, monto * cantidad)
      + (cantidad > 1 ? ' (' + cantidad + ' meses)' : '');
  }

  async function registrar(evento) {
    evento.preventDefault();
    const cajaError = $('pago-error');
    Sesion.ocultar(cajaError);

    const datos = {
      clienteId: estadoCuenta.cliente.id,
      periodos: mesesMarcados(),
      monto: Number($('pago-monto').value),
      fechaPago: $('pago-fecha').value || null,
      metodo: $('pago-metodo').value || null,
      observacion: $('pago-observacion').value.trim(),
    };

    // Validación rápida en el navegador. El servidor vuelve a validar todo.
    const problemas = [];
    if (datos.periodos.length === 0) problemas.push('Marca al menos un mes.');
    if (!(datos.monto > 0)) problemas.push('El monto debe ser mayor que 0.');
    if (!datos.fechaPago) problemas.push('La fecha de pago es obligatoria.');
    else if (datos.fechaPago > hoy()) problemas.push('La fecha de pago no puede ser futura.');
    if (!datos.metodo) problemas.push('Elige el método de pago.');
    if (problemas.length > 0) {
      Sesion.mostrarMensaje(cajaError, 'Revisa los datos:', problemas);
      return;
    }

    const boton = $('pago-guardar');
    boton.disabled = true;
    boton.textContent = 'Guardando…';
    try {
      await Sesion.api(URL_PAGOS, { method: 'POST', body: datos });
      guardado = true;
      dialogo.close();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      boton.disabled = false;
      boton.textContent = 'Registrar pago';
    }
  }

  return {
    URL_PAGOS,
    METODOS,
    abrirFormulario,
    dinero,
    nombreMes,
    fecha,
    mesActual,
    crearEstadoMes,
    crearBotonRecordar,
    textoVence,
  };
})();
