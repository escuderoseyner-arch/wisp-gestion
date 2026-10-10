-- =====================================================================
--  SISTEMA DE GESTIÓN PARA PROVEEDORES DE INTERNET (WISP)
--  INSTALACIÓN COMPLETA para una base de datos NUEVA (MySQL 8.x)
--
--  Equivale a wisp_db.sql + 002 + 003 + 004 + 005 + 006, con los cambios ya
--  integrados en cada tabla (sin los pagos de ejemplo).
--  Si tu base ya existe, NO uses este archivo: usa los scripts 002-006.
--
--  Ejecutar UNA sola vez con el usuario administrador del servidor
--  (root en local; avnadmin en Aiven).
-- =====================================================================

CREATE DATABASE IF NOT EXISTS wisp_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;   -- utf8mb4: soporta tildes, ñ y emojis

USE wisp_db;

-- ---------------------------------------------------------------------
-- 0. CONFIGURACIÓN DE LA EMPRESA (una sola fila)
-- ---------------------------------------------------------------------
CREATE TABLE configuracion (
  id                  TINYINT      PRIMARY KEY DEFAULT 1,
  nombre_empresa      VARCHAR(100) NOT NULL,
  ruc                 VARCHAR(11)  NULL,
  logo_url            VARCHAR(255) NULL,
  whatsapp_soporte    VARCHAR(15)  NOT NULL,           -- a dónde llegan los reportes de falla
  codigo_pais         VARCHAR(4)   NOT NULL DEFAULT '51', -- para los enlaces wa.me (51 = Perú)
  yape_numero         VARCHAR(15)  NULL,
  yape_titular        VARCHAR(100) NULL,
  cuenta_bancaria     VARCHAR(150) NULL,
  dias_tolerancia     TINYINT      NOT NULL DEFAULT 3, -- días de gracia antes de marcar vencido
  moneda              VARCHAR(5)   NOT NULL DEFAULT 'S/',
  plantilla_recordatorio VARCHAR(500) NOT NULL,        -- mensaje de WhatsApp con {nombre}, {monto}, {mes}
  actualizado_en      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_config_unica CHECK (id = 1),          -- impide crear una segunda fila
  CONSTRAINT chk_tolerancia   CHECK (dias_tolerancia BETWEEN 0 AND 15)
);

-- ---------------------------------------------------------------------
-- 1. PLANES
-- ---------------------------------------------------------------------
CREATE TABLE planes (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  nombre        VARCHAR(50)  NOT NULL UNIQUE,
  bajada_mbps   INT          NOT NULL,
  subida_mbps   INT          NOT NULL,
  precio        DECIMAL(8,2) NOT NULL,
  activo        BOOLEAN      NOT NULL DEFAULT TRUE,   -- para "retirar" un plan sin borrarlo
  creado_en     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_plan_precio    CHECK (precio > 0),
  CONSTRAINT chk_plan_velocidad CHECK (bajada_mbps > 0 AND subida_mbps > 0)
);

-- ---------------------------------------------------------------------
-- 1b. REDES (MikroTik). Solo se guarda el hash SHA-256 del token.
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
-- 2. CLIENTES
--    Un código (C-07) puede pasar a otra persona: el anterior queda
--    RETIRADO con su historial y se crea un registro nuevo.
-- ---------------------------------------------------------------------
CREATE TABLE clientes (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  codigo        VARCHAR(10)  NOT NULL,                -- ej: C-01
  nombres       VARCHAR(100) NOT NULL,
  celular       VARCHAR(15)  NULL,                    -- opcional: hay clientes sin número
  referencia    VARCHAR(200) NULL,                    -- "casa azul frente a la plaza"
  zona          VARCHAR(50)  NULL,                    -- caserío, sector o barrio
  plan_id       INT          NOT NULL,
  dia_pago      TINYINT      NOT NULL,                -- día del mes en que vence
  fecha_inicio  DATE         NOT NULL,
  fecha_retiro  DATE         NULL,
  ip            VARCHAR(15)  NULL,
  red_id        INT          NULL,                    -- MikroTik al que pertenece
  nombre_cola   VARCHAR(40)  NULL,                    -- por defecto el código (ej: C-07)
  corte_manual  BOOLEAN      NOT NULL DEFAULT FALSE,
  estado        ENUM('ACTIVO','SUSPENDIDO','RETIRADO') NOT NULL DEFAULT 'ACTIVO',
  creado_en     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- Vale el código si el cliente NO está retirado, y NULL si lo está.
  -- Como UNIQUE permite varios NULL, solo hay UN cliente vigente por código.
  codigo_vigente VARCHAR(10) GENERATED ALWAYS AS
    (IF(estado <> 'RETIRADO', codigo, NULL)) STORED,
  -- Igual con las colas: una sola cola vigente por nombre en cada red.
  cola_vigente VARCHAR(60) GENERATED ALWAYS AS
    (IF(estado <> 'RETIRADO' AND red_id IS NOT NULL, CONCAT(red_id, '/', nombre_cola), NULL)) STORED,

  CONSTRAINT fk_cliente_plan   FOREIGN KEY (plan_id) REFERENCES planes(id),
  CONSTRAINT fk_cliente_red    FOREIGN KEY (red_id)  REFERENCES redes(id),
  CONSTRAINT uk_codigo_vigente UNIQUE (codigo_vigente),
  CONSTRAINT uk_cola_vigente   UNIQUE (cola_vigente),
  CONSTRAINT chk_cliente_cola  CHECK (red_id IS NULL OR nombre_cola IS NOT NULL),
  -- Hasta el 28 para que exista en todos los meses (febrero incluido)
  CONSTRAINT chk_dia_pago      CHECK (dia_pago BETWEEN 1 AND 28),
  CONSTRAINT chk_retiro        CHECK (
    (estado = 'RETIRADO' AND fecha_retiro IS NOT NULL) OR
    (estado <> 'RETIRADO' AND fecha_retiro IS NULL)
  )
);

