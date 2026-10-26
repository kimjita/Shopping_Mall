#!/bin/bash
# LocalStack이 준비되면 이미지 버킷을 만든다. 이미 있으면 정책·CORS만 다시 건다.
set -euo pipefail

BUCKET=dewy-images
REGION=ap-northeast-2

awslocal s3api head-bucket --bucket "$BUCKET" 2>/dev/null \
  || awslocal s3 mb "s3://$BUCKET" --region "$REGION"

awslocal s3api put-bucket-policy --bucket "$BUCKET" --policy "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [{
    \"Effect\": \"Allow\",
    \"Principal\": \"*\",
    \"Action\": \"s3:GetObject\",
    \"Resource\": \"arn:aws:s3:::$BUCKET/*\"
  }]
}"

awslocal s3api put-bucket-cors --bucket "$BUCKET" --cors-configuration '{
  "CORSRules": [{
    "AllowedOrigins": ["http://localhost:5173"],
    "AllowedMethods": ["PUT", "GET"],
    "AllowedHeaders": ["Content-Type"]
  }]
}'

echo "S3 bucket $BUCKET ready"
