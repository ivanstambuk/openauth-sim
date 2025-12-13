import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');

function nowRunId() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

function parseArgs(argv) {
  const options = {
    baseUrl: 'http://localhost:18080',
    outDir: path.join(repoRoot, 'build', 'ui-snapshots', nowRunId()),
    headed: false,
    timeoutMs: 30_000,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--base-url') {
      options.baseUrl = String(argv[++index] ?? '');
      continue;
    }
    if (arg === '--out-dir') {
      options.outDir = path.resolve(String(argv[++index] ?? ''));
      continue;
    }
    if (arg === '--timeout-ms') {
      options.timeoutMs = Number(argv[++index] ?? options.timeoutMs);
      continue;
    }
    if (arg === '--headed') {
      options.headed = true;
      continue;
    }
    if (arg === '--help' || arg === '-h') {
      return { ...options, help: true };
    }
    throw new Error(`Unknown argument: ${arg}`);
  }

  return options;
}

function normalizeBaseUrl(baseUrl) {
  const trimmed = String(baseUrl ?? '').trim();
  if (!trimmed) {
    throw new Error('Base URL is empty');
  }
  return trimmed.endsWith('/') ? trimmed.slice(0, -1) : trimmed;
}

function uiConsoleUrl(baseUrl) {
  return `${baseUrl}/ui/console`;
}

function testIdSelector(testId) {
  return `[data-testid="${testId}"]`;
}

async function clickIfVisible(page, selector) {
  const locator = page.locator(selector).first();
  if ((await locator.count()) === 0) {
    return false;
  }
  if (!(await locator.isVisible().catch(() => false))) {
    return false;
  }
  await locator.click();
  return true;
}

async function waitForEnabled(page, selector) {
  await page.waitForFunction((sel) => {
    const element = document.querySelector(sel);
    if (!element) {
      return false;
    }
    // eslint-disable-next-line no-undef
    if (element instanceof HTMLInputElement || element instanceof HTMLSelectElement || element instanceof HTMLTextAreaElement) {
      return !element.disabled;
    }
    return !element.hasAttribute('disabled');
  }, selector);
}

async function checkAndDispatchChange(page, selector) {
  return page.evaluate((sel) => {
    const element = document.querySelector(sel);
    if (!element) {
      return false;
    }
    // eslint-disable-next-line no-undef
    if (element instanceof HTMLInputElement) {
      if (element.type === 'checkbox' || element.type === 'radio') {
        element.checked = true;
      }
    }
    element.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  }, selector);
}

async function waitForSelectHasValueOption(page, selector) {
  await page.waitForFunction((sel) => {
    const element = document.querySelector(sel);
    if (!element || element.tagName !== 'SELECT') {
      return false;
    }
    const select = /** @type {HTMLSelectElement} */ (element);
    return Array.from(select.options).some((option) => option.value && !option.disabled);
  }, selector);
}

async function selectFirstValueOption(page, selector) {
  const select = page.locator(selector).first();
  if ((await select.count()) === 0) {
    return null;
  }

  await waitForEnabled(page, selector);
  await waitForSelectHasValueOption(page, selector);

  const value = await select.evaluate((element) => {
    const selectElement = /** @type {HTMLSelectElement} */ (element);
    const option = Array.from(selectElement.options).find((candidate) => candidate.value && !candidate.disabled);
    return option ? option.value : '';
  });

  if (!value) {
    return null;
  }

  await select.selectOption(value);
  return value;
}

async function selectFirstValueOptionOrSeed(page, selector, seedButtonSelector) {
  const deadline = Date.now() + 20_000;
  let seedClicked = false;
  while (Date.now() < deadline) {
    const selectable = await page
      .evaluate((sel) => {
        const element = document.querySelector(sel);
        if (!element || element.tagName !== 'SELECT') {
          return false;
        }
        const select = /** @type {HTMLSelectElement} */ (element);
        return !select.disabled && Array.from(select.options).some((option) => option.value && !option.disabled);
      }, selector)
      .catch(() => false);

    if (selectable) {
      return selectFirstValueOption(page, selector);
    }

    if (seedButtonSelector && !seedClicked) {
      seedClicked = await clickIfVisible(page, seedButtonSelector);
    }

    await page.waitForTimeout(250);
  }

  return selectFirstValueOption(page, selector);
}

