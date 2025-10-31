# Quick script to edit .env file in Notepad
# This makes it easy to paste your Discord webhook URL and GitHub token

$envFile = "$PSScriptRoot\..\docker\jenkins-stack\.env"

if (Test-Path $envFile) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "Opening .env file in Notepad..." -ForegroundColor Cyan  
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Please update these values:" -ForegroundColor Yellow
    Write-Host "  1. DISCORD_WEBHOOK_URL - Replace with your Discord webhook URL" -ForegroundColor White
    Write-Host "  2. GITHUB_TOKEN - Replace with your GitHub Personal Access Token" -ForegroundColor White
    Write-Host ""
    Write-Host "Your AWS credentials are already configured!" -ForegroundColor Green
    Write-Host ""
    Write-Host "When done, save and close Notepad." -ForegroundColor Cyan
    Write-Host ""
    
    # Open in Notepad
    notepad $envFile
    
    Write-Host ""
    Write-Host "Next step: Run .\scripts\quick-start-jenkins.ps1 to start Jenkins!" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host "Error: .env file not found at $envFile" -ForegroundColor Red
    Write-Host "Run this script first to create it:" -ForegroundColor Yellow
    Write-Host "  .\scripts\configure-jenkins-env.ps1" -ForegroundColor White
}
