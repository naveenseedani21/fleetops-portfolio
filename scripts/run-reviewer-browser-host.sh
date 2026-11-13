#!/usr/bin/env bash
# Explicit host handoff for macOS environments that deny Chromium's Mach-port bootstrap.
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
workspace_dir="$(cd "$repo_dir/../.." && pwd)"
runner_dir=/private/tmp/fleetops-reviewer-web
node_bin=/private/tmp/fleetops-runtime/node-v22.23.5-darwin-arm64/bin/node
browser_dir="$workspace_dir/work/playwright-browsers"
[[ -x "$node_bin" && -f "$runner_dir/node_modules/playwright/cli.js" && -d "$browser_dir" ]] || { echo 'Prepared browser runtime is missing; stop and ask for preparation.' >&2; exit 1; }
cmp "$repo_dir/frontend/package-lock.json" "$runner_dir/package-lock.json" || { echo 'The prepared runtime does not match the repository lockfile; stopping.' >&2; exit 1; }
cp "$repo_dir/frontend/e2e/fleet.spec.ts" "$runner_dir/e2e/fleet.spec.ts"
cp "$repo_dir/frontend/playwright.config.ts" "$runner_dir/playwright.config.ts"
output_dir="$repo_dir/work/browser-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$output_dir"
cd "$runner_dir"
export PLAYWRIGHT_BROWSERS_PATH="$browser_dir"
echo "Evidence directory: $output_dir"
for environment in native docker; do
  if [[ "$environment" == native ]]; then
    export FLEETOPS_E2E_WEB=http://localhost:4200 FLEETOPS_E2E_API=http://localhost:8080 FLEETOPS_E2E_ISSUER=http://localhost:8180/realms/fleetops
  else
    export FLEETOPS_E2E_WEB=http://localhost:14200 FLEETOPS_E2E_API=http://localhost:18080 FLEETOPS_E2E_ISSUER=http://localhost:18180/realms/fleetops
  fi
  curl --max-time 30 -fsS "$FLEETOPS_E2E_API/actuator/health/readiness"
  curl --max-time 30 -fsS "$FLEETOPS_E2E_ISSUER/.well-known/openid-configuration" > /dev/null
  curl --max-time 30 -fsS "$FLEETOPS_E2E_WEB/config.json" > /dev/null
  echo "Running real $environment acceptance tests"
  "$node_bin" node_modules/playwright/cli.js test --output "$output_dir/$environment" 2>&1 | tee "$output_dir/$environment.log"
done
echo "Both native and isolated Docker browser workflows passed. Evidence: $output_dir"