async function ensureInputHasValue(page, selector, fallbackValue) {
  const input = page.locator(selector).first();
  if ((await input.count()) === 0) {
    return false;
  }

  const current = String(await input.inputValue().catch(() => '')).trim();
  if (current) {
    return true;
  }

  await input.fill(fallbackValue);
  return true;
}

async function clickAndWaitAriaSelected(page, testId) {
  const selector = `[data-testid="${testId}"]`;
  const button = page.locator(selector);
  if ((await button.count()) === 0) {
    return false;
  }
  await button.first().click();
  await page.waitForFunction(
    (id) => document.querySelector(`[data-testid="${id}"]`)?.getAttribute('aria-selected') === 'true',
    testId,
  );
  return true;
}

async function clickProtocolTab(page, protocol) {
  const testId = `protocol-tab-${protocol}`;
  const selector = `[data-testid="${testId}"]`;
  const tab = page.locator(selector);
  if ((await tab.count()) === 0) {
    throw new Error(`Missing protocol tab: ${selector}`);
  }
  await tab.first().click();
  await page.waitForFunction(
    (id) => document.querySelector(`[data-testid="${id}"]`)?.getAttribute('aria-selected') === 'true',
    testId,
  );
}

async function capture(page, outDir, name) {
  const filePath = path.join(outDir, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage: true });
  return filePath;
}

async function captureStep(manifest, page, outDir, protocol, state, fileBaseName, run) {
  try {
    await run();
    const filePath = await capture(page, outDir, fileBaseName);
    manifest.screenshots.push({ protocol, state, file: filePath });
  } catch (error) {
    manifest.errors = manifest.errors || [];
    manifest.errors.push({ protocol, state, error: String(error?.stack || error) });
  }
}

async function seedCanonicalData(page, baseUrl, csrfToken, timeoutMs, manifest) {
  const seeds = [
    { protocol: 'ocra', endpoint: '/api/v1/ocra/credentials/seed' },
    { protocol: 'hotp', endpoint: '/api/v1/hotp/credentials/seed' },
    { protocol: 'totp', endpoint: '/api/v1/totp/credentials/seed' },
    { protocol: 'emv', endpoint: '/api/v1/emv/cap/credentials/seed' },
    { protocol: 'fido2', endpoint: '/api/v1/webauthn/credentials/seed' },
    { protocol: 'eudi-openid4vp', endpoint: '/api/v1/eudiw/openid4vp/presentations/seed' },
  ];

  if (!csrfToken) {
    return { seeded: [], skipped: seeds.map((entry) => entry.endpoint) };
  }

  const headers = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-CSRF-TOKEN': csrfToken,
  };

  const seeded = [];
  for (const entry of seeds) {
    try {
      const response = await page.request.post(`${baseUrl}${entry.endpoint}`, {
        headers,
        data: {},
        timeout: timeoutMs,
      });
      seeded.push({ ...entry, status: response.status(), ok: response.ok() });
    } catch (error) {
      manifest.errors = manifest.errors || [];
      manifest.errors.push({
        protocol: entry.protocol,
        state: 'bootstrap-seed',
        error: String(error?.stack || error),
      });
      seeded.push({ ...entry, status: 0, ok: false });
    }
  }

  return { seeded, skipped: [] };
}

async function captureHotpInteractive(manifest, page, outDir, baseName) {
  await captureStep(manifest, page, outDir, 'hotp', 'evaluate-inline-result', `${baseName}--evaluate--inline--result`, async () => {
    await clickAndWaitAriaSelected(page, 'hotp-panel-tab-evaluate');
    await clickIfVisible(page, testIdSelector('hotp-mode-select-inline'));
    await selectFirstValueOption(page, testIdSelector('hotp-inline-preset-select'));
    await page.locator(testIdSelector('hotp-inline-evaluate-button')).first().click();
    await page.locator(testIdSelector('hotp-inline-result-panel')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'hotp', 'evaluate-stored-result', `${baseName}--evaluate--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'hotp-panel-tab-evaluate');
    await page.locator(testIdSelector('hotp-mode-select-stored')).first().click();
    await page.locator(testIdSelector('hotp-stored-evaluation-panel')).first().waitFor({ state: 'visible' });
    await selectFirstValueOptionOrSeed(page, '#hotpStoredCredentialId', testIdSelector('hotp-seed-credentials'));
    await page.locator(testIdSelector('hotp-stored-evaluate-button')).first().click();
    await page.locator(testIdSelector('hotp-stored-result-panel')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'hotp', 'replay-stored-result', `${baseName}--replay--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'hotp-panel-tab-replay');
    await clickIfVisible(page, testIdSelector('hotp-replay-mode-select-stored'));
    await page.locator(testIdSelector('hotp-replay-stored-panel')).first().waitFor({ state: 'visible' });
    await selectFirstValueOption(page, '#hotpReplayStoredCredentialId');
    await ensureInputHasValue(page, '#hotpReplayStoredOtp', '000000');
    await page.locator(testIdSelector('hotp-replay-submit')).first().click();
    await page.locator(testIdSelector('hotp-replay-result')).first().waitFor({ state: 'visible' });
  });
}

