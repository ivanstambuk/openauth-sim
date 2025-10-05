(function (global) {
  'use strict';

  var documentRef = global.document;
  var protocolTabs = Array.prototype.slice.call(documentRef.querySelectorAll('[data-protocol-tab]'));
  var protocolPanels = Array.prototype.slice.call(documentRef.querySelectorAll('[data-protocol-panel]'));
  var modeToggle = documentRef.querySelector("[data-testid='ocra-mode-toggle']");
  var evaluateButton = modeToggle && modeToggle.querySelector("[data-testid='ocra-mode-select-evaluate']");
  var replayButton = modeToggle && modeToggle.querySelector("[data-testid='ocra-mode-select-replay']");
  var evaluatePanel = documentRef.querySelector("[data-testid='ocra-evaluate-panel']");
  var replayPanel = documentRef.querySelector("[data-testid='ocra-replay-panel']");
  var operatorConsoleRoot = documentRef.querySelector('.operator-console');
  var protocolInfoTrigger = documentRef.querySelector("[data-testid='protocol-info-trigger']");

  var allowedProtocols = new Set([
    'hotp',
    'totp',
    'ocra',
    'eudi-openid4vp',
    'eudi-iso-18013-5',
    'eudi-siopv2',
    'fido2',
    'emv',
  ]);
  var allowedTabs = new Set(['evaluate', 'replay']);

  var currentProtocol = 'ocra';
  var lastOcraTab = 'evaluate';

  function setPanelVisibility(panel, hidden) {
    if (!panel) {
      return;
    }
    if (hidden) {
      panel.setAttribute('hidden', 'hidden');
      panel.setAttribute('aria-hidden', 'true');
    } else {
      panel.removeAttribute('hidden');
      panel.removeAttribute('aria-hidden');
    }
  }

  function toggleButtonState(button, active) {
    if (!button) {
      return;
    }
    button.classList.toggle('mode-pill--active', active);
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  }

  function setActiveMode(mode) {
    if (!modeToggle) {
      return;
    }
    modeToggle.setAttribute('data-mode', mode);
    var isEvaluate = mode === 'evaluate';
    toggleButtonState(evaluateButton, isEvaluate);
    toggleButtonState(replayButton, !isEvaluate);
    setPanelVisibility(evaluatePanel, !isEvaluate);
    setPanelVisibility(replayPanel, isEvaluate);
    lastOcraTab = mode;
  }

  function setActiveProtocol(protocol, ocraMode, options) {
    options = options || {};
    currentProtocol = protocol;
    protocolTabs.forEach(function (tab) {
      var tabProtocol = tab.getAttribute('data-protocol-tab');
      var isActive = tabProtocol === protocol;
      tab.classList.toggle('protocol-tab--active', isActive);
      tab.setAttribute('aria-selected', isActive ? 'true' : 'false');
    });

    protocolPanels.forEach(function (panel) {
      var panelProtocol = panel.getAttribute('data-protocol-panel');
      var isActive = panelProtocol === protocol;
      setPanelVisibility(panel, !isActive);
    });

    if (protocolInfoTrigger) {
      protocolInfoTrigger.setAttribute('data-protocol', protocol);
    }

    if (protocol === 'ocra') {
      setPanelVisibility(modeToggle, false);
      setActiveMode(ocraMode || lastOcraTab || 'evaluate');
    } else {
      setPanelVisibility(modeToggle, true);
    }

    if (options.syncProtocolInfo !== false && global.ProtocolInfo) {
      global.ProtocolInfo.setProtocol(protocol, {
        autoOpen: Boolean(options.allowAutoOpen),
        resetSection: Boolean(options.resetSection),
        notifyHost: false,
      });
    }

    try {
      var protocolEvent = new global.CustomEvent('operator:protocol-activated', {
        detail: { protocol: protocol },
      });
      documentRef.dispatchEvent(protocolEvent);
    } catch (error) {
      if (global.console && typeof global.console.error === 'function') {
        global.console.error('Failed to dispatch protocol activation event', error);
      }
    }
  }

  function normalizeState(state) {
    var protocol = allowedProtocols.has(state && state.protocol) ? state.protocol : 'ocra';
    if (protocol === 'ocra') {
      var tab = allowedTabs.has(state && state.tab) ? state.tab : 'evaluate';
      return { protocol: protocol, tab: tab };
    }
    return { protocol: protocol };
  }

  function buildSearch(state) {
    var params = new global.URLSearchParams();
    params.set('protocol', state.protocol);
    if (state.protocol === 'ocra') {
      params.set('tab', state.tab);
    }
    var rendered = params.toString();
    return rendered ? '?' + rendered : global.location.search;
  }

  function pushUrlState(state, options) {
    var search = buildSearch(state);
    var url = global.location.pathname + search;
    var historyState = state.protocol === 'ocra'
      ? { protocol: state.protocol, tab: state.tab }
      : { protocol: state.protocol };

    if (options && options.replace) {
      global.history.replaceState(historyState, '', url);
      return;
    }

    if (global.location.search === search) {
      return;
    }

    global.history.pushState(historyState, '', url);
  }

  function applyConsoleState(nextState, options) {
    options = options || {};
    var normalized = normalizeState(nextState);
    var desiredProtocol = normalized.protocol;
    var desiredTab = desiredProtocol === 'ocra' ? normalized.tab : lastOcraTab;

    var isSameProtocol = desiredProtocol === currentProtocol;
    var isSameOcraTab = desiredProtocol !== 'ocra' || desiredTab === lastOcraTab;

    if (!isSameProtocol || !isSameOcraTab) {
      setActiveProtocol(desiredProtocol, desiredProtocol === 'ocra' ? normalized.tab : undefined, {
        allowAutoOpen: options.allowAutoOpen,
        resetSection: options.resetSection,
        syncProtocolInfo: options.syncProtocolInfo,
      });
    } else if (desiredProtocol === 'ocra') {
      setActiveMode(normalized.tab);
    }

    if (desiredProtocol === 'ocra') {
      lastOcraTab = normalized.tab;
    }

    if (options.updateUrl) {
      pushUrlState(normalized, options);
    }
  }

  function parseStateFromLocation() {
    var params = new global.URLSearchParams(global.location.search);
    var protocol = params.get('protocol');
    var tab = params.get('tab');
    return normalizeState({ protocol: protocol, tab: tab });
  }

  function handleProtocolActivated(protocol) {
    var normalized = normalizeState({ protocol: protocol, tab: lastOcraTab });
    applyConsoleState(normalized, {
      updateUrl: true,
      syncProtocolInfo: false,
      allowAutoOpen: false,
    });
  }

  if (global.ProtocolInfo && operatorConsoleRoot) {
    global.ProtocolInfo.mount({
      root: operatorConsoleRoot,
      onProtocolActivated: handleProtocolActivated,
      refreshPreferences: true,
    });
  }

  protocolTabs.forEach(function (tab) {
    tab.addEventListener('click', function (event) {
      event.preventDefault();
      var protocol = tab.getAttribute('data-protocol-tab');
      if (!protocol || !allowedProtocols.has(protocol)) {
        return;
      }
      var nextState = protocol === 'ocra'
        ? { protocol: 'ocra', tab: lastOcraTab }
        : { protocol: protocol };
      applyConsoleState(nextState, { updateUrl: true, allowAutoOpen: true });
    });
  });

  if (evaluateButton) {
    evaluateButton.addEventListener('click', function (event) {
      event.preventDefault();
      applyConsoleState({ protocol: 'ocra', tab: 'evaluate' }, { updateUrl: true, allowAutoOpen: true });
    });
  }

  if (replayButton) {
    replayButton.addEventListener('click', function (event) {
      event.preventDefault();
      applyConsoleState({ protocol: 'ocra', tab: 'replay' }, { updateUrl: true, allowAutoOpen: true });
    });
  }

  global.addEventListener('popstate', function (event) {
    var state = event.state ? normalizeState(event.state) : parseStateFromLocation();
    applyConsoleState(state, { updateUrl: false, syncProtocolInfo: true });
  });

  applyConsoleState(parseStateFromLocation(), {
    updateUrl: true,
    replace: true,
    allowAutoOpen: true,
  });
})(window);
