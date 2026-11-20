#!/usr/bin/env bash
# Chequeo rapido del despliegue - se ejecuta DESDE TU EQUIPO. Uselo despues
# de iniciar el Learner Lab y antes de una demo. No modifica nada.
#
# Uso:  API_URL=https://<id>.execute-api.us-east-1.amazonaws.com bash infra/aws/08-verificar.sh
set -uo pipefail
: "${API_URL:?definir API_URL}"
ok()  { printf '  [OK]    %s\n' "$1"; }
mal() { printf '  [FALLA] %s\n' "$1"; FALLAS=$((FALLAS+1)); }
FALLAS=0

echo "EC2 backend"
# Process substitution (no pipe): asi FALLAS se suma en este mismo shell.
while IFS='=' read -r k v; do
  case "$v" in active|healthy|200) ok "$k ($v)";; *) mal "$k ($v)";; esac
done < <(ssh -o ConnectTimeout=10 p360-backend '
  for s in ms-mensajeria ms-productos ms-pedidos bff; do
    printf "%s=%s\n" "$s" "$(systemctl is-active pedidos360-$s)"
  done
  for c in rabbitmq-1 rabbitmq-2; do
    printf "%s=%s\n" "$c" "$(sudo docker inspect -f {{.State.Health.Status}} $c 2>/dev/null || echo ausente)"
  done
  printf "nginx=%s\n" "$(systemctl is-active nginx)"
  for p in 8080 8081 8082; do
    printf "health-%s=%s\n" "$p" "$(curl -s -o /dev/null -w %{http_code} -m 5 localhost:$p/actuator/health)"
  done
  printf "admin-kafka=%s\n" "$(curl -s -o /dev/null -w %{http_code} -m 20 localhost:8083/admin/kafka/cluster)"
' 2>/dev/null)

echo "Cluster Kafka"
for N in 1 2 3; do
  MODO=$(ssh -o ConnectTimeout=10 "p360-kafka-$N" "docker exec zookeeper-$N bash -c 'echo srvr | nc localhost 2181' | grep Mode" 2>/dev/null)
  [ -n "$MODO" ] && ok "zookeeper-$N: ${MODO#Mode: }" || mal "zookeeper-$N sin respuesta"
done
SUB=$(ssh p360-kafka-1 'docker exec kafka-1 kafka-topics --bootstrap-server localhost:9092 --describe --under-replicated-partitions | grep -c Topic' 2>/dev/null)
[ "${SUB:-x}" = 0 ] && ok "0 particiones sub-replicadas" || mal "particiones sub-replicadas: ${SUB:-?}"
AN=$(ssh p360-kafka-2 'systemctl is-active pedidos360-ms-analitica' 2>/dev/null)
[ "$AN" = active ] && ok "ms-analitica (kafka-2)" || mal "ms-analitica: ${AN:-?}"

echo "Acceso publico"
EIP=$(ssh -G p360-backend 2>/dev/null </dev/null | awk '/^hostname /{print $2}')
C=$(curl -sk -o /dev/null -w '%{http_code}' -m 10 "https://$EIP/")
[ "$C" = 200 ] && ok "frontend https://$EIP ($C)" || mal "frontend ($C)"
C=$(curl -s -o /dev/null -w '%{http_code}' -m 10 "$API_URL/api/productos")
[ "$C" = 401 ] && ok "API Gateway sin token -> 401" || mal "API Gateway sin token -> $C (se esperaba 401)"
C=$(curl -s -o /dev/null -w '%{http_code}' -m 10 "http://$EIP:8080/api/productos")
[ "$C" = 401 ] && ok "BFF directo sin token -> 401" || mal "BFF directo sin token -> $C (se esperaba 401)"

echo
[ "$FALLAS" = 0 ] && echo "Todo OK" || { echo "$FALLAS comprobaciones fallaron"; exit 1; }
