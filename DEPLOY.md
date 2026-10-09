# Publicar en Render + Aiven

## 1. Base de datos (Aiven, MySQL)
1. Crea un servicio MySQL en Aiven y anota host, puerto y la contraseña de `avnadmin`.
2. Abre `src/main/resources/db/instalacion_completa.sql`, cambia `CAMBIA_ESTA_CONTRASEÑA`
   por una contraseña larga para `wisp_app` y ejecútalo conectado como `avnadmin`
   (por ejemplo con MySQL Workbench, con SSL activado).
3. No guardes el archivo con la contraseña real en el repositorio.

## 2. App (Render, Web Service con Docker)
Render detecta el `Dockerfile` de la raíz. El perfil `prod` (SSL obligatorio, IP real detrás
del proxy, poca memoria) se activa solo desde el Dockerfile.

Variables de entorno:

| Variable | Valor | Obligatoria |
|---|---|---|
| `DB_URL` | `jdbc:mysql://HOST:PUERTO/wisp_db?sslMode=REQUIRED&serverTimezone=America/Lima` | Sí |
| `DB_USERNAME` | `wisp_app` | Sí |
| `DB_PASSWORD` | la contraseña que pusiste a `wisp_app` | Sí |
| `JWT_SECRET` | 64 bytes al azar en Base64 (ver abajo) | Sí |
| `ADMIN_USERNAME` | usuario del primer admin | Solo el primer despliegue |
| `ADMIN_PASSWORD` | contraseña inicial del primer admin (8 a 72 caracteres) | Solo el primer despliegue |
| `JWT_EXPIRACION_MINUTOS` | duración de la sesión (por defecto 60) | No |
| `APP_URL_PUBLICA` | URL pública con `https://`, sin `/` al final (va en el script del MikroTik, ver MIKROTIK.md) | Si usas MikroTik |
| `MIKROTIK_MARCADOR_PUENTE` | primera línea que exige el script puente (por defecto `# astranet-ok`) | No |

- `PORT` la pone Render sola: no la agregues.
- Si `DB_URL` trae `sslMode=DISABLED`, `PREFERRED` o `useSSL=false`, la app no arranca a propósito.
- Generar `JWT_SECRET` (PowerShell):
  `$b = New-Object byte[] 64; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`
- Health check path: `/login.html`.

## 3. Después del primer despliegue
1. Entra con `ADMIN_USERNAME`; el sistema te pedirá cambiar la contraseña.
2. Borra `ADMIN_USERNAME` y `ADMIN_PASSWORD` de Render.
3. Edita los datos de tu empresa en **Configuración**.
4. Comprueba la IP real: falla el login 20 veces y revisa en los logs de Render la línea
   `IP ... bloqueada`; debe ser tu IP pública, no una IP interna de Render.

## Pendiente
- `sslMode=REQUIRED` cifra la conexión, pero no comprueba que el certificado sea de Aiven.
  Para eso (`sslMode=VERIFY_CA`) hay que agregar el certificado CA de Aiven a un truststore
  dentro de la imagen; todavía no está configurado.
- Los contadores de intentos de login están en memoria: se reinician cuando Render reinicia la app.
