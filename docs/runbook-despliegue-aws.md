# Runbook de despliegue en AWS (Learner Lab)

Cómo levantar Pedidos360 completo en AWS Academy Learner Lab, cómo
operarlo día a día y cómo resolver los problemas que ya aparecieron. Todos
los pasos se ejecutaron y verificaron el 20-11-2026 en la cuenta de
Benjamín. Los scripts que se citan están en [`infra/aws/`](../infra/aws).

## 1. Qué queda desplegado

```
Navegador ──HTTPS──> nginx :443 (EC2 backend, IP elástica)   frontend Angular
    │
    └─ token Entra ID ──> API Gateway (HTTP API, authorizer JWT + scopes + CORS)
                              │
                              └─> BFF :8080 ──> ms-productos :8081 ─┐
                                   (EC2 backend)  ms-pedidos   :8082 ─┼─> RDS PostgreSQL 16 (privada)
                                                  ms-mensajeria:8083  │
                                                  RabbitMQ x2 (Docker)│
                                                                       │
              ms-pedidos / ms-productos / ms-mensajeria ──9092──> Kafka (3 EC2, IP privadas)
                                                                   kafka-1: ZK + broker + Kafka UI :8090
                                                                   kafka-2: ZK + broker + ms-analitica :8084
                                                                   kafka-3: ZK + broker
```

| Máquina | Tipo | Qué corre | Expuesto a internet |
|---|---|---|---|
| `pedidos360-backend` | EC2 t3.small, IP elástica | nginx (frontend), BFF, ms-productos, ms-pedidos, ms-mensajeria (systemd), RabbitMQ 2 nodos (Docker) | 443 y 8080. SSH 22 solo desde la IP del administrador |
| `pedidos360-kafka-1` | EC2 t3.small | ZooKeeper 1, broker 1, Kafka UI | Nada |
| `pedidos360-kafka-2` | EC2 t3.small | ZooKeeper 2, broker 2, ms-analitica (systemd) | Nada |
| `pedidos360-kafka-3` | EC2 t3.small | ZooKeeper 3, broker 3 | Nada |
| `pedidos360-db` | RDS db.t3.micro | PostgreSQL 16, cifrada | Nada (solo desde el SG backend) |
| `pedidos360-api` | API Gateway HTTP API | 9 rutas hacia el BFF | Sí (exige JWT) |

**Por qué así:**
- El Learner Lab solo permite t3.micro y t3.small. Una t3.small (2 GB) no
  aguanta 3 ZooKeeper, 3 brokers y Kafka UI además del backend, así que
  Kafka va en 3 EC2 aparte, un nodo por máquina. Esto también hace real la
  tolerancia a fallos: se puede apagar una EC2 completa.
- Las EC2 Kafka usan IP privadas. Se conservan al detener e iniciar el
  laboratorio, así que no necesitan IP elástica.
- Las consolas (Kafka UI, RabbitMQ) y los puertos internos (8081 a 8084)
  no están abiertos. Se acceden por túnel SSH (sección 4.3).
- Los secretos (clave de RDS, de RabbitMQ y cookie Erlang) se generan en
  la EC2 backend y nunca salen de ella: `~/pedidos360/.env` y
  `/etc/pedidos360/app.env`, ambos con permisos 600.

## 2. Requisitos previos

- Acceso al Learner Lab, con el laboratorio iniciado (indicador **AWS** en
  verde). Cada sesión dura 4 horas; al terminar, las EC2 se detienen
  (sección 6.1).
- En el equipo local: Git Bash (Windows) o una terminal Linux/macOS,
  `ssh`/`scp`, y Node.js 20 o superior para compilar el frontend.
- Una llave SSH propia (`ssh-keygen -t ed25519` si no existe). Solo se
  sube la parte pública.
- Tu IP pública (`curl -s ifconfig.me`). Es la única que podrá entrar por
  SSH.
- Del registro de aplicación **Pedidos360-SPA** en Entra ID: el
  *Directory (tenant) ID* y el *Application (client) ID*.
- Los repositorios `pedidos360-backend` y `pedidos360-frontend` clonados.

