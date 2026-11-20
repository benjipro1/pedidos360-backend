#!/usr/bin/env bash
# Etapa 3 - se ejecuta DESDE TU EQUIPO (Git Bash / Linux / macOS), en la raiz
# del repo. Levanta 1 ZooKeeper + 1 broker en cada EC2 Kafka y Kafka UI en
# kafka-1. Requiere los alias SSH p360-kafka-1/2/3 (ver runbook, paso 3.1),
# que entran a las EC2 Kafka saltando por la EC2 backend.
#
# Uso:
#   KAFKA_1_IP=172.31.x.x KAFKA_2_IP=172.31.y.y KAFKA_3_IP=172.31.z.z \
#     bash infra/aws/03-kafka-nodos.sh
#
# Las IP privadas salen de ~/pedidos360/infra.env en la EC2 backend.
set -euo pipefail
: "${KAFKA_1_IP:?}" "${KAFKA_2_IP:?}" "${KAFKA_3_IP:?}"
DIR=infra/kafka-cloud
IPS=("" "$KAFKA_1_IP" "$KAFKA_2_IP" "$KAFKA_3_IP")

for N in 1 2 3; do
  ENV=$(mktemp)
  cat > "$ENV" <<EOF
NODO_ID=$N
NODO_IP=${IPS[$N]}
KAFKA_1_IP=$KAFKA_1_IP
KAFKA_2_IP=$KAFKA_2_IP
KAFKA_3_IP=$KAFKA_3_IP
ZOOKEEPER_HEAP_OPTS=-Xms128m -Xmx256m
KAFKA_HEAP_OPTS=-Xms256m -Xmx512m
EOF
  ssh "p360-kafka-$N" 'mkdir -p ~/kafka'
  scp -q "$DIR/docker-compose.nodo.yml" "$DIR/docker-compose.kafka-ui.yml" "p360-kafka-$N:kafka/"
  scp -q "$ENV" "p360-kafka-$N:kafka/.env"
  rm -f "$ENV"
done

# Los 3 nodos a la vez: el ensamble ZooKeeper necesita quorum (2 de 3).
for N in 1 2 3; do
  ssh "p360-kafka-$N" 'cd ~/kafka && docker compose -f docker-compose.nodo.yml up -d' &
done
wait
echo "Esperando a que los brokers se registren..."
sleep 40

ssh p360-kafka-1 'cd ~/kafka && docker compose -f docker-compose.kafka-ui.yml up -d'

# Verificacion: un lider ZooKeeper, dos seguidores y 3 brokers registrados.
for N in 1 2 3; do
  echo -n "zookeeper-$N: "
  ssh "p360-kafka-$N" "docker exec zookeeper-$N bash -c 'echo srvr | nc localhost 2181' | grep Mode"
done
echo -n "brokers registrados: "
ssh p360-kafka-1 "docker exec kafka-1 kafka-broker-api-versions --bootstrap-server $KAFKA_1_IP:9092 2>/dev/null | grep -c 'id:'"
