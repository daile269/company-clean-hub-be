#!/usr/bin/env pwsh

Write-Host "🔍 Verifying backend build..." -ForegroundColor Cyan

# Check if Maven is available
try {
    $null = Get-Command mvn -ErrorAction Stop
    Write-Host "✅ Maven is available" -ForegroundColor Green
} catch {
    Write-Host "❌ Maven is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

# Check if Java is available (skip version check for simplicity)
try {
    $null = Get-Command java -ErrorAction Stop
    Write-Host "✅ Java is available" -ForegroundColor Green
} catch {
    Write-Host "❌ Java is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

# Check key files exist
$keyFiles = @(
    "pom.xml",
    "src/main/java/com/company/company_clean_hub_be/CompanyCleanHubBeApplication.java",
    "src/main/java/com/company/company_clean_hub_be/service/impl/EvaluationServiceImpl.java",
    "src/main/java/com/company/company_clean_hub_be/repository/EvaluationRepository.java"
)

foreach ($file in $keyFiles) {
    if (Test-Path $file) {
        Write-Host "✅ $file" -ForegroundColor Green
    } else {
        Write-Host "❌ $file - MISSING" -ForegroundColor Red
        exit 1
    }
}

# Compile the project
Write-Host ""
Write-Host "🔨 Compiling project..." -ForegroundColor Cyan
try {
    $compileResult = mvn clean compile -q 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Compilation successful" -ForegroundColor Green
    } else {
        Write-Host "❌ Compilation failed" -ForegroundColor Red
        Write-Host $compileResult -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ Compilation failed" -ForegroundColor Red
    exit 1
}

# Run tests
Write-Host ""
Write-Host "🧪 Running tests..." -ForegroundColor Cyan
try {
    $testResult = mvn test -q 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ All tests passed" -ForegroundColor Green
    } else {
        Write-Host "❌ Tests failed" -ForegroundColor Red
        Write-Host $testResult -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ Tests failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "🎉 Backend build verification PASSED!" -ForegroundColor Green
Write-Host ""
Write-Host "To run the application:" -ForegroundColor Yellow
Write-Host "  mvn spring-boot:run    # Start the Spring Boot application" -ForegroundColor White
Write-Host "  mvn clean package      # Build JAR file" -ForegroundColor White
Write-Host "  mvn test               # Run tests only" -ForegroundColor White