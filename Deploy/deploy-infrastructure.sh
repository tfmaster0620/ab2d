#!/bin/bash

set -e #Exit on first error
set -x #Be verbose

#
# Change to working directory
#

START_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "${START_DIR}"

#
# Parse options
#

for i in "$@"
do
case $i in
  --environment=*)
  ENVIRONMENT="${i#*=}"
  CMS_ENV=$(echo $ENVIRONMENT | tr '[:upper:]' '[:lower:]')
  shift # past argument=value
  ;;
  --ecr-repo-environment=*)
  ECR_REPO_ENVIRONMENT="${i#*=}"
  CMS_ECR_REPO_ENV=$(echo $ECR_REPO_ENVIRONMENT | tr '[:upper:]' '[:lower:]')
  shift # past argument=value
  ;;
  --region=*)
  REGION="${i#*=}"
  shift # past argument=value
  ;;
  --ssh-username=*)
  SSH_USERNAME="${i#*=}"
  shift # past argument=value
  ;;
  --owner=*)
  OWNER="${i#*=}"
  shift # past argument=value
  ;;
  --ec2_instance_type_api=*)
  EC2_INSTANCE_TYPE_API="${i#*=}"
  shift # past argument=value
  ;;
  --ec2_instance_type_worker=*)
  EC2_INSTANCE_TYPE_WORKER="${i#*=}"
  shift # past argument=value
  ;;
  --ec2_instance_type_other=*)
  EC2_INSTANCE_TYPE_OTHER="${i#*=}"
  EC2_INSTANCE_TYPE_CONTROLLER="${EC2_INSTANCE_TYPE_OTHER}"
  EC2_INSTANCE_TYPE_PACKER="${EC2_INSTANCE_TYPE_OTHER}"
  shift # past argument=value
  ;;
  --ec2_desired_instance_count_api=*)
  EC2_DESIRED_INSTANCE_COUNT_API="${i#*=}"
  shift # past argument=value
  ;;
  --ec2_minimum_instance_count_api=*)
  EC2_MINIMUM_INSTANCE_COUNT_API="${i#*=}"
  shift # past argument=value
  ;;
  --ec2_maximum_instance_count_api=*)
  EC2_MAXIMUM_INSTANCE_COUNT_API="${i#*=}"
  shift # past argument=value
  ;;
  --ec2_desired_instance_count_worker=*)
  EC2_DESIRED_INSTANCE_COUNT_WORKER="${i#*=}"
  shift # past argument=value
  ;;
  --ec2_minimum_instance_count_worker=*)
  EC2_MINIMUM_INSTANCE_COUNT_WORKER="${i#*=}"
  shift # past argument=value
  ;;
  --ec2_maximum_instance_count_worker=*)
  EC2_MAXIMUM_INSTANCE_COUNT_WORKER="${i#*=}"
  shift # past argument=value
  ;;
  --database-secret-datetime=*)
  DATABASE_SECRET_DATETIME=$(echo ${i#*=})
  shift # past argument=value
  ;;  
  --debug-level=*)
  DEBUG_LEVEL=$(echo ${i#*=} | tr '[:lower:]' '[:upper:]')
  shift # past argument=value
  ;;
  --build-new-images)
  BUILD_NEW_IMAGES="true"
  shift # past argument=value
  ;;
  --use-existing-images)
  USE_EXISTING_IMAGES="true"
  shift # past argument=value
  ;;
  --internet-facing=*)
  INTERNET_FACING=${i#*=}
  shift # past argument=value
  ;;
  --auto-approve)
  AUTOAPPROVE="true"
  shift # past argument=value
  ;;
esac
done

#
# Check vars are not empty before proceeding
#

# Ensure that one of the following parameters is passed
# --build-new-images
# --use-existing-images

if [ -z "${BUILD_NEW_IMAGES}" ] && [ -z "${USE_EXISTING_IMAGES}" ]; then
  echo ""
  echo "**********************************************************************"
  echo "ERROR: pass one and only one of the following parameters:"
  echo "--build-new-images"
  echo "--use-existing-images"
  echo "**********************************************************************"
  PARAMETER_ERROR="YES"
