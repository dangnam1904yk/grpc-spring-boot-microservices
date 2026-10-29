#!/usr/bin/env bash
# Build + push image của mọi service lên registry để deploy Kubernetes (k8s/).
#   docker login <registry>
#   ./scripts/k8s-build-push.sh registry.example.com 1.0.0
#   -> registry.example.com/grpc-ms/<service>:1.0.0
# Sau đó sửa newName / newTag trong k8s/kustomization.yaml cho khớp rồi: kubectl apply -k k8s/
set -euo pipefail

registry="${1:-}"
tag="${2:-latest}"
if [ -z "$registry" ]; then
  echo "Cách dùng: $0 <registry> [tag]" >&2
  exit 1
fi

root="$(cd "$(dirname "$0")/.." && pwd)"
for service in discovery auth category customer business audit apigateway; do
  image="$registry/grpc-ms/$service:$tag"
  echo "==> $image"
  docker build --build-arg SERVICE="$service" -t "$image" "$root"
  docker push "$image"
done