## 3. Despliegue desde cero

Orden obligatorio: Kafka tiene que estar arriba antes de iniciar el
backend, porque ms-mensajeria crea los tópicos al arrancar. La RDS debe
estar `available` antes del paso 3.5.

Tiempo total aproximado: 45 minutos, la mayor parte esperando a la RDS y
compilando en la EC2.

### 3.1 EC2 backend, llave e IP elástica (CloudShell, ~3 min)

En la consola de AWS, abrir **CloudShell** (región us-east-1), subir
`infra/aws/01-cloudshell-backend.sh` (menú *Actions → Upload file*) y
ejecutar:

```bash
MI_IP=<tu IP pública> KEY_NAME=pedidos360   PUBKEY="<contenido de ~/.ssh/id_ed25519.pub>" bash 01-cloudshell-backend.sh
```

Al final imprime `INSTANCIA=i-... EIP=x.x.x.x`. Guardar la EIP: se usa en
todo lo que sigue.

### 3.2 Alias SSH en el equipo local

Agregar a `~/.ssh/config` (las IP de Kafka se completan después del paso
3.3):

```
Host p360-backend
  HostName <EIP>
  User ec2-user
  IdentityFile ~/.ssh/id_ed25519
  StrictHostKeyChecking accept-new
  ServerAliveInterval 30

Host p360-kafka-1
  HostName <KAFKA_1_IP>
Host p360-kafka-2
  HostName <KAFKA_2_IP>
Host p360-kafka-3
  HostName <KAFKA_3_IP>
Host p360-kafka-*
  User ec2-user
  IdentityFile ~/.ssh/id_ed25519
  ProxyJump p360-backend
  StrictHostKeyChecking accept-new
  ServerAliveInterval 30
```

`ProxyJump` usa la EC2 backend como bastión: las EC2 Kafka no tienen IP
pública útil ni SSH abierto a internet.

Comprobar: `ssh p360-backend 'aws sts get-caller-identity --query Arn'`
debe mostrar un ARN con `LabRole`.

### 3.3 RDS, security groups y EC2 Kafka (EC2 backend, ~3 min + 10 min de RDS)

```bash
scp infra/aws/02-infra.sh p360-backend:/tmp/
ssh p360-backend 'MI_IP=<tu IP pública> KEY_NAME=pedidos360 bash /tmp/02-infra.sh'
```

Imprime `infra.env` con `KAFKA_1_IP`, `KAFKA_2_IP` y `KAFKA_3_IP`.
Completar con ellas los alias del paso 3.2.

La RDS tarda unos 10 minutos. Para saber cuándo está lista:

```bash
ssh p360-backend 'aws rds wait db-instance-available --region us-east-1 --db-instance-identifier pedidos360-db && echo lista'
```

### 3.4 Clúster Kafka y Kafka UI (equipo local, ~3 min)

Esperar 2 minutos tras el paso 3.3: el *user-data* de las EC2 Kafka
instala Docker al arrancar. Luego, desde la raíz del repo backend:

```bash
KAFKA_1_IP=<...> KAFKA_2_IP=<...> KAFKA_3_IP=<...> bash infra/aws/03-kafka-nodos.sh
```

Resultado esperado: un ZooKeeper `leader`, dos `follower` y `brokers
registrados: 3`.

Copia en cada EC2 Kafka `infra/kafka-cloud/docker-compose.nodo.yml` y un
`.env` con su `NODO_ID` y las 3 IP privadas, y lo levanta. Los contenedores
tienen `restart: unless-stopped`: vuelven solos cuando el laboratorio
reinicia las instancias.

### 3.5 Backend y RabbitMQ (EC2 backend, ~12 min)

```bash
scp infra/aws/04-backend.sh p360-backend:/tmp/
ssh p360-backend 'REPO_BRANCH=master bash /tmp/04-backend.sh'
```

