// Clientes para ADMIN: lista con filtros, detalle con acciones y formulario
// (crear, editar y reasignar código). La vista se elige con el # de la URL:
//   (vacío)              -> lista
//   #nuevo               -> crear
//   #cliente/5           -> detalle
//   #cliente/5/editar    -> editar
//   #cliente/5/reasignar -> reasignar el código a otra persona
// Así el botón "atrás" del celular vuelve a la vista anterior.

(() => {
  const datosSesion = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datosSesion) return; // ya se está redirigiendo

  const URL_CLIENTES = '/api/admin/clientes';
  const URL_PLANES_ACTIVOS = '/api/admin/planes?activos=true';
  const URL_REDES = '/api/admin/redes';

  const ETIQUETA_ESTADO = { ACTIVO: 'Activo', SUSPENDIDO: 'Suspendido', RETIRADO: 'Retirado' };
  const FORMATO_CODIGO = /^C-\d{2,8}$/;
  const FORMATO_CELULAR = /^9\d{8}$/;
  const FORMATO_NOMBRE_COLA = /^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$/;
  const FORMATO_IPV4 = /^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/;

  const $ = (id) => document.getElementById(id);
  const cajaExito = $('exito');
  const cajaError = $('error');
  const vistas = { lista: $('vista-lista'), detalle: $('vista-detalle'), formulario: $('vista-formulario') };

  const formatoPrecio = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  // Mensaje de éxito para mostrar después de cambiar de vista
  let mensajePendiente = null;
  // Cliente abierto en el detalle (para las acciones)
  let clienteActual = null;
  // Formulario: { modo: 'crear' | 'editar' | 'reasignar', cliente }
  let estadoFormulario = null;
  // Evita que una respuesta lenta de la lista pise a una más nueva
  let numeroDeCarga = 0;

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);
  configurarLista();
  configurarDetalle();
  $('form-cliente').addEventListener('submit', guardarFormulario);
  window.addEventListener('hashchange', router);
  router();

  // =====================================================================
  // Navegación
  // =====================================================================

  async function router() {
    limpiarAvisos();
    ocultarPassword();
    if (mensajePendiente) {
      Sesion.mostrarMensaje(cajaExito, mensajePendiente);
      mensajePendiente = null;
    }

    const ruta = location.hash.replace(/^#/, '');
    let partes;
    if (ruta === 'nuevo') return abrirFormulario('crear');
    if ((partes = ruta.match(/^cliente\/(\d+)$/))) return mostrarDetalle(Number(partes[1]));
    if ((partes = ruta.match(/^cliente\/(\d+)\/editar$/))) return abrirFormulario('editar', Number(partes[1]));
    if ((partes = ruta.match(/^cliente\/(\d+)\/reasignar$/))) return abrirFormulario('reasignar', Number(partes[1]));
    return mostrarLista();
  }

  // Cambia de vista; si ya estamos en esa ruta, la recarga
  function irA(hash, mensaje) {
    mensajePendiente = mensaje || null;
    const actual = location.hash || '#';
    if (actual === hash) {
      router();
    } else {
      location.hash = hash;
    }
  }

  function mostrarVista(nombre) {
    Object.entries(vistas).forEach(([clave, seccion]) => {
      seccion.hidden = clave !== nombre;
    });
    window.scrollTo(0, 0);
  }

  // =====================================================================
  // Lista
  // =====================================================================

  function configurarLista() {
    let espera;
    $('filtro-buscar').addEventListener('input', () => {
      clearTimeout(espera);
      espera = setTimeout(cargarLista, 300); // espera a que deje de escribir
    });
    $('filtro-zona').addEventListener('change', cargarLista);
    $('filtro-estado').addEventListener('change', cargarLista);
    $('filtros').addEventListener('submit', (evento) => {
      evento.preventDefault(); // "Enter" en el buscador no recarga la página
      cargarLista();
    });
  }

  function mostrarLista() {
    mostrarVista('lista');
    cargarZonas(); // por si se creó una zona nueva
    cargarLista();
  }

  async function cargarLista() {
    const parametros = new URLSearchParams();
    const busqueda = $('filtro-buscar').value.trim();
    if (busqueda) parametros.set('q', busqueda);
    if ($('filtro-zona').value) parametros.set('zona', $('filtro-zona').value);
    if ($('filtro-estado').value) parametros.set('estado', $('filtro-estado').value);

    const miCarga = ++numeroDeCarga;
    try {
      const clientes = await Sesion.api(URL_CLIENTES + (parametros.toString() ? '?' + parametros : ''));
      if (miCarga !== numeroDeCarga) return; // llegó otra búsqueda más nueva
      $('lista-clientes').replaceChildren(...clientes.map(crearTarjeta));
      $('vacio').hidden = clientes.length > 0;
      $('contador').textContent = clientes.length === 1 ? '1 cliente' : clientes.length + ' clientes';
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  // Toda la tarjeta es un enlace al detalle. Siempre textContent, nunca innerHTML.
  function crearTarjeta(cliente) {
    const item = document.createElement('li');
    const enlace = document.createElement('a');
    enlace.className = 'tarjeta-cliente' + (cliente.estado === 'RETIRADO' ? ' inactivo' : '');
    enlace.href = '#cliente/' + cliente.id;

    const cabecera = document.createElement('div');
    cabecera.className = 'tarjeta-plan-cabecera';
    cabecera.append(crearSpan('codigo', cliente.codigo), crearEstado(cliente.estado));

    const nombre = document.createElement('h2');
    nombre.className = 'tarjeta-plan-nombre';
    nombre.textContent = cliente.nombres;

    const detalle = document.createElement('p');
    detalle.className = 'tarjeta-plan-velocidad';
    detalle.textContent = [cliente.zona || 'Sin zona', cliente.planNombre].join(' · ');

    const pago = document.createElement('p');
    pago.className = 'tarjeta-cliente-pago';
    pago.textContent = 'Paga el día ' + cliente.diaPago;

    enlace.append(cabecera, nombre, detalle, pago);
    item.appendChild(enlace);
    return item;
  }

  // Llena el filtro de zonas y las sugerencias del formulario
  async function cargarZonas() {
    try {
      const zonas = await Sesion.api(URL_CLIENTES + '/zonas');
      const filtro = $('filtro-zona');
      const seleccionada = filtro.value;
      filtro.replaceChildren(new Option('Todas', ''), ...zonas.map((z) => new Option(z, z)));
      filtro.value = zonas.includes(seleccionada) ? seleccionada : '';
      $('lista-zonas').replaceChildren(...zonas.map((z) => new Option(z)));
    } catch {
      // sin zonas el filtro queda en "Todas"
    }
  }

  // =====================================================================
  // Detalle
  // =====================================================================

  function configurarDetalle() {
    $('accion-suspender').addEventListener('click', (e) => accionSimple(e.currentTarget, 'suspender', 'Cliente suspendido.'));
    $('accion-reactivar').addEventListener('click', (e) => accionSimple(e.currentTarget, 'reactivar', 'Cliente reactivado.'));
    $('accion-retirar').addEventListener('click', retirar);
    $('boton-crear-cuenta').addEventListener('click', (e) => generarPassword(e.currentTarget, 'crear'));
    $('boton-restablecer').addEventListener('click', (e) => generarPassword(e.currentTarget, 'restablecer'));
    $('boton-copiar').addEventListener('click', copiarMensaje);
    $('boton-ocultar-password').addEventListener('click', ocultarPassword);
    $('boton-registrar-pago').addEventListener('click', registrarPago);
    $('boton-cambiar-usuario').addEventListener('click', abrirFormularioUsuario);
    $('boton-cancelar-usuario').addEventListener('click', cerrarFormularioUsuario);
    $('form-usuario').addEventListener('submit', cambiarUsuario);
  }

  // ---------- Pagos del cliente ----------

  // Plantilla y código de país para "Recordar": se piden una sola vez
  let configuracion = null;

  async function cargarPagos(clienteId) {
    try {
      if (!configuracion) configuracion = Sesion.api('/api/admin/configuracion');
      const [cuenta, config] = await Promise.all([
        Sesion.api(Pagos.URL_PAGOS + '/cliente/' + clienteId),
        configuracion,
      ]);
      if (!clienteActual || clienteActual.id !== clienteId) return; // ya se abrió otro cliente
      dibujarPagos(cuenta, config);
    } catch (error) {
      configuracion = null; // se vuelve a intentar la próxima vez
      Sesion.mostrarError(cajaError, error);
    }
  }

  // Qué recordar: los meses vencidos; si está al día, el mes actual si aún no lo paga
  function botonRecordar(cuenta, config) {
    if (clienteActual.estado === 'RETIRADO') return null;
    let periodos = cuenta.meses.filter((m) => m.estado === 'VENCIDO').map((m) => m.periodo);
    let monto = cuenta.deuda;
    if (periodos.length === 0) {
      const actual = cuenta.meses.find((m) => m.periodo === Formato.mesActual() && m.estado === 'PENDIENTE');
      if (!actual) return null;
      periodos = [actual.periodo];
      monto = cuenta.montoSugerido;
    }
    return Pagos.crearBotonRecordar({
      plantilla: config.plantillaRecordatorio,
      codigoPais: config.codigoPais,
      moneda: cuenta.moneda,
      celular: clienteActual.celular,
      nombre: clienteActual.nombres,
      periodos,
      monto,
    });
  }

  function dibujarPagos(cuenta, config) {
    const moneda = cuenta.moneda;
    $('pagos-acciones').querySelectorAll('.boton-whatsapp').forEach((b) => b.remove());
    const recordar = botonRecordar(cuenta, config);
    if (recordar) $('pagos-acciones').prepend(recordar);

    let resumen = cuenta.mesesVencidos === 0
      ? 'Al día.'
      : 'Debe ' + cuenta.mesesVencidos + (cuenta.mesesVencidos === 1 ? ' mes' : ' meses') + ': '
        + Pagos.dinero(moneda, cuenta.deuda) + '.';
    if (cuenta.periodoSugerido) resumen += ' Próximo por pagar: ' + Pagos.nombreMes(cuenta.periodoSugerido) + '.';
    $('pagos-resumen').textContent = resumen;
    $('pagos-resumen').classList.toggle('con-deuda', cuenta.mesesVencidos > 0);
    $('boton-registrar-pago').hidden = cuenta.mesesPorPagar.length === 0;

    // Meses: estado de cada uno, el más reciente primero
    $('pagos-meses').replaceChildren(...cuenta.meses.map((mes) => {
      const detalle = mes.pago
        ? Pagos.dinero(moneda, mes.pago.monto) + ' · pagó el ' + Pagos.fecha(mes.pago.fechaPago)
        : Pagos.textoVence(mes);
      return crearFila(Pagos.nombreMes(mes.periodo), detalle, Pagos.crearEstadoMes(mes.estado));
    }));
    $('pagos-meses-vacio').hidden = cuenta.meses.length > 0;

    // Historial: cada pago con su botón para eliminarlo si se registró por error
    $('pagos-historial').replaceChildren(...cuenta.historial.map((pago) => {
      const partes = [
        Pagos.dinero(moneda, pago.monto),
        Pagos.fecha(pago.fechaPago),
        Pagos.METODOS[pago.metodo],
        'registró ' + pago.registradoPor,
      ];
      if (pago.observacion) partes.push(pago.observacion);

      const eliminar = document.createElement('button');
      eliminar.type = 'button';
      eliminar.className = 'boton boton-secundario boton-compacto';
      eliminar.textContent = 'Eliminar';
      Iconos.en(eliminar, 'basura');
      eliminar.setAttribute('aria-label', 'Eliminar el pago de ' + Pagos.nombreMes(pago.periodo));
      eliminar.addEventListener('click', () => eliminarPago(pago, eliminar));
      return crearFila(Pagos.nombreMes(pago.periodo), partes.join(' · '), eliminar);
    }));
    $('pagos-historial-vacio').hidden = cuenta.historial.length > 0;
  }

  function crearFila(titulo, detalle, extra) {
    const item = document.createElement('li');
    item.className = 'fila';
    const texto = document.createElement('div');
    texto.className = 'fila-texto';
    const fuerte = document.createElement('strong');
    fuerte.textContent = titulo;
    const pequeno = document.createElement('small');
    pequeno.textContent = detalle;
    texto.append(fuerte, pequeno);
    item.append(texto, extra);
    return item;
  }

  async function registrarPago() {
    limpiarAvisos();
    const id = clienteActual.id;
    try {
      if (await Pagos.abrirFormulario(id)) {
        Sesion.mostrarMensaje(cajaExito, 'Pago registrado.');
        await cargarPagos(id);
      }
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  async function eliminarPago(pago, boton) {
    const confirmado = await confirmar({
      titulo: '¿Eliminar el pago de ' + Pagos.nombreMes(pago.periodo) + '?',
      texto: 'Úsalo solo si se registró por error. El mes volverá a quedar sin pagar.',
      boton: 'Sí, eliminar',
    });
    if (!confirmado) return;

    limpiarAvisos();
    boton.disabled = true;
    try {
      await Sesion.api(Pagos.URL_PAGOS + '/' + pago.id, { method: 'DELETE' });
      Sesion.mostrarMensaje(cajaExito, 'Pago de ' + Pagos.nombreMes(pago.periodo) + ' eliminado.');
      await cargarPagos(clienteActual.id);
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
      boton.disabled = false;
    }
  }

  // ---------- Cambiar usuario de acceso ----------

  function abrirFormularioUsuario() {
    limpiarAvisos();
    Sesion.ocultar($('error-usuario'));
    $('nuevo-usuario').value = clienteActual.cuenta.username;
    $('form-usuario').hidden = false;
    $('cuenta-botones').hidden = true;
    $('nuevo-usuario').focus();
    $('nuevo-usuario').select();
  }

  function cerrarFormularioUsuario() {
    $('form-usuario').hidden = true;
    $('cuenta-botones').hidden = false;
    $('nuevo-usuario').value = '';
  }

  async function cambiarUsuario(evento) {
    evento.preventDefault();
    const cajaErrorUsuario = $('error-usuario');
    Sesion.ocultar(cajaErrorUsuario);
    const c = clienteActual;
    const username = $('nuevo-usuario').value.trim();

    if (!/^[A-Za-z0-9._-]{3,50}$/.test(username)) {
      Sesion.mostrarMensaje(cajaErrorUsuario,
        'El usuario debe tener de 3 a 50 caracteres: letras sin tildes, números, punto, guion o guion bajo.');
      return;
    }
    if (username === c.cuenta.username) {
      cerrarFormularioUsuario();
      return;
    }

    const confirmado = await confirmar({
      titulo: '¿Cambiar el usuario?',
      texto: c.nombres + ' tendrá que ingresar con "' + username + '" en lugar de "' + c.cuenta.username
        + '". Su contraseña no cambia. Avísale del cambio.',
      boton: 'Sí, cambiar',
    });
    if (!confirmado) return;

    const boton = $('boton-guardar-usuario');
    boton.disabled = true;
    try {
      dibujarDetalle(await Sesion.api(URL_CLIENTES + '/' + c.id + '/cuenta/usuario', {
        method: 'PUT',
        body: { username },
      }));
      Sesion.mostrarMensaje(cajaExito, 'Usuario cambiado a "' + username + '".');
    } catch (error) {
      Sesion.mostrarError(cajaErrorUsuario, error);
    } finally {
      boton.disabled = false;
    }
  }

  async function mostrarDetalle(id) {
    mostrarVista('detalle');
    clienteActual = null;
    try {
      dibujarDetalle(await Sesion.api(URL_CLIENTES + '/' + id));
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  function dibujarDetalle(cliente) {
    clienteActual = cliente;
    const retirado = cliente.estado === 'RETIRADO';

    $('det-codigo').textContent = cliente.codigo;
    $('det-nombre').textContent = cliente.nombres;
    const estado = $('det-estado');
    estado.className = 'estado estado-' + cliente.estado.toLowerCase();
    estado.textContent = ETIQUETA_ESTADO[cliente.estado];

    const plan = cliente.plan.nombre + ' · ' + formatoPrecio.format(cliente.plan.precio) + ' / mes'
      + (cliente.plan.activo ? '' : ' (plan desactivado)');
    const filas = [
      ['Celular', cliente.celular],
      ['Zona', cliente.zona],
      ['Referencia', cliente.referencia],
      ['Plan', plan],
      ['Día de pago', 'Día ' + cliente.diaPago + ' de cada mes'],
      ['Fecha de inicio', formatearFecha(cliente.fechaInicio)],
      ['IP', cliente.ip],
      ['Red', cliente.red ? cliente.red.nombre : null],
      ['Cola', cliente.red ? cliente.nombreCola : null],
    ];
    if (retirado) filas.push(['Fecha de retiro', formatearFecha(cliente.fechaRetiro)]);
    $('det-datos').replaceChildren(...filas.map(([etiqueta, valor]) => crearDato(etiqueta, valor)));

    // Cuenta del portal
    const cuenta = cliente.cuenta;
    $('cuenta-datos').hidden = !cuenta;
    let textoCuenta = '';
    if (cuenta) {
      $('cuenta-usuario').textContent = cuenta.username;
      $('cuenta-estado').textContent = (cuenta.activa ? 'Activa' : 'Desactivada')
        + (cuenta.debeCambiarPassword && cuenta.activa ? ' · aún no cambia su contraseña temporal' : '');
    } else if (retirado) {
      textoCuenta = 'No tiene cuenta.';
    } else if (cliente.celular) {
      textoCuenta = 'Todavía no tiene cuenta. Su usuario inicial será su celular.';
    } else {
      textoCuenta = 'Agrega un celular al cliente para poder crearle una cuenta.';
    }
    $('cuenta-texto').textContent = textoCuenta;
    $('cuenta-texto').hidden = !textoCuenta;
    $('boton-crear-cuenta').hidden = retirado || Boolean(cuenta) || !cliente.celular;
    $('boton-restablecer').hidden = retirado || !cuenta;
    $('boton-cambiar-usuario').hidden = retirado || !cuenta;
    cerrarFormularioUsuario();
    cargarPagos(cliente.id);

    // Acciones: un retirado ya no se modifica
    $('panel-acciones').hidden = retirado;
    $('accion-editar').href = '#cliente/' + cliente.id + '/editar';
    $('accion-reasignar').href = '#cliente/' + cliente.id + '/reasignar';
    $('accion-suspender').hidden = cliente.estado !== 'ACTIVO';
    $('accion-reactivar').hidden = cliente.estado !== 'SUSPENDIDO';
    document.querySelectorAll('#vista-detalle button').forEach((b) => { b.disabled = false; });
  }

  // Suspender y reactivar: se pueden deshacer, así que no piden confirmación
  async function accionSimple(boton, accion, mensaje) {
    limpiarAvisos();
    boton.disabled = true;
    try {
      dibujarDetalle(await Sesion.api(URL_CLIENTES + '/' + clienteActual.id + '/' + accion, { method: 'POST' }));
      Sesion.mostrarMensaje(cajaExito, mensaje);
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
      boton.disabled = false;
    }
  }

  async function retirar() {
    const c = clienteActual;
    const confirmado = await confirmar({
      titulo: '¿Retirar a ' + c.codigo + '?',
      texto: c.nombres + ' quedará como RETIRADO desde hoy'
        + (c.cuenta ? ' y su cuenta del portal se desactivará' : '')
        + '. Su historial se conserva. Esta acción no se puede deshacer.',
      boton: 'Sí, retirar',
    });
    if (!confirmado) return;
    await accionSimple($('accion-retirar'), 'retirar', 'Cliente ' + c.codigo + ' retirado.');
  }

  // ---------- Cuenta del portal ----------

  async function generarPassword(boton, tipo) {
    const c = clienteActual;
    if (tipo === 'restablecer') {
      const confirmado = await confirmar({
        titulo: '¿Restablecer la contraseña?',
        texto: 'La contraseña actual de ' + c.nombres + ' dejará de funcionar y se generará una temporal nueva.',
        boton: 'Sí, restablecer',
      });
      if (!confirmado) return;
    }

    limpiarAvisos();
    boton.disabled = true;
    try {
      const ruta = URL_CLIENTES + '/' + c.id + '/cuenta' + (tipo === 'restablecer' ? '/restablecer-password' : '');
      const cuenta = await Sesion.api(ruta, { method: 'POST' });
      mostrarPassword(c, cuenta);
      // Se recarga el detalle para ver el estado de la cuenta, sin borrar la contraseña de la pantalla
      dibujarDetalle(await Sesion.api(URL_CLIENTES + '/' + c.id));
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
      boton.disabled = false;
    }
  }

  function mostrarPassword(cliente, cuenta) {
    $('pass-usuario').textContent = cuenta.username;
    $('pass-valor').textContent = cuenta.passwordTemporal;
    $('pass-mensaje').value = 'Hola ' + cliente.nombres + ', ya puedes ver tu servicio de internet en '
      + location.origin + '/login.html\nUsuario: ' + cuenta.username
      + '\nContraseña temporal: ' + cuenta.passwordTemporal
      + '\nAl ingresar te pedirá crear una contraseña nueva.';
    $('panel-password').hidden = false;
    $('panel-password').scrollIntoView({ block: 'start', behavior: 'smooth' });
  }

  // La contraseña se borra de la página: no queda guardada en ningún lado
  function ocultarPassword() {
    $('panel-password').hidden = true;
    $('pass-usuario').textContent = '';
    $('pass-valor').textContent = '';
    $('pass-mensaje').value = '';
  }

  async function copiarMensaje() {
    const texto = $('pass-mensaje');
    try {
      await navigator.clipboard.writeText(texto.value);
      Sesion.mostrarMensaje(cajaExito, 'Mensaje copiado. Pégalo en WhatsApp.');
    } catch {
      // El portapapeles solo funciona en https o localhost: se selecciona el texto para copiarlo a mano
      texto.focus();
      texto.select();
      Sesion.mostrarMensaje(cajaExito, 'Mantén presionado el texto seleccionado y elige "Copiar".');
    }
  }

  // =====================================================================
  // Formulario: crear, editar y reasignar
  // =====================================================================

  async function abrirFormulario(modo, id) {
    mostrarVista('formulario');
    const form = $('form-cliente');
    form.reset();
    Sesion.ocultar($('error-formulario'));
    form.hidden = true; // hasta tener los datos
    estadoFormulario = null;

    try {
      // El orden de las variables debe coincidir con el de las promesas (cargarZonas no devuelve nada)
      const [planes, cliente, sugerido, , redes] = await Promise.all([
        Sesion.api(URL_PLANES_ACTIVOS),
        id ? Sesion.api(URL_CLIENTES + '/' + id) : null,
        modo === 'crear' ? Sesion.api(URL_CLIENTES + '/siguiente-codigo') : null,
        cargarZonas(),
        Sesion.api(URL_REDES),
      ]);
      if (cliente && cliente.estado === 'RETIRADO') {
        irA('#cliente/' + cliente.id);
        return;
      }
      estadoFormulario = { modo, cliente };
      form.redId.replaceChildren(new Option('Ninguna', ''), ...redes.map((r) => new Option(r.nombre, r.id)));
      prepararFormulario(modo, cliente, planes, sugerido);
      form.hidden = false;
      (modo === 'editar' ? form.codigo : form.nombres).focus({ preventScroll: true });
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  function prepararFormulario(modo, cliente, planes, sugerido) {
    const form = $('form-cliente');
    const volver = cliente ? '#cliente/' + cliente.id : '#';
    $('form-volver').href = volver;
    $('boton-cancelar').href = volver;

    // Planes activos; al editar se agrega el plan actual aunque esté desactivado
    const opciones = planes.map((p) => new Option(p.nombre + ' · ' + formatoPrecio.format(p.precio), p.id));
    if (modo === 'editar' && !cliente.plan.activo) {
      opciones.unshift(new Option(cliente.plan.nombre + ' (desactivado, plan actual)', cliente.plan.id));
    }
    form.planId.replaceChildren(new Option('Elige un plan', ''), ...opciones);

    form.codigo.readOnly = modo === 'reasignar';
    form.fechaInicio.value = hoy();

    if (modo === 'crear') {
      $('form-titulo').textContent = 'Nuevo cliente';
      $('form-sub').textContent = 'El código sugerido es el siguiente libre; puedes cambiarlo.';
      form.codigo.value = sugerido.codigo;
      $('boton-guardar').textContent = 'Crear cliente';
    } else if (modo === 'editar') {
      $('form-titulo').textContent = 'Editar ' + cliente.codigo;
      $('form-sub').textContent = cliente.cuenta
        ? 'Cambiar el celular no cambia su usuario de acceso.'
        : '';
      llenarDatos(form, cliente);
      $('boton-guardar').textContent = 'Guardar cambios';
    } else {
      $('form-titulo').textContent = 'Reasignar código ' + cliente.codigo;
      $('form-sub').textContent = 'Datos de la persona NUEVA. ' + cliente.nombres
        + ' quedará retirado desde hoy y su historial se conserva.';
      form.codigo.value = cliente.codigo;
      // Se mantiene lo que suele ser del lugar (zona, referencia, plan, IP del equipo)
      form.zona.value = cliente.zona || '';
      form.referencia.value = cliente.referencia || '';
      form.ip.value = cliente.ip || '';
      form.redId.value = cliente.red ? cliente.red.id : '';
      form.nombreCola.value = cliente.red ? cliente.nombreCola : '';
      if (cliente.plan.activo) form.planId.value = cliente.plan.id;
      $('boton-guardar').textContent = 'Reasignar código';
    }
  }

  function llenarDatos(form, cliente) {
    form.codigo.value = cliente.codigo;
    form.nombres.value = cliente.nombres;
    form.celular.value = cliente.celular || '';
    form.zona.value = cliente.zona || '';
    form.referencia.value = cliente.referencia || '';
    form.planId.value = cliente.plan.id;
    form.diaPago.value = cliente.diaPago;
    form.fechaInicio.value = cliente.fechaInicio;
    form.ip.value = cliente.ip || '';
    form.redId.value = cliente.red ? cliente.red.id : '';
    form.nombreCola.value = cliente.red ? cliente.nombreCola : '';
  }

  async function guardarFormulario(evento) {
    evento.preventDefault();
    if (!estadoFormulario) return;
    const { modo, cliente } = estadoFormulario;
    const form = $('form-cliente');
    const cajaErrorFormulario = $('error-formulario');
    limpiarAvisos();
    Sesion.ocultar(cajaErrorFormulario);

    const datos = {
      codigo: form.codigo.value.trim().toUpperCase(),
      nombres: form.nombres.value.trim(),
      celular: form.celular.value.replace(/[\s-]/g, ''),
      zona: form.zona.value.trim(),
      referencia: form.referencia.value.trim(),
      planId: form.planId.value ? Number(form.planId.value) : null,
      diaPago: form.diaPago.value ? Number(form.diaPago.value) : null,
      fechaInicio: form.fechaInicio.value || null,
      ip: form.ip.value.trim(),
      redId: form.redId.value ? Number(form.redId.value) : null,
      nombreCola: form.nombreCola.value.trim(),
    };

    const problemas = validar(datos);
    if (problemas.length > 0) {
      Sesion.mostrarMensaje(cajaErrorFormulario, 'Revisa los datos:', problemas);
      cajaErrorFormulario.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
      return;
    }

    if (modo === 'reasignar') {
      const confirmado = await confirmar({
        titulo: '¿Reasignar el código ' + cliente.codigo + '?',
        texto: cliente.nombres + ' quedará RETIRADO desde hoy (su historial se conserva) y '
          + datos.nombres + ' será el nuevo cliente ' + cliente.codigo + '. No se puede deshacer.',
        boton: 'Sí, reasignar',
      });
      if (!confirmado) return;
      delete datos.codigo; // el servidor usa el código del cliente que se retira
    }

    const boton = $('boton-guardar');
    const textoBoton = boton.textContent;
    boton.disabled = true;
    boton.textContent = 'Guardando…';
    try {
      let guardado;
      if (modo === 'crear') {
        guardado = await Sesion.api(URL_CLIENTES, { method: 'POST', body: datos });
      } else if (modo === 'editar') {
        guardado = await Sesion.api(URL_CLIENTES + '/' + cliente.id, { method: 'PUT', body: datos });
      } else {
        guardado = await Sesion.api(URL_CLIENTES + '/' + cliente.id + '/reasignar', { method: 'POST', body: datos });
      }
      const mensajes = {
        crear: 'Cliente ' + guardado.codigo + ' creado.',
        editar: 'Cambios guardados.',
        reasignar: 'Código ' + guardado.codigo + ' reasignado a ' + guardado.nombres + '. ' + cliente.nombres + ' quedó retirado.',
      };
      estadoFormulario = null;
      irA('#cliente/' + guardado.id, mensajes[modo]);
    } catch (error) {
      Sesion.mostrarError(cajaErrorFormulario, error);
    } finally {
      boton.disabled = false;
      boton.textContent = textoBoton;
    }
  }

  // Validación rápida en el navegador. El servidor vuelve a validar todo.
  function validar(d) {
    const problemas = [];
    if (estadoFormulario.modo !== 'reasignar' && !FORMATO_CODIGO.test(d.codigo)) {
      problemas.push('El código debe tener el formato C-NN (ej: C-07).');
    }
    if (!d.nombres) problemas.push('El nombre es obligatorio.');
    if (d.celular && !FORMATO_CELULAR.test(d.celular)) {
      problemas.push('El celular debe tener 9 dígitos y empezar con 9 (ej: 987654321).');
    }
    if (!d.planId) problemas.push('Elige un plan.');
    if (!Number.isInteger(d.diaPago) || d.diaPago < 1 || d.diaPago > 28) {
      problemas.push('El día de pago debe estar entre 1 y 28.');
    }
    if (!d.fechaInicio) problemas.push('La fecha de inicio es obligatoria.');
    if (d.ip && !FORMATO_IPV4.test(d.ip)) problemas.push('La IP no es una IPv4 válida (ej: 192.168.1.20).');
    if (d.redId && !d.ip) problemas.push('Para asignarlo a una red, el cliente necesita una IP.');
    if (d.redId && d.nombreCola && !FORMATO_NOMBRE_COLA.test(d.nombreCola)) {
      problemas.push('El nombre de la cola solo puede tener letras, números, punto, guion y guion bajo (sin espacios).');
    }
    return problemas;
  }

  // =====================================================================
  // Utilidades
  // =====================================================================

  // Ventana de confirmación con <dialog>. Devuelve true si se pulsó el botón de confirmar.
  function confirmar({ titulo, texto, boton }) {
    const dialogo = $('dialogo');
    $('dialogo-titulo').textContent = titulo;
    $('dialogo-texto').textContent = texto;
    $('dialogo-si').textContent = boton;
    dialogo.returnValue = '';
    return new Promise((resolver) => {
      dialogo.addEventListener('close', () => resolver(dialogo.returnValue === 'si'), { once: true });
      dialogo.showModal();
    });
  }

  function crearSpan(clase, texto) {
    const span = document.createElement('span');
    span.className = clase;
    span.textContent = texto;
    return span;
  }

  function crearEstado(estado) {
    return crearSpan('estado estado-' + estado.toLowerCase(), ETIQUETA_ESTADO[estado]);
  }

  function crearDato(etiqueta, valor) {
    const fila = document.createElement('div');
    const dt = document.createElement('dt');
    dt.textContent = etiqueta;
    const dd = document.createElement('dd');
    dd.textContent = valor || '—';
    fila.append(dt, dd);
    return fila;
  }

  // "2026-07-01" -> "01/07/2026"
  function formatearFecha(iso) {
    if (!iso) return '';
    const [anio, mes, dia] = iso.split('-');
    return dia + '/' + mes + '/' + anio;
  }

  // Fecha de hoy en la hora local (toISOString usaría la hora UTC y podría dar mañana)
  function hoy() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function limpiarAvisos() {
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
  }
})();
