# Comprehensive SimplyDone Test Suite
# Runs health checks, job submissions, file uploads, and readiness checks.

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$script:passedTests = 0
$script:failedTests = 0
$script:totalTests = 0
$script:failures = @()

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "==== $Title ===="
}

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Url,
        [string]$Method = "GET",
        [object]$Body = $null,
        [int]$ExpectedStatus = 200,
        [switch]$ReturnResponse
    )

    $script:totalTests++
    Write-Host "  Testing: $Name..." -NoNewline

    try {
        $params = @{
            Uri = "$BaseUrl$Url"
            Method = $Method
            UseBasicParsing = $true
            ErrorAction = "Stop"
        }

        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json -Depth 10)
            $params.ContentType = "application/json"
        }

        $response = Invoke-WebRequest @params
        if ($response.StatusCode -eq $ExpectedStatus) {
            Write-Host " PASS"
            $script:passedTests++
            if ($ReturnResponse) {
                return $response
            }
            return $null
        }

        Write-Host " FAIL (Expected $ExpectedStatus, got $($response.StatusCode))"
        $script:failedTests++
        $script:failures += "$Name (Status $($response.StatusCode))"
        return $null
    } catch {
        Write-Host " FAIL ($($_.Exception.Message))"
        $script:failedTests++
        $script:failures += "$Name ($($_.Exception.Message))"
        return $null
    }
}

function Submit-Job {
    param(
        [string]$JobType,
        [object]$Parameters,
        [string]$Message = "Test job"
    )

    $jobData = @{
        jobType = $JobType
        message = $Message
        priority = "HIGH"
        userId = "test-suite"
        delay = 0
        parameters = $Parameters
    }

    $response = Test-Endpoint -Name "$JobType job submission" -Url "/api/jobs" -Method "POST" -Body $jobData -ExpectedStatus 201 -ReturnResponse
    if ($response) {
        $result = $response.Content | ConvertFrom-Json
        return $result.id
    }

    return $null
}

Write-Host ""
Write-Host "SimplyDone Comprehensive Test Suite"

# 1. Health Checks
Write-Section "1. Health and Connectivity"
Test-Endpoint -Name "Application health" -Url "/actuator/health"
Test-Endpoint -Name "Actuator root" -Url "/actuator"
Test-Endpoint -Name "Home page" -Url "/"

# 2. Job Submission Tests
Write-Section "2. Job Submission"

$null = Submit-Job -JobType "API_CALL" -Message "Test API call" -Parameters @{
    url = "https://httpbin.org/get"
    method = "GET"
}

$null = Submit-Job -JobType "EMAIL_SEND" -Message "Test email" -Parameters @{
    to = "sumit033345@gmail.com"
    subject = "SimplyDone Test Email"
    body = "<h1>Test</h1><p>This is a test email from SimplyDone.</p>"
}

$null = Submit-Job -JobType "NOTIFICATION" -Message "Test notification" -Parameters @{
    channel = "WEBHOOK"
    webhookUrl = "https://httpbin.org/post"
    title = "Test Notification"
    message = "This is a test notification"
}

$null = Submit-Job -JobType "REPORT_GENERATION" -Message "Generate test report" -Parameters @{
    format = "HTML"
    outputPath = "d:/LocalProjects/SimplyDone/target/test-report-comprehensive.html"
    title = "Comprehensive Test Report"
    data = @(
        @{ module = "API"; status = "Tested" }
        @{ module = "Email"; status = "Tested" }
        @{ module = "Jobs"; status = "Tested" }
    )
}

"Test content for file operations" | Out-File -FilePath "d:/LocalProjects/SimplyDone/target/test-source-comprehensive.txt" -NoNewline
$null = Submit-Job -JobType "FILE_OPERATION" -Message "Copy test file" -Parameters @{
    operation = "COPY"
    source = "d:/LocalProjects/SimplyDone/target/test-source-comprehensive.txt"
    target = "d:/LocalProjects/SimplyDone/target/test-copy-comprehensive.txt"
}

"id,name,value`n1,Alice,100`n2,Bob,200" | Out-File -FilePath "d:/LocalProjects/SimplyDone/target/test-data-comprehensive.csv" -NoNewline
$null = Submit-Job -JobType "DATA_PROCESS" -Message "Transform test data" -Parameters @{
    operation = "TRANSFORM"
    inputFile = "d:/LocalProjects/SimplyDone/target/test-data-comprehensive.csv"
    outputFile = "d:/LocalProjects/SimplyDone/target/test-data-comprehensive-out.csv"
    transformations = @("UPPERCASE")
}

New-Item -ItemType Directory -Path "d:/LocalProjects/SimplyDone/temp_test" -Force | Out-Null
"cleanup test" | Out-File -FilePath "d:/LocalProjects/SimplyDone/temp_test/cleanup-test.txt"
$null = Submit-Job -JobType "CLEANUP" -Message "Clean test directory" -Parameters @{
    operation = "DELETE_BY_PATTERN"
    directory = "d:/LocalProjects/SimplyDone/temp_test"
    pattern = "cleanup-test.txt"
}

# 3. Queue Tests
Write-Section "3. Queue Checks"
Test-Endpoint -Name "High priority queue" -Url "/api/admin/queues/high"
Test-Endpoint -Name "Low priority queue" -Url "/api/admin/queues/low"
Test-Endpoint -Name "Queue statistics" -Url "/api/admin/stats"

