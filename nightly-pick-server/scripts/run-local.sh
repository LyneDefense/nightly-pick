#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.0.12.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

mvn spring-boot:run -Dspring-boot.run.profiles=local
