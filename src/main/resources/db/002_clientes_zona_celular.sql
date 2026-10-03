-- =====================================================================
--  002 - Clientes: celular opcional y zona
--  Ejecutar UNA sola vez en MySQL Workbench con el usuario root,
--  después de wisp_db.sql.
-- =====================================================================

USE wisp_db;

-- ---------------------------------------------------------------------
-- 1. Celular opcional: hay clientes que no tienen número.
--    Se mantiene el mismo tipo VARCHAR(15); solo cambia NOT NULL -> NULL.
-- ---------------------------------------------------------------------
ALTER TABLE clientes
  MODIFY celular VARCHAR(15) NULL;

-- ---------------------------------------------------------------------
-- 2. Zona del cliente (caserío, sector o barrio), para filtrar y
--    organizar los cobros. Opcional.
-- ---------------------------------------------------------------------
ALTER TABLE clientes
  ADD COLUMN zona VARCHAR(50) NULL AFTER referencia;

CREATE INDEX idx_clientes_zona ON clientes (zona);

-- ---------------------------------------------------------------------
-- 3. Zonas para los clientes de ejemplo (DEMO, nombres inventados).
--    Workbench trae activado el "modo seguro", que bloquea los UPDATE
--    sin la clave primaria en el WHERE; se apaga solo para esta parte.
-- ---------------------------------------------------------------------
SET SQL_SAFE_UPDATES = 0;

UPDATE clientes SET zona = 'Zona Norte'  WHERE codigo = 'C-01';
UPDATE clientes SET zona = 'Zona Norte'  WHERE codigo = 'C-02';
UPDATE clientes SET zona = 'Zona Centro' WHERE codigo = 'C-03';

SET SQL_SAFE_UPDATES = 1;

-- Verificación rápida: celular debe decir "YES" en Null y debe aparecer zona
SHOW COLUMNS FROM clientes WHERE Field IN ('celular', 'zona');
SELECT codigo, nombres, celular, zona, estado FROM clientes;
