#!/usr/bin/env bash
# Fetches the GT7 car catalogue from GT GridStats and writes app/src/main/assets/car-catalog.json.
# Requires GRIDSTATS_API_KEY in the environment. Uses ~10 metered calls; spacing keeps us under 10/min.
set -euo pipefail

: "${GRIDSTATS_API_KEY:?set GRIDSTATS_API_KEY to your GT GridStats token}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/android/app/src/main/assets/car-catalog.json"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

page=1
while :; do
    resp="$TMP/page-$page.json"
    code="$(curl -sS -o "$resp" -w '%{http_code}' \
        -H "Authorization: Bearer $GRIDSTATS_API_KEY" \
        -H "Accept: application/json" \
        "https://gt-gridstats.com/api/assets/cars?page=$page")"
    if [ "$code" != "200" ]; then
        echo "page $page failed: HTTP $code" >&2
        cat "$resp" >&2 || true
        exit 1
    fi
    jq -e '.data and (.total | type == "number")' "$resp" >/dev/null
    total="$(jq -r '.total' "$resp")"
    has_next="$(jq -r '.next_page_url // empty' "$resp")"
    echo "page $page: $(jq '.data | length' "$resp") cars (total $total)" >&2
    if [ -z "$has_next" ]; then break; fi
    page=$((page + 1))
    sleep 7
done

merged="$(jq -s '{source:"gt-gridstats.com", fetched_at:(now|todate), cars: ([.[].data[] | {code:.car_code, name:.name, class:.class, manufacturer:.manufacturer.name}] | sort_by(.code))}' "$TMP"/page-*.json)"
count="$(echo "$merged" | jq '.cars | length')"
if [ "$count" != "$total" ]; then
    echo "catalog count $count != API total $total" >&2
    exit 1
fi
echo "$merged" > "$OUT"
echo "wrote $OUT ($count cars)" >&2