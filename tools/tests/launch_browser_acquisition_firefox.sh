#!/bin/sh
set -eu
: "${FIREFOX_BIN:?verified official Firefox binary required}"
: "${FURYBOOK_FIREFOX_PROFILE:?task-private writable persistent profile required}"
[ "$#" -eq 1 ]
case "$1" in http://127.0.0.1:9876/\?id=*) ;; *) echo "Unexpected fixed-origin Karma capture URL" >&2; exit 1;; esac
if [ -n "${FURYBOOK_FIREFOX_EVIDENCE:-}" ]; then
    printf '%s\n' "$$" > "$FURYBOOK_FIREFOX_EVIDENCE/firefox-launcher.pid"
    printf '%s\n' "$FIREFOX_BIN" --headless --no-remote --profile "$FURYBOOK_FIREFOX_PROFILE" "$1" > "$FURYBOOK_FIREFOX_EVIDENCE/firefox-argv.txt"
    exec "$FIREFOX_BIN" --headless --no-remote --profile "$FURYBOOK_FIREFOX_PROFILE" "$1" > "$FURYBOOK_FIREFOX_EVIDENCE/firefox.log" 2>&1
fi
exec "$FIREFOX_BIN" --headless --no-remote --profile "$FURYBOOK_FIREFOX_PROFILE" "$1"
