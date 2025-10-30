#!/usr/bin/env groovy

/**
 * Cloud Provider Operations Library for F1 Data Platform
 * 
 * This library provides methods for deploying to different cloud providers,
 * with a focus on AWS for the initial implementation.
 */

def deployToAWS(Map config) {
    echo "🌩️ Deploying F1 Data Platform to AWS..."
    
    def environment = config.environment ?: 'dev'
    def version = config.version ?: 'latest'
    def dockerImage = config.dockerImage ?: ''
    def cloudConfig = config.config ?: [:]
    def infraConfig = config.infraConfig ?: [:]
    def manifest = config.manifest ?: [:]
    
    def region = cloudConfig.region ?: env.AWS_DEFAULT_REGION ?: 'us-east-1'
    
    try {
        // Validate AWS credentials
        sh 'aws sts get-caller-identity'
        
        // Deploy infrastructure based on source type
        deployAWSInfrastructure(environment, region, infraConfig, manifest)
        
        // Deploy application components
        deployAWSApplications(environment, version, dockerImage, region, cloudConfig, manifest)
        
        // Update deployment status
        updateDeploymentStatus([
            status: 'SUCCESS',
            environment: environment,
            provider: 'aws',
            version: version,
            infraSource: infraConfig.source
        ])
        
        echo "✅ AWS deployment completed successfully"
        
    } catch (Exception e) {
        echo "❌ AWS deployment failed: ${e.getMessage()}"
        
        updateDeploymentStatus([
            status: 'FAILED',
            environment: environment,
            provider: 'aws',
            version: version,
            infraSource: infraConfig.source,
            error: e.getMessage()
        ])
        
        throw e
    }
}

def deployAWSInfrastructure(String environment, String region, Map infraConfig, Map manifest) {
    echo "🏗️ Deploying AWS infrastructure for environment: ${environment}"
    echo "   Infrastructure source: ${infraConfig.source}"
    
    switch(infraConfig.source) {
        case 'terraform':
            deployTerraformInfrastructure(environment, region, infraConfig, manifest)
            break
        case 'cloudformation':
            deployCloudFormationInfrastructure(environment, region, infraConfig, manifest)
            break
        default:
            echo "⚠️ Unknown infrastructure source: ${infraConfig.source}, attempting Terraform"
            deployTerraformInfrastructure(environment, region, infraConfig, manifest)
    }
}

def deployTerraformInfrastructure(String environment, String region, Map infraConfig, Map manifest) {
    echo "🌍 Deploying infrastructure using Terraform"
    
    def terraformDir = infraConfig.paths?.main ?: '.'
    def moduleDir = infraConfig.paths?.modules ?: "infrastructure/terraform/modules/aws"
    
    // Use local terraform files if they exist, otherwise use GitOps repo templates
    def workingDir = fileExists('main.tf') ? '.' : moduleDir
    
    dir(workingDir) {
        // Initialize Terraform
        sh """
        terraform init \
            -backend-config="bucket=f1-terraform-state-\${AWS_ACCOUNT_ID}" \
            -backend-config="key=f1-data-platform/${environment}/terraform.tfstate" \
            -backend-config="region=${region}"
        """
        
        // Find terraform variables files
        def varFiles = findTerraformVarFiles(environment)
        def varFileArgs = varFiles.collect { "-var-file=\"${it}\"" }.join(' ')
        
        // Plan infrastructure changes
        sh """
        terraform plan \
            ${varFileArgs} \
            -var="aws_account_id=\${AWS_ACCOUNT_ID}" \
            -var="build_version=${env.BUILD_VERSION}" \
            -var="environment=${environment}" \
            -out=terraform.plan
        """
        
        // Apply infrastructure changes (auto-approve for non-prod, manual approval for prod)
        if (environment == 'prod' && !params.AUTO_APPROVE_INFRASTRUCTURE) {
            input message: "Approve Terraform changes for production?", ok: "Deploy"
        }
        
        sh 'terraform apply terraform.plan'
        
        // Export Terraform outputs for use in application deployment
        sh 'terraform output -json > terraform-outputs.json'
        
        // Archive outputs for later stages
        archiveArtifacts artifacts: 'terraform-outputs.json', allowEmptyArchive: false
    }
}

