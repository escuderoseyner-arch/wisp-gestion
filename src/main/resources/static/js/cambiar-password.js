// Cambio de contraseña. Es obligatorio si el token dice debeCambiarPassword = true;
// si no, el usuario entró por su cuenta y puede volver a su inicio.

(() => {
  const datos = Sesion.requerir({ esCambioPassword: true });
  if (!datos) return; // ya se está redirigiendo al login

  const form = document.getElementById('form-cambiar');
  const boton = document.getElementById('boton-guardar');
  const cajaError = document.getElementById('error');

  Sesion.cargarEmpresa();
  Sesion.activarBotonesMostrar();
  document.getElementById('cerrar-sesion').addEventListener('click', Sesion.cerrar);

  if (datos.debeCambiarPassword) {
    document.getElementById('aviso-obligatorio').hidden = false;
  } else {
    document.getElementById('enlace-volver').hidden = false; // "/" lleva al inicio según el rol
  }

  form.passwordActual.focus();

  form.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    Sesion.ocultar(cajaError);

    const passwordActual = form.passwordActual.value;
    const passwordNueva = form.passwordNueva.value;
    const passwordConfirmar = form.passwordConfirmar.value;

    // Validación rápida en el navegador. El servidor vuelve a validar todo.
    const problemas = [];
    if (!passwordActual) problemas.push('Escribe tu contraseña actual.');
    if (passwordNueva.length < 8) problemas.push('La contraseña nueva debe tener al menos 8 caracteres.');
    if (passwordNueva !== passwordConfirmar) problemas.push('Las contraseñas nuevas no coinciden.');
    if (passwordActual && passwordNueva === passwordActual) {
      problemas.push('La contraseña nueva debe ser distinta de la actual.');
    }
    if (problemas.length > 0) {
      Sesion.mostrarMensaje(cajaError, 'Revisa los datos:', problemas);
      return;
    }

    boton.disabled = true;
    boton.textContent = 'Guardando…';
    try {
      const respuesta = await Sesion.api('/api/auth/cambiar-password', {
        method: 'POST',
        body: { passwordActual, passwordNueva },
      });
      // El servidor devuelve un token nuevo, ya sin la restricción
      Sesion.guardar(respuesta.token);
      Sesion.irAlInicio(Sesion.obtener().datos);
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
      boton.disabled = false;
      boton.textContent = 'Guardar contraseña';
    }
  });
})();
