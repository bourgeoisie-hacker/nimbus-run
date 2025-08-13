#!/bin/bash

# Requirements:
# - yq (https://github.com/mikefarah/yq) must be installed (v4+)
# - Usage: ./check-compute-type.sh config.yaml

YAML_FILE="${1:-$NIMBUS_RUN_CONFIGURATION_FILE}"
echo "yaml location $YAML_FILE"
if [ -z "$YAML_FILE" ]; then
  echo "Usage: $0 <yaml-file>"
  exit 1
fi

if ! command -v yq >/dev/null 2>&1; then
  echo "Error: 'yq' is required but not installed. Install it from https://github.com/mikefarah/yq"
  exit 1
fi

# Extract computeType value
COMPUTE_TYPE=$(yq eval '.computeType' "$YAML_FILE")

# Check if it equals 'aws'
# Match computeType

case "$COMPUTE_TYPE" in
  aws)
    echo "✅ computeType is AWS"
    TYPE=aws
    ;;
  gcp)
    echo "✅ computeType is GCP"
    TYPE=gcp
    ;;
  azure)
    echo "✅ computeType is Azure"
    TYPE=azure
    ;;
  *)
    echo "❌ Unknown computeType: '$COMPUTE_TYPE'"
    exit 1
    ;;
esac

exec java ${JAVA_OPTS} -jar /jars/autoscaler-${TYPE}.jar
