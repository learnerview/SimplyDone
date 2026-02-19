# Test File Upload Feature

Write-Host "🧪 Testing File Upload Feature" -ForegroundColor Cyan

# Create a test CSV file
$testContent = @"
id,name,value
1,Alice,100
2,Bob,200
3,Charlie,300
"@

$testFile = "d:\LocalProjects\SimplyDone\test-upload.csv"
$testContent | Out-File -FilePath $testFile -Encoding UTF8

Write-Host "✅ Created test file: $testFile" -ForegroundColor Green

# Wait for server to be ready
Write-Host "`n⏳ Waiting for server..." -ForegroundColor Yellow
Start-Sleep -Seconds 2

# Test file upload
Write-Host "`n📤 Uploading file..." -ForegroundColor Cyan
try {
    $uploadUrl = "http://localhost:8080/api/files/upload"
    
    # PowerShell multipart upload
    Add-Type -AssemblyName System.Net.Http
    $httpClient = New-Object System.Net.Http.HttpClient
    $content = New-Object System.Net.Http.MultipartFormDataContent
    
    $fileStream = [System.IO.File]::OpenRead($testFile)
    $streamContent = New-Object System.Net.Http.StreamContent($fileStream)
    $streamContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("text/csv")
    
    $content.Add($streamContent, "file", "test-upload.csv")
    
    $response = $httpClient.PostAsync($uploadUrl, $content).Result
    $responseBody = $response.Content.ReadAsStringAsync().Result
    
    $fileStream.Close()
    $httpClient.Dispose()
    
    if ($response.IsSuccessStatusCode) {
        Write-Host "✅ Upload successful!" -ForegroundColor Green
        $result = $responseBody | ConvertFrom-Json
        Write-Host "`nUpload Details:" -ForegroundColor Cyan
        Write-Host "  File ID: $($result.fileId)" -ForegroundColor White
        Write-Host "  File Path: $($result.filePath)" -ForegroundColor White
        Write-Host "  File Size: $($result.fileSize) bytes" -ForegroundColor White
        Write-Host "  Uploaded At: $($result.uploadedAt)" -ForegroundColor White
        Write-Host "  Expires At: $($result.expiresAt)" -ForegroundColor White
        
        # Test listing files
        Write-Host "`n📋 Listing uploaded files..." -ForegroundColor Cyan
        $listResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/files/list" -Method Get
        Write-Host "✅ Found $($listResponse.count) file(s)" -ForegroundColor Green
        
        # Submit a job using the uploaded file
        Write-Host "`n🚀 Submitting data processing job with uploaded file..." -ForegroundColor Cyan
        $jobData = @{
            jobType = "DATA_PROCESS"
            message = "Process uploaded test file"
            priority = "HIGH"
            userId = "test-user"
            delay = 0
            parameters = @{
                operation = "TRANSFORM"
                inputFile = $result.filePath
                outputFile = "d:\LocalProjects\SimplyDone\temp_uploads\output.csv"
                transformations = @("UPPERCASE")
            }
        } | ConvertTo-Json -Depth 10
        
        $jobResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/jobs" -Method Post -ContentType "application/json" -Body $jobData
        Write-Host "✅ Job submitted: $($jobResponse.id)" -ForegroundColor Green
        
    } else {
        Write-Host "❌ Upload failed: $($response.StatusCode)" -ForegroundColor Red
        Write-Host $responseBody -ForegroundColor Red
    }
    
} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n✨ Test completed!" -ForegroundColor Green
Write-Host "`nNote: Uploaded file will be automatically deleted after 10 minutes." -ForegroundColor Yellow
