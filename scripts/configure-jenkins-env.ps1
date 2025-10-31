# Configure Jenkins Environment File
# This script helps you create .env file using your existing AWS credentials

param(
    [switch]$UseAwsCliCredentials = $true
)

$ErrorActionPreference = "Stop"

function Write-ColorOutput {
    param([string]$Message, [string]$Color = "White")
    Write-Host $Message -ForegroundColor $Color
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Jenkins Environment Configuration" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$envFile = "$PSScriptRoot\..\docker\jenkins-stack\.env"
$envExample = "$PSScriptRoot\..\docker\jenkins-stack\.env.example"

# Check if .env already exists
if (Test-Path $envFile) {
    Write-Host "WARNING: .env file already exists at: $envFile" -ForegroundColor Yellow
    $response = Read-Host "Do you want to overwrite it? (y/N)"
    if ($response -ne 'y' -and $response -ne 'Y') {
        Write-Host "Cancelled. Existing .env file preserved." -ForegroundColor Red
        exit 0
    }
}

# Get AWS credentials from AWS CLI configuration
$useManual = $false
if ($UseAwsCliCredentials) {
    Write-Host "Reading AWS credentials from AWS CLI configuration..." -ForegroundColor Cyan
    
    try {
        # Extract credentials from AWS credentials file
        $awsCredsFile = "$env:USERPROFILE\.aws\credentials"
        if (Test-Path $awsCredsFile) {
            $credsContent = Get-Content $awsCredsFile
            $accessKey = ($credsContent | Select-String "aws_access_key_id\s*=\s*(.+)" | ForEach-Object { $_.Matches.Groups[1].Value.Trim() })[0]
            $secretKey = ($credsContent | Select-String "aws_secret_access_key\s*=\s*(.+)" | ForEach-Object { $_.Matches.Groups[1].Value.Trim() })[0]
            
            if ($accessKey -and $secretKey) {
                Write-Host "Found AWS credentials!" -ForegroundColor Green
                $AWS_ACCESS_KEY_ID = $accessKey
                $AWS_SECRET_ACCESS_KEY = $secretKey
            }
        }
        
        # Get region from config file
        $awsConfigFile = "$env:USERPROFILE\.aws\config"
        if (Test-Path $awsConfigFile) {
            $configContent = Get-Content $awsConfigFile
            $region = ($configContent | Select-String "region\s*=\s*(.+)" | ForEach-Object { $_.Matches.Groups[1].Value.Trim() })[0]
            if ($region) {
                $AWS_DEFAULT_REGION = $region
                Write-Host "Found AWS region: $region" -ForegroundColor Green
            } else {
                $AWS_DEFAULT_REGION = "us-east-1"
            }
        } else {
            $AWS_DEFAULT_REGION = "us-east-1"
        }
        
        if (-not $AWS_ACCESS_KEY_ID -or -not $AWS_SECRET_ACCESS_KEY) {
            Write-Host "Could not extract AWS credentials automatically" -ForegroundColor Yellow
            $useManual = $true
        }
    } catch {
        Write-Host "Error reading AWS credentials: $_" -ForegroundColor Yellow
        $useManual = $true
    }
}

# Manual input if needed
if ($useManual) {
    Write-Host ""
    Write-Host "Please enter your credentials manually:" -ForegroundColor Cyan
    $AWS_ACCESS_KEY_ID = Read-Host "AWS Access Key ID"
    $AWS_SECRET_ACCESS_KEY = Read-Host "AWS Secret Access Key"
    $AWS_DEFAULT_REGION = Read-Host "AWS Region (default: us-east-1)"
    if ([string]::IsNullOrWhiteSpace($AWS_DEFAULT_REGION)) {
        $AWS_DEFAULT_REGION = "us-east-1"
    }
}

# Collect other required credentials
Write-Host ""
Write-Host "Now let's configure the other credentials..." -ForegroundColor Cyan

# Discord Webhook
Write-Host ""
Write-Host "1. Discord Webhook URL" -ForegroundColor Yellow
Write-Host "   Create a webhook in Discord: Channel Settings -> Integrations -> Webhooks -> New Webhook" -ForegroundColor Gray
$DISCORD_WEBHOOK_URL = Read-Host "Discord Webhook URL (or press Enter to set later)"
if ([string]::IsNullOrWhiteSpace($DISCORD_WEBHOOK_URL)) {
    $DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/YOUR_WEBHOOK_ID/YOUR_WEBHOOK_TOKEN"
    Write-Host "   Remember to update this in .env before testing!" -ForegroundColor Yellow
}

# GitHub Token
Write-Host ""
Write-Host "2. GitHub Personal Access Token" -ForegroundColor Yellow
Write-Host "   Generate at: GitHub -> Settings -> Developer settings -> Personal access tokens" -ForegroundColor Gray
Write-Host "   Required scopes: repo, admin:repo_hook, workflow" -ForegroundColor Gray
$GITHUB_TOKEN = Read-Host "GitHub Token (or press Enter to set later)"
if ([string]::IsNullOrWhiteSpace($GITHUB_TOKEN)) {
    $GITHUB_TOKEN = "your_github_pat_here"
    Write-Host "   Remember to update this in .env before creating pipeline!" -ForegroundColor Yellow
}

# Jenkins Admin
Write-Host ""
Write-Host "3. Jenkins Admin Credentials" -ForegroundColor Yellow
$JENKINS_ADMIN_USER = Read-Host "Jenkins Admin Username (default: admin)"
if ([string]::IsNullOrWhiteSpace($JENKINS_ADMIN_USER)) {
    $JENKINS_ADMIN_USER = "admin"
}

$JENKINS_ADMIN_PASSWORD = Read-Host "Jenkins Admin Password"

# SonarQube
Write-Host ""
Write-Host "4. SonarQube Configuration" -ForegroundColor Yellow
$SONARQUBE_DB_PASSWORD = Read-Host "SonarQube Database Password (or press Enter for default: sonarpass)"
if ([string]::IsNullOrWhiteSpace($SONARQUBE_DB_PASSWORD)) {
    $SONARQUBE_DB_PASSWORD = "sonarpass"
}

# Create .env file
Write-Host ""
Write-Host "Creating .env file..." -ForegroundColor Cyan

$envContent = @"
# Jenkins & CI/CD Environment Variables
# Auto-generated on $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
# DO NOT commit this file to git!

# Discord Webhook
DISCORD_WEBHOOK_URL=$DISCORD_WEBHOOK_URL

# SonarQube Configuration
SONARQUBE_DB_PASSWORD=$SONARQUBE_DB_PASSWORD
SONARQUBE_TOKEN=# Will be generated after first login to SonarQube

# AWS Credentials (from your AWS CLI configuration)
AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY
AWS_DEFAULT_REGION=$AWS_DEFAULT_REGION

# GitHub Personal Access Token
GITHUB_TOKEN=$GITHUB_TOKEN

# Jenkins Admin
JENKINS_ADMIN_USER=$JENKINS_ADMIN_USER
JENKINS_ADMIN_PASSWORD=$JENKINS_ADMIN_PASSWORD
"@

# Write to file
$envContent | Out-File -FilePath $envFile -Encoding UTF8 -Force

Write-Host ""
Write-Host ".env file created successfully!" -ForegroundColor Green
Write-Host "Location: $envFile" -ForegroundColor Gray

# Verify file is in .gitignore
$gitignorePath = "$PSScriptRoot\..\.gitignore"
if (Test-Path $gitignorePath) {
    $gitignoreContent = Get-Content $gitignorePath -Raw
    if ($gitignoreContent -notmatch "\.env$" -and $gitignoreContent -notmatch "\.env\s") {
        Write-Host ""
        Write-Host "Adding .env to .gitignore for security..." -ForegroundColor Yellow
        Add-Content -Path $gitignorePath -Value "`n# Environment variables`n.env"
    }
}

# Display summary
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Configuration Summary" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

Write-Host ""
Write-Host "Configured:" -ForegroundColor Green
Write-Host "   - AWS Access Key ID: $($AWS_ACCESS_KEY_ID.Substring(0, [Math]::Min(12, $AWS_ACCESS_KEY_ID.Length)))..." -ForegroundColor White
Write-Host "   - AWS Region: $AWS_DEFAULT_REGION" -ForegroundColor White
Write-Host "   - Jenkins Admin User: $JENKINS_ADMIN_USER" -ForegroundColor White
Write-Host "   - SonarQube DB Password: [Set]" -ForegroundColor White

if ($DISCORD_WEBHOOK_URL -like "*YOUR_WEBHOOK*") {
    Write-Host ""
    Write-Host "Still Need to Configure:" -ForegroundColor Yellow
    Write-Host "   - Discord Webhook URL (update in .env)" -ForegroundColor White
}

if ($GITHUB_TOKEN -eq "your_github_pat_here") {
    Write-Host "   - GitHub Token (update in .env)" -ForegroundColor White
}

Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "   1. If needed, update Discord webhook and GitHub token in .env" -ForegroundColor White
Write-Host "   2. Run: .\scripts\quick-start-jenkins.ps1" -ForegroundColor White
Write-Host "   3. Open http://localhost:8080 to access Jenkins" -ForegroundColor White
Write-Host "   4. Follow the GETTING_STARTED.md guide" -ForegroundColor White

Write-Host ""
Write-Host "Security Reminder:" -ForegroundColor Yellow
Write-Host "   - .env file contains secrets - never commit to git!" -ForegroundColor White
Write-Host "   - File is added to .gitignore automatically" -ForegroundColor White

Write-Host ""
