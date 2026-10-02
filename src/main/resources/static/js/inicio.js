// Inicio de ADMIN y de CLIENTE (el mismo script para los dos).
// El rol exigido se lee del atributo data-rol del <body>.

(() => {
  const datos = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datos) return; // ya se está redirigiendo

  Sesion.cargarEmpresa();
  document.getElementById('cerrar-sesion').addEventListener('click', Sesion.cerrar);

  // /api/auth/me también confirma con el servidor que el token sigue siendo válido
  Sesion.api('/api/auth/me')
    .then((usuario) => {
      document.getElementById('nombre-usuario').textContent = usuario.nombreMostrar;
    })
    .catch((error) => {
      Sesion.mostrarError(document.getElementById('error'), error);
    });
})();
