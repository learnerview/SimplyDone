# Test Docker Deployment Locally
# Simulates Render deployment environment

Write-Host "`n🐳 Testing Docker Deployment for SimplyDone`n" -ForegroundColor Cyan

# Check if Docker is running
try {
    $null = docker version 2>&1
} catch {
    Write-Host "❌ Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

Write-Host "✅ Docker is running`n" -ForegroundColor Green

# Stop and remove existing containers
Write-Host "🧹 Cleaning up existing containers..." -ForegroundColor Yellow
docker-compose down -v 2>&1 | Out-Null

# Build the application
Write-Host "`n📦 Building application with Maven..." -ForegroundColor Cyan
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Maven build failed" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Maven build successful`n" -ForegroundColor Green

# Build Docker image
Write-Host "🐳 Building Docker image..." -ForegroundColor Cyan
docker build -t simplydone:test .
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Docker build failed" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Docker image built successfully`n" -ForegroundColor Green

# Start services with docker-compose
Write-Host "🚀 Starting services with Docker Compose..." -ForegroundColor Cyan
docker-compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to start services" -ForegroundColor Red
    exit 1
}

Write-Host "`n⏳ Waiting for services to be healthy..." -ForegroundColor Yellow
$maxWait = 60
$waited = 0
$allHealthy = $false

while ($waited -lt $maxWait) {
    Start-Sleep -Seconds 2
    $waited += 2
    
    $redis = docker ps --filter "name=simplydone-redis" --filter "health=healthy" --format "{{.Names}}"
    $postgres = docker ps --filter "name=simplydone-postgres" --filter "health=healthy" --format "{{.Names}}"
    
    if ($redis -and $postgres) {
        $allHealthy = $true
        break
    }
    
    Write-Host "." -NoNewline
}

Write-Host ""

if (-not $allHealthy) {
    Write-Host "❌ Services did not become healthy in time" -ForegroundColor Red
    docker-compose logs
    exit 1
}

Write-Host "✅ All services are healthy`n" -ForegroundColor Green

# Start the application container
Write-Host "🚀 Starting SimplyDone application..." -ForegroundColor Cyan
docker run -d `
    --name simplydone-app-test `
    --network simplydone_default `
    -p 8080:8080 `
    -e SPRING_PROFILES_ACTIVE=prod `
    -e DATABASE_URL=jdbc:postgresql://simplydone-postgres:5432/simplydone `
    -e DATABASE_USER=postgres `
    -e DATABASE_PASSWORD=postgres `
    -e REDIS_URL=redis://simplydone-redis:6379 `
    -e EMAIL_ENABLED=false `
    -e EMAIL_API_KEY=test_key `
    -e EMAIL_FROM_ADDRESS=test@example.com `
    simplydone:test

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to start application" -ForegroundColor Red
    exit 1
}

Write-Host "`n⏳ Waiting for application to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 20

# Test health endpoint
Write-Host "`n🏥 Testing application health..." -ForegroundColor Cyan
$maxHealthChecks = 30
$healthCheckWait = 0

while ($healthCheckWait -lt $maxHealthChecks) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 2>$null
        if ($response.StatusCode -eq 200) {
            Write-Host "✅ Application is healthy!" -ForegroundColor Green
            $healthData = $response.Content | ConvertFrom-Json
            Write-Host "`nHealth Status:" -ForegroundColor Cyan
            Write-Host "  Status: $($healthData.status)" -ForegroundColor White
            break
        }
    } catch {
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 2
        $healthCheckWait++
    }
}

if ($healthCheckWait -ge $maxHealthChecks) {
    Write-Host "`n❌ Application health check failed" -ForegroundColor Red
    Write-Host "`nApplication Logs:" -ForegroundColor Yellow
    docker logs simplydone-app-test --tail 50
    docker stop simplydone-app-test 2>&1 | Out-Null
    docker rm simplydone-app-test 2>&1 | Out-Null
    docker-compose down 2>&1 | Out-Null
    exit 1
}

# Run comprehensive tests
Write-Host "`n🧪 Running comprehensive test suite..." -ForegroundColor Cyan
Start-Sleep -Seconds 5
.\scripts\test-comprehensive.ps1 -BaseUrl "http://localhost:8080"

$testResult = $LASTEXITCODE

# Show logs
Write-Host "`n📋 Application Logs (last 30 lines):" -ForegroundColor Cyan
docker logs simplydone-app-test --tail 30

# Cleanup
Write-Host "`n🧹 Cleaning up..." -ForegroundColor Yellow
docker stop simplydone-app-test 2>&1 | Out-Null
docker rm simplydone-app-test 2>&1 | Out-Null
docker-compose down 2>&1 | Out-Null

if ($testResult -eq 0) {
    Write-Host "`n✨ Docker deployment test completed successfully!`n" -ForegroundColor Green
    Write-Host "🚀 Your application is ready to deploy to Render!`n" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host "`n❌ Docker deployment test failed`n" -ForegroundColor Red
    exit 1
}
