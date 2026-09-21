# Pedidos360 — Backend

Proyecto académico (DSY1107 Desarrollo Cloud Native I, Duoc UC). Sistema de
pedidos con arquitectura cloud native: frontend Angular, API Gateway en AWS,
BFF y microservicios en Spring Boot, identidad en Microsoft Entra ID.

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
   +--> ms-pedidos   :8082 --+--> PostgreSQL :5432
```

El token se valida dos veces (Gateway y BFF) a propósito: el puerto del BFF
es alcanzable desde internet, así que una llamada directa a la IP de la EC2
debe ser rechazada igual. Defensa en profundidad.

## Servicios y puertos

| Servicio     | Puerto | Descripción                                                |
|--------------|--------|-------------------------------------------------------------|
| bff          | 8080   | Backend for Frontend; valida JWT (OAuth2 Resource Server)   |
| ms-productos | 8081   | Microservicio de catálogo de productos                     |
| ms-pedidos   | 8082   | Microservicio de pedidos                                    |
| PostgreSQL   | 5432   | Base de datos local (Docker); en producción, Amazon RDS     |

## Estructura del repositorio

```
pedidos360-backend/
├── ms-productos/        Spring Boot, puerto 8081
├── ms-pedidos/          Spring Boot, puerto 8082
├── bff/                 Spring Boot, puerto 8080
├── docker-compose.yml   PostgreSQL local para desarrollo
├── .env.example         Variables de entorno de referencia (sin valores reales)
├── .gitignore
└── README.md
```

Cada servicio es un proyecto Maven independiente con su propio `pom.xml` y
su Maven Wrapper (`mvnw`). No hay POM padre ni multi-módulo.

## Stack

- Java 21
- Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7, Hibernate 7,
  Jackson 3) — ver la advertencia de versiones en `CLAUDE.md` antes de
  escribir código: la API difiere de los ejemplos típicos de Spring Boot 3.x.
- PostgreSQL 16 (local con Docker; en producción Amazon RDS)
- Maven Wrapper (`./mvnw` en Linux/Mac, `.\mvnw.cmd` en Windows PowerShell)

## Prerrequisitos

- Java 21 (Temurin recomendado)
- Docker y Docker Compose
- No es necesario instalar Maven: cada servicio trae su propio Maven Wrapper.

## Cómo levantar el entorno local

1. Copiar las variables de entorno de referencia:

   ```powershell
   copy .env.example .env
   ```

   Completar `DB_PASS` en `.env`. Ese archivo nunca se commitea.

   `DB_PASS` **no tiene valor por defecto** en ninguna parte: ni
   `docker compose up` ni los microservicios arrancan sin ella. Es
   deliberado — así no queda una contraseña conocida versionada en el
   repositorio, y un despliegue mal configurado falla de inmediato en vez
   de quedar corriendo con una credencial débil.

2. Levantar PostgreSQL (lee `.env` automáticamente):

   ```bash
   docker compose up -d
   ```

   > Ojo con `DB_PASS` si ya habías levantado la base antes:
   > `POSTGRES_PASSWORD` solo se aplica cuando el volumen se crea por
   > primera vez. Si cambias `DB_PASS` sobre un volumen ya existente, la
   > base sigue esperando la contraseña anterior y los servicios fallan con
   > un error de autenticación. Para partir de cero (borra los datos
   > locales): `docker compose down -v && docker compose up -d`.

3. Cargar las variables de `.env` en la sesión desde la que vas a ejecutar
   los servicios. Docker Compose lee `.env` solo, pero Spring Boot no: hay
   que exportarlas.

   PowerShell:

   ```powershell
   Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
     $par = $_ -split '=', 2
     [Environment]::SetEnvironmentVariable($par[0].Trim(), $par[1].Trim(), 'Process')
   }
   ```

   Bash:

   ```bash
   set -a && source .env && set +a
   ```

   (Alternativamente, define las variables en la configuración de ejecución
   de tu IDE.)

4. Ejecutar cada servicio, en terminales separadas, desde su propia carpeta
   (cada terminal necesita las variables del paso 3):

   ```powershell
   cd ms-productos; .\mvnw.cmd spring-boot:run
   cd ms-pedidos;   .\mvnw.cmd spring-boot:run
   cd bff;          .\mvnw.cmd spring-boot:run
   ```

   En Linux/Mac se usa `./mvnw spring-boot:run` en cada carpeta.

5. El frontend (repositorio `pedidos360-frontend`) se levanta aparte con
   `npm start`; su proxy apunta a `http://localhost:8080`, es decir al BFF.

## Identidad (Microsoft Entra ID)

El detalle completo del tenant, client ID, issuer, JWKS, scopes y roles está
documentado en `CLAUDE.md`; no se repite aquí para evitar duplicar valores
que puedan cambiar. Las reglas de seguridad que implementa el BFF (firma,
issuer, audience, expiración, mapeo de `scp`/`roles` a autoridades,
autorización por ruta, manejo de 401/403) también están detalladas ahí.

## Seguridad: por qué ms-pedidos (y ms-productos) no validan tokens

`ms-pedidos` y `ms-productos` **no** verifican JWT ni tienen configuración de
OAuth2 Resource Server: no llevan esa dependencia (ver tabla de dependencias
en `CLAUDE.md`). Confían en que el único cliente que puede alcanzarlos es el
BFF, que ya validó el token dos veces (Gateway y BFF, ver diagrama arriba)
antes de reenviar la petición.

Esta confianza se sostiene por aislamiento de red, no por autenticación
propia: en producción, `ms-productos` y `ms-pedidos` deben desplegarse en una
subred que solo acepte tráfico desde el BFF (por ejemplo, security groups en
AWS que solo permitan el puerto de cada microservicio desde la instancia del
BFF). Si algún día esa subred quedara expuesta, cualquiera podría llamarlos
directamente sin token.

Concretamente, `ms-pedidos` toma el usuario autenticado de la cabecera
`X-User-Email`, que el BFF arma a partir del claim `preferred_username` del
token ya validado (ver "El BFF extrae..." arriba). Si esa cabecera llega
vacía o ausente:

- en `GET /pedidos`, simplemente no filtra y devuelve todos los pedidos
  (pensado para uso interno/administrativo, no para el flujo normal del
  cliente final);
- en `POST /pedidos`, el request se rechaza con `400 usuario_no_identificado`,
  porque un pedido siempre necesita un dueño.

## Restricciones del proyecto

- No se suben secretos, `target/`, ni archivos de IDE (ver `.gitignore`).
- Sin client secrets: el cliente es una SPA pública con PKCE.
- Sin Lombok.
- Sin frameworks extra (ni Spring Cloud Gateway, ni Eureka, ni Feign): el
  gateway es AWS API Gateway y las llamadas internas van con `RestClient`.
- Antes de instalar cualquier dependencia nueva, preguntar.
