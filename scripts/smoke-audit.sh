#!/usr/bin/env bash
# Kiểm tra end-to-end hệ thống audit log qua gateway (hệ thống phải đang chạy:
# docker compose up -d, hoặc docker-compose.db.yml + scripts/run-local.*).
#   ./scripts/smoke-audit.sh                 # gateway mặc định http://localhost:8080
#   G=http://localhost:8085 ./scripts/smoke-audit.sh
# Tạo + sửa 1 category bằng ADMIN, rồi kiểm tra audit-service đã lưu (qua Kafka) log HTTP (IP, curl, body)
# và log ENTITY_CHANGE (giá trị cũ -> mới) của đúng request đó; id là UUID v7, có field của BaseEntity.
set -euo pipefail

G="${G:-http://localhost:8080}"
EMAIL="${ADMIN_EMAIL:-admin@example.com}"
PASSWORD="${ADMIN_PASSWORD:-Admin@123}"
RUN="$(date +%s)"
CODE="SMOKE_$RUN"
J="Content-Type: application/json"

fail() { echo "FAIL: $*" >&2; exit 1; }
step() { echo; echo "== $*"; }

step "Đăng nhập ADMIN ($EMAIL)"
LOGIN=$(curl -s -X POST "$G/api/auth/login" -H "$J" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(echo "$LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || fail "không lấy được accessToken: $LOGIN"
AUTH="Authorization: Bearer $TOKEN"

step "Tạo category $CODE (X-Request-Id: smoke-$RUN-create)"
CREATED=$(curl -s -X POST "$G/api/category" -H "$J" -H "$AUTH" -H "X-Request-Id: smoke-$RUN-create" \
  -d "{\"code\":\"$CODE\",\"name\":\"Smoke test\"}")
echo "$CREATED"
ID=$(echo "$CREATED" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
VERSION=$(echo "$CREATED" | sed -n 's/.*"version":\([0-9]*\).*/\1/p')
[ -n "$ID" ] || fail "tạo category lỗi"

step "Đổi tên category id=$ID (X-Request-Id: smoke-$RUN-update)"
curl -s -X PUT "$G/api/category/$ID" -H "$J" -H "$AUTH" -H "X-Request-Id: smoke-$RUN-update" \
  -d "{\"name\":\"Smoke test updated\",\"active\":true,\"version\":$VERSION}"
echo

# Log được gửi theo lô mỗi ~1s: đợi tối đa 15s
step "Đợi audit-service nhận log"
for i in $(seq 1 15); do
  TIMELINE=$(curl -s "$G/api/audit/requests/smoke-$RUN-update" -H "$AUTH")
  if echo "$TIMELINE" | grep -q '"ENTITY_CHANGE"' && echo "$TIMELINE" | grep -q '"HTTP"'; then
    break
  fi
  sleep 1
done

echo "$TIMELINE" | grep -q '"type":"HTTP"' || fail "không thấy log HTTP của request update: $TIMELINE"
echo "$TIMELINE" | grep -q '"type":"ENTITY_CHANGE"' || fail "không thấy log ENTITY_CHANGE: $TIMELINE"
echo "$TIMELINE" | grep -q '"field":"name"' || fail "log thay đổi không có field name: $TIMELINE"
echo "$TIMELINE" | grep -q 'curl -X PUT' || fail "log HTTP không có lệnh curl"
echo "$TIMELINE" | grep -q "$TOKEN" && fail "token bị lưu nguyên văn trong log"
# BaseEntity: người sửa + thời gian sửa cũng nằm trong nội dung thay đổi
echo "$TIMELINE" | grep -q '"field":"lastModifyDate"' || fail "log thay đổi không có lastModifyDate (BaseEntity)"
echo "$ID" | grep -qE '^[0-9a-f]{8}-[0-9a-f]{4}-7' || fail "id không phải UUID v7: $ID"

step "Log của request update"
echo "$TIMELINE"

step "Lịch sử thay đổi của Category id=$ID"
curl -s "$G/api/audit?type=ENTITY_CHANGE&entityName=Category&entityId=$ID" -H "$AUTH"
echo

echo
echo "OK: audit log hoạt động (HTTP + ENTITY_CHANGE, token đã được che)"
