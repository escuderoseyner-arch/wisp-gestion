// Página de login. Con "defer" el script corre cuando el HTML ya está cargado.

(() => {
  // Si ya hay una sesión vigente, no tiene sentido volver a ingresar
  const sesionActual = Sesion.obtener();
  if (sesionActual) {
    Sesion.irAlInicio(sesionActual.datos);
    return;
  }

  const form = document.getElementById('form-login');
  const boton = document.getElementById('boton-ingresar');
  const cajaError = document.getElementById('error');
  const cajaAviso = document.getElementById('aviso');

  Sesion.cargarEmpresa().then(prepararEnlaceOlvido);
  Sesion.activarBotonesMostrar();

  // "¿Olvidaste tu contraseña?": abre WhatsApp de soporte con el usuario ya escrito
  function prepararEnlaceOlvido(empresa) {
    let numero = ((empresa && empresa.whatsappSoporte) || '').replace(/\D/g, '');
    if (!numero) return; // sin WhatsApp configurado, el enlace queda oculto
    // wa.me necesita el código de país: un celular peruano de 9 dígitos se completa con 51
    if (/^9\d{8}$/.test(numero)) numero = '51' + numero;

    const enlace = document.getElementById('enlace-olvido');
    const actualizar = () => {
      const mensaje = 'Hola, olvidé mi contraseña. Mi usuario es: ' + form.username.value.trim();
      enlace.href = 'https://wa.me/' + numero + '?text=' + encodeURIComponent(mensaje);
    };
    actualizar();
    form.username.addEventListener('input', actualizar);
    enlace.hidden = false;
  }

  const motivo = new URLSearchParams(location.search).get('motivo');
  if (motivo === 'expirada') Sesion.mostrarMensaje(cajaAviso, 'Tu sesión expiró. Vuelve a ingresar.');
  if (motivo === 'salida') Sesion.mostrarMensaje(cajaAviso, 'Cerraste sesión correctamente.');

  form.username.focus();

  form.addEventListener('submit', async (evento) => {
    evento.preventDefault(); // evita que el navegador recargue la página
    Sesion.ocultar(cajaError);
    Sesion.ocultar(cajaAviso);

    const username = form.username.value.trim();
    const password = form.password.value;
    if (!username || !password) {
      Sesion.mostrarMensaje(cajaError, 'Escribe tu usuario y tu contraseña.');
      return;
    }

    boton.disabled = true;
    boton.textContent = 'Ingresando…';
    try {
      const respuesta = await Sesion.api('/api/auth/login', {
        method: 'POST',
        body: { username, password },
      });
      Sesion.guardar(respuesta.token);
      Sesion.irAlInicio(Sesion.obtener().datos);
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
      form.password.value = '';
      form.password.focus();
      boton.disabled = false;
      boton.textContent = 'Ingresar';
    }
  });
})();
