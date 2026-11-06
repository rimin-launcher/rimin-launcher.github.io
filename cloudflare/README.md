# lumi-launcher.dev

- `worker.js`, `wrangler.toml`: Cloudflare Worker that serves every file from the `lumi-site` R2 bucket.
- `site/`: page source (no APKs). Downloads and signed feeds are produced by the owner source's
  `tools/publish-site.py --brand Lumi --base https://lumi-launcher.dev/`.
- `upload-site.sh <dir>`: upload a directory to the bucket (needs a Cloudflare API token with R2 edit).
