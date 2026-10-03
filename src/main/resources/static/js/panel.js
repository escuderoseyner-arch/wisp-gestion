// Panel del ADMIN: resumen del mes, atrasados, próximos a vencer y pagos de la semana.

(() => {
  const datosSesion = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datosSesion) return; // ya se está redirigiendo

  const $ = (id) => document.getElementById(id);
  const cajaExito = $('exito');
  const cajaError = $('error');

  let panel = null; // última respuesta del servidor

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);

  Sesion.api('/api/auth/me')
    .then((usuario) => { $('nombre-usuario').textContent = usuario.nombreMostrar; })
    .catch(() => { $('nombre-usuario').textContent = ''; });

  cargar();

  async function cargar() {
    try {
      panel = await Sesion.api('/api/admin/panel');
      dibujar();
      $('panel').hidden = false;
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  function dibujar() {
    const p = panel;
    const dinero = (monto) => Formato.dinero(p.moneda, monto);

    // Resumen del mes
    $('titulo-mes').textContent = Formato.nombreMes(p.periodo);
    $('total-esperado').textContent = dinero(p.esperado);
    $('total-cobrado').textContent = dinero(p.cobrado);
    $('total-pendiente').textContent = dinero(p.pendiente);
    $('clientes-resumen').textContent = p.clientesActivos + (p.clientesActivos === 1 ? ' cliente activo' : ' clientes activos')
      + ' · ' + p.clientesSuspendidos + (p.clientesSuspendidos === 1 ? ' suspendido' : ' suspendidos');

    // Atrasados
    $('titulo-atrasados').textContent = 'Atrasados (' + p.atrasados.length + ')';
    $('lista-atrasados').replaceChildren(...p.atrasados.map((a) => {
      const meses = a.periodos.length === 1 ? '1 mes' : a.periodos.length + ' meses';
      return crearFilaCliente(a, {
        detalle: [a.zona || 'Sin zona', 'Debe ' + meses + ': ' + Formato.mesesEnTexto(a.periodos)].join(' · '),
        destacado: dinero(a.deuda),
        nota: a.diasAtraso === 1 ? '1 día de atraso' : a.diasAtraso + ' días de atraso',
        periodoPago: a.periodos[0],
        recordar: { periodos: a.periodos, monto: a.deuda }, // debe todos sus meses vencidos
      });
    }));
    $('atrasados-vacio').hidden = p.atrasados.length > 0;

    // Próximos a vencer
    $('titulo-proximos').textContent = 'Próximos a vencer (' + p.diasProximos + ' días)';
    $('lista-proximos').replaceChildren(...p.proximos.map((x) => crearFilaCliente(x, {
      detalle: [x.zona || 'Sin zona', Formato.nombreMes(x.periodo)].join(' · '),
      destacado: dinero(x.monto),
      nota: textoDias(x.diasRestantes) + ' (' + Formato.fecha(x.vence) + ')',
      periodoPago: x.periodo,
      recordar: { periodos: [x.periodo], monto: x.monto }, // el mes que está por vencer
    })));
    $('proximos-vacio').hidden = p.proximos.length > 0;

    // Pagos de la semana
    $('total-semana').textContent = dinero(p.totalSemana);
    $('semana-rango').textContent = 'Del lunes ' + Formato.fecha(p.semanaDesde) + ' al domingo ' + Formato.fecha(p.semanaHasta);
    $('lista-semana').replaceChildren(...p.pagosSemana.map((pago) => {
      const item = crearItem(pago.codigo + ' · ' + pago.nombres,
        Formato.nombreMes(pago.periodo) + ' · ' + Formato.fecha(pago.fechaPago) + ' · ' + Pagos.METODOS[pago.metodo]);
      const monto = document.createElement('strong');
      monto.className = 'fila-monto';
      monto.textContent = dinero(pago.monto);
      item.appendChild(monto);
      return item;
    }));
    $('semana-vacio').hidden = p.pagosSemana.length > 0;
  }

  function textoDias(dias) {
    if (dias === 0) return 'Vence hoy';
    if (dias === 1) return 'Vence mañana';
    return 'Vence en ' + dias + ' días';
  }

  // Fila de un cliente con sus acciones. Siempre textContent, nunca innerHTML.
  function crearFilaCliente(fila, { detalle, destacado, nota, periodoPago, recordar }) {
    const item = crearItem(fila.codigo + ' · ' + fila.nombres, detalle);
    item.classList.add('fila-cliente');

    const lado = document.createElement('div');
    lado.className = 'fila-lado';
    const monto = document.createElement('strong');
    monto.className = 'fila-monto';
    monto.textContent = destacado;
    const pequena = document.createElement('small');
    pequena.textContent = nota;
    lado.append(monto, pequena);

    const acciones = document.createElement('div');
    acciones.className = 'fila-acciones';

    const registrar = document.createElement('button');
    registrar.type = 'button';
    registrar.className = 'boton boton-primario boton-compacto';
    registrar.textContent = 'Registrar pago';
    Iconos.en(registrar, 'mas');
    registrar.setAttribute('aria-label', 'Registrar pago de ' + fila.nombres);
    registrar.addEventListener('click', () => registrarPago(fila, periodoPago, registrar));

    const ver = document.createElement('a');
    ver.className = 'boton boton-secundario boton-compacto';
    ver.href = '/admin/clientes.html#cliente/' + fila.clienteId;
    ver.textContent = 'Ver';
    Iconos.en(ver, 'usuario');
    ver.setAttribute('aria-label', 'Ver a ' + fila.nombres);

    // "Recordar" solo aparece si el cliente tiene celular
    const botonRecordar = Pagos.crearBotonRecordar({
      plantilla: panel.plantillaRecordatorio,
      codigoPais: panel.codigoPais,
      moneda: panel.moneda,
      celular: fila.celular,
      nombre: fila.nombres,
      periodos: recordar.periodos,
      monto: recordar.monto,
    });

    acciones.append(...[botonRecordar, registrar, ver].filter(Boolean));
    item.append(lado, acciones);
    return item;
  }

  function crearItem(titulo, detalle) {
    const item = document.createElement('li');
    item.className = 'fila';
    const texto = document.createElement('div');
    texto.className = 'fila-texto';
    const fuerte = document.createElement('strong');
    fuerte.textContent = titulo;
    const pequeno = document.createElement('small');
    pequeno.textContent = detalle;
    texto.append(fuerte, pequeno);
    item.appendChild(texto);
    return item;
  }

  async function registrarPago(fila, periodo, boton) {
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
    boton.disabled = true;
    try {
      if (await Pagos.abrirFormulario(fila.clienteId, periodo)) {
        Sesion.mostrarMensaje(cajaExito, 'Pago de ' + fila.codigo + ' registrado.');
        await cargar();
      }
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      boton.disabled = false;
    }
  }
})();
