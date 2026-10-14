# Integración con MikroTik (RouterOS 7)

La web **nunca se conecta al router**: los MikroTik suelen estar detrás de CGNAT (ej: Starlink).
Es el MikroTik el que consulta a la web por HTTPS con `/tool fetch` ("pull").

Hay dos scripts en el router, independientes entre sí:

| Script | Quién lo instala | Qué hace |
|---|---|---|
| **puente** | Tú, a mano, una sola vez | Cada 2 min hace `GET /api/mikrotik/bootstrap` y solo ejecuta la respuesta si empieza con la línea del marcador (`MIKROTIK_MARCADOR_PUENTE`). Es tu acceso de emergencia: la web nunca lo modifica. |
| **wisp-sync** | La web (por el puente o pegándolo a mano) | Cada N segundos consulta acciones, las aplica, ejecuta los comandos de la terminal y envía el reporte. |

El script `wisp-sync` solo crea, reemplaza o borra **su propio** script y **su propia** tarea programada
(ambos llamados `wisp-sync`). No toca el puente, las reglas del firewall, la cola padre, las colas protegidas
ni ninguna cola que no sea de un cliente del sistema, y nunca activa fasttrack.
Del firewall solo maneja **sus propias** entradas de las address-list `wisp-cortados` y `wisp-aviso`
(comentario `wisp-gestion:<cola>`) y borra las conexiones abiertas de un cliente al cortarlo.
Del proxy solo agrega y borra **sus propias** reglas de `/ip proxy access` (mismo comentario).
Si `wisp-sync` falla, el puente sigue funcionando y puede reinstalarlo.

---

## 1. Antes de instalar

### 1.1 Variables en Render

- `APP_URL_PUBLICA` = la URL pública de la web, con `https://` y sin `/` al final
  (ej: `https://mi-app.onrender.com`). Es la URL que queda dentro del script del router.
- `MIKROTIK_MARCADOR_PUENTE` (opcional) = primera línea que exige tu puente. Por defecto `# astranet-ok`.

### 1.2 Hora correcta en el router

Sin la fecha correcta, ningún certificado es válido.

```
/system ntp client set enabled=yes servers=time.cloudflare.com,time.google.com
/system clock set time-zone-name=America/Lima
/system clock print
```

### 1.3 Certificados raíz (verificación HTTPS)

`check-certificate=yes-without-crl` comprueba que el certificado de la web esté firmado por una
autoridad raíz en la que el router confía, y que no esté vencido. No consulta listas de revocación (CRL),
que suelen fallar en routers sin acceso a esas listas. Para eso el router necesita **los certificados raíz**.

**a) Averigua qué autoridad firma el certificado de tu web.** Desde tu PC:

```
curl.exe -sv https://mi-app.onrender.com/api/public/configuracion -o NUL 2>&1 | findstr /i "issuer"
```

o en el navegador: candado → certificado → el primero de la cadena. Render usa normalmente
**Let's Encrypt** (raíz *ISRG Root X1*) o **Google Trust Services** (raíces *GTS Root R1* / *GTS Root R4*).
Como Render puede cambiar de autoridad al renovar, conviene instalar las tres.

**b) Descarga las raíces desde las páginas oficiales, en tu PC:**

- ISRG Root X1: https://letsencrypt.org/certificates/ (archivo `isrgrootx1.pem`)
- GTS Root R1 y R4: https://pki.goog/repository/ (archivos `r1.pem` y `r4.pem`)

**c) Súbelas al router** arrastrándolas a *Files* en Winbox (no las descargues con `fetch` sin
verificar: justamente aún no hay cómo verificar).

**d) Impórtalas y márcalas como confiables:**

```
/certificate import file-name=isrgrootx1.pem passphrase=""
/certificate import file-name=r1.pem passphrase=""
/certificate import file-name=r4.pem passphrase=""
/certificate set [find where common-name="ISRG Root X1"] trusted=yes
/certificate set [find where common-name="GTS Root R1"] trusted=yes
/certificate set [find where common-name="GTS Root R4"] trusted=yes
```

**e) Comprueba la huella** de cada una y compárala con la publicada en la página oficial:

```
/certificate print detail where common-name~"ISRG|GTS"
```

**f) Prueba la conexión:**

```
/tool fetch url="https://mi-app.onrender.com/api/public/configuracion" check-certificate=yes-without-crl output=user
```

Debe terminar con `status: finished`. Si dice algo como *certificate not trusted* o
*unable to get local issuer*, falta la raíz correcta (vuelve al paso a).