fi

# Ensure that one and only one of the following is passed
# --build-new-images
# --use-existing-images

if [ -n "${BUILD_NEW_IMAGES}" ] && [ -n "${USE_EXISTING_IMAGES}" ]; then
  echo ""
  echo "**********************************************************************"
  echo "ERROR: pass only one of the following parameters:"
  echo "--build-new-images"
  echo "--use-existing-images"
  echo "**********************************************************************"
  echo ""
  PARAMETER_ERROR="YES"
fi

#
# Use existing images temporarily disabled (until multiple labels per ECR image is implemented)
#

if [ -n "${USE_EXISTING_IMAGES}" ]; then
  echo ""
  echo "**********************************************************************"
  echo "The '--use-existing-images' option is temporarily disabled."
  echo "Please use the '--build-new-images' option instead."
  echo "**********************************************************************"
  echo ""
  exit 1
fi

# Check that the other vars are not empty before proceeding

if  [ "${PARAMETER_ERROR}" == "YES" ] \
    || [ -z "${ENVIRONMENT}" ] \
    || [ -z "${ECR_REPO_ENVIRONMENT}" ] \
    || [ -z "${REGION}" ] \
    || [ -z "${SSH_USERNAME}" ] \
    || [ -z "${OWNER}" ] \
    || [ -z "${EC2_INSTANCE_TYPE_API}" ] \
    || [ -z "${EC2_INSTANCE_TYPE_WORKER}" ] \
    || [ -z "${EC2_INSTANCE_TYPE_OTHER}" ] \
    || [ -z "${EC2_DESIRED_INSTANCE_COUNT_API}" ] \
    || [ -z "${EC2_MINIMUM_INSTANCE_COUNT_API}" ] \
    || [ -z "${EC2_MAXIMUM_INSTANCE_COUNT_API}" ] \
    || [ -z "${EC2_DESIRED_INSTANCE_COUNT_WORKER}" ] \
    || [ -z "${EC2_MINIMUM_INSTANCE_COUNT_WORKER}" ] \
    || [ -z "${EC2_MAXIMUM_INSTANCE_COUNT_WORKER}" ] \
    || [ -z "${INTERNET_FACING}" ] \
    || [ -z "${DATABASE_SECRET_DATETIME}" ]; then
  echo ""
  echo "**********************************************************************"
  echo "ERROR: Try running the script like this example:"
  echo "./deploy-infrastructure.sh \\"
  echo "  --environment=ab2d-dev \\"
  echo "  --ecr-repo-environment=-ab2d-mgmt-east-dev \\"
  echo "  --region=us-east-1 \\"
  echo "  --vpc-id=vpc-0c6413ec40c5fdac3 \\"
  echo "  --ssh-username=ec2-user \\"
  echo "  --owner=842420567215 \\"
  echo "  --ec2_instance_type_api=m5.xlarge \\"
  echo "  --ec2_instance_type_worker=m5.xlarge \\"
  echo "  --ec2_instance_type_other=m5.xlarge \\"
  echo "  --ec2_desired_instance_count_api=1 \\"
  echo "  --ec2_minimum_instance_count_api=1 \\"
  echo "  --ec2_maximum_instance_count_api=1 \\"
  echo "  --ec2_desired_instance_count_worker=1 \\"
  echo "  --ec2_minimum_instance_count_worker=1 \\"
  echo "  --ec2_maximum_instance_count_worker=1 \\"
  echo "  --database-secret-datetime=2020-01-02-09-15-01 \\"
  echo "  --build-new-images \\"
  echo "  --internet-facing=false \\"
  echo "  --auto-approve"  
  echo "**********************************************************************"
  echo ""
  exit 1
fi

# Set whether load balancer is internal based on "internet-facing" parameter

