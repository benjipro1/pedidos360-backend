# Pedidos360 — Backend

Proyecto académico (DSY1107 Desarrollo Cloud Native I, Duoc UC). Sistema de
pedidos con arquitectura cloud native: frontend Angular, API Gateway en AWS,
BFF y microservicios en Spring Boot, identidad en Microsoft Entra ID.

El frontend vive en el repositorio aparte `pedidos360-frontend`.

## Arquitectura

```
Angular (MSAL) --login OIDC + PKCE--> Microsoft Entra ID
   |                                        |
   | Authorization: Bearer <access token>   | firma con JWKS
   v                                        |
AWS API Gateway (HTTP API) <----valida------+
   | authorizer JWT (iss, aud) + scopes por ruta + CORS
   v
BFF Spring Boot :8080   <-- valida el JWT otra vez y aplica roles
   |
   +--> ms-productos :8081 --+
   +--> ms-pedidos   :8082 --+--> PostgreSQL
            |
            | (despues de guardar el pedido, best effort)
            +--> RabbitMQ (2 nodos) --> ms-mensajeria :8083   avisos, documentos, auditoria
            |
            +--> Kafka (3 ZK + 3 brokers)
                   pedidos.eventos ---------> ms-analitica :8084  (group analitica-ventas)
                   inventario.movimientos --> ms-productos        (group productos-stock: descuenta stock)
                                         \--> ms-analitica        (group analitica-inventario)
```

