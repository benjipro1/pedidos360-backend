#!/usr/bin/env bash
# Etapa 5 - se ejecuta DESDE TU EQUIPO. Instala ms-analitica en kafka-2
# como servicio systemd. Corre ahi y no en la EC2 backend porque esta ya
# tiene 4 JVM y 2 nodos RabbitMQ en 2 GB; kafka-2 tiene margen.
#
# Uso:
#   KAFKA_1_IP=... KAFKA_2_IP=... KAFKA_3_IP=... bash infra/aws/05-ms-analitica.sh
set -euo pipefail
: "${KAFKA_1_IP:?}" "${KAFKA_2_IP:?}" "${KAFKA_3_IP:?}"

ssh p360-kafka-2 'sudo dnf install -y -q java-21-amazon-corretto-headless >/dev/null && sudo mkdir -p /opt/pedidos360'
# -3: copia de EC2 a EC2 pasando por tu equipo (las EC2 no tienen tu llave).
scp -q -3 p360-backend:pedidos360-backend/ms-analitica/target/ms-analitica-0.0.1-SNAPSHOT.jar \
  p360-kafka-2:/tmp/ms-analitica.jar

ssh p360-kafka-2 "sudo mv /tmp/ms-analitica.jar /opt/pedidos360/ && sudo tee /etc/systemd/system/pedidos360-ms-analitica.service >/dev/null <<EOF
[Unit]
Description=Pedidos360 ms-analitica
After=network-online.target docker.service
Wants=network-online.target

[Service]
User=ec2-user
Environment=KAFKA_BOOTSTRAP_SERVERS=$KAFKA_1_IP:9092,$KAFKA_2_IP:9092,$KAFKA_3_IP:9092
ExecStart=/usr/bin/java -Xmx192m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -jar /opt/pedidos360/ms-analitica.jar
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload && sudo systemctl enable --now pedidos360-ms-analitica && systemctl is-active pedidos360-ms-analitica"
