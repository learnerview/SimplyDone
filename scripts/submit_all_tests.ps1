# Comprehensive Test Suite for SimplyDone

$baseUrl = "http://localhost:8080/api/jobs"

function Submit-Job($description, $payload) {
    Write-Host "`n🚀 Submitting: $description" -ForegroundColor Cyan
    try {
        $response = Invoke-RestMethod -Uri $baseUrl -Method Post -ContentType "application/json" -Body $payload
        Write-Host "✅ Success: Job ID $($response.id)" -ForegroundColor Green
        return $response.id
    } catch {
        Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

# 1. API_CALL
Submit-Job "API Call Job" '{
    "message": "Ping httpbin",
    "priority": "HIGH",
    "delay": 0,
    "userId": "test-suite",
    "jobType": "API_CALL",
    "parameters": {
        "url": "https://httpbin.org/get",
        "method": "GET"
    }
}'

# 2. EMAIL_SEND
Submit-Job "Email Job" '{
    "message": "Test email to Sumit",
    "priority": "HIGH",
    "delay": 0,
    "userId": "test-suite",
    "jobType": "EMAIL_SEND",
    "parameters": {
        "to": "sumit033345@gmail.com",
        "subject": "Verified: SimplyDone Email Service",
        "body": "<h1>SimplyDone Testing</h1><p>This email confirms that the email job type is working correctly using the Resend onboarding domain.</p>"
    }
}'

# 3. NOTIFICATION (Webhook)
Submit-Job "Notification Job" '{
    "message": "Test notification",
    "priority": "HIGH",
    "delay": 0,
    "userId": "test-suite",
    "jobType": "NOTIFICATION",
    "parameters": {
        "channel": "WEBHOOK",
        "title": "Test Notification",
        "webhookUrl": "https://httpbin.org/post",
        "message": "Notification system check"
    }
}'

# 4. REPORT_GENERATION
Submit-Job "Report Job" '{
    "message": "Generate test report",
    "priority": "LOW",
    "delay": 0,
    "userId": "test-suite",
    "jobType": "REPORT_GENERATION",
    "parameters": {
        "format": "HTML",
        "outputPath": "d:/LocalProjects/SimplyDone/target/test-report.html",
        "title": "System Test Report",
        "data": [
            {"module": "Scheduler", "status": "Tested"},
            {"module": "Worker", "status": "Verified"}
        ]
    }
}'

# 5. FILE_OPERATION (Copy)
# Create a dummy file first
if (!(Test-Path "d:/LocalProjects/SimplyDone/target")) { New-Item -ItemType Directory -Path "d:/LocalProjects/SimplyDone/target" }
"Dummy content" | Out-File -FilePath "d:/LocalProjects/SimplyDone/target/test-source.txt"
Submit-Job "File Operation Job" '{
    "message": "Copy test file",
    "priority": "LOW",
    "delay": 0,
    "userId": "test-suite",
    "jobType": "FILE_OPERATION",
    "parameters": {
        "operation": "COPY",
        "source": "d:/LocalProjects/SimplyDone/target/test-source.txt",
        "target": "d:/LocalProjects/SimplyDone/target/test-copy.txt"
    }
}'

# 6. DATA_PROCESS
# Create a dummy CSV first
"id,name,value`n1,Alice,10`n2,Bob,20" | Out-File -FilePath "d:/LocalProjects/SimplyDone/target/test-data.csv"
Submit-Job "Data Process Job" '{
    "message": "Normalize CSV",
    "priority": "LOW",
    "delay": 0,
    "userId": "test-suite",
    "jobType": "DATA_PROCESS",
    "parameters": {
        "operation": "TRANSFORM",
        "inputFile": "d:/LocalProjects/SimplyDone/target/test-data.csv",
        "outputFile": "d:/LocalProjects/SimplyDone/target/test-data-out.csv",
        "transformations": ["UPPERCASE"]
    }
}'

# 7. CLEANUP
Submit-Job "Cleanup Job" '{
    "message": "Clean test dir",
    "priority": "LOW",
    "delay": 0,
    "userId": "test-suite",
    "jobType": "CLEANUP",
    "parameters": {
        "operation": "DELETE_BY_PATTERN",
        "directory": "d:/LocalProjects/SimplyDone/target",
        "pattern": "test-source.txt"
    }
}'
