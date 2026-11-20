#!/usr/bin/env bash
# Etapa 1 - se ejecuta en AWS CloudShell (us-east-1) del Learner Lab.
# Crea el par de llaves, el security group de la EC2 backend, la EC2
# backend (t3.small, Amazon Linux 2023, rol LabInstanceProfile) y su IP
# elastica.
#
# Uso:
#   MI_IP=<tu IP publica> KEY_NAME=pedidos360 PUBKEY="ssh-ed25519 AAAA... comentario" \
#     bash 01-cloudshell-backend.sh
#
# MI_IP: la IP desde la que administraras por SSH (curl -s ifconfig.me).
# PUBKEY: el contenido de tu ~/.ssh/id_ed25519.pub (solo la parte publica).
set -euo pipefail
export AWS_DEFAULT_REGION=us-east-1
: "${MI_IP:?definir MI_IP (tu IP publica, sin /32)}"
: "${KEY_NAME:=pedidos360}"
: "${PUBKEY:?definir PUBKEY (contenido de tu llave publica)}"

VPC=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)

# --- Par de llaves: solo se sube la parte publica ---
echo "$PUBKEY" > /tmp/pedidos360.pub
aws ec2 import-key-pair --key-name "$KEY_NAME" --public-key-material fileb:///tmp/pedidos360.pub \
  --query KeyName --output text

# --- SG backend: SSH solo desde tu IP; 443 (frontend) y 8080 (BFF, lo
# llama API Gateway) publicos. Nada mas queda expuesto. ---
SG=$(aws ec2 create-security-group --group-name pedidos360-ec2 --description "Pedidos360 backend" \
  --vpc-id "$VPC" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$SG" --ip-permissions \
  "IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=$MI_IP/32,Description=SSH-admin}]" \
  'IpProtocol=tcp,FromPort=443,ToPort=443,IpRanges=[{CidrIp=0.0.0.0/0,Description=Frontend-HTTPS}]' \
  'IpProtocol=tcp,FromPort=8080,ToPort=8080,IpRanges=[{CidrIp=0.0.0.0/0,Description=BFF-API-Gateway}]' \
  --query Return --output text

# --- EC2 backend ---
AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)
ID=$(aws ec2 run-instances --image-id "$AMI" --instance-type t3.small --key-name "$KEY_NAME" \
  --security-group-ids "$SG" --iam-instance-profile Name=LabInstanceProfile \
  --metadata-options HttpTokens=required,HttpPutResponseHopLimit=2 \
  --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=20,VolumeType=gp3,Encrypted=true}' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=pedidos360-backend}]' \
  --query 'Instances[0].InstanceId' --output text)
aws ec2 wait instance-running --instance-ids "$ID"

# --- IP elastica: no cambia al detener/iniciar el laboratorio ---
ALLOC=$(aws ec2 allocate-address \
  --tag-specifications 'ResourceType=elastic-ip,Tags=[{Key=Name,Value=pedidos360-backend}]' \
  --query AllocationId --output text)
aws ec2 associate-address --instance-id "$ID" --allocation-id "$ALLOC" --query AssociationId --output text
EIP=$(aws ec2 describe-addresses --allocation-ids "$ALLOC" --query 'Addresses[0].PublicIp' --output text)

echo "VPC=$VPC SG_EC2=$SG INSTANCIA=$ID EIP=$EIP"
