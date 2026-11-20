#!/usr/bin/env bash
# Etapa 6 - se ejecuta EN la EC2 backend (o en CloudShell). Crea el HTTP API
# de API Gateway: authorizer JWT contra Entra ID, una ruta por endpoint con
# su scope y CORS solo para el frontend. Todas las rutas apuntan al BFF.
#
# Uso:  EIP=<IP elastica backend> TENANT=<tenant id> CLIENT=<client id> bash 06-apigw.sh
set -euo pipefail
export AWS_DEFAULT_REGION=us-east-1
: "${EIP:?definir EIP (IP elastica de la EC2 backend)}"
: "${TENANT:?definir TENANT (Directory/tenant ID de Entra)}"
: "${CLIENT:?definir CLIENT (Application/client ID de Pedidos360-SPA)}"
BFF="http://$EIP:8080"

API=$(aws apigatewayv2 create-api --name pedidos360-api --protocol-type HTTP \
  --cors-configuration "{\"AllowOrigins\":[\"https://$EIP\"],\"AllowHeaders\":[\"authorization\",\"content-type\"],\"AllowMethods\":[\"GET\",\"POST\",\"PUT\",\"DELETE\",\"OPTIONS\"],\"AllowCredentials\":false,\"MaxAge\":300}" \
  --query ApiId --output text)

# El token de acceso de Entra (v2) trae aud = client id e iss = .../v2.0.
AUTH=$(aws apigatewayv2 create-authorizer --api-id "$API" --name entra-jwt --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration "Audience=$CLIENT,Issuer=https://login.microsoftonline.com/$TENANT/v2.0" \
  --query AuthorizerId --output text)

ruta() {
  local metodo=$1 path=$2 scope=$3 integ
  integ=$(aws apigatewayv2 create-integration --api-id "$API" --integration-type HTTP_PROXY \
    --integration-method "$metodo" --integration-uri "$BFF$path" --payload-format-version 1.0 \
    --query IntegrationId --output text)
  aws apigatewayv2 create-route --api-id "$API" --route-key "$metodo $path" \
    --target "integrations/$integ" --authorization-type JWT --authorizer-id "$AUTH" \
    --authorization-scopes "$scope" --query RouteKey --output text
}
ruta GET    /api/productos             pedidos.read
ruta POST   /api/productos             pedidos.write
ruta GET    /api/productos/{id}        pedidos.read
ruta PUT    /api/productos/{id}        pedidos.write
ruta DELETE /api/productos/{id}        pedidos.write
ruta GET    /api/pedidos               pedidos.read
ruta POST   /api/pedidos               pedidos.write
ruta GET    /api/pedidos/{id}          pedidos.read
ruta PUT    /api/pedidos/{id}/estado   pedidos.write

aws apigatewayv2 create-stage --api-id "$API" --stage-name '$default' --auto-deploy --query StageName --output text
echo "API_URL=https://$API.execute-api.us-east-1.amazonaws.com"
