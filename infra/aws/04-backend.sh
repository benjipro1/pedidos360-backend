#!/usr/bin/env bash
# Etapa 4 - se ejecuta EN la EC2 backend, despues de la etapa 3 (Kafka
# arriba) y con la RDS en estado "available". Instala Docker, Java 21,
# nginx y git; compila los 5 servicios desde GitHub; levanta el cluster
# RabbitMQ de 2 nodos y deja los 4 servicios del backend en systemd.
# ms-analitica se compila aqui, pero corre en kafka-2 (etapa 5).
#
# Uso:  REPO_BRANCH=feature/kafka bash 04-backend.sh
set -euo pipefail
: "${REPO_BRANCH:=master}"
REPO=https://github.com/benjipro1/pedidos360-backend.git
set -a; . ~/pedidos360/.env; . ~/pedidos360/infra.env; set +a

# --- Paquetes ---
sudo dnf install -y -q docker java-21-amazon-corretto-headless nginx git >/dev/null
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -fsSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# --- Swap de 1 GB: margen para 4 JVM + 2 nodos RabbitMQ en 2 GB de RAM ---
if ! swapon --show | grep -q swapfile; then
  sudo dd if=/dev/zero of=/swapfile bs=1M count=1024 status=none
  sudo chmod 600 /swapfile && sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

# --- Codigo y compilacion (cada servicio tiene su propio Maven wrapper) ---
cd ~
[ -d pedidos360-backend ] || git clone -q -b "$REPO_BRANCH" "$REPO"
cd pedidos360-backend && git pull -q origin "$REPO_BRANCH"
for s in bff ms-productos ms-pedidos ms-mensajeria ms-analitica; do
  (cd "$s" && chmod +x mvnw && ./mvnw -q -DskipTests package)
  echo "compilado: $s"
done

# --- Cluster RabbitMQ (compose del repo) ---
sudo docker compose --env-file ~/pedidos360/.env -f docker-compose.rabbitmq.yml up -d

# --- Entorno de los servicios (solo root lo lee) ---
RDS=$(aws rds describe-db-instances --region us-east-1 --db-instance-identifier pedidos360-db \
  --query 'DBInstances[0].Endpoint.Address' --output text)
sudo mkdir -p /etc/pedidos360 /opt/pedidos360
sudo tee /etc/pedidos360/app.env >/dev/null <<EOF
DB_URL=jdbc:postgresql://$RDS:5432/pedidos360
DB_USER=$DB_USER
DB_PASS=$DB_PASS
RABBITMQ_PASSWORD=$RABBITMQ_PASSWORD
RABBITMQ_ADDRESSES=localhost:5672,localhost:5673
KAFKA_BOOTSTRAP_SERVERS=$KAFKA_1_IP:9092,$KAFKA_2_IP:9092,$KAFKA_3_IP:9092
PRODUCTOS_URL=http://localhost:8081
PEDIDOS_URL=http://localhost:8082
EOF
sudo chmod 600 /etc/pedidos360/app.env

for s in bff ms-productos ms-pedidos ms-mensajeria; do
  sudo cp "$s/target/$s-0.0.1-SNAPSHOT.jar" "/opt/pedidos360/$s.jar"
done

# --- Unidades systemd: arrancan solas cuando el laboratorio reinicia la EC2 ---
crear_unidad() {
  local s=$1 despues=$2
  sudo tee "/etc/systemd/system/pedidos360-$s.service" >/dev/null <<EOF
[Unit]
Description=Pedidos360 $s
After=network-online.target docker.service $despues
Wants=network-online.target

[Service]
User=ec2-user
EnvironmentFile=/etc/pedidos360/app.env
ExecStart=/usr/bin/java -Xmx192m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -jar /opt/pedidos360/$s.jar
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF
}
# ms-mensajeria primero: declara exchanges/colas de RabbitMQ y los topicos Kafka.
crear_unidad ms-mensajeria ""
crear_unidad ms-productos "pedidos360-ms-mensajeria.service"
crear_unidad ms-pedidos "pedidos360-ms-mensajeria.service"
crear_unidad bff ""
sudo systemctl daemon-reload
sudo systemctl enable --now pedidos360-ms-mensajeria pedidos360-ms-productos pedidos360-ms-pedidos pedidos360-bff
echo "RDS=$RDS"