Usar `REPO_BRANCH=feature/kafka` mientras la rama no esté mergeada.
Instala Docker, Java 21, nginx y git; agrega 1 GB de swap; clona y
compila los 5 servicios; levanta RabbitMQ (2 nodos); escribe
`/etc/pedidos360/app.env` y crea las unidades systemd
`pedidos360-{ms-mensajeria,ms-productos,ms-pedidos,bff}`, todas con
`-Xmx192m` para caber en 2 GB.

Al arrancar, ms-mensajeria declara en RabbitMQ los exchanges, las colas y
sus DLQ, y en Kafka los 4 tópicos (`pedidos.eventos`,
`inventario.movimientos` y sus `.DLT`, con 3 particiones y RF 3). Si hiciera
falta crearlos a mano, existe `scripts/kafka-crear-topicos.sh`; hay que
ejecutarlo en kafka-1 con `CONTENEDOR=kafka-1 BOOTSTRAP=<KAFKA_1_IP>:9092`.

### 3.6 ms-analitica en kafka-2 (equipo local, ~2 min)

```bash
KAFKA_1_IP=<...> KAFKA_2_IP=<...> KAFKA_3_IP=<...> bash infra/aws/05-ms-analitica.sh
```

Toma el jar compilado en el paso 3.5 y lo instala como
`pedidos360-ms-analitica` en kafka-2. Debe terminar en `active`.

### 3.7 API Gateway (EC2 backend, ~1 min)

```bash
scp infra/aws/06-apigw.sh p360-backend:/tmp/
ssh p360-backend 'EIP=<EIP> TENANT=<tenant id> CLIENT=<client id> bash /tmp/06-apigw.sh'
```

Imprime `API_URL`. El authorizer valida firma, emisor
(`https://login.microsoftonline.com/<tenant>/v2.0`), audiencia (client id)
y el scope de cada ruta (`pedidos.read` o `pedidos.write`). CORS solo admite
`https://<EIP>`.

### 3.8 Frontend (equipo local, ~5 min)

1. En `pedidos360-frontend/src/environments/environment.prod.ts`:
   - `redirectUri: 'https://<EIP>'`
   - `apiBaseUrl: '<API_URL>/api'`
2. Compilar: `npm install && npx ng build --configuration production`.
3. Publicar, desde la raíz del repo backend:

```bash
EIP=<EIP> FRONT_DIST=../pedidos360-frontend/dist/pedidos360-frontend/browser bash infra/aws/07-frontend.sh
```

nginx sirve el build por HTTPS con un certificado autofirmado (SAN = la
EIP). MSAL exige HTTPS fuera de `localhost`.

### 3.9 URI de redirección en Entra ID (portal, ~2 min)

En **entra.microsoft.com → Registros de aplicaciones → Pedidos360-SPA →
Authentication → Agregar URI de redireccionamiento → Aplicación de página
única**, agregar `https://<EIP>` y guardar. Requiere iniciar sesión con MFA.
Sin este paso, el login falla con `AADSTS50011` (redirect URI no
coincide).

### 3.10 Verificación final

```bash
API_URL=<API_URL> bash infra/aws/08-verificar.sh
```

Debe terminar en `Todo OK`. Comprueba los servicios systemd, RabbitMQ,
health de los servicios, el quórum ZooKeeper, que no haya particiones
sub-replicadas, ms-analitica, el frontend y que el Gateway y el BFF
rechacen peticiones sin token (401).

Prueba manual: abrir `https://<EIP>`, aceptar la advertencia del
certificado, iniciar sesión, crear un pedido y comprobar que el stock baja
en el catálogo.

## 4. Operación diaria

### 4.1 Iniciar una sesión de trabajo

1. En el Learner Lab, **Start Lab** y esperar el indicador verde (2 a 5
   minutos).
2. Las 4 EC2 arrancan solas y, con ellas, los servicios systemd, RabbitMQ
   y los contenedores de Kafka. La IP elástica y las IP privadas no
   cambian. En la prueba del 20-11-2026 la RDS ya estaba `available` al
   volver las EC2.
3. Esperar unos 3 minutos y ejecutar `08-verificar.sh`.

Si `08-verificar.sh` falla en SSH con *Connection timed out*, revisar que
tu IP pública no haya cambiado (sección 6.2).

