-- =====================================================================
--  005 - Integración con MikroTik (modelo "pull")
--  El MikroTik consulta a la web por HTTPS; la web nunca se conecta
--  al router. Este script crea TODO el modelo de las partes 1 a 5
--  (redes, colas, acciones, terminal remota y consumo mensual) para
--  no tener que ejecutar un script por cada parte.
--
--  Ejecutar UNA sola vez en MySQL Workbench con el usuario root
--  (o avnadmin en Aiven), después de 004.
-- =====================================================================

USE wisp_db;

-- ---------------------------------------------------------------------
-- 1. REDES: cada red es un MikroTik que consulta a la web.
--    El token del MikroTik NO se guarda: solo su hash SHA-256.
-- ---------------------------------------------------------------------
CREATE TABLE redes (
  id                    INT AUTO_INCREMENT PRIMARY KEY,
  nombre                VARCHAR(60)  NOT NULL UNIQUE,
  cola_padre            VARCHAR(40)  NOT NULL,               -- ej: "Total-Clientes" (NUNCA se modifica)
  colas_protegidas      VARCHAR(500) NULL,                   -- otras colas que nunca se tocan, separadas por coma
  intervalo_segundos    INT          NOT NULL DEFAULT 60,    -- cada cuánto consulta el MikroTik
  token_hash            CHAR(64)     NOT NULL UNIQUE,        -- SHA-256 del token (header X-Red-Token)
  modo                  ENUM('SOLO_LECTURA','CONTROL') NOT NULL DEFAULT 'SOLO_LECTURA',
  instalacion_pendiente BOOLEAN      NOT NULL DEFAULT TRUE,  -- el script "puente" descarga la instalación
  ultima_conexion       DATETIME     NULL,                   -- última consulta del MikroTik
  ultimo_reporte        DATETIME     NULL,                   -- último reporte de colas recibido
  ultima_ip             VARCHAR(45)  NULL,                   -- IP pública desde la que consultó
  creado_en             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_red_intervalo CHECK (intervalo_segundos BETWEEN 30 AND 3600)
);

-- ---------------------------------------------------------------------
-- 2. CLIENTES: a qué red pertenecen, nombre de su cola y corte manual.
--    cola_vigente impide que dos clientes vigentes de la misma red
--    usen la misma cola (igual que codigo_vigente con los códigos).
-- ---------------------------------------------------------------------
ALTER TABLE clientes
  ADD COLUMN red_id       INT         NULL AFTER ip,
  ADD COLUMN nombre_cola  VARCHAR(40) NULL AFTER red_id,      -- por defecto el código (ej: C-07)
  ADD COLUMN corte_manual BOOLEAN     NOT NULL DEFAULT FALSE AFTER nombre_cola,
  ADD COLUMN cola_vigente VARCHAR(60) GENERATED ALWAYS AS
    (IF(estado <> 'RETIRADO' AND red_id IS NOT NULL, CONCAT(red_id, '/', nombre_cola), NULL)) STORED,
  ADD CONSTRAINT fk_cliente_red   FOREIGN KEY (red_id) REFERENCES redes(id),
  ADD CONSTRAINT uk_cola_vigente  UNIQUE (cola_vigente),
  ADD CONSTRAINT chk_cliente_cola CHECK (red_id IS NULL OR nombre_cola IS NOT NULL);

-- ---------------------------------------------------------------------
-- 3. COLAS: estado DESEADO (lo que dice la web) y estado REPORTADO
--    (lo último que dijo el MikroTik). Una fila por cola gestionada.
--    Si el código pasa a otro cliente, la cola cambia de dueño.
-- ---------------------------------------------------------------------
CREATE TABLE colas (
  id                 INT AUTO_INCREMENT PRIMARY KEY,
  red_id             INT          NOT NULL,
  nombre             VARCHAR(40)  NOT NULL,
  cliente_id         INT          NOT NULL,                  -- dueño actual de la cola

  -- Estado deseado
  target             VARCHAR(18)  NOT NULL,                  -- ej: 192.168.1.11/32
  max_limit          VARCHAR(20)  NOT NULL,                  -- subida/bajada, ej: 5M/15M
  parent             VARCHAR(40)  NOT NULL,
  tipo_cola          VARCHAR(60)  NOT NULL,                  -- ej: fq-codel-up/fq-codel-down
  comentario         VARCHAR(100) NOT NULL,
  deshabilitada      BOOLEAN      NOT NULL,
  deseado_en         DATETIME     NOT NULL,

  -- Último reporte del MikroTik (NULL = todavía no reporta)
  rep_existe         BOOLEAN      NULL,
  rep_target         VARCHAR(60)  NULL,
  rep_max_limit      VARCHAR(40)  NULL,
  rep_parent         VARCHAR(60)  NULL,
  rep_tipo_cola      VARCHAR(80)  NULL,
  rep_comentario     VARCHAR(150) NULL,
  rep_deshabilitada  BOOLEAN      NULL,
  rate_subida_bps    BIGINT       NULL,
  rate_bajada_bps    BIGINT       NULL,
  bytes_subida       BIGINT       NULL,                      -- contador del MikroTik (se reinicia)
  bytes_bajada       BIGINT       NULL,
  ping_ok            BOOLEAN      NULL,
  reportado_en       DATETIME     NULL,

  CONSTRAINT fk_cola_red     FOREIGN KEY (red_id)     REFERENCES redes(id),
  CONSTRAINT fk_cola_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  CONSTRAINT uk_cola_nombre  UNIQUE (red_id, nombre)
);

