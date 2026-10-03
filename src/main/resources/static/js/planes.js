// CRUD de planes para ADMIN: listar, crear, editar y activar/desactivar.

(() => {
  const datos = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datos) return; // ya se está redirigiendo

  const URL_PLANES = '/api/admin/planes';

  const lista = document.getElementById('lista-planes');
  const textoCargando = document.getElementById('cargando');
  const textoVacio = document.getElementById('vacio');
  const cajaExito = document.getElementById('exito');
  const cajaError = document.getElementById('error');

  const panelFormulario = document.getElementById('panel-formulario');
  const tituloFormulario = document.getElementById('titulo-formulario');
  const cajaErrorFormulario = document.getElementById('error-formulario');
  const form = document.getElementById('form-plan');
  const botonGuardar = document.getElementById('boton-guardar');
  const botonNuevo = document.getElementById('boton-nuevo');

  // id del plan que se está editando, o null si se está creando uno nuevo
  let idEnEdicion = null;

  const formatoPrecio = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  Sesion.cargarEmpresa();
  document.getElementById('cerrar-sesion').addEventListener('click', Sesion.cerrar);
  botonNuevo.addEventListener('click', () => abrirFormulario(null));
  document.getElementById('boton-cancelar').addEventListener('click', cerrarFormulario);
  form.addEventListener('submit', guardar);

  cargarPlanes();

  // ---------- Listado ----------

  async function cargarPlanes() {
    try {
      const planes = await Sesion.api(URL_PLANES);
      dibujarLista(planes);
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      textoCargando.hidden = true;
    }
  }

  function dibujarLista(planes) {
    lista.replaceChildren(...planes.map(crearTarjeta));
    textoVacio.hidden = planes.length > 0;
  }

  // Cada tarjeta se arma con createElement + textContent (nunca innerHTML)
  function crearTarjeta(plan) {
    const item = document.createElement('li');
    item.className = 'tarjeta-plan' + (plan.activo ? '' : ' inactivo');

    const cabecera = document.createElement('div');
    cabecera.className = 'tarjeta-plan-cabecera';
    const nombre = document.createElement('h2');
    nombre.className = 'tarjeta-plan-nombre';
    nombre.textContent = plan.nombre;
    const estado = document.createElement('span');
    estado.className = 'estado ' + (plan.activo ? 'estado-activo' : 'estado-inactivo');
    estado.textContent = plan.activo ? 'Activo' : 'Inactivo';
    cabecera.append(nombre, estado);

    const precio = document.createElement('p');
    precio.className = 'tarjeta-plan-precio';
    precio.textContent = formatoPrecio.format(plan.precio);
    const porMes = document.createElement('span');
    porMes.textContent = ' / mes';
    precio.appendChild(porMes);

    const velocidad = document.createElement('p');
    velocidad.className = 'tarjeta-plan-velocidad';
    velocidad.textContent = '↓ ' + plan.bajadaMbps + ' Mbps · ↑ ' + plan.subidaMbps + ' Mbps';

    item.append(cabecera, precio, velocidad);

    if (!plan.activo) {
      const nota = document.createElement('p');
      nota.className = 'campo-ayuda';
      nota.textContent = 'No se puede elegir para clientes nuevos. Los clientes que ya lo tienen siguen igual.';
      item.appendChild(nota);
    }

    const acciones = document.createElement('div');
    acciones.className = 'tarjeta-plan-acciones';

    const botonEditar = document.createElement('button');
    botonEditar.type = 'button';
    botonEditar.className = 'boton boton-secundario boton-compacto';
    botonEditar.textContent = 'Editar';
    Iconos.en(botonEditar, 'editar');
    botonEditar.setAttribute('aria-label', 'Editar el plan ' + plan.nombre);
    botonEditar.addEventListener('click', () => abrirFormulario(plan));

    const botonEstado = document.createElement('button');
    botonEstado.type = 'button';
    botonEstado.className = 'boton boton-secundario boton-compacto';
    botonEstado.textContent = plan.activo ? 'Desactivar' : 'Activar';
    Iconos.en(botonEstado, plan.activo ? 'pausa' : 'play');
    botonEstado.setAttribute('aria-label', (plan.activo ? 'Desactivar' : 'Activar') + ' el plan ' + plan.nombre);
    botonEstado.addEventListener('click', () => cambiarActivo(plan, botonEstado));

    acciones.append(botonEditar, botonEstado);
    item.appendChild(acciones);
    return item;
  }

  // ---------- Activar / desactivar ----------

  async function cambiarActivo(plan, boton) {
    limpiarAvisos();
    boton.disabled = true;
    try {
      const actualizado = await Sesion.api(URL_PLANES + '/' + plan.id + '/activo', {
        method: 'PATCH',
        body: { activo: !plan.activo },
      });
      Sesion.mostrarMensaje(cajaExito, actualizado.activo
        ? 'Plan "' + actualizado.nombre + '" activado.'
        : 'Plan "' + actualizado.nombre + '" desactivado. Los clientes que ya lo tienen no cambian.');
      await recargar();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
      boton.disabled = false;
    }
  }

  // ---------- Formulario ----------

  // plan = null para crear uno nuevo
  function abrirFormulario(plan) {
    limpiarAvisos();
    Sesion.ocultar(cajaErrorFormulario);
    idEnEdicion = plan ? plan.id : null;
    tituloFormulario.textContent = plan ? 'Editar plan' : 'Nuevo plan';
    form.nombre.value = plan ? plan.nombre : '';
    form.bajadaMbps.value = plan ? plan.bajadaMbps : '';
    form.subidaMbps.value = plan ? plan.subidaMbps : '';
    form.precio.value = plan ? Number(plan.precio).toFixed(2) : '';

    panelFormulario.hidden = false;
    botonNuevo.hidden = true;
    panelFormulario.scrollIntoView({ block: 'start', behavior: 'smooth' });
    form.nombre.focus({ preventScroll: true });
  }

  function cerrarFormulario() {
    idEnEdicion = null;
    form.reset();
    Sesion.ocultar(cajaErrorFormulario);
    panelFormulario.hidden = true;
    botonNuevo.hidden = false;
  }

  async function guardar(evento) {
    evento.preventDefault();
    limpiarAvisos();
    Sesion.ocultar(cajaErrorFormulario);

    const plan = {
      nombre: form.nombre.value.trim(),
      bajadaMbps: Number(form.bajadaMbps.value),
      subidaMbps: Number(form.subidaMbps.value),
      precio: Number(form.precio.value),
    };

    // Validación rápida en el navegador. El servidor vuelve a validar todo.
    const problemas = [];
    if (!plan.nombre) problemas.push('El nombre del plan es obligatorio.');
    if (!form.bajadaMbps.value || !Number.isInteger(plan.bajadaMbps) || plan.bajadaMbps <= 0) {
      problemas.push('La velocidad de bajada debe ser un número entero mayor que 0.');
    }
    if (!form.subidaMbps.value || !Number.isInteger(plan.subidaMbps) || plan.subidaMbps <= 0) {
      problemas.push('La velocidad de subida debe ser un número entero mayor que 0.');
    }
    if (!form.precio.value || !(plan.precio > 0)) problemas.push('El precio debe ser mayor que 0.');
    if (problemas.length > 0) {
      Sesion.mostrarMensaje(cajaErrorFormulario, 'Revisa los datos:', problemas);
      return;
    }

    const editando = idEnEdicion !== null;
    botonGuardar.disabled = true;
    botonGuardar.textContent = 'Guardando…';
    try {
      const guardado = await Sesion.api(editando ? URL_PLANES + '/' + idEnEdicion : URL_PLANES, {
        method: editando ? 'PUT' : 'POST',
        body: plan,
      });
      cerrarFormulario();
      Sesion.mostrarMensaje(cajaExito, 'Plan "' + guardado.nombre + '" ' + (editando ? 'actualizado.' : 'creado.'));
      await recargar();
    } catch (error) {
      Sesion.mostrarError(cajaErrorFormulario, error);
    } finally {
      botonGuardar.disabled = false;
      botonGuardar.textContent = 'Guardar plan';
    }
  }

  // ---------- Utilidades ----------

  async function recargar() {
    try {
      dibujarLista(await Sesion.api(URL_PLANES));
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    }
  }

  function limpiarAvisos() {
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
  }
})();
