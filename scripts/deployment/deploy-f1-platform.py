#!/usr/bin/env python3
"""
CloudFormation Deployment Script for F1 Data Platform
Modern AWS Serverless Architecture

This script deploys the complete F1 data platform infrastructure using AWS CloudFormation.
It handles stack dependencies and provides deployment status monitoring.
"""

import boto3
import json
import time
import argparse
from typing import Dict, List, Optional
from pathlib import Path


class CloudFormationDeployer:
    """Handles CloudFormation stack deployments with dependency management."""
    
    def __init__(self, region: str = 'us-east-1', profile: Optional[str] = None):
        """Initialize the deployer with AWS configuration."""
        self.region = region
        self.session = boto3.Session(profile_name=profile) if profile else boto3.Session()
        self.cf_client = self.session.client('cloudformation', region_name=region)
        self.s3_client = self.session.client('s3', region_name=region)
        
    def create_deployment_bucket(self, bucket_name: str) -> str:
        """Create S3 bucket for CloudFormation templates if it doesn't exist."""
        try:
            # Check if bucket exists
            self.s3_client.head_bucket(Bucket=bucket_name)
            print(f"✓ Deployment bucket '{bucket_name}' already exists")
        except:
            # Create bucket
            if self.region == 'us-east-1':
                self.s3_client.create_bucket(Bucket=bucket_name)
            else:
                self.s3_client.create_bucket(
                    Bucket=bucket_name,
                    CreateBucketConfiguration={'LocationConstraint': self.region}
                )
            print(f"✓ Created deployment bucket: {bucket_name}")
            
        return bucket_name
    
    def upload_template(self, template_path: Path, bucket_name: str, key_prefix: str = "") -> str:
        """Upload CloudFormation template to S3."""
        key = f"{key_prefix}{template_path.name}" if key_prefix else template_path.name
        
        with open(template_path, 'r') as f:
            template_content = f.read()
            
        self.s3_client.put_object(
            Bucket=bucket_name,
            Key=key,
            Body=template_content,
            ContentType='text/yaml'
        )
        
        template_url = f"https://{bucket_name}.s3.{self.region}.amazonaws.com/{key}"
        print(f"✓ Uploaded template: {template_path.name}")
        return template_url
    
    def deploy_stack(self, stack_name: str, template_url: str, parameters: Dict[str, str], 
                    capabilities: List[str] = None) -> bool:
        """Deploy or update a CloudFormation stack."""
        
        # Convert parameters to CloudFormation format
        cf_parameters = [
            {"ParameterKey": key, "ParameterValue": value}
            for key, value in parameters.items()
        ]
        
        # Check if stack exists
        stack_exists = self._stack_exists(stack_name)
        
        try:
            if stack_exists:
                print(f"📝 Updating stack: {stack_name}")
                self.cf_client.update_stack(
                    StackName=stack_name,
                    TemplateURL=template_url,
                    Parameters=cf_parameters,
                    Capabilities=capabilities or []
                )
            else:
                print(f"🚀 Creating stack: {stack_name}")
                self.cf_client.create_stack(
                    StackName=stack_name,
                    TemplateURL=template_url,
                    Parameters=cf_parameters,
                    Capabilities=capabilities or []
                )
            
            # Wait for stack operation to complete
            return self._wait_for_stack_operation(stack_name)
            
        except Exception as e:
            print(f"❌ Failed to deploy {stack_name}: {str(e)}")
            return False
    
    def _stack_exists(self, stack_name: str) -> bool:
        """Check if CloudFormation stack exists."""
        try:
            response = self.cf_client.describe_stacks(StackName=stack_name)
            stack_status = response['Stacks'][0]['StackStatus']
            return stack_status not in ['DELETE_COMPLETE']
        except:
            return False
    
    def _wait_for_stack_operation(self, stack_name: str) -> bool:
        """Wait for CloudFormation stack operation to complete."""
        print(f"⏳ Waiting for stack operation to complete...")
        
        while True:
            try:
                response = self.cf_client.describe_stacks(StackName=stack_name)
                stack_status = response['Stacks'][0]['StackStatus']
                
                if stack_status in ['CREATE_COMPLETE', 'UPDATE_COMPLETE']:
                    print(f"✅ Stack {stack_name} operation completed successfully")
                    return True
                elif stack_status in ['CREATE_FAILED', 'UPDATE_FAILED', 
                                    'ROLLBACK_COMPLETE', 'UPDATE_ROLLBACK_COMPLETE']:
                    print(f"❌ Stack {stack_name} operation failed: {stack_status}")
                    self._print_stack_events(stack_name)
                    return False
                elif 'IN_PROGRESS' in stack_status:
                    print(f"⏳ Stack status: {stack_status}")
                    time.sleep(30)
                else:
                    print(f"⚠️  Unknown stack status: {stack_status}")
                    time.sleep(30)
                    
            except Exception as e:
                print(f"❌ Error checking stack status: {str(e)}")
                return False
    
    def _print_stack_events(self, stack_name: str):
        """Print recent stack events for debugging."""
        try:
            events = self.cf_client.describe_stack_events(StackName=stack_name)
            print("\n📋 Recent stack events:")
            for event in events['StackEvents'][:5]:  # Show last 5 events
                timestamp = event.get('Timestamp', 'Unknown')
                resource_type = event.get('ResourceType', 'Unknown')
                logical_id = event.get('LogicalResourceId', 'Unknown')
                status = event.get('ResourceStatus', 'Unknown')
                reason = event.get('ResourceStatusReason', '')
                print(f"  {timestamp} - {resource_type} {logical_id}: {status} {reason}")
        except Exception as e:
            print(f"Could not retrieve stack events: {str(e)}")
    
    def get_stack_outputs(self, stack_name: str) -> Dict[str, str]:
        """Get CloudFormation stack outputs."""
        try:
            response = self.cf_client.describe_stacks(StackName=stack_name)
            outputs = response['Stacks'][0].get('Outputs', [])
            return {output['OutputKey']: output['OutputValue'] for output in outputs}
        except Exception as e:
            print(f"❌ Failed to get outputs from {stack_name}: {str(e)}")
            return {}


