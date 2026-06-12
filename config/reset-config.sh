#!/usr/bin/env bash
# Reset all *-config.properties from *-config.example.properties templates.
# Usage: sh reset-config.sh   (run from any directory)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

count=0
shopt -s nullglob
for example in *-config.example.properties; do
  target="${example%.example.properties}.properties"
  cp -f "$example" "$target"
  echo "Copied: $example -> $target"
  count=$((count + 1))
done

if [ "$count" -eq 0 ]; then
  echo "No *-config.example.properties files found in $SCRIPT_DIR"
  exit 1
fi

echo "Done. $count config file(s) reset from templates."
