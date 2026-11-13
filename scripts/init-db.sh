#!/bin/sh
set -eu
endpoint="${DYNAMODB_ENDPOINT:-http://dynamodb:8000}"
attempt=0
until aws dynamodb list-tables --endpoint-url "$endpoint" >/dev/null 2>&1; do
 attempt=$((attempt + 1))
 [ "$attempt" -lt 30 ] || { echo 'DynamoDB did not start' >&2; exit 1; }
 sleep 2
done
if aws dynamodb describe-table --table-name fleetops --endpoint-url "$endpoint" >/dev/null 2>&1; then exit 0; fi
aws dynamodb create-table --endpoint-url "$endpoint" --table-name fleetops --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=sk,AttributeType=S --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE --billing-mode PAY_PER_REQUEST
