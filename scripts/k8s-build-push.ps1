# Build + push image của mọi service lên registry để deploy Kubernetes (k8s/).
#   docker login <registry>
#   .\scripts\k8s-build-push.ps1 registry.example.com 1.0.0
#   -> registry.example.com/grpc-ms/<service>:1.0.0
# Sau đó sửa newName / newTag trong k8s/kustomization.yaml cho khớp rồi: kubectl apply -k k8s/
param(
    [Parameter(Mandatory = $true)]
    [string]$Registry,
    [string]$Tag = "latest"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

foreach ($service in "discovery", "auth", "category", "customer", "business", "audit", "apigateway") {
    $image = "$Registry/grpc-ms/${service}:$Tag"
    Write-Host "==> $image"
    docker build --build-arg SERVICE=$service -t $image $root
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    docker push $image
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
