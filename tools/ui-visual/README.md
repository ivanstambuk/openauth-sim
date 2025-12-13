# Operator Console visual snapshots (Playwright)

This tool captures **headless** real-browser screenshots of the operator console (`/ui/console`) so CSS/layout regressions
can be reviewed from images (spacing, backgrounds, alignment) without relying on HtmlUnit/Selenium rendering.

## What it does
- Starts the local REST server on a dedicated port (default `18080`).
- Clicks through protocol tabs and common Evaluate/Replay sub-tabs.
- Drives representative **preset/seed** flows to render right-side result panels (inline/stored + replay) and captures those states.
- Writes PNG screenshots into `build/ui-snapshots/**` (gitignored).

## Prerequisites
- Java 17 (`JAVA_HOME` configured)
- Node.js + npm

## Setup (one-time)
From the repo root:

```bash
cd tools/ui-visual
npm ci
npx playwright install chromium
```

## Run (recommended)
From the repo root:

```bash
bash tools/ui-visual/run-operator-console-snapshots.sh
```

Outputs land under `build/ui-snapshots/<run-id>/`.

### Keeping the snapshot directory bounded
The runner prunes old runs under `build/ui-snapshots/**` before creating a new one.

- Keep last 10 runs (default): `UI_VISUAL_MAX_RUNS=10`
- Keep last 5 runs: `UI_VISUAL_MAX_RUNS=5`
- Disable pruning (unbounded): `UI_VISUAL_MAX_RUNS=0`

Example:

```bash
UI_VISUAL_MAX_RUNS=5 bash tools/ui-visual/run-operator-console-snapshots.sh
```

## Visual QA workflow (capture → review → backlog → validate)
Use this harness only for UI-affecting changes (CSS/layout tweaks, console JS changes, or REST changes that alter UI rendering).

1) **Capture**: run `bash tools/ui-visual/run-operator-console-snapshots.sh` and note the produced run directory.
2) **Review**: use `build/ui-snapshots/<run-id>/manifest.json` as the table of contents and have the agent inspect the PNGs for layout/CSS anomalies.
   - If `manifest.json` includes entries with an `error`, treat that state as missing and fix the UI or harness until it can be captured again.
3) **Backlog**: create a task (Feature 009 or the owning UI feature) per issue with screenshot references + acceptance criteria.
4) **Validate**: re-run the harness after each fix and compare against the prior run directory to confirm the issue is resolved.

### Backlog item template
- **Symptom:** What looks wrong (alignment, spacing, background, typography, overflow, etc.).
- **Evidence:** `build/ui-snapshots/<run-id>/<file>.png` (and any other screens where it should match).
- **Fix sketch:** Likely CSS selector/module to adjust.
- **Acceptance criteria:** What must look consistent after the fix.
- **Validation:** `bash tools/ui-visual/run-operator-console-snapshots.sh` (compare to previous run).

## Run against an already-started server
If you already have the console running (for example on `http://localhost:8080`):

```bash
node tools/ui-visual/capture-operator-console.mjs --base-url http://localhost:8080 --out-dir build/ui-snapshots/manual
```

## Options
- `--base-url <url>`: base URL hosting `/ui/console` (default: `http://localhost:18080`)
- `--out-dir <path>`: output directory for screenshots (default: `build/ui-snapshots/<timestamp>/`)
- `--headed`: run with a visible browser window (off by default)
