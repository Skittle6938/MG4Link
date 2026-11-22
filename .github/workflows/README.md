# CI

Android CI checks version progression and translation formats, runs car unit tests and lint,
and builds the car and phone variants. Each run has a summary and downloadable reports.
The Android toolchain is pinned in `scripts/ci_android_sdk.sh`.

In Actions, run **Publish beta** on `main` and supply the version and release notes.
Leave **dry_run** enabled to get a signed APK and its checksum as artifacts without
changing `main` or creating a release. With dry-run disabled, the workflow increments
the Android version code, pushes the version and tag, validates the draft assets, then
publishes a prerelease. A separate tag workflow still supports the existing release path.

**UI previews** renders MG4Link screens and a startup fade as a downloadable artifact.

Publish download site deploys only `site/` to GitHub Pages after regenerating all five
language editions. Pages uses the GitHub Actions deployment source.
