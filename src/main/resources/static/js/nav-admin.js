// Barra de navegación del ADMIN. Se agrega dentro de la barra superior de cada página:
// en celular queda fija abajo de la pantalla (como una app) y en escritorio arriba, junto al nombre.
// Se carga después de iconos.js.

(() => {
  // operador: true = también la ve el OPERADOR (las demás son solo del ADMIN)
  const SECCIONES = [
    { ruta: '/admin/inicio.html', texto: 'Inicio', icono: 'inicio', operador: true },
    { ruta: '/admin/clientes.html', texto: 'Clientes', icono: 'clientes', operador: true },
    { ruta: '/admin/pagos.html', texto: 'Pagos', icono: 'pagos', operador: true },
    { ruta: '/admin/caja.html', texto: 'Caja', icono: 'dinero', operador: true },
    { ruta: '/admin/planes.html', texto: 'Planes', icono: 'planes' },
    { ruta: '/admin/redes.html', texto: 'Redes', icono: 'router' },
    { ruta: '/admin/administradores.html', texto: 'Admins', icono: 'escudo' },
    { ruta: '/admin/configuracion.html', texto: 'Configuración', icono: 'configuracion' },
  ];

  const barra = document.querySelector('header.barra');
  const sesion = Sesion.obtener();
  if (!barra || !sesion) return;
  const esAdmin = sesion.datos.rol === 'ADMIN';

  const visibles = SECCIONES.filter((seccion) => esAdmin || seccion.operador);
  // En celular, las secciones que no son del operador van dentro de "Más" (el operador no lo necesita)
  const enMas = visibles.filter((seccion) => !seccion.operador);

  function crearEnlace(seccion) {
    const enlace = document.createElement('a');
    enlace.href = seccion.ruta;
    enlace.className = 'nav-enlace';
    if (location.pathname === seccion.ruta) {
      enlace.setAttribute('aria-current', 'page'); // la página actual se resalta
    }
    const texto = document.createElement('span');
    texto.textContent = seccion.texto;
    enlace.append(Iconos.crear(seccion.icono), texto);
    return enlace;
  }

  const nav = document.createElement('nav');
  nav.className = 'nav-admin';
  nav.setAttribute('aria-label', 'Menú principal');
  const lista = document.createElement('ul');

  visibles.forEach((seccion) => {
    const item = document.createElement('li');
    if (enMas.includes(seccion)) {
      item.className = 'nav-solo-escritorio';
    }
    item.appendChild(crearEnlace(seccion));
    lista.appendChild(item);
  });

  if (enMas.length > 0) {
    lista.appendChild(crearMas());
  }

  nav.appendChild(lista);
  // Entre el nombre de la empresa y el botón de salir
  barra.insertBefore(nav, barra.querySelector('#cerrar-sesion'));
  document.body.classList.add('con-nav-admin');

  // Botón "Más" (solo en celular) que abre un panel desde abajo con el resto de secciones
  function crearMas() {
    const panel = document.createElement('dialog');
    panel.className = 'panel-mas';
    panel.setAttribute('aria-label', 'Más secciones');
    const listaMas = document.createElement('ul');
    enMas.forEach((seccion) => {
      const item = document.createElement('li');
      item.appendChild(crearEnlace(seccion));
      listaMas.appendChild(item);
    });
    panel.appendChild(listaMas);
    // Tocar fuera del panel (en el fondo oscuro) lo cierra
    panel.addEventListener('click', (evento) => {
      if (evento.target === panel) panel.close();
    });
    document.body.appendChild(panel);

    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'nav-enlace';
    boton.setAttribute('aria-haspopup', 'dialog');
    if (enMas.some((seccion) => seccion.ruta === location.pathname)) {
      boton.classList.add('activo'); // la página actual está dentro de "Más"
    }
    const texto = document.createElement('span');
    texto.textContent = 'Más';
    boton.append(Iconos.crear('puntos'), texto);
    boton.addEventListener('click', () => panel.showModal());

    // Si la pantalla crece a escritorio con el panel abierto, se cierra
    matchMedia('(min-width: 960px)').addEventListener('change', (evento) => {
      if (evento.matches && panel.open) panel.close();
    });

    const item = document.createElement('li');
    item.className = 'nav-solo-celular';
    item.appendChild(boton);
    return item;
  }
})();
