const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const SANITIZED_SUMMARIES = [
  {
    id: 'emv-123',
    label: 'CAP Identify baseline',
    mode: 'IDENTIFY',
    masterKeySha256: 'sha256:SANITIZED-IDENTIFY',
    masterKeyHexLength: 32,
    defaultAtc: '00B4',
    branchFactor: 4,
    height: 8,
    iv: '0001020304050607',
    cdol1HexLength: 64,
    issuerProprietaryBitmapHexLength: 32,
    iccDataTemplateHexLength: 40,
    issuerApplicationDataHexLength: 48,
    defaults: {
      challenge: '',
      reference: '',
      amount: '',
    },
    transaction: {
      iccResolved: '',
    },
    metadata: {
      presetLabel: 'CAP Identify baseline',
    },
  },
];

async function flushMicrotasks() {
  await Promise.resolve();
  await Promise.resolve();
}

function createMaskField() {
  const attributes = new Map();
  const style = {};
  const valueNode = { textContent: '' };
  return {
    style,
    valueNode,
    setAttribute(name, value) {
      attributes.set(name, String(value));
    },
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
    removeAttribute(name) {
      attributes.delete(name);
    },
    querySelector(selector) {
      if (selector === '[data-mask-value]') {
        return valueNode;
      }
      return null;
    },
  };
}

function createHintNode() {
  const attributes = new Map();
  return {
    textContent: '',
    setAttribute(name, value) {
      attributes.set(name, String(value));
    },
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
  };
}

const scriptSource = readFileSync(
  path.resolve(__dirname, '../../../main/resources/static/ui/emv/console.js'),
  'utf8',
);

