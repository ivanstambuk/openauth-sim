const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const scriptSource = readFileSync(
  path.resolve(__dirname, '../../../main/resources/static/ui/emv/console.js'),
  'utf8',
);

function createInput(id, value = '') {
  const attributes = new Map();
  return {
    id,
    tagName: 'INPUT',
    value,
    textContent: '',
    checked: false,
    listeners: {},
    setAttribute(name, val) {
      attributes.set(name, String(val));
    },
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
    removeAttribute(name) {
      attributes.delete(name);
    },
    addEventListener(type, handler) {
      if (!this.listeners[type]) {
        this.listeners[type] = [];
      }
      this.listeners[type].push(handler);
    },
    dispatchEvent(type) {
      const handlers = this.listeners[type] || [];
      handlers.forEach((handler) => handler({ target: this }));
    },
  };
}

function createTextarea(id, value = '') {
  return { ...createInput(id, value), tagName: 'TEXTAREA' };
}

function createButton() {
  const attributes = new Map([
    ['disabled', 'disabled'],
  ]);
  const listeners = {};
  return {
    tagName: 'BUTTON',
    setAttribute(name, value) {
      attributes.set(name, String(value));
    },
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
    removeAttribute(name) {
      attributes.delete(name);
    },
    addEventListener(type, handler) {
      if (!listeners[type]) {
        listeners[type] = [];
      }
      listeners[type].push(handler);
    },
    click() {
      (listeners.click || []).forEach((handler) => handler({ preventDefault() {} }));
    },
  };
}

function createSelect() {
  const attributes = new Map();
  const listeners = {};
  const select = {
    tagName: 'SELECT',
    value: '',
    options: [{ value: '', textContent: 'Select a preset credential' }],
    setAttribute(name, value) {
      attributes.set(name, String(value));
    },
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
    removeAttribute(name) {
      attributes.delete(name);
    },
    addEventListener(type, handler) {
      if (!listeners[type]) {
        listeners[type] = [];
      }
      listeners[type].push(handler);
    },
    dispatchEvent(type) {
      (listeners[type] || []).forEach((handler) => handler({ target: select }));
    },
    appendChild(option) {
      select.options.push(option);
      return option;
    },
    remove(index) {
      select.options.splice(index, 1);
    },
  };
  return select;
}

function createPreviewBody() {
  const state = { children: [] };
  return {
    appendChild(node) {
      state.children.push(node);
      return node;
    },
    removeChild(node) {
      state.children = state.children.filter((child) => child !== node);
    },
    get firstChild() {
      return state.children.length > 0 ? state.children[0] : null;
    },
  };
}

function createDomElement(tagName) {
  const attributes = new Map();
  const children = [];
  return {
    tagName: tagName.toUpperCase(),
    className: '',
    textContent: '',
    dataset: {},
    appendChild(node) {
      children.push(node);
      return node;
    },
    removeChild(node) {
      const index = children.indexOf(node);
      if (index >= 0) {
        children.splice(index, 1);
      }
    },
    setAttribute(name, value) {
      attributes.set(name, String(value));
    },
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
    removeAttribute(name) {
      attributes.delete(name);
    },
  };
}

