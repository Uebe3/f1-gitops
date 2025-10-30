#!/usr/bin/env groovy

/**
 * Configuration Parser Library for F1 Data Platform
 * 
 * This library reads and parses configuration files from the source repository
 * to determine deployment targets, cloud providers, and environment settings.
 */

def parseApplicationConfig(String configPath = 'config/config.yaml') {
    echo "📋 Parsing application configuration from: ${configPath}"
    
    try {
        if (!fileExists(configPath)) {
            echo "⚠️ Configuration file not found at ${configPath}, using defaults"
            return getDefaultConfig()
        }
        
        def configContent = readFile(configPath)
        def config = readYaml text: configContent
        
        // Validate required fields
        validateConfig(config)
        
        // Enhance config with computed values
        config = enhanceConfig(config)
        
        echo "✅ Configuration parsed successfully"
        echo "   Cloud Provider: ${config.cloud_provider}"
        echo "   Environment: ${config.environment}"
        echo "   Deployment Mode: ${config.deployment?.mode ?: 'standard'}"
        
        return config
        
    } catch (Exception e) {
        echo "❌ Failed to parse configuration: ${e.getMessage()}"
        echo "🔄 Falling back to default configuration"
        return getDefaultConfig()
    }
}

def getDefaultConfig() {
    return [
        cloud_provider: 'aws',
        environment: 'dev',
        application: [
            name: 'f1-data-platform',
            version: 'latest'
        ],
        deployment: [
            mode: 'standard',
            infrastructure_source: 'terraform',
            skip_tests: false
        ],
        features: [
            enable_monitoring: true,
            enable_backup: false,
            enable_multi_az: false
        ]
    ]
}

def validateConfig(Map config) {
    def requiredFields = ['cloud_provider']
    def supportedProviders = ['aws', 'azure', 'gcp']
    
    requiredFields.each { field ->
        if (!config.containsKey(field)) {
            throw new Exception("Missing required configuration field: ${field}")
        }
    }
    
    if (!supportedProviders.contains(config.cloud_provider)) {
        throw new Exception("Unsupported cloud provider: ${config.cloud_provider}. Supported: ${supportedProviders.join(', ')}")
    }
}

def enhanceConfig(Map config) {
    // Add computed values
    config.deployment = config.deployment ?: [:]
    config.deployment.resource_prefix = "${config.application?.name ?: 'f1-data-platform'}-${config.environment ?: 'dev'}"
    
    // Determine infrastructure source based on available files
    config.deployment.infrastructure_source = determineInfrastructureSource()
    
    // Set cloud-specific defaults
    config = setCloudSpecificDefaults(config)
    
    return config
}

def determineInfrastructureSource() {
    def sources = [
        'terraform': ['main.tf', 'infrastructure.tf', 'terraform/'],
        'cloudformation': ['template.yaml', 'template.yml', 'cloudformation/', 'config/cloudformation/'],
        'bicep': ['main.bicep', 'template.bicep', 'config/azure-templates/'],
        'gcp-terraform': ['gcp-terraform/', 'config/gcp-terraform/']
    ]
    
    for (def source : sources) {
        def sourceType = source.key
        def patterns = source.value
        
        for (def pattern : patterns) {
            if (fileExists(pattern)) {
                echo "🔍 Detected infrastructure source: ${sourceType} (found: ${pattern})"
                return sourceType
            }
        }
    }
    
    echo "⚠️ No infrastructure source detected, defaulting to terraform"
    return 'terraform'
}

def setCloudSpecificDefaults(Map config) {
    switch(config.cloud_provider) {
        case 'aws':
            config.aws = config.aws ?: [:]
            config.aws.region = config.aws.region ?: env.AWS_DEFAULT_REGION ?: 'us-east-1'
            break
        case 'azure':
            config.azure = config.azure ?: [:]
            config.azure.location = config.azure.location ?: 'East US'
            break
        case 'gcp':
            config.gcp = config.gcp ?: [:]
            config.gcp.region = config.gcp.region ?: 'us-central1'
            break
    }
    
    return config
}

def getCloudConfig(Map appConfig, String environment) {
    echo "☁️ Loading cloud-specific configuration for ${appConfig.cloud_provider}"
    
    def cloudProvider = appConfig.cloud_provider
    def cloudConfigPath = "config/${cloudProvider}.yaml"
    
    // Try environment-specific config first
    def envCloudConfigPath = "config/${cloudProvider}.${environment}.yaml"
    if (fileExists(envCloudConfigPath)) {
        cloudConfigPath = envCloudConfigPath
    } else if (fileExists(cloudConfigPath)) {
        // Use general cloud config
    } else {
        echo "⚠️ No cloud-specific configuration found, using application config"
        return appConfig[cloudProvider] ?: [:]
    }
    
    try {
        def cloudConfigContent = readFile(cloudConfigPath)
        def cloudConfig = readYaml text: cloudConfigContent
        
        echo "✅ Cloud configuration loaded from: ${cloudConfigPath}"
        return cloudConfig
        
    } catch (Exception e) {
        echo "⚠️ Failed to load cloud configuration: ${e.getMessage()}"
        return appConfig[cloudProvider] ?: [:]
    }
}

