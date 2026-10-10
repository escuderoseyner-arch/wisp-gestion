// Barra de navegación del ADMIN. Se agrega dentro de la barra superior de cada página:
// en celular queda fija abajo de la pantalla (como una app) y en escritorio arriba, junto al nombre.
// Se carga después de iconos.js.

(() => {
  const SECCIONES = [
    { ruta: '/admin/inicio.html', texto: 'Inicio', icono: 'inicio' },
    { ruta: '/admin/clientes.html', texto: 'Clientes', icono: 'clientes' },
    { ruta: '/admin/pagos.html', texto: 'Pagos', icono: 'pagos' },
    { ruta: '/admin/caja.html', texto: 'Caja', icono: 'dinero' },
    { ruta: '/admin/planes.html', texto: 'Planes', icono: 'planes' },
    { ruta: '/admin/redes.html', texto: 'Redes', icono: 'router' },
    { ruta: '/admin/administradores.html', texto: 'Admins', icono: 'escudo' },
    { ruta: '/admin/configuracion.html', texto: 'Configuración', icono: 'configuracion' },
  ];

  const barra = document.querySelector('header.barra');
  if (!barra) return;

  const nav = document.createElement('nav');
  nav.className = 'nav-admin';
  nav.setAttribute('aria-label', 'Menú principal');
  const lista = document.createElement('ul');

  SECCIONES.forEach((seccion) => {
    const item = document.createElement('li');
    const enlace = document.createElement('a');
    enlace.href = seccion.ruta;
    enlace.className = 'nav-enlace';
    if (location.pathname === seccion.ruta) {
      enlace.setAttribute('aria-current', 'page'); // la página actual se resalta
    }
    const texto = document.createElement('span');
    texto.textContent = seccion.texto;
    enlace.append(Iconos.crear(seccion.icono), texto);
    item.appendChild(enlace);
    lista.appendChild(item);
  });

  nav.appendChild(lista);
  // Entre el nombre de la empresa y el botón de salir
  barra.insertBefore(nav, barra.querySelector('#cerrar-sesion'));
  document.body.classList.add('con-nav-admin');
})();
