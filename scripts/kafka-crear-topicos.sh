#!/usr/bin/env sh
# Crea (o deja como estan, si ya existen) los topicos de Pedidos360 con sus
# configuraciones. Es la alternativa por CLI a lo que ms-mensajeria declara
# al arrancar (KafkaTopicsConfig): ambos caminos dejan el mismo resultado.
#
# Uso local:  ./scripts/kafka-crear-topicos.sh
# Uso nube:   CONTENEDOR=kafka-1 BOOTSTRAP=<IP privada kafka-1>:9092 ./scripts/kafka-crear-topicos.sh
set -eu

CONTENEDOR="${CONTENEDOR:-kafka-1}"
BOOTSTRAP="${BOOTSTRAP:-kafka-1:29092}"

DIA_MS=86400000

crear() {
  topico="$1"; retencion_dias="$2"
  echo ">> $topico (3 particiones, RF 3, retencion ${retencion_dias} dias)"
  docker exec "$CONTENEDOR" kafka-topics --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists --topic "$topico" \
    --partitions 3 --replication-factor 3 \
    --config min.insync.replicas=2 \
    --config cleanup.policy=delete \
    --config retention.ms=$((retencion_dias * DIA_MS))
}

# Eventos de ciclo de vida del pedido -> analitica de ventas.
crear pedidos.eventos 7
# Movimientos de stock por producto -> stock (ms-productos) y analitica.
crear inventario.movimientos 30
# Dead letter topics: mismas 3 particiones que el original, porque el
# recoverer publica en la misma particion de origen.
crear pedidos.eventos.DLT 30
crear inventario.movimientos.DLT 30

echo
# --topic acepta una expresion regular: describe los 4 topicos de negocio.
docker exec "$CONTENEDOR" kafka-topics --bootstrap-server "$BOOTSTRAP" --describe \
  --topic '(pedidos[.]eventos|inventario[.]movimientos).*'
