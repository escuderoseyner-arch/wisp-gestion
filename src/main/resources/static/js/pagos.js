// Pagos de un mes para ADMIN: totales, estado de cada cliente de ese mes (incluye retirados después) y "Registrar pago".

(() => {
  const datosSesion = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datosSesion) return; // ya se está redirigiendo

  const $ = (id) => document.getElementById(id);
  const cajaExito = $('exito');
  const cajaError = $('error');
  const inputMes = $('mes');

  let resumen = null;       // última respuesta del servidor
  let numeroDeCarga = 0;    // evita que una respuesta lenta pise a una más nueva

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);

  inputMes.value = Pagos.mesActual();
  inputMes.addEventListener('change', () => { if (inputMes.value) cargar(); });
  $('mes-anterior').addEventListener('click', () => moverMes(-1));
  $('mes-siguiente').addEventListener('click', () => moverMes(1));
  $('filtro-buscar').addEventListener('input', dibujarLista);
  $('filtro-estado').addEventListener('change', dibujarLista);
  $('filtros').addEventListener('submit', (e) => e.preventDefault());

  cargar();

  function moverMes(delta) {
    const [anio, mes] = (inputMes.value || Pagos.mesActual()).split('-').map(Number);
    const fecha = new Date(anio, mes - 1 + delta, 1);
    inputMes.value = fecha.getFullYear() + '-' + String(fecha.getMonth() + 1).padStart(2, '0');
    cargar();
  }

  async function cargar() {
    const periodo = inputMes.value || Pagos.mesActual();
    $('mes-nombre').textContent = Pagos.nombreMes(periodo);
    const miCarga = ++numeroDeCarga;
    try {
      const datos = await Sesion.api(Pagos.URL_PAGOS + '/mes?periodo=' + encodeURIComponent(periodo));
      if (miCarga !== numeroDeCarga) return;
      resumen = datos;
      dibujarTotales();
      dibujarLista();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  function dibujarTotales() {
    const r = resumen;
    $('total-cobrado').textContent = Pagos.dinero(r.moneda, r.cobrado);
    $('total-cobrado-detalle').textContent = r.pagados === 1 ? '1 pagado' : r.pagados + ' pagados';
    $('total-pendiente').textContent = Pagos.dinero(r.moneda, r.pendiente);
    $('total-pendiente-detalle').textContent = r.pendientes + ' por vencer · ' + r.vencidos
      + (r.vencidos === 1 ? ' vencido' : ' vencidos');
  }

  // El filtro se aplica en el navegador: la lista del mes ya está cargada
  function dibujarLista() {
    if (!resumen) return;
    const busqueda = $('filtro-buscar').value.trim().toLowerCase();
    const estado = $('filtro-estado').value;
    const filas = resumen.clientes.filter((f) =>
      (!estado || f.estado === estado)
      && (!busqueda || f.codigo.toLowerCase().includes(busqueda) || f.nombres.toLowerCase().includes(busqueda)));

    $('lista-pagos').replaceChildren(...filas.map(crearTarjeta));
    $('vacio').hidden = filas.length > 0;
  }

  // Siempre textContent, nunca innerHTML
  function crearTarjeta(fila) {
    const item = document.createElement('li');
    item.className = 'tarjeta-plan';

    const cabecera = document.createElement('div');
    cabecera.className = 'tarjeta-plan-cabecera';
    const codigo = document.createElement('span');
    codigo.className = 'codigo';
    codigo.textContent = fila.codigo;
    cabecera.append(codigo, Pagos.crearEstadoMes(fila.estado));

    const nombre = document.createElement('h2');
    nombre.className = 'tarjeta-plan-nombre';
    nombre.textContent = fila.nombres;

    const plan = document.createElement('p');
    plan.className = 'tarjeta-plan-velocidad';
    // Un retirado aparece si ese mes todavía era cliente o si pagó ese mes
    plan.textContent = [fila.zona || 'Sin zona', fila.planNombre].concat(fila.retirado ? ['Retirado'] : []).join(' · ');

    const detalle = document.createElement('p');
    detalle.className = 'tarjeta-cliente-pago';
    if (fila.pago) {
      detalle.textContent = 'Pagó ' + Pagos.dinero(resumen.moneda, fila.pago.monto) + ' el '
        + Pagos.fecha(fila.pago.fechaPago) + ' · ' + Pagos.METODOS[fila.pago.metodo];
    } else {
      detalle.textContent = Pagos.dinero(resumen.moneda, fila.precioPlan) + ' · '
        + Pagos.textoVence({ estado: fila.estado, vence: fila.vence, periodo: resumen.periodo });
    }

    const acciones = document.createElement('div');
    acciones.className = 'tarjeta-plan-acciones';
    const verCliente = document.createElement('a');
    verCliente.className = 'boton boton-secundario boton-compacto';
    verCliente.href = '/admin/clientes.html#cliente/' + fila.clienteId;
    verCliente.textContent = 'Ver cliente';
    Iconos.en(verCliente, 'usuario');
    if (fila.pago) {
      acciones.appendChild(verCliente);
      acciones.classList.add('una-accion');
    } else {
      const registrar = document.createElement('button');
      registrar.type = 'button';
      registrar.className = 'boton boton-primario boton-compacto';
      registrar.textContent = 'Registrar pago';
      Iconos.en(registrar, 'mas');
      registrar.setAttribute('aria-label', 'Registrar pago de ' + fila.nombres);
      registrar.addEventListener('click', () => registrarPago(fila, registrar));
      acciones.append(registrar, verCliente);
    }

    item.append(cabecera, nombre, plan, detalle, acciones);
    return item;
  }

  async function registrarPago(fila, boton) {
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
    boton.disabled = true;
    try {
      const registrado = await Pagos.abrirFormulario(fila.clienteId, resumen.periodo);
      if (registrado) {
        Sesion.mostrarMensaje(cajaExito, 'Pago de ' + fila.codigo + ' registrado.');
        await cargar();
      }
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      boton.disabled = false;
    }
  }
})();