async function captureTotpInteractive(manifest, page, outDir, baseName) {
  await captureStep(manifest, page, outDir, 'totp', 'evaluate-inline-result', `${baseName}--evaluate--inline--result`, async () => {
    await clickAndWaitAriaSelected(page, 'totp-panel-tab-evaluate');
    await clickIfVisible(page, testIdSelector('totp-mode-select-inline'));
    await selectFirstValueOption(page, testIdSelector('totp-inline-preset-select'));
    await page.locator(testIdSelector('totp-inline-evaluate-button')).first().click();
    await page.locator(testIdSelector('totp-inline-result-panel')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'totp', 'evaluate-stored-result', `${baseName}--evaluate--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'totp-panel-tab-evaluate');
    await page.locator(testIdSelector('totp-mode-select-stored')).first().click();
    await page.locator(testIdSelector('totp-stored-panel')).first().waitFor({ state: 'visible' });
    await selectFirstValueOptionOrSeed(page, '#totpStoredCredentialId', testIdSelector('totp-seed-credentials'));
    await page.locator(testIdSelector('totp-stored-evaluate-button')).first().click();
    await page.locator(testIdSelector('totp-stored-result-panel')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'totp', 'replay-stored-result', `${baseName}--replay--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'totp-panel-tab-replay');
    await clickIfVisible(page, testIdSelector('totp-replay-mode-select-stored'));
    await selectFirstValueOption(page, '#totpReplayStoredCredentialId');
    await ensureInputHasValue(page, '#totpReplayStoredOtp', '000000');
    await page.locator(testIdSelector('totp-replay-stored-submit')).first().click();
    await page.locator(testIdSelector('totp-replay-result-panel')).first().waitFor({ state: 'visible' });
  });
}

async function captureOcraInteractive(manifest, page, outDir, baseName) {
  await captureStep(manifest, page, outDir, 'ocra', 'evaluate-inline-result', `${baseName}--evaluate--inline--result`, async () => {
    await clickAndWaitAriaSelected(page, 'ocra-mode-select-evaluate');
    await clickIfVisible(page, '#mode-inline');
    await selectFirstValueOption(page, testIdSelector('inline-policy-select'));
    await page.locator(testIdSelector('ocra-evaluate-button')).first().click();
    await page.locator(testIdSelector('ocra-result-panel')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'ocra', 'evaluate-stored-result', `${baseName}--evaluate--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'ocra-mode-select-evaluate');
    await clickIfVisible(page, '#mode-credential');
    await selectFirstValueOptionOrSeed(page, '#credentialId', testIdSelector('ocra-seed-credentials'));
    await page.locator(testIdSelector('ocra-evaluate-button')).first().click();
    await page.locator(testIdSelector('ocra-result-panel')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'ocra', 'replay-stored-result', `${baseName}--replay--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'ocra-mode-select-replay');
    await checkAndDispatchChange(page, '#replayModeStored');
    await selectFirstValueOption(page, '#replayCredentialId');
    await page
      .waitForFunction(() => {
        const otpInput = document.getElementById('replayOtp');
        return otpInput && typeof otpInput.value === 'string' && otpInput.value.trim().length > 0;
      }, null, { timeout: 5_000 })
      .catch(() => {});
    await ensureInputHasValue(page, '#replayOtp', '000000');
    await page.locator(testIdSelector('ocra-replay-submit')).first().click();
    await page.locator(testIdSelector('ocra-replay-result')).first().waitFor({ state: 'visible' });
  });
}

