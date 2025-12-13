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

## Run against an already-started server
If you already have the console running (for example on `http://localhost:8080`):

```bash
node tools/ui-visual/capture-operator-console.mjs --base-url http://localhost:8080 --out-dir build/ui-snapshots/manual
```

## Options
- `--base-url <url>`: base URL hosting `/ui/console` (default: `http://localhost:18080`)
- `--out-dir <path>`: output directory for screenshots (default: `build/ui-snapshots/<timestamp>/`)
- `--headed`: run with a visible browser window (off by default)