def findTerraformVarFiles(String environment) {
    def varFiles = []
    def candidates = [
        "terraform.tfvars",
        "${environment}.tfvars",
        "config/${environment}.tfvars",
        "config/aws.tfvars",
        "config/${environment}/aws/terraform.tfvars",
        "environments/${environment}/aws/terraform.tfvars"
    ]
    
    candidates.each { candidate ->
        if (fileExists(candidate)) {
            echo "📝 Found Terraform variables file: ${candidate}"
            varFiles.add(candidate)
        }
    }
    
    if (varFiles.isEmpty()) {
        echo "⚠️ No Terraform variables files found, using defaults"
    }
    
    return varFiles
}

def deployCloudFormationInfrastructure(String environment, String region, Map infraConfig, Map manifest) {
    echo "☁️ Deploying infrastructure using CloudFormation"
    
    def templatesDir = infraConfig.paths?.templates ?: 'config/cloudformation'
    def stackName = "f1-data-platform-${environment}"
    
    // Find CloudFormation templates
    def templates = findCloudFormationTemplates(templatesDir)
    
    templates.each { template ->
        def templateName = template.name
        def templatePath = template.path
        def currentStackName = "${stackName}-${templateName}"
        
        echo "📋 Deploying CloudFormation template: ${templatePath}"
        
        // Check if stack exists
        def stackExists = sh(
            script: "aws cloudformation describe-stacks --stack-name ${currentStackName} --region ${region}",
            returnStatus: true
        ) == 0
        
        def action = stackExists ? 'update-stack' : 'create-stack'
        def changeSetName = "f1-changeset-${env.BUILD_NUMBER}"
        
        // Create change set
        sh """
        aws cloudformation create-change-set \
            --stack-name ${currentStackName} \
            --template-body file://${templatePath} \
            --change-set-name ${changeSetName} \
            --parameters ${getCloudFormationParameters(environment, templateName)} \
            --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
            --region ${region}
        """
        
        // Wait for change set creation
        sh """
        aws cloudformation wait change-set-create-complete \
            --stack-name ${currentStackName} \
            --change-set-name ${changeSetName} \
            --region ${region}
        """
        
        // Execute change set (with approval for prod)
        if (environment == 'prod' && !params.AUTO_APPROVE_INFRASTRUCTURE) {
            input message: "Approve CloudFormation changes for ${templateName} in production?", ok: "Deploy"
        }
        
        sh """
        aws cloudformation execute-change-set \
            --stack-name ${currentStackName} \
            --change-set-name ${changeSetName} \
            --region ${region}
        """
        
        // Wait for stack operation completion
        def waitCommand = stackExists ? 'stack-update-complete' : 'stack-create-complete'
        sh """
        aws cloudformation wait ${waitCommand} \
            --stack-name ${currentStackName} \
            --region ${region}
        """
        
        echo "✅ CloudFormation stack ${currentStackName} deployed successfully"
    }
    
    // Export stack outputs
    exportCloudFormationOutputs(stackName, region)
}

def findCloudFormationTemplates(String templatesDir) {
    def templates = []
    def templateFiles = [
        [name: 'foundation', path: "${templatesDir}/01-data-lake-foundation.yaml"],
        [name: 'glue-etl', path: "${templatesDir}/02-glue-etl-jobs.yaml"],
        [name: 'analytics', path: "${templatesDir}/03-athena-analytics.yaml"]
    ]
    
    // Also check for generic template files
    def genericFiles = ['template.yaml', 'template.yml', 'main.yaml', 'main.yml']
    genericFiles.each { filename ->
        def path = "${templatesDir}/${filename}"
        if (fileExists(path)) {
            templates.add([name: 'main', path: path])
        }
    }
    
    // Check predefined templates
    templateFiles.each { template ->
        if (fileExists(template.path)) {
            templates.add(template)
        }
    }
    
    if (templates.isEmpty()) {
        throw new Exception("No CloudFormation templates found in ${templatesDir}")
    }
    
    return templates
}

