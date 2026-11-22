#!/usr/bin/env bash
set -euo pipefail

sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager" ]]; then sdkmanager=$(command -v sdkmanager); fi
for attempt in 1 2 3; do
    "$sdkmanager" 'platforms;android-36' 'build-tools;36.0.0' 'ndk;27.2.12479018' 'cmake;3.22.1' && exit 0
    echo "Android SDK installation attempt $attempt failed" >&2
    if (( attempt < 3 )); then sleep 15; fi
done
exit 1
