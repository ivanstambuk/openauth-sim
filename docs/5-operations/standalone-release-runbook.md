# Standalone Release Runbook

Use this checklist whenever you want to publish a new `io.github.ivanstambuk:openauth-sim-standalone` release.

1. **Bump the version.** Update `VERSION_NAME` in [gradle.properties](../../gradle.properties) to the new semantic version (for example `0.1.3`).
2. **Rebuild the aggregated jar.**
   ```bash
   ./gradlew --no-daemon :standalone:jar
   ```
3. **Run the workspace gate.**
   ```bash
   ./gradlew --no-daemon spotlessApply check
   ```
4. **Commit the version bump.**
   ```bash
   git add -A
   git commit -m "chore: bump version to <version>" --timeout=300000
   ```
   (Add any other touched files to `git add` as needed.)
5. **Push to main.**
   ```bash
   git push origin main
   ```
6. **Tag the release.**
   ```bash
   git tag -f v<version>
   git push -f origin v<version>
   ```
7. **Publish on Maven Central.** Trigger the GitHub Action **Publish Standalone Artifact** (either via `workflow_dispatch` or by publishing the GitHub release).

> Once the workflow succeeds, verify the release at <https://central.sonatype.com/artifact/io.github.ivanstambuk/openauth-sim-standalone> and update docs/roadmap if needed.
