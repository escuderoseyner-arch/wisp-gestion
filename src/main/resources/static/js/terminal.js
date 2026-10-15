// Terminal remota del MikroTik (ADMIN): encolar comandos y ver su historial con la salida.
// La red se elige en la lista; si la URL trae ?red=ID, se preselecciona esa.

(() => {
  const datos = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datos) return; // ya se está redirigiendo

  const URL_REDES = '/api/admin/redes';
  const INTERVALO_REVISION_MS = 5000;
  const PASO_HISTORIAL = 10;
  const $ = (id) => document.getElementById(id);

  const ETIQUETA_ESTADO = {
    PENDIENTE: 'Esperando al MikroTik',
    ENVIADO: 'Ejecutándose',
    EJECUTADO: 'Ejecutado',
    ERROR: 'Error',
    EXPIRADO: 'Expirado (no se ejecutó)',
    CANCELADO: 'Cancelado',
  };
  const CLASE_ESTADO = {
    PENDIENTE: 'estado-pendiente',
    ENVIADO: 'estado-pendiente',
    EJECUTADO: 'estado-activo',
    ERROR: 'estado-vencido',
    EXPIRADO: 'estado-inactivo',
    CANCELADO: 'estado-inactivo',
  };

  const cajaExito = $('exito');
  const cajaError = $('error');
  const form = $('form-comando');
  const formatoFecha = new Intl.DateTimeFormat(undefined, { dateStyle: 'short', timeStyle: 'medium' });

  let redes = [];
  let temporizador = null;
  let visibles = PASO_HISTORIAL;   // cuántos comandos se muestran ("Ver más" suma 10)
  const abiertos = new Set();      // ids desplegados (se mantienen al refrescar)

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);
  form.addEventListener('submit', enviar);
  form.red.addEventListener('change', () => {
    actualizarAvisoModo();
    visibles = PASO_HISTORIAL;
    abiertos.clear();
    cargarHistorial();
  });
  $('ver-mas').addEventListener('click', () => {
    visibles += PASO_HISTORIAL;
    cargarHistorial();
  });
  $('boton-limpiar').addEventListener('click', limpiarHistorial);

  iniciar();

  async function iniciar() {
    try {
      redes = await Sesion.api(URL_REDES);
      if (redes.length === 0) {
        Sesion.mostrarMensaje(cajaError, 'Primero crea una red en “Redes”.');
        $('cargando').hidden = true;
        return;
      }
      form.red.replaceChildren(...redes.map((r) => new Option(r.nombre, r.id)));
      const pedida = new URLSearchParams(location.search).get('red');
      if (pedida && redes.some((r) => String(r.id) === pedida)) form.red.value = pedida;
      actualizarAvisoModo();
      await cargarHistorial();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  function redActual() {
    return redes.find((r) => String(r.id) === form.red.value);
  }

  function actualizarAvisoModo() {
    const red = redActual();
    const soloLectura = !red || red.modo !== 'CONTROL';
    $('aviso-modo').hidden = !soloLectura;
    $('boton-enviar').disabled = soloLectura;
  }

  // ---------- Historial ----------

  async function cargarHistorial() {
    clearTimeout(temporizador);
    const red = redActual();
    if (!red) return;
    try {
      // Se pide uno más de los que se muestran para saber si hay "Ver más"
      const recibidos = await Sesion.api(URL_REDES + '/' + red.id + '/comandos?limite=' + (visibles + 1));
      if (redActual() !== red) return; // se cambió de red mientras cargaba
      const comandos = recibidos.slice(0, visibles);
      $('historial').replaceChildren(...comandos.map(crearFila));
      $('vacio').hidden = comandos.length > 0;
      $('contenedor-ver-mas').hidden = recibidos.length <= visibles;
      $('boton-limpiar').hidden = comandos.length === 0;
      // Mientras haya comandos sin terminar, se revisa cada 5 segundos
      if (comandos.some((c) => c.estado === 'PENDIENTE' || c.estado === 'ENVIADO')) {
        temporizador = setTimeout(cargarHistorial, INTERVALO_REVISION_MS);
      }
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  // createElement + textContent (nunca innerHTML): la salida del router se muestra tal cual, sin interpretarse
  // Cada comando es una fila compacta: al tocarla se despliega su salida y al tocarla de nuevo se cierra.
  function crearFila(c) {
    const item = document.createElement('li');
    item.className = 'fila-comando';

    const resumen = document.createElement('button');
    resumen.type = 'button';
    resumen.className = 'fila-comando-resumen';
    resumen.setAttribute('aria-controls', 'detalle-' + c.id);

    const meta = document.createElement('span');
    meta.className = 'fila-comando-meta';
    meta.textContent = (c.creadoEn ? formatoFecha.format(new Date(c.creadoEn)) : 'ahora') + ' · ' + c.enviadoPor;
    const estado = document.createElement('span');
    estado.className = 'estado ' + CLASE_ESTADO[c.estado];
    estado.textContent = ETIQUETA_ESTADO[c.estado];
    const texto = document.createElement('span');
    texto.className = 'fila-comando-texto';
    texto.textContent = c.comando.replace(/\s+/g, ' ');
    resumen.append(meta, texto, estado);

    const detalle = document.createElement('div');
    detalle.className = 'fila-comando-detalle';
    detalle.id = 'detalle-' + c.id;
    llenarDetalle(detalle, c);

    const ponerAbierta = (abierta) => {
      resumen.setAttribute('aria-expanded', String(abierta));
      detalle.hidden = !abierta;
      item.classList.toggle('abierta', abierta);
    };
    ponerAbierta(abiertos.has(c.id));
    resumen.addEventListener('click', () => {
      const abrir = !abiertos.has(c.id);
      if (abrir) abiertos.add(c.id); else abiertos.delete(c.id);
      ponerAbierta(abrir);
    });

    item.append(resumen, detalle);
    return item;
  }

  function llenarDetalle(detalle, c) {
    const comando = document.createElement('pre');
    comando.className = 'salida-terminal comando-terminal';
    comando.textContent = c.comando;
    detalle.appendChild(comando);

    const tieneSalida = c.salida !== null && c.salida !== undefined;
    if (tieneSalida) {
      const salida = document.createElement('pre');
      salida.className = 'salida-terminal';
      salida.textContent = c.salida === '' ? '(sin salida)' : c.salida;
      detalle.appendChild(salida);
      if (c.salidaTruncada) {
        detalle.appendChild(nota('La salida era más larga: se guardaron solo los primeros 16 KB.'));
      }
    } else {
      detalle.appendChild(nota(c.estado === 'PENDIENTE' || c.estado === 'ENVIADO'
        ? 'Aún no hay salida: esperando al MikroTik.' : 'Este comando no tiene salida.'));
    }

    const acciones = document.createElement('div');
    acciones.className = 'fila-comando-acciones';
    if (tieneSalida && c.salida !== '') {
      const copiar = botonCompacto('Copiar salida');
      copiar.addEventListener('click', () => copiarSalida(c.salida, copiar));
      acciones.appendChild(copiar);
    }
    if (c.estado === 'PENDIENTE') {
      const cancelar = botonCompacto('Cancelar');
      cancelar.addEventListener('click', () => cancelarComando(c, cancelar));
      acciones.appendChild(cancelar);
    }
    if (acciones.childElementCount > 0) detalle.appendChild(acciones);
  }

  function nota(texto) {
    const p = document.createElement('p');
    p.className = 'campo-ayuda';
    p.textContent = texto;
    return p;
  }

  function botonCompacto(texto) {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'boton boton-secundario boton-compacto';
    boton.textContent = texto;
    return boton;
  }

  async function copiarSalida(texto, boton) {
    try {
      await navigator.clipboard.writeText(texto);
      boton.textContent = 'Copiado';
    } catch {
      boton.textContent = 'No se pudo copiar';
    }
    setTimeout(() => { boton.textContent = 'Copiar salida'; }, 2000);
  }

  // Borra el historial de la red elegida (los comandos que aún esperan al MikroTik se conservan)
  async function limpiarHistorial() {
    limpiarAvisos();
    const red = redActual();
    if (!red) return;
    const confirmado = await confirmar({
      titulo: '¿Limpiar el historial de ' + red.nombre + '?',
      texto: 'Se borrarán los comandos ya terminados de esta red y sus salidas. No se puede deshacer. '
        + 'Los que aún esperan al MikroTik se conservan.',
      boton: 'Sí, limpiar historial',
    });
    if (!confirmado) return;

    const boton = $('boton-limpiar');
    boton.disabled = true;
    try {
      const respuesta = await Sesion.api(URL_REDES + '/' + red.id + '/comandos', { method: 'DELETE' });
      Sesion.mostrarMensaje(cajaExito, 'Historial limpiado: ' + respuesta.borrados + ' comando(s) borrado(s).');
      visibles = PASO_HISTORIAL;
      abiertos.clear();
      await cargarHistorial();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      boton.disabled = false;
    }
  }

  // ---------- Enviar ----------

  async function enviar(evento) {
    evento.preventDefault();
    limpiarAvisos();
    const red = redActual();
    const comando = form.comando.value.trim();
    if (!red) return;
    if (!comando) {
      Sesion.mostrarMensaje(cajaError, 'Escribe un comando.');
      return;
    }
    if (/fasttrack/i.test(comando)) {
      Sesion.mostrarMensaje(cajaError, 'Por seguridad, la terminal no acepta comandos que mencionen fasttrack.');
      return;
    }

    const confirmado = await confirmar({
      titulo: '¿Ejecutar en el MikroTik de ' + red.nombre + '?',
      texto: 'Este comando se ejecutará en el router REAL en su próxima consulta y no se puede deshacer desde aquí:',
      comando,
      boton: 'Sí, ejecutar en el router',
    });
    if (!confirmado) return;

    const boton = $('boton-enviar');
    boton.disabled = true;
    try {
      await Sesion.api(URL_REDES + '/' + red.id + '/comandos', {
        method: 'POST',
        body: { comando, confirmo: true },
      });
      form.comando.value = '';
      Sesion.mostrarMensaje(cajaExito, 'Comando encolado. Se ejecutará cuando el MikroTik consulte (cada '
        + red.intervaloSegundos + ' s aprox.).');
      await cargarHistorial();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      actualizarAvisoModo();
    }
  }

  async function cancelarComando(c, boton) {
    limpiarAvisos();
    boton.disabled = true;
    try {
      await Sesion.api('/api/admin/comandos/' + c.id + '/cancelar', { method: 'POST' });
      Sesion.mostrarMensaje(cajaExito, 'Comando cancelado.');
      await cargarHistorial();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
      boton.disabled = false;
    }
  }

  // ---------- Utilidades ----------

  function limpiarAvisos() {
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
  }

  function confirmar({ titulo, texto, comando, boton }) {
    const dialogo = $('dialogo');
    $('dialogo-titulo').textContent = titulo;
    $('dialogo-texto').textContent = texto;
    $('dialogo-comando').textContent = comando || '';
    $('dialogo-comando').hidden = !comando;
    $('dialogo-si').textContent = boton;
    dialogo.returnValue = '';
    return new Promise((resolver) => {
      dialogo.addEventListener('close', () => resolver(dialogo.returnValue === 'si'), { once: true });
      dialogo.showModal();
    });
  }
})();
