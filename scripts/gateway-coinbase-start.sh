#!/usr/bin/env bash

# Starts the dedicated Coinbase production gateway instance.
#
# NitroJEx runs one gateway process per venue. This wrapper makes the venue
# choice explicit and delegates all common JVM/runtime flags to gateway-start.sh.
# Future venues should add their own wrapper with their own venue name and
# gateway config rather than relying on a hidden default.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

exec "${SCRIPT_DIR}/gateway-start.sh" \
  COINBASE \
  "${REPO_ROOT}/config/gateway-1.toml" \
  "${1:-${REPO_ROOT}/config/venues.toml}" \
  "${2:-${REPO_ROOT}/config/instruments.toml}"