def getInfrastructureConfig(Map appConfig, String environment) {
    echo "🏗️ Loading infrastructure configuration"
    
    def infrastructureSource = appConfig.deployment?.infrastructure_source ?: 'terraform'
    def cloudProvider = appConfig.cloud_provider
    
    def config = [
        source: infrastructureSource,
        cloud_provider: cloudProvider,
        environment: environment,
        paths: getInfrastructurePaths(infrastructureSource, cloudProvider)
    ]
    
    // Load infrastructure-specific variables
    switch(infrastructureSource) {
        case 'terraform':
            config.variables = loadTerraformVariables(cloudProvider, environment)
            break
        case 'cloudformation':
            config.parameters = loadCloudFormationParameters(cloudProvider, environment)
            break
        case 'bicep':
            config.parameters = loadBicepParameters(environment)
            break
        case 'gcp-terraform':
            config.variables = loadGCPTerraformVariables(environment)
            break
    }
    
    return config
}

def getInfrastructurePaths(String source, String cloudProvider) {
    def basePaths = [
        'terraform': [
            'main': '.',
            'modules': "infrastructure/terraform/modules/${cloudProvider}",
            'configs': "config/terraform"
        ],
        'cloudformation': [
            'templates': 'config/cloudformation',
            'main': 'config/cloudformation'
        ],
        'bicep': [
            'templates': 'config/azure-templates',
            'main': 'config/azure-templates'
        ],
        'gcp-terraform': [
            'main': 'config/gcp-terraform',
            'modules': 'config/gcp-terraform'
        ]
    ]
    
    return basePaths[source] ?: basePaths['terraform']
}

def loadTerraformVariables(String cloudProvider, String environment) {
    def varFiles = [
        "terraform.tfvars",
        "${environment}.tfvars",
        "config/${cloudProvider}.tfvars",
        "config/${environment}/${cloudProvider}/terraform.tfvars"
    ]
    
    def variables = [:]
    
    varFiles.each { varFile ->
        if (fileExists(varFile)) {
            echo "📝 Loading Terraform variables from: ${varFile}"
            // In a real implementation, you'd parse the .tfvars file
            // For now, we'll just note that it exists
            variables[varFile] = true
        }
    }
    
    return variables
}

def loadCloudFormationParameters(String cloudProvider, String environment) {
    def paramFiles = [
        "config/cloudformation/${environment}-parameters.json",
        "config/cloudformation/parameters.json"
    ]
    
    def parameters = [:]
    
    paramFiles.each { paramFile ->
        if (fileExists(paramFile)) {
            echo "📝 Loading CloudFormation parameters from: ${paramFile}"
            try {
                def paramContent = readFile(paramFile)
                def params = readJSON text: paramContent
                parameters.putAll(params)
            } catch (Exception e) {
                echo "⚠️ Failed to load parameters from ${paramFile}: ${e.getMessage()}"
            }
        }
    }
    
    return parameters
}

def loadBicepParameters(String environment) {
    def paramFiles = [
        "config/azure-templates/${environment}-parameters.json",
        "config/azure-templates/parameters.json"
    ]
    
    def parameters = [:]
    
    paramFiles.each { paramFile ->
        if (fileExists(paramFile)) {
            echo "📝 Loading Bicep parameters from: ${paramFile}"
            try {
                def paramContent = readFile(paramFile)
                def params = readJSON text: paramContent
                parameters.putAll(params)
            } catch (Exception e) {
                echo "⚠️ Failed to load parameters from ${paramFile}: ${e.getMessage()}"
            }
        }
    }
    
    return parameters
}

def loadGCPTerraformVariables(String environment) {
    def varFiles = [
        "config/gcp-terraform/${environment}.tfvars",
        "config/gcp-terraform/terraform.tfvars"
    ]
    
    def variables = [:]
    
    varFiles.each { varFile ->
        if (fileExists(varFile)) {
            echo "📝 Loading GCP Terraform variables from: ${varFile}"
            variables[varFile] = true
        }
    }
    
    return variables
}

