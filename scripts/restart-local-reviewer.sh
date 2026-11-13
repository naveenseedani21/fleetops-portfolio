#!/usr/bin/env bash
# Host-only handoff. Never runs implicitly from tests, CI, or another script.
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"
if [[ $# != 2 || ! "$1" =~ ^[0-9]+$ || ! "$2" =~ ^[0-9]+$ ]]; then
  echo 'Usage: bash scripts/restart-local-reviewer.sh <verified-8080-pid> <verified-18081-pid>' >&2
  exit 2
fi
jar_file="$repo_dir/backend/target/fleetops-reviewer.jar"
[[ -f "$jar_file" && -f backend/target/fleetops-reviewer.sha256 ]] || { echo 'Verified reviewer build is missing; do not stop any process.' >&2; exit 1; }
shasum -a 256 -c backend/target/fleetops-reviewer.sha256
java_bin="${JAVA_HOME:-/private/tmp/fleetops-runtime/jdk-21.0.13+9/Contents/Home}/bin/java"
[[ -x "$java_bin" ]] || { echo 'Set JAVA_HOME to the verified Java 21 installation.' >&2; exit 1; }
java_version="$("$java_bin" -version 2>&1)"
[[ "$java_version" == *'version "21.'* ]] || { echo 'Java 21 is required; stopping nothing.' >&2; exit 1; }
verify_owner() {
  local expected_pid="$1" port="$2" expected_jar="$3" owner command
  owner="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t)"
  [[ "$owner" == "$expected_pid" ]] || { echo "Port $port no longer belongs only to PID $expected_pid; stopping nothing." >&2; exit 1; }
  command="$(ps -p "$expected_pid" -o command=)"
  case "$command" in *java*"-jar $expected_jar"*) ;; *) echo "PID $expected_pid is not the expected FleetOps jar; stopping nothing." >&2; exit 1;; esac
}
# Validate both identities before sending either signal. No force-kill fallback.
verify_owner "$1" 8080 /private/tmp/fleetops-checkout/backend/target/fleetops-0.1.0.jar
verify_owner "$2" 18081 /private/tmp/fleetops-audit/backend/target/fleetops-0.1.0.jar
kill -TERM "$1" "$2"
for attempt in $(seq 1 30); do
  if ! lsof -nP -iTCP:8080 -iTCP:18081 -sTCP:LISTEN -t >/dev/null; then
    echo 'Starting the verified FleetOps backend on loopback port 8080; existing database and identity services are unchanged.'
    exec env AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_REGION=us-east-1 \
      DYNAMODB_ENDPOINT=http://localhost:8000 DYNAMODB_TABLE=fleetops \
      OIDC_ISSUER=http://localhost:8180/realms/fleetops OIDC_JWKS=http://localhost:8180/realms/fleetops/protocol/openid-connect/certs \
      SERVER_ADDRESS=127.0.0.1 SERVER_PORT=8080 "$java_bin" -jar "$jar_file"
  fi
  sleep 0.5
done
echo 'A listener remains. No force kill or additional process action was attempted.' >&2
exit 1
