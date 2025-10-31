# Quick Start Jenkins Stack
# This script helps you get the Jenkins + SonarQube stack running quickly

param(
    [switch]$Stop,
    [switch]$Restart,
    [switch]$Status,
    [switch]$Logs,
    [string]$Service = "all"
)

$ErrorActionPreference = "Stop"
$StackDir = "$PSScriptRoot\..\..\docker\jenkins-stack"

function Write-ColorOutput {
    param([string]$Message, [string]$Color = "White")
    Write-Host $Message -ForegroundColor $Color
}

function Test-Prerequisites {
    Write-ColorOutput "`n🔍 Checking prerequisites..." "Cyan"
    
    # Check Docker
    try {
        $dockerVersion = docker --version
        Write-ColorOutput "✅ Docker: $dockerVersion" "Green"
    } catch {
        Write-ColorOutput "❌ Docker not found. Please install Docker Desktop." "Red"
        Write-ColorOutput "   Download from: https://www.docker.com/products/docker-desktop" "Yellow"
        exit 1
    }
    
    # Check docker-compose
    try {
        $composeVersion = docker-compose --version
        Write-ColorOutput "✅ Docker Compose: $composeVersion" "Green"
    } catch {
        Write-ColorOutput "❌ Docker Compose not found." "Red"
        exit 1
    }
    
    # Check .env file
    if (!(Test-Path "$StackDir\.env")) {
        Write-ColorOutput "❌ .env file not found in $StackDir" "Red"
        Write-ColorOutput "   Please create .env from .env.example and fill in your credentials" "Yellow"
        Write-ColorOutput "   Example: Copy-Item '$StackDir\.env.example' '$StackDir\.env'" "Yellow"
        exit 1
    } else {
        Write-ColorOutput "✅ .env file found" "Green"
    }
    
    Write-ColorOutput "✅ All prerequisites met!`n" "Green"
}

function Start-JenkinsStack {
    Write-ColorOutput "`n🚀 Starting Jenkins Stack..." "Cyan"
    
    Push-Location $StackDir
    try {
        docker-compose up -d
        
        Write-ColorOutput "`n✅ Jenkins stack is starting!" "Green"
        Write-ColorOutput "`nServices will be available at:" "Cyan"
        Write-ColorOutput "  📊 Jenkins:   http://localhost:8080" "White"
        Write-ColorOutput "  🔍 SonarQube: http://localhost:9000" "White"
        
        Write-ColorOutput "`n⏳ Waiting for services to be ready..." "Yellow"
        Write-ColorOutput "   This may take 1-2 minutes on first startup`n" "Yellow"
        
        # Wait for Jenkins
        $maxAttempts = 30
        $attempt = 0
        $jenkinsReady = $false
        
        while ($attempt -lt $maxAttempts -and !$jenkinsReady) {
            try {
                $response = Invoke-WebRequest -Uri "http://localhost:8080" -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
                if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 403) {
                    $jenkinsReady = $true
                }
            } catch {
                # Jenkins not ready yet
            }
            
            if (!$jenkinsReady) {
                Write-Host "." -NoNewline
                Start-Sleep -Seconds 2
                $attempt++
            }
        }
        
        if ($jenkinsReady) {
            Write-ColorOutput "`n✅ Jenkins is ready!" "Green"
            
            # Get initial admin password
            Write-ColorOutput "`n🔑 Getting Jenkins initial admin password..." "Cyan"
            $password = docker exec jenkins-master cat /var/jenkins_home/secrets/initialAdminPassword 2>$null
            
            if ($password) {
                Write-ColorOutput "`n╔════════════════════════════════════════════════════════╗" "Green"
                Write-ColorOutput "║ Jenkins Initial Admin Password:                       ║" "Green"
                Write-ColorOutput "║ $password                                  ║" "Yellow"
                Write-ColorOutput "╚════════════════════════════════════════════════════════╝" "Green"
                Write-ColorOutput "`n📋 Copy this password to log into Jenkins" "Cyan"
            }
        } else {
            Write-ColorOutput "`n⚠️ Jenkins is taking longer than expected to start" "Yellow"
            Write-ColorOutput "   You can check the logs with: .\quick-start-jenkins.ps1 -Logs -Service jenkins" "Yellow"
        }
        
        Write-ColorOutput "`n📖 Next Steps:" "Cyan"
        Write-ColorOutput "   1. Open Jenkins at http://localhost:8080" "White"
        Write-ColorOutput "   2. Enter the initial admin password shown above" "White"
        Write-ColorOutput "   3. Click 'Install suggested plugins'" "White"
        Write-ColorOutput "   4. Create your admin user" "White"
        Write-ColorOutput "   5. Jenkins Configuration as Code will auto-configure the rest!" "White"
        
        Write-ColorOutput "`n📚 Full setup guide: docs/setup/JENKINS_SETUP_GUIDE.md`n" "Cyan"
        
    } finally {
        Pop-Location
    }
}

function Stop-JenkinsStack {
    Write-ColorOutput "`n🛑 Stopping Jenkins Stack..." "Yellow"
    
    Push-Location $StackDir
    try {
        docker-compose down
        Write-ColorOutput "✅ Jenkins stack stopped`n" "Green"
    } finally {
        Pop-Location
    }
}

function Restart-JenkinsStack {
    Write-ColorOutput "`n🔄 Restarting Jenkins Stack..." "Yellow"
    
    Push-Location $StackDir
    try {
        docker-compose restart
        Write-ColorOutput "✅ Jenkins stack restarted`n" "Green"
    } finally {
        Pop-Location
    }
}

function Show-Status {
    Write-ColorOutput "`n📊 Jenkins Stack Status:" "Cyan"
    
    Push-Location $StackDir
    try {
        docker-compose ps
    } finally {
        Pop-Location
    }
}

function Show-Logs {
    Write-ColorOutput "`n📜 Jenkins Stack Logs (press Ctrl+C to exit):" "Cyan"
    
    Push-Location $StackDir
    try {
        if ($Service -eq "all") {
            docker-compose logs -f
        } else {
            docker-compose logs -f $Service
        }
    } finally {
        Pop-Location
    }
}

# Main execution
Write-ColorOutput "════════════════════════════════════════════════════════" "Cyan"
Write-ColorOutput "🏎️  F1 Data Platform - Jenkins Quick Start" "Cyan"
Write-ColorOutput "════════════════════════════════════════════════════════" "Cyan"

if ($Stop) {
    Test-Prerequisites
    Stop-JenkinsStack
}
elseif ($Restart) {
    Test-Prerequisites
    Restart-JenkinsStack
}
elseif ($Status) {
    Show-Status
}
elseif ($Logs) {
    Show-Logs
}
else {
    # Default: Start the stack
    Test-Prerequisites
    Start-JenkinsStack
}
