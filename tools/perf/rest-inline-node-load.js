#!/usr/bin/env node

/* eslint-disable no-console */

/**
 * REST inline evaluation load harness (Node).
 *
 * Benchmarks multiple inline evaluation endpoints:
 *   - HOTP: POST /api/v1/hotp/evaluate/inline
 *   - TOTP: POST /api/v1/totp/evaluate/inline
 *   - OCRA: POST /api/v1/ocra/evaluate
 *   - EMV/CAP: POST /api/v1/emv/cap/evaluate
 *   - WebAuthn (FIDO2): POST /api/v1/webauthn/evaluate/inline
 *   - EUDIW OpenID4VP wallet simulation: POST /api/v1/eudiw/openid4vp/wallet/simulate
 *   - EUDIW OpenID4VP validation: POST /api/v1/eudiw/openid4vp/validate
 *
 * Each scenario reuses known-good inline parameters from existing tests or
 * fixtures and reports throughput + latency percentiles. The process exits
 * non-zero if any scenario fails its thresholds.
 *
 * Examples (from repository root):
 *
 *   # Run all scenarios with shared thresholds
 *   node tools/perf/rest-inline-node-load.js \
 *     --baseUrl http://localhost:8080 \
 *     --durationSeconds 30 \
 *     --concurrency 32 \
 *     --targetTps 500 \
 *     --maxP95 50 \
 *     --maxP99 100
 *
 *   # Run a single scenario
 *   node tools/perf/rest-inline-node-load.js \
 *     --baseUrl http://localhost:8080 \
 *     --scenario hotp_inline \
 *     --durationSeconds 30 \
 *     --concurrency 32 \
 *     --targetTps 500
 */

const http = require('http');
const https = require('https');
const { URL } = require('url');
const { performance } = require('perf_hooks');
const fs = require('fs');

function parseArgs(argv) {
  const args = {
    baseUrl: 'http://localhost:8080',
    durationSeconds: 30,
    concurrency: 32,
    targetTps: 500,
    maxP95: undefined,
    maxP99: undefined,
    scenario: 'all',
    baselineFile: 'docs/3-reference/rest-inline-baseline-2025-11-18.json',
    baselineTolerance: 0.2,
  };

  for (let i = 2; i < argv.length; i += 1) {
    const key = argv[i];
    const next = argv[i + 1];
    if (!key.startsWith('--')) {
      continue;
    }
    switch (key) {
      case '--baseUrl':
        args.baseUrl = next;
        i += 1;
        break;
      case '--durationSeconds':
        args.durationSeconds = Number(next);
        i += 1;
        break;
      case '--concurrency':
        args.concurrency = Number(next);
        i += 1;
        break;
      case '--targetTps':
        args.targetTps = Number(next);
        i += 1;
        break;
      case '--maxP95':
        args.maxP95 = Number(next);
        i += 1;
        break;
      case '--maxP99':
        args.maxP99 = Number(next);
        i += 1;
        break;
      case '--scenario':
        args.scenario = next || 'all';
        i += 1;
        break;
      case '--baselineFile':
        args.baselineFile = next;
        i += 1;
        break;
      case '--baselineTolerance':
        args.baselineTolerance = Number(next);
        i += 1;
        break;
      case '--help':
      case '-h':
        printHelpAndExit();
        break;
      default:
        // ignore unknown flags
        break;
    }
  }

  return args;
}

function printHelpAndExit() {
  console.log(
    [
      'Usage: node tools/perf/rest-inline-node-load.js [options]',
      '',
      'Options:',
      '  --baseUrl <url>          Base URL for REST API (default: http://localhost:8080)',
      '  --scenario <name>        Scenario id (hotp_inline, totp_inline, ocra_evaluate, emv_cap_inline, webauthn_inline, eudiw_wallet_simulate, eudiw_validate, all)',
      '  --durationSeconds <n>    Duration per scenario in seconds (default: 30)',
      '  --concurrency <n>        Number of concurrent in-flight requests (default: 32)',
      '  --targetTps <n>          Minimum acceptable throughput in requests/second (default: 500)',
      '  --maxP95 <ms>            Optional maximum allowed p95 latency in milliseconds',
      '  --maxP99 <ms>            Optional maximum allowed p99 latency in milliseconds',
      '  --baselineFile <path>    Optional JSON file with baseline summaries (array with scenarioId, throughputTps, p95Ms, p99Ms)',
      '  --baselineTolerance <f>  Allowed relative deviation for baseline comparison (default: 0.2 = 20%)',
      '  --help, -h               Show this help message',
      '',
      'Example:',
      '  node tools/perf/rest-inline-node-load.js \\',
      '    --baseUrl http://localhost:8080 \\',
      '    --durationSeconds 30 \\',
      '    --concurrency 32 \\',
      '    --targetTps 500 \\',
      '    --maxP95 50 \\',
      '    --maxP99 100',
    ].join('\n'),
  );
  process.exit(0);
}

