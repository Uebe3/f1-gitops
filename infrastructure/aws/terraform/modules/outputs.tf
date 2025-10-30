# Outputs for F1 Data Platform AWS Infrastructure

# S3 Data Lake
output "data_lake_bucket_name" {
  description = "Name of the S3 data lake bucket"
  value       = aws_s3_bucket.data_lake.bucket
}

output "data_lake_bucket_arn" {
  description = "ARN of the S3 data lake bucket"
  value       = aws_s3_bucket.data_lake.arn
}

output "data_lake_bucket_domain_name" {
  description = "Domain name of the S3 data lake bucket"
  value       = aws_s3_bucket.data_lake.bucket_domain_name
}

# Glue Resources
output "glue_database_name" {
  description = "Name of the Glue catalog database"
  value       = aws_glue_catalog_database.f1_database.name
}

output "glue_crawler_name" {
  description = "Name of the Glue crawler"
  value       = aws_glue_crawler.f1_crawler.name
}

output "glue_crawler_arn" {
  description = "ARN of the Glue crawler"
  value       = aws_glue_crawler.f1_crawler.arn
}

output "glue_role_arn" {
  description = "ARN of the Glue service role"
  value       = aws_iam_role.glue_role.arn
}

# IAM Roles
output "lambda_role_arn" {
  description = "ARN of the Lambda execution role"
  value       = aws_iam_role.lambda_role.arn
}

output "lambda_role_name" {
  description = "Name of the Lambda execution role"
  value       = aws_iam_role.lambda_role.name
}

# Athena
output "athena_workgroup_name" {
  description = "Name of the Athena workgroup"
  value       = aws_athena_workgroup.f1_analytics.name
}

output "athena_workgroup_arn" {
  description = "ARN of the Athena workgroup"
  value       = aws_athena_workgroup.f1_analytics.arn
}

# CloudWatch
output "cloudwatch_dashboard_url" {
  description = "URL of the CloudWatch dashboard"
  value       = var.enable_cloudwatch ? "https://${local.region}.console.aws.amazon.com/cloudwatch/home?region=${local.region}#dashboards:name=${aws_cloudwatch_dashboard.f1_dashboard[0].dashboard_name}" : null
}

output "cloudwatch_log_groups" {
  description = "Names of the CloudWatch log groups"
  value       = [for lg in aws_cloudwatch_log_group.lambda_logs : lg.name]
}

# SNS Topic
output "alerts_topic_arn" {
  description = "ARN of the SNS alerts topic"
  value       = var.enable_cloudwatch ? aws_sns_topic.f1_alerts[0].arn : null
}

# General Information
output "aws_region" {
  description = "AWS region"
  value       = local.region
}

output "aws_account_id" {
  description = "AWS account ID"
  value       = local.account_id
}

output "environment" {
  description = "Environment name"
  value       = var.environment
}

output "resource_prefix" {
  description = "Resource naming prefix"
  value       = local.resource_prefix
}

# Data Lake Endpoints
output "data_lake_endpoints" {
  description = "Important S3 endpoints for the data lake"
  value = {
    raw_data       = "s3://${aws_s3_bucket.data_lake.bucket}/raw-data/"
    processed_data = "s3://${aws_s3_bucket.data_lake.bucket}/processed-data/"
    analytics_data = "s3://${aws_s3_bucket.data_lake.bucket}/analytics/"
    athena_results = "s3://${aws_s3_bucket.data_lake.bucket}/athena-results/"
    scripts        = "s3://${aws_s3_bucket.data_lake.bucket}/scripts/"
  }
}

# Infrastructure Summary
output "infrastructure_summary" {
  description = "Summary of deployed infrastructure"
  value = {
    environment             = var.environment
    region                 = local.region
    data_lake_bucket       = aws_s3_bucket.data_lake.bucket
    glue_database          = aws_glue_catalog_database.f1_database.name
    athena_workgroup       = aws_athena_workgroup.f1_analytics.name
    monitoring_enabled     = var.enable_cloudwatch
    cost_alerts_enabled    = var.enable_cost_alerts
    backup_enabled         = var.enable_backup
    multi_az_enabled       = var.enable_multi_az
  }
}

# Connection Strings and Configuration
output "deployment_config" {
  description = "Configuration values for application deployment"
  value = {
    AWS_REGION              = local.region
    AWS_ACCOUNT_ID         = local.account_id
    DATA_LAKE_BUCKET       = aws_s3_bucket.data_lake.bucket
    GLUE_DATABASE_NAME     = aws_glue_catalog_database.f1_database.name
    GLUE_CRAWLER_NAME      = aws_glue_crawler.f1_crawler.name
    ATHENA_WORKGROUP       = aws_athena_workgroup.f1_analytics.name
    LAMBDA_ROLE_ARN        = aws_iam_role.lambda_role.arn
    GLUE_ROLE_ARN          = aws_iam_role.glue_role.arn
    ENVIRONMENT            = var.environment
    BUILD_VERSION          = var.build_version
  }
  sensitive = false
}

# Cost Estimation (for reference)
output "estimated_monthly_cost" {
  description = "Estimated monthly cost breakdown (USD)"
  value = {
    s3_storage     = "~$23/TB/month (Standard storage)"
    glue_jobs      = "~$0.44/DPU-Hour"
    athena_queries = "~$5/TB scanned"
    lambda         = "~$0.20/1M requests + $0.00001667/GB-second"
    cloudwatch     = "~$0.30/month per custom metric"
    note           = "Actual costs depend on usage patterns and data volume"
  }
}