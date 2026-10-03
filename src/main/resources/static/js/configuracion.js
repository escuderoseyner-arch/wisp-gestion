// Configuración de la empresa para ADMIN.

(() => {
  const datosSesion = Sesion.requerir({ rol: document.body.dataset.rol });
  if (!datosSesion) return; // ya se está redirigiendo

  const URL_CONFIG = '/api/admin/configuracion';
  const VARIABLES = ['nombre', 'monto', 'mes'];

  const $ = (id) => document.getElementById(id);
  const form = $('form-config');
  const cajaExito = $('exito');
  const cajaError = $('error');
  const cajaErrorFormulario = $('error-formulario');
  let moneda = 'S/';

  Sesion.cargarEmpresa();
  $('cerrar-sesion').addEventListener('click', Sesion.cerrar);
  form.addEventListener('submit', guardar);
  form.plantillaRecordatorio.addEventListener('input', actualizarVistaPrevia);

  cargar();

  async function cargar() {
    try {
      llenar(await Sesion.api(URL_CONFIG));
      form.hidden = false;
    } catch (error) {
      Sesion.mostrarError(cajaError, error);
    } finally {
      $('cargando').hidden = true;
    }
  }

  function llenar(c) {
    moneda = c.moneda || 'S/';
    form.nombreEmpresa.value = c.nombreEmpresa || '';
    form.ruc.value = c.ruc || '';
    form.logoUrl.value = c.logoUrl || '';
    form.codigoPais.value = c.codigoPais || '';
    form.whatsappSoporte.value = c.whatsappSoporte || '';
    form.yapeNumero.value = c.yapeNumero || '';
    form.yapeTitular.value = c.yapeTitular || '';
    form.cuentaBancaria.value = c.cuentaBancaria || '';
    form.diasTolerancia.value = c.diasTolerancia ?? '';
    form.plantillaRecordatorio.value = c.plantillaRecordatorio || '';
    actualizarVistaPrevia();
  }

  // Cómo se vería el mensaje con datos de ejemplo
  function actualizarVistaPrevia() {
    $('vista-previa').textContent = Formato.rellenarPlantilla(form.plantillaRecordatorio.value, {
      nombre: 'María Torres',
      monto: Formato.dinero(moneda, 158),
      mes: Formato.mesesEnTexto(['2026-08', '2026-09']),
    });
  }

  const soloDigitos = (texto) => texto.replace(/\D/g, '');

  async function guardar(evento) {
    evento.preventDefault();
    Sesion.ocultar(cajaExito);
    Sesion.ocultar(cajaError);
    Sesion.ocultar(cajaErrorFormulario);

    const datos = {
      nombreEmpresa: form.nombreEmpresa.value.trim(),
      ruc: soloDigitos(form.ruc.value),
      logoUrl: form.logoUrl.value.trim(),
      codigoPais: soloDigitos(form.codigoPais.value),
      whatsappSoporte: soloDigitos(form.whatsappSoporte.value),
      yapeNumero: soloDigitos(form.yapeNumero.value),
      yapeTitular: form.yapeTitular.value.trim(),
      cuentaBancaria: form.cuentaBancaria.value.trim(),
      diasTolerancia: form.diasTolerancia.value === '' ? null : Number(form.diasTolerancia.value),
      plantillaRecordatorio: form.plantillaRecordatorio.value.trim(),
    };

    // Validación rápida en el navegador. El servidor vuelve a validar todo.
    const problemas = [];
    if (!datos.nombreEmpresa) problemas.push('El nombre de la empresa es obligatorio.');
    if (datos.ruc && datos.ruc.length !== 11) problemas.push('El RUC debe tener 11 dígitos.');
    if (datos.logoUrl && !/^(https:\/\/|\/)\S+$/.test(datos.logoUrl)) {
      problemas.push('La URL del logo debe empezar con https://');
    }
    if (!/^[1-9]\d{0,3}$/.test(datos.codigoPais)) problemas.push('El código de país debe tener de 1 a 4 dígitos (ej: 51).');
    if (!/^\d{6,15}$/.test(datos.whatsappSoporte)) {
      problemas.push('El WhatsApp de soporte debe tener entre 6 y 15 dígitos, sin el código de país.');
    }
    if (datos.yapeNumero && !/^\d{6,15}$/.test(datos.yapeNumero)) problemas.push('El número de Yape debe tener entre 6 y 15 dígitos.');
    if (!Number.isInteger(datos.diasTolerancia) || datos.diasTolerancia < 0 || datos.diasTolerancia > 15) {
      problemas.push('Los días de tolerancia deben estar entre 0 y 15.');
    }
    if (!datos.plantillaRecordatorio) problemas.push('La plantilla de recordatorio es obligatoria.');
    (datos.plantillaRecordatorio.match(/\{[^{}]*\}/g) || []).forEach((v) => {
      if (!VARIABLES.includes(v.slice(1, -1))) {
        problemas.push('La plantilla usa la variable ' + v + ', que no existe. Usa solo {nombre}, {monto} y {mes}.');
      }
    });
    if (problemas.length > 0) {
      Sesion.mostrarMensaje(cajaErrorFormulario, 'Revisa los datos:', problemas);
      cajaErrorFormulario.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
      return;
    }

    const boton = $('boton-guardar');
    boton.disabled = true;
    boton.textContent = 'Guardando…';
    try {
      llenar(await Sesion.api(URL_CONFIG, { method: 'PUT', body: datos }));
      Sesion.mostrarMensaje(cajaExito, 'Configuración guardada.');
      window.scrollTo(0, 0);
      Sesion.cargarEmpresa(); // por si cambió el nombre o el logo
    } catch (error) {
      Sesion.mostrarError(cajaErrorFormulario, error);
    } finally {
      boton.disabled = false;
      boton.textContent = 'Guardar configuración';
    }
  }
})();