if [ "$INTERNET_FACING" == "false" ]; then
  ALB_INTERNAL=true
elif [ "$INTERNET_FACING" == "true" ]; then
  ALB_INTERNAL=false
else
  echo "**********************************************************************"
  echo "ERROR: the '--internet-facing' parameter must be true or false"
  echo "**********************************************************************"
  exit 1
fi

#
# Set AWS account numbers
#

CMS_ECR_REPO_ENV_AWS_ACCOUNT_NUMBER=653916833532

if [ "${CMS_ENV}" == "ab2d-dev" ]; then
  CMS_ENV_AWS_ACCOUNT_NUMBER=349849222861
elif [ "${CMS_ENV}" == "ab2d-sbx-sandbox" ]; then
  CMS_ENV_AWS_ACCOUNT_NUMBER=777200079629
elif [ "${CMS_ENV}" == "ab2d-east-impl" ]; then
  CMS_ENV_AWS_ACCOUNT_NUMBER=330810004472
elif [ "${CMS_ENV}" == "ab2d-east-prod" ]; then
  CMS_ENV_AWS_ACCOUNT_NUMBER=595094747606
else
  echo "ERROR: 'CMS_ENV' environment is unknown."
  exit 1  
fi

#
# Define functions
#

get_temporary_aws_credentials ()
{
  # Set AWS account number

  AWS_ACCOUNT_NUMBER="$1"

  # Verify that CloudTamer user name and password environment variables are set

  if [ -z $CLOUDTAMER_USER_NAME ] || [ -z $CLOUDTAMER_PASSWORD ]; then
    echo ""
    echo "----------------------------"
    echo "Enter CloudTamer credentials"
    echo "----------------------------"
  fi

  if [ -z $CLOUDTAMER_USER_NAME ]; then
    echo ""
    echo "Enter your CloudTamer user name (EUA ID):"
    read CLOUDTAMER_USER_NAME
  fi

  if [ -z $CLOUDTAMER_PASSWORD ]; then
    echo ""
    echo "Enter your CloudTamer password:"
    read CLOUDTAMER_PASSWORD
  fi

  # Get bearer token

  echo ""
  echo "--------------------"
  echo "Getting bearer token"
  echo "--------------------"
  echo ""

  BEARER_TOKEN=$(curl --location --request POST 'https://cloudtamer.cms.gov/api/v2/token' \
    --header 'Accept: application/json' \
    --header 'Accept-Language: en-US,en;q=0.5' \
    --header 'Content-Type: application/json' \
    --data-raw "{\"username\":\"${CLOUDTAMER_USER_NAME}\",\"password\":\"${CLOUDTAMER_PASSWORD}\",\"idms\":{\"id\":2}}" \
    | jq --raw-output ".data.access.token")

  # Get json output for temporary AWS credentials

  echo ""
  echo "-----------------------------"
  echo "Getting temporary credentials"
  echo "-----------------------------"
  echo ""

  JSON_OUTPUT=$(curl --location --request POST 'https://cloudtamer.cms.gov/api/v3/temporary-credentials' \
    --header 'Accept: application/json' \
    --header 'Accept-Language: en-US,en;q=0.5' \
    --header 'Content-Type: application/json' \
    --header "Authorization: Bearer ${BEARER_TOKEN}" \
    --header 'Content-Type: application/json' \
    --data-raw "{\"account_number\":\"${AWS_ACCOUNT_NUMBER}\",\"iam_role_name\":\"ab2d-spe-developer\"}" \
    | jq --raw-output ".data")

  # Set default AWS region

  export AWS_DEFAULT_REGION=us-east-1

  # Get temporary AWS credentials

  export AWS_ACCESS_KEY_ID=$(echo $JSON_OUTPUT | jq --raw-output ".access_key")
  export AWS_SECRET_ACCESS_KEY=$(echo $JSON_OUTPUT | jq --raw-output ".secret_access_key")

  # Get AWS session token (required for temporary credentials)

  export AWS_SESSION_TOKEN=$(echo $JSON_OUTPUT | jq --raw-output ".session_token")

  # Verify AWS credentials

  if [ -z "${AWS_ACCESS_KEY_ID}" ] \
      || [ -z "${AWS_SECRET_ACCESS_KEY}" ] \
      || [ -z "${AWS_SESSION_TOKEN}" ]; then
    echo "**********************************************************************"
    echo "ERROR: AWS credentials do not exist for the ${CMS_ENV} AWS account"
    echo "**********************************************************************"
    echo ""
    exit 1
  fi
}

