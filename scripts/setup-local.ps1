# SimplyDone Local Setup & Test Script
# One-click solution to start dependencies and run the app safely

Write-Host "🚀 Starting SimplyDone Local Environment Setup" -ForegroundColor Cyan

# 1. Check for Docker
if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Docker is not installed or not in PATH. Please install Docker Desktop." -ForegroundColor Red
    exit 1
}

# 2. Free up default ports if requested/necessary
$ports = 5432, 6379, 8080
Write-Host "🔍 Checking for port conflicts..." -ForegroundColor Gray
foreach ($port in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conn) {
        Write-Host "⚠️ Port $port is busy. You might want to stop the conflicting service." -ForegroundColor Yellow
    }
}

# 3. Start Dependencies via Docker Compose
Write-Host "🐳 Starting Docker dependencies (Redis, Postgres)..." -ForegroundColor Cyan
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to start Docker containers." -ForegroundColor Red
    exit 1
}

# 4. Wait for healthy containers
Write-Host "⏳ Waiting for dependencies to initialize..." -ForegroundColor Gray
Start-Sleep -Seconds 5

# 5. Launch Spring Boot App
Write-Host "☕ Launching SimplyDone (Spring Boot)..." -ForegroundColor Cyan
$env:SPRING_PROFILES_ACTIVE = "local"
mvn spring-boot:run