### 4.2 Detener

**End Lab** detiene todo. No hay que hacer nada más: los datos de la RDS,
los tópicos de Kafka y las colas de RabbitMQ se conservan.

### 4.3 Túneles SSH a las consolas

Se usan puertos locales altos para no chocar con servicios locales (el BFF
local usa 8080 y Kafka UI local, 8090):

```bash
# Kafka UI -> http://localhost:18090
ssh -N -L 18090:localhost:8090 p360-kafka-1

# Admin Kafka/RabbitMQ (ms-mensajeria) -> http://localhost:18083/admin/kafka/...
# ms-analitica                         -> http://localhost:18084/analitica/resumen
# Consola RabbitMQ (nodo 1)            -> http://localhost:25672
ssh -N -L 18083:localhost:8083 -L 18084:<KAFKA_2_IP>:8084 -L 25672:localhost:15672 p360-backend
```

Usuario de la consola RabbitMQ: `admin`. La contraseña está solo en la EC2:

```bash
ssh p360-backend 'sudo grep RABBITMQ_PASSWORD /etc/pedidos360/app.env'
```

### 4.4 Logs

```bash
ssh p360-backend 'sudo journalctl -u pedidos360-ms-pedidos -n 100 --no-pager'
ssh p360-kafka-2 'sudo journalctl -u pedidos360-ms-analitica -f'
ssh p360-kafka-1 'docker logs --tail 100 kafka-1'
```

## 5. Actualizar a una nueva versión

### 5.1 Un servicio del backend

Después de hacer push a la rama:

```bash
ssh p360-backend 'set -e
cd ~/pedidos360-backend && git pull origin <rama>
cd ms-mensajeria && ./mvnw -q -DskipTests package
sudo cp target/ms-mensajeria-0.0.1-SNAPSHOT.jar /opt/pedidos360/ms-mensajeria.jar
sudo systemctl restart pedidos360-ms-mensajeria'
```

Cambiar `ms-mensajeria` por el servicio que corresponda. Se compila en la
EC2 para que el jar salga exactamente del commit publicado. Reiniciar un
consumidor no pierde eventos: Kafka los retiene y el consumer group retoma
desde su último offset confirmado. Reiniciar ms-pedidos o el BFF sí corta
las peticiones durante unos 30 segundos.

### 5.2 ms-analitica

Compilar en la EC2 backend como en 5.1 (`cd ms-analitica`) y volver a
ejecutar `05-ms-analitica.sh`. Al arrancar relee los tópicos desde el
inicio (replay) y reconstruye sus KPIs.

### 5.3 Frontend

Repetir el paso 3.8 (compilar y `07-frontend.sh`). El certificado se
conserva.

## 6. Problemas conocidos

### 6.1 Todo deja de responder (SSH `Connection refused` o timeout, túneles caídos)

Terminó la sesión del Learner Lab (4 horas) y se detuvieron las
instancias. Iniciar el laboratorio de nuevo (sección 4.1). Los túneles hay
que volver a abrirlos.

### 6.2 SSH a la EC2 backend da timeout, pero el frontend responde

Cambió tu IP pública y el security group `pedidos360-ec2` solo admite la
anterior en el puerto 22. Actualizar la regla:

```bash
# En CloudShell
SG=$(aws ec2 describe-security-groups --group-names pedidos360-ec2 --query 'SecurityGroups[0].GroupId' --output text)
aws ec2 revoke-security-group-ingress --group-id $SG --protocol tcp --port 22 --cidr <IP anterior>/32
aws ec2 authorize-security-group-ingress --group-id $SG --protocol tcp --port 22 --cidr <IP nueva>/32
```

Hay que hacer lo mismo con la regla `SSH-admin` del SG `pedidos360-kafka`
si se entra directo a las EC2 Kafka. Con `ProxyJump` no hace falta.

### 6.3 El navegador advierte que la conexión no es privada

Es el certificado autofirmado. Elegir **Configuración avanzada →
Continuar a <EIP>**. Para una demo, hacerlo antes de presentar.

### 6.4 Login falla con `AADSTS50011`

