const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const SCRIPT_SOURCE = readFileSync(
  path.resolve(__dirname, '../../../../main/resources/static/ui/eudi-openid4vp/console.js'),
  'utf8',
);

function createNode(overrides = {}) {
  const attributes = new Map();
  const dataset = {};
  const children = [];
  const node = {
    tagName: overrides.tagName || 'div',
    textContent: overrides.textContent || '',
    value: overrides.value || '',
    style: overrides.style || {},
    dataset,
    children,
    parentNode: null,
    classList: {
      add() {},
      remove() {},
      toggle() {},
      contains() {
        return false;
      },
    },
    appendChild(child) {
      children.push(child);
      child.parentNode = node;
      return child;
    },
    removeChild(child) {
      const index = children.indexOf(child);
      if (index >= 0) {
        children.splice(index, 1);
        child.parentNode = null;
      }
      return child;
    },
    setAttribute(name, value) {
      attributes.set(name, String(value));
      if (name.startsWith('data-')) {
        const key = name
            .substring(5)
            .split('-')
            .map((part, index) => (index === 0 ? part : part.charAt(0).toUpperCase() + part.slice(1)))
            .join('');
        dataset[key] = String(value);
      }
    },
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
    removeAttribute(name) {
      attributes.delete(name);
      if (name.startsWith('data-')) {
        const key = name
            .substring(5)
            .split('-')
            .map((part, index) => (index === 0 ? part : part.charAt(0).toUpperCase() + part.slice(1)))
            .join('');
        delete dataset[key];
      }
    },
    addEventListener() {},
    removeEventListener() {},
    querySelector() {
      return null;
    },
    querySelectorAll() {
      return [];
    },
    focus() {},
    click() {
      node.clicked = true;
    },
    remove() {
      if (node.parentNode && typeof node.parentNode.removeChild === 'function') {
        node.parentNode.removeChild(node);
      }
    },
    select() {
      node.selected = true;
    },
  };
  return Object.assign(node, overrides);
}

function createDocumentEnvironment() {
  const evaluateContainer = createNode({ tagName: 'div' });
  const replayContainer = createNode({ tagName: 'div' });
  const documentMessages = createNode({ tagName: 'p' });
  const documentHints = createNode({ tagName: 'p' });
  const resultStatus = createNode({ tagName: 'p' });
  const replayStatus = createNode({ tagName: 'p' });

  const panel = createNode({ tagName: 'section' });
  panel.querySelector = function (selector) {
    if (selector === '[data-testid="eudiw-result-presentations"]') {
      return evaluateContainer;
    }
    if (selector === '[data-testid="eudiw-replay-presentations"]') {
      return replayContainer;
    }
    if (selector === '[data-testid="eudiw-result-status"]') {
      return resultStatus;
    }
    if (selector === '[data-testid="eudiw-replay-status"]') {
      return replayStatus;
    }
    if (selector === '[data-result-message]') {
      return documentMessages;
    }
    if (selector === '[data-result-hint]') {
      return documentHints;
    }
    return null;
  };
  panel.querySelectorAll = function (selector) {
    if (selector === 'input[name="_csrf"]') {
      return [];
    }
    return [];
  };

  const documentStub = {
    querySelector(selector) {
      if (selector === '[data-protocol-panel="eudi-openid4vp"]') {
        return panel;
      }
      return null;
    },
    querySelectorAll() {
      return [];
    },
    createElement(tagName) {
      return createNode({ tagName: String(tagName).toLowerCase() });
    },
    createTextNode(text) {
      return { tagName: '#text', textContent: String(text) };
    },
    getElementById() {
      return null;
    },
    addEventListener() {},
    removeEventListener() {},
    dispatchEvent() {},
  };
  documentStub.body = createNode({ tagName: 'body' });
  documentStub.execCommand = () => true;

  return {
    documentStub,
    nodes: {
      evaluateContainer,
      replayContainer,
      resultStatus,
      replayStatus,
      documentMessages,
      documentHints,
    },
  };
}

