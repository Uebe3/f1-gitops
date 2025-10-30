# F1 Data Platform - AWS Infrastructure
# Terraform configuration for multi-environment F1 data platform

terraform {
  required_version = ">= 1.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  
  backend "s3" {
    # Backend configuration will be provided during init
    # bucket = "f1-terraform-state-{account-id}"
    # key    = "f1-data-platform/{environment}/terraform.tfstate"
    # region = "us-east-1"
  }
}

# AWS Provider configuration
provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = var.tags
  }
}

# Data sources
data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# Local values
locals {
  account_id = data.aws_caller_identity.current.account_id
  region     = data.aws_region.current.name
  
  # Naming convention
  resource_prefix = "${var.project_name}-${var.environment}"
  
  # S3 bucket names (must be globally unique)
  data_lake_bucket_name = "${var.data_lake_bucket}-${var.environment}-${local.account_id}"
  
  # Common tags
  common_tags = merge(var.tags, {
    Environment   = var.environment
    Project       = var.project_name
    TerraformPath = path.cwd
    AccountId     = local.account_id
    Region        = local.region
  })
}

# Data Lake S3 Bucket
resource "aws_s3_bucket" "data_lake" {
  bucket = local.data_lake_bucket_name
  
  tags = merge(local.common_tags, {
    Name        = "${local.resource_prefix}-data-lake"
    Purpose     = "F1 Data Lake Storage"
    DataClass   = "analytics"
  })
}

# S3 Bucket versioning
resource "aws_s3_bucket_versioning" "data_lake" {
  bucket = aws_s3_bucket.data_lake.id
  versioning_configuration {
    status = var.enable_versioning ? "Enabled" : "Disabled"
  }
}

# S3 Bucket encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "data_lake" {
  bucket = aws_s3_bucket.data_lake.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# S3 Bucket public access block
resource "aws_s3_bucket_public_access_block" "data_lake" {
  bucket = aws_s3_bucket.data_lake.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# S3 Bucket lifecycle configuration
resource "aws_s3_bucket_lifecycle_configuration" "data_lake" {
  depends_on = [aws_s3_bucket_versioning.data_lake]
  bucket     = aws_s3_bucket.data_lake.id

  rule {
    id     = "data_lifecycle"
    status = "Enabled"

    # Raw data transitions
    expiration {
      days = var.environment == "prod" ? 2555 : 365  # 7 years for prod, 1 year for others
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    transition {
      days          = 365
      storage_class = "DEEP_ARCHIVE"
    }
  }

  rule {
    id     = "incomplete_multipart_uploads"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# IAM Role for Glue
resource "aws_iam_role" "glue_role" {
  name = "${local.resource_prefix}-glue-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "glue.amazonaws.com"
        }
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-glue-role"
  })
}

# IAM Policy for Glue to access S3
resource "aws_iam_role_policy" "glue_s3_policy" {
  name = "${local.resource_prefix}-glue-s3-policy"
  role = aws_iam_role.glue_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:GetObjectVersion",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.data_lake.arn,
          "${aws_s3_bucket.data_lake.arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

# Attach AWS managed policy for Glue
resource "aws_iam_role_policy_attachment" "glue_service_role" {
  role       = aws_iam_role.glue_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSGlueServiceRole"
}

# Glue Catalog Database
resource "aws_glue_catalog_database" "f1_database" {
  name        = var.glue_database_name
  description = "F1 Data Platform catalog database for ${var.environment}"

  tags = merge(local.common_tags, {
    Name = var.glue_database_name
  })
}

# Glue Crawler for F1 data
resource "aws_glue_crawler" "f1_crawler" {
  database_name = aws_glue_catalog_database.f1_database.name
  name          = var.glue_crawler_name
  role          = aws_iam_role.glue_role.arn

  s3_target {
    path = "s3://${aws_s3_bucket.data_lake.bucket}/raw-data/"
  }

  s3_target {
    path = "s3://${aws_s3_bucket.data_lake.bucket}/processed-data/"
  }

  configuration = jsonencode({
    Version = 1.0
    CrawlerOutput = {
      Partitions = {
        AddOrUpdateBehavior = "InheritFromTable"
      }
      Tables = {
        AddOrUpdateBehavior = "MergeNewColumns"
      }
    }
  })

  tags = merge(local.common_tags, {
    Name = var.glue_crawler_name
  })
}

# IAM Role for Lambda
resource "aws_iam_role" "lambda_role" {
  name = "${local.resource_prefix}-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-lambda-role"
  })
}

