// Panel de una red (ADMIN): estado del MikroTik, clientes sin conexión, consumo del mes,
// diferencias entre la web y el router, y todas las colas. La red viene en la URL: red.html?id=N

(() => {
  const datos = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datos) return; // ya se está redirigiendo

  const URL_REDES = '/api/admin/redes';
  const $ = (id) => document.getElementById(id);
  const redId = Number(new URLSearchParams(location.search).get('id'));

  const ETIQUETA_ACCION = {
    PENDIENTE: 'pendiente', ENVIADA: 'enviada, sin confirmar', APLICADA: 'aplicada',
    ERROR: 'con error', REEMPLAZADA: 'reemplazada',
  };
  const ETIQUETA_MOTIVO = { CAMBIO: 'Cambio', CORTE: 'Corte', RECONEXION: 'Reconexión', SINCRONIZACION: 'Sincronización' };

  const cajaExito = $('exito');
  const cajaError = $('error');
  let panelActual = null;

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);
  $('boton-actualizar').addEventListener('click', cargar);
  $('boton-aplicar').addEventListener('click', aplicarDiferencias);
  $('boton-ver-colas').addEventListener('click', alternarColas);

  if (!Number.isInteger(redId) || redId <= 0) {
    $('cargando').hidden = true;
    Sesion.mostrarMensaje(cajaError, 'Falta la red. Entra desde “Redes”.');
    return;
  }
  $('enlace-terminal').href = '/admin/terminal.html?red=' + redId;
  cargar();

  // ---------- Panel ----------

  async function cargar() {
    try {
      const panel = await Sesion.api(URL_REDES + '/' + redId + '/panel');
      panelActual = panel;
      dibujar(panel);
      $('contenido-panel').hidden = false;
      if (!$('lista-colas').hidden) await cargarColas();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  function dibujar(p) {
    const red = p.red;
    document.title = 'Red ' + red.nombre;
    $('titulo').textContent = red.nombre;
    $('subtitulo').textContent = (red.modo === 'CONTROL' ? 'Modo Control' : 'Modo Solo lectura')
      + ' · Última conexión del MikroTik: '
      + (red.ultimaConexion ? Formato.fechaHora(red.ultimaConexion) + (red.ultimaIp ? ' desde ' + red.ultimaIp : '') : 'nunca')
      + (red.instalacionPendiente ? ' · Instalación pendiente' : '');

    const insignia = $('estado-mikrotik');
    insignia.hidden = false;
    insignia.className = 'estado ' + (p.mikrotikEnLinea ? 'estado-activo' : 'estado-vencido');
    insignia.textContent = p.mikrotikEnLinea ? 'MikroTik en línea' : 'MikroTik sin consultar';

    $('total-conectados').textContent = p.conectados + ' de ' + p.colas;
    $('detalle-conectados').textContent = p.sinConexion.length + ' sin conexión · ' + p.sinDatos + ' sin datos recientes';
    $('total-consumo').textContent = Formato.bytes(p.consumoMes.bytesBajada + p.consumoMes.bytesSubida);
    $('detalle-consumo').textContent = '↓ ' + Formato.bytes(p.consumoMes.bytesBajada) + ' · ↑ ' + Formato.bytes(p.consumoMes.bytesSubida)
      + ' · ' + Formato.nombreMes(p.consumoMes.periodo.slice(0, 7));
    $('total-acciones').textContent = p.accionesPendientes + ' pendientes';
    $('detalle-acciones').textContent = p.accionesConError + ' con error';

    $('boton-aplicar').hidden = red.modo !== 'CONTROL' || p.diferencias.length === 0;

    $('lista-sin-conexion').replaceChildren(...p.sinConexion.map((c) => fila(c.clienteId, c.codigo + ' · ' + c.nombres, 'Cola ' + c.cola)));
    $('vacio-sin-conexion').hidden = p.sinConexion.length > 0;

    $('lista-diferencias').replaceChildren(...p.diferencias.map(tarjetaDiferencia));
    $('vacio-diferencias').hidden = p.diferencias.length > 0;
  }

  // Fila con enlace al detalle del cliente
  function fila(clienteId, titulo, detalle) {
    const item = document.createElement('li');
    item.className = 'fila';
    const enlace = document.createElement('a');
    enlace.className = 'fila-texto';
    enlace.href = '/admin/clientes.html#cliente/' + encodeURIComponent(clienteId);
    const fuerte = document.createElement('strong');
    fuerte.textContent = titulo;
    const pequeno = document.createElement('small');
    pequeno.textContent = detalle;
    enlace.append(fuerte, pequeno);
    item.appendChild(enlace);
    return item;
  }

  function tarjetaDiferencia(d) {
    const item = document.createElement('li');
    item.className = 'tarjeta-plan';
    const titulo = document.createElement('h3');
    titulo.className = 'tarjeta-plan-nombre';
    titulo.textContent = d.cola + ' · ' + d.codigo + ' ' + d.nombres + (d.clienteVigente ? '' : ' (sin dueño vigente)');
    const lista = document.createElement('ul');
    lista.className = 'campo-ayuda';
    d.diferencias.forEach((texto) => {
      const li = document.createElement('li');
      li.textContent = texto;
      lista.appendChild(li);
    });
    item.append(titulo, lista);
    return item;
  }

  // ---------- Todas las colas ----------

  async function alternarColas() {
    const lista = $('lista-colas');
    const mostrar = lista.hidden;
    lista.hidden = !mostrar;
    $('boton-ver-colas').textContent = mostrar ? 'Ocultar' : 'Mostrar';
    $('boton-ver-colas').setAttribute('aria-expanded', String(mostrar));
    if (mostrar) await cargarColas();
  }

  async function cargarColas() {
    try {
      const colas = await Sesion.api(URL_REDES + '/' + redId + '/colas');
      $('lista-colas').replaceChildren(...colas.map(tarjetaCola));
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  function tarjetaCola(cola) {
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
      ['Velocidad actual', r && r.existe && r.rateBajadaBps !== null
        ? '↓ ' + Formato.velocidad(r.rateBajadaBps) + ' · ↑ ' + Formato.velocidad(r.rateSubidaBps) : '—'],
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
    return item;
  }

  // ---------- Aplicar diferencias ----------

  async function aplicarDiferencias() {
    if (!panelActual) return;
    const confirmado = await confirmar({
      titulo: '¿Aplicar diferencias en ' + panelActual.red.nombre + '?',
      texto: 'El MikroTik real dejará cada cola de cliente como dice la web (velocidad, target, cola padre, '
        + 'habilitada o deshabilitada) en su próxima consulta. La cola padre y las protegidas no se tocan.',
      boton: 'Sí, aplicar',
    });
    if (!confirmado) return;
    const boton = $('boton-aplicar');
    boton.disabled = true;
    try {
      const respuesta = await Sesion.api(URL_REDES + '/' + redId + '/aplicar-diferencias', { method: 'POST' });
      Sesion.mostrarMensaje(cajaExito, respuesta.accionesCreadas === 0
        ? 'No había diferencias por aplicar (o ya hay acciones pendientes).'
        : respuesta.accionesCreadas + ' acciones creadas. El MikroTik las aplicará en su próxima consulta.');
      await cargar();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      boton.disabled = false;
    }
  }

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