#
# Set default values
#

export DEBUG_LEVEL="WARN"

#
# Set AWS target environment
#

get_temporary_aws_credentials "${CMS_ENV_AWS_ACCOUNT_NUMBER}"

#########################
# BEGIN #1 (LSH 2020-04-27)
#########################

# #
# # Verify that VPC ID exists
# #

# VPC_EXISTS=$(aws --region "${REGION}" ec2 describe-vpcs \
#   --query "Vpcs[?VpcId=='$VPC_ID'].VpcId" \
#   --output text)

# if [ -z "${VPC_EXISTS}" ]; then
#   echo "*********************************************************"
#   echo "ERROR: VPC ID does not exist for the target AWS profile."
#   echo "*********************************************************"
#   exit 1
# fi

#
# Get VPC ID
#

VPC_ID=$(aws --region "${REGION}" ec2 describe-vpcs \
  --filters "Name=tag:Name,Values=${CMS_ENV}" \
  --query "Vpcs[*].VpcId" \
  --output text)

#########################
# END #1 (LSH 2020-04-27)
#########################

# #
# # Get KMS key id for target environment (if exists)
# #

# KMS_KEY_ID=$(aws --region "${REGION}" kms list-aliases \
#   --query="Aliases[?AliasName=='alias/ab2d-kms'].TargetKeyId" \
#   --output text)

# if [ -n "${KMS_KEY_ID}" ]; then
#   KMS_KEY_STATE=$(aws --region "${REGION}" kms describe-key \
#     --key-id alias/ab2d-kms \
#     --query "KeyMetadata.KeyState" \
#     --output text)
# fi

# #
# # Deploy or enable KMS for target environment
# #

# # If target environment KMS key exists, enable it; otherwise, create it

# if [ -n "$KMS_KEY_ID" ]; then
#   echo "Enabling KMS key..."
#   cd "${START_DIR}"
#   cd python3
#   ./enable-kms-key.py $KMS_KEY_ID
# else
#   echo "Deploying KMS..."
#   cd "${START_DIR}"
#   cd terraform/environments/$CMS_SHARED_ENV
#   terraform apply \
#     --target module.kms --auto-approve
# fi

#
# Configure terraform
#

# Reset logging

echo "Setting terraform debug level to $DEBUG_LEVEL..."
export TF_LOG=$DEBUG_LEVEL
export TF_LOG_PATH=/var/log/terraform/tf.log
rm -f /var/log/terraform/tf.log

# Initialize and validate terraform for the target environment

echo "***************************************************************"
echo "Initialize and validate terraform for the target environment..."
echo "***************************************************************"

cd "${START_DIR}"
cd terraform/environments/$CMS_ENV

rm -f *.tfvars

terraform init \
  -backend-config="bucket=${CMS_ENV}-automation" \
  -backend-config="key=${CMS_ENV}/terraform/terraform.tfstate" \
  -backend-config="region=${REGION}" \
  -backend-config="encrypt=true"

terraform validate

#
# Deploy db
#

echo "Create or update database instance..."

# Change to the "python3" directory

cd "${START_DIR}"
cd python3

# Get database secrets

DATABASE_USER=$(./get-database-secret.py $CMS_ENV database_user $DATABASE_SECRET_DATETIME)
DATABASE_PASSWORD=$(./get-database-secret.py $CMS_ENV database_password $DATABASE_SECRET_DATETIME)
DATABASE_NAME=$(./get-database-secret.py $CMS_ENV database_name $DATABASE_SECRET_DATETIME)

