-- =====================================================================
--  003 - Usuarios: fecha del último cambio de contraseña
--  Ejecutar UNA sola vez en MySQL Workbench con el usuario root,
--  después de 002_clientes_zona_celular.sql.
--
--  Sirve para invalidar los tokens (sesiones) emitidos ANTES de que el
--  usuario cambie su contraseña o el admin se la restablezca.
--  NULL = nunca se ha cambiado desde que existe esta columna.
-- =====================================================================

USE wisp_db;

ALTER TABLE usuarios
  ADD COLUMN password_cambiado_en DATETIME NULL AFTER password_hash;

-- Verificación rápida: debe aparecer la columna, con Null = YES
SHOW COLUMNS FROM usuarios WHERE Field = 'password_cambiado_en';
