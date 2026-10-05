// Administradores: listar, crear, desactivar/reactivar y restablecer contraseña.

(() => {
  const datosSesion = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datosSesion) return; // ya se está redirigiendo

  const URL_ADMINS = '/api/admin/administradores';
  const $ = (id) => document.getElementById(id);
  const cajaExito = $('exito');
  const cajaError = $('error');
  const form = $('form-admin');

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);
  $('boton-nuevo').addEventListener('click', abrirFormulario);
  $('boton-cancelar').addEventListener('click', cerrarFormulario);
  $('boton-copiar').addEventListener('click', copiar);
  $('boton-ocultar-password').addEventListener('click', ocultarPassword);
  form.addEventListener('submit', crear);

  cargar();

  async function cargar() {
    try {
      const admins = await Sesion.api(URL_ADMINS);
      $('lista-admins').replaceChildren(...admins.map(crearTarjeta));
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  // Siempre textContent, nunca innerHTML
  function crearTarjeta(admin) {
    const item = document.createElement('li');
    item.className = 'tarjeta-plan' + (admin.activo ? '' : ' inactivo');

    const cabecera = document.createElement('div');
    cabecera.className = 'tarjeta-plan-cabecera';
    const nombre = document.createElement('h2');
    nombre.className = 'tarjeta-plan-nombre';
    nombre.textContent = admin.nombreMostrar + (admin.esUstedMismo ? ' (tú)' : '');
    const estado = document.createElement('span');
    estado.className = 'estado ' + (admin.activo ? 'estado-activo' : 'estado-inactivo');
    estado.textContent = admin.activo ? 'Activo' : 'Desactivado';
    cabecera.append(nombre, estado);

    const usuario = document.createElement('p');
    usuario.className = 'tarjeta-plan-velocidad';
    usuario.textContent = 'Usuario: ' + admin.username;

    const acceso = document.createElement('p');
    acceso.className = 'campo-ayuda';
    acceso.textContent = admin.debeCambiarPassword
      ? 'Aún no cambia su contraseña temporal.'
      : 'Último ingreso: ' + (admin.ultimoAcceso ? fechaHora(admin.ultimoAcceso) : 'nunca');

    item.append(cabecera, usuario, acceso);

    // Sobre su propia cuenta, un admin no puede desactivarse ni restablecerse
    if (admin.esUstedMismo) {
      const nota = document.createElement('p');
      nota.className = 'campo-ayuda';
      const enlace = document.createElement('a');
      enlace.href = '/cambiar-password.html';
      enlace.textContent = 'Cambiar mi contraseña';
      nota.appendChild(enlace);
      item.appendChild(nota);
      return item;
    }

    const acciones = document.createElement('div');
    acciones.className = 'tarjeta-plan-acciones';
    if (admin.activo) {
      acciones.append(
        boton('Restablecer contraseña', 'candado', 'boton-secundario', (b) => restablecer(admin, b)),
        boton('Desactivar', 'pausa', 'boton-peligro', (b) => desactivar(admin, b)));
    } else {
      acciones.append(boton('Reactivar', 'play', 'boton-secundario', (b) => reactivar(admin, b)));
    }
    item.appendChild(acciones);
    return item;
  }

  function boton(texto, icono, estilo, alPulsar) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'boton ' + estilo + ' boton-compacto';
    b.textContent = texto;
    Iconos.en(b, icono);
    b.addEventListener('click', () => alPulsar(b));
    return b;
  }

  // "2026-10-04T09:15:00" -> "04/10/2026 09:15"
  function fechaHora(iso) {
    const [fecha, hora] = iso.split('T');
    return Formato.fecha(fecha) + (hora ? ' ' + hora.slice(0, 5) : '');
  }

  // ---------- Acciones ----------

  async function restablecer(admin, b) {
    const ok = await confirmar({
      titulo: '¿Restablecer la contraseña de ' + admin.nombreMostrar + '?',
      texto: 'Su contraseña actual dejará de funcionar y se cerrará su sesión. Recibirás una contraseña temporal para entregarle.',
      boton: 'Sí, restablecer',
    });
    if (!ok) return;
    await accion(b, async () => {
      mostrarPassword(await Sesion.api(URL_ADMINS + '/' + admin.id + '/restablecer-password', { method: 'POST' }));
      Sesion.mostrarMensaje(cajaExito, 'Contraseña de ' + admin.nombreMostrar + ' restablecida.');
    });
  }

  async function desactivar(admin, b) {
    const ok = await confirmar({
      titulo: '¿Desactivar a ' + admin.nombreMostrar + '?',
      texto: 'Ya no podrá entrar al panel y su sesión abierta se cerrará. Puedes reactivarlo cuando quieras.',
      boton: 'Sí, desactivar',
    });
    if (!ok) return;
    await accion(b, async () => {
      await Sesion.api(URL_ADMINS + '/' + admin.id + '/desactivar', { method: 'POST' });
      Sesion.mostrarMensaje(cajaExito, admin.nombreMostrar + ' fue desactivado.');
      await cargar();
    });
  }

  async function reactivar(admin, b) {
    await accion(b, async () => {
      await Sesion.api(URL_ADMINS + '/' + admin.id + '/reactivar', { method: 'POST' });
      Sesion.mostrarMensaje(cajaExito, admin.nombreMostrar + ' puede volver a entrar.');
      await cargar();
    });
  }

  async function accion(b, hacer) {
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
    b.disabled = true;
    try {
      await hacer();
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      b.disabled = false;
    }
  }

  // ---------- Nuevo administrador ----------

  function abrirFormulario() {
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
    Sesion.ocultar($('error-formulario'));
    form.reset();
    $('panel-formulario').hidden = false;
    $('boton-nuevo').hidden = true;
    form.nombreMostrar.focus();
  }

  function cerrarFormulario() {
    form.reset();
    $('panel-formulario').hidden = true;
    $('boton-nuevo').hidden = false;
  }

  async function crear(evento) {
    evento.preventDefault();
    const cajaErrorForm = $('error-formulario');
    Sesion.ocultar(cajaErrorForm);
    const datos = { nombreMostrar: form.nombreMostrar.value.trim(), username: form.username.value.trim() };

    const problemas = [];
    if (!datos.nombreMostrar) problemas.push('Escribe el nombre.');
    if (!/^[A-Za-z0-9._-]{3,50}$/.test(datos.username)) {
      problemas.push('El usuario debe tener de 3 a 50 caracteres: letras sin tildes, números, punto, guion o guion bajo.');
    }
    if (problemas.length > 0) {
      Sesion.mostrarMensaje(cajaErrorForm, 'Revisa los datos:', problemas);
      return;
    }

    const botonGuardar = $('boton-guardar');
    botonGuardar.disabled = true;
    try {
      const cuenta = await Sesion.api(URL_ADMINS, { method: 'POST', body: datos });
      cerrarFormulario();
      mostrarPassword(cuenta);
      Sesion.mostrarMensaje(cajaExito, 'Administrador ' + datos.nombreMostrar + ' creado.');
      await cargar();
    } catch (error) {
      Sesion.mostrarError(cajaErrorForm, error);
    } finally {
      botonGuardar.disabled = false;
    }
  }

  // ---------- Contraseña temporal (se muestra una sola vez) ----------

  function mostrarPassword(cuenta) {
    $('pass-usuario').textContent = cuenta.username;
    $('pass-valor').textContent = cuenta.passwordTemporal;
    $('panel-password').hidden = false;
    $('panel-password').scrollIntoView({ block: 'start', behavior: 'smooth' });
  }

  function ocultarPassword() {
    $('panel-password').hidden = true;
    $('pass-usuario').textContent = '';
    $('pass-valor').textContent = '';
  }

  async function copiar() {
    const texto = 'Usuario: ' + $('pass-usuario').textContent + '\nContraseña temporal: ' + $('pass-valor').textContent;
    try {
      await navigator.clipboard.writeText(texto);
      Sesion.mostrarMensaje(cajaExito, 'Copiado.');
    } catch {
      Sesion.mostrarMensaje(cajaExito, 'No se pudo copiar automáticamente: anótalos a mano.');
    }
  }

  // Ventana de confirmación con <dialog>. Devuelve true si se pulsó el botón de confirmar.
  function confirmar({ titulo, texto, boton: textoBoton }) {
    const dialogo = $('dialogo');
    $('dialogo-titulo').textContent = titulo;
    $('dialogo-texto').textContent = texto;
    $('dialogo-si').textContent = textoBoton;
    dialogo.returnValue = '';
    return new Promise((resolver) => {
      dialogo.addEventListener('close', () => resolver(dialogo.returnValue === 'si'), { once: true });
      dialogo.showModal();
    });
  }
})();
