// Contraseña inicial que escribe el admin (crear cuenta o restablecer). Se usa en Admins y Clientes.
// La persona debe cambiarla en su próximo ingreso. Depende de sesion.js e iconos.js.

const Clave = (() => {
  const MIN = 8;
  const MAX = 72;
  // Sin caracteres que se confunden al leerlos en un celular: O/o/0, I/l/1
  const LETRAS = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz';
  const NUMEROS = '23456789';
  const TODOS = LETRAS + NUMEROS;

  // Aleatoria y segura (crypto, no Math.random), con al menos una letra y un número
  function generar(longitud = 10) {
    while (true) {
      const valores = crypto.getRandomValues(new Uint32Array(longitud));
      const texto = Array.from(valores, (v) => TODOS[v % TODOS.length]).join('');
      if (/[2-9]/.test(texto) && /[A-Za-z]/.test(texto)) return texto;
    }
  }

  // Devuelve el problema en texto, o null si está bien. BCrypt acepta hasta 72 bytes.
  function validar(password) {
    if (password.length < MIN) return 'La contraseña debe tener al menos ' + MIN + ' caracteres.';
    if (new TextEncoder().encode(password).length > MAX) return 'La contraseña no puede tener más de ' + MAX + ' caracteres.';
    return null;
  }

  // Conecta un input con sus botones "Mostrar" y "Generar"
  function conectar(input, botonMostrar, botonGenerar) {
    const mostrar = (visible) => {
      input.type = visible ? 'text' : 'password';
      botonMostrar.textContent = visible ? 'Ocultar' : 'Mostrar';
      botonMostrar.setAttribute('aria-pressed', String(visible));
      botonMostrar.setAttribute('aria-label', visible ? 'Ocultar contraseña' : 'Mostrar contraseña');
    };
    botonMostrar.addEventListener('click', () => mostrar(input.type === 'password'));
    botonGenerar.addEventListener('click', () => {
      input.value = generar();
      mostrar(true); // para que el admin la vea y la anote
      input.focus();
    });
    return { reiniciar: () => { input.value = ''; mostrar(false); } };
  }

  // Ventana para escribir la contraseña. Devuelve la contraseña, o null si se canceló.
  function pedir({ titulo, texto, boton }) {
    const dialogo = document.createElement('dialog');
    dialogo.className = 'dialogo';
    const form = document.createElement('form');
    form.noValidate = true;

    const h2 = document.createElement('h2');
    h2.className = 'panel-titulo';
    h2.id = 'clave-titulo';
    h2.textContent = titulo;
    dialogo.setAttribute('aria-labelledby', h2.id);
    const p = document.createElement('p');
    p.textContent = texto;

    const error = document.createElement('div');
    error.className = 'aviso aviso-error';
    error.setAttribute('role', 'alert');
    error.hidden = true;

    const campo = document.createElement('div');
    campo.className = 'campo campo-password';
    const label = document.createElement('label');
    label.htmlFor = 'clave-nueva';
    label.textContent = 'Contraseña (mínimo ' + MIN + ' caracteres)';
    const input = document.createElement('input');
    input.id = 'clave-nueva';
    input.type = 'password';
    input.maxLength = MAX;
    input.autocomplete = 'new-password';
    input.autocapitalize = 'none';
    input.spellcheck = false;
    const botonMostrar = document.createElement('button');
    botonMostrar.type = 'button';
    botonMostrar.className = 'boton-ojo';
    campo.append(label, input, botonMostrar);

    const botonGenerar = document.createElement('button');
    botonGenerar.type = 'button';
    botonGenerar.className = 'boton boton-secundario boton-compacto boton-generar';
    botonGenerar.textContent = 'Generar una segura';

    const acciones = document.createElement('div');
    acciones.className = 'acciones';
    const si = document.createElement('button');
    si.type = 'submit';
    si.className = 'boton boton-primario';
    si.textContent = boton;
    const no = document.createElement('button');
    no.type = 'button';
    no.className = 'boton boton-secundario';
    no.textContent = 'Cancelar';
    acciones.append(si, no);

    form.append(h2, p, error, campo, botonGenerar, acciones);
    dialogo.appendChild(form);
    document.body.appendChild(dialogo);
    conectar(input, botonMostrar, botonGenerar).reiniciar();

    return new Promise((resolver) => {
      let resultado = null;
      form.addEventListener('submit', (evento) => {
        evento.preventDefault();
        const problema = validar(input.value);
        if (problema) {
          Sesion.mostrarMensaje(error, problema);
          input.focus();
          return;
        }
        resultado = input.value;
        dialogo.close();
      });
      no.addEventListener('click', () => dialogo.close());
      dialogo.addEventListener('close', () => {
        dialogo.remove(); // la contraseña no queda en la página
        resolver(resultado);
      }, { once: true });
      dialogo.showModal();
      input.focus();
    });
  }

  return { generar, validar, conectar, pedir, MIN, MAX };
})();
