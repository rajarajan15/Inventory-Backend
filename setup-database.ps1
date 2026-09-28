# Setup Database Script for Inventory Management System

$psqlPath = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
if (-not (Test-Path $psqlPath)) {
    $psqlCmd = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($psqlCmd) {
        $psqlPath = $psqlCmd.Source
    } else {
        Write-Error "Could not find psql.exe. Please ensure PostgreSQL is installed."
        exit 1
    }
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Inventory System - Database Setup        " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$dbUser = Read-Host "Enter PostgreSQL username [default: postgres]"
if ([string]::IsNullOrWhiteSpace($dbUser)) {
    $dbUser = "postgres"
}

$dbPassword = Read-Host "Enter PostgreSQL password for '$dbUser'" -AsSecureString
$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($dbPassword)
$plainPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)

$env:PGPASSWORD = $plainPassword

Write-Host "`nConnecting to PostgreSQL and creating 'inventory_db'..." -ForegroundColor Yellow

# Check if database already exists
$dbExists = & $psqlPath -U $dbUser -h localhost -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='inventory_db';" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to connect to PostgreSQL with provided credentials." -ForegroundColor Red
    Write-Host "Error: $dbExists" -ForegroundColor Red
    $env:PGPASSWORD = $null
    exit 1
}

if ($dbExists -eq "1") {
    Write-Host "Database 'inventory_db' already exists!" -ForegroundColor Green
} else {
    $createResult = & $psqlPath -U $dbUser -h localhost -d postgres -c "CREATE DATABASE inventory_db;" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Database 'inventory_db' created successfully!" -ForegroundColor Green
    } else {
        Write-Host "Failed to create database: $createResult" -ForegroundColor Red
        $env:PGPASSWORD = $null
        exit 1
    }
}

$env:PGPASSWORD = $null

# Update application.properties
$propPath = "$PSScriptRoot\src\main\resources\application.properties"
if (Test-Path $propPath) {
    $content = Get-Content $propPath -Raw
    $content = $content -replace "spring.datasource.username=.*", "spring.datasource.username=$dbUser"
    $content = $content -replace "spring.datasource.password=.*", "spring.datasource.password=$plainPassword"
    Set-Content -Path $propPath -Value $content -NoNewline
    Write-Host "Updated src/main/resources/application.properties with credentials!" -ForegroundColor Green
}

Write-Host "`nSetup complete! You can now run:" -ForegroundColor Cyan
Write-Host ".\mvnw.cmd spring-boot:run`n" -ForegroundColor Yellow
