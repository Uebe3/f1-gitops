# Required Changes for showcase-f1-pipeline Repository

## Overview
The following changes are required in the `showcase-f1-pipeline` repository to ensure full compatibility with the f1-gitops deployment pipeline and to resolve current warnings and issues.

---

## 1. Add MonitoringLevel Parameter to CloudFormation Templates

### Status: **CRITICAL - Required for Cost Control**

### Affected Files:
- `config/cloudformation/02-glue-etl-jobs.yaml`
- `config/cloudformation/03-athena-analytics.yaml`
- `config/cloudformation/04-data-lake.yaml`
- `config/cloudformation/05-access-control.yaml`

### Required Changes:

#### Add Parameter Definition
Add the following parameter to **all CloudFormation templates**:

```yaml
Parameters:
  # ... existing parameters ...
  
  MonitoringLevel:
    Type: String
    Default: minimal
    AllowedValues: [none, minimal, standard, full]
    Description: |
      CloudWatch monitoring and logging level:
      - none: No CloudWatch monitoring or logging ($0/month)
      - minimal: Basic metrics only (~$5-10/month)
      - standard: Metrics + basic logs (~$25-50/month)
      - full: Detailed metrics + comprehensive logs + alarms (~$100-150/month)
```

#### Add Conditions
Add these conditions to support conditional resource creation:

```yaml
Conditions:
  EnableMinimalMonitoring: !Or
    - !Equals [!Ref MonitoringLevel, minimal]
    - !Equals [!Ref MonitoringLevel, standard]
    - !Equals [!Ref MonitoringLevel, full]
  
  EnableStandardMonitoring: !Or
    - !Equals [!Ref MonitoringLevel, standard]
    - !Equals [!Ref MonitoringLevel, full]
  
  EnableFullMonitoring: !Equals [!Ref MonitoringLevel, full]
```

#### Apply Conditions to CloudWatch Resources
Wrap all CloudWatch-related resources with appropriate conditions:

```yaml
# Example: Basic CloudWatch Logs
GlueJobLogGroup:
  Type: AWS::Logs::LogGroup
  Condition: EnableMinimalMonitoring  # Only create if monitoring >= minimal
  Properties:
    # ... existing properties ...

# Example: Detailed Metrics
DetailedMetricAlarm:
  Type: AWS::CloudWatch::Alarm
  Condition: EnableFullMonitoring  # Only create if monitoring = full
  Properties:
    # ... existing properties ...
```

### Why This Matters:
The f1-gitops pipeline passes `--monitoring-level minimal` to all stack deployments. Without this parameter in the templates:
- **Current Issue**: Stack updates fail with error: `Parameters: [MonitoringLevel] do not exist in the template`
- **Impact**: Cannot update existing stacks, forcing recreation
- **Cost Savings**: This feature can save $50-100/month by limiting CloudWatch resources in dev environments

---

## 2. Enable S3 Bucket Versioning

### Status: **HIGH PRIORITY - Data Protection**

### Affected Files:
- `config/cloudformation/02-glue-etl-jobs.yaml` (if it creates buckets)
- `config/cloudformation/04-data-lake.yaml`

### Required Changes:

Add versioning configuration to all S3 buckets:

```yaml
Resources:
  DataLakeBucket:
    Type: AWS::S3::Bucket
    Properties:
      BucketName: !Sub '\${ProjectName}-data-\${Environment}'
      VersioningConfiguration:
        Status: Enabled  # ADD THIS
      # ... other properties ...
      
  # For temporary/results buckets, add lifecycle policy for old versions
  TemporaryBucket:
    Type: AWS::S3::Bucket
    Properties:
      BucketName: !Sub '\${ProjectName}-temp-\${Environment}'
      VersioningConfiguration:
        Status: Enabled
      LifecycleConfiguration:
        Rules:
          - Id: DeleteOldVersions
            Status: Enabled
            NoncurrentVersionExpirationInDays: 7  # Delete old versions after 7 days
```

### Current Warnings:
```
⚠️ f1-data-platform-athena-results-dev: Versioning=Disabled
⚠️ f1-platform-cf-templates-dev: Versioning=Disabled
```

### Why This Matters:
- **Data Protection**: Prevents accidental data loss
- **Compliance**: Many AWS best practices require versioning
- **Recovery**: Allows recovery from accidental deletions or overwrites
- **Minimal Cost**: Storage cost only for changed files

---