# 4. File Upload Tests
Write-Section "4. File Upload"

$uploadTestFile = "d:/LocalProjects/SimplyDone/target/upload-test.csv"
"id,name,score`n1,Test,95`n2,Demo,88" | Out-File -FilePath $uploadTestFile -NoNewline

try {
    Add-Type -AssemblyName System.Net.Http
    $httpClient = New-Object System.Net.Http.HttpClient
    $content = New-Object System.Net.Http.MultipartFormDataContent

    $fileStream = [System.IO.File]::OpenRead($uploadTestFile)
    $streamContent = New-Object System.Net.Http.StreamContent($fileStream)
    $streamContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("text/csv")
    $content.Add($streamContent, "file", "upload-test.csv")

    $script:totalTests++
    Write-Host "  Testing: File upload..." -NoNewline

    $response = $httpClient.PostAsync("$BaseUrl/api/files/upload", $content).Result
    $responseBody = $response.Content.ReadAsStringAsync().Result

    $fileStream.Close()
    $httpClient.Dispose()

    if ($response.IsSuccessStatusCode) {
        Write-Host " PASS"
        $script:passedTests++
        $null = $responseBody | ConvertFrom-Json
        Test-Endpoint -Name "List uploaded files" -Url "/api/files"
    } else {
        Write-Host " FAIL ($($response.StatusCode))"
        $script:failedTests++
    }
} catch {
    Write-Host " FAIL ($($_.Exception.Message))"
    $script:failedTests++
}

# 5. Admin Endpoints
Write-Section "5. Admin and Monitoring"
Test-Endpoint -Name "System statistics" -Url "/api/admin/stats"
Test-Endpoint -Name "Dead letter queue" -Url "/api/admin/dead-letter-queue"

# 6. Web UI Pages
Write-Section "6. Web UI Pages"

$pages = @(
    @{ Name = "Dashboard"; Url = "/" }
    @{ Name = "All Jobs"; Url = "/jobs" }
    @{ Name = "Email Send"; Url = "/email-send" }
    @{ Name = "API Call"; Url = "/api-call" }
    @{ Name = "Data Process"; Url = "/data-process" }
    @{ Name = "File Operation"; Url = "/file-operation" }
    @{ Name = "Notification"; Url = "/notification" }
    @{ Name = "Report Generation"; Url = "/report-generation" }
    @{ Name = "Cleanup"; Url = "/cleanup" }
    @{ Name = "Admin"; Url = "/admin" }
)

foreach ($page in $pages) {
    Test-Endpoint -Name "$($page.Name) page" -Url $page.Url
}

# 7. Job Execution Verification
Write-Section "7. Job Execution Verification"
Write-Host "  Waiting 15 seconds for jobs to execute..."
Start-Sleep -Seconds 15

$script:totalTests++
Write-Host "  Testing: Report file created..." -NoNewline
if (Test-Path "d:/LocalProjects/SimplyDone/target/test-report-comprehensive.html") {
    Write-Host " PASS"
    $script:passedTests++
} else {
    Write-Host " FAIL"
    $script:failedTests++
}

$script:totalTests++
Write-Host "  Testing: File copy created..." -NoNewline
if (Test-Path "d:/LocalProjects/SimplyDone/target/test-copy-comprehensive.txt") {
    Write-Host " PASS"
    $script:passedTests++
} else {
    Write-Host " FAIL"
    $script:failedTests++
}

# 8. Deployment Readiness
Write-Section "8. Deployment Readiness"

$deploymentChecks = @(
    @{ Name = "Dockerfile exists"; Path = "Dockerfile" }
    @{ Name = "render.yaml exists"; Path = "render.yaml" }
    @{ Name = "docker-compose.yml exists"; Path = "docker-compose.yml" }
    @{ Name = "pom.xml exists"; Path = "pom.xml" }
    @{ Name = "application-prod.properties exists"; Path = "src/main/resources/application-prod.properties" }
)

foreach ($check in $deploymentChecks) {
    $script:totalTests++
    Write-Host "  Testing: $($check.Name)..." -NoNewline
    if (Test-Path $check.Path) {
        Write-Host " PASS"
        $script:passedTests++
    } else {
        Write-Host " FAIL"
        $script:failedTests++
    }
}

$script:totalTests++
Write-Host "  Testing: EMAIL_API_KEY in prod config..." -NoNewline
$prodConfig = Get-Content "src/main/resources/application-prod.properties" -Raw
if ($prodConfig -match 'EMAIL_API_KEY') {
    Write-Host " PASS"
    $script:passedTests++
} else {
    Write-Host " FAIL"
    $script:failedTests++
}

# Summary
Write-Host ""
Write-Host "Test Summary"
Write-Host "  Total Tests: $script:totalTests"
Write-Host "  Passed:      $script:passedTests"
Write-Host "  Failed:      $script:failedTests"

if ($script:failedTests -gt 0) {
    Write-Host ""
    Write-Host "Failed Tests:"
    foreach ($failure in $script:failures) {
        Write-Host "  - $failure"
    }
}

if ($script:failedTests -eq 0) {
    Write-Host "All tests passed. Application is ready for Render deployment."
    exit 0
}

Write-Host "Some tests failed. Review the output above."
exit 1