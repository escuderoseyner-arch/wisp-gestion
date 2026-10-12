-- =====================================================================
--  008 - Separar la información por red (Shiaunto y Cuypirmarca son
--  negocios distintos)
--   1. usuario_redes: qué redes ve cada OPERADOR (el ADMIN ve todas).
--   2. planes.red_id: NULL = plan disponible en todas las redes.
--      Los planes que ya existen quedan asignados a Shiaunto.
--      El nombre del plan pasa a ser único POR RED.
--   3. redes.prefijo_codigo: prefijo de los códigos de sus clientes
--      (Shiaunto "C-", Cuypirmarca "CP-").
--
--  Ejecutar en MySQL Workbench con el usuario root (o avnadmin en
--  Aiven), después de 007. Se puede ejecutar dos veces sin error:
--  cada paso revisa si ya está hecho.
-- =====================================================================

USE wisp_db;

-- ---------------------------------------------------------------------
-- 0. RED DE SHIAUNTO (en Aiven es la 1). Revisa: SELECT id, nombre FROM redes;
-- ---------------------------------------------------------------------
SET @red_shiaunto_id = 1;

SELECT id, nombre FROM redes;

DROP PROCEDURE IF EXISTS separar_por_red_008;
DELIMITER $$
CREATE PROCEDURE separar_por_red_008(IN red_shiaunto INT)
BEGIN
  IF red_shiaunto IS NULL OR NOT EXISTS (SELECT 1 FROM redes WHERE id = red_shiaunto) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
      'No existe la red de @red_shiaunto_id. Revisa SELECT id, nombre FROM redes; y corrige @red_shiaunto_id al inicio del script. No se cambio nada.';
  END IF;

  -- 1. Redes asignadas a cada OPERADOR
  CREATE TABLE IF NOT EXISTS usuario_redes (
    usuario_id  INT NOT NULL,
    red_id      INT NOT NULL,
    PRIMARY KEY (usuario_id, red_id),
    CONSTRAINT fk_usuario_redes_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_usuario_redes_red     FOREIGN KEY (red_id)     REFERENCES redes(id)
  );

  -- 2. Red del plan. Solo la PRIMERA vez se asignan los planes existentes a Shiaunto
  --    (si se ejecuta de nuevo, no toca los planes "de todas las redes" creados después).
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planes' AND COLUMN_NAME = 'red_id') THEN
    ALTER TABLE planes
      ADD COLUMN red_id INT NULL AFTER id,
      ADD CONSTRAINT fk_plan_red FOREIGN KEY (red_id) REFERENCES redes(id);
    UPDATE planes SET red_id = red_shiaunto;
  END IF;

  -- Columna auxiliar para el UNIQUE: en MySQL un UNIQUE con NULL permite repetidos,
  -- así que los planes "de todas las redes" (NULL) se comparan como red 0.
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planes' AND COLUMN_NAME = 'red_unica') THEN
    ALTER TABLE planes ADD COLUMN red_unica INT AS (IFNULL(red_id, 0)) STORED AFTER red_id;
  END IF;

  -- Se quita el UNIQUE antiguo de "nombre" (se llama "nombre" porque se creó dentro de la columna)
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planes' AND INDEX_NAME = 'nombre') THEN
    ALTER TABLE planes DROP INDEX nombre;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planes' AND INDEX_NAME = 'uk_plan_red_nombre') THEN
    ALTER TABLE planes ADD CONSTRAINT uk_plan_red_nombre UNIQUE (red_unica, nombre);
  END IF;

  -- 3. Prefijo de los códigos de cada red. Solo la PRIMERA vez: todas "C-" y
  --    la red cuyo nombre contenga "cuypirmarca" pasa a "CP-" (después se cambia en la pantalla Redes).
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'redes' AND COLUMN_NAME = 'prefijo_codigo') THEN
    ALTER TABLE redes ADD COLUMN prefijo_codigo VARCHAR(5) NOT NULL DEFAULT 'C-' AFTER nombre;
    UPDATE redes SET prefijo_codigo = 'CP-'
      WHERE REPLACE(LOWER(nombre), ' ', '') LIKE '%cuypirmarca%' AND id <> red_shiaunto;
  END IF;
END$$
DELIMITER ;

CALL separar_por_red_008(@red_shiaunto_id);
DROP PROCEDURE separar_por_red_008;

-- ---------------------------------------------------------------------
-- VERIFICACIÓN (revisa estos resultados y avísale a Joaquín / al sistema)
-- ---------------------------------------------------------------------

-- Redes con su prefijo
SELECT id, nombre, prefijo_codigo FROM redes ORDER BY id;

-- Planes con su red (NULL = todas las redes)
SELECT p.id, p.nombre, p.red_id, r.nombre AS red, p.activo FROM planes p LEFT JOIN redes r ON r.id = p.red_id ORDER BY p.red_id, p.nombre;

-- Clientes vigentes SIN red: ningún operador los ve. Asígnales su red en Clientes → Editar.
SELECT id, codigo, nombres FROM clientes WHERE red_id IS NULL AND estado <> 'RETIRADO' ORDER BY codigo;

-- Clientes vigentes cuyo plan es de OTRA red (ej: clientes de Cuypirmarca con un plan que quedó en Shiaunto).
-- Pueden seguir con ese plan, pero conviene crear el plan en su red y cambiárselo.
SELECT c.id, c.codigo, c.nombres, rc.nombre AS red_cliente, p.nombre AS plan, rp.nombre AS red_plan
FROM clientes c
JOIN planes p ON p.id = c.plan_id
LEFT JOIN redes rc ON rc.id = c.red_id
LEFT JOIN redes rp ON rp.id = p.red_id
WHERE c.estado <> 'RETIRADO' AND p.red_id IS NOT NULL AND (c.red_id IS NULL OR c.red_id <> p.red_id);

-- Operadores y sus redes (al inicio no tienen ninguna: asígnalas en Admins)
SELECT u.username, GROUP_CONCAT(r.nombre ORDER BY r.nombre) AS redes
FROM usuarios u LEFT JOIN usuario_redes ur ON ur.usuario_id = u.id LEFT JOIN redes r ON r.id = ur.red_id
WHERE u.rol = 'OPERADOR' GROUP BY u.id, u.username;
