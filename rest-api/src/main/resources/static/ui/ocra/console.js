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
  var allowedTotpTabs = new Set(['evaluate', 'replay']);
  var allowedTotpModes = new Set(['stored', 'inline']);

  var currentProtocol = 'ocra';
  var lastProtocolTabs = { ocra: 'evaluate', hotp: 'evaluate' };
  var lastTotpTab = 'evaluate';
  var lastTotpMode = 'stored';
  var lastTotpReplayMode = 'stored';

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
    rememberTab('ocra', mode);
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
      setActiveMode(ocraMode || getLastTab('ocra') || 'evaluate');
    } else {
      setPanelVisibility(modeToggle, true);
    }

    if ((protocol === 'ocra' || protocol === 'hotp') && allowedTabs.has(ocraMode)) {
      rememberTab(protocol, ocraMode);
    }

    if (protocol === 'totp') {
      var desiredTab = options && options.totpTab ? options.totpTab : getLastTotpTab();
      var desiredMode = options && options.totpMode ? options.totpMode : getLastTotpMode();
      var desiredReplayMode =
          options && options.totpReplayMode ? options.totpReplayMode : getLastTotpReplayMode();
      if (global.TotpConsole && typeof global.TotpConsole.setTab === 'function') {
        global.TotpConsole.setTab(desiredTab, { broadcast: false, force: true });
        if (typeof global.TotpConsole.setMode === 'function') {
          global.TotpConsole.setMode(desiredMode, { broadcast: false, force: true });
        }
        if (typeof global.TotpConsole.setReplayMode === 'function') {
          global.TotpConsole.setReplayMode(desiredReplayMode, { broadcast: false, force: true });
        }
      }
    }

    if (options.syncProtocolInfo !== false && global.ProtocolInfo) {
      global.ProtocolInfo.setProtocol(protocol, {
        autoOpen: Boolean(options.allowAutoOpen),
        resetSection: Boolean(options.resetSection),
        notifyHost: false,
      });
    }

    try {
      var protocolEventDetail = { protocol: protocol };
      if (protocol === 'totp') {
        protocolEventDetail.totpTab =
            options && options.totpTab ? options.totpTab : getLastTotpTab();
        protocolEventDetail.totpMode =
            options && options.totpMode ? options.totpMode : getLastTotpMode();
        protocolEventDetail.totpReplayMode =
            options && options.totpReplayMode ? options.totpReplayMode : getLastTotpReplayMode();
      }
      var protocolEvent = new global.CustomEvent('operator:protocol-activated', {
        detail: protocolEventDetail,
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
    if (protocol === 'ocra' || protocol === 'hotp') {
      var tab = allowedTabs.has(state && state.tab) ? state.tab : getLastTab(protocol);
      return { protocol: protocol, tab: tab };
    }
    if (protocol === 'totp') {
      var tab = allowedTotpTabs.has(state && state.totpTab) ? state.totpTab : getLastTotpTab();
      var mode = allowedTotpModes.has(state && state.totpMode) ? state.totpMode : getLastTotpMode();
      var replayMode = allowedTotpModes.has(state && state.totpReplayMode)
          ? state.totpReplayMode
          : getLastTotpReplayMode();
      return { protocol: protocol, totpTab: tab, totpMode: mode, totpReplayMode: replayMode };
    }
    return { protocol: protocol };
  }

  function buildSearch(state) {
    var params = new global.URLSearchParams();
    params.set('protocol', state.protocol);
    if (state.protocol === 'ocra' || state.protocol === 'hotp') {
      params.set('tab', state.tab);
    }
    if (state.protocol === 'totp') {
      var tab = allowedTotpTabs.has(state.totpTab) ? state.totpTab : getLastTotpTab();
      params.set('totpTab', tab);
      var mode = allowedTotpModes.has(state.totpMode) ? state.totpMode : getLastTotpMode();
      params.set('totpMode', mode);
      var replayMode = allowedTotpModes.has(state.totpReplayMode)
          ? state.totpReplayMode
          : getLastTotpReplayMode();
      params.set('totpReplayMode', replayMode);
    }
    var rendered = params.toString();
    return rendered ? '?' + rendered : global.location.search;
  }

  function pushUrlState(state, options) {
    var normalized = normalizeState(state);
    var search = buildSearch(normalized);
    var url = global.location.pathname + search;
    var historyState;
    if (normalized.protocol === 'ocra' || normalized.protocol === 'hotp') {
      historyState = { protocol: normalized.protocol, tab: normalized.tab };
    } else if (normalized.protocol === 'totp') {
      historyState = {
        protocol: normalized.protocol,
        totpTab: normalized.totpTab,
        totpMode: normalized.totpMode,
        totpReplayMode: normalized.totpReplayMode,
      };
    } else {
      historyState = { protocol: normalized.protocol };
    }

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
    var desiredTab = normalized.tab;
    var previousTab = getLastTab(desiredProtocol);

    if ((desiredProtocol === 'ocra' || desiredProtocol === 'hotp') && !allowedTabs.has(desiredTab)) {
      desiredTab = previousTab;
      normalized.tab = desiredTab;
    }

    var isSameProtocol = desiredProtocol === currentProtocol;
    var isSameTab = true;
    if (desiredProtocol === 'ocra' || desiredProtocol === 'hotp') {
      isSameTab = desiredTab === previousTab;
    }

    if (!isSameProtocol || !isSameTab) {
      setActiveProtocol(
          desiredProtocol,
          desiredProtocol === 'ocra' || desiredProtocol === 'hotp' ? desiredTab : undefined,
          {
            allowAutoOpen: options.allowAutoOpen,
            resetSection: options.resetSection,
            syncProtocolInfo: options.syncProtocolInfo,
            totpMode: desiredProtocol === 'totp' ? normalized.totpMode : undefined,
          });
    } else if (desiredProtocol === 'ocra') {
      setActiveMode(desiredTab);
    }

    if (desiredProtocol === 'ocra' || desiredProtocol === 'hotp') {
      rememberTab(desiredProtocol, desiredTab);
    }

    if (desiredProtocol === 'totp') {
      var desiredTab = allowedTotpTabs.has(normalized.totpTab)
        ? normalized.totpTab
        : getLastTotpTab();
      var desiredMode = allowedTotpModes.has(normalized.totpMode)
        ? normalized.totpMode
        : getLastTotpMode();
      var desiredReplayMode = allowedTotpModes.has(normalized.totpReplayMode)
        ? normalized.totpReplayMode
        : getLastTotpReplayMode();
      rememberTotpTab(desiredTab);
      rememberTotpMode(desiredMode);
      rememberTotpReplayMode(desiredReplayMode);
      if (global.TotpConsole && typeof global.TotpConsole.setTab === 'function') {
        global.TotpConsole.setTab(desiredTab, { broadcast: false, force: true });
        if (typeof global.TotpConsole.setMode === 'function') {
          global.TotpConsole.setMode(desiredMode, { broadcast: false, force: true });
        }
        if (typeof global.TotpConsole.setReplayMode === 'function') {
          global.TotpConsole.setReplayMode(desiredReplayMode, { broadcast: false, force: true });
        }
      }
    }

    if (options.updateUrl) {
      pushUrlState(normalized, options);
    }
  }

  function parseStateFromLocation() {
    var params = new global.URLSearchParams(global.location.search);
    var protocol = params.get('protocol');
    var tab = params.get('tab');
    var totpTab = params.get('totpTab');
    var totpMode = params.get('totpMode');
    var totpReplayMode = params.get('totpReplayMode');
    return normalizeState({
      protocol: protocol,
      tab: tab,
      totpTab: totpTab,
      totpMode: totpMode,
      totpReplayMode: totpReplayMode,
    });
  }

  function handleProtocolActivated(protocol) {
    var initialState = { protocol: protocol };
    if (protocol === 'ocra' || protocol === 'hotp') {
      initialState.tab = getLastTab(protocol);
    } else if (protocol === 'totp') {
      initialState.totpTab = getLastTotpTab();
      initialState.totpMode = getLastTotpMode();
      initialState.totpReplayMode = getLastTotpReplayMode();
    }
    var normalized = normalizeState(initialState);
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
      var nextState;
      if (protocol === 'ocra') {
        nextState = { protocol: 'ocra', tab: getLastTab('ocra') };
      } else if (protocol === 'hotp') {
        nextState = { protocol: 'hotp', tab: getLastTab('hotp') };
      } else if (protocol === 'totp') {
        nextState = {
          protocol: 'totp',
          totpTab: getLastTotpTab(),
          totpMode: getLastTotpMode(),
          totpReplayMode: getLastTotpReplayMode(),
        };
      } else {
        nextState = { protocol: protocol };
      }
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

  global.addEventListener('operator:totp-mode-changed', function (event) {
    var mode = event && event.detail ? event.detail.mode : null;
    if (!allowedTotpModes.has(mode)) {
      return;
    }
    rememberTotpMode(mode);
    if (currentProtocol !== 'totp') {
      return;
    }
    pushUrlState(
        { protocol: 'totp', totpMode: mode },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:totp-tab-changed', function (event) {
    var tab = event && event.detail ? event.detail.tab : null;
    if (!allowedTotpTabs.has(tab)) {
      return;
    }
    rememberTotpTab(tab);
    if (currentProtocol !== 'totp') {
      return;
    }
    pushUrlState(
        {
          protocol: 'totp',
          totpTab: tab,
          totpMode: getLastTotpMode(),
          totpReplayMode: getLastTotpReplayMode(),
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:totp-replay-mode-changed', function (event) {
    var mode = event && event.detail ? event.detail.mode : null;
    if (!allowedTotpModes.has(mode)) {
      return;
    }
    rememberTotpReplayMode(mode);
    if (currentProtocol !== 'totp') {
      return;
    }
    pushUrlState(
        {
          protocol: 'totp',
          totpTab: getLastTotpTab(),
          totpMode: getLastTotpMode(),
          totpReplayMode: mode,
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('popstate', function (event) {
    var state = event.state ? normalizeState(event.state) : parseStateFromLocation();
    applyConsoleState(state, { updateUrl: false, syncProtocolInfo: true });
  });

  applyConsoleState(parseStateFromLocation(), {
    updateUrl: true,
    replace: true,
    allowAutoOpen: true,
  });

  function getLastTab(protocol) {
    if (protocol === 'ocra' || protocol === 'hotp') {
      return lastProtocolTabs[protocol] || 'evaluate';
    }
    return undefined;
  }

  function rememberTab(protocol, tab) {
    if ((protocol === 'ocra' || protocol === 'hotp') && allowedTabs.has(tab)) {
      lastProtocolTabs[protocol] = tab;
    }
  }

  function getLastTotpTab() {
    return allowedTotpTabs.has(lastTotpTab) ? lastTotpTab : 'evaluate';
  }

  function rememberTotpTab(tab) {
    if (allowedTotpTabs.has(tab)) {
      lastTotpTab = tab;
    }
  }

  function getLastTotpMode() {
    return allowedTotpModes.has(lastTotpMode) ? lastTotpMode : 'stored';
  }

  function rememberTotpMode(mode) {
    if (allowedTotpModes.has(mode)) {
      lastTotpMode = mode;
    }
  }

  function getLastTotpReplayMode() {
    return allowedTotpModes.has(lastTotpReplayMode) ? lastTotpReplayMode : 'stored';
  }

  function rememberTotpReplayMode(mode) {
    if (allowedTotpModes.has(mode)) {
      lastTotpReplayMode = mode;
    }
  }
})(window);
