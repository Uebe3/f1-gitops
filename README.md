# F1 Data Platform - GitOps CI/CD Pipeline

A comprehensive Jenkins-based CI/CD pipeline for deploying F1 data platform infrastructure and applications using configuration-driven GitOps practices.
Still WIP, currently working on the AWS deployments and reorganizing between this repo and the f1-data-platform to determine which should handle infrastructure.

## Overview

This repository contains a production-ready Jenkins pipeline that supports:
- **Configuration-driven deployment** based on source repository settings
- **Multi-cloud support** (AWS, Azure, GCP) with automatic provider selection
- **Infrastructure as Code** (Terraform, CloudFormation, Bicep) with auto-detection
- **Automated testing** and quality gates
- **Security scanning** and compliance
- **Monitoring and alerting** integration
- **GitOps best practices**

## Key Features

### Configuration-Driven Deployment
The pipeline reads configuration from your F1 data platform repository to determine:
- Which cloud provider to deploy to
- Infrastructure source (Terraform, CloudFormation, Bicep)
- Environment-specific settings
- Feature flags and deployment options

### Auto-Detection
- Automatically detects infrastructure source files
- Supports multiple IaC formats in the same repository
- Configurable deployment strategies per environment

## Quick Start

### Prerequisites
- Docker Desktop installed and running
- PowerShell (for automation scripts)
- Internet connection for downloading Jenkins image

### 🚀 One-Command Setup

1. **Clone this repository:**
   ```bash
   git clone <repository-url>
   cd f1-gitops
   ```

2. **Run the Docker setup script:**
   ```powershell
   .\scripts\docker-jenkins-setup-clean.ps1
   ```

3. **Open Jenkins at http://localhost:8080** and follow the setup wizard

4. **Configure your F1 pipeline!**

### What You Get
- ✅ Jenkins running in Docker container
- ✅ Persistent data storage
- ✅ Ready for F1 pipeline configuration
- ✅ Easy to reset or upgrade
- ✅ Professional DevOps setup

### Setting Up Your F1 Repository

1. **Initialize your F1 repository with proper configuration:**
   ```powershell
   .\scripts\init-f1-repo.ps1 -RepoPath "C:\path\to\your\f1-repo" -CloudProvider aws
   ```

2. **Validate your repository configuration:**
   ```powershell
   .\scripts\validate-config.ps1 -RepoPath "C:\path\to\your\f1-repo"
   ```

3. **Customize configuration files as needed**

## Architecture

### Pipeline Flow
```
Source Repo → Config Parse → Infrastructure Detection → Cloud Provider Selection → Deployment
```

### Configuration-Driven Approach
```yaml
# Your F1 repo: config/config.yaml
cloud_provider: aws
deployment:
  infrastructure_source: auto  # terraform, cloudformation, bicep
  strategy: rolling
features:
  enable_monitoring: true
  enable_backup: true
```

### Directory Structure
```
f1-gitops/                          # This GitOps repository
├── jenkins/                        # Jenkins pipeline and libraries
│   ├── Jenkinsfile                 # Main pipeline configuration
│   └── shared-libraries/           # Reusable pipeline components
├── infrastructure/                 # Infrastructure templates
│   ├── terraform/                  # Terraform modules
│   ├── cloudformation/             # CloudFormation templates
│   └── bicep/                      # Azure Bicep templates
├── environments/                   # Environment configurations
├── scripts/                        # Automation scripts
├── docs/                          # Documentation
└── examples/                      # Configuration examples

your-f1-repo/                      # Your F1 data platform repository
├── config/                        # Configuration files
│   ├── config.yaml               # Main configuration
│   └── environments/             # Environment-specific configs
├── infrastructure/               # Your infrastructure code
│   ├── terraform/               # Terraform files
│   ├── cloudformation/          # CloudFormation templates
│   └── bicep/                   # Bicep templates
├── src/                         # Application code
└── tests/                       # Test files
```

## Configuration Guide

### Basic Configuration

Create `config/config.yaml` in your F1 repository:

```yaml
# Cloud provider selection
cloud_provider: aws  # aws, azure, gcp

# Deployment configuration
deployment:
  infrastructure_source: auto  # auto-detect or specify
  strategy: rolling
  timeout_minutes: 60

# Feature flags
features:
  enable_monitoring: true
  enable_backup: false
  enable_encryption: true

# Environment configuration
environment: dev
```

### Environment Overrides

Create environment-specific files in `config/environments/`:

```yaml
# config/environments/prod.yaml
cloud_provider: aws
environment: prod
features:
  enable_monitoring: true
  enable_backup: true
  enable_encryption: true
quality_gates:
  code_coverage_threshold: 90
```

