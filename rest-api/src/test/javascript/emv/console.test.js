const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

global.__emvHydrationDebug = true;

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

const HYDRATION_DETAILS = {
  'emv-123': {
    id: 'emv-123',
    mode: 'IDENTIFY',
    masterKey: '0123456789ABCDEFFEDCBA9876543210',
    cdol1: '9F2608AABBCCDDEEFF',
    issuerProprietaryBitmap: 'F0F0F0F0',
    iccDataTemplate: '5A0843211234567890',
    issuerApplicationData: '9F108101112233445566778899AABBCC',
    defaults: {
      challenge: '12345678',
      reference: '',
      amount: '',
    },
  },
};

async function flushMicrotasks() {
  await Promise.resolve();
  await Promise.resolve();
}

async function waitForCredentialSummaries(env) {
  if (!env || !env.hooks || typeof env.hooks.listCredentials !== 'function') {
    return;
  }
  let attempts = 0;
  while (env.hooks.listCredentials().length === 0 && attempts < 20) {
    await flushMicrotasks();
    attempts += 1;
  }
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

const traceFixture = JSON.parse(
  readFileSync(
    path.resolve(__dirname, '../../../../../docs/test-vectors/emv-cap/trace-provenance-example.json'),
    'utf8',
  ),
);

const scriptSource = readFileSync(
  path.resolve(__dirname, '../../../main/resources/static/ui/emv/console.js'),
  'utf8',
);

const panelTemplateSource = readFileSync(
  path.resolve(__dirname, '../../../main/resources/templates/ui/emv/panel.html'),
  'utf8',
);

test('EMV evaluate action bar keeps stack spacing utility', () => {
  const evaluateMatch = panelTemplateSource.match(
    /<div class="([^"]*?emv-action-bar[^"]*?)"[^>]*data-testid="emv-action-bar"/,
  );
  assert.ok(evaluateMatch, 'Unable to locate evaluate action bar markup');
  assert.match(
    evaluateMatch[1],
    /\bstack-offset-top-lg\b/,
    'Evaluate action bar must include stack-offset-top-lg to keep CTA spacing',
  );
});

test('EMV replay action bar reuses stack spacing utility', () => {
  const replayMatch = panelTemplateSource.match(
    /<div class="([^"]*?emv-action-bar[^"]*?)">\s*<button[^>]+data-testid="emv-replay-submit"/,
  );
  assert.ok(replayMatch, 'Unable to locate replay action bar markup');
  assert.match(
    replayMatch[1],
    /\bstack-offset-top-lg\b/,
    'Replay action bar must include stack-offset-top-lg to match other protocols',
  );
});

function extractFieldsetMarkup(template, dataTestId) {
  const marker = `data-testid="${dataTestId}"`;
  const markerIndex = template.indexOf(marker);
  assert.notEqual(markerIndex, -1, `Unable to find fieldset marker ${marker}`);
  const fieldsetStart = template.lastIndexOf('<fieldset', markerIndex);
  assert.notEqual(fieldsetStart, -1, `Unable to locate opening fieldset for ${marker}`);
  const fieldsetEnd = template.indexOf('</fieldset>', markerIndex);
  assert.notEqual(fieldsetEnd, -1, `Unable to locate closing fieldset for ${marker}`);
  return template.slice(fieldsetStart, fieldsetEnd);
}

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
    querySelector(selector) {
      const match = /^option\[value="([^"]+)"\]$/.exec(selector || '');
      if (!match) {
        return null;
      }
      const value = match[1];
      return select.options.find((option) => option.value === value) || null;
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
  const classListSet = new Set();

  function syncClassName(element) {
    element.className = Array.from(classListSet).join(' ');
  }

  const element = {
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
    classList: {
      add(className) {
        if (!className) {
          return;
        }
        classListSet.add(className);
        syncClassName(element);
      },
      remove(className) {
        if (!className) {
          return;
        }
        classListSet.delete(className);
        syncClassName(element);
      },
      contains(className) {
        return classListSet.has(className);
      },
    },
  };

  return element;
}