def generateDeploymentManifest(Map appConfig, Map cloudConfig, Map infraConfig, String environment) {
    echo "📋 Generating deployment manifest"
    
    def manifest = [
        metadata: [
            generated_at: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            build_number: env.BUILD_NUMBER,
            git_commit: env.GIT_COMMIT?.take(8),
            environment: environment
        ],
        application: appConfig.application ?: [:],
        cloud: [
            provider: appConfig.cloud_provider,
            region: cloudConfig.region ?: cloudConfig.location,
            config: cloudConfig
        ],
        infrastructure: infraConfig,
        deployment: [
            strategy: appConfig.deployment?.strategy ?: 'rolling',
            mode: appConfig.deployment?.mode ?: 'standard',
            skip_tests: appConfig.deployment?.skip_tests ?: false,
            auto_approve: appConfig.deployment?.auto_approve ?: false
        ],
        features: appConfig.features ?: [:]
    ]
    
    // Write manifest for later stages
    writeJSON file: 'deployment-manifest.json', json: manifest
    archiveArtifacts artifacts: 'deployment-manifest.json', allowEmptyArchive: false
    
    return manifest
}

def validateDeploymentCompatibility(Map manifest) {
    echo "🔍 Validating deployment compatibility"
    
    def cloudProvider = manifest.cloud.provider
    def infraSource = manifest.infrastructure.source
    
    // Check if infrastructure source is compatible with cloud provider
    def compatibility = [
        'aws': ['terraform', 'cloudformation'],
        'azure': ['terraform', 'bicep'],
        'gcp': ['terraform', 'gcp-terraform']
    ]
    
    def supportedSources = compatibility[cloudProvider] ?: []
    
    if (!supportedSources.contains(infraSource)) {
        throw new Exception("Infrastructure source '${infraSource}' is not compatible with cloud provider '${cloudProvider}'. Supported sources: ${supportedSources.join(', ')}")
    }
    
    // Validate required credentials are available
    validateCredentials(cloudProvider)
    
    echo "✅ Deployment compatibility validated"
}

def validateCredentials(String cloudProvider) {
    echo "🔐 Validating ${cloudProvider} credentials"
    
    switch(cloudProvider) {
        case 'aws':
            if (!env.AWS_ACCESS_KEY_ID && !env.AWS_PROFILE) {
                // Check if running in EC2 with IAM role
                try {
                    sh 'aws sts get-caller-identity'
                    echo "✅ AWS credentials validated (IAM role or instance profile)"
                } catch (Exception e) {
                    throw new Exception("AWS credentials not found. Please configure AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY or AWS_PROFILE")
                }
            } else {
                echo "✅ AWS credentials available"
            }
            break
            
        case 'azure':
            try {
                sh 'az account show'
                echo "✅ Azure credentials validated"
            } catch (Exception e) {
                throw new Exception("Azure credentials not found. Please run 'az login' or configure service principal")
            }
            break
            
        case 'gcp':
            if (!env.GOOGLE_APPLICATION_CREDENTIALS) {
                throw new Exception("GCP credentials not found. Please set GOOGLE_APPLICATION_CREDENTIALS")
            }
            echo "✅ GCP credentials available"
            break
            
        default:
            echo "⚠️ Unknown cloud provider: ${cloudProvider}"
    }
}

def detectSourceRepositoryStructure() {
    echo "🔍 Detecting source repository structure"
    
    def structure = [
        has_config: fileExists('config/'),
        has_infrastructure: false,
        has_terraform: fileExists('main.tf') || fileExists('terraform/'),
        has_cloudformation: fileExists('template.yaml') || fileExists('cloudformation/'),
        has_bicep: fileExists('main.bicep') || fileExists('azure-templates/'),
        has_docker: fileExists('Dockerfile') || fileExists('docker-compose.yml'),
        has_kubernetes: fileExists('k8s/') || fileExists('kubernetes/'),
        config_files: []
    ]
    
    // Detect configuration files
    def configPatterns = [
        'config/config.yaml',
        'config/config.yml',
        'config.yaml',
        'config.yml',
        'app.yaml',
        'app.yml'
    ]
    
    configPatterns.each { pattern ->
        if (fileExists(pattern)) {
            structure.config_files.add(pattern)
        }
    }
    
    structure.has_infrastructure = structure.has_terraform || structure.has_cloudformation || structure.has_bicep
    
    echo "📋 Repository structure detected:"
    echo "   Configuration files: ${structure.config_files}"
    echo "   Has infrastructure: ${structure.has_infrastructure}"
    echo "   Has Docker: ${structure.has_docker}"
    echo "   Has Kubernetes: ${structure.has_kubernetes}"
    
    return structure
}

def createConfigurationReport(Map appConfig, Map cloudConfig, Map infraConfig, String environment) {
    echo "📊 Creating configuration report"
    
    def report = [
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        environment: environment,
        build: env.BUILD_NUMBER,
        configuration: [
            application: appConfig,
            cloud: cloudConfig,
            infrastructure: infraConfig
        ],
        source_repository: detectSourceRepositoryStructure()
    ]
    
    writeJSON file: "config-report-${environment}.json", json: report
    archiveArtifacts artifacts: "config-report-*.json", allowEmptyArchive: true
    
    return report
}

// Export functions for Jenkins pipeline use
return this