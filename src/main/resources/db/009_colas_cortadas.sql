-- =====================================================================
--  009 - Corte real del servicio (address-list "wisp-cortados")
--  colas.cortada:     la web quiere la IP del cliente en wisp-cortados
--                     (SUSPENDIDO, con corte manual o RETIRADO).
--  colas.rep_cortada: lo que informó el MikroTik (NULL = su script aún
--                     no informa la lista).
--
--  Ejecutar en MySQL Workbench con el usuario root (o avnadmin en
--  Aiven), después de 008. Se puede ejecutar dos veces sin error.
--  La web calcula sola el valor de "cortada" en la próxima consulta
--  de cada MikroTik.
-- =====================================================================

USE wisp_db;

DROP PROCEDURE IF EXISTS colas_cortadas_009;
DELIMITER $$
CREATE PROCEDURE colas_cortadas_009()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colas' AND COLUMN_NAME = 'cortada') THEN
    ALTER TABLE colas ADD COLUMN cortada BOOLEAN NOT NULL DEFAULT FALSE AFTER deshabilitada;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colas' AND COLUMN_NAME = 'rep_cortada') THEN
    ALTER TABLE colas ADD COLUMN rep_cortada BOOLEAN NULL AFTER rep_deshabilitada;
  END IF;
END$$
DELIMITER ;

CALL colas_cortadas_009();
DROP PROCEDURE colas_cortadas_009;

-- Verificación: deben aparecer "cortada" y "rep_cortada"
SHOW COLUMNS FROM colas WHERE Field IN ('deshabilitada', 'cortada', 'rep_deshabilitada', 'rep_cortada');
