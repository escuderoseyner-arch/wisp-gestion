// Caja por red para ADMIN: saldo, resumen del mes, retiros, descuento mensual e historial.
// El saldo lo calcula el servidor sumando movimientos; aquí solo se muestra.

(() => {
  const datos = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datos) return; // ya se está redirigiendo
  // El OPERADOR ve la caja y registra retiros; no los elimina ni edita Starlink (el servidor lo valida igual)
  const esAdmin = datos.rol === 'ADMIN';

  const URL_CAJAS = '/api/admin/cajas';
  const TIPOS = {
    INGRESO_PAGO: 'Pago de cliente',
    DESCUENTO_MENSUAL: 'Starlink',
    RETIRO: 'Retiro',
  };
  const $ = (id) => document.getElementById(id);

  const cajaExito = $('exito');
  const cajaError = $('error');
  const inputMes = $('mes');

  let cajaId = null;
  let caja = null;          // detalle de la caja elegida
  let verTodo = false;      // historial completo en vez de un mes
  let numeroDeCarga = 0;    // descarta respuestas viejas si se cambia rápido de mes

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);
  $('red').addEventListener('change', () => elegirCaja(Number($('red').value)));
  $('boton-retiro').addEventListener('click', abrirRetiro);
  $('boton-cancelar-retiro').addEventListener('click', cerrarRetiro);
  $('form-retiro').addEventListener('submit', guardarRetiro);
  $('form-descuento').addEventListener('submit', guardarDescuento);
  $('boton-editar-starlink').addEventListener('click', () => mostrarStarlink($('panel-starlink').hidden));
  $('boton-cancelar-starlink').addEventListener('click', () => mostrarStarlink(false));
  $('mes-anterior').addEventListener('click', () => moverMes(-1));
  $('mes-siguiente').addEventListener('click', () => moverMes(1));
  inputMes.addEventListener('change', () => { if (inputMes.value) cargarHistorial(); });
  $('boton-ver-todo').addEventListener('click', () => {
    verTodo = !verTodo;
    cargarHistorial();
  });

  inputMes.value = Formato.mesActual();
  iniciar();

  // ---------- Carga ----------

  async function iniciar() {
    try {
      const cajas = await Sesion.api(URL_CAJAS);
      if (cajas.length === 0) {
        $('sin-cajas').hidden = false;
        return;
      }
      const selector = $('red');
      selector.replaceChildren(...cajas.map((c) => {
        const opcion = document.createElement('option');
        opcion.value = c.id;
        opcion.textContent = c.redNombre;
        return opcion;
      }));
      $('vista').hidden = false;
      await elegirCaja(cajas[0].id);
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  async function elegirCaja(id) {
    cajaId = id;
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
    cerrarRetiro();
    mostrarStarlink(false);
    await Promise.all([cargarDetalle(), cargarHistorial()]);
  }

  async function cargarDetalle() {
    try {
      dibujarDetalle(await Sesion.api(URL_CAJAS + '/' + cajaId));
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  function dibujarDetalle(detalle) {
    caja = detalle;
    const m = detalle.moneda;
    const saldo = Number(detalle.saldo);
    $('saldo').textContent = Formato.dinero(m, saldo);
    $('saldo').classList.toggle('monto-deuda', saldo < 0);
    $('saldo-aviso').hidden = saldo >= 0;

    $('titulo-mes').textContent = 'Resumen de ' + Formato.nombreMes(detalle.mes);
    $('mes-ingresos').textContent = '+ ' + Formato.dinero(m, detalle.ingresosMes);
    $('mes-descuentos').textContent = '− ' + Formato.dinero(m, detalle.descuentosMes);
    $('mes-retiros').textContent = '− ' + Formato.dinero(m, detalle.retirosMes);

    const d = detalle.descuento;
    $('starlink-resumen').textContent = d.activo
      ? 'Starlink: ' + Formato.dinero(m, d.monto) + ' el día ' + d.dia
        + (d.proximaFecha ? ' · próximo: ' + Formato.fecha(d.proximaFecha) : '')
      : 'Starlink: pausado (no se descuenta)';
  }

  // ---------- Pago mensual de Starlink ----------

  // Despliega u oculta el formulario. Al abrirlo muestra los valores guardados.
  function mostrarStarlink(visible) {
    if (visible && caja) {
      const d = caja.descuento;
      $('descuento-monto').value = Number(d.monto).toFixed(2);
      $('descuento-dia').value = d.dia;
      $('descuento-activo').checked = d.activo;
      Sesion.ocultar($('error-descuento'));
    }
    $('panel-starlink').hidden = !visible;
    $('boton-editar-starlink').setAttribute('aria-expanded', String(visible));
    if (visible) $('descuento-monto').focus();
  }

  // ---------- Historial ----------

  function moverMes(delta) {
    const [anio, mes] = (inputMes.value || Formato.mesActual()).split('-').map(Number);
    const fecha = new Date(anio, mes - 1 + delta, 1);
    inputMes.value = fecha.getFullYear() + '-' + String(fecha.getMonth() + 1).padStart(2, '0');
    verTodo = false;
    cargarHistorial();
  }

  async function cargarHistorial() {
    if (!inputMes.value) inputMes.value = Formato.mesActual();
    $('navegador-mes').hidden = verTodo;
    $('boton-ver-todo').textContent = verTodo ? 'Ver por mes' : 'Ver todo';
    $('mes-nombre').textContent = Formato.nombreMes(inputMes.value);

    const miCarga = ++numeroDeCarga;
    const url = URL_CAJAS + '/' + cajaId + '/movimientos' + (verTodo ? '' : '?mes=' + encodeURIComponent(inputMes.value));
    try {
      const historial = await Sesion.api(url);
      if (miCarga !== numeroDeCarga) return;
      dibujarHistorial(historial);
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  // createElement + textContent (nunca innerHTML)
  function dibujarHistorial(historial) {
    const m = historial.moneda;
    const filas = historial.movimientos.map((mov) => {
      const fila = document.createElement('tr');
      const suma = mov.tipo === 'INGRESO_PAGO';

      const celda = (texto, clase) => {
        const td = document.createElement('td');
        td.textContent = texto;
        if (clase) td.className = clase;
        return td;
      };
      const saldo = Number(mov.saldo);

      const acciones = document.createElement('td');
      if (mov.eliminable && esAdmin) {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'boton boton-secundario boton-compacto';
        boton.textContent = 'Eliminar';
        Iconos.en(boton, 'basura');
        boton.setAttribute('aria-label', 'Eliminar el retiro del ' + Formato.fecha(mov.fecha));
        boton.addEventListener('click', () => eliminarRetiro(mov, m));
        acciones.appendChild(boton);
      }

      fila.append(
        celda(Formato.fecha(mov.fecha), 'sin-salto'),
        celda(TIPOS[mov.tipo] || mov.tipo, 'sin-salto'),
        celda(mov.descripcion),
        celda((suma ? '+ ' : '− ') + Formato.dinero(m, mov.monto), 'numero ' + (suma ? 'monto-ingreso' : 'monto-egreso')),
        celda(Formato.dinero(m, saldo), 'numero' + (saldo < 0 ? ' monto-deuda' : '')),
        celda(mov.hechoPor || 'Automático', 'sin-salto'),
        acciones,
      );
      return fila;
    });
    $('filas-historial').replaceChildren(...filas);
    $('tabla-historial').hidden = filas.length === 0;
    $('historial-vacio').hidden = filas.length > 0;
  }

  // ---------- Retiros ----------

  function abrirRetiro() {
    Sesion.ocultar(cajaExito);
    Sesion.ocultar($('error-retiro'));
    $('form-retiro').reset();
    $('retiro-fecha').value = Formato.hoy();
    $('retiro-fecha').max = Formato.hoy();
    $('panel-retiro').hidden = false;
    $('retiro-monto').focus();
  }

  function cerrarRetiro() {
    $('panel-retiro').hidden = true;
  }

  async function guardarRetiro(evento) {
    evento.preventDefault();
    const errorRetiro = $('error-retiro');
    Sesion.ocultar(errorRetiro);

    const monto = $('retiro-monto').value.trim();
    const fecha = $('retiro-fecha').value;
    const descripcion = $('retiro-descripcion').value.trim();
    const problemas = [];
    if (!monto || Number(monto) <= 0) problemas.push('El monto debe ser mayor que 0.');
    if (fecha && fecha > Formato.hoy()) problemas.push('La fecha no puede ser futura.');
    if (!descripcion) problemas.push('La descripción es obligatoria.');
    if (problemas.length > 0) {
      Sesion.mostrarMensaje(errorRetiro, 'Revisa los datos del retiro.', problemas);
      return;
    }

    const confirmado = await confirmar({
      titulo: 'Registrar retiro',
      texto: 'Se restarán ' + Formato.dinero(caja.moneda, monto) + ' de la caja de ' + caja.redNombre
        + ' con fecha ' + Formato.fecha(fecha || Formato.hoy()) + ': "' + descripcion + '".',
      boton: 'Sí, registrar retiro',
      peligro: false,
    });
    if (!confirmado) return;

    const boton = $('boton-guardar-retiro');
    boton.disabled = true;
    try {
      const detalle = await Sesion.api(URL_CAJAS + '/' + cajaId + '/retiros', {
        method: 'POST',
        body: { monto, fecha: fecha || null, descripcion },
      });
      dibujarDetalle(detalle);
      cerrarRetiro();
      Sesion.mostrarMensaje(cajaExito, 'Retiro registrado.');
      await cargarHistorial();
    } catch (error) {
      Sesion.mostrarError(errorRetiro, error);
    } finally {
      boton.disabled = false;
    }
  }

  async function eliminarRetiro(mov, moneda) {
    const confirmado = await confirmar({
      titulo: 'Eliminar retiro',
      texto: 'Se eliminará el retiro de ' + Formato.dinero(moneda, mov.monto) + ' del '
        + Formato.fecha(mov.fecha) + ' ("' + mov.descripcion + '") y el dinero vuelve a la caja.',
      boton: 'Sí, eliminar',
      peligro: true,
    });
    if (!confirmado) return;
    try {
      await Sesion.api(URL_CAJAS + '/' + cajaId + '/retiros/' + mov.id, { method: 'DELETE' });
      Sesion.mostrarMensaje(cajaExito, 'Retiro eliminado.');
      await Promise.all([cargarDetalle(), cargarHistorial()]);
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  // ---------- Descuento mensual ----------

  async function guardarDescuento(evento) {
    evento.preventDefault();
    const errorDescuento = $('error-descuento');
    Sesion.ocultar(errorDescuento);
    Sesion.ocultar(cajaExito);

    const monto = $('descuento-monto').value.trim();
    const dia = Number($('descuento-dia').value);
    const activo = $('descuento-activo').checked;
    const problemas = [];
    if (monto === '' || Number(monto) < 0) problemas.push('El monto no puede estar vacío ni ser negativo.');
    if (activo && Number(monto) === 0) problemas.push('Para activar el descuento, el monto debe ser mayor que 0.');
    if (!Number.isInteger(dia) || dia < 1 || dia > 28) problemas.push('El día debe estar entre 1 y 28.');
    if (problemas.length > 0) {
      Sesion.mostrarMensaje(errorDescuento, 'Revisa los datos del descuento.', problemas);
      return;
    }

    const boton = $('boton-guardar-descuento');
    boton.disabled = true;
    try {
      const detalle = await Sesion.api(URL_CAJAS + '/' + cajaId + '/descuento', {
        method: 'PUT',
        body: { monto, dia, activo },
      });
      dibujarDetalle(detalle);
      mostrarStarlink(false);
      Sesion.mostrarMensaje(cajaExito, 'Pago mensual de Starlink guardado.');
      await cargarHistorial(); // por si se aplicó un descuento al cambiar el día
    } catch (error) {
      Sesion.mostrarError(errorDescuento, error);
    } finally {
      boton.disabled = false;
    }
  }

  // ---------- Utilidades ----------

  // Ventana de confirmación con <dialog>. Devuelve true si se pulsó el botón de confirmar.
  function confirmar({ titulo, texto, boton, peligro }) {
    const dialogo = $('dialogo');
    $('dialogo-titulo').textContent = titulo;
    $('dialogo-texto').textContent = texto;
    $('dialogo-si').textContent = boton;
    $('dialogo-si').className = 'boton ' + (peligro ? 'boton-peligro' : 'boton-primario');
    dialogo.returnValue = '';
    return new Promise((resolver) => {
      dialogo.addEventListener('close', () => resolver(dialogo.returnValue === 'si'), { once: true });
      dialogo.showModal();
    });
  }
})();
