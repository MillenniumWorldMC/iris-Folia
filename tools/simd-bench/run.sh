#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
java --add-modules jdk.incubator.vector -jar simd-bench.jar "$@"