function createEnvironment({ verboseEnabled, includeReplay = false }) {
  const storedSelect = createSelect();
  const masterKeyContainer = createFieldGroup('emv-master-key-group');
  const atcContainer = createFieldGroup('emv-atc-group');
  const branchFactorContainer = createFieldGroup('emv-branch-factor-group');
  const heightContainer = createFieldGroup('emv-height-group');
  const ivContainer = createFieldGroup('emv-iv-group');
  const cdol1Container = createFieldGroup('emv-cdol1-group');
  const ipbContainer = createFieldGroup('emv-ipb-group');
  const iccTemplateContainer = createFieldGroup('emv-icc-template-group');
  const issuerApplicationDataContainer = createFieldGroup('emv-issuer-application-data-group');
  const challengeContainer = createFieldGroup('emv-challenge-group');
  const referenceContainer = createFieldGroup('emv-reference-group');
  const amountContainer = createFieldGroup('emv-amount-group');

  const masterKeyInput = createInput('emvMasterKey', 'A1B2', masterKeyContainer);
  const atcInput = createInput('emvAtc', '01', atcContainer);
  const branchFactorInput = createInput('emvBranchFactor', '2', branchFactorContainer);
  const heightInput = createInput('emvHeight', '1', heightContainer);
  const ivInput = createInput('emvIv', 'AA', ivContainer);
  const cdol1Input = createTextarea('emvCdol1', 'BB', cdol1Container);
  const ipbInput = createInput('emvIpb', 'CC', ipbContainer);
  const iccTemplateInput = createTextarea('emvIccTemplate', 'DD', iccTemplateContainer);
  const issuerApplicationDataInput = createInput('emvIssuerApplicationData', 'EE', issuerApplicationDataContainer);
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

  let replayEnv = null;
  if (includeReplay) {
    const replayStoredSelect = createSelect();
    const replayMasterKeyContainer = createFieldGroup('emv-replay-master-key-group');
    const replayAtcContainer = createFieldGroup('emv-replay-atc-group');
    const replayBranchFactorContainer = createFieldGroup('emv-replay-branch-factor-group');
    const replayHeightContainer = createFieldGroup('emv-replay-height-group');
    const replayIvContainer = createFieldGroup('emv-replay-iv-group');
    const replayCdol1Container = createFieldGroup('emv-replay-cdol1-group');
    const replayIssuerBitmapContainer = createFieldGroup('emv-replay-issuer-bitmap-group');
    const replayIccTemplateContainer = createFieldGroup('emv-replay-icc-template-group');
    const replayIssuerApplicationDataContainer = createFieldGroup('emv-replay-issuer-application-data-group');
    const replayChallengeContainer = createFieldGroup('emv-replay-challenge-group');
    const replayReferenceContainer = createFieldGroup('emv-replay-reference-group');
    const replayAmountContainer = createFieldGroup('emv-replay-amount-group');
    const replayMasterKeyInput = createInput('emvReplayMasterKey', 'AA', replayMasterKeyContainer);
    const replayAtcInput = createInput('emvReplayAtc', '00B4', replayAtcContainer);
    const replayBranchFactorInput = createInput('emvReplayBranchFactor', '4', replayBranchFactorContainer);
    const replayHeightInput = createInput('emvReplayHeight', '8', replayHeightContainer);
    const replayIvInput = createInput('emvReplayIv', '0001020304050607', replayIvContainer);
    const replayCdol1Input = createTextarea('emvReplayCdol1', 'CDOL', replayCdol1Container);
    const replayIssuerBitmapInput = createInput('emvReplayIssuerBitmap', 'IPB', replayIssuerBitmapContainer);
    const replayIccTemplateInput =
        createTextarea('emvReplayIccTemplate', 'ICC', replayIccTemplateContainer);
    const replayIssuerApplicationDataInput = createInput(
        'emvReplayIssuerApplicationData', 'IAD', replayIssuerApplicationDataContainer);
    const replayChallengeInput = createInput('emvReplayChallenge', '', replayChallengeContainer);
    const replayReferenceInput = createInput('emvReplayReference', '', replayReferenceContainer);
    const replayAmountInput = createInput('emvReplayAmount', '', replayAmountContainer);
    const replayOtpContainer = createFieldGroup('emv-replay-otp-group');
    const replayOtpInput = createInput('emvReplayOtp', '00000000', replayOtpContainer);
    const replayDriftBackwardContainer = createFieldGroup('emv-replay-drift-backward-group');
    const replayDriftForwardContainer = createFieldGroup('emv-replay-drift-forward-group');
    const replayDriftBackwardInput = createInput('emvReplayDriftBackward', '0', replayDriftBackwardContainer);
    const replayDriftForwardInput = createInput('emvReplayDriftForward', '1', replayDriftForwardContainer);
    const replayMasterKeyMaskNode = createMaskField();
    const replayCdol1MaskNode = createMaskField();
    const replayIssuerBitmapMaskNode = createMaskField();
    const replayIccTemplateMaskNode = createMaskField();
    const replayIssuerApplicationDataMaskNode = createMaskField();
    const replayModeInline = createInput('emvReplayModeInline', 'inline');
    replayModeInline.value = 'inline';
    replayModeInline.checked = true;
    const replayModeStored = createInput('emvReplayModeStored', 'stored');
    replayModeStored.value = 'stored';
    const replayModeToggle = {
      dataset: { mode: 'inline' },
      setAttribute(name, value) {
        if (name === 'data-mode') {
          this.dataset.mode = String(value);
        }
      },
      getAttribute(name) {
        if (name === 'data-mode') {
          return this.dataset.mode;
        }
        return null;
      },
      querySelector(selector) {
        if (selector === '#emvReplayModeStored') {
          return replayModeStored;
        }
        if (selector === '#emvReplayModeInline') {
          return replayModeInline;
        }
        return null;
      },
    };
    const replayStoredSection = { setAttribute() {}, removeAttribute() {} };
    const replayModeIdentify = createInput('emvReplayModeIdentify', 'IDENTIFY');
    replayModeIdentify.checked = true;
    const replayModeRespond = createInput('emvReplayModeRespond', 'RESPOND');
    const replayModeSign = createInput('emvReplayModeSign', 'SIGN');

    const replayFormClassSet = new Set();
    const replayForm = {
      className: '',
      classList: {
        add(className) {
          replayFormClassSet.add(className);
          replayForm.className = Array.from(replayFormClassSet).join(' ');
        },
        remove(className) {
          replayFormClassSet.delete(className);
          replayForm.className = Array.from(replayFormClassSet).join(' ');
        },
        contains(className) {
          return replayFormClassSet.has(className);
        },
      },
      getAttribute(name) {
        if (name === 'data-replay-endpoint') {
          return '/api/v1/emv/cap/replay';
        }
        if (name === 'data-credentials-endpoint') {
          return '/api/v1/emv/cap/credentials';
        }
        return null;
      },
      querySelector(selector) {
        switch (selector) {
          case '[data-replay-section="stored"]':
            return replayStoredSection;
          case '#emvReplayStoredCredentialId':
            return replayStoredSelect;
          case '[data-testid="emv-replay-master-key"] input':
            return replayMasterKeyInput;
          case '[data-testid="emv-replay-master-key-mask"]':
            return replayMasterKeyMaskNode;
          case '[data-testid="emv-replay-atc"] input':
            return replayAtcInput;
          case '[data-testid="emv-replay-branch-factor"] input':
            return replayBranchFactorInput;
          case '[data-testid="emv-replay-height"] input':
            return replayHeightInput;
          case '[data-testid="emv-replay-iv"] input':
            return replayIvInput;
          case '[data-testid="emv-replay-cdol1"] textarea':
            return replayCdol1Input;
          case '[data-testid="emv-replay-cdol1-mask"]':
            return replayCdol1MaskNode;
          case '[data-testid="emv-replay-issuer-bitmap"] input':
            return replayIssuerBitmapInput;
          case '[data-testid="emv-replay-ipb-mask"]':
            return replayIssuerBitmapMaskNode;
          case '[data-testid="emv-replay-icc-template"] textarea':
            return replayIccTemplateInput;
          case '[data-testid="emv-replay-icc-template-mask"]':
            return replayIccTemplateMaskNode;
          case '[data-testid="emv-replay-issuer-application-data"] input':
            return replayIssuerApplicationDataInput;
          case '[data-testid="emv-replay-issuer-application-data-mask"]':
            return replayIssuerApplicationDataMaskNode;
          case '[data-testid="emv-replay-challenge"] input':
            return replayChallengeInput;
          case '[data-testid="emv-replay-reference"] input':
            return replayReferenceInput;
          case '[data-testid="emv-replay-amount"] input':
            return replayAmountInput;
          case '[data-testid="emv-replay-otp"] input':
            return replayOtpInput;
          case '[data-testid="emv-replay-drift-backward"] input':
            return replayDriftBackwardInput;
          case '[data-testid="emv-replay-drift-forward"] input':
            return replayDriftForwardInput;
          case '[data-testid="emv-replay-mode-toggle"]':
            return replayModeToggle;
          case '#emvReplayModeStored':
            return replayModeStored;
          case '#emvReplayModeInline':
            return replayModeInline;
          default:
            return null;
        }
      },
      querySelectorAll(selector) {
        if (selector === 'input[name="replayMode"]') {
          return [replayModeIdentify, replayModeRespond, replayModeSign];
        }
        return [];
      },
      addEventListener() {},
    };

    const replayStatusNode = { textContent: '—' };
    const replayOtpResultNode = { textContent: '—' };
    const replayMatchedNode = {
      textContent: '',
      setAttribute() {},
      removeAttribute() {},
    };
    const replayReasonNode = {
      textContent: '',
      setAttribute() {},
      removeAttribute() {},
    };

    replayEnv = {
      form: replayForm,
      storedSelect: replayStoredSelect,
      masterKeyInput: replayMasterKeyInput,
      masterKeyContainer: replayMasterKeyContainer,
      atcInput: replayAtcInput,
      branchFactorInput: replayBranchFactorInput,
      heightInput: replayHeightInput,
      ivInput: replayIvInput,
      cdol1Container: replayCdol1Container,
      cdol1Input: replayCdol1Input,
      issuerBitmapContainer: replayIssuerBitmapContainer,
      issuerBitmapInput: replayIssuerBitmapInput,
      iccTemplateContainer: replayIccTemplateContainer,
      iccTemplateInput: replayIccTemplateInput,
      issuerApplicationDataContainer: replayIssuerApplicationDataContainer,
      issuerApplicationDataInput: replayIssuerApplicationDataInput,
      challengeInput: replayChallengeInput,
      referenceInput: replayReferenceInput,
      amountInput: replayAmountInput,
      masterKeyMask: replayMasterKeyMaskNode,
      atcContainer: replayAtcContainer,
      branchFactorContainer: replayBranchFactorContainer,
      heightContainer: replayHeightContainer,
      ivContainer: replayIvContainer,
      cdol1Mask: replayCdol1MaskNode,
      issuerBitmapMask: replayIssuerBitmapMaskNode,
      iccTemplateMask: replayIccTemplateMaskNode,
      issuerApplicationDataMask: replayIssuerApplicationDataMaskNode,
      modeToggle: replayModeToggle,
      capModeRadios: [replayModeIdentify, replayModeRespond, replayModeSign],
      otpInput: replayOtpInput,
      driftBackwardInput: replayDriftBackwardInput,
      driftForwardInput: replayDriftForwardInput,
      statusNode: replayStatusNode,
      otpResultNode: replayOtpResultNode,
      matchedNode: replayMatchedNode,
      reasonNode: replayReasonNode,
    };
  }
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

  const replayResultPanel = includeReplay
      ? {
        hidden: true,
        setAttribute() {},
        removeAttribute() {},
        querySelector(selector) {
          if (!replayEnv) {
            return null;
          }
          if (selector === '[data-testid=\'emv-replay-status\']') {
            return replayEnv.statusNode;
          }
          if (selector === '[data-testid=\'emv-replay-otp\']') {
            return replayEnv.otpResultNode;
          }
          if (selector === '[data-testid=\'emv-replay-matched-delta\']') {
            return replayEnv.matchedNode;
          }
          if (selector === '[data-testid=\'emv-replay-reason\']') {
            return replayEnv.reasonNode;
          }
          return null;
        },
      }
      : null;

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
      if (selector === '[data-testid="emv-replay-form"]') {
        return replayEnv ? replayEnv.form : null;
      }
      if (selector === '[data-testid="emv-replay-result-card"]') {
        return replayResultPanel;
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
        case '[data-testid="emv-evaluate-mode-toggle"]':
          return evaluateModeToggle;
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
      if (selector === '[data-testid="emv-replay-result-card"]') {
        return replayResultPanel;
      }
      return null;
    },
  };

  const fetchCalls = [];
  const cloneFixture = () => JSON.parse(JSON.stringify(traceFixture));

  const fetchStub = (url, options = {}) => {
    const urlString = String(url);
    if (!options.method || options.method === 'GET') {
      if (/\/api\/v1\/emv\/cap\/credentials\/[^/]+$/.test(urlString)) {
        const credentialId = decodeURIComponent(urlString.substring(urlString.lastIndexOf('/') + 1));
        const detail = HYDRATION_DETAILS[credentialId];
        if (!detail) {
          return Promise.resolve({
            ok: false,
            status: 404,
            json: () => Promise.resolve({}),
          });
        }
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(detail),
        });
      }
      if (urlString.endsWith('/api/v1/emv/cap/credentials')) {
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
    const responseBody = cloneFixture();
    return Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve(responseBody),
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
  const tracePayloads = [];
  globalObject.VerboseTraceConsole = {
    isEnabled() {
      return verboseEnabled;
    },
    beginRequest() {},
    clearTrace() {},
    handleResponse(payload) {
      tracePayloads.push(payload);
    },
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
    tracePayloads,
    inputs: {
      masterKey: masterKeyInput,
      atc: atcInput,
      branchFactor: branchFactorInput,
      height: heightInput,
      iv: ivInput,
      cdol1: cdol1Input,
      issuerProprietaryBitmap: ipbInput,
      iccTemplate: iccTemplateInput,
      issuerApplicationData: issuerApplicationDataInput,
      challenge: challengeInput,
      reference: referenceInput,
      amount: amountInput,
    },
    containers: {
      masterKey: masterKeyContainer,
      atc: atcContainer,
      branchFactor: branchFactorContainer,
      height: heightContainer,
      iv: ivContainer,
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
    replayInputs: includeReplay
        ? {
          masterKey: replayEnv.masterKeyInput,
          atc: replayEnv.atcInput,
          branchFactor: replayEnv.branchFactorInput,
          height: replayEnv.heightInput,
          iv: replayEnv.ivInput,
          cdol1: replayEnv.cdol1Input,
          issuerProprietaryBitmap: replayEnv.issuerBitmapInput,
          iccTemplate: replayEnv.iccTemplateInput,
          issuerApplicationData: replayEnv.issuerApplicationDataInput,
          challenge: replayEnv.challengeInput,
          reference: replayEnv.referenceInput,
          amount: replayEnv.amountInput,
        }
        : null,
    replayContainers: includeReplay && replayEnv
        ? {
          masterKey: replayEnv.masterKeyContainer,
          atc: replayEnv.atcContainer,
          branchFactor: replayEnv.branchFactorContainer,
          height: replayEnv.heightContainer,
          iv: replayEnv.ivContainer,
          cdol1: replayEnv.cdol1Container,
          issuerProprietaryBitmap: replayEnv.issuerBitmapContainer,
          iccTemplate: replayEnv.iccTemplateContainer,
          issuerApplicationData: replayEnv.issuerApplicationDataContainer,
        }
        : null,
    replayStoredSelect: includeReplay ? replayEnv.storedSelect : null,
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
    'Stored issuer application data input should remain blank',
  );
  assert.equal(env.inputs.iccTemplate.value, '', 'Stored ICC template textarea should remain blank');
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
    env.containers.iccTemplate.getAttribute('hidden'),
    'hidden',
    'ICC template field group should be hidden in stored mode',
  );
  assert.equal(
    env.containers.iccTemplate.getAttribute('aria-hidden'),
    'true',
    'ICC template field group should be marked aria-hidden in stored mode',
  );
  assert.equal(
    env.containers.atc.getAttribute('hidden'),
    null,
    'ATC field group should remain visible while stored mode hides master key secrets',
  );
  assert.equal(
    env.containers.atc.getAttribute('aria-hidden'),
    null,
    'ATC field group should keep aria-hidden cleared while stored mode is active',
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
    env.inputs.atc.style.pointerEvents || '',
    '',
    'ATC input should remain interactive so operators can adjust stored derivation values',
  );
  assert.equal(
    env.inputs.atc.getAttribute('aria-hidden'),
    null,
    'ATC input should remain visible/accessible during stored mode',
  );
  assert.equal(
    env.inputs.iccTemplate.style.pointerEvents,
    'none',
    'ICC template textarea should disable pointer events in stored mode',
  );
  assert.equal(
    env.inputs.iccTemplate.getAttribute('aria-hidden'),
    'true',
    'ICC template input should be marked aria-hidden in stored mode',
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
    env.masks.iccTemplate.getAttribute('hidden'),
    'hidden',
    'ICC template mask should remain hidden in stored mode',
  );
  assert.equal(
    env.masks.iccTemplate.getAttribute('aria-hidden'),
    'true',
    'ICC template mask should be marked aria-hidden in stored mode',
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

test('session key derivation fields remain visible while stored mode hides sensitive evaluate inputs', async () => {
  const env = createEnvironment({ verboseEnabled: true });
  await flushMicrotasks();
  await waitForCredentialSummaries(env);
  if (typeof env.hooks.setEvaluateMode === 'function') {
    env.hooks.setEvaluateMode('stored');
  }
  if (typeof env.hooks.updateStoredControls === 'function') {
    env.hooks.updateStoredControls();
  }
  env.hooks.storedSelect.value = SANITIZED_SUMMARIES[0].id;
  if (typeof env.hooks.storedSelect.dispatchEvent === 'function') {
    env.hooks.storedSelect.dispatchEvent('change');
  }
  await flushMicrotasks();
  const derivationFields = [
    { key: 'atc', label: 'ATC' },
    { key: 'branchFactor', label: 'Branch factor' },
    { key: 'height', label: 'Height' },
    { key: 'iv', label: 'IV' },
  ];
  derivationFields.forEach(({ key, label }) => {
    const container = env.containers[key];
    assert.ok(container, `${label} field group should exist.`);
    assert.equal(
        container.getAttribute('hidden'),
        null,
        `${label} field group should remain visible when stored mode is active`,
    );
    assert.equal(
        container.getAttribute('aria-hidden'),
        null,
        `${label} field group should keep aria-hidden cleared in stored mode`,
    );
    const input = env.inputs[key];
    assert.ok(input, `${label} input should exist.`);
    assert.equal(
        input.getAttribute('aria-hidden'),
        null,
        `${label} input should remain accessible while stored mode hides secrets`,
    );
    assert.equal(
        input.style.pointerEvents || '',
        '',
        `${label} input should stay interactive so operators can tweak derivation inputs`,
    );
  });
});

test('replay session key derivation fields remain visible when stored mode hides secrets', async () => {
  const env = createEnvironment({ verboseEnabled: true, includeReplay: true });
  await flushMicrotasks();
  await waitForCredentialSummaries(env);
  if (typeof env.hooks.setActivePanel === 'function') {
    env.hooks.setActivePanel('replay');
  }
  if (typeof env.hooks.setReplayMode === 'function') {
    env.hooks.setReplayMode('stored');
  }
  if (env.replayStoredSelect) {
    env.replayStoredSelect.value = SANITIZED_SUMMARIES[0].id;
    if (typeof env.replayStoredSelect.dispatchEvent === 'function') {
      env.replayStoredSelect.dispatchEvent('change');
    }
  }
  await flushMicrotasks();
  await flushMicrotasks();
  assert.ok(env.replayContainers, 'Replay containers should be available when includeReplay=true.');
  const derivationFields = [
    { key: 'atc', label: 'Replay ATC' },
    { key: 'branchFactor', label: 'Replay branch factor' },
    { key: 'height', label: 'Replay height' },
    { key: 'iv', label: 'Replay IV' },
  ];
  derivationFields.forEach(({ key, label }) => {
    const container = env.replayContainers[key];
    assert.ok(container, `${label} field group should exist.`);
    assert.equal(
        container.getAttribute('hidden'),
        null,
        `${label} field group should remain visible in stored replay mode`,
    );
    assert.equal(
        container.getAttribute('aria-hidden'),
        null,
        `${label} field group should keep aria-hidden cleared in stored replay mode`,
    );
    const input = env.replayInputs ? env.replayInputs[key] : null;
    assert.ok(input, `${label} input should exist.`);
    assert.equal(
        input.getAttribute('aria-hidden'),
        null,
        `${label} input should remain accessible while stored replay hides secrets`,
    );
    assert.equal(
        input.style.pointerEvents || '',
        '',
        `${label} input should stay interactive so operators can adjust derivation values`,
    );
  });
});

test('card configuration fieldsets isolate transaction and customer sections', () => {
  const evaluateCardMarkup = extractFieldsetMarkup(panelTemplateSource, 'emv-card-block');
  assert.ok(
      !evaluateCardMarkup.includes('data-testid="emv-transaction-block"'),
      'Evaluate card configuration should not wrap the transaction block',
  );
  assert.ok(
      !evaluateCardMarkup.includes('data-testid="emv-customer-block"'),
      'Evaluate card configuration should not wrap the customer block',
  );

  const replayCardMarkup = extractFieldsetMarkup(panelTemplateSource, 'emv-replay-card-block');
  assert.ok(
      !replayCardMarkup.includes('data-testid="emv-replay-transaction-block"'),
      'Replay card configuration should not wrap the transaction block',
  );
  assert.ok(
      !replayCardMarkup.includes('data-testid="emv-replay-customer-block"'),
      'Replay card configuration should not wrap the customer block',
  );
});

test('customer input grid exposes a single shared set of inputs for evaluate mode', () => {
  const evaluateCustomerMarkup = extractFieldsetMarkup(panelTemplateSource, 'emv-customer-block');
  const challengeMatches = evaluateCustomerMarkup.match(/data-field="challenge"/g) || [];
  const referenceMatches = evaluateCustomerMarkup.match(/data-field="reference"/g) || [];
  const amountMatches = evaluateCustomerMarkup.match(/data-field="amount"/g) || [];
  assert.strictEqual(challengeMatches.length, 1, 'Evaluate grid should expose exactly one challenge field group');
  assert.strictEqual(referenceMatches.length, 1, 'Evaluate grid should expose exactly one reference field group');
  assert.strictEqual(amountMatches.length, 1, 'Evaluate grid should expose exactly one amount field group');

  const respondIndex = evaluateCustomerMarkup.indexOf('data-mode="RESPOND"');
  const challengeIndex = evaluateCustomerMarkup.indexOf('data-field="challenge"');
  assert.ok(respondIndex > -1 && challengeIndex > respondIndex,
      'Challenge field group should render after the Respond radio to share the same row');

  const signIndex = evaluateCustomerMarkup.indexOf('data-mode="SIGN"');
  const referenceIndex = evaluateCustomerMarkup.indexOf('data-field="reference"');
  const amountIndex = evaluateCustomerMarkup.indexOf('data-field="amount"');
  assert.ok(signIndex > -1 && referenceIndex > signIndex,
      'Reference field group should render after the Sign radio to share the same row');
  assert.ok(signIndex > -1 && amountIndex > signIndex,
      'Amount field group should render after the Sign radio to share the same row');
});

test('customer input grid exposes a single shared set of inputs for replay mode', () => {
  const replayCustomerMarkup = extractFieldsetMarkup(panelTemplateSource, 'emv-replay-customer-block');
  const challengeMatches = replayCustomerMarkup.match(/data-field="challenge"/g) || [];
  const referenceMatches = replayCustomerMarkup.match(/data-field="reference"/g) || [];
  const amountMatches = replayCustomerMarkup.match(/data-field="amount"/g) || [];
  assert.strictEqual(challengeMatches.length, 1, 'Replay grid should expose exactly one challenge field group');
  assert.strictEqual(referenceMatches.length, 1, 'Replay grid should expose exactly one reference field group');
  assert.strictEqual(amountMatches.length, 1, 'Replay grid should expose exactly one amount field group');

  const respondIndex = replayCustomerMarkup.indexOf('data-mode="RESPOND"');
  const challengeIndex = replayCustomerMarkup.indexOf('data-field="challenge"');
  assert.ok(respondIndex > -1 && challengeIndex > respondIndex,
      'Replay challenge field group should render after the Respond radio');

  const signIndex = replayCustomerMarkup.indexOf('data-mode="SIGN"');
  const referenceIndex = replayCustomerMarkup.indexOf('data-field="reference"');
  const amountIndex = replayCustomerMarkup.indexOf('data-field="amount"');
  assert.ok(signIndex > -1 && referenceIndex > signIndex,
      'Replay reference field group should render after the Sign radio');
  assert.ok(signIndex > -1 && amountIndex > signIndex,
      'Replay amount field group should render after the Sign radio');
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
});

test('inline preset hydration populates sensitive fields for evaluate flow', async () => {
  const env = createEnvironment({ verboseEnabled: true });
  await flushMicrotasks();
  await waitForCredentialSummaries(env);
  if (typeof env.hooks.setEvaluateMode === 'function') {
    env.hooks.setEvaluateMode('inline');
  }
  env.hooks.updateStoredControls();
  env.hooks.storedSelect.value = SANITIZED_SUMMARIES[0].id;
  if (typeof env.hooks.storedSelect.dispatchEvent === 'function') {
    env.hooks.storedSelect.dispatchEvent('change');
  }
  await flushMicrotasks();
  await flushMicrotasks();
  await flushMicrotasks();
  // Debugging inline hydration state
  // eslint-disable-next-line no-console
  console.log('Evaluate hydration debug:', global.__emvLastEvaluateHydration);

  const hydration = HYDRATION_DETAILS[SANITIZED_SUMMARIES[0].id];
  assert.equal(env.inputs.masterKey.value, hydration.masterKey, 'Master key should hydrate inline input');
  assert.equal(env.inputs.cdol1.value, hydration.cdol1, 'CDOL1 should hydrate inline textarea');
  assert.equal(
    env.inputs.issuerProprietaryBitmap.value,
    hydration.issuerProprietaryBitmap,
    'Issuer bitmap should hydrate inline input',
  );
  assert.equal(
    env.inputs.iccTemplate.value,
    hydration.iccDataTemplate,
    'ICC template should hydrate inline textarea',
  );
  assert.equal(
    env.inputs.issuerApplicationData.value,
    hydration.issuerApplicationData,
    'Issuer application data should hydrate inline input',
  );
  assert.equal(env.inputs.challenge.value, hydration.defaults.challenge, 'Challenge default should hydrate inline field');
  assert.equal(env.inputs.reference.value, hydration.defaults.reference, 'Reference default should hydrate inline field');
  assert.equal(env.inputs.amount.value, hydration.defaults.amount, 'Amount default should hydrate inline field');
});

test('inline preset hydration populates sensitive fields for replay flow', async () => {
  const env = createEnvironment({ verboseEnabled: true, includeReplay: true });
  await flushMicrotasks();
  await waitForCredentialSummaries(env);
  if (typeof env.hooks.setActivePanel === 'function') {
    env.hooks.setActivePanel('replay');
  }
  if (typeof env.hooks.setReplayMode === 'function') {
    env.hooks.setReplayMode('inline');
  }
  while (env.replayStoredSelect && env.replayStoredSelect.options.length <= 1) {
    await flushMicrotasks();
  }
  if (env.replayStoredSelect) {
    env.replayStoredSelect.value = SANITIZED_SUMMARIES[0].id;
    if (typeof env.replayStoredSelect.dispatchEvent === 'function') {
      env.replayStoredSelect.dispatchEvent('change');
    }
  }
  await flushMicrotasks();
  await flushMicrotasks();
  await flushMicrotasks();

  const hydration = HYDRATION_DETAILS[SANITIZED_SUMMARIES[0].id];
  assert.ok(env.replayInputs, 'Replay inputs should be available when includeReplay=true');
  assert.equal(
    env.replayInputs.masterKey.value,
    hydration.masterKey,
    'Replay master key should hydrate inline input',
  );
  assert.equal(
    env.replayInputs.cdol1.value,
    hydration.cdol1,
    'Replay CDOL1 should hydrate inline textarea',
  );
  assert.equal(
    env.replayInputs.issuerProprietaryBitmap.value,
    hydration.issuerProprietaryBitmap,
    'Replay issuer bitmap should hydrate inline input',
  );
  assert.equal(
    env.replayInputs.iccTemplate.value,
    hydration.iccDataTemplate,
    'Replay ICC template should hydrate inline textarea',
  );
  assert.equal(
    env.replayInputs.issuerApplicationData.value,
    hydration.issuerApplicationData,
    'Replay issuer application data should hydrate inline input',
  );
  assert.equal(
    env.replayInputs.challenge.value,
    hydration.defaults.challenge,
    'Replay challenge default should hydrate inline field',
  );
  assert.equal(
    env.replayInputs.reference.value,
    hydration.defaults.reference,
    'Replay reference default should hydrate inline field',
  );
  assert.equal(
    env.replayInputs.amount.value,
    hydration.defaults.amount,
    'Replay amount default should hydrate inline field',
  );
});

test('CAP mode toggles customer inputs', async () => {
  const env = createEnvironment({ verboseEnabled: true });
  await flushMicrotasks();
  env.hooks.updateStoredControls();

  const { challenge, reference, amount } = env.inputs;
  const { challenge: challengeGroup, reference: referenceGroup, amount: amountGroup } = env.containers;

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

test('inline verbose trace forwards provenance sections to the console', async () => {
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
  await flushMicrotasks();
  await flushMicrotasks();
  assert.ok(env.tracePayloads.length > 0, 'Verbose trace console should receive a payload');
  const tracePayload = env.tracePayloads[0];
  assert.ok(tracePayload, 'Trace payload should exist');
  assert.ok(tracePayload.trace, 'Trace payload should include trace details');
  assert.ok(tracePayload.trace.provenance, 'Trace payload should include provenance section');

  const sections = [
    'protocolContext',
    'keyDerivation',
    'cdolBreakdown',
    'iadDecoding',
    'macTranscript',
    'decimalizationOverlay',
  ];
  sections.forEach((section) => {
    assert.ok(
      tracePayload.trace.provenance[section],
      `provenance.${section} should be present on verbose traces`,
    );
  });
});

test('inline replay payload requests verbose trace when console toggle is enabled', async () => {
  const env = createEnvironment({ verboseEnabled: true, includeReplay: true });
  await flushMicrotasks();
  if (typeof env.hooks.setActivePanel === 'function') {
    env.hooks.setActivePanel('replay');
  }
  await flushMicrotasks();
  const payload = env.hooks.buildReplayPayload();
  assert.ok(payload, 'Replay payload builder should return a payload bundle');
  assert.ok(payload.body, 'Replay payload bundle should include the request body');
  assert.equal(
    payload.body.includeTrace,
    true,
    'Replay payload should request verbose traces when the global toggle is enabled',
  );
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
