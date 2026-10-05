// Tema de colores: Automático (según el sistema), Claro u Oscuro.
// Se carga SIN defer y ANTES del CSS: así el tema se aplica antes de pintar la página y no parpadea.
// La elección se guarda en localStorage (solo en este navegador). El botón para cambiarlo se agrega
// en la barra superior o, en el login, arriba a la derecha.

(() => {
  const CLAVE = 'wisp.tema';
  const OPCIONES = ['auto', 'claro', 'oscuro'];
  const TEXTOS = { auto: 'Automático', claro: 'Claro', oscuro: 'Oscuro' };
  const ICONOS = { auto: 'auto', claro: 'sol', oscuro: 'luna' };

  function leer() {
    try {
      const valor = localStorage.getItem(CLAVE);
      return OPCIONES.includes(valor) ? valor : 'auto';
    } catch {
      return 'auto'; // el navegador bloquea el almacenamiento
    }
  }

  function guardar(tema) {
    try {
      if (tema === 'auto') localStorage.removeItem(CLAVE);
      else localStorage.setItem(CLAVE, tema);
    } catch {
      // sin almacenamiento: el cambio dura hasta recargar la página
    }
  }

  // Automático = sin atributo: manda el CSS @media (prefers-color-scheme)
  function aplicar(tema) {
    if (tema === 'auto') document.documentElement.removeAttribute('data-tema');
    else document.documentElement.setAttribute('data-tema', tema);
  }

  let actual = leer();
  aplicar(actual); // <- esto corre antes de que se vea la página

  // Si se cambia en otra pestaña, se aplica aquí también
  window.addEventListener('storage', (e) => {
    if (e.key === CLAVE) {
      actual = leer();
      aplicar(actual);
      document.querySelectorAll('.boton-tema').forEach(dibujar);
    }
  });

  function dibujar(boton) {
    boton.replaceChildren();
    // "const Iconos" no queda en window: se revisa con typeof (en index.html no se carga iconos.js)
    if (typeof Iconos !== 'undefined') boton.appendChild(Iconos.crear(ICONOS[actual]));
    const texto = document.createElement('span');
    texto.className = 'boton-tema-texto';
    texto.textContent = TEXTOS[actual];
    boton.appendChild(texto);
    boton.setAttribute('aria-label', 'Tema de colores: ' + TEXTOS[actual] + '. Toca para cambiarlo.');
    boton.title = 'Tema: ' + TEXTOS[actual];
  }

  // Cada toque pasa al siguiente: Automático -> Claro -> Oscuro -> Automático
  function crearBoton() {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'boton boton-secundario boton-compacto boton-tema';
    boton.addEventListener('click', () => {
      actual = OPCIONES[(OPCIONES.indexOf(actual) + 1) % OPCIONES.length];
      guardar(actual);
      aplicar(actual);
      dibujar(boton);
    });
    dibujar(boton);
    return boton;
  }

  // Cuando la página ya cargó (y iconos.js también), se agrega el botón
  document.addEventListener('DOMContentLoaded', () => {
    const barra = document.querySelector('header.barra');
    if (barra) {
      barra.insertBefore(crearBoton(), barra.querySelector('#cerrar-sesion'));
    } else if (document.body.classList.contains('pantalla-centrada')) {
      const contenedor = document.createElement('div');
      contenedor.className = 'selector-tema-flotante';
      contenedor.appendChild(crearBoton());
      document.body.appendChild(contenedor);
    }
  });
})();