> **Atajo en RouterOS 7.20:** el router trae raíces incorporadas. Revisa:
> ```
> /certificate/settings print
> ```
> Si muestra `builtin-trust-anchors=trusted`, el router ya confía en las autoridades raíz comunes
> (Let's Encrypt, Google, etc.) y **no hace falta importar nada** (pasos a-e): ve directo a la prueba del paso f.
> Si dice otra cosa: `/certificate/settings set builtin-trust-anchors=trusted`.
> (En 7.20 el parámetro se llama `builtin-trust-anchors`; `builtin-trust-store` no existe.)

### 1.4 Device-mode (fetch y scheduler)

En RouterOS 7 el *device-mode* puede bloquear `/tool fetch` y el *scheduler*; sin ellos no funcionan
ni el puente ni `wisp-sync`. Revisa:

```
/system/device-mode/print
```

Si `fetch` o `scheduler` dicen `no`, habilítalos. **Requiere confirmación física**: tras el comando
hay que presionar el botón del router (o desconectar y conectar la energía) dentro del tiempo que indica,
así que hazlo con alguien en el lugar:

```
/system/device-mode/update fetch=yes scheduler=yes
```

### 1.5 Tipos de cola

Las colas de clientes usan `queue=fq-codel-up/fq-codel-down`. Si esos tipos no existen:

```
/queue type add name=fq-codel-up kind=fq-codel
/queue type add name=fq-codel-down kind=fq-codel
```

Si falta un tipo o la cola padre, el script **no** modifica la cola y la acción queda con el error en la web.

### 1.6 Corte real del servicio (firewall)

Una cola deshabilitada **no corta** el internet: solo quita el límite. Para cortar de verdad, la web
pone la IP de cada cliente **suspendido, con corte manual o retirado** en la address-list `wisp-cortados`
(en modo Control). Esa lista no bloquea nada sola: agrega **una vez**, a mano, estas dos reglas:

```
/ip firewall filter add chain=forward src-address-list=wisp-cortados action=drop comment="wisp-gestion: clientes cortados (salida)" place-before=0
/ip firewall filter add chain=forward dst-address-list=wisp-cortados action=drop comment="wisp-gestion: clientes cortados (entrada)" place-before=0
```

- `place-before=0` las deja primeras, antes de cualquier "accept established" o fasttrack.
- Al cortar, `wisp-sync` también borra las conexiones abiertas de esa IP (si no, un video o una
  descarga que ya estaba en curso seguiría, sobre todo con fasttrack).
- Es `forward`: el cliente cortado aún llega al router (DHCP, DNS, ping), así que la web sigue viendo su conexión.
- Si la IP de un retirado ya la usa un cliente vigente de la misma red, esa IP **no** se bloquea.
- Límite: un cliente que se cambie la IP a mano se salta el bloqueo (usa leases DHCP estáticos o ARP `reply-only`).

Revisa la lista: `/ip firewall address-list print where list=wisp-cortados`

### 1.7 Aviso de pago por Wi-Fi (y página "Servicio suspendido")

Cuando a un cliente le llega su día de pago y no ha pagado, la web pone su IP en la address-list
`wisp-aviso` y crea en el proxy del router una regla **solo para su IP** que lo redirige a
`<web>/aviso/<token>`. Así, al navegar, su celular detecta un "portal" ("Iniciar sesión en la red")
y le muestra su aviso. **Su internet sigue funcionando**: solo se desvían las páginas `http://`
(puerto 80); HTTPS nunca se toca. Los suspendidos (`wisp-cortados`) ven "Servicio suspendido" y siguen sin internet.

- El token es aleatorio, cambia en cada envío y en la web solo se guarda su hash. Nunca va el código del cliente.
- Se activa por red: **Redes → (red) → Panel → Activar aviso**. Solo funciona en modo **Control**.
- Las IP terminadas en **.1 a .10** nunca reciben aviso (la web y el script lo impiden).
- "Entendido" lo saca del aviso hasta el día siguiente a las 8:00. Al registrar su pago, sale en la siguiente consulta.

Agrega **una vez**, a mano, estas reglas. Cambia `TU-APP.onrender.com` por el dominio de `APP_URL_PUBLICA`
(sin `https://`) y la subred por la de cada red (ejemplo: Cuypirmarca `192.168.10.0/24`).
Pega los bloques **en este orden**:

```
# a) Proxy del router, sin caché (solo se usa para el aviso)
/ip proxy set enabled=yes port=8080 max-cache-size=none cache-on-disk=no

# b) Regla final (todo lo que no tenga regla propia va a la página general) y, antes, la web pasa directo
/ip proxy access add action=deny redirect-to="TU-APP.onrender.com/aviso" comment="wisp-gestion: fin"
/ip proxy access add dst-host=TU-APP.onrender.com action=allow comment="wisp-gestion: la web" place-before=0

# c) Nadie usa el proxy directamente: solo las conexiones desviadas por la NAT de abajo
/ip firewall filter add chain=input protocol=tcp dst-port=8080 connection-nat-state=!dstnat action=drop comment="wisp-gestion: proxy solo para el aviso" place-before=0

# d) Infraestructura que nunca ve el aviso (.1 a .10 y, en Cuypirmarca, el sectorial en .99)
/ip firewall address-list add list=wisp-sin-aviso address=192.168.10.1-192.168.10.10 comment="wisp-gestion: MikroTik e infraestructura"
/ip firewall address-list add list=wisp-sin-aviso address=192.168.10.99 comment="wisp-gestion: sectorial (temporal)"

# e) Desviar SOLO el HTTP (puerto 80) de wisp-aviso y wisp-cortados al proxy. La infraestructura queda primero.
/ip firewall nat add chain=dstnat src-address-list=wisp-cortados protocol=tcp dst-port=80 action=redirect to-ports=8080 comment="wisp-gestion: suspendidos a la pagina de aviso" place-before=0
/ip firewall nat add chain=dstnat src-address-list=wisp-aviso protocol=tcp dst-port=80 action=redirect to-ports=8080 comment="wisp-gestion: aviso de pago" place-before=0
/ip firewall nat add chain=dstnat src-address-list=wisp-sin-aviso protocol=tcp dst-port=80 action=accept comment="wisp-gestion: infraestructura sin aviso" place-before=0

# f) Los suspendidos pueden abrir SOLO la web (HTTPS) para ver su página. Van antes de los "drop" de 1.6.
/ip firewall address-list add list=wisp-web address=TU-APP.onrender.com comment="wisp-gestion: la web (pagina de aviso)"
/ip firewall filter add chain=forward src-address-list=wisp-cortados dst-address-list=wisp-web protocol=tcp dst-port=443 action=accept comment="wisp-gestion: suspendidos a la web (salida)" place-before=0
/ip firewall filter add chain=forward src-address-list=wisp-web dst-address-list=wisp-cortados protocol=tcp src-port=443 action=accept comment="wisp-gestion: suspendidos a la web (entrada)" place-before=0
```

- Si un `place-before=0` da error porque la lista está vacía, vuelve a pegar esa línea sin `place-before=0`.
- Para Shiaunto, cambia `192.168.10.` por su subred en el bloque d) (y no agregues el .99).
- `wisp-web` usa el dominio: el router lo resuelve solo y actualiza las IP. Límite: Render comparte IPs entre
  apps, así que un suspendido podría abrir otras webs alojadas en Render (solo por HTTPS).

