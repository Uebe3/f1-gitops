# F1 Data Platform CI/CD with Jenkins

Complete CI/CD pipeline using Jenkins, Discord notifications, and SonarQube code quality gates.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CI/CD Pipeline Flow                       │
└─────────────────────────────────────────────────────────────────┘

GitHub Push
    │
    ├──> GitHub Webhook
    │         │
    │         ▼
    │    Jenkins Pipeline
    │         │
    │         ├──> Initialize & Load Config
    │         │
    │         ├──> Code Quality (SonarQube)
    │         │         │
    │         │         └──> Quality Gate Check
    │         │
    │         ├──> Validate CloudFormation Templates
    │         │
    │         ├──> Deploy to AWS
    │         │         │
    │         │         ├──> Foundation Stack (S3, Glue, Athena, IAM)
    │         │         ├──> Glue ETL Stack
    │         │         ├──> Athena Analytics Stack
    │         │         └──> Access Stack
    │         │
    │         ├──> Run Validation Tests
    │         │
    │         └──> Generate Deployment Report
    │
    └──> Discord Notifications (every step)
```

## 📋 Stack Components

### Jenkins
- **Port**: 8080
- **Purpose**: CI/CD orchestration
- **Features**: 
  - Configuration as Code (JCasC)
  - AWS CLI integration
  - Python 3.9 with boto3
  - Discord webhook notifications

### SonarQube
- **Port**: 9000
- **Purpose**: Code quality and security scanning
- **Features**:
  - Quality gates
  - Code coverage tracking
  - Security vulnerability detection
  - Technical debt analysis

### PostgreSQL
- **Port**: 5432 (internal)
- **Purpose**: SonarQube database backend
- **Features**:
  - Persistent storage
  - Automatic backups

### Nginx (Optional)
- **Port**: 443
- **Purpose**: HTTPS reverse proxy
- **Features**:
  - SSL/TLS termination
  - Load balancing

## 🚀 Quick Start

### Prerequisites

1. **Docker Desktop** installed and running
2. **Discord webhook URL** (see setup steps below)
3. **GitHub Personal Access Token** (see setup steps below)
4. **AWS credentials** (from IAM user f1-admin)
5. **Git** repository initialized

### 1. Create Discord Webhook

1. Open your Discord server
2. Right-click on the channel (e.g., `#f1-deployments`)
3. Click **Edit Channel** → **Integrations** → **Webhooks**
4. Click **New Webhook**
5. Set name to `F1 CI/CD Bot`
6. Copy the **Webhook URL**

### 2. Create GitHub Personal Access Token

1. Go to GitHub → **Settings** → **Developer settings** → **Personal access tokens** → **Tokens (classic)**
2. Click **Generate new token (classic)**
3. Set name: `Jenkins F1 GitOps`
4. Select scopes:
   - `repo` (all)
   - `admin:repo_hook` (all)
   - `workflow`
