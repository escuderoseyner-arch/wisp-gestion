// Selector "Todas las redes / Shiaunto / Cuypirmarca" de Inicio, Clientes y Pagos.
// Recuerda la última opción en este navegador (por usuario). Si el usuario ve una sola red
// (o no hay más de una), no se muestra y se usan todas las suyas.
// El servidor vuelve a revisar la red en cada petición: elegir otra aquí no da acceso a nada nuevo.
// Se carga después de sesion.js.

const SelectorRed = (() => {
  let redes = [];
  let actual = null; // id de la red elegida, o null = todas

  function clave() {
    const sesion = Sesion.obtener();
    return 'wisp.filtroRed.' + (sesion ? sesion.datos.sub : '');
  }

  function leer() {
    try {
      return localStorage.getItem(clave());
    } catch {
      return null;
    }
  }

  function guardar(valor) {
    try {
      if (valor == null) localStorage.removeItem(clave());
      else localStorage.setItem(clave(), String(valor));
    } catch {
      // sin almacenamiento: solo no se recuerda
    }
  }

  // Lo agrega dentro de "contenedor" y llama a alCambiar() cada vez que se elige otra red.
  // Devuelve la lista de redes que ve el usuario.
  async function iniciar(contenedor, alCambiar) {
    try {
      redes = await Sesion.api('/api/admin/redes/visibles');
    } catch {
      redes = [];
    }
    const guardado = Number(leer());
    actual = redes.some((r) => r.id === guardado) ? guardado : null;
    if (redes.length <= 1 || !contenedor) {
      actual = null;
      return redes;
    }

    const campo = document.createElement('div');
    campo.className = 'campo selector-red';
    const etiqueta = document.createElement('label');
    etiqueta.htmlFor = 'filtro-red';
    etiqueta.textContent = 'Red';
    const select = document.createElement('select');
    select.id = 'filtro-red';
    select.append(new Option('Todas las redes', ''));
    redes.forEach((r) => select.append(new Option(r.nombre, r.id)));
    select.value = actual == null ? '' : String(actual);
    select.addEventListener('change', () => {
      actual = select.value === '' ? null : Number(select.value);
      guardar(actual);
      alCambiar(actual);
    });
    campo.append(etiqueta, select);
    contenedor.appendChild(campo);
    contenedor.hidden = false;
    return redes;
  }

  function redId() {
    return actual;
  }

  // Agrega ?redId=N (o &redId=N) a una URL si hay una red elegida
  function enUrl(url) {
    if (actual == null) return url;
    return url + (url.includes('?') ? '&' : '?') + 'redId=' + actual;
  }

  return { iniciar, redId, enUrl };
})();
