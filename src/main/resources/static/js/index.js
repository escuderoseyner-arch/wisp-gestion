// "/" lleva al inicio del usuario si tiene sesión, o al login si no
(() => {
  const sesion = Sesion.obtener();
  if (sesion) {
    Sesion.irAlInicio(sesion.datos);
  } else {
    location.replace('/login.html');
  }
})();
