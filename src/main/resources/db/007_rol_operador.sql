-- =====================================================================
--  007 - Rol OPERADOR
--  Para alguien que ayuda con los cobros: ve el panel, los clientes,
--  la caja, registra pagos y retiros. No edita ni borra nada.
--  Como el ADMIN, no está ligado a un cliente (cliente_id NULL).
--
--  Ejecutar UNA sola vez en MySQL Workbench con el usuario root
--  (o avnadmin en Aiven), después de 006.
-- =====================================================================

USE wisp_db;

-- El CHECK que liga el rol con cliente_id se reemplaza: si no, MySQL rechazaría a un OPERADOR
ALTER TABLE usuarios DROP CHECK chk_usuario_rol;

ALTER TABLE usuarios
  MODIFY COLUMN rol ENUM('ADMIN','OPERADOR','CLIENTE') NOT NULL,
  ADD CONSTRAINT chk_usuario_rol CHECK (
    (rol IN ('ADMIN','OPERADOR') AND cliente_id IS NULL) OR
    (rol = 'CLIENTE'             AND cliente_id IS NOT NULL)
  );

-- Verificación rápida: el ENUM debe mostrar ADMIN, OPERADOR y CLIENTE
SHOW COLUMNS FROM usuarios WHERE Field = 'rol';
SELECT rol, COUNT(*) AS usuarios FROM usuarios GROUP BY rol;