function createEnvironment({ verboseEnabled }) {
  const storedSelect = createSelect();
  const masterKeyInput = createInput('emvMasterKey', 'A1B2');
  const atcInput = createInput('emvAtc', '01');
  const branchFactorInput = createInput('emvBranchFactor', '2');
  const heightInput = createInput('emvHeight', '1');
  const ivInput = createTextarea('emvIv', 'AA');
  const cdol1Input = createTextarea('emvCdol1', 'BB');
  const ipbInput = createTextarea('emvIpb', 'CC');
  const iccTemplateInput = createTextarea('emvIccTemplate', 'DD');
  const issuerApplicationDataInput = createTextarea('emvIssuerApplicationData', 'EE');
  const challengeInput = createInput('emvChallenge', '1234');
  const referenceInput = createInput('emvReference', '5678');
  const amountInput = createInput('emvAmount', '999');
  const terminalInput = createTextarea('emvTerminalData', 'ABC');
  const iccOverrideInput = createTextarea('emvIccOverride', 'DEF');
  const iccResolvedInput = createTextarea('emvIccResolved', 'FED');
  const csrfInput = createInput('_csrf', 'token');
  const storedSubmitButton = createButton();
  const storedHint = { textContent: '' };

  const modeIdentify = createInput('emvModeIdentify', 'IDENTIFY');
  modeIdentify.value = 'IDENTIFY';
  modeIdentify.checked = true;
  const modeRespond = createInput('emvModeRespond', 'RESPOND');
  modeRespond.value = 'RESPOND';
  const modeSign = createInput('emvModeSign', 'SIGN');
  modeSign.value = 'SIGN';

  const resultStatus = { textContent: '—' };
  const previewBody = createPreviewBody();
  const previewContainer = {
    setAttribute() {},
    removeAttribute() {},
    querySelector(selector) {
      if (selector === '[data-result-preview-body]') {
        return previewBody;
      }
      return null;
    },
  };

  const resultPanel = {
    hidden: true,
    setAttribute() {},
    removeAttribute() {},
    querySelector(selector) {
      if (selector === '[data-testid=\'emv-status\']') {
        return resultStatus;
      }
      if (selector === '[data-result-preview]') {
        return previewContainer;
      }
      return null;
    },
  };

  const evaluatePanel = {
    setAttribute() {},
    removeAttribute() {},
  };
  const replayPanel = {
    setAttribute() {},
    removeAttribute() {},
  };

  const seedScript = {
    textContent: '[]',
    parentNode: { removeChild() {} },
  };

  const documentStub = {
    querySelector(selector) {
      if (selector === '[data-protocol-panel="emv"]') {
        return rootPanel;
      }
      if (selector === '[data-testid="emv-form"]') {
        return form;
      }
      if (selector === '[data-testid="emv-result-card"]') {
        return resultPanel;
      }
      return null;
    },
    getElementById(id) {
      if (id === 'emv-seed-definitions') {
        return seedScript;
      }
      return null;
    },
    createElement(tagName) {
      if (tagName.toLowerCase() === 'option') {
        return { value: '', textContent: '' };
      }
      return createDomElement(tagName);
    },
  };

  const form = {
    getAttribute(name) {
      if (name === 'data-evaluate-endpoint') {
        return '/api/v1/emv/cap/evaluate';
      }
      if (name === 'data-stored-endpoint') {
        return '/api/v1/emv/cap/evaluate';
      }
      if (name === 'data-credentials-endpoint' || name === 'data-seed-endpoint') {
        return '';
      }
      return null;
    },
    querySelector(selector) {
      switch (selector) {
        case 'input[name="_csrf"]':
          return csrfInput;
        case '#emvStoredCredentialId':
          return storedSelect;
        case '#emvMasterKey':
          return masterKeyInput;
        case '#emvAtc':
          return atcInput;
        case '#emvBranchFactor':
          return branchFactorInput;
        case '#emvHeight':
          return heightInput;
        case '#emvIv':
          return ivInput;
        case '#emvCdol1':
          return cdol1Input;
        case '#emvIpb':
          return ipbInput;
        case '#emvIccTemplate':
          return iccTemplateInput;
        case '#emvIssuerApplicationData':
          return issuerApplicationDataInput;
        case '#emvChallenge':
          return challengeInput;
        case '#emvReference':
          return referenceInput;
        case '#emvAmount':
          return amountInput;
        case '#emvTerminalData':
          return terminalInput;
        case '#emvIccOverride':
          return iccOverrideInput;
        case '#emvIccResolved':
          return iccResolvedInput;
        case '[data-testid="emv-seed-actions"]':
          return null;
        case '[data-testid="emv-stored-submit"]':
          return storedSubmitButton;
        case '[data-testid="emv-stored-empty"]':
          return storedHint;
        default:
          return null;
      }
    },
    querySelectorAll(selector) {
      if (selector === 'input[name="mode"]') {
        return [modeIdentify, modeRespond, modeSign];
      }
      return [];
    },
    addEventListener() {},
  };

  const rootPanel = {
    querySelector(selector) {
      if (selector === '[data-testid="emv-console-tabs"]') {
        return null;
      }
      if (selector === '[data-emv-panel="evaluate"]') {
        return evaluatePanel;
      }
      if (selector === '[data-emv-panel="replay"]') {
        return replayPanel;
      }
      if (selector === '[data-testid="emv-result-card"]') {
        return resultPanel;
      }
      return null;
    },
  };

  const fetchCalls = [];
  const fetchStub = (url, options = {}) => {
    if (!options.method || options.method === 'GET') {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });
    }
    const body = options.body ? JSON.parse(options.body) : null;
    fetchCalls.push({ url, options, body });
    return Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ telemetry: { status: 'success', fields: {} } }),
    });
  };

  const sandbox = { window: {} };
  const globalObject = sandbox.window;
  globalObject.window = globalObject;
  globalObject.document = documentStub;
  globalObject.fetch = fetchStub;
  globalObject.console = console;
  globalObject.Promise = Promise;
  globalObject.ResultCard = null;
  globalObject.VerboseTraceConsole = {
    isEnabled() {
      return verboseEnabled;
    },
    beginRequest() {},
    clearTrace() {},
    handleResponse() {},
    handleError() {},
  };

  const hooksContainer = {};
  globalObject.EmvConsoleTestHooks = {
    attach(hooks) {
      hooksContainer.hooks = hooks;
    },
  };

  vm.runInNewContext(scriptSource, sandbox, { filename: 'emv-console.js' });

  const hooks = hooksContainer.hooks;
  if (!hooks) {
    throw new Error('Console hooks were not attached');
  }

  return {
    hooks,
    fetchCalls,
    inputs: {
      masterKey: masterKeyInput,
      atc: atcInput,
    },
    storedHint,
  };
}

