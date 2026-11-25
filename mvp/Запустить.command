#!/bin/zsh
set -eu
cd "$(dirname "$0")"
RUNTIME="../../it-championship-runtime/node-v26.13.1-darwin-arm64/bin"
if [[ -x "$RUNTIME/node" ]]; then export PATH="$PWD/$RUNTIME:$PATH"; fi
if ! command -v node >/dev/null; then echo "Не найден Node.js. Установите Node.js 22.12 или новее."; read; exit 1; fi
if curl -fsS http://127.0.0.1:5173/ 2>/dev/null | grep -q 'Своё Село'; then open http://127.0.0.1:5173/; exit 0; fi
if [[ ! -d node_modules ]]; then npm ci; fi
(sleep 2; open http://127.0.0.1:5173/) &
npm run dev