### Infrastructure Auto-Detection

The pipeline automatically detects and uses:
- **Terraform**: `infrastructure/terraform/main.tf`
- **CloudFormation**: `infrastructure/cloudformation/template.yaml`
- **Bicep**: `infrastructure/bicep/main.bicep`

## Pipeline Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `ENVIRONMENT` | Target environment | `dev` |
| `CONFIG_FILE` | Configuration file path | `config/config.yaml` |
| `FORCE_CLOUD_PROVIDER` | Override cloud provider | `null` |
| `INFRASTRUCTURE_SOURCE` | Force infrastructure source | `auto` |
| `AUTO_APPROVE` | Skip manual approval | `false` |
| `SKIP_TESTS` | Skip test execution | `false` |
| `DRY_RUN` | Plan only, don't apply | `false` |

## Cloud Provider Support

### AWS
- **Services**: S3, Lambda, Glue, Athena, CloudWatch
- **IaC**: Terraform, CloudFormation
- **Authentication**: IAM roles, access keys

### Azure
- **Services**: Storage Account, Function Apps, Data Factory, Monitor
- **IaC**: Terraform, Bicep
- **Authentication**: Service Principal, Managed Identity

### Google Cloud Platform
- **Services**: Cloud Storage, Cloud Functions, BigQuery, Cloud Monitoring
- **IaC**: Terraform
- **Authentication**: Service Account, Application Default Credentials

## Jenkins Shared Libraries

### Configuration Parser (`configParser.groovy`)
- Parses YAML configuration files
- Handles environment-specific overrides
- Validates configuration structure

### Cloud Provider (`cloudProvider.groovy`)
- Manages cloud-specific deployments
- Auto-detects infrastructure source
- Handles multiple IaC formats

### Test Runner (`testRunner.groovy`)
- Executes various test types
- Manages test reporting
- Handles quality gates

### Notification Helper (`notificationHelper.groovy`)
- Sends deployment notifications
- Supports multiple channels (Slack, email, Teams)
- Customizable notification content

## Testing

### Test Types
- **Unit Tests**: Code-level testing
- **Integration Tests**: Component interaction testing
- **Security Tests**: Vulnerability scanning
- **Performance Tests**: Load and stress testing
- **Infrastructure Tests**: Infrastructure validation

### Quality Gates
- Code coverage thresholds
- Security scan results
- Performance benchmarks
- Cost optimization checks

## Security

### Best Practices
- Store secrets in Jenkins credentials
- Use least-privilege IAM policies
- Enable security scanning
- Implement proper access controls
- Monitor and audit deployments

### Credential Management
- AWS: IAM roles or access keys
- Azure: Service principals or managed identity
- GCP: Service account keys

## Monitoring and Alerting

### Built-in Monitoring
- CloudWatch (AWS)
- Azure Monitor (Azure)
- Cloud Monitoring (GCP)

### Alerting
- Slack notifications
- Email alerts
- PagerDuty integration
- Custom webhooks

## Environments

### Development
- Auto-approval enabled
- Reduced resource allocation
- Verbose logging
- Cost optimization

### Staging
- Manual approval required
- Full feature testing
- Performance validation
- Security scanning

### Production
- Strict approval process
- Blue-green deployment
- Comprehensive monitoring
- Automated rollback

## Troubleshooting

### Common Issues

1. **Configuration File Not Found**
   ```
   Error: Configuration file not found at config/config.yaml
   Solution: Ensure config file exists in your repository
   ```

2. **Invalid Cloud Provider**
   ```
   Error: Unsupported cloud provider: xyz
   Solution: Use aws, azure, or gcp
   ```

3. **Infrastructure Source Not Detected**
   ```
   Error: No infrastructure files found
   Solution: Ensure terraform/, cloudformation/, or bicep/ directory exists
   ```

### Debugging
- Enable verbose logging in pipeline parameters
- Check Jenkins console output
- Review cloud provider logs
- Validate configuration with validation script

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Update documentation
6. Submit a pull request

## Support

- **Documentation**: See [CONFIG_GUIDE.md](CONFIG_GUIDE.md) for detailed configuration
- **Examples**: Check the `examples/` directory
- **Issues**: Open GitHub issues for bugs or feature requests

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Changelog

### v2.0.0 (Current)
- Configuration-driven deployment
- Auto-detection of infrastructure sources
- Enhanced multi-cloud support
- Improved error handling and validation

### v1.0.0
- Initial release
- Basic multi-cloud deployment
- Jenkins pipeline implementation
- Infrastructure as Code support