def getCloudFormationParameters(String environment, String templateName) {
    def parameterFiles = [
        "config/cloudformation/${environment}-parameters.json",
        "config/cloudformation/${templateName}-${environment}.json",
        "config/cloudformation/parameters.json"
    ]
    
    def parameters = []
    
    parameterFiles.each { paramFile ->
        if (fileExists(paramFile)) {
            echo "📝 Loading CloudFormation parameters from: ${paramFile}"
            return "file://${paramFile}"
        }
    }
    
    // Default parameters
    return """
    ParameterKey=Environment,ParameterValue=${environment} \
    ParameterKey=ProjectName,ParameterValue=f1-data-platform \
    ParameterKey=BuildVersion,ParameterValue=${env.BUILD_VERSION}
    """.replaceAll(/\s+/, ' ').trim()
}

def exportCloudFormationOutputs(String stackName, String region) {
    echo "📤 Exporting CloudFormation outputs"
    
    try {
        sh """
        aws cloudformation describe-stacks \
            --stack-name ${stackName}-foundation \
            --query 'Stacks[0].Outputs' \
            --region ${region} \
            --output json > cloudformation-outputs.json
        """
        
        archiveArtifacts artifacts: 'cloudformation-outputs.json', allowEmptyArchive: false
        
    } catch (Exception e) {
        echo "⚠️ Could not export CloudFormation outputs: ${e.getMessage()}"
        // Create empty outputs file to prevent downstream failures
        writeFile file: 'cloudformation-outputs.json', text: '[]'
        archiveArtifacts artifacts: 'cloudformation-outputs.json', allowEmptyArchive: true
    }
}

def deployLambdaFunctions(String environment, String version, String dockerImage, Map terraformOutputs, String region) {
    echo "⚡ Deploying Lambda functions..."
    
    def lambdaRole = terraformOutputs.lambda_role_arn.value
    def functions = ['data-processor', 'data-scheduler']
    
    functions.each { functionName ->
        def fullFunctionName = "f1-data-platform-${environment}-${functionName}"
        
        try {
            // Check if function exists
            def functionExists = sh(
                script: "aws lambda get-function --function-name ${fullFunctionName} --region ${region}",
                returnStatus: true
            ) == 0
            
            if (functionExists) {
                // Update existing function
                if (dockerImage) {
                    echo "📦 Updating Lambda function ${fullFunctionName} with container image..."
                    sh """
                    aws lambda update-function-code \
                        --function-name ${fullFunctionName} \
                        --image-uri ${dockerImage} \
                        --region ${region}
                    """
                } else {
                    echo "📦 Updating Lambda function ${fullFunctionName} with ZIP package..."
                    // Create deployment package
                    createLambdaDeploymentPackage(functionName, environment)
                    
                    sh """
                    aws lambda update-function-code \
                        --function-name ${fullFunctionName} \
                        --zip-file fileb://${functionName}-deployment.zip \
                        --region ${region}
                    """
                }
                
                // Update function configuration
                sh """
                aws lambda update-function-configuration \
                    --function-name ${fullFunctionName} \
                    --environment Variables='{
                        "ENVIRONMENT":"${environment}",
                        "BUILD_VERSION":"${version}",
                        "DATA_LAKE_BUCKET":"${terraformOutputs.data_lake_bucket_name.value}",
                        "GLUE_DATABASE":"${terraformOutputs.glue_database_name.value}"
                    }' \
                    --region ${region}
                """
            } else {
                // Create new function
                echo "🆕 Creating new Lambda function ${fullFunctionName}..."
                
                if (dockerImage) {
                    sh """
                    aws lambda create-function \
                        --function-name ${fullFunctionName} \
                        --role ${lambdaRole} \
                        --code ImageUri=${dockerImage} \
                        --package-type Image \
                        --environment Variables='{
                            "ENVIRONMENT":"${environment}",
                            "BUILD_VERSION":"${version}",
                            "DATA_LAKE_BUCKET":"${terraformOutputs.data_lake_bucket_name.value}",
                            "GLUE_DATABASE":"${terraformOutputs.glue_database_name.value}"
                        }' \
                        --timeout 300 \
                        --memory-size 512 \
                        --region ${region}
                    """
                } else {
                    createLambdaDeploymentPackage(functionName, environment)
                    
                    sh """
                    aws lambda create-function \
                        --function-name ${fullFunctionName} \
                        --role ${lambdaRole} \
                        --zip-file fileb://${functionName}-deployment.zip \
                        --handler lambda_function.lambda_handler \
                        --runtime python3.9 \
                        --environment Variables='{
                            "ENVIRONMENT":"${environment}",
                            "BUILD_VERSION":"${version}",
                            "DATA_LAKE_BUCKET":"${terraformOutputs.data_lake_bucket_name.value}",
                            "GLUE_DATABASE":"${terraformOutputs.glue_database_name.value}"
                        }' \
                        --timeout 300 \
                        --memory-size 512 \
                        --region ${region}
                    """
                }
            }
            
            echo "✅ Lambda function ${fullFunctionName} deployed successfully"
            
        } catch (Exception e) {
            echo "❌ Failed to deploy Lambda function ${fullFunctionName}: ${e.getMessage()}"
            throw e
        }
    }
}

