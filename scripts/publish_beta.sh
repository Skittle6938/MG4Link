#!/usr/bin/env bash
set -euo pipefail

version=${1:?Missing beta version}
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+-beta[1-9][0-9]*$ ]]
tag="v$version"
apk="MG4Link_$version.apk"
notes="$RUNNER_TEMP/release-notes.md"
sha256sum --check "$apk.sha256"

git fetch --tags origin main
test "$(git rev-parse FETCH_HEAD)" = "$GITHUB_SHA" ||
    { echo 'main changed during the build; dispatch again from main'; exit 1; }
python3 scripts/ci_version.py --prepare-beta "$version"

if ! git diff --quiet -- app/build.gradle.kts; then
    git -c user.name='github-actions[bot]' \
        -c user.email='41898282+github-actions[bot]@users.noreply.github.com' \
        commit -m "Prepare MG4Link $version" -- app/build.gradle.kts
fi
sha=$(git rev-parse HEAD)
if git show-ref --verify --quiet "refs/tags/$tag"; then
    test "$(git rev-list -n 1 "$tag")" = "$sha" ||
        { echo 'Existing tag belongs to a different build'; exit 1; }
else
    git tag "$tag" "$sha"
fi
# Both refs are updated together, without force. A concurrent main update fails the push.
git push --atomic origin HEAD:refs/heads/main "refs/tags/$tag"

printf '\nBuilt by the [Publish beta workflow](%s/%s/actions/runs/%s) from `%s`.\n' \
    "$GITHUB_SERVER_URL" "$GITHUB_REPOSITORY" "$GITHUB_RUN_ID" "$sha" >> "$notes"
if draft=$(gh release view "$tag" --json isDraft --jq .isDraft 2>/dev/null); then
    test "$draft" = true || { echo 'Version already published'; exit 1; }
else
    gh release create "$tag" --verify-tag --draft --prerelease --latest=false \
        --title "MG4Link $version" --notes-file "$notes"
fi
gh release upload "$tag" "$apk" "$apk.sha256" --clobber
for file in "$apk" "$apk.sha256"; do
    local_digest=$(sha256sum "$file" | cut -d' ' -f1)
    uploaded_digest=$(gh release view "$tag" --json assets \
        --jq ".assets[] | select(.name == \"$file\" and .state == \"uploaded\") | .digest")
    test "$uploaded_digest" = "sha256:$local_digest" ||
        { echo "Digest verification failed for $file; release remains a draft"; exit 1; }
done
gh release edit "$tag" --draft=false --prerelease --latest=false --notes-file "$notes"
echo "## Published MG4Link $version" >> "$GITHUB_STEP_SUMMARY"
gh release view "$tag" --json url --jq .url >> "$GITHUB_STEP_SUMMARY"
