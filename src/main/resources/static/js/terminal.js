// Terminal remota del MikroTik (ADMIN): encolar comandos y ver su historial con la salida.
// La red se elige en la lista; si la URL trae ?red=ID, se preselecciona esa.

(() => {
  const datos = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datos) return; // ya se está redirigiendo

  const URL_REDES = '/api/admin/redes';
  const INTERVALO_REVISION_MS = 5000;
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

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);
  form.addEventListener('submit', enviar);
  form.red.addEventListener('change', () => {
    actualizarAvisoModo();
    cargarHistorial();
  });

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
      const comandos = await Sesion.api(URL_REDES + '/' + red.id + '/comandos');
      if (redActual() !== red) return; // se cambió de red mientras cargaba
      $('historial').replaceChildren(...comandos.map(crearTarjeta));
      $('vacio').hidden = comandos.length > 0;
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
  function crearTarjeta(c) {
    const item = document.createElement('li');
    item.className = 'tarjeta-plan';

    const cabecera = document.createElement('div');
    cabecera.className = 'tarjeta-plan-cabecera';
    const titulo = document.createElement('p');
    titulo.className = 'campo-ayuda';
    titulo.textContent = c.enviadoPor + ' · ' + (c.creadoEn ? formatoFecha.format(new Date(c.creadoEn)) : 'ahora');
    const estado = document.createElement('span');
    estado.className = 'estado ' + CLASE_ESTADO[c.estado];
    estado.textContent = ETIQUETA_ESTADO[c.estado];
    cabecera.append(titulo, estado);

    const comando = document.createElement('pre');
    comando.className = 'salida-terminal comando-terminal';
    comando.textContent = c.comando;
    item.append(cabecera, comando);

    if (c.salida !== null && c.salida !== undefined) {
      const salida = document.createElement('pre');
      salida.className = 'salida-terminal';
      salida.textContent = c.salida === '' ? '(sin salida)' : c.salida;
      item.appendChild(salida);
      if (c.salidaTruncada) {
        const nota = document.createElement('p');
        nota.className = 'campo-ayuda';
        nota.textContent = 'La salida era más larga: se guardaron solo los primeros 16 KB.';
        item.appendChild(nota);
      }
    }

    if (c.estado === 'PENDIENTE') {
      const acciones = document.createElement('div');
      acciones.className = 'tarjeta-plan-acciones';
      const cancelar = document.createElement('button');
      cancelar.type = 'button';
      cancelar.className = 'boton boton-secundario boton-compacto';
      cancelar.textContent = 'Cancelar';
      cancelar.addEventListener('click', () => cancelarComando(c, cancelar));
      acciones.appendChild(cancelar);
      item.appendChild(acciones);
    }
    return item;
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
    $('dialogo-comando').textContent = comando;
    $('dialogo-si').textContent = boton;
    dialogo.returnValue = '';
    return new Promise((resolver) => {
      dialogo.addEventListener('close', () => resolver(dialogo.returnValue === 'si'), { once: true });
      dialogo.showModal();
    });
  }
})();