async function captureEmvInteractive(manifest, page, outDir, baseName) {
  await captureStep(manifest, page, outDir, 'emv', 'evaluate-inline-result', `${baseName}--evaluate--inline--result`, async () => {
    await clickAndWaitAriaSelected(page, 'emv-console-tab-evaluate');
    await checkAndDispatchChange(page, testIdSelector('emv-mode-select-stored'));
    await selectFirstValueOptionOrSeed(page, '#emvStoredCredentialId', testIdSelector('emv-seed-credentials'));
    await checkAndDispatchChange(page, testIdSelector('emv-mode-select-inline'));
    await page.locator(testIdSelector('emv-evaluate-submit')).first().click();
    await page.locator(testIdSelector('emv-result-card')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'emv', 'evaluate-stored-result', `${baseName}--evaluate--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'emv-console-tab-evaluate');
    await checkAndDispatchChange(page, testIdSelector('emv-mode-select-stored'));
    await selectFirstValueOptionOrSeed(page, '#emvStoredCredentialId', testIdSelector('emv-seed-credentials'));
    await page.locator(testIdSelector('emv-evaluate-submit')).first().click();
    await page.locator(testIdSelector('emv-result-card')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'emv', 'replay-stored-result', `${baseName}--replay--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'emv-console-tab-replay');
    await checkAndDispatchChange(page, '#emvReplayModeStored');
    await selectFirstValueOption(page, '#emvReplayStoredCredentialId');
    await ensureInputHasValue(page, '#emvReplayOtp', '000000');
    await page.locator(testIdSelector('emv-replay-submit')).first().click();
    await page.locator(testIdSelector('emv-replay-result-card')).first().waitFor({ state: 'visible' });
  });
}

async function captureFido2Interactive(manifest, page, outDir, baseName) {
  await captureStep(manifest, page, outDir, 'fido2', 'evaluate-inline-result', `${baseName}--evaluate--inline--result`, async () => {
    await clickAndWaitAriaSelected(page, 'fido2-panel-tab-evaluate');
    await clickIfVisible(page, testIdSelector('fido2-evaluate-mode-select-inline'));
    await page.locator(testIdSelector('fido2-evaluate-inline-section')).first().waitFor({ state: 'visible' });
    await waitForEnabled(page, '#fido2InlineSampleSelect');
    await selectFirstValueOption(page, '#fido2InlineSampleSelect');
    await page.locator(testIdSelector('fido2-evaluate-inline-submit')).first().click();
    await page.locator(testIdSelector('fido2-inline-result')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'fido2', 'evaluate-stored-result', `${baseName}--evaluate--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'fido2-panel-tab-evaluate');
    await clickIfVisible(page, testIdSelector('fido2-evaluate-mode-select-stored'));
    await page.locator(testIdSelector('fido2-evaluate-stored-section')).first().waitFor({ state: 'visible' });
    await selectFirstValueOptionOrSeed(page, '#fido2StoredCredentialId', testIdSelector('fido2-seed-credentials'));
    await page.locator(testIdSelector('fido2-evaluate-stored-submit')).first().click();
    await page.locator(testIdSelector('fido2-stored-result')).first().waitFor({ state: 'visible' });
  });

  await captureStep(manifest, page, outDir, 'fido2', 'replay-stored-result', `${baseName}--replay--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'fido2-panel-tab-replay');
    await clickIfVisible(page, testIdSelector('fido2-replay-mode-select-stored'));
    await page.locator(testIdSelector('fido2-replay-form')).first().waitFor({ state: 'visible' });
    await selectFirstValueOption(page, '#fido2ReplayCredentialId');
    await page.locator(testIdSelector('fido2-replay-stored-submit')).first().click();
    await page.locator(testIdSelector('fido2-replay-result')).first().waitFor({ state: 'visible' });
  });
}