# IAM Policy for Lambda
resource "aws_iam_role_policy" "lambda_policy" {
  name = "${local.resource_prefix}-lambda-policy"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:GetObjectVersion",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.data_lake.arn,
          "${aws_s3_bucket.data_lake.arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "glue:StartCrawler",
          "glue:GetCrawler",
          "glue:StartJobRun",
          "glue:GetJobRun",
          "glue:GetJobRuns"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

# Attach AWS managed policy for Lambda basic execution
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# CloudWatch Log Groups for Lambda functions
resource "aws_cloudwatch_log_group" "lambda_logs" {
  for_each = toset(["data-processor", "data-scheduler"])
  
  name              = "/aws/lambda/${local.resource_prefix}-${each.key}"
  retention_in_days = var.log_retention_days

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-${each.key}-logs"
  })
}

# Athena Workgroup
resource "aws_athena_workgroup" "f1_analytics" {
  name  = "${local.resource_prefix}-analytics"
  state = "ENABLED"

  configuration {
    enforce_workgroup_configuration    = true
    publish_cloudwatch_metrics_enabled = var.enable_cloudwatch

    result_configuration {
      output_location = "s3://${aws_s3_bucket.data_lake.bucket}/athena-results/"

      encryption_configuration {
        encryption_option = "SSE_S3"
      }
    }
  }

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-analytics"
  })
}

# CloudWatch Dashboard (if monitoring enabled)
resource "aws_cloudwatch_dashboard" "f1_dashboard" {
  count = var.enable_cloudwatch ? 1 : 0
  
  dashboard_name = "${local.resource_prefix}-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["AWS/S3", "BucketSizeBytes", "BucketName", aws_s3_bucket.data_lake.bucket, "StorageType", "StandardStorage"],
            ["AWS/S3", "NumberOfObjects", "BucketName", aws_s3_bucket.data_lake.bucket, "StorageType", "AllStorageTypes"]
          ]
          view    = "timeSeries"
          stacked = false
          region  = local.region
          title   = "S3 Data Lake Metrics"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["AWS/Glue", "glue.driver.aggregate.numCompletedTasks", "JobName", var.glue_job_name],
            ["AWS/Glue", "glue.driver.aggregate.numFailedTasks", "JobName", var.glue_job_name]
          ]
          view    = "timeSeries"
          stacked = false
          region  = local.region
          title   = "Glue Job Metrics"
          period  = 300
        }
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-dashboard"
  })
}

# Cost Anomaly Detection (if enabled)
resource "aws_ce_anomaly_detector" "f1_cost_anomaly" {
  count = var.enable_cost_alerts ? 1 : 0
  
  name         = "${local.resource_prefix}-cost-anomaly"
  monitor_type = "DIMENSIONAL"

  specification = jsonencode({
    dimension_key           = "SERVICE"
    dimension_value_matches = ["Amazon Simple Storage Service", "AWS Glue", "Amazon Athena"]
  })

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-cost-anomaly"
  })
}

# SNS Topic for alerts
resource "aws_sns_topic" "f1_alerts" {
  count = var.enable_cloudwatch ? 1 : 0
  
  name = "${local.resource_prefix}-alerts"

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-alerts"
  })
}

# CloudWatch Alarms for S3 bucket
resource "aws_cloudwatch_metric_alarm" "s3_bucket_size" {
  count = var.enable_cloudwatch ? 1 : 0
  
  alarm_name          = "${local.resource_prefix}-s3-bucket-size"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "BucketSizeBytes"
  namespace           = "AWS/S3"
  period              = "86400"  # 24 hours
  statistic           = "Average"
  threshold           = "1000000000000"  # 1TB
  alarm_description   = "This metric monitors S3 bucket size"
  alarm_actions       = [aws_sns_topic.f1_alerts[0].arn]

  dimensions = {
    BucketName  = aws_s3_bucket.data_lake.bucket
    StorageType = "StandardStorage"
  }

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-s3-size-alarm"
  })
}