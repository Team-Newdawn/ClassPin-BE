#!/usr/bin/env bash
set -Eeuo pipefail

deployment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$deployment_dir"

image_tag="${IMAGE_TAG:?IMAGE_TAG is required}"
image_archive="ohpin-be-image.tar.gz"
image_name="ohpin-be"

if [[ ! -f .env.prod || ! -f "$image_archive" ]]; then
  echo "Release files are incomplete in $deployment_dir" >&2
  exit 1
fi

api_bind_address="$(awk -F= '$1 == "API_BIND_ADDRESS" { print substr($0, index($0, "=") + 1) }' .env.prod | tail -n 1)"
api_port="$(awk -F= '$1 == "API_PORT" { print substr($0, index($0, "=") + 1) }' .env.prod | tail -n 1)"
management_port="$(awk -F= '$1 == "MANAGEMENT_PORT" { print substr($0, index($0, "=") + 1) }' .env.prod | tail -n 1)"
management_port="${management_port:-8083}"

if [[ "$api_bind_address" != "127.0.0.1" ]]; then
  echo "API_BIND_ADDRESS must remain 127.0.0.1 behind Nginx" >&2
  exit 1
fi
if [[ ! "$api_port" =~ ^[0-9]+$ ]] || ((api_port < 1 || api_port > 65535)); then
  echo "API_PORT must be a valid TCP port" >&2
  exit 1
fi
if [[ ! "$management_port" =~ ^[0-9]+$ ]] || ((management_port < 1 || management_port > 65535)); then
  echo "MANAGEMENT_PORT must be a valid TCP port" >&2
  exit 1
fi

previous_image="$(docker image inspect --format '{{.Id}}' "${image_name}:latest" 2>/dev/null || true)"

compose=(docker compose -f compose.yml -f compose.observability.yml --env-file .env.prod)

gzip -dc "$image_archive" | docker load
docker tag "${image_name}:${image_tag}" "${image_name}:latest"
"${compose[@]}" up -d --no-build --force-recreate api
"${compose[@]}" up -d --no-build prometheus loki grafana alloy cadvisor

health_url="http://127.0.0.1:${management_port}/actuator/health"
healthy=false
for _ in $(seq 1 30); do
  if curl --fail --silent --show-error "$health_url" | grep -q '"status":"UP"'; then
    healthy=true
    break
  fi
  sleep 5
done

if [[ "$healthy" == true ]]; then
  rm -f "$image_archive"
  "${compose[@]}" ps
  echo "OhPin backend deployment completed: ${image_tag}"
  exit 0
fi

echo "New release failed its health check" >&2
"${compose[@]}" logs --no-color --tail=200 api >&2 || true

if [[ -n "$previous_image" ]]; then
  echo "Restoring previous image ${previous_image}" >&2
  docker tag "$previous_image" "${image_name}:latest"
  "${compose[@]}" up -d --no-build --force-recreate api
fi

exit 1
