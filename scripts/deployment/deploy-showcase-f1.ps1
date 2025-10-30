# F1 Data Platform - Deploy from showcase-f1-pipeline
# Uses the CloudFormation templates from your application repository

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet('dev', 'staging', 'prod')]
    [string]$Environment = 'dev',
    
    [Parameter(Mandatory=$false)]
    [string]$Region = 'us-east-1',
    
    [Parameter(Mandatory=$false)]
    [switch]$DryRun = $false
)

$ErrorActionPreference = "Stop"

# Paths
$ShowcaseRepo = "C:\scripts\showcase-f1-pipeline"
$CloudFormationDir = Join-Path $ShowcaseRepo "config\cloudformation"

# Color output
function Write-Info { param($msg) Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Success { param($msg) Write-Host "[SUCCESS] $msg" -ForegroundColor Green }
function Write-Warning { param($msg) Write-Host "[WARNING] $msg" -ForegroundColor Yellow }
function Write-Error { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red }

Write-Host "`n=== F1 Data Platform Deployment ===" -ForegroundColor Magenta
Write-Info "Using templates from: $CloudFormationDir"
Write-Info "Environment: $Environment"
Write-Info "Region: $Region"

# Verify AWS credentials
Write-Info "Verifying AWS credentials..."
$identity = aws sts get-caller-identity --output json | ConvertFrom-Json
Write-Success "Authenticated as: $($identity.Arn)"
$accountId = $identity.Account

# Deployment order
$stacks = @(
    @{
        Name = "f1-data-lake-foundation-$Environment"
        Template = "01-data-lake-foundation.yaml"
        Parameters = @{
            Environment = $Environment
            ProjectPrefix = "f1-platform"
        }
    },
    @{
        Name = "f1-glue-etl-$Environment"
        Template = "02-glue-etl-jobs.yaml"
        Parameters = @{
            Environment = $Environment
            FoundationStackName = "f1-data-lake-foundation-$Environment"
        }
    },
    @{
        Name = "f1-athena-analytics-$Environment"
        Template = "03-athena-analytics.yaml"
        Parameters = @{
            Environment = $Environment
            FoundationStackName = "f1-data-lake-foundation-$Environment"
        }
    },
    @{
        Name = "f1-data-platform-access-$Environment"
        Template = "04-f1-data-platform-access-role.yaml"
        Parameters = @{
            Environment = $Environment
        }
    }
)

# Deploy each stack
foreach ($stack in $stacks) {
    Write-Host "`n--- Deploying: $($stack.Name) ---" -ForegroundColor Yellow
    
    $templatePath = Join-Path $CloudFormationDir $stack.Template
    
    if (-not (Test-Path $templatePath)) {
        Write-Error "Template not found: $templatePath"
        continue
    }
    
    # Build parameters
    $paramArray = @()
    foreach ($key in $stack.Parameters.Keys) {
        $paramArray += "ParameterKey=$key,ParameterValue=$($stack.Parameters[$key])"
    }
    
    if ($DryRun) {
        Write-Warning "[DRY RUN] Would deploy: $($stack.Name)"
        Write-Info "Template: $($stack.Template)"
        Write-Info "Parameters: $($paramArray -join ' ')"
        continue
    }
    
    # Check if stack exists
    $stackExists = $false
    try {
        aws cloudformation describe-stacks --stack-name $stack.Name --region $Region 2>$null | Out-Null
        $stackExists = $true
    } catch {}
    
    try {
        if ($stackExists) {
            Write-Info "Updating existing stack..."
            aws cloudformation update-stack `
                --stack-name $stack.Name `
                --template-body "file://$templatePath" `
                --parameters $paramArray `
                --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM `
                --region $Region
            
            Write-Info "Waiting for stack update..."
            aws cloudformation wait stack-update-complete `
                --stack-name $stack.Name `
                --region $Region
        } else {
            Write-Info "Creating new stack..."
            aws cloudformation create-stack `
                --stack-name $stack.Name `
                --template-body "file://$templatePath" `
                --parameters $paramArray `
                --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM `
                --region $Region
            
            Write-Info "Waiting for stack creation..."
            aws cloudformation wait stack-create-complete `
                --stack-name $stack.Name `
                --region $Region
        }
        
        Write-Success "Stack deployed: $($stack.Name)"
        
        # Show outputs
        Write-Info "Stack outputs:"
        aws cloudformation describe-stacks `
            --stack-name $stack.Name `
            --region $Region `
            --query 'Stacks[0].Outputs' `
            --output table
            
    } catch {
        if ($_.Exception.Message -like "*No updates are to be performed*") {
            Write-Warning "No changes detected for: $($stack.Name)"
        } else {
            Write-Error "Failed to deploy: $($stack.Name)"
            Write-Error $_.Exception.Message
            
            # Check for rollback
            $status = aws cloudformation describe-stacks `
                --stack-name $stack.Name `
                --region $Region `
                --query 'Stacks[0].StackStatus' `
                --output text 2>$null
            
            if ($status -like "*ROLLBACK*" -or $status -like "*FAILED*") {
                Write-Error "Stack rolled back. Check CloudFormation console for details."
                Write-Info "View errors: https://console.aws.amazon.com/cloudformation/home?region=$Region#/stacks"
                exit 1
            }
        }
    }
}

Write-Host "`n=== Deployment Complete ===" -ForegroundColor Green
Write-Info "View resources: https://console.aws.amazon.com/cloudformation/home?region=$Region"
Write-Info "`nNext: Configure showcase-f1-pipeline to use the deployed infrastructure"