def main():
    """Main deployment orchestration."""
    parser = argparse.ArgumentParser(description='Deploy F1 Data Platform AWS Infrastructure')
    parser.add_argument('--environment', '-e', default='dev', 
                       choices=['dev', 'staging', 'prod'],
                       help='Environment to deploy (default: dev)')
    parser.add_argument('--region', '-r', default='us-east-1',
                       help='AWS region (default: us-east-1)')
    parser.add_argument('--profile', '-p', default=None,
                       help='AWS profile to use')
    parser.add_argument('--project-name', default='f1-data-platform',
                       help='Project name for resource naming')
    parser.add_argument('--skip-foundation', action='store_true',
                       help='Skip foundation stack deployment')
    parser.add_argument('--skip-glue', action='store_true',
                       help='Skip Glue ETL stack deployment')
    parser.add_argument('--skip-athena', action='store_true',
                       help='Skip Athena analytics stack deployment')
    
    args = parser.parse_args()
    
    # Initialize deployer
    deployer = CloudFormationDeployer(region=args.region, profile=args.profile)
    
    # Create deployment bucket
    account_id = deployer.session.client('sts').get_caller_identity()['Account']
    bucket_name = f"f1-platform-cf-templates-{args.environment}-{account_id}"
    deployer.create_deployment_bucket(bucket_name)
    
    # Define deployment configuration
    # Use fixed template for foundation (circular dependency fix), rest from showcase repo
    gitops_template_dir = Path("C:/scripts/f1-gitops/infrastructure/aws/cloudformation")
    showcase_template_dir = Path("C:/scripts/showcase-f1-pipeline/config/cloudformation")
    
    stacks = [
        {
            'name': f"{args.project_name}-foundation-{args.environment}",
            'template': '01-data-lake-foundation-fixed.yaml',
            'template_dir': gitops_template_dir,  # Use fixed version
            'parameters': {
                'Environment': args.environment,
                'ProjectName': args.project_name,
                'DataLakeBucketName': f'f1-data-lake'
            },
            'capabilities': ['CAPABILITY_NAMED_IAM'],
            'skip': args.skip_foundation
        },
        {
            'name': f"{args.project_name}-glue-etl-{args.environment}",
            'template': '02-glue-etl-jobs.yaml',
            'template_dir': showcase_template_dir,
            'parameters': {
                'Environment': args.environment,
                'ProjectName': args.project_name,
                # These will be populated from foundation stack outputs
                'DataLakeBucket': '',
                'GlueDatabase': '',
                'GlueServiceRoleArn': ''
            },
            'capabilities': [],
            'skip': args.skip_glue,
            'depends_on': f"{args.project_name}-foundation-{args.environment}"
        },
        {
            'name': f"{args.project_name}-athena-analytics-{args.environment}",
            'template': '03-athena-analytics.yaml',
            'template_dir': showcase_template_dir,
            'parameters': {
                'Environment': args.environment,
                'ProjectName': args.project_name,
                # These will be populated from foundation stack outputs
                'DataLakeBucket': '',
                'GlueDatabase': '',
                'AthenaWorkgroup': ''
            },
            'capabilities': [],
            'skip': args.skip_athena,
            'depends_on': f"{args.project_name}-foundation-{args.environment}"
        },
        {
            'name': f"{args.project_name}-access-{args.environment}",
            'template': '04-f1-data-platform-access-role.yaml',
            'template_dir': showcase_template_dir,
            'parameters': {
                'Environment': args.environment,
                'ProjectName': args.project_name,
                # These will be populated from foundation stack outputs
                'DataLakeBucket': '',
                'GlueDatabase': '',
                'AthenaResultsBucket': ''
            },
            'capabilities': ['CAPABILITY_NAMED_IAM'],
            'skip': False,
            'depends_on': f"{args.project_name}-foundation-{args.environment}"
        }
    ]
    
    print(f"🚀 Starting F1 Data Platform deployment to {args.environment} environment")
    print(f"📍 Region: {args.region}")
    print(f"🪣 Deployment bucket: {bucket_name}")
    print("=" * 70)
    
    # Deploy stacks in order
    deployed_stacks = {}
    
    for stack_config in stacks:
        if stack_config['skip']:
            print(f"⏭️  Skipping {stack_config['name']}")
            continue
            
        # Upload template (use stack-specific template_dir)
        template_path = stack_config['template_dir'] / stack_config['template']
        if not template_path.exists():
            print(f"❌ Template not found: {template_path}")
            continue
            
        template_url = deployer.upload_template(template_path, bucket_name, "templates/")
        
        # Handle dependencies
        if 'depends_on' in stack_config:
            dependency_stack = stack_config['depends_on']
            if dependency_stack in deployed_stacks:
                outputs = deployed_stacks[dependency_stack]
                # Map foundation outputs to dependent stack parameters
                if 'DataLakeBucket' in stack_config['parameters']:
                    stack_config['parameters']['DataLakeBucket'] = outputs.get('DataLakeBucketName', '')
                if 'GlueDatabase' in stack_config['parameters']:
                    stack_config['parameters']['GlueDatabase'] = outputs.get('GlueDatabaseName', '')
                if 'GlueServiceRoleArn' in stack_config['parameters']:
                    stack_config['parameters']['GlueServiceRoleArn'] = outputs.get('GlueServiceRoleArn', '')
                if 'AthenaWorkgroup' in stack_config['parameters']:
                    stack_config['parameters']['AthenaWorkgroup'] = outputs.get('AthenaWorkgroupName', '')
                if 'AthenaResultsBucket' in stack_config['parameters']:
                    stack_config['parameters']['AthenaResultsBucket'] = outputs.get('AthenaResultsBucketName', '')
            else:
                print(f"❌ Dependency {dependency_stack} not found, skipping {stack_config['name']}")
                continue
        
        # Deploy stack
        success = deployer.deploy_stack(
            stack_config['name'],
            template_url,
            stack_config['parameters'],
            stack_config['capabilities']
        )
        
        if success:
            # Store outputs for dependent stacks
            outputs = deployer.get_stack_outputs(stack_config['name'])
            deployed_stacks[stack_config['name']] = outputs
            print(f"📊 Stack outputs: {json.dumps(outputs, indent=2)}")
        else:
            print(f"❌ Failed to deploy {stack_config['name']}")
            break
        
        print("-" * 70)
    
    # Summary
    print("\n🎯 Deployment Summary:")
    for stack_name, outputs in deployed_stacks.items():
        print(f"✅ {stack_name}: Successfully deployed")
    
    if len(deployed_stacks) == len([s for s in stacks if not s['skip']]):
        print("\n🎉 F1 Data Platform deployment completed successfully!")
        print("\nNext steps:")
        print("1. Upload Glue ETL scripts to the data lake bucket")
        print("2. Run the Glue crawlers to discover data schemas")
        print("3. Execute the named queries in Athena for analytics")
        print("4. Set up your F1 data extraction pipeline")
    else:
        print("\n⚠️  Deployment completed with errors. Check the logs above.")


if __name__ == '__main__':
    main()