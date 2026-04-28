#!/usr/bin/env bash
set -euo pipefail

# V12 QA/UAT gate runner.
#
# This script is intentionally local and evidence-oriented: it runs the automated
# gates that must pass before real Coinbase QA/UAT, then prints the manual
# production-readiness artifacts that must be attached to the release record.

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"

"$(dirname "$0")/../gradlew" clean
"$(dirname "$0")/../gradlew" check
"$(dirname "$0")/../gradlew" e2eTest
"$(dirname "$0")/../gradlew" :platform-benchmarks:jmh
"$(dirname "$0")/../gradlew" :platform-benchmarks:jmhLatencyReport

cat <<'EOT'
Manual evidence required before production connectivity claims:
- secrets handling and credential rotation signoff
- kill switch, rejected-order, disconnect/reconnect, stale-data, and self-trade-prevention evidence
- balance, position, order, and execution reconciliation evidence
- monitoring dashboards, alert routing, failover, disaster recovery, deployment, and rollback evidence
EOT