function setStoredSelection(env, credentialId) {
  env.hooks.storedSelect.value = credentialId;
  env.hooks.updateStoredControls();
}

async function invokeStoredSubmit(env) {
  await env.hooks.handleStoredSubmit();
  await Promise.resolve();
}

test('stored submission posts credential ID to stored endpoint when verbose enabled', async () => {
  const env = createEnvironment({ verboseEnabled: true });
  env.hooks.storedState.resetBaseline('emv-123');
  setStoredSelection(env, 'emv-123');
  await invokeStoredSubmit(env);
  assert.equal(env.fetchCalls.length, 1);
  const call = env.fetchCalls[0];
  assert.equal(call.url, '/api/v1/emv/cap/evaluate');
  assert.deepEqual(call.body, { credentialId: 'emv-123', includeTrace: true });
  assert.equal(env.hooks.storedState.lastSubmission(), 'stored');
});

test('overriding stored fields triggers inline fallback with inline payload', async () => {
  const env = createEnvironment({ verboseEnabled: false });
  env.hooks.storedState.resetBaseline('emv-inline');
  setStoredSelection(env, 'emv-inline');
  env.inputs.masterKey.value = 'DEADBEEF';
  await invokeStoredSubmit(env);
  assert.equal(env.fetchCalls.length, 1);
  const call = env.fetchCalls[0];
  assert.equal(call.url, '/api/v1/emv/cap/evaluate');
  assert.equal(call.body.credentialId, undefined);
  assert.equal(call.body.masterKey, 'DEADBEEF');
  assert.equal(call.body.includeTrace, false);
  assert.equal(call.body.mode, 'IDENTIFY');
  assert.equal(env.hooks.storedState.lastSubmission(), 'inline');
});

test('stored submission honours verbose toggle when disabled', async () => {
  const env = createEnvironment({ verboseEnabled: false });
  env.hooks.storedState.resetBaseline('emv-456');
  setStoredSelection(env, 'emv-456');
  await invokeStoredSubmit(env);
  assert.equal(env.fetchCalls.length, 1);
  const call = env.fetchCalls[0];
  assert.equal(call.body.includeTrace, false);
});
