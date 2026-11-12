#!/bin/sh
set -eu
build_dir="build/classes"
rm -rf "$build_dir"
mkdir -p "$build_dir"
javac -d "$build_dir" src/creator/*.java
java -cp "$build_dir" creator.ReceiptDecisionTest
if [ "${RUN_REMOTE:-false}" = "true" ]; then
    java -cp "$build_dir" creator.ReceiptServiceMain
fi
