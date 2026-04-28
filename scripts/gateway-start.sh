#!/usr/bin/env bash

# Generic startup wrapper for one NitroJEx gateway process.
#
# The gateway shares the same JIT/native-access requirements as the cluster process,
# but remains a separate runtime so deployment can pin it to different CPUs and
# lifecycle policies. V11 runs one gateway instance per venue; callers must pass
# the intended venue explicitly so adding future venue wrappers cannot silently
# inherit the Coinbase venue-1 configuration.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/gateway-start.sh <venue-name> <gateway-config> [venues-config] [instruments-config]

Example:
  scripts/gateway-start.sh COINBASE config/gateway-1.toml

Per-venue wrapper scripts should call this file with an explicit venue name and
gateway config. NitroJEx deploys one gateway process per venue.
USAGE
}

if (( $# < 2 || $# > 4 )); then
  usage
  exit 64
fi

GATEWAY_VENUE="${1}"
GATEWAY_CONFIG="${2}"
VENUES_CONFIG="${3:-config/venues.toml}"
INSTRUMENTS_CONFIG="${4:-config/instruments.toml}"

if [[ -z "${GATEWAY_VENUE}" ]]; then
  echo "Gateway venue name must be explicit." >&2
  usage
  exit 64
fi

if [[ ! -f "${GATEWAY_CONFIG}" ]]; then
  echo "Gateway config not found: ${GATEWAY_CONFIG}" >&2
  exit 66
fi

if [[ ! -f "${VENUES_CONFIG}" ]]; then
  echo "Venues config not found: ${VENUES_CONFIG}" >&2
  exit 66
fi

if [[ ! -f "${INSTRUMENTS_CONFIG}" ]]; then
  echo "Instruments config not found: ${INSTRUMENTS_CONFIG}" >&2
  exit 66
fi

CONFIG_VENUE_ID="$(awk '
  /^\[process\]/ { in_process = 1; next }
  /^\[/ { in_process = 0 }
  in_process && $1 == "venueId" {
    gsub(/[[:space:]]/, "", $0)
    sub(/^venueId=/, "", $0)
    print $0
    exit
  }
' "${GATEWAY_CONFIG}")"

if [[ -z "${CONFIG_VENUE_ID}" ]]; then
  echo "Gateway config does not define [process].venueId: ${GATEWAY_CONFIG}" >&2
  exit 65
fi

CONFIG_VENUE_NAME="$(awk -v id="${CONFIG_VENUE_ID}" '
  /^\[\[venue\]\]/ { in_venue = 1; found_id = 0; name = ""; next }
  in_venue && $1 == "id" {
    value = $0
    gsub(/[[:space:]]/, "", value)
    sub(/^id=/, "", value)
    found_id = (value == id)
  }
  in_venue && $1 == "name" {
    value = $0
    sub(/^[[:space:]]*name[[:space:]]*=[[:space:]]*/, "", value)
    gsub(/"/, "", value)
    name = value
  }
  in_venue && found_id && name != "" {
    print name
    exit
  }
' "${VENUES_CONFIG}")"

if [[ -z "${CONFIG_VENUE_NAME}" ]]; then
  echo "Venue id ${CONFIG_VENUE_ID} from ${GATEWAY_CONFIG} is not present in ${VENUES_CONFIG}" >&2
  exit 65
fi

if [[ "${CONFIG_VENUE_NAME}" != "${GATEWAY_VENUE}" ]]; then
  echo "Gateway venue mismatch: requested ${GATEWAY_VENUE}, but ${GATEWAY_CONFIG} points to ${CONFIG_VENUE_NAME} (venueId=${CONFIG_VENUE_ID})." >&2
  exit 65
fi

if [[ -z "${GATEWAY_JAR:-}" ]]; then
  shopt -s nullglob
  gateway_jars=("${REPO_ROOT}"/platform-gateway/build/libs/*-all.jar)
  shopt -u nullglob

  if (( ${#gateway_jars[@]} == 0 )); then
    echo "Gateway shadow jar not found. Build it with: ./gradlew :platform-gateway:shadowJar" >&2
    exit 1
  fi

  GATEWAY_JAR="${gateway_jars[0]}"
fi

JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}java"

FIRST_SESSION_FLAGS="-XX:-BackgroundCompilation"
JVM_PROD_FLAGS="-XX:+UnlockDiagnosticVMOptions \
                -XX:+PrintCompilation -XX:+TraceDeoptimization \
                -XX:CompileThreshold=5000 -XX:Tier4InvocationThreshold=5000 \
                -XX:MaxInlineLevel=15 \
                -XX:CompileCommandFile=${REPO_ROOT}/config/hotspot_compiler \
                -XX:-RestrictContended \
                --enable-native-access=ALL-UNNAMED"

JAVA_GC_FLAGS="${JAVA_GC_FLAGS:--XX:+UseZGC}"
JAVA_HEAP_FLAGS="${JAVA_HEAP_FLAGS:--Xms1g -Xmx1g}"

exec "${JAVA_BIN}" \
  ${FIRST_SESSION_FLAGS} \
  ${JVM_PROD_FLAGS} \
  ${JAVA_GC_FLAGS} \
  ${JAVA_HEAP_FLAGS} \
  --enable-preview \
  --add-modules jdk.incubator.foreign \
  -jar "${GATEWAY_JAR}" \
  "${GATEWAY_CONFIG}" \
  "${VENUES_CONFIG}" \
  "${INSTRUMENTS_CONFIG}"
