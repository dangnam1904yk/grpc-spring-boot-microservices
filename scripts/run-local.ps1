# Chạy 1 service local, trỏ vào database trong docker-compose.db.yml.
#   docker compose -f docker-compose.db.yml up -d
#   mvn clean install -DskipTests                  # 1 lần, từ thư mục gốc
#   .\scripts\run-local.ps1 discovery              # mỗi service 1 terminal, theo thứ tự:
#   .\scripts\run-local.ps1 auth                   #   discovery -> auth -> audit, category, customer
#   .\scripts\run-local.ps1 audit                  #   -> business -> apigateway
#   ...
# Port đổi được bằng biến MYSQL_PORT / MSSQL_PORT / ORACLE_PORT / KAFKA_PORT (phải giống lúc chạy compose).
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("discovery", "auth", "audit", "category", "customer", "business", "apigateway")]
    [string]$Service
)

$ErrorActionPreference = "Stop"

function Get-OrDefault([string]$name, [string]$default) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrEmpty($value)) { return $default }
    return $value
}

$mysqlPort = Get-OrDefault "MYSQL_PORT" "13306"
$mssqlPort = Get-OrDefault "MSSQL_PORT" "11433"
$oraclePort = Get-OrDefault "ORACLE_PORT" "11521"
$kafkaPort = Get-OrDefault "KAFKA_PORT" "19092"

# Biến chỉ có hiệu lực trong tiến trình này, không ghi vào hệ thống
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:$kafkaPort"
switch ($Service) {
    { $_ -in "auth", "audit", "customer" } {
        $env:DB_HOST = "localhost"
        $env:DB_PORT = $mysqlPort
        $env:DB_USERNAME = "root"
        $env:DB_PASSWORD = Get-OrDefault "MYSQL_ROOT_PASSWORD" "dangnam"
    }
    "category" {
        $env:DB_HOST = "localhost"
        $env:DB_PORT = $mssqlPort
        $env:DB_USERNAME = "sa"
        $env:DB_PASSWORD = Get-OrDefault "MSSQL_SA_PASSWORD" "Dangnam@2024"
    }
    "business" {
        $env:DB_URL = "jdbc:oracle:thin:@//localhost:$oraclePort/FREEPDB1"
        $env:DB_USERNAME = "hr"
        $env:DB_PASSWORD = Get-OrDefault "ORACLE_HR_PASSWORD" "hr"
    }
}
if ($Service -eq "auth") {
    $env:ADMIN_EMAIL = Get-OrDefault "ADMIN_EMAIL" "admin@example.com"
    $env:ADMIN_PASSWORD = Get-OrDefault "ADMIN_PASSWORD" "Admin@123"
}

$root = Split-Path -Parent $PSScriptRoot
Write-Host "Chạy $Service (DB_PORT=$env:DB_PORT DB_URL=$env:DB_URL)"
mvn -f "$root\pom.xml" -pl $Service spring-boot:run
