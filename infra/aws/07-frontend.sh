#!/usr/bin/env bash
# Etapa 7 - se ejecuta DESDE TU EQUIPO. Publica el build de Angular en nginx
# (EC2 backend) por HTTPS, con un certificado autofirmado cuyo SAN es la IP
# elastica. MSAL exige HTTPS fuera de localhost.
#
# Antes: en pedidos360-frontend, ajustar src/environments/environment.prod.ts
# (redirectUri = https://<EIP>, apiBaseUrl = <API_URL>/api) y compilar:
#   npm install && npx ng build --configuration production
#
# Uso:  EIP=<IP elastica> FRONT_DIST=../pedidos360-frontend/dist/pedidos360-frontend/browser \
#         bash infra/aws/07-frontend.sh
set -euo pipefail
: "${EIP:?definir EIP}"
: "${FRONT_DIST:?definir FRONT_DIST (carpeta browser del build)}"
[ -f "$FRONT_DIST/index.html" ] || { echo "No hay index.html en $FRONT_DIST"; exit 1; }

TMP=$(mktemp -d)
tar czf "$TMP/front.tgz" -C "$FRONT_DIST" .
cat > "$TMP/pedidos360.conf" <<EOF
server {
    listen 443 ssl;
    http2 on;
    server_name $EIP;
    ssl_certificate     /etc/nginx/ssl/pedidos360.crt;
    ssl_certificate_key /etc/nginx/ssl/pedidos360.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    root /usr/share/nginx/pedidos360;
    index index.html;
    # SPA: cualquier ruta de Angular devuelve index.html
    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF
scp -q "$TMP/front.tgz" "$TMP/pedidos360.conf" p360-backend:/tmp/
rm -rf "$TMP"

ssh p360-backend "set -e
sudo mkdir -p /usr/share/nginx/pedidos360 /etc/nginx/ssl
sudo rm -rf /usr/share/nginx/pedidos360/*
sudo tar xzf /tmp/front.tgz -C /usr/share/nginx/pedidos360
[ -f /etc/nginx/ssl/pedidos360.crt ] || sudo openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -subj '/CN=$EIP' -addext 'subjectAltName=IP:$EIP' \
  -keyout /etc/nginx/ssl/pedidos360.key -out /etc/nginx/ssl/pedidos360.crt 2>/dev/null
sudo mv /tmp/pedidos360.conf /etc/nginx/conf.d/pedidos360.conf
sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl reload nginx"
curl -sk -o /dev/null -w "frontend: %{http_code}\n" "https://$EIP/"