def createLambdaDeploymentPackage(String functionName, String environment) {
    echo "📦 Creating deployment package for ${functionName}..."
    
    sh """
    # Create temporary directory
    mkdir -p lambda-package
    
    # Copy function code
    cp -r ../showcase-f1-pipeline/f1_data_platform lambda-package/
    
    # Copy specific lambda function
    cp lambda-functions/${functionName}.py lambda-package/lambda_function.py
    
    # Install dependencies
    pip install -r ../showcase-f1-pipeline/requirements.txt -t lambda-package/
    
    # Create ZIP package
    cd lambda-package
    zip -r ../${functionName}-deployment.zip .
    cd ..
    
    # Cleanup
    rm -rf lambda-package
    """
}

def uploadGlueScripts(String environment, Map terraformOutputs, String region) {
    echo "📜 Uploading Glue ETL scripts..."
    
    def bucketName = terraformOutputs.data_lake_bucket_name.value
    
    // Upload main ETL script
    sh """
    aws s3 cp glue-scripts/f1_etl_job.py s3://${bucketName}/scripts/ \
        --region ${region}
    """
    
    // Upload any additional Glue scripts
    sh """
    if [ -d "glue-scripts" ]; then
        aws s3 sync glue-scripts/ s3://${bucketName}/scripts/ \
            --exclude "*.pyc" \
            --exclude "__pycache__/*" \
            --region ${region}
    fi
    """
    
    echo "✅ Glue scripts uploaded successfully"
}

def startInitialDataCrawl(Map terraformOutputs, String region) {
    echo "🕷️ Starting initial data crawl..."
    
    def crawlerName = terraformOutputs.glue_crawler_name.value
    
    try {
        // Start the crawler
        sh """
        aws glue start-crawler \
            --name ${crawlerName} \
            --region ${region}
        """
        
        echo "✅ Data crawler started successfully"
        
    } catch (Exception e) {
        echo "⚠️ Could not start data crawler: ${e.getMessage()}"
        // Don't fail the deployment for this
    }
}

def deployToAzure(Map config) {
    echo "🔵 Azure deployment not yet implemented"
    echo "Environment: ${config.environment}"
    echo "Version: ${config.version}"
    
    // TODO: Implement Azure deployment
    // - Deploy ARM templates or Bicep
    // - Configure Azure Data Factory
    // - Set up Azure Synapse Analytics
    // - Deploy Azure Functions
}

def deployToGCP(Map config) {
    echo "🔴 GCP deployment not yet implemented"
    echo "Environment: ${config.environment}"
    echo "Version: ${config.version}"
    
    // TODO: Implement GCP deployment
    // - Deploy Terraform templates
    // - Configure Cloud Dataflow
    // - Set up BigQuery
    // - Deploy Cloud Functions
}

