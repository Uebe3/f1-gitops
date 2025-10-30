# Variables for F1 Data Platform AWS Infrastructure

# General Configuration
variable "aws_region" {
  description = "AWS region where resources will be created"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "f1-data-platform"
}

variable "aws_account_id" {
  description = "AWS Account ID"
  type        = string
  default     = ""
}

# Data Lake Configuration
variable "data_lake_bucket" {
  description = "Base name for the S3 data lake bucket (will be suffixed with environment and account ID)"
  type        = string
  default     = "f1-data-lake"
}

variable "enable_versioning" {
  description = "Enable S3 bucket versioning"
  type        = bool
  default     = true
}

variable "enable_encryption" {
  description = "Enable S3 bucket encryption"
  type        = bool
  default     = true
}

# Glue Configuration
variable "glue_database_name" {
  description = "Name of the Glue catalog database"
  type        = string
  default     = "f1_data_dev"
}

variable "glue_crawler_name" {
  description = "Name of the Glue crawler"
  type        = string
  default     = "f1-data-crawler-dev"
}

variable "glue_job_name" {
  description = "Name of the Glue ETL job"
  type        = string
  default     = "f1-etl-job-dev"
}

# Lambda Configuration
variable "lambda_runtime" {
  description = "Lambda runtime version"
  type        = string
  default     = "python3.9"
}

variable "lambda_timeout" {
  description = "Lambda function timeout in seconds"
  type        = number
  default     = 300
}

variable "lambda_memory_size" {
  description = "Lambda function memory size in MB"
  type        = number
  default     = 512
}

# VPC Configuration
variable "create_vpc" {
  description = "Whether to create a VPC for the resources"
  type        = bool
  default     = false
}

variable "vpc_cidr_block" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

# Monitoring Configuration
variable "enable_cloudwatch" {
  description = "Enable CloudWatch monitoring and dashboards"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Number of days to retain CloudWatch logs"
  type        = number
  default     = 14
}

# Cost Management
variable "enable_cost_alerts" {
  description = "Enable cost anomaly detection and alerts"
  type        = bool
  default     = true
}

variable "cost_threshold" {
  description = "Cost threshold for alerts (in USD)"
  type        = number
  default     = 100
}

# Build Configuration
variable "build_version" {
  description = "Build version for deployment"
  type        = string
  default     = "latest"
}

# Resource Tags
variable "tags" {
  description = "A map of tags to assign to resources"
  type        = map(string)
  default = {
    Environment = "dev"
    Project     = "f1-data-platform"
    ManagedBy   = "terraform"
  }
}

# Feature Flags
variable "enable_backup" {
  description = "Enable automated backups"
  type        = bool
  default     = false
}

variable "enable_multi_az" {
  description = "Enable multi-AZ deployment"
  type        = bool
  default     = false
}

# Scaling Configuration
variable "lambda_concurrent_executions" {
  description = "Reserved concurrent executions for Lambda functions"
  type        = number
  default     = 10
}

variable "glue_max_capacity" {
  description = "Maximum capacity for Glue jobs"
  type        = number
  default     = 2
}

variable "glue_worker_type" {
  description = "Glue worker type (Standard, G.1X, G.2X)"
  type        = string
  default     = "Standard"
  validation {
    condition     = contains(["Standard", "G.1X", "G.2X"], var.glue_worker_type)
    error_message = "Glue worker type must be one of: Standard, G.1X, G.2X."
  }
}