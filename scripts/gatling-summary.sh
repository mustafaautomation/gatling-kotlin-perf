#!/usr/bin/env bash
# Turn Gatling's console output into a markdown summary (assertion results + global stats).
# Usage: scripts/gatling-summary.sh <gatling-output.log> <title>
set -euo pipefail
log="$1"; title="$2"
{
  echo "### ${title}"
  echo
  echo '```'
  grep -E '^> (request count|mean throughput|response time (50th|95th|99th) percentile)' "$log" || true
  echo '```'
  echo
  echo "| Assertion | Result |"
  echo "|---|---|"
  grep -E ' : (true|false) \(actual' "$log" | sed -E 's/^(.*) : (true|false) (\(actual.*\))$/| \1 \3 | \2 |/' || true
  echo
} >> "${GITHUB_STEP_SUMMARY:-/dev/stdout}"
