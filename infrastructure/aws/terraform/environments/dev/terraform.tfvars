# Terraform variables for F1 Data Platform - Development Environment

# AWS Configuration
aws_region          = "us-east-1"
aws_account_id      = "" # Will be populated by Jenkins or set manually

# Environment Configuration
environment         = "dev"
project_name        = "f1-data-platform"

# Data Lake Configuration
data_lake_bucket    = "f1-data-lake-dev"
enable_versioning   = true
enable_encryption   = true

# Glue Configuration
glue_database_name  = "f1_data_dev"
glue_crawler_name   = "f1-data-crawler-dev"
glue_job_name       = "f1-etl-job-dev"

# Lambda Configuration
lambda_runtime      = "python3.9"
lambda_timeout      = 300
lambda_memory_size  = 512

# VPC Configuration
create_vpc          = false
vpc_cidr_block      = "10.0.0.0/16"

# Monitoring Configuration
enable_cloudwatch   = true
log_retention_days  = 14

# Cost Management
enable_cost_alerts  = true
cost_threshold      = 100  # USD per month

# Tags
tags = {
  Environment = "dev"
  Project     = "f1-data-platform"
  Owner       = "data-engineering-team"
  ManagedBy   = "terraform"
}