-- ---------------------------------------------------------------------
-- 4. ACCIONES: "deja esta cola como dice la web". Se generan solo en
--    modo CONTROL. El MikroTik confirma si se aplicó o el error.
--    Si hay una acción nueva para la misma cola, la anterior pendiente
--    queda REEMPLAZADA.
-- ---------------------------------------------------------------------
CREATE TABLE acciones_cola (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  cola_id      INT          NOT NULL,
  motivo       ENUM('CAMBIO','CORTE','RECONEXION','SINCRONIZACION') NOT NULL,
  estado       ENUM('PENDIENTE','ENVIADA','APLICADA','ERROR','REEMPLAZADA') NOT NULL DEFAULT 'PENDIENTE',
  error        VARCHAR(500) NULL,
  creado_por   INT          NULL,                            -- NULL = generada por el sistema
  creado_en    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  enviada_en   DATETIME     NULL,
  resuelta_en  DATETIME     NULL,
  CONSTRAINT fk_accion_cola    FOREIGN KEY (cola_id)    REFERENCES colas(id),
  CONSTRAINT fk_accion_usuario FOREIGN KEY (creado_por) REFERENCES usuarios(id)
);

CREATE INDEX idx_acciones_estado ON acciones_cola (estado);

-- ---------------------------------------------------------------------
-- 5. TERMINAL REMOTA: comandos que un ADMIN encola y el MikroTik
--    ejecuta UNA sola vez en su siguiente consulta.
--    Si no se recoge a tiempo, EXPIRA (no se ejecuta horas después).
-- ---------------------------------------------------------------------
CREATE TABLE comandos_terminal (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  red_id           INT           NOT NULL,
  comando          VARCHAR(2000) NOT NULL,
  estado           ENUM('PENDIENTE','ENVIADO','EJECUTADO','ERROR','EXPIRADO','CANCELADO') NOT NULL DEFAULT 'PENDIENTE',
  salida           TEXT          NULL,                       -- limitada por la aplicación
  salida_truncada  BOOLEAN       NOT NULL DEFAULT FALSE,
  creado_por       INT           NOT NULL,
  creado_en        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expira_en        DATETIME      NOT NULL,
  enviado_en       DATETIME      NULL,
  finalizado_en    DATETIME      NULL,
  CONSTRAINT fk_comando_red     FOREIGN KEY (red_id)     REFERENCES redes(id),
  CONSTRAINT fk_comando_usuario FOREIGN KEY (creado_por) REFERENCES usuarios(id)
);

CREATE INDEX idx_comandos_red_estado ON comandos_terminal (red_id, estado);

-- ---------------------------------------------------------------------
-- 6. CONSUMO POR MES de cada cliente (suma de lo que reporta el MikroTik)
-- ---------------------------------------------------------------------
CREATE TABLE consumo_mensual (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  cliente_id      INT      NOT NULL,
  periodo         DATE     NOT NULL,                         -- primer día del mes
  bytes_subida    BIGINT   NOT NULL DEFAULT 0,
  bytes_bajada    BIGINT   NOT NULL DEFAULT 0,
  actualizado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_consumo_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  CONSTRAINT uk_consumo_mes     UNIQUE (cliente_id, periodo),
  CONSTRAINT chk_consumo_periodo CHECK (DAY(periodo) = 1)
);

-- Verificación rápida: deben aparecer las 5 tablas nuevas y las columnas en clientes
SHOW TABLES;
SHOW COLUMNS FROM clientes WHERE Field IN ('red_id', 'nombre_cola', 'corte_manual', 'cola_vigente');