def setupMonitoring(Map config) {
    echo "📊 Setting up monitoring for deployed resources..."
    
    def clouds = config.clouds ?: ['aws']
    def environment = config.environment ?: 'dev'
    def slackChannel = config.slackChannel ?: '#f1-deployments'
    
    clouds.each { cloud ->
        switch(cloud) {
            case 'aws':
                setupAWSMonitoring(environment, slackChannel)
                break
            case 'azure':
                echo "🔵 Azure monitoring setup not yet implemented"
                break
            case 'gcp':
                echo "🔴 GCP monitoring setup not yet implemented"
                break
            default:
                echo "⚠️ Unknown cloud provider: ${cloud}"
        }
    }
}

def setupAWSMonitoring(String environment, String slackChannel) {
    echo "📈 Setting up AWS CloudWatch monitoring..."
    
    // The CloudWatch dashboard and alarms are created by Terraform
    // Here we can set up additional monitoring like custom metrics
    
    try {
        // Create custom metrics for deployment success
        sh """
        aws cloudwatch put-metric-data \
            --namespace "F1DataPlatform/Deployment" \
            --metric-data MetricName=DeploymentSuccess,Value=1,Unit=Count,Dimensions=Environment=${environment}
        """
        
        echo "✅ AWS monitoring setup completed"
        
    } catch (Exception e) {
        echo "⚠️ Failed to set up custom monitoring: ${e.getMessage()}"
        // Don't fail the deployment for monitoring issues
    }
}

def updateDeploymentStatus(Map config) {
    echo "📝 Updating deployment status..."
    
    def status = config.status ?: 'UNKNOWN'
    def environment = config.environment ?: 'dev'
    def provider = config.provider ?: 'unknown'
    def version = config.version ?: 'unknown'
    def error = config.error ?: ''
    
    // Log deployment status to CloudWatch
    try {
        def metricValue = (status == 'SUCCESS') ? 1 : 0
        
        sh """
        aws cloudwatch put-metric-data \
            --namespace "F1DataPlatform/Deployment" \
            --metric-data MetricName=DeploymentStatus,Value=${metricValue},Unit=Count,Dimensions=Environment=${environment},Provider=${provider},Version=${version}
        """
        
        // Also log to a deployment tracking system if available
        def deploymentRecord = [
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
            environment: environment,
            provider: provider,
            version: version,
            status: status,
            error: error,
            buildNumber: env.BUILD_NUMBER,
            gitCommit: env.GIT_COMMIT
        ]
        
        writeJSON file: "deployment-record-${environment}-${provider}.json", json: deploymentRecord
        archiveArtifacts artifacts: "deployment-record-*.json", allowEmptyArchive: true
        
        echo "✅ Deployment status updated: ${status}"
        
    } catch (Exception e) {
        echo "⚠️ Failed to update deployment status: ${e.getMessage()}"
    }
}

def getCloudProviderStatus(String provider, String environment) {
    echo "🔍 Checking ${provider} deployment status for ${environment}..."
    
    switch(provider) {
        case 'aws':
            return getAWSStatus(environment)
        case 'azure':
            return getAzureStatus(environment)
        case 'gcp':
            return getGCPStatus(environment)
        default:
            return [status: 'UNKNOWN', message: "Unknown provider: ${provider}"]
    }
}

def getAWSStatus(String environment) {
    try {
        // Check if key AWS resources exist and are healthy
        def bucketExists = sh(
            script: "aws s3 ls f1-data-lake-${environment}-\${AWS_ACCOUNT_ID}",
            returnStatus: true
        ) == 0
        
        if (bucketExists) {
            return [status: 'HEALTHY', message: 'AWS resources are operational']
        } else {
            return [status: 'UNHEALTHY', message: 'Data lake bucket not found']
        }
    } catch (Exception e) {
        return [status: 'ERROR', message: e.getMessage()]
    }
}

def getAzureStatus(String environment) {
    // TODO: Implement Azure health checks
    return [status: 'NOT_IMPLEMENTED', message: 'Azure status check not implemented']
}

def getGCPStatus(String environment) {
    // TODO: Implement GCP health checks
    return [status: 'NOT_IMPLEMENTED', message: 'GCP status check not implemented']
}

// Export functions for Jenkins pipeline use
return this