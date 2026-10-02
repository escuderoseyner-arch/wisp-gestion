-- =====================================================================
--  SISTEMA DE GESTIÓN PARA PROVEEDORES DE INTERNET (WISP)
--  Base de datos v1 - MySQL 8.4
--  Genérico: los datos de cada empresa (nombre, Yape, WhatsApp, etc.)
--  van en la tabla "configuracion", no en el código.
--  Ejecutar completo en MySQL Workbench con el usuario root.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS wisp_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;   -- utf8mb4: soporta tildes, ñ y emojis

USE wisp_db;

-- ---------------------------------------------------------------------
-- 0. CONFIGURACIÓN DE LA EMPRESA (una sola fila)
--    Para venderle el sistema a otra empresa, solo cambias esta fila.
-- ---------------------------------------------------------------------
CREATE TABLE configuracion (
  id                  TINYINT      PRIMARY KEY DEFAULT 1,
  nombre_empresa      VARCHAR(100) NOT NULL,
  ruc                 VARCHAR(11)  NULL,
  logo_url            VARCHAR(255) NULL,
  whatsapp_soporte    VARCHAR(15)  NOT NULL,           -- a dónde llegan los reportes de falla
  yape_numero         VARCHAR(15)  NULL,
  yape_titular        VARCHAR(100) NULL,
  cuenta_bancaria     VARCHAR(150) NULL,               -- ej: "BBVA Ahorros 0011-..."
  dias_tolerancia     TINYINT      NOT NULL DEFAULT 3, -- días de gracia antes de marcar atrasado
  moneda              VARCHAR(5)   NOT NULL DEFAULT 'S/',
  plantilla_recordatorio VARCHAR(500) NOT NULL,        -- mensaje de WhatsApp con {nombre}, {monto}, {mes}
  actualizado_en      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_config_unica CHECK (id = 1),          -- impide crear una segunda fila
  CONSTRAINT chk_tolerancia   CHECK (dias_tolerancia BETWEEN 0 AND 15)
);

-- ---------------------------------------------------------------------
-- 1. PLANES: los paquetes de internet que vende la empresa
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
-- 2. CLIENTES
--    Un código (C-07) puede pasar a otra persona: el anterior queda
--    RETIRADO con su historial y se crea un registro nuevo.
-- ---------------------------------------------------------------------
CREATE TABLE clientes (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  codigo        VARCHAR(10)  NOT NULL,                -- ej: C-01
  nombres       VARCHAR(100) NOT NULL,
  celular       VARCHAR(15)  NOT NULL,
  referencia    VARCHAR(200) NULL,                    -- "casa azul frente a la plaza"
  plan_id       INT          NOT NULL,
  dia_pago      TINYINT      NOT NULL,                -- día del mes en que vence
  fecha_inicio  DATE         NOT NULL,
  fecha_retiro  DATE         NULL,
  ip            VARCHAR(15)  NULL,
  estado        ENUM('ACTIVO','SUSPENDIDO','RETIRADO') NOT NULL DEFAULT 'ACTIVO',
  creado_en     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- Truco: vale el código si el cliente NO está retirado, y NULL si lo está.
  -- Como UNIQUE permite varios NULL, solo puede haber UN cliente vigente
  -- por código, pero sí varios retirados con el mismo código.
  codigo_vigente VARCHAR(10) GENERATED ALWAYS AS
    (IF(estado <> 'RETIRADO', codigo, NULL)) STORED,

  CONSTRAINT fk_cliente_plan   FOREIGN KEY (plan_id) REFERENCES planes(id),
  CONSTRAINT uk_codigo_vigente UNIQUE (codigo_vigente),
  -- Hasta el 28 para que exista en todos los meses (febrero incluido)
  CONSTRAINT chk_dia_pago      CHECK (dia_pago BETWEEN 1 AND 28),
  CONSTRAINT chk_retiro        CHECK (
    (estado = 'RETIRADO' AND fecha_retiro IS NOT NULL) OR
    (estado <> 'RETIRADO' AND fecha_retiro IS NULL)
  )
);

CREATE INDEX idx_clientes_estado ON clientes (estado);

