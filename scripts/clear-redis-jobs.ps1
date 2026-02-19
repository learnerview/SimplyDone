# Clear all jobs from Redis queues

Write-Host "🧹 Clearing all jobs from Redis queues..." -ForegroundColor Yellow

# Redis connection details
$redisHost = "localhost"
$redisPort = 6380

# Check if redis-cli is available
$redisCli = Get-Command redis-cli -ErrorAction SilentlyContinue

if ($redisCli) {
    Write-Host "Using redis-cli to clear queues..." -ForegroundColor Cyan
    
    # Clear high priority queue
    redis-cli -h $redisHost -p $redisPort DEL "jobs:high"
    Write-Host "✅ Cleared jobs:high" -ForegroundColor Green
    
    # Clear low priority queue
    redis-cli -h $redisHost -p $redisPort DEL "jobs:low"
    Write-Host "✅ Cleared jobs:low" -ForegroundColor Green
    
    # Clear dead letter queue
    redis-cli -h $redisHost -p $redisPort DEL "jobs:dead-letter"
    Write-Host "✅ Cleared jobs:dead-letter" -ForegroundColor Green
    
    # Reset counters
    redis-cli -h $redisHost -p $redisPort DEL "stats:executed"
    redis-cli -h $redisHost -p $redisPort DEL "stats:rejected"
    Write-Host "✅ Reset statistics" -ForegroundColor Green
    
    Write-Host "`n✨ All queues cleared successfully!" -ForegroundColor Green
} else {
    Write-Host "❌ redis-cli not found. Using REST API instead..." -ForegroundColor Yellow
    
    # Using the admin endpoint if available
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/clear-queues" -Method Post
        Write-Host "✅ Queues cleared via API" -ForegroundColor Green
    } catch {
        Write-Host "❌ Could not clear queues. Please install redis-cli or restart Redis." -ForegroundColor Red
        Write-Host "   Install redis-cli: choco install redis-64 (requires Chocolatey)" -ForegroundColor Yellow
    }
}
