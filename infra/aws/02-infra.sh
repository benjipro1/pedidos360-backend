#!/usr/bin/env bash
# Etapa 2 - se ejecuta EN la EC2 backend (usa el rol LabRole de la
# instancia, no hacen falta credenciales). Crea:
#   - los secretos en ~/pedidos360/.env (solo en esta EC2, permisos 600)
#   - los security groups de RDS y de Kafka
#   - la base RDS PostgreSQL 16 (privada y cifrada)
#   - las 3 EC2 Kafka (t3.small, Docker + compose al arrancar)
# y deja los IDs e IP privadas en ~/pedidos360/infra.env.
#
# Uso:  MI_IP=<tu IP publica> KEY_NAME=pedidos360 bash 02-infra.sh
set -euo pipefail
export AWS_DEFAULT_REGION=us-east-1
: "${MI_IP:?definir MI_IP (tu IP publica, sin /32)}"
: "${KEY_NAME:=pedidos360}"
mkdir -p ~/pedidos360 && cd ~/pedidos360

VPC=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)
SG_EC2=$(aws ec2 describe-security-groups --group-names pedidos360-ec2 --query 'SecurityGroups[0].GroupId' --output text)
AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)

# --- Secretos: se generan aqui y nunca salen de la EC2 ---
if [ ! -f .env ]; then
  umask 077
  cat > .env <<EOF
DB_USER=pedidos
DB_PASS=$(openssl rand -hex 16)
RABBITMQ_PASSWORD=$(openssl rand -hex 16)
RABBITMQ_ERLANG_COOKIE=$(openssl rand -hex 24)
EOF
fi
set -a; . ./.env; set +a

# --- SG RDS: 5432 solo desde la EC2 backend ---
SG_RDS=$(aws ec2 create-security-group --group-name pedidos360-rds --description "Pedidos360 RDS" \
  --vpc-id "$VPC" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$SG_RDS" --ip-permissions \
  "IpProtocol=tcp,FromPort=5432,ToPort=5432,UserIdGroupPairs=[{GroupId=$SG_EC2,Description=PostgreSQL-desde-backend}]" >/dev/null

# --- SG Kafka: ZooKeeper y broker solo entre nodos; 9092, ms-analitica
# (8084) y Kafka UI (8090) solo desde el backend; SSH desde el backend
# (bastion) y desde tu IP. Nada publico. ---
SG_KAFKA=$(aws ec2 create-security-group --group-name pedidos360-kafka --description "Pedidos360 Kafka" \
  --vpc-id "$VPC" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$SG_KAFKA" --ip-permissions \
  "IpProtocol=tcp,FromPort=2181,ToPort=2181,UserIdGroupPairs=[{GroupId=$SG_KAFKA,Description=ZK-cliente}]" \
  "IpProtocol=tcp,FromPort=2888,ToPort=2888,UserIdGroupPairs=[{GroupId=$SG_KAFKA,Description=ZK-quorum}]" \
  "IpProtocol=tcp,FromPort=3888,ToPort=3888,UserIdGroupPairs=[{GroupId=$SG_KAFKA,Description=ZK-eleccion}]" \
  "IpProtocol=tcp,FromPort=9092,ToPort=9092,UserIdGroupPairs=[{GroupId=$SG_KAFKA,Description=Kafka-entre-nodos},{GroupId=$SG_EC2,Description=Kafka-desde-backend}]" \
  "IpProtocol=tcp,FromPort=8084,ToPort=8084,UserIdGroupPairs=[{GroupId=$SG_EC2,Description=ms-analitica-desde-backend}]" \
  "IpProtocol=tcp,FromPort=8090,ToPort=8090,UserIdGroupPairs=[{GroupId=$SG_EC2,Description=Kafka-UI-desde-backend}]" \
  "IpProtocol=tcp,FromPort=22,ToPort=22,UserIdGroupPairs=[{GroupId=$SG_EC2,Description=SSH-desde-backend}],IpRanges=[{CidrIp=$MI_IP/32,Description=SSH-admin}]" >/dev/null

# --- RDS PostgreSQL 16 (tarda ~10 min en quedar "available") ---
aws rds create-db-instance \
  --db-instance-identifier pedidos360-db \
  --engine postgres --engine-version 16.15 \
  --db-instance-class db.t3.micro \
  --allocated-storage 20 --max-allocated-storage 40 --storage-type gp2 --storage-encrypted \
  --master-username "$DB_USER" --master-user-password "$DB_PASS" \
  --db-name pedidos360 \
  --vpc-security-group-ids "$SG_RDS" \
  --no-publicly-accessible \
  --backup-retention-period 7 \
  --tags Key=Name,Value=pedidos360-db \
  --query 'DBInstance.DBInstanceStatus' --output text

# --- 3 EC2 Kafka: Docker y el plugin compose se instalan al arrancar ---
cat > /tmp/kafka-userdata.sh <<'EOF'
#!/bin/bash
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
EOF
IDS=""
for N in 1 2 3; do
  ID=$(aws ec2 run-instances --image-id "$AMI" --instance-type t3.small --key-name "$KEY_NAME" \
    --security-group-ids "$SG_KAFKA" --iam-instance-profile Name=LabInstanceProfile \
    --metadata-options HttpTokens=required \
    --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=16,VolumeType=gp3,Encrypted=true}' \
    --user-data file:///tmp/kafka-userdata.sh \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=pedidos360-kafka-$N}]" \
    --query 'Instances[0].InstanceId' --output text)
  IDS="$IDS $ID"
done
aws ec2 wait instance-running --instance-ids $IDS

{
  echo "VPC=$VPC"; echo "SG_EC2=$SG_EC2"; echo "SG_RDS=$SG_RDS"; echo "SG_KAFKA=$SG_KAFKA"; echo "AMI=$AMI"
  N=1
  for ID in $IDS; do
    PRIV=$(aws ec2 describe-instances --instance-ids "$ID" \
      --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)
    echo "KAFKA_${N}_ID=$ID"; echo "KAFKA_${N}_IP=$PRIV"
    N=$((N+1))
  done
} > infra.env
cat infra.env
