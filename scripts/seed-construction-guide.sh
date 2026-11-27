#!/usr/bin/env bash
set -euo pipefail
GUIDE_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$GUIDE_ROOT/frontend"
node scripts/seed-construction-guide.mjs