CREATE INDEX idx_clientes_estado ON clientes (estado);
CREATE INDEX idx_clientes_zona   ON clientes (zona);

-- ---------------------------------------------------------------------
-- 3. USUARIOS (login). La contraseña se guarda cifrada con BCrypt.
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
  id                    INT AUTO_INCREMENT PRIMARY KEY,
  username              VARCHAR(50)  NOT NULL UNIQUE,
  email                 VARCHAR(100) NULL UNIQUE,
  password_hash         VARCHAR(100) NOT NULL,
  password_cambiado_en  DATETIME     NULL,           -- los tokens anteriores a esta fecha dejan de valer
  rol                   ENUM('ADMIN','CLIENTE') NOT NULL,
  cliente_id            INT          NULL UNIQUE,      -- solo si es CLIENTE
  nombre_mostrar        VARCHAR(100) NOT NULL,
  activo                BOOLEAN      NOT NULL DEFAULT TRUE,
  debe_cambiar_password BOOLEAN      NOT NULL DEFAULT TRUE,  -- obliga a cambiar la clave inicial
  intentos_fallidos     INT          NOT NULL DEFAULT 0,     -- (ya no se usa: ver ControlIntentosLogin)
  bloqueado_hasta       DATETIME     NULL,                   -- (ya no se usa: ver ControlIntentosLogin)
  ultimo_acceso         DATETIME     NULL,
  creado_en             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_usuario_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  -- Un ADMIN no tiene cliente asociado; un CLIENTE siempre tiene uno
  CONSTRAINT chk_usuario_rol CHECK (
    (rol = 'ADMIN'   AND cliente_id IS NULL) OR
    (rol = 'CLIENTE' AND cliente_id IS NOT NULL)
  )
);