async function captureEudiwInteractive(manifest, page, outDir, baseName) {
  await captureStep(manifest, page, outDir, 'eudi-openid4vp', 'evaluate-inline-result', `${baseName}--evaluate--inline--result`, async () => {
    await clickAndWaitAriaSelected(page, 'eudiw-panel-tab-evaluate');
    await checkAndDispatchChange(page, testIdSelector('eudiw-evaluate-mode-select-inline'));
    await page.locator(testIdSelector('eudiw-evaluate-inline-section')).first().waitFor({ state: 'visible' });
    await waitForEnabled(page, testIdSelector('eudiw-inline-sample-apply'));
    await page.locator(testIdSelector('eudiw-inline-sample-apply')).first().click();
    await page.locator(testIdSelector('eudiw-simulate-button')).first().click();
    await page.waitForFunction(() => {
      const node = document.querySelector('[data-testid="eudiw-result-status"]');
      return node && node.textContent && node.textContent.trim() !== 'Awaiting simulation';
    });
  });

  await captureStep(manifest, page, outDir, 'eudi-openid4vp', 'evaluate-stored-result', `${baseName}--evaluate--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'eudiw-panel-tab-evaluate');
    await checkAndDispatchChange(page, testIdSelector('eudiw-evaluate-mode-select-stored'));
    await page.locator(testIdSelector('eudiw-evaluate-stored-section')).first().waitFor({ state: 'visible' });
    await selectFirstValueOption(page, '#eudiwStoredCredential');
    await page.locator(testIdSelector('eudiw-simulate-button')).first().click();
    await page.waitForFunction(() => {
      const node = document.querySelector('[data-testid="eudiw-result-status"]');
      return node && node.textContent && node.textContent.trim() !== 'Awaiting simulation';
    });
  });

  await captureStep(manifest, page, outDir, 'eudi-openid4vp', 'replay-inline-result', `${baseName}--replay--inline--result`, async () => {
    await clickAndWaitAriaSelected(page, 'eudiw-panel-tab-replay');
    await checkAndDispatchChange(page, testIdSelector('eudiw-replay-mode-select-inline'));
    await page.locator(testIdSelector('eudiw-replay-inline-section')).first().waitFor({ state: 'visible' });
    await waitForEnabled(page, testIdSelector('eudiw-replay-sample-apply'));
    await page.locator(testIdSelector('eudiw-replay-sample-apply')).first().click();
    await page.locator(testIdSelector('eudiw-validate-button')).first().click();
    await page.waitForFunction(() => {
      const node = document.querySelector('[data-testid="eudiw-replay-status"]');
      return node && node.textContent && node.textContent.trim() !== 'Awaiting validation';
    });
  });

  await captureStep(manifest, page, outDir, 'eudi-openid4vp', 'replay-stored-result', `${baseName}--replay--stored--result`, async () => {
    await clickAndWaitAriaSelected(page, 'eudiw-panel-tab-replay');
    await checkAndDispatchChange(page, testIdSelector('eudiw-replay-mode-select-stored'));
    await page.locator(testIdSelector('eudiw-replay-stored-section')).first().waitFor({ state: 'visible' });
    await selectFirstValueOption(page, '#eudiwReplayStoredSelect');
    await page.locator(testIdSelector('eudiw-validate-button')).first().click();
    await page.waitForFunction(() => {
      const node = document.querySelector('[data-testid="eudiw-replay-status"]');
      return node && node.textContent && node.textContent.trim() !== 'Awaiting validation';
    });
  });
}

async function run() {
  const argv = process.argv.slice(2);
  const options = parseArgs(argv);
  if (options.help) {
    process.stdout.write(
      [
        'Usage: node tools/ui-visual/capture-operator-console.mjs [options]',
        '',
        'Options:',
        '  --base-url <url>     Base URL hosting /ui/console (default: http://localhost:18080)',
        '  --out-dir <path>     Output directory for screenshots (default: build/ui-snapshots/<run-id>/)',
        '  --timeout-ms <ms>    Playwright action timeout (default: 30000)',
        '  --headed             Run with a visible browser window (default: headless)',
        '',
      ].join('\n'),
    );
    return;
  }

  const baseUrl = normalizeBaseUrl(options.baseUrl);
  const outDir = path.resolve(options.outDir);
  await mkdir(outDir, { recursive: true });

  const browser = await chromium.launch({ headless: !options.headed });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    colorScheme: 'dark',
    reducedMotion: 'reduce',
  });
  await context.addInitScript({
    content: `
      (function () {
        try {
          var namespace = 'protoInfo.v1';
          var protocols = [
            'hotp',
            'totp',
            'ocra',
            'emv',
            'fido2',
            'eudi-openid4vp',
            'eudi-iso-18013-5',
            'eudi-siopv2',
          ];
          protocols.forEach(function (protocol) {
            localStorage.setItem(namespace + '.seen.' + protocol, 'true');
          });
        } catch (e) {
          // ignore localStorage issues (e.g., disabled storage in hardened browsers)
        }
      })();
    `,
  });

  const page = await context.newPage();
  page.setDefaultTimeout(options.timeoutMs);

  const manifest = {
    baseUrl,
    startedAt: new Date().toISOString(),
    screenshots: [],
    errors: [],
    bootstrap: {},
    notes: [
      'All screenshots are captured headless-by-default using Playwright Chromium.',
      'Animations/transitions are force-disabled via injected CSS to reduce diff noise.',
      'Interactive states rely on sample presets and seed controls (no manual data entry).',
    ],
  };

  try {
    await page.goto(uiConsoleUrl(baseUrl), { waitUntil: 'networkidle' });
    await page.waitForSelector('[data-testid="operator-protocol-tabs"]', { state: 'attached' });

    const csrfToken = await page
      .locator('input[name="_csrf"]')
      .first()
      .inputValue()
      .catch(() => '');
    manifest.bootstrap = {
      seededAt: new Date().toISOString(),
      ...await seedCanonicalData(page, baseUrl, csrfToken, options.timeoutMs, manifest),
    };

    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForSelector('[data-testid="operator-protocol-tabs"]', { state: 'attached' });

    await page.addStyleTag({
      content: `
        *, *::before, *::after {
          animation-duration: 0s !important;
          animation-delay: 0s !important;
          transition-duration: 0s !important;
          transition-delay: 0s !important;
          scroll-behavior: auto !important;
        }
      `,
    });

    const protocols = [
      'hotp',
      'totp',
      'ocra',
      'emv',
      'fido2',
      'eudi-openid4vp',
      'eudi-iso-18013-5',
      'eudi-siopv2',
    ];

    for (const protocol of protocols) {
      await clickProtocolTab(page, protocol);
      const baseName = `ui-console--${protocol}`;
      const filePath = await capture(page, outDir, baseName);
      manifest.screenshots.push({ protocol, state: 'default', file: filePath });

      if (protocol === 'ocra') {
        if (await clickAndWaitAriaSelected(page, 'ocra-mode-select-evaluate')) {
          const evaluatePath = await capture(page, outDir, `${baseName}--evaluate`);
          manifest.screenshots.push({ protocol, state: 'evaluate', file: evaluatePath });
        }
        if (await clickAndWaitAriaSelected(page, 'ocra-mode-select-replay')) {
          const replayPath = await capture(page, outDir, `${baseName}--replay`);
          manifest.screenshots.push({ protocol, state: 'replay', file: replayPath });
        }

        await captureOcraInteractive(manifest, page, outDir, baseName);
        continue;
      }

      const tabPairs = {
        hotp: ['hotp-panel-tab-evaluate', 'hotp-panel-tab-replay'],
        totp: ['totp-panel-tab-evaluate', 'totp-panel-tab-replay'],
        fido2: ['fido2-panel-tab-evaluate', 'fido2-panel-tab-replay'],
        'eudi-openid4vp': ['eudiw-panel-tab-evaluate', 'eudiw-panel-tab-replay'],
        emv: ['emv-console-tab-evaluate', 'emv-console-tab-replay'],
      };

      if (tabPairs[protocol]) {
        const [evaluateTestId, replayTestId] = tabPairs[protocol];
        if (await clickAndWaitAriaSelected(page, evaluateTestId)) {
          const evaluatePath = await capture(page, outDir, `${baseName}--evaluate`);
          manifest.screenshots.push({ protocol, state: 'evaluate', file: evaluatePath });
        }
        if (await clickAndWaitAriaSelected(page, replayTestId)) {
          const replayPath = await capture(page, outDir, `${baseName}--replay`);
          manifest.screenshots.push({ protocol, state: 'replay', file: replayPath });
        }
      }

      if (protocol === 'hotp') {
        await captureHotpInteractive(manifest, page, outDir, baseName);
      } else if (protocol === 'totp') {
        await captureTotpInteractive(manifest, page, outDir, baseName);
      } else if (protocol === 'emv') {
        await captureEmvInteractive(manifest, page, outDir, baseName);
      } else if (protocol === 'fido2') {
        await captureFido2Interactive(manifest, page, outDir, baseName);
      } else if (protocol === 'eudi-openid4vp') {
        await captureEudiwInteractive(manifest, page, outDir, baseName);
      }
    }
  } finally {
    await context.close();
    await browser.close();
  }

  await writeFile(path.join(outDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf-8');
  process.stdout.write(`Wrote ${manifest.screenshots.length} screenshots to ${outDir}\n`);
}

run().catch((error) => {
  process.stderr.write(String(error?.stack || error) + '\n');
  process.exitCode = 1;
});
