#!/usr/bin/env bash

# Startup wrapper for a NitroJEx cluster node.
#
# The script documents the production JVM defaults required by the execution plan:
# - synchronous first-session warmup compilation
# - compilation/deoptimization visibility
# - compile-command file injection
# - native-access and @Contended support
#
# TASK-001 provides only the scaffold. Later tasks supply the real cluster jar,
# config schema, and operational wiring.

set -euo pipefail

NODE_CONFIG="${1:-config/cluster-node-0.toml}"
CLUSTER_JAR="${CLUSTER_JAR:-platform-cluster/build/libs/platform-cluster-all.jar}"

# Session-1 warmup flag. Operators can clear this after ReadyNow profiles exist.
FIRST_SESSION_FLAGS="-XX:-BackgroundCompilation"

# Shared production/runtime tuning from Spec §27.2 and §27.5.
JVM_PROD_FLAGS="-XX:+PrintCompilation -XX:+TraceDeoptimization \
                -XX:CompileThreshold=5000 -XX:Tier4InvocationThreshold=5000 \
                -XX:MaxInlineLevel=15 \
                -XX:CompileCommandFile=config/hotspot_compiler \
                -XX:-RestrictContended \
                --enable-native-access=ALL-UNNAMED"

# Development defaults to ZGC for local iteration. Production can override JAVA_GC_FLAGS.
JAVA_GC_FLAGS="${JAVA_GC_FLAGS:--XX:+UseZGC}"
JAVA_HEAP_FLAGS="${JAVA_HEAP_FLAGS:--Xms1g -Xmx1g}"

exec java \
  ${FIRST_SESSION_FLAGS} \
  ${JVM_PROD_FLAGS} \
  ${JAVA_GC_FLAGS} \
  ${JAVA_HEAP_FLAGS} \
  --enable-preview \
  --add-modules jdk.incubator.foreign \
  -jar "${CLUSTER_JAR}" \
  "${NODE_CONFIG}"
