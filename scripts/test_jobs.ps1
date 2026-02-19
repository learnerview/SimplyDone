# Comprehensive Job Testing Script for SimplyDone

$baseUrl = "http://localhost:8080/api/jobs"
$recipientEmail = "sumit1202shukla@gmail.com"
$testDataDir = "d:\LocalProjects\SimplyDone\tests\data"

# Create test data if missing
if (!(Test-Path $testDataDir)) {
    New-Item -ItemType Directory -Path $testDataDir -Force
    "id,name,value`n1,item1,100`n2,item2,200`n3,item3,300" | Out-File -FilePath "$testDataDir\test.csv" -Encoding UTF8
    "Test content 1" | Out-File -FilePath "$testDataDir\file1.txt"
    "Test content 2" | Out-File -FilePath "$testDataDir\file2.txt"
}

function Submit-Job($type, $message, $params) {
    Write-Host "Submitting $type Job: $message" -ForegroundColor Cyan
    $payload = @{
        userId = "test-user-system"
        jobType = $type
        priority = "HIGH"
        message = $message
        parameters = $params
    } | ConvertTo-Json -Depth 10

    try {
        $response = Invoke-RestMethod -Uri $baseUrl -Method Post -Body $payload -ContentType "application/json"
        $jobId = $response.data.id
        Write-Host "Success! Job ID: $jobId" -ForegroundColor Green
        return $jobId
    } catch {
        Write-Host "Failed to submit $type job: $($_.Exception.Message)" -ForegroundColor Red
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            Write-Host "Error Body: $($reader.ReadToEnd())" -ForegroundColor Yellow
        }
        return $null
    }
}

function Monitor-Jobs($jobIds) {
    Write-Host "`nMonitoring $($jobIds.Count) jobs..." -ForegroundColor Magenta
    $timeout = 60
    $elapsed = 0
    $pending = $jobIds.Clone()

    while ($elapsed -lt $timeout -and $pending.Count -gt 0) {
        $stillPending = @()
        foreach ($id in $pending) {
            $response = Invoke-RestMethod -Uri "$baseUrl/$id" -Method Get
            $status = $response.data.status
            if ($status -eq "EXECUTED") {
                Write-Host "Job ${id}: EXECUTED" -ForegroundColor Green
            } elseif ($status -eq "FAILED") {
                Write-Host "Job ${id}: FAILED - $($response.data.errorMessage)" -ForegroundColor Red
            } else {
                $stillPending += $id
            }
        }
        $pending = $stillPending
        if ($pending.Count -gt 0) {
            Start-Sleep -Seconds 2
            $elapsed += 2
        }
    }

    if ($pending.Count -eq 0) {
        Write-Host "`nAll jobs reached terminal state." -ForegroundColor Green
    } else {
        Write-Host "`nTimed out waiting for jobs: $($pending -join ', ')" -ForegroundColor Yellow
    }
}

# --- Main Execution ---

try {
    Write-Host "--- SimplyDone Comprehensive Job Test ---" -ForegroundColor White -BackgroundColor Blue

    # 1. EMAIL_SEND
    $emailId = Submit-Job "EMAIL_SEND" "Test Email with Custom Credentials" @{
        to = $recipientEmail
        subject = "SimplyDone Multi-Job Test - $(Get-Date)"
        body = "<h1>Comprehensive Test</h1><p>Test email from SimplyDone script.</p>"
        senderEmail = "sumit033345@gmail.com"
        senderPassword = "uwmk ynaf qhbp rlxf"
    }
    Start-Sleep -Seconds 6

    # 2. CLEANUP
    $cleanupId = Submit-Job "CLEANUP" "Cleanup temp test data" @{
        operation = "DELETE_BY_PATTERN"
        directory = "$testDataDir"
        pattern = "*.log"
    }
    Start-Sleep -Seconds 6

    # 3. API_CALL
    $apiId = Submit-Job "API_CALL" "Test external API call" @{
        url = "https://httpbin.org/get"
        method = "GET"
        expectedStatus = 200
    }
    Start-Sleep -Seconds 6

    # 4. DATA_PROCESS
    $dataId = Submit-Job "DATA_PROCESS" "Aggregate test CSV" @{
        operation = "AGGREGATE"
        inputFile = "$testDataDir\test.csv"
        outputFile = "$testDataDir\result.csv"
        groupBy = "name"
        aggregate = "value"
        function = "SUM"
    }
    Start-Sleep -Seconds 6

    # 5. FILE_OPERATION
    $fileId = Submit-Job "FILE_OPERATION" "Zip test files" @{
        operation = "ZIP"
        source = "$testDataDir"
        target = "$testDataDir\test_data.zip"
    }
    Start-Sleep -Seconds 1

    # 6. NOTIFICATION
    $notifId = Submit-Job "NOTIFICATION" "Test generic webhook notification" @{
        channel = "WEBHOOK"
        webhookUrl = "https://httpbin.org/post"
        message = "Test notification message"
        title = "Job Scheduler Notification"
    }
    Start-Sleep -Seconds 1

    # 7. REPORT_GENERATION
    $reportId = Submit-Job "REPORT_GENERATION" "Generate HTML test report" @{
        format = "HTML"
        outputPath = "$testDataDir\report.html"
        title = "Comprehensive Job Report"
        data = @(
            @{ id = 1; job = "Email"; result = "Success" }
            @{ id = 2; job = "API"; result = "Success" }
            @{ id = 3; job = "Data"; result = "Success" }
        )
    }

    $allIds = @($emailId, $cleanupId, $apiId, $dataId, $fileId, $notifId, $reportId) | Where-Object { $_ -ne $null }
    
    if ($allIds.Count -gt 0) {
        Monitor-Jobs $allIds
    }

    Write-Host "`nTest script execution complete." -ForegroundColor Cyan

} catch {
    Write-Error "Error: $($_.Exception.Message)"
}