function bootstrapConsole(options = {}) {
  const { documentStub, nodes } = createDocumentEnvironment();
  const sandbox = { window: {} };
  const globalObject = sandbox.window;
  globalObject.window = globalObject;
  globalObject.document = documentStub;
  globalObject.URLSearchParams = URLSearchParams;
  const locationMock = {
    search: typeof options.initialSearch === 'string' ? options.initialSearch : '',
    pathname: '/ui/console',
  };
  const historyMock = {
    pushCalls: [],
    replaceCalls: [],
    state: options.initialHistoryState || null,
    pushState(state, title, url) {
      this.state = state;
      this.pushCalls.push({ state, title, url });
      this.lastUrl = url;
    },
    replaceState(state, title, url) {
      this.state = state;
      this.replaceCalls.push({ state, title, url });
      this.lastUrl = url;
    },
  };
  globalObject.location = locationMock;
  globalObject.history = historyMock;
  if (typeof options.initialSearchGlobal === 'string') {
    globalObject.__operatorConsoleInitialSearch = options.initialSearchGlobal;
  }
  globalObject.fetch = () => Promise.reject(new Error('fetch not stubbed in test env'));
  globalObject.console = console;
  globalObject.Promise = Promise;
  globalObject.navigator = { clipboard: { writeText: () => Promise.resolve() } };
  const listenerMap = {};
  globalObject.addEventListener = function (type, handler) {
    if (!listenerMap[type]) {
      listenerMap[type] = [];
    }
    listenerMap[type].push(handler);
  };
  globalObject.removeEventListener = function (type, handler) {
    if (!listenerMap[type]) {
      return;
    }
    listenerMap[type] = listenerMap[type].filter((existing) => existing !== handler);
  };
  function emit(type, event) {
    const handlers = listenerMap[type] || [];
    handlers.forEach((handler) => {
      if (typeof handler === 'function') {
        handler(event);
      }
    });
  }
  globalObject.VerboseTraceConsole = {
    isEnabled() {
      return false;
    },
    beginRequest() {},
    clearTrace() {},
  };

  vm.runInNewContext(SCRIPT_SOURCE, sandbox, { filename: 'eudi-openid4vp-console.js' });

  return {
    hooks: globalObject.EudiwConsoleTestHooks || {},
    nodes,
    globals: {
      window: globalObject,
      history: historyMock,
      location: locationMock,
      emit,
    },
  };
}

function samplePresentation(id, format) {
  return {
    credentialId: id,
    format,
    holderBinding: format === 'dc+sd-jwt',
    trustedAuthorityMatch: 'aki:s9tIpP7qrS9=',
    vpToken: {
      vp_token: 'eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.' + id,
      presentation_submission: {
        descriptor_map: [
          {
            id,
            path: '$.vp_token.' + id,
          },
        ],
      },
    },
    disclosureHashes: ['sha-256:deadbeef-' + id],
  };
}

function sampleTrace(id, traceId) {
  return {
    id: traceId,
    credentialId: id,
    format: id.includes('mdoc') ? 'mso_mdoc' : 'dc+sd-jwt',
    holderBinding: id.includes('sdjwt'),
    vpTokenHash: 'sha-256:trace-' + traceId,
    disclosureHashes: ['sha-256:deadbeef-' + traceId],
  };
}

function findDescendantByTestId(node, testId) {
  if (!node || !node.children) {
    return null;
  }
  for (let index = 0; index < node.children.length; index += 1) {
    const child = node.children[index];
    if (child && child.dataset && child.dataset.testid === testId) {
      return child;
    }
    const nested = findDescendantByTestId(child, testId);
    if (nested) {
      return nested;
    }
  }
  return null;
}

test('EUDIW console renders multi-presentation sections with trace IDs and actions', () => {
  const env = bootstrapConsole();
  assert.equal(
      typeof env.hooks.renderPresentationsForTest,
      'function',
      'renderPresentationsForTest hook missing',
  );

  const presentations = [
    samplePresentation('pid-haip-multi', 'dc+sd-jwt'),
    samplePresentation('pid-mdoc-multi', 'mso_mdoc'),
  ];
  const traceEntries = [
    sampleTrace('pid-haip-multi', 'trace-sdjwt'),
    sampleTrace('pid-mdoc-multi', 'trace-mdoc'),
  ];

  env.hooks.renderPresentationsForTest({
    container: env.nodes.evaluateContainer,
    presentations,
    tracePresentations: traceEntries,
    responseMode: 'DIRECT_POST_JWT',
    sourceLabel: 'Stored preset – HAIP multi',
    kind: 'evaluate',
  });

  assert.equal(
      env.nodes.evaluateContainer.children.length,
      2,
      'expected two presentation sections',
  );
  const firstSection = env.nodes.evaluateContainer.children[0];
  const secondSection = env.nodes.evaluateContainer.children[1];
  assert.equal(firstSection.dataset.traceId, 'trace-sdjwt');
  assert.equal(secondSection.dataset.traceId, 'trace-mdoc');

  const firstSummary = firstSection.children.find((child) => child && child.tagName === 'summary');
  assert.ok(firstSummary, 'summary element missing');
  const titleNode = firstSummary.children && firstSummary.children[0];
  assert.ok(titleNode, 'summary title missing');
  assert.match(
      titleNode.textContent || '',
      /pid-haip-multi/i,
      'summary should include credential identifier',
  );

  const tokenField = findDescendantByTestId(firstSection, 'eudiw-result-vp-token');
  assert.ok(tokenField, 'expected VP Token textarea result');
  assert.ok(
      tokenField.value && tokenField.value.length > 0,
      'expected VP Token textarea to contain JSON payload',
  );
});

