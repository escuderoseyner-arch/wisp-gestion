-- =====================================================================
--  004 - Código de país para WhatsApp y pagos de ejemplo
--  Ejecutar UNA sola vez en MySQL Workbench con el usuario root,
--  después de 003_usuarios_password_cambiado_en.sql.
--
--  IMPORTANTE: antes de ejecutarlo, la app debe haber arrancado al menos
--  una vez, porque los pagos de ejemplo usan al ADMIN como "registrado_por".
-- =====================================================================

USE wisp_db;

-- ---------------------------------------------------------------------
-- 1. Código de país para los enlaces wa.me (51 = Perú).
--    wa.me necesita el número completo: código de país + celular.
-- ---------------------------------------------------------------------
ALTER TABLE configuracion
  ADD COLUMN codigo_pais VARCHAR(4) NOT NULL DEFAULT '51' AFTER whatsapp_soporte;

-- ---------------------------------------------------------------------
-- 2. Pagos de ejemplo (DEMO) para que el panel muestre datos:
--    C-01: al día (julio, agosto y septiembre pagados)
--    C-02: solo julio pagado -> agosto y septiembre VENCIDOS
--    C-03: agosto pagado     -> septiembre VENCIDO
--    El monto es el precio actual de su plan. Si un cliente demo ya fue
--    retirado o ese mes ya está pagado, esa fila simplemente se omite.
-- ---------------------------------------------------------------------
SET @admin = (SELECT id FROM usuarios WHERE rol = 'ADMIN' ORDER BY id LIMIT 1);

-- Si @admin sale NULL, no existe ningún ADMIN: arranca la app una vez y vuelve a ejecutar esta parte
SELECT @admin AS admin_que_registra;

INSERT INTO pagos (cliente_id, periodo, monto, metodo, fecha_pago, registrado_por, observacion)
SELECT c.id, d.periodo, pl.precio, d.metodo, d.fecha_pago, @admin, 'Pago de ejemplo'
FROM (
          SELECT 'C-01' AS codigo, DATE '2026-07-01' AS periodo, 'YAPE'          AS metodo, DATE '2026-07-26' AS fecha_pago
    UNION SELECT 'C-01',           DATE '2026-08-01',            'YAPE',                    DATE '2026-08-27'
    UNION SELECT 'C-01',           DATE '2026-09-01',            'EFECTIVO',                DATE '2026-09-25'
    UNION SELECT 'C-02',           DATE '2026-07-01',            'PLIN',                    DATE '2026-07-15'
    UNION SELECT 'C-03',           DATE '2026-08-01',            'TRANSFERENCIA',           DATE '2026-08-10'
) AS d
JOIN clientes c ON c.codigo_vigente = d.codigo
JOIN planes pl ON pl.id = c.plan_id
ON DUPLICATE KEY UPDATE pagos.id = pagos.id;   -- si ese mes ya estaba pagado, no hace nada

-- Verificación rápida
SELECT codigo_pais FROM configuracion;
SELECT c.codigo, p.periodo, p.monto, p.metodo, p.fecha_pago
FROM pagos p JOIN clientes c ON c.id = p.cliente_id
ORDER BY c.codigo, p.periodo;