-- ---------------------------------------------------------------------
-- 4. RECUPERACIÓN DE CONTRASEÑA (se guarda el HASH del token)
-- ---------------------------------------------------------------------
CREATE TABLE tokens_recuperacion (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id  INT       NOT NULL,
  token_hash  CHAR(64)  NOT NULL UNIQUE,   -- SHA-256 del token enviado por correo
  expira_en   DATETIME  NOT NULL,
  usado_en    DATETIME  NULL,              -- un token solo se puede usar una vez
  creado_en   DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- ---------------------------------------------------------------------
-- 5. PAGOS ("periodo" = primer día del mes que se paga)
-- ---------------------------------------------------------------------
CREATE TABLE pagos (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  cliente_id      INT          NOT NULL,
  periodo         DATE         NOT NULL,
  monto           DECIMAL(8,2) NOT NULL,
  metodo          ENUM('YAPE','PLIN','TRANSFERENCIA','EFECTIVO','OTRO') NOT NULL,
  fecha_pago      DATE         NOT NULL,
  registrado_por  INT          NOT NULL,              -- qué admin lo registró
  observacion     VARCHAR(255) NULL,
  creado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_pago_cliente  FOREIGN KEY (cliente_id)     REFERENCES clientes(id),
  CONSTRAINT fk_pago_usuario  FOREIGN KEY (registrado_por) REFERENCES usuarios(id),
  -- Evita registrar dos veces el mismo mes del mismo cliente
  CONSTRAINT uk_pago_mes      UNIQUE (cliente_id, periodo),
  CONSTRAINT chk_pago_monto   CHECK (monto > 0),
  CONSTRAINT chk_pago_periodo CHECK (DAY(periodo) = 1)
);

CREATE INDEX idx_pagos_periodo ON pagos (periodo);

-- ---------------------------------------------------------------------
-- 6. COLAS (MikroTik): estado DESEADO (lo que dice la web) y estado REPORTADO
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
-- 7. ACCIONES: "deja esta cola como dice la web". Se generan solo en
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
-- 8. TERMINAL REMOTA: comandos que un ADMIN encola y el MikroTik
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
-- 9. CONSUMO POR MES de cada cliente (suma de lo que reporta el MikroTik)
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

-- ---------------------------------------------------------------------
-- 10. CAJA POR RED: sube con los pagos, baja con el pago mensual (Starlink) y
--     con retiros. El saldo no se guarda: es la suma de movimientos.
--     La caja de cada red se crea con un INSERT (ver 006_caja.sql).
-- ---------------------------------------------------------------------
CREATE TABLE cajas (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  red_id            INT          NOT NULL,
  descuento_monto   DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  descuento_dia     TINYINT      NOT NULL DEFAULT 1,
  descuento_activo  BOOLEAN      NOT NULL DEFAULT FALSE,
  descuento_desde   DATE         NOT NULL,
  creado_en         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_caja_red          FOREIGN KEY (red_id) REFERENCES redes(id),
  CONSTRAINT uk_caja_red          UNIQUE (red_id),
  CONSTRAINT chk_caja_dia         CHECK (descuento_dia BETWEEN 1 AND 28),
  CONSTRAINT chk_caja_monto       CHECK (descuento_monto >= 0)
);

CREATE TABLE caja_movimientos (
  id                 INT AUTO_INCREMENT PRIMARY KEY,
  caja_id            INT          NOT NULL,
  tipo               ENUM('INGRESO_PAGO','DESCUENTO_MENSUAL','RETIRO') NOT NULL,
  monto              DECIMAL(8,2) NOT NULL,
  fecha              DATE         NOT NULL,
  descripcion        VARCHAR(255) NOT NULL,
  pago_id            INT          NULL,
  periodo            DATE         NULL,
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
  CONSTRAINT chk_movimiento_periodo CHECK (periodo IS NULL OR DAY(periodo) = 1)
);

CREATE INDEX idx_movimientos_caja_fecha ON caja_movimientos (caja_id, fecha, id);

-- =====================================================================
--  DATOS INICIALES
--  La fila de configuración es obligatoria: edítala luego desde
--  "Configuración" en el panel del admin.
-- =====================================================================

INSERT INTO configuracion (id, nombre_empresa, whatsapp_soporte, codigo_pais, yape_numero, yape_titular,
                           dias_tolerancia, plantilla_recordatorio) VALUES
  (1, 'Internet Rural Demo', '900000000', '51', '900000000', 'Empresa Demo', 3,
   'Hola {nombre}, te recordamos que tu pago de internet de {mes} por {monto} está pendiente. ¡Gracias!');

-- ---- DEMO (inventado). Si es para una empresa real, borra estas dos instrucciones. ----
INSERT INTO planes (nombre, bajada_mbps, subida_mbps, precio) VALUES
  ('Estándar', 15, 5, 79.00),
  ('Básico',    8, 3, 60.00);

INSERT INTO clientes (codigo, nombres, celular, referencia, zona, plan_id, dia_pago, fecha_inicio, ip) VALUES
  ('C-01', 'María Torres Díaz', '900000001', 'Casa verde junto a la iglesia', 'Zona Norte',  1, 27, '2026-07-01', '192.168.1.11'),
  ('C-02', 'Luis Rojas Vega',   '900000002', 'Frente a la cancha',            'Zona Norte',  1, 15, '2026-07-01', '192.168.1.12'),
  ('C-03', 'Ana Quispe Flores', '900000003', 'Bajada del colegio',            'Zona Centro', 2, 5,  '2026-08-10', '192.168.1.13');
-- ---- FIN DEMO ----

-- El usuario ADMIN no se crea aquí: su contraseña debe cifrarse con BCrypt.
-- Lo crea la aplicación al arrancar con ADMIN_USERNAME y ADMIN_PASSWORD.

-- =====================================================================
--  USUARIO EXCLUSIVO PARA LA APLICACIÓN (seguridad)
--  Solo puede leer y escribir datos en wisp_db: no puede borrar tablas
--  ni tocar otras bases de datos. '%' = puede conectarse desde cualquier
--  IP (necesario en Aiven, porque Render no tiene una IP fija).
--  REQUIRE SSL: el servidor rechaza a este usuario si no usa conexión cifrada.
--  >>> Cambia la contraseña antes de ejecutar y anótala en un lugar seguro. <<<
-- =====================================================================
CREATE USER IF NOT EXISTS 'wisp_app'@'%'
  IDENTIFIED BY 'CAMBIA_ESTA_CONTRASEÑA'
  REQUIRE SSL;

GRANT SELECT, INSERT, UPDATE, DELETE ON wisp_db.* TO 'wisp_app'@'%';

-- Verificación rápida
SELECT codigo, nombres, zona, estado, codigo_vigente FROM clientes;