## 3. Standardize IAM Role Naming

### Status: **MEDIUM PRIORITY - Validation Consistency**

### Affected Files:
- `config/cloudformation/02-glue-etl-jobs.yaml`
- `config/cloudformation/05-access-control.yaml`

### Required Changes:

Ensure all IAM roles follow the standardized naming pattern:

```yaml
# Pattern: \${ProjectName}-{service}-role-\${Environment}

GlueServiceRole:
  Type: AWS::IAM::Role
  Properties:
    RoleName: !Sub '\${ProjectName}-glue-role-\${Environment}'
    # NOT: f1-glue-execution-role-\${Environment}
    # ... rest of properties ...

LambdaExecutionRole:
  Type: AWS::IAM::Role
  Properties:
    RoleName: !Sub '\${ProjectName}-lambda-role-\${Environment}'
    # NOT: f1-lambda-execution-role-\${Environment}
    # ... rest of properties ...

AthenaServiceRole:
  Type: AWS::IAM::Role
  Properties:
    RoleName: !Sub '\${ProjectName}-athena-role-\${Environment}'
    # ... rest of properties ...
```

### Current Warnings:
```
⚠️ IAM Role f1-glue-execution-role-dev: NOT FOUND (may use default naming)
⚠️ IAM Role f1-lambda-execution-role-dev: NOT FOUND (may use default naming)
```

### Why This Matters:
- **Consistency**: Matches f1-gitops naming convention
- **Validation**: Allows proper infrastructure validation
- **Clarity**: Makes it clear which project/environment owns each role

---

## 4. Add Missing Glue Crawler (Optional)

### Status: **LOW PRIORITY - Optional Resource**

### Affected Files:
- `config/cloudformation/02-glue-etl-jobs.yaml`

### Required Changes:

If the Glue Crawler is needed for data discovery, add:

```yaml
DataCrawler:
  Type: AWS::Glue::Crawler
  Properties:
    Name: !Sub 'f1-data-crawler-\${Environment}'
    Role: !GetAtt GlueServiceRole.Arn
    DatabaseName: !Ref GlueDatabase
    Targets:
      S3Targets:
        - Path: !Sub 's3://\${DataLakeBucket}/raw/'
        - Path: !Sub 's3://\${DataLakeBucket}/processed/'
    SchemaChangePolicy:
      UpdateBehavior: UPDATE_IN_DATABASE
      DeleteBehavior: LOG
    Schedule:
      ScheduleExpression: 'cron(0 2 * * ? *)'  # Run daily at 2 AM
```

### Current Warning:
```
⚠️ Glue Crawler f1-data-crawler-dev: NOT FOUND (optional)
```

### Why This Matters:
- **Data Discovery**: Automatically discovers and catalogs new data
- **Schema Management**: Keeps Glue Data Catalog up to date
- **Automation**: Reduces manual schema management

---

## Implementation Priority

### Immediate (This Sprint):
1. ✅ **Add MonitoringLevel parameter** - Blocks stack updates, critical for cost control
2. ✅ **Enable S3 versioning** - Data protection best practice

### Next Sprint:
3. 🔄 **Standardize IAM role naming** - Improves validation and consistency
4. 📋 **Add Glue Crawler** - If automated data discovery is needed

---

## Testing Requirements

After implementing these changes:

1. **Parameter Validation**: Ensure all templates validate with MonitoringLevel parameter
   ```bash
   aws cloudformation validate-template \
     --template-body file://config/cloudformation/02-glue-etl-jobs.yaml
   ```

2. **Stack Update Test**: Deploy to dev environment with monitoring level
   ```bash
   aws cloudformation update-stack \
     --stack-name f1-data-platform-glue-etl-dev \
     --template-body file://config/cloudformation/02-glue-etl-jobs.yaml \
     --parameters ParameterKey=MonitoringLevel,ParameterValue=minimal
   ```

3. **Resource Verification**: Confirm resources are created/omitted based on monitoring level

---

## Questions or Issues?

If you have questions about these requirements or need clarification on implementation:
- **GitOps Team Contact**: [Your Contact Info]
- **Reference Implementation**: See `f1-gitops/infrastructure/aws/cloudformation/01-data-lake-foundation-fixed.yaml`
- **Slack Channel**: #f1-deployments

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-11-06 | Initial requirements document |

---

**Document Owner**: F1 GitOps Team  
**Last Updated**: November 6, 2025  
**Review Date**: December 6, 2025