# Verify database credentials

if [ -z "${DATABASE_USER}" ] \
    || [ -z "${DATABASE_PASSWORD}" ] \
    || [ -z "${DATABASE_NAME}" ]; then
  echo "***********************************************************************"
  echo "ERROR: Database credentials do not exist for the ${CMS_ENV} AWS account"
  echo "***********************************************************************"
  echo ""
  exit 1
fi

# Get VPN privte ip address range

VPN_PRIVATE_IP_ADDRESS_CIDR_RANGE=$(./get-database-secret.py $CMS_ENV vpn_private_ip_address_cidr_range $DATABASE_SECRET_DATETIME)

# Change to the target directory

cd "${START_DIR}"
cd terraform/environments/$CMS_ENV

#
# Get network information
#

# Get first public subnet id

SUBNET_PUBLIC_1_ID=$(aws --region "${REGION}" ec2 describe-subnets \
  --filters "Name=tag:Name,Values=${CMS_ENV}-public-a" \
  --query "Subnets[*].SubnetId" \
  --output text)

if [ -z "${SUBNET_PUBLIC_1_ID}" ]; then
  echo "ERROR: public subnet #1 not found..."
  exit 1
fi

# Get second public subnet id

SUBNET_PUBLIC_2_ID=$(aws --region "${REGION}" ec2 describe-subnets \
  --filters "Name=tag:Name,Values=${CMS_ENV}-public-b" \
  --query "Subnets[*].SubnetId" \
  --output text)

if [ -z "${SUBNET_PUBLIC_2_ID}" ]; then
  echo "ERROR: public subnet #2 not found..."
  exit 1
fi

# Get first private subnet id

SUBNET_PRIVATE_1_ID=$(aws --region "${REGION}" ec2 describe-subnets \
  --filters "Name=tag:Name,Values=${CMS_ENV}-private-a" \
  --query "Subnets[*].SubnetId" \
  --output text)

if [ -z "${SUBNET_PRIVATE_1_ID}" ]; then
  echo "ERROR: private subnet #1 not found..."
  exit 1
fi

# Get second private subnet id

SUBNET_PRIVATE_2_ID=$(aws --region "${REGION}" ec2 describe-subnets \
  --filters "Name=tag:Name,Values=${CMS_ENV}-private-b" \
  --query "Subnets[*].SubnetId" \
  --output text)

if [ -z "${SUBNET_PRIVATE_2_ID}" ]; then
  echo "ERROR: private subnet #2 not found..."
  exit 1
fi

#
# Create "auto.tfvars" file
#

# Get AMI ID

AMI_ID=$(aws --region "${REGION}" ec2 describe-images \
  --owners self \
  --filters "Name=tag:Name,Values=ab2d-ami" \
  --query "Images[*].[ImageId]" \
  --output text)

# Get deployer IP address

DEPLOYER_IP_ADDRESS=$(curl ipinfo.io/ip)

# Get DB endpoint

DB_ENDPOINT=$(aws --region "${REGION}" rds describe-db-instances \
  --query="DBInstances[?DBInstanceIdentifier=='ab2d'].Endpoint.Address" \
  --output=text)

# Save VPC_ID

echo 'vpc_id = "'$VPC_ID'"' \
  > $CMS_ENV.auto.tfvars

# Save private_subnet_ids

if [ -z "${DB_ENDPOINT}" ]; then
  echo 'private_subnet_ids = ["'$SUBNET_PRIVATE_1_ID'","'$SUBNET_PRIVATE_2_ID'"]' \
    >> $CMS_ENV.auto.tfvars