-- ---------------------------------------------------------------------
-- 3. USUARIOS: login estándar (usuario + contraseña + roles)
--    "username" es genérico: cada empresa decide si usa celular,
--    DNI o un nombre de usuario. El email es opcional y sirve para
--    recuperar la contraseña por correo.
--    La contraseña se guarda CIFRADA (BCrypt), nunca en texto plano.
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
  id                    INT AUTO_INCREMENT PRIMARY KEY,
  username              VARCHAR(50)  NOT NULL UNIQUE,
  email                 VARCHAR(100) NULL UNIQUE,
  password_hash         VARCHAR(100) NOT NULL,
  rol                   ENUM('ADMIN','CLIENTE') NOT NULL,
  cliente_id            INT          NULL UNIQUE,      -- solo si es CLIENTE
  nombre_mostrar        VARCHAR(100) NOT NULL,
  activo                BOOLEAN      NOT NULL DEFAULT TRUE,
  debe_cambiar_password BOOLEAN      NOT NULL DEFAULT TRUE,  -- obliga a cambiar la clave inicial
  intentos_fallidos     INT          NOT NULL DEFAULT 0,     -- para frenar ataques de fuerza bruta
  bloqueado_hasta       DATETIME     NULL,
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
-- 4. RECUPERACIÓN DE CONTRASEÑA ("¿Olvidaste tu contraseña?")
--    Se guarda el HASH del token, no el token real: si alguien roba
--    la base de datos, no puede usar los enlaces de recuperación.
-- ---------------------------------------------------------------------
CREATE TABLE tokens_recuperacion (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id  INT       NOT NULL,
  token_hash  CHAR(64)  NOT NULL UNIQUE,   -- SHA-256 del token enviado por correo
  expira_en   DATETIME  NOT NULL,          -- ej: 30 minutos después de pedirlo
  usado_en    DATETIME  NULL,              -- un token solo se puede usar una vez
  creado_en   DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- ---------------------------------------------------------------------
-- 5. PAGOS
--    "periodo" = primer día del mes que se paga (ej: 2026-10-01 = octubre)
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

-- =====================================================================
--  DATOS INICIALES (DEMO)
--  Todo inventado: sirve para pruebas y para mostrar en el portafolio.
--  Los datos reales de una empresa NO van en este archivo ni en GitHub.
-- =====================================================================

INSERT INTO configuracion (id, nombre_empresa, whatsapp_soporte, yape_numero, yape_titular,
                           dias_tolerancia, plantilla_recordatorio) VALUES
  (1, 'Internet Rural Demo', '900000000', '900000000', 'Empresa Demo', 3,
   'Hola {nombre}, te recordamos que tu pago de internet de {mes} por {monto} está pendiente. ¡Gracias!');

INSERT INTO planes (nombre, bajada_mbps, subida_mbps, precio) VALUES
  ('Estándar', 15, 5, 79.00),
  ('Básico',    8, 3, 60.00);

INSERT INTO clientes (codigo, nombres, celular, referencia, plan_id, dia_pago, fecha_inicio, ip) VALUES
  ('C-01', 'María Torres Díaz', '900000001', 'Casa verde junto a la iglesia', 1, 27, '2026-07-01', '192.168.1.11'),
  ('C-02', 'Luis Rojas Vega',   '900000002', 'Frente a la cancha',            1, 15, '2026-07-01', '192.168.1.12'),
  ('C-03', 'Ana Quispe Flores', '900000003', 'Bajada del colegio',            2, 5,  '2026-08-10', '192.168.1.13');

-- El usuario ADMIN no se crea aquí porque su contraseña debe cifrarse
-- con BCrypt desde Spring Boot. Lo crearemos desde la aplicación.

-- =====================================================================
--  USUARIO EXCLUSIVO PARA LA APLICACIÓN (seguridad)
--  La app NO debe conectarse como root. Este usuario solo puede leer
--  y escribir datos en wisp_db: no puede borrar tablas ni tocar
--  otras bases de datos.
--  >>> Cambia la contraseña antes de ejecutar y anótala en tu libreta. <<<
-- =====================================================================
CREATE USER IF NOT EXISTS 'wisp_app'@'localhost'
  IDENTIFIED BY 'CAMBIA_ESTA_CONTRASEÑA';

GRANT SELECT, INSERT, UPDATE, DELETE ON wisp_db.* TO 'wisp_app'@'localhost';

FLUSH PRIVILEGES;

-- Verificación rápida
SELECT codigo, nombres, estado, codigo_vigente FROM clientes;
