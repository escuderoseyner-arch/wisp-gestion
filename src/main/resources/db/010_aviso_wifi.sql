-- =====================================================================
--  010 - Aviso de pago por Wi-Fi (address-list "wisp-aviso" + proxy del MikroTik)
--
--  redes.aviso_activo            la red muestra el aviso de pago y la página "Servicio suspendido"
--  clientes.sin_aviso            casilla "No mostrar aviso" del cliente
--  clientes.aviso_pospuesto_hasta  tocó "Entendido": no vuelve a ver el aviso hasta esta hora
--  colas.aviso                   la web quiere su IP en wisp-aviso (aviso de pago)
--  colas.portal                  la web quiere su regla en el proxy (aviso o suspendido)
--  colas.portal_token_hash       SHA-256 del token de su página de aviso (el token nunca se guarda)
--  colas.rep_aviso               lo que informó el MikroTik (NULL = su script aún no informa la lista)
--  configuracion.aviso_texto_*   textos de la página de aviso ({nombre}, {monto}, {mes})
--
--  Ejecutar en MySQL Workbench con el usuario root (o avnadmin en
--  Aiven), después de 009. Se puede ejecutar dos veces sin error.
--  Todas las redes quedan con el aviso DESACTIVADO.
-- =====================================================================

USE wisp_db;

DROP PROCEDURE IF EXISTS aviso_wifi_010;
DELIMITER $$
CREATE PROCEDURE aviso_wifi_010()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'redes' AND COLUMN_NAME = 'aviso_activo') THEN
    ALTER TABLE redes ADD COLUMN aviso_activo BOOLEAN NOT NULL DEFAULT FALSE AFTER instalacion_pendiente;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clientes' AND COLUMN_NAME = 'sin_aviso') THEN
    ALTER TABLE clientes ADD COLUMN sin_aviso BOOLEAN NOT NULL DEFAULT FALSE AFTER corte_manual;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clientes' AND COLUMN_NAME = 'aviso_pospuesto_hasta') THEN
    ALTER TABLE clientes ADD COLUMN aviso_pospuesto_hasta DATETIME NULL AFTER sin_aviso;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colas' AND COLUMN_NAME = 'aviso') THEN
    ALTER TABLE colas ADD COLUMN aviso BOOLEAN NOT NULL DEFAULT FALSE AFTER cortada;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colas' AND COLUMN_NAME = 'portal') THEN
    ALTER TABLE colas ADD COLUMN portal BOOLEAN NOT NULL DEFAULT FALSE AFTER aviso;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colas' AND COLUMN_NAME = 'portal_token_hash') THEN
    ALTER TABLE colas ADD COLUMN portal_token_hash CHAR(64) NULL AFTER portal;
    ALTER TABLE colas ADD CONSTRAINT uk_cola_portal_token UNIQUE (portal_token_hash);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colas' AND COLUMN_NAME = 'rep_aviso') THEN
    ALTER TABLE colas ADD COLUMN rep_aviso BOOLEAN NULL AFTER rep_cortada;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'configuracion' AND COLUMN_NAME = 'aviso_texto_suave') THEN
    ALTER TABLE configuracion ADD COLUMN aviso_texto_suave VARCHAR(500) NOT NULL
      DEFAULT 'Hola {nombre}, te recordamos con cariño que ya llegó tu fecha de pago de internet ({mes}): {monto}. Si ya pagaste, envíanos tu comprobante. ¡Gracias por tu preferencia!'
      AFTER plantilla_recordatorio;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'configuracion' AND COLUMN_NAME = 'aviso_texto_vencido') THEN
    ALTER TABLE configuracion ADD COLUMN aviso_texto_vencido VARCHAR(500) NOT NULL
      DEFAULT 'Hola {nombre}, tu pago de internet de {mes} ya venció. Debes {monto}. Por favor paga pronto para que tu servicio no se suspenda. Si ya pagaste, envíanos tu comprobante.'
      AFTER aviso_texto_suave;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'configuracion' AND COLUMN_NAME = 'aviso_texto_suspendido') THEN
    ALTER TABLE configuracion ADD COLUMN aviso_texto_suspendido VARCHAR(500) NOT NULL
      DEFAULT 'Hola {nombre}, tu servicio de internet está suspendido. Debes {monto} ({mes}). Paga y envíanos tu comprobante por WhatsApp para volver a activarlo.'
      AFTER aviso_texto_vencido;
  END IF;
END$$
DELIMITER ;

CALL aviso_wifi_010();
DROP PROCEDURE aviso_wifi_010;

-- Verificación: deben aparecer todas las columnas nuevas
SHOW COLUMNS FROM redes WHERE Field = 'aviso_activo';
SHOW COLUMNS FROM clientes WHERE Field IN ('sin_aviso', 'aviso_pospuesto_hasta');
SHOW COLUMNS FROM colas WHERE Field IN ('aviso', 'portal', 'portal_token_hash', 'rep_aviso');
SHOW COLUMNS FROM configuracion WHERE Field LIKE 'aviso_texto_%';
