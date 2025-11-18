import http from 'k6/http';
import { check } from 'k6';

/**
 * EMV/CAP inline evaluation load test.
 *
 * - Targets REST endpoint: POST /api/v1/emv/cap/evaluate
 * - Scenario: inline-only requests (no stored credentials).
 * - Goal: sustain >=500 requests/second while keeping latency within bounds.
 *
 * Usage:
 *   1. Start the REST API (see tools/run-rest-api.init.gradle.kts).
 *   2. Run:
 *        BASE_URL=http://localhost:8080 k6 run tools/perf/emv-cap-inline-k6.js
 *
 * Adjust BASE_URL to match the running simulator instance.
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    emv_inline: {
      executor: 'constant-arrival-rate',
      rate: 500, // target arrivals per second
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    // Latency targets (tune as needed for your baseline).
    'http_req_duration{scenario:emv_inline}': ['p(95)<50', 'p(99)<100'],
    // Effective request rate should stay at or above the 500 TPS target.
    'http_reqs{scenario:emv_inline}': ['rate>=500'],
    // Ensure most requests succeed; adjust if you want stricter guarantees.
    'http_req_failed{scenario:emv_inline}': ['rate==0'],
  },
};

export default function () {
  const url = `${BASE_URL}/api/v1/emv/cap/evaluate`;

  // Request shape mirrors docs/test-vectors/emv-cap/respond-baseline.json (inline RESPOND flow).
  const payload = JSON.stringify({
    mode: 'RESPOND',
    masterKey: '0123456789ABCDEF0123456789ABCDEF',
    atc: '00B5',
    branchFactor: 4,
    height: 8,
    iv: '00000000000000000000000000000000',
    cdol1: '9F02069F03069F1A0295055F2A029A039C019F3704',
    issuerProprietaryBitmap: '00001F00000000000FFFFF00000000008000',
    previewWindow: { backward: 0, forward: 0 },
    customerInputs: {
      challenge: '1122334455',
      reference: '',
      amount: '',
    },
    transactionData: {
      terminal: '0000000000000000000000000000800000000000000000000011223344',
      icc: '100000B5A50006040000',
    },
    iccDataTemplate: '1000xxxxA50006040000',
    issuerApplicationData: '06770A03A48000',
    includeTrace: false,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      scenario: 'emv_inline',
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}

