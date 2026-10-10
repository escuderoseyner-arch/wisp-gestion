-- =====================================================================
--  006 - Caja por red
--  Cada red puede tener una caja: sube sola con los pagos de sus
--  clientes, baja sola con una cuota mensual (préstamo) y admite
--  retiros manuales. El saldo NO se guarda: es la suma de los
--  movimientos, así nunca se descuadra.
--
--  Ejecutar en MySQL Workbench con el usuario root (o avnadmin en
--  Aiven), después de 005. Se puede volver a ejecutar sin duplicar
--  nada: crea solo lo que falte.
--  Requisito: la red de Shiaunto ya debe existir (pantalla Redes).
-- =====================================================================

USE wisp_db;

-- ---------------------------------------------------------------------
-- 0. BUSCAR LA RED (antes de crear o cambiar nada)
--    Se busca una sola red cuyo nombre contenga "shiaunto" (sin
--    importar mayúsculas ni espacios). Si no la encuentra, o encuentra
--    más de una, pon su id a mano aquí (ver: SELECT id, nombre FROM redes;)
-- ---------------------------------------------------------------------
SET @red_id_manual = NULL;   -- ej: SET @red_id_manual = 1;

SELECT id, nombre FROM redes;

SET @red_shiaunto = COALESCE(
  (SELECT id FROM redes WHERE id = @red_id_manual),
  (SELECT IF(COUNT(*) = 1, MIN(id), NULL) FROM redes
    WHERE REPLACE(LOWER(nombre), ' ', '') LIKE '%shiaunto%'));

-- Detiene el script con un mensaje claro si no se encontró la red
DROP PROCEDURE IF EXISTS verificar_red_006;
DELIMITER $$
CREATE PROCEDURE verificar_red_006(IN red INT)
BEGIN
  IF red IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
      'No se encontro la red Shiaunto (o hay varias). Revisa SELECT id, nombre FROM redes; y pon su id en @red_id_manual. No se cambio nada.';
  END IF;
END$$
DELIMITER ;

CALL verificar_red_006(@red_shiaunto);
DROP PROCEDURE verificar_red_006;

SELECT @red_shiaunto AS red_shiaunto, (SELECT nombre FROM redes WHERE id = @red_shiaunto) AS nombre;

-- ---------------------------------------------------------------------
-- 1. CAJAS: una por red, con la configuración del descuento mensual.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cajas (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  red_id            INT          NOT NULL,
  descuento_monto   DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  descuento_dia     TINYINT      NOT NULL DEFAULT 1,       -- día del mes en que se descuenta
  descuento_activo  BOOLEAN      NOT NULL DEFAULT FALSE,
  descuento_desde   DATE         NOT NULL,                 -- primer mes en que se descuenta
  creado_en         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_caja_red          FOREIGN KEY (red_id) REFERENCES redes(id),
  CONSTRAINT uk_caja_red          UNIQUE (red_id),
  CONSTRAINT chk_caja_dia         CHECK (descuento_dia BETWEEN 1 AND 28),
  CONSTRAINT chk_caja_monto       CHECK (descuento_monto >= 0)
);

-- ---------------------------------------------------------------------
-- 2. MOVIMIENTOS: monto siempre positivo; el tipo dice si suma o resta.
--    periodo_descuento impide dos descuentos del mismo mes en la misma
--    caja (igual que codigo_vigente con los códigos de cliente).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS caja_movimientos (
  id                 INT AUTO_INCREMENT PRIMARY KEY,
  caja_id            INT          NOT NULL,
  tipo               ENUM('INGRESO_PAGO','DESCUENTO_MENSUAL','RETIRO') NOT NULL,
  monto              DECIMAL(8,2) NOT NULL,
  fecha              DATE         NOT NULL,
  descripcion        VARCHAR(255) NOT NULL,
  pago_id            INT          NULL,                    -- solo INGRESO_PAGO
  periodo            DATE         NULL,                    -- solo DESCUENTO_MENSUAL: primer día del mes
  usuario_id         INT          NULL,                    -- NULL = automático
  creado_en          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  periodo_descuento  DATE GENERATED ALWAYS AS
    (IF(tipo = 'DESCUENTO_MENSUAL', periodo, NULL)) STORED,
  CONSTRAINT fk_movimiento_caja    FOREIGN KEY (caja_id)    REFERENCES cajas(id),
  CONSTRAINT fk_movimiento_pago    FOREIGN KEY (pago_id)    REFERENCES pagos(id),
  CONSTRAINT fk_movimiento_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT uk_movimiento_pago    UNIQUE (pago_id),
  CONSTRAINT uk_movimiento_descuento UNIQUE (caja_id, periodo_descuento),
  CONSTRAINT chk_movimiento_monto  CHECK (monto > 0),
  CONSTRAINT chk_movimiento_periodo CHECK (periodo IS NULL OR DAY(periodo) = 1),
  INDEX idx_movimientos_caja_fecha (caja_id, fecha, id)
);

-- ---------------------------------------------------------------------
-- 3. DATOS DE SHIAUNTO
-- ---------------------------------------------------------------------

-- 3.1 Clientes C-01 a C-17 y los retirados que todavía no tengan red.
--     Los que ya tienen una red asignada no se tocan. La cola se llama como el código.
UPDATE clientes
SET red_id = @red_shiaunto,
    nombre_cola = COALESCE(nombre_cola, codigo)
WHERE red_id IS NULL
  AND (estado = 'RETIRADO'
       OR codigo IN ('C-01','C-02','C-03','C-04','C-05','C-06','C-07','C-08','C-09',
                     'C-10','C-11','C-12','C-13','C-14','C-15','C-16','C-17'));

-- 3.2 La caja: 180.00 el día 27 de cada mes, desde octubre de 2026 (si aún no existe)
INSERT INTO cajas (red_id, descuento_monto, descuento_dia, descuento_activo, descuento_desde)
SELECT @red_shiaunto, 180.00, 27, TRUE, '2026-10-01'
WHERE NOT EXISTS (SELECT 1 FROM cajas WHERE red_id = @red_shiaunto);

SET @caja_shiaunto = (SELECT id FROM cajas WHERE red_id = @red_shiaunto);

-- 3.3 Un ingreso por cada pago de los clientes de Shiaunto (también retirados) que aún no lo tenga.
--     usuario_id = el admin que registró el pago.
INSERT INTO caja_movimientos (caja_id, tipo, monto, fecha, descripcion, pago_id, usuario_id)
SELECT @caja_shiaunto, 'INGRESO_PAGO', p.monto, p.fecha_pago,
       CONCAT('Pago ', c.codigo, ' - ', c.nombres, ' (', DATE_FORMAT(p.periodo, '%m/%Y'), ')'),
       p.id, p.registrado_por
FROM pagos p
JOIN clientes c ON c.id = p.cliente_id
WHERE c.red_id = @red_shiaunto
  AND NOT EXISTS (SELECT 1 FROM caja_movimientos m WHERE m.pago_id = p.id)
ORDER BY p.fecha_pago, p.id;

-- Verificación rápida: la caja, cuántos ingresos tiene y el saldo, y la red de cada cliente
SELECT * FROM cajas;
SELECT COUNT(*) AS ingresos, SUM(monto) AS total_ingresos
FROM caja_movimientos WHERE caja_id = @caja_shiaunto AND tipo = 'INGRESO_PAGO';
SELECT codigo, estado, red_id, nombre_cola FROM clientes ORDER BY codigo, estado;