else
  PRIVATE_SUBNETS_OUTPUT=$(aws --region "${REGION}" ec2 describe-subnets \
    --filters "Name=tag:Name,Values=*-private-*" \
    --query "Subnets[*].SubnetId" \
    --output text)

  IFS=$'\t' read -ra subnet_array <<< "$PRIVATE_SUBNETS_OUTPUT"
  unset IFS

  COUNT=1
  for i in "${subnet_array[@]}"
  do
    if [ $COUNT == 1 ]; then
      PRIVATE_SUBNETS=\"$i\"
    else
      PRIVATE_SUBNETS=$PRIVATE_SUBNETS,\"$i\"
    fi
    COUNT=$COUNT+1
  done

  echo "private_subnet_ids = [${PRIVATE_SUBNETS}]" \
    >> $CMS_ENV.auto.tfvars
fi

# Set remaining vars

echo 'deployment_controller_subnet_ids = ["'$SUBNET_PUBLIC_1_ID'","'$SUBNET_PUBLIC_2_ID'"]' \
  >> $CMS_ENV.auto.tfvars

echo 'ec2_instance_type = "'$EC2_INSTANCE_TYPE_CONTROLLER'"' \
  >> $CMS_ENV.auto.tfvars

echo 'linux_user = "'$SSH_USERNAME'"' \
  >> $CMS_ENV.auto.tfvars

echo 'ami_id = "'$AMI_ID'"' \
  >> $CMS_ENV.auto.tfvars

echo 'deployer_ip_address = "'$DEPLOYER_IP_ADDRESS'"' \
  >> $CMS_ENV.auto.tfvars
echo 'vpn_private_ip_address_cidr_range = "'$VPN_PRIVATE_IP_ADDRESS_CIDR_RANGE'"' \
  >> $CMS_ENV.auto.tfvars

# Create DB instance (if doesn't exist)

terraform apply \
  --var "db_username=${DATABASE_USER}" \
  --var "db_password=${DATABASE_PASSWORD}" \
  --var "db_name=${DATABASE_NAME}" \
  --target module.db --auto-approve

DB_ENDPOINT=$(aws --region "${REGION}" rds describe-db-instances \
  --query="DBInstances[?DBInstanceIdentifier=='ab2d'].Endpoint.Address" \
  --output=text)

cd "${START_DIR}"
cd terraform/environments/$CMS_ENV
rm -f generated/.pgpass

# Generate ".pgpass" file

cd "${START_DIR}"
cd terraform/environments/$CMS_ENV
mkdir -p generated

# Add default database

echo "${DB_ENDPOINT}:5432:postgres:${DATABASE_USER}:${DATABASE_PASSWORD}" > generated/.pgpass

#
# Deploy controller
#

echo "Create or update controller..."

terraform apply \
  --var "env=${CMS_ENV}" \
  --var "db_username=${DATABASE_USER}" \
  --var "db_password=${DATABASE_PASSWORD}" \
  --var "db_name=${DATABASE_NAME}" \
  --var "deployer_ip_address=${DEPLOYER_IP_ADDRESS}" \
  --var "vpn_private_ip_address_cidr_range=${VPN_PRIVATE_IP_ADDRESS_CIDR_RANGE}" \
  --target module.controller \
  --auto-approve

#
# Deploy EFS
#

# Get files system id (if exists)

EFS_FS_ID=$(aws --region "${REGION}" efs describe-file-systems \
  --query="FileSystems[?CreationToken=='${CMS_ENV}-efs'].FileSystemId" \
  --output=text)

# Create file system (if doesn't exist)

if [ -z "${EFS_FS_ID}" ]; then
  echo "Creating EFS..."
  terraform apply \
    --var "env=${CMS_ENV}" \
    --target module.efs \
    --auto-approve
fi

#
# Create database
#

cd "${START_DIR}"

# Get the private ip address of the controller

CONTROLLER_PRIVATE_IP=$(aws --region "${REGION}" ec2 describe-instances \
  --filters "Name=tag:Name,Values=ab2d-deployment-controller" \
  --query="Reservations[*].Instances[?State.Name == 'running'].PrivateIpAddress" \
  --output text)

# Determine if the database for the environment exists

