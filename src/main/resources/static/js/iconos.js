// Íconos SVG simples, dibujados aquí mismo (sin librerías ni archivos externos, por la CSP).
// Uso en HTML:  <button><span data-icono="mas"></span>Nuevo</button>  -> se dibuja al cargar.
// Uso en JS:    Iconos.en(boton, 'mas')  o  Iconos.crear('mas')
// Todos usan trazo (stroke) del color del texto, así se adaptan al tema claro y oscuro.

const Iconos = (() => {
  const SVG = 'http://www.w3.org/2000/svg';

  // Cada ícono es una lista de trazos en una cuadrícula de 24x24
  const TRAZOS = {
    inicio: ['M3 10.5 12 3l9 7.5', 'M5 9.5V21h14V9.5', 'M10 21v-6h4v6'],
    clientes: ['M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M2 21v-1a6 6 0 0 1 6-6h2a6 6 0 0 1 6 6v1',
      'M16 3.5a4 4 0 0 1 0 7.5', 'M19 14.5a6 6 0 0 1 3 5.5v1'],
    pagos: ['M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z', 'M3 7l12-4v4', 'M16 13.5h1'],
    planes: ['M2 9a15 15 0 0 1 20 0', 'M5.5 12.5a10 10 0 0 1 13 0', 'M9 16a5 5 0 0 1 6 0', 'M12 20h.01'],
    configuracion: ['M4 6h10', 'M18 6h2', 'M14 4v4', 'M4 12h4', 'M12 12h8', 'M8 10v4', 'M4 18h12', 'M16 16v4'],
    salir: ['M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3', 'M10 17l5-5-5-5', 'M15 12H3'],
    mas: ['M12 5v14', 'M5 12h14'],
    whatsapp: ['M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z', 'M9 10h.01', 'M12 10h.01', 'M15 10h.01'],
    buscar: ['M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z', 'M20 20l-4-4'],
    atras: ['M15 18l-6-6 6-6'],
    adelante: ['M9 18l6-6-6-6'],
    check: ['M20 6 9 17l-5-5'],
    editar: ['M4 20h4L19 9l-4-4L4 16v4z', 'M13 7l4 4'],
    basura: ['M4 7h16', 'M10 11v6', 'M14 11v6', 'M6 7l1 13h10l1-13', 'M9 7V4h6v3'],
    calendario: ['M4 6h16v14H4z', 'M4 10h16', 'M8 3v4', 'M16 3v4'],
    candado: ['M6 11h12v9H6z', 'M8 11V8a4 4 0 0 1 8 0v3'],
    usuario: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21a8 8 0 0 1 16 0'],
    recibo: ['M6 3h12v18l-3-2-3 2-3-2-3 2V3z', 'M9 8h6', 'M9 12h6'],
    falla: ['M12 3 2 21h20L12 3z', 'M12 10v5', 'M12 18h.01'],
    dinero: ['M3 6h18v12H3z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z'],
    reloj: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 7v5l3 2'],
    copiar: ['M8 8h12v12H8z', 'M4 16V4h12'],
    pausa: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M10 9v6', 'M14 9v6'],
    play: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M10 8.5v7l5.5-3.5L10 8.5z'],
    intercambio: ['M4 8h13', 'M14 5l3 3-3 3', 'M20 16H7', 'M10 13l-3 3 3 3'],
    ojo: ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z'],
  };

  // Devuelve un <svg> decorativo (aria-hidden): el texto del botón es el que se lee en voz alta
  function crear(nombre) {
    const svg = document.createElementNS(SVG, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', '20');
    svg.setAttribute('height', '20');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '2');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    svg.setAttribute('class', 'icono');
    (TRAZOS[nombre] || []).forEach((d) => {
      const trazo = document.createElementNS(SVG, 'path');
      trazo.setAttribute('d', d);
      svg.appendChild(trazo);
    });
    return svg;
  }

  // Pone el ícono al inicio de un elemento (botón, enlace) y lo devuelve
  function en(elemento, nombre) {
    elemento.prepend(crear(nombre));
    return elemento;
  }

  // Dibuja los íconos marcados en el HTML con data-icono="nombre"
  function dibujarMarcados(raiz = document) {
    raiz.querySelectorAll('[data-icono]').forEach((marca) => {
      marca.replaceWith(crear(marca.dataset.icono));
    });
  }

  dibujarMarcados();

  return { crear, en, dibujarMarcados };
})();
