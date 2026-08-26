#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "usage: $0 PRIVATE_KEY_PEM OUTPUT_DIRECTORY" >&2
  exit 2
fi

task_key=$1
task_output=$2
task_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
task_source="$task_root/extension"
task_chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
task_temp=$(mktemp -d /tmp/glosh-13bp-pack.XXXXXX)
trap 'rm -rf "$task_temp"' EXIT HUP INT TERM

test -f "$task_key"
test -x "$task_chrome"
mkdir -p "$task_output"
cp -R "$task_source" "$task_temp/extension"
openssl pkcs8 -topk8 -nocrypt -in "$task_key" -out "$task_temp/extension-key.pem"
chmod 600 "$task_temp/extension-key.pem"
"$task_chrome" \
  --no-message-box \
  --pack-extension="$task_temp/extension" \
  --pack-extension-key="$task_temp/extension-key.pem"
mv "$task_temp/extension.crx" "$task_output/extension.crx"

task_id=$(node "$task_root/verify-source.mjs" --print-id)
task_version=$(node -e 'const manifest=require(process.argv[1]); process.stdout.write(manifest.version)' "$task_source/manifest.json")
sed -e "s/@EXTENSION_ID@/$task_id/g" -e "s/@VERSION@/$task_version/g" \
  "$task_root/update.xml.template" > "$task_output/update.xml"

shasum -a 256 "$task_output/extension.crx" "$task_output/update.xml"
printf 'extensionId=%s version=%s\n' "$task_id" "$task_version"