DB_NAME_IF_EXISTS=$(ssh -tt -i "~/.ssh/${CMS_ENV}.pem" \
  "${SSH_USERNAME}@${CONTROLLER_PRIVATE_IP}" \
  "psql -t --host "${DB_ENDPOINT}" --username "${DATABASE_USER}" --dbname postgres --command='SELECT datname FROM pg_catalog.pg_database'" \
  | grep "${DATABASE_NAME}" \
  | sort \
  | head -n 1 \
  | xargs \
  | tr -d '\r')

# Create the database for the environment if it doesn't exist

if [ -n "${CONTROLLER_PRIVATE_IP}" ] && [ -n "${DB_ENDPOINT}" ] && [ "${DB_NAME_IF_EXISTS}" != "${DATABASE_NAME}" ]; then
  echo "Creating database..."
  ssh -tt -i "~/.ssh/${CMS_ENV}.pem" \
    "${SSH_USERNAME}@${CONTROLLER_PRIVATE_IP}" \
    "createdb ${DATABASE_NAME} --host ${DB_ENDPOINT} --username ${DATABASE_USER}"
fi

# Change to the "python3" directory

cd "${START_DIR}"
cd python3

# Create or get database host secret

DATABASE_HOST=$(./get-database-secret.py $CMS_ENV database_host $DATABASE_SECRET_DATETIME)
if [ -z "${DATABASE_HOST}" ]; then
  aws secretsmanager create-secret \
    --name "ab2d/${CMS_ENV}/module/db/database_host/${DATABASE_SECRET_DATETIME}" \
    --secret-string "${DB_ENDPOINT}"
  DATABASE_HOST=$(./get-database-secret.py $CMS_ENV database_host $DATABASE_SECRET_DATETIME)
fi

# Create or get database port secret

DB_PORT=$(aws --region "${REGION}" rds describe-db-instances \
  --query="DBInstances[?DBInstanceIdentifier=='ab2d'].Endpoint.Port" \
  --output=text)

DATABASE_PORT=$(./get-database-secret.py $CMS_ENV database_port $DATABASE_SECRET_DATETIME)
if [ -z "${DATABASE_PORT}" ]; then
  aws secretsmanager create-secret \
    --name "ab2d/${CMS_ENV}/module/db/database_port/${DATABASE_SECRET_DATETIME}" \
    --secret-string "${DB_PORT}"
  DATABASE_PORT=$(./get-database-secret.py $CMS_ENV database_port $DATABASE_SECRET_DATETIME)
fi

# Get database secret manager ARNs

DATABASE_HOST_SECRET_ARN=$(aws --region "${REGION}" secretsmanager describe-secret \
  --secret-id "ab2d/${CMS_ENV}/module/db/database_host/${DATABASE_SECRET_DATETIME}" \
  --query "ARN" \
  --output text)

DATABASE_PORT_SECRET_ARN=$(aws --region "${REGION}" secretsmanager describe-secret \
  --secret-id "ab2d/${CMS_ENV}/module/db/database_port/${DATABASE_SECRET_DATETIME}" \
  --query "ARN" \
  --output text)

DATABASE_USER_SECRET_ARN=$(aws --region "${REGION}" secretsmanager describe-secret \
  --secret-id "ab2d/${CMS_ENV}/module/db/database_user/${DATABASE_SECRET_DATETIME}" \
  --query "ARN" \
  --output text)

DATABASE_PASSWORD_SECRET_ARN=$(aws --region "${REGION}" secretsmanager describe-secret \
  --secret-id "ab2d/${CMS_ENV}/module/db/database_password/${DATABASE_SECRET_DATETIME}" \
  --query "ARN" \
  --output text)

DATABASE_NAME_SECRET_ARN=$(aws --region "${REGION}" secretsmanager describe-secret \
  --secret-id "ab2d/${CMS_ENV}/module/db/database_name/${DATABASE_SECRET_DATETIME}" \
  --query "ARN" \
  --output text)