La URI de redirección no está registrada o no coincide exactamente (sección
3.9).

### 6.5 Después de apagar un broker, `/admin/kafka/topics/{t}` responde 503

Pasa en la primera consulta, a los ~12 s, mientras Kafka reelige los
líderes. Las siguientes responden al instante. Esperar unos 15 s antes de
mostrarlo.

### 6.6 El broker volvió, pero sigue sin liderar particiones

El ISR se recupera solo, pero el liderazgo vuelve al líder preferido recién
cuando corre `auto.leader.rebalance` (cada 5 minutos). Para forzarlo:

```bash
ssh p360-kafka-1 'docker exec kafka-1 kafka-leader-election --bootstrap-server localhost:9092 --election-type preferred --all-topic-partitions'
```

### 6.7 Kafka UI muestra menos lag del real con el consumidor detenido

Si un consumer group no tiene miembros, Kafka UI solo cuenta las
particiones donde el grupo ya confirmó algún offset. Los mensajes que caen
en otras particiones no aparecen. `GET /admin/kafka/consumer-groups/{id}`
de ms-mensajeria sí los cuenta: recorre todas las particiones de los
tópicos del grupo.

### 6.8 Un túnel falla con `Address already in use`

Otro proceso local usa ese puerto (por ejemplo, el stack de Docker local).
Detenerlo o cambiar el puerto local del túnel.

### 6.9 La EC2 backend se queda sin memoria

Tiene 2 GB para 4 JVM, 2 nodos RabbitMQ y nginx, con 1 GB de swap.
Revisar con `ssh p360-backend 'free -m'`. No agregar servicios en esa EC2:
por eso ms-analitica corre en kafka-2.

## 7. Costo y eliminación

Con todo encendido el gasto estimado es de unos USD 0,13 por hora
(4 t3.small, 1 db.t3.micro y las IP públicas). Con las EC2 detenidas se
deja de pagar su cómputo; los discos, la RDS si sigue encendida y la IP
elástica siguen sumando. El panel del Learner Lab muestra el gasto real.

Para eliminar todo al cierre del semestre, en CloudShell y en este orden.
**No se puede deshacer y borra la base de datos:**

```bash
aws apigatewayv2 delete-api --api-id <API_ID>
aws ec2 terminate-instances --instance-ids <ID backend> <ID kafka-1> <ID kafka-2> <ID kafka-3>
aws rds delete-db-instance --db-instance-identifier pedidos360-db --skip-final-snapshot
aws ec2 wait instance-terminated --instance-ids <ID backend> <ID kafka-1> <ID kafka-2> <ID kafka-3>
aws ec2 release-address --allocation-id <ALLOCATION_ID>
aws rds wait db-instance-deleted --db-instance-identifier pedidos360-db
aws ec2 delete-security-group --group-name pedidos360-kafka
aws ec2 delete-security-group --group-name pedidos360-rds
aws ec2 delete-security-group --group-name pedidos360-ec2
aws ec2 delete-key-pair --key-name <KEY_NAME>
```

Quitar también la URI `https://<EIP>` del registro en Entra ID.

## Anexo: despliegue vigente (20-11-2026)

| Recurso | Valor |
|---|---|
| EIP backend | 32.196.34.1 (`i-04c28b8d0c44b3312`) |
| kafka-1 / 2 / 3 | 172.31.17.30 (`i-0d0ec3d174c6dbeef`) / 172.31.24.131 (`i-00b72046aea714b86`) / 172.31.17.148 (`i-0e06135d3faa35f23`) |
| RDS | `pedidos360-db.cnrvtgawd2om.us-east-1.rds.amazonaws.com` |
| API Gateway | `https://s57a92dg4h.execute-api.us-east-1.amazonaws.com` (authorizer `entra-jwt`) |
| Frontend | `https://32.196.34.1` |
| Llave SSH | `pedidos360-benji` |
| Security groups | `pedidos360-ec2` `sg-010d11fb400e551cb`, `pedidos360-rds` `sg-0258c8913c89d1576`, `pedidos360-kafka` `sg-0b0e65a898260c72a` |
