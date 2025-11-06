"""
Infrastructure Validation Script
Validates deployed AWS CloudFormation infrastructure
"""

import boto3
import argparse
import sys
from typing import Dict, List, Tuple
from pathlib import Path

class InfrastructureValidator:
    """Validates deployed AWS infrastructure"""
    
    def __init__(self, environment: str, region: str = 'us-east-1'):
        self.environment = environment
        self.region = region
        self.cf_client = boto3.client('cloudformation', region_name=region)
        self.s3_client = boto3.client('s3', region_name=region)
        self.glue_client = boto3.client('glue', region_name=region)
        self.athena_client = boto3.client('athena', region_name=region)
        self.iam_client = boto3.client('iam', region_name=region)
        
        self.validation_results = {
            'passed': [],
            'failed': [],
            'warnings': []
        }
    
    def validate_all(self) -> bool:
        """Run all validation checks"""
        print(f"\n🧪 Validating F1 Data Platform infrastructure in {self.environment}...")
        print("=" * 70)
        
        # Validate stacks exist and are in good state
        self.validate_cloudformation_stacks()
        
        # Validate S3 buckets
        self.validate_s3_buckets()
        
        # Validate Glue resources
        self.validate_glue_resources()
        
        # Validate Athena resources
        self.validate_athena_resources()
        
        # Validate IAM roles and policies
        self.validate_iam_resources()
        
        # Print summary
        self.print_summary()
        
        # Return overall status
        return len(self.validation_results['failed']) == 0
    
    def validate_cloudformation_stacks(self):
        """Validate CloudFormation stacks are deployed and healthy"""
        print("\n📋 Validating CloudFormation Stacks...")
        
        expected_stacks = [
            f'f1-data-platform-foundation-{self.environment}',
            f'f1-data-platform-glue-etl-{self.environment}',
            f'f1-data-platform-athena-analytics-{self.environment}',
            f'f1-data-platform-access-{self.environment}'
        ]
        
        for stack_name in expected_stacks:
            try:
                response = self.cf_client.describe_stacks(StackName=stack_name)
                stack = response['Stacks'][0]
                status = stack['StackStatus']
                
                if 'COMPLETE' in status and 'ROLLBACK' not in status:
                    self.validation_results['passed'].append(
                        f"✅ Stack {stack_name}: {status}"
                    )
                    print(f"  ✅ {stack_name}: {status}")
                else:
                    self.validation_results['failed'].append(
                        f"❌ Stack {stack_name}: {status}"
                    )
                    print(f"  ❌ {stack_name}: {status}")
                    
            except self.cf_client.exceptions.ClientError as e:
                if 'does not exist' in str(e):
                    self.validation_results['failed'].append(
                        f"❌ Stack {stack_name}: NOT FOUND"
                    )
                    print(f"  ❌ {stack_name}: NOT FOUND")
                else:
                    self.validation_results['failed'].append(
                        f"❌ Stack {stack_name}: ERROR - {str(e)}"
                    )
                    print(f"  ❌ {stack_name}: ERROR - {str(e)}")
    
    def validate_s3_buckets(self):
        """Validate S3 buckets exist and have correct configuration"""
        print("\n🪣 Validating S3 Buckets...")
        
        expected_buckets = [
            f'f1-data-lake-{self.environment}',
            f'f1-data-platform-athena-results-{self.environment}',
            f'f1-platform-cf-templates-{self.environment}'
        ]
        
        for bucket_name in expected_buckets:
            try:
                # Check bucket exists
                self.s3_client.head_bucket(Bucket=bucket_name)
                
                # Check versioning
                versioning = self.s3_client.get_bucket_versioning(Bucket=bucket_name)
                versioning_status = versioning.get('Status', 'Disabled')
                
                # Check encryption
                try:
                    encryption = self.s3_client.get_bucket_encryption(Bucket=bucket_name)
                    encryption_enabled = True
                except self.s3_client.exceptions.ClientError:
                    encryption_enabled = False
                
                if versioning_status == 'Enabled' and encryption_enabled:
                    self.validation_results['passed'].append(
                        f"✅ Bucket {bucket_name}: Versioning={versioning_status}, Encryption=Enabled"
                    )
                    print(f"  ✅ {bucket_name}: Versioning={versioning_status}, Encryption=Enabled")
                else:
                    self.validation_results['warnings'].append(
                        f"⚠️ Bucket {bucket_name}: Versioning={versioning_status}, Encryption={'Enabled' if encryption_enabled else 'Disabled'}"
                    )
                    print(f"  ⚠️ {bucket_name}: Versioning={versioning_status}, Encryption={'Enabled' if encryption_enabled else 'Disabled'}")
                    
            except self.s3_client.exceptions.ClientError as e:
                if e.response['Error']['Code'] == '404':
                    self.validation_results['failed'].append(
                        f"❌ Bucket {bucket_name}: NOT FOUND"
                    )
                    print(f"  ❌ {bucket_name}: NOT FOUND")
                else:
                    self.validation_results['failed'].append(
                        f"❌ Bucket {bucket_name}: ERROR - {str(e)}"
                    )
                    print(f"  ❌ {bucket_name}: ERROR - {str(e)}")
    
    def validate_glue_resources(self):
        """Validate Glue databases and crawlers"""
        print("\n🔍 Validating Glue Resources...")
        
        # Check Glue database - use correct naming from CloudFormation template
        database_name = f'f1-data-platform_{self.environment}'
        try:
            self.glue_client.get_database(Name=database_name)
            self.validation_results['passed'].append(
                f"✅ Glue Database {database_name}: EXISTS"
            )
            print(f"  ✅ Glue Database {database_name}: EXISTS")
        except self.glue_client.exceptions.EntityNotFoundException:
            self.validation_results['failed'].append(
                f"❌ Glue Database {database_name}: NOT FOUND"
            )
            print(f"  ❌ Glue Database {database_name}: NOT FOUND")
        
        # Check Glue crawler
        crawler_name = f'f1-data-crawler-{self.environment}'
        try:
            response = self.glue_client.get_crawler(Name=crawler_name)
            crawler_state = response['Crawler']['State']
            self.validation_results['passed'].append(
                f"✅ Glue Crawler {crawler_name}: {crawler_state}"
            )
            print(f"  ✅ Glue Crawler {crawler_name}: {crawler_state}")
        except self.glue_client.exceptions.EntityNotFoundException:
            self.validation_results['warnings'].append(
                f"⚠️ Glue Crawler {crawler_name}: NOT FOUND (optional)"
            )
            print(f"  ⚠️ Glue Crawler {crawler_name}: NOT FOUND (optional)")
    
    def validate_athena_resources(self):
        """Validate Athena workgroups"""
        print("\n📊 Validating Athena Resources...")
        
        # Use correct workgroup name from CloudFormation template
        workgroup_name = f'f1-data-platform-{self.environment}'
        try:
            response = self.athena_client.get_work_group(WorkGroup=workgroup_name)
            state = response['WorkGroup']['State']
            
            if state == 'ENABLED':
                self.validation_results['passed'].append(
                    f"✅ Athena Workgroup {workgroup_name}: {state}"
                )
                print(f"  ✅ Athena Workgroup {workgroup_name}: {state}")
            else:
                self.validation_results['warnings'].append(
                    f"⚠️ Athena Workgroup {workgroup_name}: {state}"
                )
                print(f"  ⚠️ Athena Workgroup {workgroup_name}: {state}")
                
        except self.athena_client.exceptions.InvalidRequestException:
            self.validation_results['failed'].append(
                f"❌ Athena Workgroup {workgroup_name}: NOT FOUND"
            )
            print(f"  ❌ Athena Workgroup {workgroup_name}: NOT FOUND")
    
    def validate_iam_resources(self):
        """Validate IAM roles exist"""
        print("\n🔐 Validating IAM Resources...")
        
        # Use correct role names from CloudFormation template
        # Pattern: ${ProjectName}-{service}-role-${Environment}
        expected_roles = [
            f'f1-data-platform-glue-role-{self.environment}',
            f'f1-data-platform-lambda-role-{self.environment}',
            f'f1-data-platform-athena-role-{self.environment}'
        ]
        
        for role_name in expected_roles:
            try:
                self.iam_client.get_role(RoleName=role_name)
                self.validation_results['passed'].append(
                    f"✅ IAM Role {role_name}: EXISTS"
                )
                print(f"  ✅ IAM Role {role_name}: EXISTS")
            except self.iam_client.exceptions.NoSuchEntityException:
                self.validation_results['warnings'].append(
                    f"⚠️ IAM Role {role_name}: NOT FOUND (may use default naming)"
                )
                print(f"  ⚠️ IAM Role {role_name}: NOT FOUND (may use default naming)")
    
    def print_summary(self):
        """Print validation summary"""
        print("\n" + "=" * 70)
        print("📊 Validation Summary")
        print("=" * 70)
        
        total = (len(self.validation_results['passed']) + 
                len(self.validation_results['failed']) + 
                len(self.validation_results['warnings']))
        
        print(f"\nTotal Checks: {total}")
        print(f"✅ Passed: {len(self.validation_results['passed'])}")
        print(f"❌ Failed: {len(self.validation_results['failed'])}")
        print(f"⚠️ Warnings: {len(self.validation_results['warnings'])}")
        
        if self.validation_results['failed']:
            print("\n❌ Failed Checks:")
            for check in self.validation_results['failed']:
                print(f"  {check}")
        
        if self.validation_results['warnings']:
            print("\n⚠️ Warnings:")
            for check in self.validation_results['warnings']:
                print(f"  {check}")
        
        print("\n" + "=" * 70)
        
        if len(self.validation_results['failed']) == 0:
            print("✅ All critical validation checks passed!")
            return 0
        else:
            print("❌ Some validation checks failed!")
            return 1


def main():
    parser = argparse.ArgumentParser(
        description='Validate F1 Data Platform infrastructure'
    )
    parser.add_argument(
        '--environment',
        required=True,
        choices=['dev', 'staging', 'prod'],
        help='Deployment environment'
    )
    parser.add_argument(
        '--region',
        default='us-east-1',
        help='AWS region (default: us-east-1)'
    )
    
    args = parser.parse_args()
    
    # Create validator and run checks
    validator = InfrastructureValidator(args.environment, args.region)
    success = validator.validate_all()
    
    # Exit with appropriate code
    sys.exit(0 if success else 1)


if __name__ == '__main__':
    main()
