#!/bin/bash

# Webhook Lambda Deployment Script
set -e

ENVIRONMENT=${1:-dev}
AWS_REGION=${2:-ap-south-1}
UPDATE_CODE_ONLY=${3:-false}   # pass "true" to only update Lambda code

# Version identifier (timestamp)
VERSION=$(date +%Y%m%d%H%M%S)

echo "🚀 Deploying Webhook Lambda System to $ENVIRONMENT environment in $AWS_REGION..."
echo "📌 Deployment version: $VERSION"

# --- Auto-detect VPC and Subnets if not provided ---
if [ -z "$VPC_ID" ]; then
  echo "Detecting default VPC..."
  VPC_ID=$(aws ec2 describe-vpcs \
    --region $AWS_REGION \
    --filters "Name=isDefault,Values=true" \
    --query "Vpcs[0].VpcId" \
    --output text)
fi

if [ -z "$SUBNET_IDS" ]; then
  echo "Detecting available subnets in VPC $VPC_ID..."
  SUBNET_IDS=$(aws ec2 describe-subnets \
    --region $AWS_REGION \
    --filters "Name=vpc-id,Values=$VPC_ID" \
    --query "Subnets[*].SubnetId" \
    --output text | tr '\t' ',')
fi

# --- Fetch Database URL ---
if [ -z "$DATABASE_URL" ]; then
  echo "Fetching DATABASE_URL from SSM or Secrets Manager..."

  # Try SSM Parameter Store
  DATABASE_URL=$(aws secretsmanager get-secret-value --region ap-south-1 --secret-id "dev/application-secret-keys" --query SecretString --output text 2>/dev/null | sed -n 's/.*"database_url":"\([^"]*\)".*/\1/p')

  if [ -z "$DATABASE_URL" ] || [ "$DATABASE_URL" == "None" ]; then
    echo "❌ DATABASE_URL not found in environment, SSM, or Secrets Manager."
    exit 1
  fi
fi

if [ -z "$DATABASE_USERNAME" ]; then
  echo "Fetching DATABASE_USERNAME from SSM or Secrets Manager..."

  # Try SSM Parameter Store
  DATABASE_USERNAME=$(aws secretsmanager get-secret-value --region ap-south-1 --secret-id "dev/application-secret-keys" --query SecretString --output text 2>/dev/null | sed -n 's/.*"database_username":"\([^"]*\)".*/\1/p')

  if [ -z "$DATABASE_USERNAME" ] || [ "$DATABASE_USERNAME" == "None" ]; then
    echo "❌ DATABASE_USERNAME not found in environment, SSM, or Secrets Manager."
    exit 1
  fi
fi

if [ -z "$DATABASE_PASSWORD" ]; then
  echo "Fetching DATABASE_PASSWORD from SSM or Secrets Manager..."

  # Try SSM Parameter Store
  DATABASE_PASSWORD=$(aws secretsmanager get-secret-value --region ap-south-1 --secret-id "dev/application-secret-keys" --query SecretString --output text 2>/dev/null | sed -n 's/.*"database_password":"\([^"]*\)".*/\1/p')

  if [ -z "$DATABASE_PASSWORD" ] || [ "$DATABASE_PASSWORD" == "None" ]; then
    echo "❌ DATABASE_PASSWORD not found in environment, SSM, or Secrets Manager."
    exit 1
  fi
fi

echo "✅ Using VPC: $VPC_ID"
echo "✅ Using Subnets: $SUBNET_IDS"
echo "✅ Using Database URL (fetched securely)"

# --- Build the project ---
echo "🛠 Building project..."
./gradlew clean build publisherJar deliveryJar

# --- Upload JARs to S3 (versioned path) ---
echo "☁️ Uploading JARs to S3 (version $VERSION)..."
aws s3 cp build/libs/webhook-publisher.jar s3://pi-checkout-$ENVIRONMENT-s3-$AWS_REGION-webhook/$VERSION/webhook-publisher.jar --region $AWS_REGION
aws s3 cp build/libs/webhook-delivery.jar s3://pi-checkout-$ENVIRONMENT-s3-$AWS_REGION-webhook/$VERSION/webhook-delivery.jar --region $AWS_REGION

# --- If only updating code, skip infra ---
if [ "$UPDATE_CODE_ONLY" == "true" ]; then
  echo "🔄 Updating Lambda function code only..."

  aws lambda update-function-code \
    --region $AWS_REGION \
    --function-name pi-checkout-$ENVIRONMENT-lmd-$AWS_REGION-webhookpublisher \
    --s3-bucket pi-checkout-$ENVIRONMENT-s3-$AWS_REGION-webhook \
    --s3-key $VERSION/webhook-publisher.jar

  aws lambda update-function-code \
    --region $AWS_REGION \
    --function-name pi-checkout-$ENVIRONMENT-lmd-$AWS_REGION-webhookdelivery \
    --s3-bucket pi-checkout-$ENVIRONMENT-s3-$AWS_REGION-webhook \
    --s3-key $VERSION/webhook-delivery.jar

  echo "✅ Lambda code updated successfully!"
  exit 0
fi

# --- Deploy CloudFormation stack ---
echo "📦 Deploying CloudFormation stack..."
aws cloudformation deploy \
  --template-file deployment/cloudformation-template.yaml \
  --stack-name pi-checkout-$ENVIRONMENT-lmd-$AWS_REGION-webhook \
  --parameter-overrides \
    Environment=$ENVIRONMENT \
    VpcId=$VPC_ID \
    SubnetIds=$SUBNET_IDS \
    DatabaseUrl=$DATABASE_URL \
    DatabaseUsername=$DATABASE_USERNAME \
    DatabasePassword=$DATABASE_PASSWORD \
    ArtifactVersion=$VERSION \
    Region=$AWS_REGION \
  --capabilities CAPABILITY_NAMED_IAM \
  --region $AWS_REGION

echo "🎉 Deployment completed successfully! (Version: $VERSION)"
