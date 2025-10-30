# F1 Data Platform - AWS Deployment Script
# Deploys CloudFormation infrastructure for F1 data lake
# Uses GitOps repository structure

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet('dev', 'staging', 'prod')]
    [string]$Environment = 'dev',
    
    [Parameter(Mandatory=$false)]
    [switch]$DeployDataLake = $true,
    
    [Parameter(Mandatory=$false)]
    [switch]$DeployGlue = $false,
    
    [Parameter(Mandatory=$false)]
    [switch]$DryRun = $false,
    
    [Parameter(Mandatory=$false)]
    [string]$Region = 'us-east-1'
)

$ErrorActionPreference = "Stop"

# Color output
function Write-Info { param($msg) Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Success { param($msg) Write-Host "[SUCCESS] $msg" -ForegroundColor Green }
function Write-Warning { param($msg) Write-Host "[WARNING] $msg" -ForegroundColor Yellow }
function Write-Error { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red }

# Get repository root (scripts/deployment -> repo root)
$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$InfraDir = Join-Path $RepoRoot "infrastructure\aws\cloudformation"

Write-Host "`n=== F1 Data Platform AWS Deployment ===" -ForegroundColor Magenta
Write-Info "Environment: $Environment"
Write-Info "Region: $Region"
Write-Info "Dry Run: $DryRun"

# Verify AWS credentials
Write-Info "Verifying AWS credentials..."
try {
    $identity = aws sts get-caller-identity --output json | ConvertFrom-Json
    Write-Success "Authenticated as: $($identity.Arn)"
    Write-Info "Account ID: $($identity.Account)"
} catch {
    Write-Error "AWS credentials not configured. Run 'aws configure' first."
    exit 1
}

# Function to deploy CloudFormation stack
function Deploy-CloudFormationStack {
    param(
        [string]$StackName,
        [string]$TemplatePath,
        [hashtable]$Parameters
    )
    
    if (-not (Test-Path $TemplatePath)) {
        Write-Error "Template not found: $TemplatePath"
        return $false
    }
    
    Write-Info "Deploying stack: $StackName"
    
    # Convert parameters to array format for AWS CLI
    $paramArray = @()
    foreach ($key in $Parameters.Keys) {
        $paramArray += "ParameterKey=$key,ParameterValue=$($Parameters[$key])"
    }
    
    if ($DryRun) {
        Write-Warning "[DRY RUN] Would deploy stack: $StackName"
        Write-Info "Template: $TemplatePath"
        Write-Info "Parameters: $($paramArray -join ' ')"
        return $true
    }
    
    # Check if stack exists
    $stackExists = $false
    try {
        aws cloudformation describe-stacks --stack-name $StackName --region $Region 2>$null | Out-Null
        $stackExists = $true
        Write-Info "Stack exists - updating..."
    } catch {
        Write-Info "Stack does not exist - creating..."
    }
    
    try {
        if ($stackExists) {
            # Update existing stack
            Write-Info "Updating CloudFormation stack..."
            aws cloudformation update-stack `
                --stack-name $StackName `
                --template-body "file://$TemplatePath" `
                --parameters $paramArray `
                --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM `
                --region $Region
            
            Write-Info "Waiting for stack update to complete..."
            aws cloudformation wait stack-update-complete `
                --stack-name $StackName `
                --region $Region
        } else {
            # Create new stack
            Write-Info "Creating CloudFormation stack..."
            aws cloudformation create-stack `
                --stack-name $StackName `
                --template-body "file://$TemplatePath" `
                --parameters $paramArray `
                --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM `
                --region $Region
            
            Write-Info "Waiting for stack creation to complete..."
            aws cloudformation wait stack-create-complete `
                --stack-name $StackName `
                --region $Region
        }
        
        Write-Success "Stack deployment completed: $StackName"
        
        # Get stack outputs
        Write-Info "Stack outputs:"
        $outputs = aws cloudformation describe-stacks `
            --stack-name $StackName `
            --region $Region `
            --query 'Stacks[0].Outputs' `
            --output table
        Write-Host $outputs
        
        return $true
    } catch {
        if ($_.Exception.Message -like "*No updates are to be performed*") {
            Write-Warning "No updates needed for stack: $StackName"
            return $true
        }
        Write-Error "Failed to deploy stack: $StackName"
        Write-Error $_.Exception.Message
        return $false
    }
}

# Deploy Data Lake Foundation
if ($DeployDataLake) {
    Write-Host "`n--- Deploying Data Lake Foundation ---" -ForegroundColor Yellow
    
    $dataLakeTemplate = Join-Path $InfraDir "data-lake.yaml"
    $stackName = "f1-data-platform-data-lake-$Environment"
    
    $parameters = @{
        Environment = $Environment
        ProjectName = "f1-data-platform"
        EnableVersioning = "true"
        EnableEncryption = "true"
        LogRetentionDays = "14"
    }
    
    $success = Deploy-CloudFormationStack -StackName $stackName -TemplatePath $dataLakeTemplate -Parameters $parameters
    
    if (-not $success) {
        Write-Error "Data Lake deployment failed. Stopping."
        exit 1
    }
}

# Deploy Glue ETL Jobs
if ($DeployGlue) {
    Write-Host "`n--- Deploying Glue ETL Jobs ---" -ForegroundColor Yellow
    
    $glueTemplate = Join-Path $InfraDir "glue-jobs.yaml"
    $stackName = "f1-data-platform-glue-$Environment"
    
    # Get Data Lake bucket name from previous stack
    $dataLakeBucket = aws cloudformation describe-stacks `
        --stack-name "f1-data-platform-data-lake-$Environment" `
        --region $Region `
        --query 'Stacks[0].Outputs[?OutputKey==`DataLakeBucketName`].OutputValue' `
        --output text
    
    if (-not $dataLakeBucket) {
        Write-Error "Could not retrieve Data Lake bucket name. Deploy Data Lake first."
        exit 1
    }
    
    $parameters = @{
        Environment = $Environment
        ProjectName = "f1-data-platform"
        DataLakeBucket = $dataLakeBucket
    }
    
    $success = Deploy-CloudFormationStack -StackName $stackName -TemplatePath $glueTemplate -Parameters $parameters
    
    if (-not $success) {
        Write-Error "Glue deployment failed."
        exit 1
    }
}

# Summary
Write-Host "`n=== Deployment Summary ===" -ForegroundColor Magenta
Write-Success "Deployment completed successfully!"

Write-Info "`nDeployed Resources:"
if ($DeployDataLake) {
    Write-Host "  [+] Data Lake Foundation (S3, IAM, CloudWatch)" -ForegroundColor Green
}
if ($DeployGlue) {
    Write-Host "  [+] Glue ETL Jobs" -ForegroundColor Green
}

Write-Host "`nNext Steps:" -ForegroundColor Yellow
Write-Host "  1. Review stack outputs above"
Write-Host "  2. Configure your F1 application to use the S3 bucket"
Write-Host "  3. Upload F1 data to the data lake"
Write-Host "  4. Run your data processing pipelines"

Write-Host "`nView resources in AWS Console:"
Write-Host "  CloudFormation: https://console.aws.amazon.com/cloudformation/home?region=$Region"
Write-Host "  S3 Buckets: https://s3.console.aws.amazon.com/s3/buckets?region=$Region"
Write-Host ""
