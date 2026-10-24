#!/usr/bin/env bash
# Chạy 1 service local, trỏ vào database trong docker-compose.db.yml.
#   docker compose -f docker-compose.db.yml up -d
#   mvn clean install -DskipTests                  # 1 lần, từ thư mục gốc
#   ./scripts/run-local.sh discovery               # mỗi service 1 terminal, theo thứ tự:
#   ./scripts/run-local.sh auth                    #   discovery -> auth -> audit, category, customer
#   ./scripts/run-local.sh audit                   #   -> business -> apigateway
# Port đổi được bằng biến MYSQL_PORT / MSSQL_PORT / ORACLE_PORT / KAFKA_PORT (phải giống lúc chạy compose).
set -euo pipefail

service="${1:-}"
export KAFKA_BOOTSTRAP_SERVERS="localhost:${KAFKA_PORT:-19092}"
case "$service" in
  discovery|apigateway) ;;
  auth|audit|customer)
    export DB_HOST=localhost DB_PORT="${MYSQL_PORT:-13306}" DB_USERNAME=root
    export DB_PASSWORD="${MYSQL_ROOT_PASSWORD:-dangnam}" ;;
  category)
    export DB_HOST=localhost DB_PORT="${MSSQL_PORT:-11433}" DB_USERNAME=sa
    export DB_PASSWORD="${MSSQL_SA_PASSWORD:-Dangnam@2024}" ;;
  business)
    export DB_URL="jdbc:oracle:thin:@//localhost:${ORACLE_PORT:-11521}/FREEPDB1" DB_USERNAME=hr
    export DB_PASSWORD="${ORACLE_HR_PASSWORD:-hr}" ;;
  *)
    echo "Cách dùng: $0 discovery|auth|audit|category|customer|business|apigateway" >&2
    exit 1 ;;
esac
if [ "$service" = auth ]; then
  export ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}" ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123}"
fi

root="$(cd "$(dirname "$0")/.." && pwd)"
echo "Chạy $service (DB_PORT=${DB_PORT:-} DB_URL=${DB_URL:-})"
exec mvn -f "$root/pom.xml" -pl "$service" spring-boot:run