**DNS de los suspendidos (revisa una vez).** Para que el celular de un suspendido encuentre la web,
debe usar el DNS del router. Revisa `/ip dhcp-server network print` (columna `dns-server`) y
`/ip dns print` (`allow-remote-requests`). Si los clientes usan otro DNS (ej: 8.8.8.8) y el router
tiene `allow-remote-requests=yes`, agrega:

```
/ip firewall nat add chain=dstnat src-address-list=wisp-cortados protocol=udp dst-port=53 action=redirect to-ports=53 comment="wisp-gestion: DNS de suspendidos al router" place-before=0
```

**Revisar:**

```
/ip firewall address-list print where list=wisp-aviso
/ip proxy access print
/log print where message~"wisp"
```

Si `wisp-sync` es de una versión anterior, no maneja el aviso: **Script → Que el puente lo reinstale**.
Para apagarlo de emergencia sin tocar la web: `/ip firewall nat disable [find where comment~"wisp-gestion: aviso"]`.

---

## 2. Instalar

1. En la web, **Redes → Nueva red**: elige **Solo lectura** y "Usar una clave que ya tengo"
   con la clave de tu puente.
2. Instala `wisp-sync` de una de estas dos formas:
   - **Por el puente (recomendado):** la red nueva queda con la instalación pendiente; el puente la
     descarga en su próxima consulta (máx. 2 min).
   - **Comando corto:** Redes → **Script** → escribe el token → **Generar script** → copia las 2 líneas de
     la *Opción 1* y pégalas en *New Terminal* de Winbox. El router descarga el instalador
     (`GET /api/mikrotik/instalar` con el token en el header), lo ejecuta con `/import` y borra el archivo.
   - **Script completo:** la *Opción 2* de la misma pantalla. Se puede pegar en la terminal o subir como
     archivo `.rsc` y usar `/import file-name=...`. Cada comando ocupa una sola línea (como `/export`):
     el código de `wisp-sync` va escapado dentro de `source="..."`.
3. Revisa el log del router (`/log print where message~"wisp"`). Debe decir
   `wisp-gestion: sincronizacion instalada`.
4. Al primer reporte, la red queda como "Instalada". En **Colas** verás qué coincide y qué no.
5. Cuando todo coincida (o entiendas las diferencias), cambia la red a **Control**.

Para actualizar el script después de una nueva versión de la web: **Script → Que el puente lo reinstale**.

## 3. Qué hacer si algo falla

- `wisp-sync: no se pudo consultar la web`: revisa internet, DNS, la hora y los certificados (1.2 y 1.3).
  Un token incorrecto también da este error (la web responde 401).
- `respuesta incompleta`: la respuesta se cortó; se descarta completa y no se aplica nada.
- Para desactivar todo sin tocar el puente:
  ```
  /system scheduler disable [find where name="wisp-sync"]
  ```