RabbitMQ y Kafka conviven con roles distintos (ver [Mensajeria](#mensajeria)):
RabbitMQ reparte **tareas** que se consumen una vez; Kafka guarda un **log de
eventos** retenido por dias que varios consumer groups leen de forma
independiente y que se puede releer.

El token se valida dos veces (Gateway y BFF) a propósito: el puerto del BFF
es alcanzable desde internet, así que una llamada directa a la IP de la EC2
debe ser rechazada igual. Defensa en profundidad.

En desarrollo local no hay API Gateway: Angular llama a `/api/...` y su
`proxy.conf.json` redirige al BFF, evitando CORS.

## Servicios y puertos

| Servicio     | Puerto | Descripción                                               |
|--------------|--------|-----------------------------------------------------------|
| bff          | 8080   | Backend for Frontend; valida JWT (OAuth2 Resource Server) |
| ms-productos | 8081   | Microservicio de catálogo de productos                    |
| ms-pedidos   | 8082   | Microservicio de pedidos; productor RabbitMQ y Kafka      |
| ms-mensajeria| 8083   | Consumidor RabbitMQ; administrador de RabbitMQ y de Kafka |
| ms-analitica | 8084   | Consumidor Kafka; KPIs de ventas e inventario en tiempo real |
| PostgreSQL   | 5432   | Base de datos local en Docker; configurable con `DB_PORT` |
| RabbitMQ     | 5672 / 5673 | AMQP de rabbitmq-1 / rabbitmq-2 (consolas 15672 / 15673) |
| Kafka        | 9092 / 9093 / 9094 | Brokers kafka-1 / -2 / -3 (Zookeeper solo en la red Docker) |
| Kafka UI     | 8090   | Administracion y monitoreo del cluster Kafka              |

## Stack

- Java 21 (Temurin recomendado)
- Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7, Hibernate 7,
  Jackson 3) — la API difiere de los ejemplos típicos de Spring Boot 3.x.
- PostgreSQL 16 (local con Docker; en producción Amazon RDS)
- RabbitMQ 4.3.6 (Spring AMQP)
- Kafka: brokers Confluent Platform 7.9.11 con Zookeeper; clientes Spring
  Kafka 4.1 / kafka-clients 4.2. Se usa Confluent 7.x porque Kafka 4.x y
  Confluent 8.x solo soportan KRaft, y la arquitectura pide 3 nodos Zookeeper.
- Kafka UI: `kafbat/kafka-ui` v1.5.0 (fork mantenido de provectus/kafka-ui)
- Maven Wrapper (`./mvnw` en Linux/Mac, `.\mvnw.cmd` en Windows PowerShell)

## Estructura del repositorio

```
pedidos360-backend/
├── bff/                         Spring Boot, puerto 8080
├── ms-productos/                Spring Boot, puerto 8081
├── ms-pedidos/                  Spring Boot, puerto 8082
├── ms-mensajeria/               Spring Boot, puerto 8083
├── ms-analitica/                Spring Boot, puerto 8084
├── docker-compose.yml           PostgreSQL local para desarrollo
├── docker-compose.rabbitmq.yml  Cluster RabbitMQ de 2 nodos
├── docker-compose.kafka.yml     3 Zookeeper + 3 brokers Kafka
├── docker-compose.kafka-ui.yml  Kafka UI
├── infra/
│   ├── rabbitmq/                Config de formacion del cluster
│   └── kafka-cloud/             Compose por nodo para las 3 EC2 Kafka
├── scripts/
│   └── kafka-crear-topicos.sh   Crea los topicos con sus configs (CLI)
├── .env.example                 Variables de referencia (sin valores reales)
├── .gitignore
└── README.md
```

Cada servicio es un proyecto Maven independiente con su propio `pom.xml` y su
Maven Wrapper. No hay POM padre ni multi-módulo.

## Prerrequisitos

- Java 21
- Docker y Docker Compose
- Maven **no** hace falta: cada servicio trae su Maven Wrapper.

## Puesta en marcha

### 1. Configurar variables de entorno

```powershell
copy .env.example .env
```

Completar `DB_PASS` en `.env`. Ese archivo nunca se commitea.

`DB_PASS` **no tiene valor por defecto** en ninguna parte: ni
`docker compose up` ni los microservicios arrancan sin ella. Es deliberado:
así no queda una contraseña conocida versionada en el repositorio, y un
despliegue mal configurado falla de inmediato en vez de quedar corriendo con
una credencial débil.

Si ya tienes un PostgreSQL instalado en la máquina, define también
`DB_PORT=5433` y usa ese mismo puerto dentro de `DB_URL` (ver
[Problemas conocidos](#problemas-conocidos)).

### 2. Levantar PostgreSQL, RabbitMQ y Kafka

```bash
docker compose up -d                                   # PostgreSQL
docker compose -f docker-compose.rabbitmq.yml up -d    # RabbitMQ (2 nodos)
docker compose -f docker-compose.kafka.yml up -d       # 3 Zookeeper + 3 brokers
docker compose -f docker-compose.kafka-ui.yml up -d    # Kafka UI en http://localhost:8090
```

Docker Compose lee `.env` por su cuenta. RabbitMQ necesita
`RABBITMQ_PASSWORD` y `RABBITMQ_ERLANG_COOKIE`.

Los topicos Kafka los crea ms-mensajeria al arrancar. Si quieres crearlos
antes (o sin levantar el servicio): `./scripts/kafka-crear-topicos.sh`.

Comprobaciones rapidas:

```bash
docker exec rabbitmq-1 rabbitmqctl cluster_status                 # 2 nodos
docker exec zookeeper-1 bash -c "echo srvr | nc localhost 2181"   # Mode: leader/follower
docker exec kafka-1 kafka-topics --bootstrap-server kafka-1:29092 --describe --topic pedidos.eventos
```

### 3. Cargar las variables en la terminal

Spring Boot **no** lee `.env`: hay que exportar las variables en cada
terminal desde la que ejecutes un servicio.

PowerShell:

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
  $par = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($par[0].Trim(), $par[1].Trim().Trim('"'), 'Process')
}
```

Bash:

```bash
set -a && source .env && set +a
```

También puedes definirlas en la configuración de ejecución de tu IDE.

### 4. Ejecutar los servicios

Una terminal por servicio, cada una con las variables del paso 3:

```powershell
cd ms-mensajeria; .\mvnw.cmd spring-boot:run   # primero: declara colas y topicos
cd ms-productos;  .\mvnw.cmd spring-boot:run
cd ms-pedidos;    .\mvnw.cmd spring-boot:run
cd ms-analitica;  .\mvnw.cmd spring-boot:run
cd bff;           .\mvnw.cmd spring-boot:run
```

En Linux/Mac: `./mvnw spring-boot:run`.

### 5. Levantar el frontend

En el repositorio `pedidos360-frontend`: `npm start`. Queda en
`http://localhost:4200` y su proxy apunta al BFF en el 8080.

### 6. Comprobar que todo responde

```bash
curl http://localhost:8080/actuator/health   # bff
curl http://localhost:8081/actuator/health   # ms-productos
curl http://localhost:8082/actuator/health   # ms-pedidos
curl http://localhost:8084/actuator/health   # ms-analitica
curl http://localhost:8083/admin/kafka/cluster   # 3 brokers
curl http://localhost:8080/api/productos     # debe responder 401 sin token
```

El 401 con cuerpo JSON es la respuesta correcta sin token. Para ver datos
reales hay que iniciar sesión desde el frontend.

## Variables de entorno

| Variable       | Servicio | Descripción                                                    |
|----------------|----------|----------------------------------------------------------------|
| `DB_PORT`      | Docker   | Puerto del host donde se publica PostgreSQL (por defecto 5432) |
| `DB_URL`       | ms-*     | JDBC de conexión; el puerto debe coincidir con `DB_PORT`       |
| `DB_USER`      | ms-*     | Usuario de la base                                             |
| `DB_PASS`      | ms-*     | **Obligatoria, sin default.** Sin ella no arranca nada         |
| `JWT_ISSUER`   | bff      | Issuer exacto del tenant de Entra ID                           |
| `JWT_JWKS_URI` | bff      | Endpoint de claves públicas para validar la firma              |
| `JWT_AUDIENCE` | bff      | Audiencia esperada en el token                                 |
| `PRODUCTOS_URL`| bff      | URL de ms-productos                                            |
| `PEDIDOS_URL`  | bff      | URL de ms-pedidos                                              |
| `RABBITMQ_PASSWORD` | Docker, ms-pedidos, ms-mensajeria | **Obligatoria.** Clave del usuario `admin` de RabbitMQ |
| `RABBITMQ_ERLANG_COOKIE` | Docker | **Obligatoria.** Cookie compartida por los nodos del cluster |
| `KAFKA_BOOTSTRAP_SERVERS` | ms-pedidos, ms-productos, ms-mensajeria, ms-analitica | Brokers Kafka (por defecto `localhost:9092,localhost:9093,localhost:9094`) |

Los valores de `JWT_*` tienen defaults en `application.properties` porque no
son secretos: identifican al tenant y a la aplicación, no autentican nada por
sí solos. El cliente es una SPA pública con PKCE, sin client secret.

## Identidad (Microsoft Entra ID)

```
Tenant ID:   baffd3de-6d87-4351-b113-0276e469ae8d
Client ID:   b5c90e11-373c-4846-a4e0-5440187d52de
Issuer:      https://login.microsoftonline.com/<tenant>/v2.0
JWKS:        https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys
Audience:    b5c90e11-373c-4846-a4e0-5440187d52de
App ID URI:  api://b5c90e11-373c-4846-a4e0-5440187d52de
Scopes:      pedidos.read, pedidos.write   (claim scp)
Roles:       Admin, Cliente                (claim roles)
```

El token es v2 (`requestedAccessTokenVersion: 2`).

## Seguridad del BFF

Implementado en `bff/src/main/java/cl/benji/pedidos360/bff/config/SecurityConfig.java`.

**Validación del token** (`jwtDecoder`):

| Regla      | Cómo se cumple                                                        |
|------------|-----------------------------------------------------------------------|
| Firma      | `NimbusJwtDecoder.withJwkSetUri(...)` contra el JWKS del tenant       |
| Issuer     | `JwtValidators.createDefaultWithIssuer(issuer)`                       |
| Expiración | Incluida en el validador por defecto                                  |
| Audience   | `AudienceValidator` propio — Spring **no** la valida por defecto      |

Los cuatro se combinan con `DelegatingOAuth2TokenValidator`: el token debe
pasarlos todos.

**Autoridades**: el claim `scp` se mapea a `SCOPE_pedidos.read` /
`SCOPE_pedidos.write` (lo hace Spring), y el claim `roles` a `ROLE_Admin` /
`ROLE_Cliente` (mapeo propio, porque el nombre del claim varía según el
proveedor).

**Autorización por ruta**:

| Ruta                        | Requisito                              |
|-----------------------------|----------------------------------------|
| `/actuator/health`          | Público                                |
| `GET /api/**`               | `SCOPE_pedidos.read`                   |
| Resto de `/api/**`          | `SCOPE_pedidos.write`                  |
| Escribir/borrar productos   | Además `ROLE_Admin` (`@PreAuthorize`)  |
| Cualquier otra ruta         | Autenticado                            |

**Sesiones**: `STATELESS` y CSRF deshabilitado. La API no usa cookies; cada
request se autentica con su propio JWT, así que no hay sesión que un sitio
externo pueda aprovechar.

**CORS**: no se configura en el BFF. En producción lo maneja el API Gateway
(y sobrescribe lo que devuelva el backend); en local se evita con el proxy de
Angular.

## Respuestas de error

Toda la API responde errores con el mismo formato:

```json
{ "status": 401, "error": "no_autenticado", "message": "se requiere un token de acceso valido" }
```

| Código | `error`                  | Cuándo                                              |
|--------|--------------------------|-----------------------------------------------------|
| 400    | `validacion`             | Falla Bean Validation en el cuerpo                  |
| 400    | `parametro_invalido`     | Un path variable no es del tipo esperado            |
| 400    | `usuario_no_identificado`| `POST /pedidos` sin `X-User-Email`                  |
| 401    | `no_autenticado`         | Sin token, o token inválido/expirado/de otra audiencia |
| 403    | `acceso_denegado`        | Token válido, pero sin el scope o rol necesario     |
| 404    | `producto_no_encontrado` / `pedido_no_encontrado` | El recurso no existe       |
| 503    | `servicio_no_disponible` | El BFF no pudo contactar al microservicio           |

La distinción entre 401 y 403 es deliberada: 401 es "no sé quién eres", 403 es
"sé quién eres y no puedes hacer esto". El 401 no revela **cuál** validación
falló (firma, issuer, audience o expiración), porque eso solo le sirve a quien
intenta falsificar un token.

## API

A través del BFF (todas requieren token):

| Método | Ruta                      | Requisito             |
|--------|---------------------------|-----------------------|
| GET    | `/api/productos`          | `pedidos.read`        |
| GET    | `/api/productos/{id}`     | `pedidos.read`        |
| POST   | `/api/productos`          | `pedidos.write` + Admin |
| PUT    | `/api/productos/{id}`     | `pedidos.write` + Admin |
| DELETE | `/api/productos/{id}`     | `pedidos.write` + Admin |
| GET    | `/api/pedidos`            | `pedidos.read` — devuelve solo los del usuario del token |
| GET    | `/api/pedidos/{id}`       | `pedidos.read`        |
| POST   | `/api/pedidos`            | `pedidos.write`       |
| PUT    | `/api/pedidos/{id}/estado`| `pedidos.write`       |

Notas:

- **No existe `/api/pedidos/mios`**: `GET /api/pedidos` ya filtra por el
  usuario, porque el BFF envía `X-User-Email` sacado del token.
- `DELETE /api/productos/{id}` es **baja lógica**: marca `activo = false` y
  responde 204. El producto sigue apareciendo en `GET /api/productos`, así que
  el catálogo del frontend filtra los inactivos. Se reactiva con un `PUT`
  enviando `activo: true`.
- Estados de pedido: `PENDIENTE`, `PAGADO`, `ENVIADO`, `CANCELADO`.

## Mensajeria

La publicacion en ambos brokers es **best effort**: el pedido se guarda
primero y una falla de RabbitMQ o Kafka se registra en el log, pero nunca
revierte el pedido ni devuelve un 500.

### RabbitMQ (Parcial 2)

ms-pedidos publica `pedido.creado`, `pedido.pagado` y cada cambio de estado.
ms-mensajeria declara la topologia (`TopologyInitializer`) y consume:

| Recurso | Tipo | Uso |
|---|---|---|
| `pedidos.direct.exchange` | direct | `pedido.creado` -> notificaciones, `pedido.pagado` -> documentos |
| `pedidos.topic.exchange` | topic (`pedido.#`) | auditoria de todo cambio |
| `pedidos.dlx` | direct | dead-letter: `notificaciones.dlq`, `documentos.dlq`, `auditoria.dlq` |

Listeners con ACK manual, `prefetch: 1` y `concurrency: 2`. Administracion en
`/admin/rabbitmq` (exchanges, colas y bindings).

### Kafka (EP5)

**Topicos** (los declara ms-mensajeria en `application.yml`; alternativa CLI
en `scripts/kafka-crear-topicos.sh`):

| Topico | Key | Productor | Consumer groups | Configuracion |
|---|---|---|---|---|
| `pedidos.eventos` | pedidoId | ms-pedidos | `analitica-ventas` (ms-analitica) | 3 particiones, RF 3, `min.insync.replicas=2`, `delete`, retencion 7 dias |
| `inventario.movimientos` | productoId | ms-pedidos (uno por linea) | `productos-stock` (ms-productos), `analitica-inventario` (ms-analitica) | 3 particiones, RF 3, `min.insync.replicas=2`, `delete`, retencion 30 dias |
| `*.DLT` | igual al original | error handler de cada consumidor | — | 3 particiones, RF 3, retencion 30 dias |

- **Key = orden**: todos los eventos de un pedido (o de un producto) van a la
  misma particion, asi que se consumen en el orden en que ocurrieron.
- **3 particiones**: hasta 3 consumidores en paralelo por group (un hilo por
  particion, `concurrency=3`).
- **RF 3 + `min.insync.replicas=2` + `acks=all`**: un mensaje confirmado esta
  en al menos 2 brokers; el cluster sigue funcionando si cae 1 broker.
  Los topicos internos (`__consumer_offsets`) tambien tienen RF 3.
- **Dos groups sobre `inventario.movimientos`**: ambos reciben todos los
  mensajes (broadcast entre groups); dentro de cada group las particiones se
  reparten entre sus consumidores (balanceo).

**Eventos** (JSON, sin cabeceras de tipo: cada servicio deserializa a su
propio DTO):

```json
// pedidos.eventos
{ "eventId": "uuid", "tipo": "PEDIDO_CREADO | PEDIDO_ESTADO_CAMBIADO", "version": 1,
  "fechaEvento": "...", "pedidoId": 8, "usuarioEmail": "...", "estado": "PAGADO",
  "estadoAnterior": "PENDIENTE", "total": 6565, "fechaCreacion": "...",
  "items": [{ "productoId": 1, "nombreProducto": "...", "cantidad": 1, "precioUnitario": 6565 }] }

// inventario.movimientos
{ "eventId": "uuid", "tipo": "SALIDA_VENTA | DEVOLUCION_CANCELACION", "version": 1,
  "fechaEvento": "...", "productoId": 1, "nombreProducto": "...", "cantidad": 1, "pedidoId": 8 }
```

**Stock**: un pedido compromete stock mientras no este `CANCELADO`. Crear un
pedido publica una `SALIDA_VENTA` por linea; cancelarlo, una
`DEVOLUCION_CANCELACION`. ms-productos aplica cada movimiento una sola vez
(deduplica por `eventId` en la tabla `movimientos_procesados`); si no hay
stock suficiente descuenta hasta 0 y registra una alerta, y una devolucion
nunca devuelve mas de lo que realmente se desconto.

**Analitica**: ms-analitica mantiene los KPIs en memoria y, al arrancar,
relee los topicos desde el inicio (replay) para reconstruirlos. `GET
/analitica/resumen` devuelve ventas totales, pedidos por estado, ticket
promedio y productos mas vendidos.

**Errores en los consumidores**:

- `ErrorHandlingDeserializer`: un mensaje que no es JSON valido no bloquea la
  particion.
- Validacion del payload (Bean Validation) antes de procesar; logs con
  topico, particion, offset, key y `eventId`.
- `DefaultErrorHandler`: 3 reintentos con espera exponencial (1 s, 2 s, 4 s);
  los eventos invalidos y los errores de deserializacion no se reintentan.
  Agotados los reintentos, el mensaje va a `<topico>.DLT`.

**Administracion** (`ms-mensajeria`, `/admin/kafka`):

| Metodo | Ruta | Accion |
|---|---|---|
| GET | `/cluster` | Brokers y controller |
| GET | `/topics?internos=false` | Listar topicos |
| GET | `/topics/{nombre}` | Particiones (lider, replicas, ISR, offsets) y configs |
| POST | `/topics` | Crear `{nombre, particiones, replicas, configs}` |
| DELETE | `/topics/{nombre}` | Eliminar |
| PATCH | `/topics/{nombre}/configs` | `{set: {...}, eliminar: [...]}` (incrementalAlterConfigs) |
| POST | `/topics/{nombre}/particiones` | `{total}` (solo aumentar) |
| GET | `/consumer-groups` | Listar groups y su estado |
| GET | `/consumer-groups/{id}` | Miembros, asignacion, offsets y lag por particion |
| POST | `/consumer-groups/{id}/reset-offsets` | `{topico, posicion: INICIO\|FIN}` (group detenido) |
| DELETE | `/consumer-groups/{id}` | Eliminar group |

Igual que `/admin/rabbitmq`, no tiene autenticacion propia: se protege por
red (puerto 8083 cerrado en el security group, acceso por tunel SSH).

### Kafka en la nube

Cada par Zookeeper + broker corre en su propia EC2 t3.small
(`infra/kafka-cloud/docker-compose.nodo.yml`, `network_mode: host`, IP
privadas). Kafka UI corre en la EC2 kafka-1
(`infra/kafka-cloud/docker-compose.kafka-ui.yml`). En los microservicios
basta con definir `KAFKA_BOOTSTRAP_SERVERS=<ip1>:9092,<ip2>:9092,<ip3>:9092`.

## Datos

`ms-productos` siembra ocho productos desde `data.sql` (uno inactivo, a
propósito, para comprobar el filtrado del catálogo). Ambos microservicios usan
`ddl-auto=update`, así que los datos **sobreviven a los reinicios**; la semilla
está escrita para insertarse solo si la tabla está vacía.

## Por qué ms-pedidos y ms-productos no validan tokens

No verifican JWT ni llevan la dependencia de OAuth2 Resource Server. Confían en
que el único cliente que los alcanza es el BFF, que ya validó el token.

Esa confianza se sostiene por **aislamiento de red**, no por autenticación
propia: en producción deben desplegarse en una subred que solo acepte tráfico
desde el BFF (security groups en AWS). Si esa subred quedara expuesta,
cualquiera podría llamarlos sin token.

`ms-pedidos` toma el usuario de la cabecera `X-User-Email`, que el BFF arma
desde el claim `preferred_username` del token ya validado — nunca desde una
cabecera que mande el cliente. Si llega vacía:

- `GET /pedidos` no filtra y devuelve todos (uso interno/administrativo);
- `POST /pedidos` se rechaza con `400 usuario_no_identificado`.

## Pruebas

```powershell
cd bff; .\mvnw.cmd test
```

Nueve tests en el BFF: validación de audience (token correcto, múltiples
audiencias, audiencia ajena, sin claim `aud`), autorización por ruta y rol, y
que todo 401 lleve cuerpo JSON.

Pruebas unitarias de la mensajeria Kafka (no necesitan infraestructura):

```powershell
cd ms-pedidos;   .\mvnw.cmd test -Dtest=KafkaEventPublisherTest   # salidas, devoluciones, best effort
cd ms-productos; .\mvnw.cmd test -Dtest=StockServiceTest          # stock, piso 0, idempotencia
cd ms-analitica; .\mvnw.cmd test                                  # KPIs, duplicados, replay
```

Los `contextLoads` de ms-productos y ms-pedidos necesitan PostgreSQL arriba
(paso 2) y las variables del paso 3.

## Problemas conocidos

### El BFF rechaza todos los tokens con 401 (`PKIX path building failed`)

Si en el log del BFF aparece:

```
I/O error on GET request for "https://login.microsoftonline.com/.../keys":
PKIX path building failed: unable to find valid certification path
```

hay un antivirus o proxy corporativo interceptando HTTPS (Avast, ESET,
Kaspersky, Zscaler…). Re-firman los certificados con su propia raíz, que
Windows conoce pero Java **no**. Sin poder descargar el JWKS, el BFF no puede
verificar la firma de ningún token y responde 401 a todo — aunque el token sea
perfectamente válido y el navegador funcione sin problema.

Solución sin desactivar el antivirus: crear un truststore propio con esa raíz.

1. Identificar la raíz que está interceptando:

   ```powershell
   Get-ChildItem Cert:\LocalMachine\Root |
     Where-Object { $_.Subject -like '*Avast*' -or $_.Subject -like '*ESET*' } |
     Select-Object Subject, Thumbprint
   ```

2. Exportarla (reemplaza el thumbprint y usa una ruta **sin espacios**):

   ```powershell
   mkdir $HOME\.pedidos360 -Force
   $c = Get-ChildItem Cert:\LocalMachine\Root |
     Where-Object { $_.Thumbprint -eq 'TU_THUMBPRINT' }
   [IO.File]::WriteAllBytes("$HOME\.pedidos360\root.cer", $c.RawData)
   ```

3. Copiar el `cacerts` del JDK y agregarle esa raíz (el del sistema queda
   intacto):

   ```powershell
   $jdk = Split-Path (Split-Path (Get-Command java).Source)
   copy "$jdk\lib\security\cacerts" "$HOME\.pedidos360\truststore.jks"
   & "$jdk\bin\keytool.exe" -importcert -noprompt -alias antivirus-root `
     -file "$HOME\.pedidos360\root.cer" `
     -keystore "$HOME\.pedidos360\truststore.jks" -storepass changeit
   ```

4. Agregar al `.env` (**con comillas**, porque el valor lleva un espacio y sin
   ellas la shell intenta ejecutar el segundo `-D` como comando):

   ```
   JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=C:/Users/TU_USUARIO/.pedidos360/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
   ```

La ruta del truststore **no puede tener espacios**: `JAVA_TOOL_OPTIONS` separa
sus argumentos por espacios. Por eso va en `$HOME` y no dentro del proyecto.

Alternativa más simple, pero que baja una protección: desactivar el escaneo
HTTPS del antivirus. Lo que **no** hay que hacer es desactivar la verificación
de certificados en el código: eso deja la validación de firma inservible.

### Los microservicios no conectan: `la autentificación password falló`

Dos causas posibles:

1. **Ya tienes PostgreSQL instalado** y ocupa el 5432, así que las conexiones
   nunca llegan al contenedor. Comprueba con `netstat -ano | findstr 5432`: si
   aparece más de un proceso escuchando, define `DB_PORT=5433` en `.env` y usa
   `localhost:5433` en `DB_URL`.

2. **Cambiaste `DB_PASS` sobre un volumen ya creado.** `POSTGRES_PASSWORD`
   solo se aplica cuando el volumen se crea por primera vez. Para partir de
   cero (borra los datos locales):
   `docker compose down -v && docker compose up -d`.

### `Unable to determine Dialect without JDBC metadata`

Es el síntoma, no la causa: el servicio no pudo conectarse a la base. Busca más
arriba en el log el error real (autenticación, puerto o `DB_PASS` sin definir).

## Restricciones del proyecto

- No se suben secretos, `target/`, ni archivos de IDE (ver `.gitignore`).
- Sin client secrets: el cliente es una SPA pública con PKCE.
- Sin Lombok, salvo en ms-mensajeria, que ya lo usaba desde la Parcial 2.
- Sin frameworks extra (ni Spring Cloud Gateway, ni Eureka, ni Feign): el
  gateway es AWS API Gateway y las llamadas internas van con `RestClient`.
- Antes de instalar cualquier dependencia nueva, preguntar.

## Pendiente conocido

`POST /pedidos` confía en el `precioUnitario` que envía el cliente: un cliente
con token válido podría crear un pedido con precios arbitrarios. Resolverlo
implica que el precio se resuelva en el servidor (ms-pedidos consultando a
ms-productos, o el BFF enriqueciendo el pedido), lo que cambia la topología
entre servicios.
