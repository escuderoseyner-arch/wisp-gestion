// Portal del CLIENTE: su deuda, próximo vencimiento, cómo pagar, su plan y su historial.
// El servidor devuelve solo los datos del cliente del token: aquí no se envía ningún id.

(() => {
  const datosSesion = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datosSesion) return; // ya se está redirigiendo

  const METODOS = { YAPE: 'Yape', PLIN: 'Plin', TRANSFERENCIA: 'Transferencia', EFECTIVO: 'Efectivo', OTRO: 'Otro' };
  const ESTADOS = { ACTIVO: 'Activo', SUSPENDIDO: 'Suspendido' };

  const $ = (id) => document.getElementById(id);

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);

  cargar();

  async function cargar() {
    try {
      dibujar(await Sesion.api('/api/cliente/resumen'));
      $('portal').hidden = false;
    } catch (error) {
      Sesion.mostrarError($('error'), error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  function dibujar(r) {
    const dinero = (monto) => Formato.dinero(r.moneda, monto);

    $('codigo').textContent = r.codigo;
    $('nombre').textContent = r.nombres;
    $('aviso-suspendido').hidden = r.estado !== 'SUSPENDIDO';

    // Deuda
    const debe = r.mesesQueDebe.length > 0;
    $('con-deuda').hidden = !debe;
    $('al-dia').hidden = debe;
    if (debe) {
      $('deuda-total').textContent = dinero(r.deuda);
      $('deuda-meses').textContent = (r.mesesQueDebe.length === 1 ? '1 mes vencido: ' : r.mesesQueDebe.length + ' meses vencidos: ')
        + Formato.mesesEnTexto(r.mesesQueDebe.map((m) => m.periodo)) + '.';
    }
    $('proximo').hidden = !r.proximoVencimiento;
    if (r.proximoVencimiento) {
      const p = r.proximoVencimiento;
      $('proximo-texto').textContent = Formato.nombreMes(p.periodo) + ': ' + dinero(p.monto)
        + ', paga hasta el ' + Formato.fecha(p.vence);
    }

    // Cómo pagar
    const datosPago = [
      ['Yape', r.datosParaPagar.yapeNumero],
      ['Titular de Yape', r.datosParaPagar.yapeTitular],
      ['Cuenta bancaria', r.datosParaPagar.cuentaBancaria],
    ].filter(([, valor]) => valor);
    $('datos-pago').replaceChildren(...datosPago.map(([etiqueta, valor]) => crearDato(etiqueta, valor)));
    $('datos-pago-vacio').hidden = datosPago.length > 0;

    // Botones de WhatsApp al soporte, con su código y nombre en el mensaje
    const quienSoy = 'Hola, soy ' + r.nombres + ' (código ' + r.codigo + ').';
    enlazar($('boton-comprobante'), r.soporte, quienSoy + ' Te envío el comprobante de mi pago de internet.');
    enlazar($('boton-falla'), r.soporte, quienSoy + ' Quiero reportar una falla en mi servicio de internet: ');

    // Plan
    const estado = $('estado-servicio');
    estado.className = 'estado estado-' + r.estado.toLowerCase();
    estado.textContent = 'Servicio ' + (ESTADOS[r.estado] || r.estado).toLowerCase();
    $('plan-nombre').textContent = r.plan.nombre;
    $('plan-velocidad').textContent = 'Bajada ' + r.plan.bajadaMbps + ' Mbps · Subida ' + r.plan.subidaMbps + ' Mbps';
    $('plan-precio').textContent = dinero(r.plan.precio) + ' al mes';

    // Historial
    $('historial').replaceChildren(...r.historial.map((pago) => {
      const item = document.createElement('li');
      item.className = 'fila';
      const texto = document.createElement('div');
      texto.className = 'fila-texto';
      const mes = document.createElement('strong');
      mes.textContent = Formato.nombreMes(pago.periodo);
      const detalle = document.createElement('small');
      detalle.textContent = 'Pagado el ' + Formato.fecha(pago.fechaPago) + ' · ' + METODOS[pago.metodo];
      texto.append(mes, detalle);
      const monto = document.createElement('strong');
      monto.className = 'fila-monto';
      monto.textContent = dinero(pago.monto);
      item.append(texto, monto);
      return item;
    }));
    $('historial-vacio').hidden = r.historial.length > 0;
  }

  function enlazar(enlace, soporte, mensaje) {
    const url = Formato.enlaceWhatsApp(soporte.codigoPais, soporte.whatsapp, mensaje);
    enlace.hidden = !url;
    if (url) enlace.href = url;
  }

  function crearDato(etiqueta, valor) {
    const fila = document.createElement('div');
    const dt = document.createElement('dt');
    dt.textContent = etiqueta;
    const dd = document.createElement('dd');
    dd.textContent = valor;
    fila.append(dt, dd);
    return fila;
  }
})();
