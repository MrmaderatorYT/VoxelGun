#!/usr/bin/env bash
# Standalone JVM regression runner: no Gradle, Android emulator, network or external jars needed.
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"
scenario_java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
scenario_javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
scenario_classes="$(mktemp -d "${TMPDIR:-/tmp}/voxelgun-bot-scenarios.XXXXXX")"
trap 'rm -rf "$scenario_classes"' EXIT
"$scenario_javac" --release 17 -d "$scenario_classes" \
  -sourcepath app/src/main/java tools/BotScenarioRunner.java
"$scenario_java" -Xmx1536m -cp "$scenario_classes" BotScenarioRunner "$@"
