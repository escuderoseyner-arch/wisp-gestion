// =====================================================================
// Funciones compartidas por todas las páginas: guardar y leer el token,
// proteger páginas, llamar a la API y mostrar mensajes.
// Se carga ANTES del script propio de cada página.
// =====================================================================

const Sesion = (() => {
  const CLAVE_TOKEN = 'wisp.token';
  const RUTA_LOGIN = '/login.html';
  const RUTA_CAMBIAR_PASSWORD = '/cambiar-password.html';
  const INICIO_POR_ROL = {
    ADMIN: '/admin/inicio.html',
    CLIENTE: '/cliente/inicio.html',
  };

  // ---------- Token ----------

  // Lee el contenido (payload) del JWT. Solo sirve para decidir a dónde ir:
  // el servidor vuelve a validar la firma en cada petición, así que no es un riesgo.
  function leerPayload(token) {
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const conRelleno = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
      const bytes = Uint8Array.from(atob(conRelleno), (c) => c.charCodeAt(0));
      return JSON.parse(new TextDecoder().decode(bytes)); // TextDecoder: respeta tildes y ñ
    } catch {
      return null;
    }
  }

  function leerToken() {
    try {
      return sessionStorage.getItem(CLAVE_TOKEN);
    } catch {
      return null; // el navegador bloquea el almacenamiento
    }
  }

  // sessionStorage: se borra solo al cerrar la pestaña (más seguro que localStorage)
  function guardar(token) {
    try {
      sessionStorage.setItem(CLAVE_TOKEN, token);
    } catch {
      throw new Error('Tu navegador no permite guardar la sesión. Desactiva el modo de navegación restringida.');
    }
  }

  function borrar() {
    try {
      sessionStorage.removeItem(CLAVE_TOKEN);
    } catch {
      // nada que borrar
    }
  }

  // Devuelve { token, datos } si hay una sesión vigente, o null
  function obtener() {
    const token = leerToken();
    if (!token) return null;
    const datos = leerPayload(token);
    if (!datos || typeof datos.exp !== 'number' || datos.exp * 1000 <= Date.now()) {
      borrar();
      return null;
    }
    return { token, datos };
  }

  // ---------- Navegación ----------

  // motivo: 'expirada', 'cerrada' o 'salida', para mostrar un aviso en el login
  function irAlLogin(motivo) {
    borrar();
    location.replace(RUTA_LOGIN + (motivo ? '?motivo=' + motivo : ''));
  }

  function cerrar() {
    irAlLogin('salida');
  }

  // Después del login: primero el cambio de contraseña obligatorio, luego el inicio según el rol
  function irAlInicio(datos) {
    if (datos.debeCambiarPassword) {
      location.replace(RUTA_CAMBIAR_PASSWORD);
      return;
    }
    const destino = INICIO_POR_ROL[datos.rol];
    if (destino) {
      location.replace(destino);
    } else {
      irAlLogin();
    }
  }

  // Protege una página. Devuelve los datos del token, o null si redirigió a otra página.
  //  - rol: solo deja pasar a ese rol ('ADMIN' o 'CLIENTE')
  //  - esCambioPassword: true solo en cambiar-password.html
  function requerir({ rol = null, esCambioPassword = false } = {}) {
    const habiaToken = leerToken() !== null;
    const sesion = obtener();
    if (!sesion) {
      irAlLogin(habiaToken ? 'expirada' : null);
      return null;
    }
    if (sesion.datos.debeCambiarPassword && !esCambioPassword) {
      location.replace(RUTA_CAMBIAR_PASSWORD);
      return null;
    }
    if (rol && sesion.datos.rol !== rol) {
      irAlInicio(sesion.datos); // un CLIENTE que abre una página de ADMIN va a la suya
      return null;
    }
    programarExpiracion(sesion.datos.exp);
    return sesion.datos;
  }

  // Si la pestaña queda abierta, cierra la sesión justo cuando vence el token
  function programarExpiracion(exp) {
    const milisegundos = exp * 1000 - Date.now();
    setTimeout(() => irAlLogin('expirada'), Math.min(milisegundos, 2147483647));
  }

  // ---------- API ----------

  // fetch con el token y manejo de errores. Devuelve el JSON o lanza un Error con el mensaje del servidor.
  async function api(url, { method = 'GET', body } = {}) {
    const sesion = obtener();
    const headers = { Accept: 'application/json' };
    if (sesion) headers.Authorization = 'Bearer ' + sesion.token;
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    let respuesta;
    try {
      respuesta = await fetch(url, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch {
      throw new Error('No se pudo conectar con el servidor. Revisa tu conexión a internet.');
    }

    // Token rechazado por el servidor: expiró, cambió la contraseña o la cuenta se desactivó.
    // Se borra el token y se vuelve al login.
    if (respuesta.status === 401 && sesion) {
      irAlLogin('cerrada');
      throw new Error('Tu sesión se cerró. Vuelve a ingresar.');
    }

    const datos = await respuesta.json().catch(() => null);
    if (!respuesta.ok) {
      const error = new Error(
        (datos && datos.mensaje) || 'Ocurrió un error inesperado (código ' + respuesta.status + ').'
      );
      error.detalles = (datos && datos.detalles) || [];
      throw error;
    }
    return datos;
  }

  // ---------- Interfaz ----------

  // Siempre textContent (nunca innerHTML): así un texto con <script> no se ejecuta
  function mostrarMensaje(caja, texto, detalles = []) {
    caja.textContent = texto;
    if (detalles.length > 0) {
      const lista = document.createElement('ul');
      detalles.forEach((detalle) => {
        const item = document.createElement('li');
        item.textContent = detalle;
        lista.appendChild(item);
      });
      caja.appendChild(lista);
    }
    caja.hidden = false;
  }

  function mostrarError(caja, error) {
    mostrarMensaje(caja, error.message, error.detalles || []);
    caja.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  function ocultar(caja) {
    caja.hidden = true;
    caja.textContent = '';
  }

  // Pone el nombre y el logo de la empresa en los elementos con data-empresa y data-logo.
  // Devuelve los datos públicos de la empresa, o null si no se pudieron cargar.
  async function cargarEmpresa() {
    try {
      const empresa = await api('/api/public/configuracion');
      document.querySelectorAll('[data-empresa]').forEach((el) => {
        el.textContent = empresa.nombreEmpresa;
      });
      document.title = document.title + ' · ' + empresa.nombreEmpresa;

      const logoValido = empresa.logoUrl && /^(https:\/\/|\/)/.test(empresa.logoUrl);
      if (logoValido) {
        document.querySelectorAll('img[data-logo]').forEach((img) => {
          img.src = empresa.logoUrl;
          img.alt = 'Logo de ' + empresa.nombreEmpresa;
          img.addEventListener('error', () => { img.hidden = true; }); // si la imagen no carga
          img.hidden = false;
        });
      }
      return empresa;
    } catch {
      // Si falla, se queda el nombre genérico del HTML
      return null;
    }
  }

  // Botones "Mostrar / Ocultar" de los campos de contraseña (data-mostrar="id-del-input")
  function activarBotonesMostrar() {
    document.querySelectorAll('[data-mostrar]').forEach((boton) => {
      const input = document.getElementById(boton.dataset.mostrar);
      boton.addEventListener('click', () => {
        const visible = input.type === 'text';
        input.type = visible ? 'password' : 'text';
        boton.textContent = visible ? 'Mostrar' : 'Ocultar';
        boton.setAttribute('aria-pressed', String(!visible));
        boton.setAttribute('aria-label', visible ? 'Mostrar contraseña' : 'Ocultar contraseña');
      });
    });
  }

  return {
    guardar,
    obtener,
    requerir,
    irAlInicio,
    cerrar,
    api,
    mostrarMensaje,
    mostrarError,
    ocultar,
    cargarEmpresa,
    activarBotonesMostrar,
  };
})();
