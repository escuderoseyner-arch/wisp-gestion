// Redes (MikroTik) para ADMIN: listar, crear, editar y cambiar el token.
// El token solo se muestra una vez (al crear la red con token generado o al cambiarlo).

(() => {
  const datos = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datos) return; // ya se está redirigiendo

  const URL_REDES = '/api/admin/redes';
  const FORMATO_TOKEN = /^[A-Za-z0-9._~+/=-]{20,128}$/;
  const $ = (id) => document.getElementById(id);

  const cajaExito = $('exito');
  const cajaError = $('error');
  const form = $('form-red');
  const formToken = $('form-cambiar-token');

  // Red que se está editando (null = creando una nueva)
  let redEnEdicion = null;
  // Red a la que se le cambia el token
  let redCambioToken = null;
  // Red cuyas colas se están mostrando
  let redColas = null;

  const formatoFecha = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });

  Sesion.cargarEmpresa();
  Sesion.activarBotonesMostrar();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);
  $('boton-nuevo').addEventListener('click', () => abrirFormulario(null));
  $('boton-cancelar').addEventListener('click', cerrarFormulario);
  $('boton-cancelar-token').addEventListener('click', cerrarCambioToken);
  $('boton-copiar').addEventListener('click', copiarToken);
  $('boton-ocultar-token').addEventListener('click', ocultarToken);
  $('boton-aplicar').addEventListener('click', aplicarDiferencias);
  $('boton-recargar-colas').addEventListener('click', cargarColas);
  $('boton-cerrar-colas').addEventListener('click', cerrarColas);
  form.addEventListener('submit', guardar);
  formToken.addEventListener('submit', cambiarToken);
  form.querySelectorAll('input[name="origenToken"]').forEach((radio) => {
    radio.addEventListener('change', () => {
      $('campo-token-propio').hidden = origenToken() !== 'propio';
    });
  });

  cargar();

  // ---------- Listado ----------

  async function cargar() {
    try {
      dibujarLista(await Sesion.api(URL_REDES));
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  function dibujarLista(redes) {
    $('lista-redes').replaceChildren(...redes.map(crearTarjeta));
    $('vacio').hidden = redes.length > 0;
  }

  // createElement + textContent (nunca innerHTML)
  function crearTarjeta(red) {
    const item = document.createElement('li');
    item.className = 'tarjeta-plan';

    const cabecera = document.createElement('div');
    cabecera.className = 'tarjeta-plan-cabecera';
    const nombre = document.createElement('h2');
    nombre.className = 'tarjeta-plan-nombre';
    nombre.textContent = red.nombre;
    const modo = document.createElement('span');
    modo.className = 'estado ' + (red.modo === 'CONTROL' ? 'estado-activo' : 'estado-inactivo');
    modo.textContent = red.modo === 'CONTROL' ? 'Control' : 'Solo lectura';
    cabecera.append(nombre, modo);

    const lista = document.createElement('dl');
    lista.className = 'datos';
    const filas = [
      ['Cola padre', red.colaPadre],
      ['Colas protegidas', red.colasProtegidas.length ? red.colasProtegidas.join(', ') : 'Ninguna'],
      ['Consulta cada', red.intervaloSegundos + ' s'],
      ['Clientes', String(red.clientes)],
      ['Última conexión', red.ultimaConexion
        ? formatoFecha.format(new Date(red.ultimaConexion)) + (red.ultimaIp ? ' desde ' + red.ultimaIp : '')
        : 'Nunca se ha conectado'],
      ['Instalación', red.instalacionPendiente ? 'Pendiente (la descargará el script puente)' : 'Instalada'],
    ];
    filas.forEach(([etiqueta, valor]) => {
      const fila = document.createElement('div');
      const dt = document.createElement('dt');
      dt.textContent = etiqueta;
      const dd = document.createElement('dd');
      dd.textContent = valor;
      fila.append(dt, dd);
      lista.appendChild(fila);
    });

    const acciones = document.createElement('div');
    acciones.className = 'tarjeta-plan-acciones';

    const botonEditar = document.createElement('button');
    botonEditar.type = 'button';
    botonEditar.className = 'boton boton-secundario boton-compacto';
    botonEditar.textContent = 'Editar';
    Iconos.en(botonEditar, 'editar');
    botonEditar.setAttribute('aria-label', 'Editar la red ' + red.nombre);
    botonEditar.addEventListener('click', () => abrirFormulario(red));

    const botonToken = document.createElement('button');
    botonToken.type = 'button';
    botonToken.className = 'boton boton-secundario boton-compacto';
    botonToken.textContent = 'Cambiar token';
    Iconos.en(botonToken, 'candado');
    botonToken.setAttribute('aria-label', 'Cambiar el token de la red ' + red.nombre);
    botonToken.addEventListener('click', () => abrirCambioToken(red));

    const botonColas = document.createElement('button');
    botonColas.type = 'button';
    botonColas.className = 'boton boton-secundario boton-compacto';
    botonColas.textContent = 'Colas';
    Iconos.en(botonColas, 'ojo');
    botonColas.setAttribute('aria-label', 'Ver las colas de la red ' + red.nombre);
    botonColas.addEventListener('click', () => abrirColas(red));

    acciones.append(botonColas, botonEditar, botonToken);
    item.append(cabecera, lista, acciones);
    return item;
  }

  // ---------- Colas: web vs MikroTik ----------

  const ETIQUETA_ACCION = {
    PENDIENTE: 'pendiente', ENVIADA: 'enviada, sin confirmar', APLICADA: 'aplicada',
    ERROR: 'con error', REEMPLAZADA: 'reemplazada',
  };
  const ETIQUETA_MOTIVO = { CAMBIO: 'Cambio', CORTE: 'Corte', RECONEXION: 'Reconexión', SINCRONIZACION: 'Sincronización' };

  async function abrirColas(red) {
    limpiarAvisos();
    redColas = red;
    $('titulo-colas').textContent = 'Colas de ' + red.nombre;
    $('boton-aplicar').hidden = red.modo !== 'CONTROL';
    $('panel-colas').hidden = false;
    $('panel-colas').scrollIntoView({ block: 'start', behavior: 'smooth' });
    await cargarColas();
  }

  function cerrarColas() {
    redColas = null;
    $('panel-colas').hidden = true;
    $('lista-colas').replaceChildren();
  }

  async function cargarColas() {
    if (!redColas) return;
    const cajaErrorColas = $('error-colas');
    Sesion.ocultar(cajaErrorColas);
    try {
      const colas = await Sesion.api(URL_REDES + '/' + redColas.id + '/colas');
      const conDiferencias = colas.filter((c) => c.diferencias.length > 0).length;
      const sinReporte = colas.filter((c) => !c.reportado).length;
      $('colas-resumen').textContent = colas.length + ' colas gestionadas · '
        + conDiferencias + ' con diferencias · ' + sinReporte + ' sin reporte del MikroTik'
        + (redColas.modo === 'CONTROL' ? '' : ' · Solo lectura: la web no envía cambios.');
      $('lista-colas').replaceChildren(...colas.map(crearTarjetaCola));
    } catch (error) {
      Sesion.mostrarError(cajaErrorColas, error);
    }
  }

  function crearTarjetaCola(cola) {
    const item = document.createElement('li');
    item.className = 'tarjeta-plan';

    const cabecera = document.createElement('div');
    cabecera.className = 'tarjeta-plan-cabecera';
    const nombre = document.createElement('h3');
    nombre.className = 'tarjeta-plan-nombre';
    nombre.textContent = cola.nombre + ' · ' + cola.cliente.codigo + ' ' + cola.cliente.nombres;
    const estado = document.createElement('span');
    const iguales = cola.reportado && cola.diferencias.length === 0;
    estado.className = 'estado ' + (!cola.reportado ? 'estado-inactivo' : iguales ? 'estado-activo' : 'estado-vencido');
    estado.textContent = !cola.reportado ? 'Sin reporte' : iguales ? 'Coincide' : 'Diferente';
    cabecera.append(nombre, estado);

    const d = cola.deseado;
    const r = cola.reportado;
    const filas = [
      ['Web', d.target + ' · ' + d.maxLimit + ' · ' + (d.deshabilitada ? 'deshabilitada' : 'habilitada')
        + (cola.cliente.vigente ? '' : ' (sin dueño vigente)')],
      ['MikroTik', !r ? '—' : !r.existe ? 'No existe' : (r.target || '—') + ' · ' + (r.maxLimit || '—') + ' · '
        + (r.deshabilitada ? 'deshabilitada' : 'habilitada')],
      ['Ping', !r || r.pingOk === null ? '—' : r.pingOk ? 'Responde' : 'No responde'],
      ['Última acción', cola.ultimaAccion
        ? ETIQUETA_MOTIVO[cola.ultimaAccion.motivo] + ': ' + ETIQUETA_ACCION[cola.ultimaAccion.estado]
          + (cola.ultimaAccion.error ? ' — ' + cola.ultimaAccion.error : '')
        : 'Ninguna'],
    ];
    const lista = document.createElement('dl');
    lista.className = 'datos';
    filas.forEach(([etiqueta, valor]) => {
      const fila = document.createElement('div');
      const dt = document.createElement('dt');
      dt.textContent = etiqueta;
      const dd = document.createElement('dd');
      dd.textContent = valor;
      fila.append(dt, dd);
      lista.appendChild(fila);
    });
    item.append(cabecera, lista);

    if (cola.diferencias.length > 0) {
      const diferencias = document.createElement('ul');
      diferencias.className = 'campo-ayuda';
      cola.diferencias.forEach((texto) => {
        const li = document.createElement('li');
        li.textContent = texto;
        diferencias.appendChild(li);
      });
      item.appendChild(diferencias);
    }
    return item;
  }

  async function aplicarDiferencias() {
    const red = redColas;
    if (!red) return;
    const confirmado = await confirmar({
      titulo: '¿Aplicar diferencias en ' + red.nombre + '?',
      texto: 'El MikroTik real dejará cada cola de cliente como dice la web (velocidad, target, cola padre, '
        + 'habilitada o deshabilitada). Se aplica en su próxima consulta. La cola padre y las protegidas no se tocan.',
      boton: 'Sí, aplicar',
    });
    if (!confirmado) return;
    const boton = $('boton-aplicar');
    boton.disabled = true;
    try {
      const respuesta = await Sesion.api(URL_REDES + '/' + red.id + '/aplicar-diferencias', { method: 'POST' });
      Sesion.mostrarMensaje(cajaExito, respuesta.accionesCreadas === 0
        ? 'No había diferencias por aplicar (o ya hay acciones pendientes).'
        : respuesta.accionesCreadas + ' acciones creadas. El MikroTik las aplicará en su próxima consulta.');
      await cargarColas();
    } catch (error) {
      Sesion.mostrarError($('error-colas'), error);
    } finally {
      boton.disabled = false;
    }
  }

  // ---------- Crear / editar ----------

  function abrirFormulario(red) {
    limpiarAvisos();
    cerrarCambioToken();
    Sesion.ocultar($('error-formulario'));
    redEnEdicion = red;
    $('titulo-formulario').textContent = red ? 'Editar red' : 'Nueva red';
    form.nombre.value = red ? red.nombre : '';
    form.colaPadre.value = red ? red.colaPadre : '';
    form.colasProtegidas.value = red ? red.colasProtegidas.join(', ') : '';
    form.intervaloSegundos.value = red ? red.intervaloSegundos : 60;
    form.modo.value = red ? red.modo : 'SOLO_LECTURA';
    form.token.value = '';
    form.querySelector('input[name="origenToken"][value="generar"]').checked = true;
    $('campo-token-propio').hidden = true;
    $('grupo-token').hidden = red !== null; // el token se cambia aparte

    $('panel-formulario').hidden = false;
    $('boton-nuevo').hidden = true;
    $('panel-formulario').scrollIntoView({ block: 'start', behavior: 'smooth' });
    form.nombre.focus({ preventScroll: true });
  }

  function cerrarFormulario() {
    redEnEdicion = null;
    form.reset();
    Sesion.ocultar($('error-formulario'));
    $('panel-formulario').hidden = true;
    $('boton-nuevo').hidden = false;
  }

  function origenToken() {
    return form.querySelector('input[name="origenToken"]:checked').value;
  }

  async function guardar(evento) {
    evento.preventDefault();
    limpiarAvisos();
    const cajaErrorFormulario = $('error-formulario');
    Sesion.ocultar(cajaErrorFormulario);

    const editando = redEnEdicion !== null;
    const red = {
      nombre: form.nombre.value.trim(),
      colaPadre: form.colaPadre.value.trim(),
      colasProtegidas: form.colasProtegidas.value.trim(),
      intervaloSegundos: Number(form.intervaloSegundos.value),
      modo: form.modo.value,
    };
    if (!editando && origenToken() === 'propio') {
      red.token = form.token.value.trim();
    }

    // Validación rápida en el navegador. El servidor vuelve a validar todo.
    const problemas = [];
    if (!red.nombre) problemas.push('El nombre de la red es obligatorio.');
    if (!red.colaPadre) problemas.push('La cola padre es obligatoria.');
    if (!Number.isInteger(red.intervaloSegundos) || red.intervaloSegundos < 30 || red.intervaloSegundos > 3600) {
      problemas.push('El intervalo de consulta debe estar entre 30 y 3600 segundos.');
    }
    if (red.token !== undefined && !FORMATO_TOKEN.test(red.token)) {
      problemas.push('La clave debe tener entre 20 y 128 caracteres: letras, números y . _ ~ + / = -');
    }
    if (problemas.length > 0) {
      Sesion.mostrarMensaje(cajaErrorFormulario, 'Revisa los datos:', problemas);
      return;
    }

    // Pasar a CONTROL hace que la web empiece a cambiar colas en el router real
    const pasaAControl = red.modo === 'CONTROL' && (!editando || redEnEdicion.modo !== 'CONTROL');
    if (pasaAControl) {
      const confirmado = await confirmar({
        titulo: '¿Activar el modo Control?',
        texto: 'La web enviará cambios a las colas de los clientes en el MikroTik real '
          + '(velocidades, cortes y reconexiones). La cola padre y las protegidas nunca se tocan.',
        boton: 'Sí, activar Control',
      });
      if (!confirmado) return;
    }

    const boton = $('boton-guardar');
    boton.disabled = true;
    boton.textContent = 'Guardando…';
    try {
      const respuesta = await Sesion.api(editando ? URL_REDES + '/' + redEnEdicion.id : URL_REDES, {
        method: editando ? 'PUT' : 'POST',
        body: red,
      });
      cerrarFormulario();
      if (editando) {
        Sesion.mostrarMensaje(cajaExito, 'Red "' + respuesta.nombre + '" actualizada.');
      } else {
        Sesion.mostrarMensaje(cajaExito, 'Red "' + respuesta.red.nombre + '" creada.'
          + (respuesta.token ? '' : ' Se usará la clave que escribiste.'));
        if (respuesta.token) mostrarToken(respuesta.red, respuesta.token);
      }
      await recargar();
    } catch (error) {
      Sesion.mostrarError(cajaErrorFormulario, error);
    } finally {
      boton.disabled = false;
      boton.textContent = 'Guardar red';
    }
  }

  // ---------- Cambiar token ----------

  function abrirCambioToken(red) {
    limpiarAvisos();
    cerrarFormulario();
    redCambioToken = red;
    formToken.reset();
    Sesion.ocultar($('error-cambiar-token'));
    $('titulo-cambiar-token').textContent = 'Cambiar token de ' + red.nombre;
    $('panel-cambiar-token').hidden = false;
    $('panel-cambiar-token').scrollIntoView({ block: 'start', behavior: 'smooth' });
  }

  function cerrarCambioToken() {
    redCambioToken = null;
    formToken.reset();
    $('panel-cambiar-token').hidden = true;
  }

  async function cambiarToken(evento) {
    evento.preventDefault();
    const red = redCambioToken;
    if (!red) return;
    const cajaErrorToken = $('error-cambiar-token');
    Sesion.ocultar(cajaErrorToken);

    const token = formToken.token.value.trim();
    if (token && !FORMATO_TOKEN.test(token)) {
      Sesion.mostrarMensaje(cajaErrorToken, 'La clave debe tener entre 20 y 128 caracteres: letras, números y . _ ~ + / = -');
      return;
    }

    const confirmado = await confirmar({
      titulo: '¿Cambiar el token de ' + red.nombre + '?',
      texto: 'El token actual deja de funcionar YA. El script puente y la sincronización del MikroTik '
        + 'no podrán conectarse hasta que pongas la clave nueva en el router. '
        + 'Si solo entras al router por el puente, perderás el acceso remoto.',
      boton: 'Sí, cambiar token',
    });
    if (!confirmado) return;

    const boton = $('boton-cambiar-token');
    boton.disabled = true;
    try {
      const respuesta = await Sesion.api(URL_REDES + '/' + red.id + '/token', {
        method: 'POST',
        body: token ? { token } : {},
      });
      cerrarCambioToken();
      Sesion.mostrarMensaje(cajaExito, 'Token de "' + respuesta.red.nombre + '" cambiado.'
        + (respuesta.token ? '' : ' Se usará la clave que escribiste.'));
      if (respuesta.token) mostrarToken(respuesta.red, respuesta.token);
    } catch (error) {
      Sesion.mostrarError(cajaErrorToken, error);
    } finally {
      boton.disabled = false;
    }
  }

  // ---------- Token (se muestra una sola vez) ----------

  function mostrarToken(red, token) {
    $('token-red').textContent = red.nombre;
    $('token-valor').textContent = token;
    $('panel-token').hidden = false;
    $('panel-token').scrollIntoView({ block: 'start', behavior: 'smooth' });
  }

  function ocultarToken() {
    $('panel-token').hidden = true;
    $('token-red').textContent = '';
    $('token-valor').textContent = '';
  }

  async function copiarToken() {
    try {
      await navigator.clipboard.writeText($('token-valor').textContent);
      Sesion.mostrarMensaje(cajaExito, 'Token copiado.');
    } catch {
      Sesion.mostrarMensaje(cajaExito, 'No se pudo copiar automáticamente: cópialo a mano.');
    }
  }

  // ---------- Utilidades ----------

  async function recargar() {
    try {
      dibujarLista(await Sesion.api(URL_REDES));
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  function limpiarAvisos() {
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
  }

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
})();
