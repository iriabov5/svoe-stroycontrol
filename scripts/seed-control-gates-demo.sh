#!/usr/bin/env bash
set -euo pipefail

# Создаёт изолированные локальные данные для просмотра «Контрольных точек».
# Скрипт предназначен только для Docker Compose разработки; production API не изменяется.

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
API_URL=${API_URL:-http://localhost:8080/api/v1}
PASSWORD=${DEMO_PASSWORD:-example-password-123}
STAMP=$(date +%s)
CUSTOMER_EMAIL="gates-customer-${STAMP}@example.test"
CONTRACTOR_EMAIL="gates-contractor-${STAMP}@example.test"
CUSTOMER_COOKIE=$(mktemp)
CONTRACTOR_COOKIE=$(mktemp)
trap 'rm -f "$CUSTOMER_COOKIE" "$CONTRACTOR_COOKIE"' EXIT

csrf() {
    curl -fsS -c "$1" -b "$1" "$API_URL/auth/csrf" | json_field token
}

json_field() {
    sed -nE "s/.*\"$1\":\"([^\"]+)\".*/\1/p"
}

register() {
    curl -fsS -X POST "$API_URL/auth/register" \
        -H "Content-Type: application/json" \
        -H "X-CSRF-TOKEN: $(csrf "$3")" \
        -b "$3" -c "$3" \
        --data "{\"email\":\"$1\",\"password\":\"$PASSWORD\",\"role\":\"$2\",\"displayName\":\"$4\"}" >/dev/null
}

login() {
    curl -fsS -X POST "$API_URL/auth/login" \
        -H "Content-Type: application/json" \
        -H "X-CSRF-TOKEN: $(csrf "$2")" \
        -b "$2" -c "$2" \
        --data "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" >/dev/null
}

create_project() {
    curl -fsS -X POST "$API_URL/projects" \
        -H "Content-Type: application/json" \
        -H "X-CSRF-TOKEN: $(csrf "$CUSTOMER_COOKIE")" \
        -b "$CUSTOMER_COOKIE" -c "$CUSTOMER_COOKIE" \
        --data "{\"name\":\"$1\",\"address\":\"Тестовая улица, $2\"}" \
        | json_field id
}

create_stage() {
    curl -fsS -X POST "$API_URL/projects/$1/stages" \
        -H "Content-Type: application/json" \
        -H "X-CSRF-TOKEN: $(csrf "$CUSTOMER_COOKIE")" \
        -b "$CUSTOMER_COOKIE" -c "$CUSTOMER_COOKIE" \
        --data "{\"name\":\"$2\",\"workType\":\"$3\",\"plannedStart\":\"$4\",\"plannedFinish\":\"$5\"}" \
        | json_field id
}

publish_journal() {
    curl -fsS -X POST "$API_URL/projects/$1/stages/$2/journal" \
        -H "Content-Type: application/json" \
        -H "X-CSRF-TOKEN: $(csrf "$CONTRACTOR_COOKIE")" \
        -b "$CONTRACTOR_COOKIE" -c "$CONTRACTOR_COOKIE" \
        --data "{\"kind\":\"$3\",\"text\":\"$4\",\"photos\":$5,\"attachmentIds\":[],\"sourceEntryId\":null,\"idempotencyKey\":\"$(uuidgen | tr '[:upper:]' '[:lower:]')\"}" >/dev/null
}

register "$CUSTOMER_EMAIL" CUSTOMER "$CUSTOMER_COOKIE" "Анна Заказчик"
register "$CONTRACTOR_EMAIL" CONTRACTOR "$CONTRACTOR_COOKIE" "Илья Подрядчик"
login "$CUSTOMER_EMAIL" "$CUSTOMER_COOKIE"
login "$CONTRACTOR_EMAIL" "$CONTRACTOR_COOKIE"

CUSTOMER_PROJECT=$(create_project "Дом семьи Орловых" "посёлок Берёзки, участок 14")
CONTRACTOR_PROJECT=$(create_project "Каркасный дом на Озёрной" "посёлок Сосны, участок 8")

CONTRACTOR_ID=$(curl -fsS -b "$CONTRACTOR_COOKIE" "$API_URL/auth/me" | json_field id)
docker compose -f "$ROOT_DIR/compose.yaml" exec -T postgres psql -U stroycontrol -d stroycontrol -v ON_ERROR_STOP=1 -c "
update house_projects
set process_mode = 'LEGACY', contractor_id = '$CONTRACTOR_ID',
    area_sqm = 128, floors = 2, wall_material = 'Газобетон D400',
    foundation_type = 'Монолитная лента', roof_type = 'Металлочерепица',
    planned_completion = 'октябрь 2027'
where id = '$CUSTOMER_PROJECT';
update house_projects
set process_mode = 'LEGACY', contractor_id = '$CONTRACTOR_ID',
    area_sqm = 104, floors = 1, wall_material = 'Каркас с утеплением',
    foundation_type = 'УШП', roof_type = 'Фальцевая кровля',
    planned_completion = 'август 2027'
where id = '$CONTRACTOR_PROJECT';" >/dev/null

CUSTOMER_FOUNDATION=$(create_stage "$CUSTOMER_PROJECT" "Фундамент: армирование и заливка" FOUNDATION 2027-04-10 2027-04-30)
create_stage "$CUSTOMER_PROJECT" "Кладка стен первого этажа" WALLS 2027-05-05 2027-06-10 >/dev/null
CONTRACTOR_UTILITIES=$(create_stage "$CONTRACTOR_PROJECT" "Инженерные сети: вводы и разводка" UTILITIES 2027-04-15 2027-05-10)
create_stage "$CONTRACTOR_PROJECT" "Монтаж кровли" ROOF 2027-05-15 2027-06-05 >/dev/null

for stage in "$CUSTOMER_PROJECT:$CUSTOMER_FOUNDATION" "$CONTRACTOR_PROJECT:$CONTRACTOR_UTILITIES"; do
    project_id=${stage%%:*}
    stage_id=${stage##*:}
    curl -fsS -X POST "$API_URL/projects/$project_id/stages/$stage_id/start" \
        -H "Content-Type: application/json" \
        -H "X-CSRF-TOKEN: $(csrf "$CONTRACTOR_COOKIE")" \
        -b "$CONTRACTOR_COOKIE" -c "$CONTRACTOR_COOKIE" \
        --data '{"version":0}' >/dev/null
done

publish_journal "$CUSTOMER_PROJECT" "$CUSTOMER_FOUNDATION" NOTE "Демонстрационный пример: собраны армирование, опалубка и закладные. Перед заливкой нужен осмотр заказчика." '[{"url":"/demo/foundation-reinforcement-example.png","caption":"Демонстрационный пример: армирование и закладные до заливки"}]'
publish_journal "$CUSTOMER_PROJECT" "$CUSTOMER_FOUNDATION" QUESTION "Подтвердите, пожалуйста, осмотр демонстрационных фото армирования до передачи этапа на приёмку." '[]'
publish_journal "$CONTRACTOR_PROJECT" "$CONTRACTOR_UTILITIES" NOTE "Демонстрационный пример: введены трубы водоснабжения и канализации. Нужно приложить результат испытаний до закрытия трасс." '[{"url":"/demo/utilities-example.png","caption":"Демонстрационный пример: инженерные вводы до обратной засыпки"}]'

curl -fsS -X POST "$API_URL/projects/$CUSTOMER_PROJECT/stages/$CUSTOMER_FOUNDATION/complete" \
    -H "Content-Type: application/json" \
    -H "X-CSRF-TOKEN: $(csrf "$CONTRACTOR_COOKIE")" \
    -b "$CONTRACTOR_COOKIE" -c "$CONTRACTOR_COOKIE" \
    --data '{"version":1}' >/dev/null

cat <<EOF
Демонстрационные данные созданы.
Заказчик: $CUSTOMER_EMAIL
Подрядчик: $CONTRACTOR_EMAIL
Пароль: $PASSWORD
Откройте: http://localhost:8088
EOF