function percentile(sortedArray, p) {
  if (!sortedArray.length) {
    return 0;
  }
  const index = Math.min(
    sortedArray.length - 1,
    Math.max(0, Math.ceil(p * sortedArray.length) - 1),
  );
  return sortedArray[index];
}

function buildHotpInlinePayload() {
  // Based on HotpEvaluationServiceTest inline example.
  return {
    sharedSecretHex: '3132333435363738393031323334353637383930',
    sharedSecretBase32: null,
    algorithm: 'SHA1',
    digits: 6,
    window: { backward: 0, forward: 0 },
    counter: 10,
    metadata: {},
    verbose: false,
  };
}

function buildTotpInlinePayload() {
  // Based on TotpEvaluationServiceTest inline example (generation, not validation).
  return {
    sharedSecretHex: null,
    sharedSecretBase32: 'MFRGGZDFMZTWQ2LK',
    algorithm: 'SHA1',
    digits: 6,
    stepSeconds: 30,
    window: { backward: 0, forward: 0 },
    timestamp: null,
    timestampOverride: null,
    otp: '',
    metadata: {},
    verbose: false,
  };
}

function buildOcraEvaluatePayload() {
  // Based on OcraEvaluationServiceTest inline Base32 example.
  return {
    credentialId: null,
    suite: 'OCRA-1:HOTP-SHA1-6:QN08',
    sharedSecretHex: null,
    sharedSecretBase32: 'GEZDGNBVGY3TQOJQGEZDGNBVGY======',
    challenge: '12345678',
    sessionHex: null,
    clientChallenge: null,
    serverChallenge: null,
    pinHashHex: null,
    timestampHex: null,
    counter: null,
    window: { backward: 0, forward: 0 },
    verbose: false,
  };
}

function buildEmvCapInlinePayload() {
  return {
    // Based on docs/test-vectors/emv-cap/respond-baseline.json (RESPOND flow).
    mode: 'RESPOND',
    masterKey: '0123456789ABCDEF0123456789ABCDEF',
    atc: '00B5',
    branchFactor: 4,
    height: 8,
    iv: '00000000000000000000000000000000',
    cdol1: '9F02069F03069F1A0295055F2A029A039C019F3704',
    issuerProprietaryBitmap: '00001F00000000000FFFFF00000000008000',
    previewWindow: {
      backward: 0,
      forward: 0,
    },
    customerInputs: {
      challenge: '1122334455',
      reference: '',
      amount: '',
    },
    iccDataTemplate: '1000xxxxA50006040000',
    issuerApplicationData: '06770A03A48000',
    includeTrace: false,
  };
}

function buildWebauthnInlinePayload() {
  // Based on docs/webauthn_assertion_vectors.json vector ES256:uv0_up1.
  return {
    credentialName: 'inline',
    credentialId: '0RslpdQUn7oso12_9jP3Dm7XDJrDo4xhVNGKtX2vqnE',
    relyingPartyId: 'example.com',
    origin: 'https://example.com',
    expectedType: 'webauthn.get',
    algorithm: 'ES256',
    signatureCounter: 0,
    userVerificationRequired: false,
    challenge: 'FuQPVg8SQt9QMm_YSPHm6df9Rgq5AHJ2gNSE4YPlQRw',
    privateKey: JSON.stringify({
      kty: 'EC',
      crv: 'P-256',
      x: 'GX7yBx_D2NEMdL469MfSqu9-UkLv_I11DTRH0Ht1Hxw',
      y: '5zD4EKQS6qI4qaJqvVq7I1-J_H-S4fqxEAjqumW3tyY',
      d: '2n3OH6uNKXTckYr_gWLwNtsiOW64FWO9PFe0CMmJOsQ',
    }),
    verbose: false,
  };
}

function buildEudiwWalletSimulatePayload() {
  // Based on Oid4vpRestContractTest wallet simulation example and Feature 006 fixtures.
  return {
    requestId: 'OID4VP-REQ-LOADTEST',
    walletPreset: 'pid-haip-baseline',
    profile: 'HAIP',
    trustedAuthorityPolicy: 'aki:s9tIpP7qrS9=',
  };
}

function buildEudiwValidatePayload() {
  // Matches telemetry snapshot for successful validation of pid-haip-baseline.
  return {
    requestId: 'OID4VP-REQ-LOADTEST',
    presetId: 'pid-haip-baseline',
    trustedAuthorityPolicy: 'aki:s9tIpP7qrS9=',
    profile: 'HAIP',
  };
}

