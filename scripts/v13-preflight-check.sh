#!/usr/bin/env bash
set -euo pipefail

# V13 QA/UAT gate runner.
#
# This extends the V12 local evidence gate with the V13 parent/execution
# strategy layer. It does not connect to live Coinbase. Real QA/UAT remains
# blocked until this script passes and the manual evidence below is attached to
# the release record.

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"

"$(dirname "$0")/../gradlew" clean
"$(dirname "$0")/../gradlew" check
"$(dirname "$0")/../gradlew" e2eTest
"$(dirname "$0")/../gradlew" :platform-benchmarks:jmh
"$(dirname "$0")/../gradlew" :platform-benchmarks:jmhLatencyReport
"$(dirname "$0")/../gradlew" :platform-benchmarks:verifyJmhReports

cat <<'EOT'
Manual V13 evidence required before real Coinbase QA/UAT:
- V12 preflight evidence remains attached and valid
- parent intent, parent terminal, child attribution, and parent snapshot/recovery evidence
- parent stuck, child stuck, hedge rejected, post-only reject loop, capacity full, and V12 rollback runbook signoff
- V12-to-V13 behavior-equivalence evidence for MarketMaking/PostOnlyQuote and Arb/MultiLegContingent
- JMH allocation and latency reports for parent registry, execution engine, and built-in execution strategies
- live-wire parent-intent E2E evidence using only the local Coinbase simulator
- secrets handling, credential rotation, monitoring, alert routing, failover, disaster recovery, deployment, and rollback evidence
EOT
