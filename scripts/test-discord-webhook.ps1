# Test Discord Webhook Integration
# Sends test notifications to verify Discord webhook is working

param(
    [Parameter(Mandatory=$true)]
    [string]$WebhookUrl,
    
    [ValidateSet('simple', 'deployment', 'quality', 'failure', 'all')]
    [string]$TestType = 'all'
)

function Send-DiscordMessage {
    param(
        [hashtable]$Payload
    )
    
    $jsonPayload = $Payload | ConvertTo-Json -Depth 10
    
    try {
        $response = Invoke-RestMethod -Uri $WebhookUrl `
            -Method Post `
            -Body $jsonPayload `
            -ContentType "application/json"
        
        Write-Host "✅ Message sent successfully" -ForegroundColor Green
        return $true
    } catch {
        Write-Host "❌ Failed to send message: $_" -ForegroundColor Red
        return $false
    }
}

function Test-SimpleMessage {
    Write-Host "`n📝 Testing simple message..." -ForegroundColor Cyan
    
    $payload = @{
        content = "🏎️ **Test from F1 Data Platform CI/CD**"
        embeds = @(
            @{
                title = "Simple Test Message"
                description = "If you can see this, your Discord webhook is configured correctly!"
                color = 3447003  # Blue
                timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
                footer = @{
                    text = "F1 Data Platform"
                }
            }
        )
    }
    
    Send-DiscordMessage -Payload $payload
}

function Test-DeploymentStarted {
    Write-Host "`n🚀 Testing deployment started notification..." -ForegroundColor Cyan
    
    $payload = @{
        content = "🚀 **Deployment Started**"
        embeds = @(
            @{
                title = "🚀 Deployment Started"
                description = "Deploying to dev environment"
                color = 3447003  # Blue
                fields = @(
                    @{
                        name = "Environment"
                        value = "dev"
                        inline = $true
                    },
                    @{
                        name = "Branch"
                        value = "main"
                        inline = $true
                    },
                    @{
                        name = "Commit"
                        value = "abc12345"
                        inline = $true
                    },
                    @{
                        name = "Triggered By"
                        value = "Jenkins"
                        inline = $true
                    }
                )
                timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
                footer = @{
                    text = "Jenkins CI/CD"
                    icon_url = "https://www.jenkins.io/images/logos/jenkins/jenkins.png"
                }
            }
        )
    }
    
    Send-DiscordMessage -Payload $payload
}

function Test-DeploymentSuccess {
    Write-Host "`n✅ Testing deployment success notification..." -ForegroundColor Cyan
    
    $payload = @{
        content = "✅ **Deployment Successful**"
        embeds = @(
            @{
                title = "✅ Deployment Successful"
                description = "Successfully deployed to dev"
                color = 65280  # Green
                fields = @(
                    @{
                        name = "Environment"
                        value = "dev"
                        inline = $true
                    },
                    @{
                        name = "Duration"
                        value = "5 min 23 sec"
                        inline = $true
                    },
                    @{
                        name = "CloudFormation Stacks"
                        value = "f1-data-platform-foundation-dev, f1-data-platform-glue-etl-dev"
                        inline = $false
                    },
                    @{
                        name = "Resources Created"
                        value = "S3 Buckets (3), Glue Database (1), Athena Workgroup (1), IAM Roles (2)"
                        inline = $false
                    }
                )
                timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
                footer = @{
                    text = "Jenkins CI/CD"
                    icon_url = "https://www.jenkins.io/images/logos/jenkins/jenkins.png"
                }
            }
        )
    }
    
    Send-DiscordMessage -Payload $payload
}

function Test-QualityGate {
    Write-Host "`n📊 Testing quality gate notification..." -ForegroundColor Cyan
    
    $payload = @{
        content = "✅ **Quality Gate Passed**"
        embeds = @(
            @{
                title = "✅ Quality Gate Passed"
                description = "SonarQube analysis complete"
                color = 65280  # Green
                fields = @(
                    @{
                        name = "Quality Gate"
                        value = "PASSED"
                        inline = $true
                    },
                    @{
                        name = "Coverage"
                        value = "85%"
                        inline = $true
                    },
                    @{
                        name = "Bugs"
                        value = "0"
                        inline = $true
                    },
                    @{
                        name = "Code Smells"
                        value = "12"
                        inline = $true
                    },
                    @{
                        name = "Security Hotspots"
                        value = "1"
                        inline = $true
                    },
                    @{
                        name = "SonarQube URL"
                        value = "http://localhost:9000"
                        inline = $false
                    }
                )
                timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
                footer = @{
                    text = "Jenkins CI/CD"
                    icon_url = "https://www.jenkins.io/images/logos/jenkins/jenkins.png"
                }
            }
        )
    }
    
    Send-DiscordMessage -Payload $payload
}

function Test-DeploymentFailure {
    Write-Host "`n❌ Testing deployment failure notification..." -ForegroundColor Cyan
    
    $payload = @{
        content = "❌ **Deployment Failed**"
        embeds = @(
            @{
                title = "❌ Deployment Failed"
                description = "Deployment to dev failed"
                color = 16711680  # Red
                fields = @(
                    @{
                        name = "Environment"
                        value = "dev"
                        inline = $true
                    },
                    @{
                        name = "Stage"
                        value = "Deploy Infrastructure"
                        inline = $true
                    },
                    @{
                        name = "Error"
                        value = "CloudFormation stack creation failed: Template validation error"
                        inline = $false
                    },
                    @{
                        name = "Build URL"
                        value = "http://localhost:8080/job/F1-Platform/42/"
                        inline = $false
                    }
                )
                timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
                footer = @{
                    text = "Jenkins CI/CD"
                    icon_url = "https://www.jenkins.io/images/logos/jenkins/jenkins.png"
                }
            }
        )
    }
    
    Send-DiscordMessage -Payload $payload
}

# Main execution
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "🧪 Discord Webhook Tester - F1 Data Platform" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan

Write-Host "`n📡 Testing webhook: $WebhookUrl" -ForegroundColor Yellow

$results = @{
    passed = 0
    failed = 0
}

if ($TestType -eq 'simple' -or $TestType -eq 'all') {
    if (Test-SimpleMessage) { $results.passed++ } else { $results.failed++ }
    Start-Sleep -Seconds 2
}

if ($TestType -eq 'deployment' -or $TestType -eq 'all') {
    if (Test-DeploymentStarted) { $results.passed++ } else { $results.failed++ }
    Start-Sleep -Seconds 2
    if (Test-DeploymentSuccess) { $results.passed++ } else { $results.failed++ }
    Start-Sleep -Seconds 2
}

if ($TestType -eq 'quality' -or $TestType -eq 'all') {
    if (Test-QualityGate) { $results.passed++ } else { $results.failed++ }
    Start-Sleep -Seconds 2
}

if ($TestType -eq 'failure' -or $TestType -eq 'all') {
    if (Test-DeploymentFailure) { $results.passed++ } else { $results.failed++ }
    Start-Sleep -Seconds 2
}

Write-Host "`n════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "📊 Test Results" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "✅ Passed: $($results.passed)" -ForegroundColor Green
Write-Host "❌ Failed: $($results.failed)" -ForegroundColor Red

if ($results.failed -eq 0) {
    Write-Host "`n🎉 All tests passed! Your Discord webhook is working correctly." -ForegroundColor Green
    Write-Host "   Check your Discord channel to see the test messages." -ForegroundColor Cyan
} else {
    Write-Host "`n⚠️ Some tests failed. Please check:" -ForegroundColor Yellow
    Write-Host "   1. Webhook URL is correct" -ForegroundColor White
    Write-Host "   2. Channel permissions allow webhook messages" -ForegroundColor White
    Write-Host "   3. Discord server is accessible" -ForegroundColor White
}

Write-Host "`n"
