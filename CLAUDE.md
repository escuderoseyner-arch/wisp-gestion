# wisp-gestion

Sistema web de gestión de clientes y cobros para proveedores de internet inalámbrico (WISP) rurales.
Debe ser genérico y vendible a otras empresas: los datos de cada empresa viven en la tabla `configuracion`, nunca en el código.

## Sobre el desarrollador
- Joaquín, estudiante de Computación e Informática. Entiende Java/Spring a nivel de curso, pero ya no escribe mucho código a mano.
- Explica cada cambio en español sencillo: qué archivo tocaste, qué hace y por qué. Si usas un concepto nuevo, explícalo en una o dos frases.
- Trabaja en pasos pequeños. Al terminar cada paso, compila y ejecuta para comprobar que funciona antes de seguir.

## Stack
- Java 21, Spring Boot 4.1.x, Maven (`./mvnw`)
- Spring Web, Spring Data JPA, Spring Security, Validation, Lombok
- MySQL 8.4, base de datos `wisp_db`, usuario de app `wisp_app` (permisos solo SELECT/INSERT/UPDATE/DELETE)
- La app corre en el puerto 8081
- Paquete base: `com.escuderoseyner.wisp` con subpaquetes `model`, `repository`, `service`, `controller`, `dto`, `config`, `security`

## Base de datos
- El esquema oficial está en `src/main/resources/db/wisp_db.sql`. Léelo antes de crear o modificar entidades.
- `spring.jpa.hibernate.ddl-auto=validate`. NUNCA cambiarlo a `update` ni `create`. Hibernate no modifica tablas.
- Si una funcionalidad necesita cambiar el esquema, NO lo hagas tú: crea un script nuevo en `src/main/resources/db/` (ej: `002_descripcion.sql`) y avisa a Joaquín para que lo ejecute en MySQL Workbench.
- Tablas: configuracion, planes, clientes, usuarios, tokens_recuperacion, pagos.
- Notas de mapeo con `validate`:
  - Columnas `TINYINT` (no booleanas) → `@JdbcTypeCode(SqlTypes.TINYINT)`.
  - Columnas `ENUM` de MySQL → enums de Java con `@Enumerated(EnumType.STRING)`.
  - Columnas generadas o con DEFAULT de MySQL (`codigo_vigente`, `creado_en`, `actualizado_en`) → `insertable = false, updatable = false`.
  - Dinero siempre con `BigDecimal`, nunca `double`.

## Reglas de código
- Entidades con `@Getter` y `@Setter` de Lombok. NO usar `@Data` en entidades.
- Inyección de dependencias por constructor.
- La lógica de negocio va en `service`, no en los controladores.
- Los controladores reciben y devuelven DTOs, nunca entidades directamente. Validar entradas con Bean Validation (`@Valid`, `@NotBlank`, etc.).
- Manejo de errores centralizado con `@RestControllerAdvice`, devolviendo mensajes claros en español.

## Seguridad (prioridad máxima)
- NUNCA escribir contraseñas, tokens ni secretos en el código ni en `application.properties`. Usar variables de entorno (ej: `${DB_PASSWORD}`).
- Contraseñas de usuarios cifradas con BCrypt.
- Roles: ADMIN (ve y gestiona todo) y CLIENTE (solo ve su propia información). Un cliente jamás debe poder ver datos de otro cliente: validarlo en el backend, no solo en la interfaz.
- Bloquear la cuenta temporalmente tras varios intentos de login fallidos (campos `intentos_fallidos` y `bloqueado_hasta`).
- Los tokens de recuperación de contraseña se guardan hasheados (SHA-256), expiran y son de un solo uso.
- Los datos de ejemplo son inventados. Nunca agregar datos reales de clientes al repositorio.
- Antes de dar por terminada una funcionalidad de seguridad, enumera brevemente qué riesgos cubre y cuáles quedan pendientes.

## Alcance de la versión 1
1. CRUD de planes y clientes (incluye retirar un cliente y reasignar su código a otro).
2. Registro de pagos por mes.
3. Panel de admin: clientes atrasados (día de pago + días de tolerancia), pagos de la semana, cobrado vs pendiente del mes.
4. Botón de recordatorio por WhatsApp (enlace wa.me con la plantilla de `configuracion`).
5. Login con roles; portal del cliente para ver su plan, estado e historial de pagos.
