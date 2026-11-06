#!/usr/bin/env bash
# Upload site/ to the R2 bucket served by the lumi-launcher Worker (lumi-launcher.dev).
# Needs a Cloudflare API token (R2 edit) sent as "Authorization: Bearer ..." — here via
# CLOUDFLARE_API_TOKEN, or injected by a network proxy. Usage: cloudflare/upload-site.sh [site-dir]
set -euo pipefail
ACCOUNT="${CLOUDFLARE_ACCOUNT_ID:-cdbc0771b0f3c6b3d2619b35e4a080e7}"
BUCKET="${LUMI_BUCKET:-lumi-site}"
SITE="${1:-site}"
auth=(); [ -n "${CLOUDFLARE_API_TOKEN:-}" ] && auth=(-H "authorization: Bearer $CLOUDFLARE_API_TOKEN")
cd "$SITE"
find . -type f | sort | while IFS= read -r f; do
  k="${f#./}"
  case "$k" in
    *.html) t="text/html; charset=utf-8";; *.css) t="text/css; charset=utf-8";;
    *.js) t="text/javascript; charset=utf-8";; *.json) t="application/json; charset=utf-8";;
    *.svg) t="image/svg+xml";; *.png) t="image/png";; *.webp) t="image/webp";;
    *.txt) t="text/plain; charset=utf-8";; *.apk) t="application/vnd.android.package-archive";;
    *) t="application/octet-stream";;
  esac
  ok=$(curl -s -X PUT "https://api.cloudflare.com/client/v4/accounts/$ACCOUNT/r2/buckets/$BUCKET/objects/$k" \
       "${auth[@]}" -H "content-type: $t" --data-binary @"$f" | python3 -c "import sys,json;print(json.load(sys.stdin)['success'])")
  echo "$ok $k"; [ "$ok" = "True" ]
done