const SCENARIOS = {
  hotp_inline: {
    id: 'hotp_inline',
    description: 'HOTP inline evaluation',
    path: '/api/v1/hotp/evaluate/inline',
    method: 'POST',
    buildPayload: buildHotpInlinePayload,
  },
  totp_inline: {
    id: 'totp_inline',
    description: 'TOTP inline evaluation',
    path: '/api/v1/totp/evaluate/inline',
    method: 'POST',
    buildPayload: buildTotpInlinePayload,
  },
  ocra_evaluate: {
    id: 'ocra_evaluate',
    description: 'OCRA inline evaluation',
    path: '/api/v1/ocra/evaluate',
    method: 'POST',
    buildPayload: buildOcraEvaluatePayload,
  },
  emv_cap_inline: {
    id: 'emv_cap_inline',
    description: 'EMV/CAP inline evaluation',
    path: '/api/v1/emv/cap/evaluate',
    method: 'POST',
    buildPayload: buildEmvCapInlinePayload,
  },
  webauthn_inline: {
    id: 'webauthn_inline',
    description: 'WebAuthn inline evaluation',
    path: '/api/v1/webauthn/evaluate/inline',
    method: 'POST',
    buildPayload: buildWebauthnInlinePayload,
  },
  eudiw_wallet_simulate: {
    id: 'eudiw_wallet_simulate',
    description: 'EUDIW OpenID4VP wallet simulation',
    path: '/api/v1/eudiw/openid4vp/wallet/simulate',
    method: 'POST',
    buildPayload: buildEudiwWalletSimulatePayload,
  },
  eudiw_validate: {
    id: 'eudiw_validate',
    description: 'EUDIW OpenID4VP presentation validation',
    path: '/api/v1/eudiw/openid4vp/validate',
    method: 'POST',
    buildPayload: buildEudiwValidatePayload,
  },
};

function runScenario(config, scenario) {
  return new Promise((resolve) => {
    const endpoint = new URL(scenario.path, config.baseUrl);
    const client = endpoint.protocol === 'https:' ? https : http;

    const payload = JSON.stringify(scenario.buildPayload());
    const requestOptions = {
      protocol: endpoint.protocol,
      hostname: endpoint.hostname,
      port: endpoint.port || (endpoint.protocol === 'https:' ? 443 : 80),
      path: endpoint.pathname + endpoint.search,
      method: scenario.method || 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload),
      },
    };

    const startTime = performance.now();
    const endTime = startTime + config.durationSeconds * 1000;
    const latencies = [];
    let completed = 0;
    let succeeded = 0;
    let failed = 0;
    let inFlight = 0;
    let finished = false;

    function maybeFinish() {
      if (finished) {
        return;
      }
      if (inFlight === 0 && performance.now() >= endTime) {
        finished = true;
        const end = performance.now();
        const durationSeconds = Math.max(0.000001, (end - startTime) / 1000);

        const sortedLatencies = latencies.slice().sort((a, b) => a - b);
        const p50 = percentile(sortedLatencies, 0.5);
        const p90 = percentile(sortedLatencies, 0.9);
        const p95 = percentile(sortedLatencies, 0.95);
        const p99 = percentile(sortedLatencies, 0.99);
        const max = sortedLatencies.length ? sortedLatencies[sortedLatencies.length - 1] : 0;
        const throughput = completed / durationSeconds;

        const summary = {
          scenarioId: scenario.id,
          description: scenario.description,
          path: scenario.path,
          baseUrl: config.baseUrl,
          durationSeconds: config.durationSeconds,
          concurrency: config.concurrency,
          targetTps: config.targetTps,
          completed,
          succeeded,
          failed,
          throughputTps: throughput,
          p50Ms: p50,
          p90Ms: p90,
          p95Ms: p95,
          p99Ms: p99,
          maxMs: max,
        };

        const passThroughput = throughput >= config.targetTps;
        const passP95 =
                typeof config.maxP95 !== 'number' || p95 <= config.maxP95;
        const passP99 =
                typeof config.maxP99 !== 'number' || p99 <= config.maxP99;

        summary.passThroughput = passThroughput;
        summary.passP95 = passP95;
        summary.passP99 = passP99;

        resolve(summary);
      }
    }

    function sendOnce() {
      if (performance.now() >= endTime) {
        // Stop issuing new requests, wait for in-flight to complete.
        maybeFinish();
        return;
      }

      inFlight += 1;
      const requestStart = performance.now();

      const req = client.request(requestOptions, (res) => {
        res.on('data', () => {
          // drain
        });
        res.on('end', () => {
          const requestEnd = performance.now();
          const latencyMs = requestEnd - requestStart;
          latencies.push(latencyMs);
          completed += 1;
          if (res.statusCode >= 200 && res.statusCode < 300) {
            succeeded += 1;
          } else {
            failed += 1;
          }

          inFlight -= 1;
          if (!finished) {
            sendOnce();
            maybeFinish();
          }
        });
      });

      req.on('error', (error) => {
        const requestEnd = performance.now();
        const latencyMs = requestEnd - requestStart;
        latencies.push(latencyMs);
        completed += 1;
        failed += 1;
        inFlight -= 1;
        console.error(`[${scenario.id}] request error:`, error.message);
        if (!finished) {
          sendOnce();
          maybeFinish();
        }
      });

      req.write(payload);
      req.end();
    }

    for (let i = 0; i < config.concurrency; i += 1) {
      sendOnce();
    }
  });
}