test('Deep-link parameters hydrate tab and mode state', () => {
  const env = bootstrapConsole();
  assert.equal(
      typeof env.hooks.applyUrlStateForTest,
      'function',
      'applyUrlStateForTest hook missing',
  );
  env.hooks.applyUrlStateForTest('?protocol=eudiw&tab=replay&mode=stored');
  const state = env.hooks.readStateForTest();
  assert.equal(state.tab, 'replay');
  assert.equal(state.replayMode, 'stored');
});

test('syncDeepLink pushes alias URLs into history', () => {
  const env = bootstrapConsole();
  env.hooks.applyUrlStateForTest('?protocol=eudiw&tab=evaluate&mode=inline');
  env.hooks.syncUrlForTest({ replace: true });
  assert.equal(env.globals.history.replaceCalls.length, 1);
  assert.match(env.globals.history.lastUrl || '', /protocol=eudiw/);
  env.hooks.applyUrlStateForTest('?protocol=eudiw&tab=replay&mode=stored');
  env.hooks.syncUrlForTest();
  assert.equal(env.globals.history.pushCalls.length, 1);
  assert.match(env.globals.history.pushCalls[0].url || '', /tab=replay/);
  assert.match(env.globals.history.pushCalls[0].url || '', /protocol=eudiw/);
});

test('syncDeepLink preserves canonical protocol when no alias is provided', () => {
  const env = bootstrapConsole();
  env.hooks.applyUrlStateForTest('?protocol=eudi-openid4vp&tab=evaluate&mode=inline');
  env.hooks.syncUrlForTest({ replace: true });
  assert.equal(env.globals.history.replaceCalls.length, 1);
  assert.match(env.globals.history.lastUrl || '', /protocol=eudi-openid4vp/);
});

test('initial URL search hydrates state without pushing history', () => {
  const env = bootstrapConsole({ initialSearch: '?protocol=eudiw&tab=replay&mode=stored' });
  const state = env.hooks.readStateForTest();
  assert.equal(state.tab, 'replay');
  assert.equal(state.replayMode, 'stored');
  assert.equal(env.globals.history.pushCalls.length, 0);
  assert.equal(env.globals.history.replaceCalls.length, 1);
  assert.match(env.globals.history.replaceCalls[0].url || '', /tab=replay/);
});

test('history state fallback tolerates partial state objects', () => {
  const env = bootstrapConsole();
  env.globals.history.state = { protocol: 'eudiw' };
  env.hooks.applyUrlStateForTest('');
  const state = env.hooks.readStateForTest();
  assert.equal(state.tab, 'evaluate');
  assert.equal(state.evaluateMode, 'inline');
});

test('protocol activation reuses the initial search state before rewrites', () => {
  const env = bootstrapConsole({ initialSearch: '?protocol=eudiw&tab=replay&mode=stored' });
  env.globals.location.search = '?protocol=eudi-openid4vp';
  env.globals.emit('operator:protocol-activated', { detail: { protocol: 'eudi-openid4vp' } });
  const state = env.hooks.readStateForTest();
  assert.equal(state.tab, 'replay');
  assert.equal(state.replayMode, 'stored');
});

test('popstate restores prior alias, tab, and mode selections', () => {
  const env = bootstrapConsole();
  env.hooks.applyUrlStateForTest('?protocol=eudiw&tab=replay&mode=stored');
  env.hooks.syncUrlForTest();
  env.globals.location.search = '?protocol=eudiw&tab=replay&mode=stored';
  env.globals.emit('popstate', { state: { protocol: 'eudiw', tab: 'replay', mode: 'stored' } });
  const state = env.hooks.readStateForTest();
  assert.equal(state.tab, 'replay');
  assert.equal(state.replayMode, 'stored');
});