5. Click **Generate token**
6. **Copy the token immediately** (you won't see it again!)

### 3. Configure Environment

```powershell
# Navigate to docker stack directory
cd docker/jenkins-stack

# Copy the example environment file
Copy-Item .env.example .env

# Edit .env and fill in your credentials
notepad .env
```

Fill in these values in `.env`:
```bash
# Discord
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/YOUR_WEBHOOK_ID/YOUR_WEBHOOK_TOKEN

# SonarQube (use defaults for initial setup)
SONARQUBE_ADMIN_PASSWORD=admin123  # Change this!

# AWS Credentials (from IAM user f1-admin)
AWS_ACCESS_KEY_ID=your_access_key_here
AWS_SECRET_ACCESS_KEY=your_secret_key_here
AWS_REGION=us-east-1

# GitHub
GITHUB_TOKEN=ghp_your_token_here

# Jenkins Admin (create your own)
JENKINS_ADMIN_USER=admin
JENKINS_ADMIN_PASSWORD=your_secure_password_here
```

### 4. Start the Stack

```powershell
# Using the quick start script (recommended)
.\scripts\quick-start-jenkins.ps1

# OR manually with docker-compose
cd docker/jenkins-stack
docker-compose up -d
```

The script will:
- ✅ Check prerequisites
- ✅ Validate .env file exists
- ✅ Start all services
- ✅ Wait for Jenkins to be ready
- ✅ Display initial admin password

### 5. Access Services

- **Jenkins**: http://localhost:8080
- **SonarQube**: http://localhost:9000

### 6. Complete Jenkins Setup

1. Open http://localhost:8080
2. Enter the **initial admin password** (shown by quick-start script)
3. Click **Install suggested plugins**
4. Create your admin user
5. Set Jenkins URL: `http://localhost:8080`
6. Click **Start using Jenkins**

**Note**: Jenkins Configuration as Code (JCasC) will automatically configure:
- Discord webhook credentials
- AWS credentials
- GitHub token
- SonarQube server connection

### 7. Configure SonarQube

1. Open http://localhost:9000
2. Login with `admin` / `admin` (or your custom password from .env)
3. Change the admin password when prompted
4. Click **Create new project**
   - Project key: `f1-data-platform`
   - Display name: `F1 Data Platform`
5. Click **Set Up**
6. Choose **With Jenkins**
7. Generate a token:
   - Name: `jenkins-f1-platform`
   - Copy the token
8. Update `.env` with the SonarQube token:
   ```bash
   SONARQUBE_TOKEN=squ_your_token_here
   ```
9. Restart Jenkins:
   ```powershell
   .\scripts\quick-start-jenkins.ps1 -Restart
   ```

### 8. Create Jenkins Pipeline Job

1. In Jenkins, click **New Item**
2. Enter name: `F1-Data-Platform-GitOps`
3. Select **Pipeline**
4. Click **OK**
5. In configuration:
   - **Build Triggers**: Check "GitHub hook trigger for GITScm polling"
   - **Pipeline**:
     - Definition: "Pipeline script from SCM"
     - SCM: Git
     - Repository URL: `https://github.com/Uebe3/f1-gitops.git`
     - Credentials: Select your GitHub token
     - Branch: `*/main`
     - Script Path: `ci/jenkins/Jenkinsfile-GitOps`
6. Click **Save**

### 9. Set Up GitHub Webhook

**For Local Development** (using ngrok):
```powershell
# Install ngrok (if not installed)
choco install ngrok

# Start ngrok tunnel
ngrok http 8080

# Copy the HTTPS URL (e.g., https://abc123.ngrok.io)
```

**Configure Webhook**:
1. Go to your GitHub repository → **Settings** → **Webhooks**
2. Click **Add webhook**
3. Set **Payload URL**: `https://your-jenkins-url:8080/github-webhook/`
   - For ngrok: `https://abc123.ngrok.io/github-webhook/`
4. Set **Content type**: `application/json`
5. Select **Let me select individual events**:
   - ✅ Pushes
   - ✅ Pull requests
6. Click **Add webhook**

### 10. Test the Pipeline

```powershell
# Make a small change
echo "# Test CI/CD" >> README.md

# Commit and push
git add README.md
git commit -m "test: trigger CI/CD pipeline"
git push origin main
```

Watch for:
1. ✅ GitHub webhook triggers Jenkins build
2. ✅ Jenkins runs the pipeline
3. ✅ SonarQube analyzes code quality
4. ✅ CloudFormation templates validated
5. ✅ Infrastructure deployed to AWS
6. ✅ Validation tests run
7. ✅ Discord notifications at each step

## 📊 Pipeline Parameters

The pipeline accepts these parameters:

| Parameter | Options | Default | Description |
|-----------|---------|---------|-------------|
| `ENVIRONMENT` | dev, staging, prod | dev | Target AWS environment |
| `DEPLOYMENT_TYPE` | full, foundation, glue, athena, access | full | Which stacks to deploy |
| `RUN_QUALITY_CHECKS` | true/false | true | Run SonarQube analysis |
| `RUN_TESTS` | true/false | true | Run validation tests |
| `SKIP_DEPLOY` | true/false | false | Skip AWS deployment (testing) |

## 🔧 Common Operations

### View Logs
```powershell
# All services
.\scripts\quick-start-jenkins.ps1 -Logs

# Specific service
.\scripts\quick-start-jenkins.ps1 -Logs -Service jenkins
.\scripts\quick-start-jenkins.ps1 -Logs -Service sonarqube
```

### Check Status
```powershell
.\scripts\quick-start-jenkins.ps1 -Status
```

### Restart Services
```powershell
.\scripts\quick-start-jenkins.ps1 -Restart
```

### Stop Everything
```powershell
.\scripts\quick-start-jenkins.ps1 -Stop
```

### Get Jenkins Admin Password
```powershell
docker exec jenkins-master cat /var/jenkins_home/secrets/initialAdminPassword
```

### Access Jenkins Container
```powershell
docker exec -it jenkins-master /bin/bash
```

### Access SonarQube Container
```powershell
docker exec -it sonarqube /bin/bash
```

## 🐛 Troubleshooting

### Jenkins Not Starting

**Problem**: Jenkins container exits immediately

**Solution**:
```powershell
# Check logs
docker logs jenkins-master

# Common issues:
# 1. Port 8080 already in use
Get-NetTCPConnection -LocalPort 8080
# Kill the process using port 8080

# 2. Volume permission issues
docker-compose down -v
docker-compose up -d
```

### SonarQube Not Starting

**Problem**: SonarQube shows "SonarQube is not available"

**Solution**:
```powershell
# SonarQube needs 2-3 minutes to start on first run
docker logs sonarqube -f

# Wait for: "SonarQube is operational"
```

### Discord Notifications Not Working

**Problem**: No Discord messages

**Solution**:
1. Verify webhook URL in `.env`
2. Test webhook manually:
   ```powershell
   $webhook = "YOUR_DISCORD_WEBHOOK_URL"
   $body = @{
       content = "Test from PowerShell"
   } | ConvertTo-Json

   Invoke-RestMethod -Uri $webhook -Method Post -Body $body -ContentType "application/json"
   ```
3. Check Jenkins credentials: Manage Jenkins → Credentials
4. Restart Jenkins after updating `.env`

### AWS Deployment Fails

**Problem**: CloudFormation deployment errors

**Solution**:
```powershell
# Verify AWS credentials
.\.venv\Scripts\activate
aws sts get-caller-identity

# Check CloudFormation events
aws cloudformation describe-stack-events --stack-name f1-data-platform-foundation-dev --region us-east-1

# Validate template locally
.\scripts\deployment\deploy-f1-platform.py --environment dev --region us-east-1
```

### Quality Gate Fails

**Problem**: SonarQube quality gate fails

**Solution**:
1. Open SonarQube: http://localhost:9000
2. View the project analysis
3. Fix code issues
4. Commit and push again

## 📚 Additional Resources

- [Complete Jenkins Setup Guide](../../docs/setup/JENKINS_SETUP_GUIDE.md)
- [Jenkins Configuration as Code](../../config/jenkins/jenkins.yaml)
- [Pipeline Definition](../../ci/jenkins/Jenkinsfile-GitOps)
- [Discord Notifier](../../ci/shared-libraries/discordNotifier.groovy)
- [Deployment Script](../../scripts/deployment/deploy-f1-platform.py)
- [Validation Script](../../scripts/deployment/validate-infrastructure.py)

## 🔒 Security Notes

- **Never commit `.env`** - it contains secrets
- **Rotate credentials** regularly
- **Use webhook secrets** for GitHub webhooks (production)
- **Enable HTTPS** for production (use Nginx service)
- **Restrict IAM permissions** to minimum required
- **Enable MFA** on AWS account

## 🎯 Next Steps

1. ✅ Set up CI/CD pipeline (you are here)
2. Configure production environment in AWS
3. Set up staging environment for testing
4. Add automated testing (unit, integration, E2E)
5. Configure monitoring and alerting
6. Set up automated backups
7. Implement blue-green deployments

## 📖 Pipeline Stages

### 1. Initialize
- Load configuration
- Load shared libraries
- Send deployment started notification to Discord

### 2. Code Quality (SonarQube)
- Run static code analysis
- Check code coverage
- Detect security vulnerabilities
- Wait for quality gate result
- Notify Discord of quality gate status

### 3. Validate CloudFormation Templates
- Validate all CloudFormation templates using AWS CLI
- Ensure templates are syntactically correct
- Notify Discord if validation fails

### 4. Deploy Infrastructure
- Upload templates to S3
- Deploy CloudFormation stacks in order:
  1. Foundation (S3, Glue, Athena, IAM, Lambda)
  2. Glue ETL Jobs
  3. Athena Analytics
  4. Access Control
- Notify Discord of deployment progress

### 5. Run Validation Tests
- Verify all stacks deployed successfully
- Check S3 buckets exist and configured correctly
- Verify Glue database and crawlers
- Confirm Athena workgroups
- Validate IAM roles
- Notify Discord of test results

### 6. Generate Deployment Report
- Create JSON report with deployment details
- Archive as Jenkins artifact
- Display summary

## 🏎️ Happy Building!

Your F1 Data Platform CI/CD pipeline is now ready to deploy infrastructure automatically with every code push!