async function main() {
  const config = parseArgs(process.argv);

  let baselineByScenario = null;
  if (config.baselineFile) {
    try {
      const text = fs.readFileSync(config.baselineFile, 'utf8');
      const parsed = JSON.parse(text);
      if (Array.isArray(parsed)) {
        baselineByScenario = {};
        parsed.forEach((entry) => {
          if (entry && typeof entry.scenarioId === 'string') {
            baselineByScenario[entry.scenarioId] = entry;
          }
        });
      } else {
        console.error(
          `Baseline file ${config.baselineFile} does not contain a JSON array; ignoring baseline.`,
        );
      }
    } catch (error) {
      console.error(
        `Failed to read baseline file ${config.baselineFile}:`,
        error.message,
      );
    }
  }

  const allScenarioIds = Object.keys(SCENARIOS);
  const scenarioIds =
    config.scenario === 'all'
      ? allScenarioIds
      : allScenarioIds.includes(config.scenario)
        ? [config.scenario]
        : [];

  if (scenarioIds.length === 0) {
    console.error(
      `Unknown scenario "${config.scenario}". Supported: ${allScenarioIds.join(', ')}, all`,
    );
    process.exit(1);
  }

  const summaries = [];
  let exitCode = 0;

  for (const id of scenarioIds) {
    const scenario = SCENARIOS[id];
    console.log(`Running scenario: ${id} (${scenario.description})`);
    // eslint-disable-next-line no-await-in-loop
    const summary = await runScenario(config, scenario);
    const failures = [];

    if (baselineByScenario && baselineByScenario[scenario.id]) {
      const baseline = baselineByScenario[scenario.id];
      const tol = typeof config.baselineTolerance === 'number'
        && Number.isFinite(config.baselineTolerance)
        ? Math.max(0, config.baselineTolerance)
        : 0.2;

      const baseThroughput = Number(baseline.throughputTps);
      const baseP95 = Number(baseline.p95Ms);
      const baseP99 = Number(baseline.p99Ms);

      if (Number.isFinite(baseThroughput) && baseThroughput > 0) {
        summary.baselineThroughputTps = baseThroughput;
        summary.throughputRatio = summary.throughputTps / baseThroughput;
        summary.passBaselineThroughput = summary.throughputRatio >= 1 - tol;
        if (!summary.passBaselineThroughput) {
          failures.push('baseline-throughput');
        }
      }

      if (Number.isFinite(baseP95) && baseP95 > 0) {
        summary.baselineP95Ms = baseP95;
        summary.p95Ratio = summary.p95Ms / baseP95;
        summary.passBaselineP95 = summary.p95Ratio <= 1 + tol;
        if (!summary.passBaselineP95) {
          failures.push('baseline-p95');
        }
      }

      if (Number.isFinite(baseP99) && baseP99 > 0) {
        summary.baselineP99Ms = baseP99;
        summary.p99Ratio = summary.p99Ms / baseP99;
        summary.passBaselineP99 = summary.p99Ratio <= 1 + tol;
        if (!summary.passBaselineP99) {
          failures.push('baseline-p99');
        }
      }
    }

    summaries.push(summary);

    if (summary.completed === 0 || summary.succeeded === 0) {
      failures.push('no-success');
    }
    if (!summary.passThroughput) {
      failures.push('throughput');
    }
    if (!summary.passP95) {
      failures.push('p95');
    }
    if (!summary.passP99) {
      failures.push('p99');
    }

    if (failures.length > 0) {
      exitCode = 1;
      console.error(
        `Scenario ${id} failed thresholds: ${failures.join(', ')}`,
      );
    }
  }

  console.log('--- REST inline load summary ---');
  console.log(JSON.stringify(summaries, null, 2));

  if (exitCode !== 0) {
    console.error('One or more scenarios failed thresholds.');
  }
  process.exit(exitCode);
}

if (require.main === module) {
  // eslint-disable-next-line promise/catch-or-return
  main().catch((error) => {
    console.error('Fatal error:', error);
    process.exit(1);
  });
}