function createInput(id, value = '', container = null) {
  const attributes = new Map();
  const element = {
    id,
    tagName: 'INPUT',
    value,
    textContent: '',
    checked: false,
    style: {},
    disabled: false,
    listeners: {},
    setAttribute(name, val) {
      attributes.set(name, String(val));
      if (name === 'disabled') {
        this.disabled = true;
      }
    },
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
    removeAttribute(name) {
      attributes.delete(name);
      if (name === 'disabled') {
        this.disabled = false;
      }
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
    closest(selector) {
      if (!this.__container) {
        return null;
      }
      if (selector === '.field-group') {
        return this.__container;
      }
      return null;
    },
  };
  element.__container = container;
  return element;
}

function createTextarea(id, value = '', container = null) {
  const input = createInput(id, value, container);
  input.tagName = 'TEXTAREA';
  return input;
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

function createFieldGroup(id) {
  const attributes = new Map();
  const style = {};
  return {
    id,
    className: 'field-group',
    style,
    setAttribute(name, value) {
      attributes.set(name, String(value));
    },
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
    removeAttribute(name) {
      attributes.delete(name);
    },
    closest(selector) {
      if (selector === '.field-group') {
        return this;
      }
      return null;
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
  const masterKeyContainer = createFieldGroup('emv-master-key-group');
  const cdol1Container = createFieldGroup('emv-cdol1-group');
  const ipbContainer = createFieldGroup('emv-ipb-group');
  const iccTemplateContainer = createFieldGroup('emv-icc-template-group');
  const issuerApplicationDataContainer = createFieldGroup('emv-issuer-application-data-group');
  const challengeContainer = createFieldGroup('emv-challenge-group');
  const referenceContainer = createFieldGroup('emv-reference-group');
  const amountContainer = createFieldGroup('emv-amount-group');

  const masterKeyInput = createTextarea('emvMasterKey', 'A1B2', masterKeyContainer);
  const atcInput = createInput('emvAtc', '01');
  const branchFactorInput = createInput('emvBranchFactor', '2');
  const heightInput = createInput('emvHeight', '1');
  const ivInput = createTextarea('emvIv', 'AA');
  const cdol1Input = createTextarea('emvCdol1', 'BB', cdol1Container);
  const ipbInput = createTextarea('emvIpb', 'CC', ipbContainer);
  const iccTemplateInput = createTextarea('emvIccTemplate', 'DD', iccTemplateContainer);
  const issuerApplicationDataInput = createTextarea('emvIssuerApplicationData', 'EE', issuerApplicationDataContainer);
  const challengeInput = createInput('emvChallenge', '1234', challengeContainer);
  const referenceInput = createInput('emvReference', '5678', referenceContainer);
  const amountInput = createInput('emvAmount', '999', amountContainer);
  const terminalInput = createTextarea('emvTerminalData', 'ABC');
  const iccOverrideInput = createTextarea('emvIccOverride', 'DEF');
  const iccResolvedInput = createTextarea('emvIccResolved', 'FED');
  const masterKeyMaskNode = createMaskField();
  const cdol1MaskNode = createMaskField();
  const ipbMaskNode = createMaskField();
  const iccTemplateMaskNode = createMaskField();
  const issuerApplicationDataMaskNode = createMaskField();
  const csrfInput = createInput('_csrf', 'token');
  const evaluateButton = createButton();
  evaluateButton.removeAttribute('disabled');
  const storedHint = { textContent: '' };
  const customerHint = createHintNode();

  const evaluateModeInline = createInput('emvEvaluateModeInline', 'inline');
  evaluateModeInline.value = 'inline';
  evaluateModeInline.checked = true;
  const evaluateModeStored = createInput('emvEvaluateModeStored', 'stored');
  evaluateModeStored.value = 'stored';

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
  const evaluateModeToggle = {
    setAttribute() {},
    getAttribute() {
      return null;
    },
    querySelectorAll(selector) {
      if (selector === 'input[name="emvEvaluateMode"]') {
        return [evaluateModeInline, evaluateModeStored];
      }
      return [];
    },
  };
  const actionBar = {
    setAttribute() {},
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
        return '/api/v1/emv/cap/credentials';
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
        case '[data-testid="emv-master-key-mask"]':
          return masterKeyMaskNode;
        case '[data-testid="emv-cdol1-mask"]':
          return cdol1MaskNode;
        case '[data-testid="emv-ipb-mask"]':
          return ipbMaskNode;
        case '[data-testid="emv-icc-template-mask"]':
          return iccTemplateMaskNode;
        case '[data-testid="emv-issuer-application-data-mask"]':
          return issuerApplicationDataMaskNode;
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
        case '[data-testid="emv-action-bar"]':
          return actionBar;
        case '[data-testid="emv-evaluate-submit"]':
          return evaluateButton;
        case '[data-testid="emv-stored-empty"]':
          return storedHint;
        case '[data-testid="emv-evaluate-mode-toggle"]':
          return evaluateModeToggle;
        case '[data-testid="emv-customer-hint"]':
          return customerHint;
        default:
          return null;
      }
    },
    querySelectorAll(selector) {
      if (selector === 'input[name="mode"]') {
        return [modeIdentify, modeRespond, modeSign];
      }
      if (selector === 'input[name="emvEvaluateMode"]') {
        return [evaluateModeInline, evaluateModeStored];
      }
      return [];
    },
    addEventListener() {},
  };

  const formClassSet = new Set();
  form.className = '';
  form.classList = {
    add(className) {
      formClassSet.add(className);
      form.className = Array.from(formClassSet).join(' ');
    },
    remove(className) {
      formClassSet.delete(className);
      form.className = Array.from(formClassSet).join(' ');
    },
    contains(className) {
      return formClassSet.has(className);
    },
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
      if (String(url).includes('/api/v1/emv/cap/credentials')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(SANITIZED_SUMMARIES),
        });
      }
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
      cdol1: cdol1Input,
      issuerApplicationData: issuerApplicationDataInput,
      challenge: challengeInput,
      reference: referenceInput,
      amount: amountInput,
    },
    containers: {
      masterKey: masterKeyContainer,
      cdol1: cdol1Container,
      ipb: ipbContainer,
      iccTemplate: iccTemplateContainer,
      issuerApplicationData: issuerApplicationDataContainer,
      challenge: challengeContainer,
      reference: referenceContainer,
      amount: amountContainer,
    },
    masks: {
      masterKey: masterKeyMaskNode,
      cdol1: cdol1MaskNode,
      ipb: ipbMaskNode,
      iccTemplate: iccTemplateMaskNode,
      issuerApplicationData: issuerApplicationDataMaskNode,
    },
    hints: {
      evaluate: customerHint,
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

test('stored credential mode hides sensitive inputs and masks while leaving values blank', async () => {
  const env = createEnvironment({ verboseEnabled: true });
  await flushMicrotasks();
  if (typeof env.hooks.setEvaluateMode === 'function') {
    env.hooks.setEvaluateMode('stored');
  }
  env.hooks.updateStoredControls();
  env.hooks.storedSelect.value = SANITIZED_SUMMARIES[0].id;
  if (typeof env.hooks.storedSelect.dispatchEvent === 'function') {
    env.hooks.storedSelect.dispatchEvent('change');
  }
  await flushMicrotasks();
  assert.equal(env.inputs.masterKey.value, '', 'Stored master key input should remain blank');
  assert.equal(env.inputs.cdol1.value, '', 'Stored CDOL1 textarea should remain blank');
  assert.equal(
    env.inputs.issuerApplicationData.value,
    '',
    'Stored issuer application data textarea should remain blank',
  );
  assert.equal(
    env.containers.masterKey.getAttribute('hidden'),
    'hidden',
    'Master key field group should be hidden in stored mode',
  );
  assert.equal(
    env.containers.masterKey.getAttribute('aria-hidden'),
    'true',
    'Master key field group should be marked aria-hidden in stored mode',
  );
  assert.equal(
    env.containers.cdol1.getAttribute('hidden'),
    'hidden',
    'CDOL1 field group should be hidden in stored mode',
  );
  assert.equal(
    env.containers.issuerApplicationData.getAttribute('hidden'),
    'hidden',
    'Issuer application data field group should be hidden in stored mode',
  );
  assert.equal(
    env.inputs.masterKey.getAttribute('data-secret-mode'),
    'stored',
    'Master key input should record stored mode state',
  );
  assert.equal(
    env.inputs.masterKey.style.pointerEvents,
    'none',
    'Master key input should disable pointer events in stored mode',
  );
  assert.equal(
    env.inputs.masterKey.getAttribute('aria-hidden'),
    'true',
    'Master key input should be marked aria-hidden in stored mode',
  );
  assert.equal(
    env.inputs.cdol1.style.pointerEvents,
    'none',
    'CDOL1 textarea should disable pointer events in stored mode',
  );
  assert.equal(
    env.inputs.issuerApplicationData.style.pointerEvents,
    'none',
    'Issuer application data textarea should disable pointer events in stored mode',
  );
  assert.equal(
    env.masks.masterKey.getAttribute('hidden'),
    'hidden',
    'Master key mask should remain hidden in stored mode',
  );
  assert.equal(
    env.masks.masterKey.getAttribute('aria-hidden'),
    'true',
    'Master key mask should be marked aria-hidden in stored mode',
  );
  assert.equal(
    env.masks.cdol1.getAttribute('hidden'),
    'hidden',
    'CDOL1 mask should remain hidden in stored mode',
  );
  assert.equal(
    env.masks.cdol1.getAttribute('aria-hidden'),
    'true',
    'CDOL1 mask should be marked aria-hidden in stored mode',
  );
  assert.equal(
    env.masks.issuerApplicationData.getAttribute('hidden'),
    'hidden',
    'Issuer application data mask should remain hidden in stored mode',
  );
  assert.equal(
    env.masks.issuerApplicationData.getAttribute('aria-hidden'),
    'true',
    'Issuer application data mask should be marked aria-hidden in stored mode',
  );
  assert.equal(
    env.masks.masterKey.valueNode.textContent,
    SANITIZED_SUMMARIES[0].masterKeySha256,
    'Digest metadata should still be populated for diagnostics',
  );
  assert.equal(
    env.masks.cdol1.valueNode.textContent,
    `Hidden (${SANITIZED_SUMMARIES[0].cdol1HexLength} hex chars)`,
    'CDOL1 mask metadata should still be populated behind the scenes',
  );
  assert.equal(
    env.masks.issuerApplicationData.valueNode.textContent,
    `Hidden (${SANITIZED_SUMMARIES[0].issuerApplicationDataHexLength} hex chars)`,
    'Issuer application data metadata should still be populated behind the scenes',
  );
});

test('selecting a preset while inline mode is active keeps inline controls editable', async () => {
  const env = createEnvironment({ verboseEnabled: true });
  await flushMicrotasks();
  if (typeof env.hooks.setEvaluateMode === 'function') {
    env.hooks.setEvaluateMode('inline');
  }
  env.hooks.updateStoredControls();
  assert.equal(env.hooks.selectedEvaluateMode(), 'inline');

  env.hooks.storedSelect.value = SANITIZED_SUMMARIES[0].id;
  if (typeof env.hooks.storedSelect.dispatchEvent === 'function') {
    env.hooks.storedSelect.dispatchEvent('change');
  }
  await flushMicrotasks();

  assert.equal(env.hooks.selectedEvaluateMode(), 'inline');
  assert.equal(
    env.inputs.masterKey.getAttribute('data-secret-mode'),
    'inline',
    'Master key input should remain editable when inline mode is active',
  );
  assert.equal(
    env.inputs.masterKey.getAttribute('aria-hidden'),
    null,
    'Master key input should remain visible when inline mode is active',
  );
  assert.equal(
    env.containers.masterKey.getAttribute('hidden'),
    null,
    'Master key field group should remain visible in inline mode',
  );
  assert.equal(
    env.containers.masterKey.getAttribute('aria-hidden'),
    null,
    'Master key field group should clear aria-hidden when inline mode is active',
  );
  assert.equal(
    env.containers.cdol1.getAttribute('hidden'),
    null,
    'CDOL1 field group should remain visible in inline mode',
  );
  assert.equal(
    env.containers.issuerApplicationData.getAttribute('hidden'),
    null,
    'Issuer application data field group should remain visible in inline mode',
  );
  assert.equal(
    env.masks.masterKey.getAttribute('hidden'),
    null,
    'Master key mask should clear hidden attribute when inline mode is active',
  );
  assert.equal(
    env.masks.masterKey.getAttribute('aria-hidden'),
    null,
    'Master key mask should clear aria-hidden when inline mode is active',
  );
  assert.equal(
    env.masks.masterKey.style.display,
    'none',
    'Master key mask should remain visually hidden while inline mode is active',
  );
  assert.equal(
    env.inputs.masterKey.style.pointerEvents,
    '',
    'Master key input should allow pointer events in inline mode',
  );
  assert.ok(
    env.storedHint.textContent.includes('Inline evaluation'),
    'Inline mode hint should remain visible after selecting a preset',
  );
});

test('CAP mode toggles customer inputs and helper hint', async () => {
  const env = createEnvironment({ verboseEnabled: true });
  await flushMicrotasks();
  env.hooks.updateStoredControls();

  const { challenge, reference, amount } = env.inputs;
  const { challenge: challengeGroup, reference: referenceGroup, amount: amountGroup } = env.containers;
  const customerHint = env.hints.evaluate;

  assert.equal(challenge.disabled, true, 'Identify mode should disable challenge input');
  assert.equal(reference.disabled, true, 'Identify mode should disable reference input');
  assert.equal(amount.disabled, true, 'Identify mode should disable amount input');
  assert.equal(
    challengeGroup.getAttribute('aria-disabled'),
    'true',
    'Identify mode should mark challenge container as aria-disabled',
  );
  assert.equal(
    referenceGroup.getAttribute('aria-disabled'),
    'true',
    'Identify mode should mark reference container as aria-disabled',
  );
  assert.equal(
    amountGroup.getAttribute('aria-disabled'),
    'true',
    'Identify mode should mark amount container as aria-disabled',
  );
  assert.equal(
    customerHint.textContent,
    'Identify mode does not accept customer inputs.',
    'Identify hint text should describe disabled inputs',
  );
  assert.equal(customerHint.getAttribute('data-mode'), 'IDENTIFY', 'Hint should note active Identify mode');

  env.hooks.setCapMode('RESPOND');
  assert.equal(challenge.disabled, false, 'Respond mode should enable challenge input');
  assert.equal(reference.disabled, true, 'Respond mode should keep reference disabled');
  assert.equal(amount.disabled, true, 'Respond mode should keep amount disabled');
  assert.equal(
    challengeGroup.getAttribute('aria-disabled'),
    null,
    'Respond mode should clear aria-disabled from challenge container',
  );
  assert.equal(
    referenceGroup.getAttribute('aria-disabled'),
    'true',
    'Respond mode should leave reference container aria-disabled',
  );
  assert.equal(
    customerHint.textContent,
    'Respond mode enables Challenge; Reference and Amount remain disabled.',
    'Respond hint should describe enabled fields',
  );
  assert.equal(customerHint.getAttribute('data-mode'), 'RESPOND', 'Hint should note Respond mode');

  env.hooks.setCapMode('SIGN');
  assert.equal(challenge.disabled, true, 'Sign mode should disable challenge input');
  assert.equal(reference.disabled, false, 'Sign mode should enable reference input');
  assert.equal(amount.disabled, false, 'Sign mode should enable amount input');
  assert.equal(
    referenceGroup.getAttribute('aria-disabled'),
    null,
    'Sign mode should clear aria-disabled from reference container',
  );
  assert.equal(
    amountGroup.getAttribute('aria-disabled'),
    null,
    'Sign mode should clear aria-disabled from amount container',
  );
  assert.equal(
    customerHint.textContent,
    'Sign mode enables Reference and Amount; Challenge stays disabled.',
    'Sign hint should describe enabled fields',
  );
  assert.equal(customerHint.getAttribute('data-mode'), 'SIGN', 'Hint should note Sign mode');
});

test('inline submit with preset falls back to stored credential when secrets are blank', async () => {
  const env = createEnvironment({ verboseEnabled: true });
  await flushMicrotasks();
  if (typeof env.hooks.setEvaluateMode === 'function') {
    env.hooks.setEvaluateMode('inline');
  }
  env.hooks.updateStoredControls();
  env.hooks.storedSelect.value = SANITIZED_SUMMARIES[0].id;
  if (typeof env.hooks.storedSelect.dispatchEvent === 'function') {
    env.hooks.storedSelect.dispatchEvent('change');
  }
  await flushMicrotasks();

  await env.hooks.handleInlineSubmit();

  assert.equal(env.fetchCalls.length, 1, 'Inline submit should issue one fetch call');
  const call = env.fetchCalls[0];
  assert.equal(call.body.credentialId, SANITIZED_SUMMARIES[0].id);
  assert.equal(call.body.masterKey, '', 'Inline payload keeps master key blank to trigger fallback');
  assert.equal(call.body.includeTrace, true, 'Inline payload should request trace when verbose is enabled');
});

test('inline preset submit preserves overrides while keeping credential fallback', async () => {
  const env = createEnvironment({ verboseEnabled: false });
  await flushMicrotasks();
  if (typeof env.hooks.setEvaluateMode === 'function') {
    env.hooks.setEvaluateMode('inline');
  }
  env.hooks.updateStoredControls();
  env.hooks.storedSelect.value = SANITIZED_SUMMARIES[0].id;
  if (typeof env.hooks.storedSelect.dispatchEvent === 'function') {
    env.hooks.storedSelect.dispatchEvent('change');
  }
  await flushMicrotasks();

  env.inputs.masterKey.value = 'DEADBEEFDEADBEEFDEADBEEFDEADBEEF';
  env.inputs.atc.value = '00B5';

  await env.hooks.handleInlineSubmit();

  assert.equal(env.fetchCalls.length, 1);
  const call = env.fetchCalls[0];
  assert.equal(call.body.credentialId, SANITIZED_SUMMARIES[0].id);
  assert.equal(call.body.masterKey, 'DEADBEEFDEADBEEFDEADBEEFDEADBEEF');
  assert.equal(call.body.atc, '00B5');
  assert.equal(call.body.includeTrace, false);
});

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
  assert.equal(call.body.credentialId, 'emv-inline');
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
