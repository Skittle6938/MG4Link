#!/usr/bin/env bash
set -euo pipefail

version=${1:?Missing APK version}
tools="$ANDROID_HOME/build-tools/36.0.0"
keys=$(mktemp -d "${RUNNER_TEMP:-/tmp}/mg4link-sign.XXXXXX")
trap 'rm -rf -- "$keys"' EXIT
umask 077

# MG4 firmware trusts the public AOSP platform test certificate. Both the
# beta signing input and files fetched by the tag workflow must match its hashes.
aosp_keys='https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security'
if [[ -n "${MG4LINK_PLATFORM_KEY_BASE64:-}" ]]; then
    printf '%s' "$MG4LINK_PLATFORM_KEY_BASE64" | base64 -d > "$keys/platform.pk8"
else
    curl --retry 3 -fsS "$aosp_keys/platform.pk8?format=TEXT" | base64 -d > "$keys/platform.pk8"
fi
curl --retry 3 -fsS "$aosp_keys/platform.x509.pem?format=TEXT" | base64 -d > "$keys/platform.x509.pem"
printf '%s\n' \
    "1ad8ef556870edb70f69a9d3c112544c07de5162ba440d84d33f8bb0c5962875  $keys/platform.pk8" \
    "9837de028f460c35cc8d3fa45f14eecce30f6fbfe4b93d399aef1acb80c20d14  $keys/platform.x509.pem" \
    | sha256sum --check --status

shopt -s nullglob
unsigned=(app/build/outputs/apk/car/release/*.apk)
if (( ${#unsigned[@]} != 1 )); then
    echo 'Expected exactly one unsigned carRelease APK' >&2
    exit 1
fi
apk="MG4Link_$version.apk"
"$tools/zipalign" -f -p 4 "${unsigned[0]}" "$keys/aligned.apk"
"$tools/apksigner" sign --key "$keys/platform.pk8" --cert "$keys/platform.x509.pem" \
    --v4-signing-enabled false --out "$apk" "$keys/aligned.apk"
python3 scripts/verify_apk.py "$apk" --build-tools "$tools"
sha256sum "$apk" > "$apk.sha256"
sha256sum --check "$apk.sha256"
