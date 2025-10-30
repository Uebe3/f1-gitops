param(
    [Parameter(Mandatory=$false)]
    [ValidateSet('dev', 'staging', 'prod')]
    [string]$Environment = 'dev',
    
    [Parameter(Mandatory=$false)]
    [string]$Region = 'us-east-1',
    
    [Parameter(Mandatory=$false)]
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

# Paths
$ShowcaseRepo = "C:\scripts\showcase-f1-pipeline"
$CloudFormationDir = Join-Path $ShowcaseRepo "config\cloudformation"

# Helper functions
function Write-Info { param($msg) Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Success { param($msg) Write-Host "[SUCCESS] $msg" -ForegroundColor Green }
function Write-Warning { param($msg) Write-Host "[WARNING] $msg" -ForegroundColor Yellow }
function Write-Error { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red }

function Get-StackOutput {
    param(
        [string]$StackName,
        [string]$OutputKey
    )
    
    try {
        $stack = aws cloudformation describe-stacks --stack-name $StackName --region $Region --output json | ConvertFrom-Json
        $output = $stack.Stacks[0].Outputs | Where-Object { $_.OutputKey -eq $OutputKey }
        return $output.OutputValue
    } catch {
        Write-Error "Failed to get output '$OutputKey' from stack '$StackName': $_"
        return $null
    }
}

function Deploy-Stack {
    param(
        [string]$StackName,
        [string]$TemplatePath,
        [hashtable]$Parameters,
        [bool]$IsDryRun = $false
    )
    
    if (-not (Test-Path $TemplatePath)) {
        Write-Error "Template not found: $TemplatePath"
        return $false
    }
    
    # Build parameter array
    $paramArray = @()
    foreach ($key in $Parameters.Keys) {
        $paramArray += "ParameterKey=$key,ParameterValue=$($Parameters[$key])"
    }
    
    if ($IsDryRun) {
        Write-Warning "[DRY RUN] Would deploy: $StackName"
        Write-Info "Template: $TemplatePath"
        Write-Info "Parameters:"
        foreach ($key in $Parameters.Keys) {
            Write-Info "  $key = $($Parameters[$key])"
        }
        return $true
    }
    
    # Check if stack exists
    $stackExists = $false
    try {
        aws cloudformation describe-stacks --stack-name $StackName --region $Region 2>$null | Out-Null
        $stackExists = $true
    } catch {}
    
    try {
        if ($stackExists) {
            Write-Info "Updating existing stack: $StackName"
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
            Write-Info "Creating new stack: $StackName"
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
        
        Write-Success "Stack deployed successfully: $StackName"
        
        # Show outputs
        Write-Info "Stack outputs:"
        $stack = aws cloudformation describe-stacks --stack-name $StackName --region $Region --output json | ConvertFrom-Json
        foreach ($output in $stack.Stacks[0].Outputs) {
            Write-Info "  $($output.OutputKey): $($output.OutputValue)"
        }
        
        return $true
    } catch {
        Write-Error "Failed to deploy stack $StackName : $_"
        return $false
    }
}

Write-Host "`n=== F1 Data Platform Deployment ===" -ForegroundColor Magenta
Write-Info "Using templates from: $CloudFormationDir"
Write-Info "Environment: $Environment"
Write-Info "Region: $Region"

# Verify AWS credentials
Write-Info "Verifying AWS credentials..."
$identity = aws sts get-caller-identity --output json | ConvertFrom-Json
Write-Success "Authenticated as: $($identity.Arn)"

# Stack 1: Data Lake Foundation
Write-Host "`n=== [1/4] Data Lake Foundation ===" -ForegroundColor Cyan
$foundationStackName = "f1-data-lake-foundation-$Environment"
$foundationParams = @{
    Environment = $Environment
    ProjectName = "f1-data-platform"
    DataLakeBucketName = "f1-data-lake"
}

$success = Deploy-Stack `
    -StackName $foundationStackName `
    -TemplatePath "C:\scripts\f1-gitops\infrastructure\aws\cloudformation\01-data-lake-foundation-fixed.yaml" `
    -Parameters $foundationParams `
    -IsDryRun $DryRun

if (-not $success) {
    Write-Error "Foundation stack deployment failed. Exiting."
    exit 1
}

if ($DryRun) {
    Write-Warning "[DRY RUN] Skipping retrieval of foundation stack outputs"
    $dataLakeBucket = "f1-data-lake-dev-ACCOUNTID"
    $glueDatabase = "f1_data_platform_dev"
    $glueServiceRole = "arn:aws:iam::ACCOUNTID:role/GlueServiceRole"
    $athenaWorkgroup = "f1-data-platform-dev"
    $athenaResultsBucket = "f1-athena-results-dev-ACCOUNTID"
} else {
    # Get outputs from foundation stack
    Write-Info "Retrieving outputs from foundation stack..."
    $dataLakeBucket = Get-StackOutput -StackName $foundationStackName -OutputKey "DataLakeBucketName"
    $athenaResultsBucket = Get-StackOutput -StackName $foundationStackName -OutputKey "AthenaResultsBucketName"
    $glueDatabase = Get-StackOutput -StackName $foundationStackName -OutputKey "GlueDatabaseName"
    $glueServiceRole = Get-StackOutput -StackName $foundationStackName -OutputKey "GlueServiceRoleArn"
    $athenaWorkgroup = Get-StackOutput -StackName $foundationStackName -OutputKey "AthenaWorkgroupName"
    
    Write-Info "Foundation stack outputs:"
    Write-Info "  DataLakeBucket: $dataLakeBucket"
    Write-Info "  AthenaResultsBucket: $athenaResultsBucket"
    Write-Info "  GlueDatabase: $glueDatabase"
    Write-Info "  GlueServiceRole: $glueServiceRole"
    Write-Info "  AthenaWorkgroup: $athenaWorkgroup"
}

# Stack 2: Glue ETL Jobs
Write-Host "`n=== [2/4] Glue ETL Jobs ===" -ForegroundColor Cyan
$glueParams = @{
    Environment = $Environment
    ProjectName = "f1-data-platform"
    DataLakeBucket = $dataLakeBucket
    GlueDatabase = $glueDatabase
    GlueServiceRoleArn = $glueServiceRole
}

$success = Deploy-Stack `
    -StackName "f1-glue-etl-$Environment" `
    -TemplatePath (Join-Path $CloudFormationDir "02-glue-etl-jobs.yaml") `
    -Parameters $glueParams `
    -IsDryRun $DryRun

if (-not $success) {
    Write-Error "Glue ETL stack deployment failed. Exiting."
    exit 1
}

# Stack 3: Athena Analytics
Write-Host "`n=== [3/4] Athena Analytics ===" -ForegroundColor Cyan
$athenaParams = @{
    Environment = $Environment
    ProjectName = "f1-data-platform"
    DataLakeBucket = $dataLakeBucket
    GlueDatabase = $glueDatabase
    AthenaWorkgroup = $athenaWorkgroup
}

$success = Deploy-Stack `
    -StackName "f1-athena-analytics-$Environment" `
    -TemplatePath (Join-Path $CloudFormationDir "03-athena-analytics.yaml") `
    -Parameters $athenaParams `
    -IsDryRun $DryRun

if (-not $success) {
    Write-Error "Athena Analytics stack deployment failed. Exiting."
    exit 1
}

# Stack 4: Data Platform Access Role
Write-Host "`n=== [4/4] Data Platform Access Role ===" -ForegroundColor Cyan
$accessParams = @{
    Environment = $Environment
    ProjectName = "f1-data-platform"
    DataLakeBucket = $dataLakeBucket
    GlueDatabase = $glueDatabase
    AthenaResultsBucket = $athenaResultsBucket
}

$success = Deploy-Stack `
    -StackName "f1-data-platform-access-$Environment" `
    -TemplatePath (Join-Path $CloudFormationDir "04-f1-data-platform-access-role.yaml") `
    -Parameters $accessParams `
    -IsDryRun $DryRun

if (-not $success) {
    Write-Error "Access Role stack deployment failed. Exiting."
    exit 1
}

Write-Host "`n=== Deployment Complete ===" -ForegroundColor Green
if (-not $DryRun) {
    Write-Success "All 4 stacks deployed successfully!"
    Write-Info "Foundation Stack: $foundationStackName"
    Write-Info "Data Lake Bucket: $dataLakeBucket"
    Write-Info "Glue Database: $glueDatabase"
    Write-Info "Athena Workgroup: $athenaWorkgroup"
} else {
    Write-Warning "[DRY RUN] No actual changes made"
}
