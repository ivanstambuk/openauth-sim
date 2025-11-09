(function (global) {
  'use strict';

  var documentRef = global.document;
  var protocolTabs = Array.prototype.slice.call(documentRef.querySelectorAll('[data-protocol-tab]'));
  var protocolPanels = Array.prototype.slice.call(documentRef.querySelectorAll('[data-protocol-panel]'));
  var verboseConsole = global.VerboseTraceConsole || null;
  var fido2Panel = documentRef.querySelector("[data-protocol-panel='fido2']");
  var hotpPanel = documentRef.querySelector("[data-protocol-panel='hotp']");
  var totpPanel = documentRef.querySelector("[data-protocol-panel='totp']");
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
  var protocolAliases = new Map([
    ['eudiw', 'eudi-openid4vp'],
  ]);
  var allowedTabs = new Set(['evaluate', 'replay']);
  var allowedModes = new Set(['inline', 'stored']);
  var allowedTotpTabs = new Set(['evaluate', 'replay']);
  var allowedTotpModes = new Set(['stored', 'inline']);
  var allowedFido2Modes = new Set(['stored', 'inline', 'replay']);

  function canonicalProtocolValue(value) {
    if (!value) {
      return 'ocra';
    }
    var normalized = String(value).trim().toLowerCase();
    if (protocolAliases.has(normalized)) {
      return protocolAliases.get(normalized);
    }
    return allowedProtocols.has(normalized) ? normalized : 'ocra';
  }

  function protocolQueryValue(value) {
    if (!value) {
      return 'ocra';
    }
    return value;
  }

  var currentProtocol = 'ocra';
  var lastProtocolTabs = { ocra: 'evaluate', hotp: 'evaluate', totp: 'evaluate', fido2: 'evaluate' };
  var lastProtocolModes = { ocra: 'inline', hotp: 'inline', totp: 'inline', fido2: 'inline' };
  var lastTotpTab = 'evaluate';
  var lastTotpMode = 'inline';
  var lastTotpReplayMode = 'stored';
  var lastFido2Mode = 'inline';
  var lastFido2EvaluateMode = 'inline';
  var lastFido2ReplayMode = 'inline';

  if (operatorConsoleRoot) {
    var activeProtocolAttr = operatorConsoleRoot.getAttribute('data-active-protocol');
    if (activeProtocolAttr) {
      currentProtocol = canonicalProtocolValue(activeProtocolAttr);
    }
  }

  function clearVerboseTracePanel() {
    if (verboseConsole && typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    }
  }

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

  function ensureFido2Panel() {
    if (!fido2Panel || !documentRef.contains(fido2Panel)) {
      fido2Panel = documentRef.querySelector("[data-protocol-panel='fido2']");
    }
    return fido2Panel;
  }

  function rememberInitialFido2Mode(mode, replayMode) {
    var panel = ensureFido2Panel();
    if (allowedFido2Modes.has(mode)) {
      if (panel) {
        panel.setAttribute('data-initial-fido2-mode', mode);
        if (mode === 'replay' && allowedModes.has(replayMode)) {
          panel.setAttribute('data-initial-fido2-replay-mode', replayMode);
        } else {
          panel.removeAttribute('data-initial-fido2-replay-mode');
        }
      }
      return;
    }
    if (panel) {
      panel.removeAttribute('data-initial-fido2-mode');
    }
    if (panel) {
      panel.removeAttribute('data-initial-fido2-replay-mode');
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
    var previousMode = modeToggle.getAttribute('data-mode') || '';
    modeToggle.setAttribute('data-mode', mode);
    var isEvaluate = mode === 'evaluate';
    toggleButtonState(evaluateButton, isEvaluate);
    toggleButtonState(replayButton, !isEvaluate);
    setPanelVisibility(evaluatePanel, !isEvaluate);
    setPanelVisibility(replayPanel, isEvaluate);
    if (previousMode !== mode) {
      clearVerboseTracePanel();
    }
    rememberTab('ocra', mode);
  }

  function setActiveProtocol(protocol, ocraMode, options) {
    options = options || {};
    var canonicalProtocol = canonicalProtocolValue(protocol);
    var previousProtocol = currentProtocol;
    currentProtocol = canonicalProtocol;
    protocol = canonicalProtocol;
    if (previousProtocol !== protocol) {
      clearVerboseTracePanel();
    }
    

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
      var desiredOcraEvaluateMode =
          options && options.ocraEvaluateMode && allowedModes.has(options.ocraEvaluateMode)
              ? options.ocraEvaluateMode
              : getLastMode('ocra');
      if (global.OcraConsole && typeof global.OcraConsole.setMode === 'function') {
        global.OcraConsole.setMode(desiredOcraEvaluateMode, { broadcast: false, force: true });
      } else {
        if (modeToggle) {
          modeToggle.setAttribute('data-initial-ocra-mode', desiredOcraEvaluateMode);
        }
      }
    } else {
      setPanelVisibility(modeToggle, true);
      if (modeToggle) {
        modeToggle.removeAttribute('data-initial-ocra-mode');
      }
    }

    if ((protocol === 'ocra' || protocol === 'hotp') && allowedTabs.has(ocraMode)) {
      rememberTab(protocol, ocraMode);
    }

    if (protocol === 'hotp') {
      var hotpTargetTab = allowedTabs.has(ocraMode) ? ocraMode : getLastTab('hotp');
      var hotpEvaluateButton = documentRef.querySelector("[data-testid='hotp-panel-tab-evaluate']");
      var hotpReplayButton = documentRef.querySelector("[data-testid='hotp-panel-tab-replay']");
      if (hotpTargetTab === 'replay') {
        if (hotpReplayButton && hotpReplayButton.getAttribute('aria-selected') !== 'true') {
          hotpReplayButton.click();
        }
      } else if (hotpEvaluateButton && hotpEvaluateButton.getAttribute('aria-selected') !== 'true') {
        hotpEvaluateButton.click();
      }
      var desiredHotpMode =
          options && options.hotpMode && allowedModes.has(options.hotpMode)
              ? options.hotpMode
              : getLastMode('hotp');
      if (hotpTargetTab === 'replay') {
        if (hotpPanel) {
          hotpPanel.setAttribute('data-initial-replay-mode', desiredHotpMode);
        }
        if (global.HotpConsole && typeof global.HotpConsole.setReplayMode === 'function') {
          global.HotpConsole.setReplayMode(desiredHotpMode, { broadcast: false, force: true });
        }
      } else {
        if (hotpPanel) {
          hotpPanel.setAttribute('data-initial-evaluate-mode', desiredHotpMode);
        }
        if (global.HotpConsole && typeof global.HotpConsole.setMode === 'function') {
          global.HotpConsole.setMode(desiredHotpMode, { broadcast: false, force: true });
        }
      }
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
      } else if (totpPanel) {
        totpPanel.setAttribute('data-initial-totp-tab', desiredTab);
        totpPanel.setAttribute('data-initial-evaluate-mode', desiredMode);
        totpPanel.setAttribute('data-initial-replay-mode', desiredReplayMode);
      }
    }

    if (protocol === 'fido2') {
      var desiredFidoMode =
          options && options.fido2Mode && allowedFido2Modes.has(options.fido2Mode)
              ? options.fido2Mode
              : getLastFido2Mode();
      rememberFido2Mode(desiredFidoMode);
      var desiredFidoPanelMode =
          options && options.fido2PanelMode && allowedModes.has(options.fido2PanelMode)
              ? options.fido2PanelMode
              : undefined;
      var desiredFidoTab = desiredFidoMode === 'replay' ? 'replay' : 'evaluate';
      var targetEvaluateMode =
          desiredFidoMode === 'replay'
              ? getLastFido2EvaluateMode()
              : (allowedModes.has(desiredFidoMode) ? desiredFidoMode : getLastFido2EvaluateMode());
      if (desiredFidoMode !== 'replay'
          && allowedModes.has(desiredFidoPanelMode)) {
        targetEvaluateMode = desiredFidoPanelMode;
      }
      var targetReplayMode =
          desiredFidoMode === 'replay'
              ? (allowedModes.has(desiredFidoPanelMode)
                     ? desiredFidoPanelMode
                     : getLastFido2ReplayMode())
              : getLastFido2ReplayMode();
      rememberFido2EvaluateMode(targetEvaluateMode);
      rememberFido2ReplayMode(targetReplayMode);
      rememberInitialFido2Mode(
          desiredFidoMode === 'replay' ? 'replay' : targetEvaluateMode,
          desiredFidoMode === 'replay' ? targetReplayMode : undefined);
      if (global.Fido2Console) {
        if (typeof global.Fido2Console.setEvaluateMode === 'function') {
          global.Fido2Console.setEvaluateMode(targetEvaluateMode, { broadcast: false, force: true });
        }
        if (typeof global.Fido2Console.setReplayMode === 'function') {
          global.Fido2Console.setReplayMode(targetReplayMode, { broadcast: false, force: true });
        }
        if (typeof global.Fido2Console.setTab === 'function') {
          global.Fido2Console.setTab(desiredFidoTab, { broadcast: false, force: true });
        }
      }
    } else {
      rememberInitialFido2Mode(undefined);
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
    var protocol = canonicalProtocolValue(state && state.protocol);
    var normalized = { protocol: protocol };

    if (protocol === 'totp') {
      var tabCandidate = state && state.tab;
      if (!allowedTabs.has(tabCandidate)) {
        tabCandidate = getLastTotpTab();
      }
      var totpTab = allowedTabs.has(tabCandidate) ? tabCandidate : 'evaluate';
      normalized.tab = totpTab;
      var evalModeCandidate = getLastTotpMode();
      var replayModeCandidate = getLastTotpReplayMode();
      if (allowedModes.has(state && state.mode)) {
        if (totpTab === 'replay') {
          replayModeCandidate = state.mode;
        } else {
          evalModeCandidate = state.mode;
        }
      }
      normalized.totpTab = totpTab;
      normalized.totpMode =
          allowedTotpModes.has(evalModeCandidate) ? evalModeCandidate : getLastTotpMode();
      normalized.totpReplayMode =
          allowedTotpModes.has(replayModeCandidate) ? replayModeCandidate : getLastTotpReplayMode();
      normalized.mode =
          totpTab === 'replay' ? normalized.totpReplayMode : normalized.totpMode;
      return normalized;
    }

    if (protocol === 'fido2') {
      var fidoTabCandidate = allowedTabs.has(state && state.tab) ? state.tab : null;
      var fidoModeCandidate;
      if (allowedFido2Modes.has(state && state.fido2Mode)) {
        fidoModeCandidate = state.fido2Mode;
      } else if (allowedModes.has(state && state.mode)) {
        fidoModeCandidate = state.mode === 'stored' ? 'stored' : 'inline';
      } else {
        fidoModeCandidate = getLastFido2Mode();
      }
      if (!fidoTabCandidate) {
        if (fidoModeCandidate === 'replay') {
          fidoTabCandidate = 'replay';
        } else {
          var previousTab = getLastTab('fido2');
          fidoTabCandidate = allowedTabs.has(previousTab) ? previousTab : 'evaluate';
        }
      }
      if (!allowedTabs.has(fidoTabCandidate)) {
        fidoTabCandidate = 'evaluate';
      }
      var evaluateModeCandidate = getLastFido2EvaluateMode();
      var replayModeCandidate = getLastFido2ReplayMode();
      if (allowedModes.has(state && state.mode)) {
        if (fidoTabCandidate === 'replay') {
          replayModeCandidate = state.mode;
        } else {
          evaluateModeCandidate = state.mode;
        }
      }
      if (allowedFido2Modes.has(state && state.fido2Mode) && state.fido2Mode !== 'replay') {
        evaluateModeCandidate = state.fido2Mode;
      }
      if (fidoModeCandidate === 'replay') {
        fidoTabCandidate = 'replay';
      }
      if (!allowedModes.has(evaluateModeCandidate)) {
        evaluateModeCandidate = getLastFido2EvaluateMode();
      }
      if (!allowedModes.has(replayModeCandidate)) {
        replayModeCandidate = getLastFido2ReplayMode();
      }
      normalized.tab = fidoTabCandidate;
      normalized.fido2Mode =
          fidoTabCandidate === 'replay' ? 'replay' : evaluateModeCandidate;
      normalized.mode =
          fidoTabCandidate === 'replay' ? replayModeCandidate : evaluateModeCandidate;
      return normalized;
    }

    if (protocol === 'ocra' || protocol === 'hotp') {
      var panelTab =
          allowedTabs.has(state && state.tab) ? state.tab : getLastTab(protocol);
      normalized.tab = allowedTabs.has(panelTab) ? panelTab : 'evaluate';
      var panelMode =
          allowedModes.has(state && state.mode) ? state.mode : getLastMode(protocol);
      normalized.mode = allowedModes.has(panelMode) ? panelMode : 'inline';
      return normalized;
    }

    normalized.tab = allowedTabs.has(state && state.tab) ? state.tab : undefined;
    normalized.mode = allowedModes.has(state && state.mode) ? state.mode : undefined;
    return normalized;
  }

  function buildSearch(state) {
    var params = new global.URLSearchParams();
    params.set('protocol', protocolQueryValue(state.protocol));
    if (state.tab && allowedTabs.has(state.tab)) {
      params.set('tab', state.tab);
    }
    if (state.mode && allowedModes.has(state.mode)) {
      params.set('mode', state.mode);
    }
    var rendered = params.toString();
    return rendered ? '?' + rendered : global.location.search;
  }

  function pushUrlState(state, options) {
    var normalized = normalizeState(state);
    var search = buildSearch(normalized);
    var url = global.location.pathname + search;
    var historyState = { protocol: normalized.protocol };
    if (normalized.tab && allowedTabs.has(normalized.tab)) {
      historyState.tab = normalized.tab;
    }
    if (normalized.mode && allowedModes.has(normalized.mode)) {
      historyState.mode = normalized.mode;
    }
    if (normalized.protocol === 'fido2') {
      historyState.fido2Mode = normalized.fido2Mode;
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
    var desiredTab = normalized.tab || getLastTab(desiredProtocol);
    var desiredMode = normalized.mode || getLastMode(desiredProtocol);
    var previousTab = getLastTab(desiredProtocol);

    if ((desiredProtocol === 'ocra' || desiredProtocol === 'hotp' || desiredProtocol === 'totp')
        && !allowedTabs.has(desiredTab)) {
      desiredTab = previousTab;
      normalized.tab = desiredTab;
    }

    var isSameProtocol = desiredProtocol === currentProtocol;
    var isSameTab = true;
    if (desiredProtocol === 'ocra' || desiredProtocol === 'hotp' || desiredProtocol === 'totp'
        || desiredProtocol === 'fido2') {
      isSameTab = desiredTab === previousTab;
    }

    if (!isSameProtocol || !isSameTab || desiredProtocol === 'fido2') {
      setActiveProtocol(
          desiredProtocol,
          desiredProtocol === 'ocra' || desiredProtocol === 'hotp' ? desiredTab : undefined,
          {
            allowAutoOpen: options.allowAutoOpen,
            resetSection: options.resetSection,
            syncProtocolInfo: options.syncProtocolInfo,
            ocraEvaluateMode: desiredProtocol === 'ocra' ? normalized.mode : undefined,
            hotpMode: desiredProtocol === 'hotp' ? normalized.mode : undefined,
            totpTab: desiredProtocol === 'totp' ? normalized.totpTab : undefined,
            totpMode: desiredProtocol === 'totp' ? normalized.totpMode : undefined,
            totpReplayMode: desiredProtocol === 'totp' ? normalized.totpReplayMode : undefined,
            fido2Mode: desiredProtocol === 'fido2' ? normalized.fido2Mode : undefined,
            fido2PanelMode: desiredProtocol === 'fido2' ? normalized.mode : undefined,
          });
    } else if (desiredProtocol === 'ocra') {
      setActiveMode(desiredTab);
    }

  if (desiredProtocol === 'ocra' || desiredProtocol === 'hotp' || desiredProtocol === 'totp'
      || desiredProtocol === 'fido2') {
    rememberTab(desiredProtocol, desiredTab);
    rememberMode(desiredProtocol, desiredMode);
  }
  if (desiredProtocol === 'totp' && desiredTab === 'replay'
      && allowedTotpModes.has(desiredMode)) {
    rememberTotpReplayMode(desiredMode);
  }

  if (desiredProtocol === 'ocra' && desiredTab === 'replay' && allowedModes.has(normalized.mode)) {
    global.__ocraReplayInitialMode = normalized.mode;
  } else if (desiredProtocol !== 'ocra') {
    global.__ocraReplayInitialMode = undefined;
  }

    if (desiredProtocol === 'totp') {
      rememberTotpTab(normalized.totpTab);
      rememberTotpMode(normalized.totpMode);
      rememberTotpReplayMode(normalized.totpReplayMode);
      if (global.TotpConsole && typeof global.TotpConsole.setTab === 'function') {
        global.TotpConsole.setTab(normalized.totpTab, { broadcast: false, force: true });
        if (typeof global.TotpConsole.setMode === 'function') {
          global.TotpConsole.setMode(normalized.totpMode, { broadcast: false, force: true });
        }
        if (typeof global.TotpConsole.setReplayMode === 'function') {
          global.TotpConsole.setReplayMode(
              normalized.totpReplayMode, { broadcast: false, force: true });
        }
      }
    }

    if (desiredProtocol === 'fido2') {
      rememberFido2Mode(normalized.fido2Mode);
      if (normalized.fido2Mode === 'replay') {
        rememberFido2ReplayMode(normalized.mode);
        rememberInitialFido2Mode('replay', normalized.mode);
      } else {
        rememberFido2EvaluateMode(normalized.mode);
        rememberInitialFido2Mode(normalized.mode);
      }
      if (global.Fido2Console) {
        if (typeof global.Fido2Console.setEvaluateMode === 'function') {
          global.Fido2Console.setEvaluateMode(
              getLastFido2EvaluateMode(), { broadcast: false, force: true });
        }
        if (typeof global.Fido2Console.setReplayMode === 'function') {
          global.Fido2Console.setReplayMode(
              getLastFido2ReplayMode(), { broadcast: false, force: true });
        }
        if (typeof global.Fido2Console.setTab === 'function') {
          global.Fido2Console.setTab(
              normalized.fido2Mode === 'replay' ? 'replay' : 'evaluate',
              { broadcast: false, force: true });
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
    var mode = params.get('mode');
    if (!protocol && operatorConsoleRoot) {
      var attr = operatorConsoleRoot.getAttribute('data-active-protocol');
      if (attr) {
        protocol = canonicalProtocolValue(attr);
      }
    }
    return normalizeState({
      protocol: protocol,
      tab: tab,
      mode: mode,
    });
  }

  function handleProtocolActivated(protocol) {
    var initialState = { protocol: protocol, tab: 'evaluate', mode: 'inline' };
    if (protocol === 'totp') {
      initialState.totpTab = 'evaluate';
      initialState.totpMode = 'inline';
      initialState.totpReplayMode = getLastTotpReplayMode();
    } else if (protocol === 'fido2') {
      initialState.fido2Mode = 'inline';
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
      var protocolAttr = tab.getAttribute('data-protocol-tab');
      if (!protocolAttr) {
        return;
      }
      var protocol = canonicalProtocolValue(protocolAttr);
      var nextState;
      if (protocol === 'ocra') {
        nextState = { protocol: 'ocra', tab: 'evaluate', mode: 'inline' };
      } else if (protocol === 'hotp') {
        nextState = { protocol: 'hotp', tab: 'evaluate', mode: 'inline' };
      } else if (protocol === 'totp') {
        nextState = {
          protocol: 'totp',
          tab: 'evaluate',
          mode: 'inline',
          totpTab: 'evaluate',
          totpMode: 'inline',
          totpReplayMode: getLastTotpReplayMode(),
        };
      } else if (protocol === 'fido2') {
        nextState = {
          protocol: 'fido2',
          tab: 'evaluate',
          mode: 'inline',
          fido2Mode: 'inline',
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
    if (totpPanel) {
      totpPanel.setAttribute('data-initial-evaluate-mode', mode);
    }
    if (currentProtocol !== 'totp') {
      return;
    }
    rememberTab('totp', 'evaluate');
    rememberMode('totp', mode);
    pushUrlState(
        {
          protocol: 'totp',
          tab: 'evaluate',
          mode: mode,
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:totp-tab-changed', function (event) {
    var tab = event && event.detail ? event.detail.tab : null;
    if (!allowedTotpTabs.has(tab)) {
      return;
    }
    rememberTotpTab(tab);
    rememberTab('totp', tab);
    if (totpPanel) {
      totpPanel.setAttribute('data-initial-totp-tab', tab);
    }
    if (currentProtocol !== 'totp') {
      return;
    }
    var activeMode = tab === 'replay' ? getLastTotpReplayMode() : getLastTotpMode();
    rememberMode('totp', activeMode);
    pushUrlState(
        {
          protocol: 'totp',
          tab: tab,
          mode: activeMode,
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:totp-replay-mode-changed', function (event) {
    var mode = event && event.detail ? event.detail.mode : null;
    if (!allowedTotpModes.has(mode)) {
      return;
    }
    rememberTotpReplayMode(mode);
    if (totpPanel) {
      totpPanel.setAttribute('data-initial-replay-mode', mode);
    }
    if (currentProtocol !== 'totp') {
      return;
    }
    var activeTab = getLastTab('totp');
    if (activeTab === 'replay') {
      rememberMode('totp', mode);
    }
    pushUrlState(
        {
          protocol: 'totp',
          tab: activeTab,
          mode: activeTab === 'replay' ? mode : getLastTotpMode(),
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:ocra-replay-mode-changed', function (event) {
    var mode = event && event.detail ? event.detail.mode : null;
    if (!allowedModes.has(mode)) {
      return;
    }
    rememberMode('ocra', mode);
    if (currentProtocol !== 'ocra') {
      return;
    }
    if (getLastTab('ocra') !== 'replay') {
      return;
    }
    pushUrlState(
        {
          protocol: 'ocra',
          tab: 'replay',
          mode: mode,
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:hotp-tab-changed', function (event) {
    var tab = event && event.detail ? event.detail.tab : null;
    if (!allowedTabs.has(tab)) {
      return;
    }
    rememberTab('hotp', tab);
    var modeCandidate = getLastMode('hotp');
    if (tab === 'replay') {
      var replayMode =
          global.HotpConsole && typeof global.HotpConsole.getReplayMode === 'function'
              ? global.HotpConsole.getReplayMode()
              : (hotpPanel
                     ? normalizeModeAttribute(hotpPanel.getAttribute('data-initial-replay-mode'))
                     : null);
      if (allowedModes.has(replayMode)) {
        modeCandidate = replayMode;
      }
    }
    rememberMode('hotp', modeCandidate);
    if (currentProtocol !== 'hotp') {
      return;
    }
    pushUrlState(
        {
          protocol: 'hotp',
          tab: tab,
          mode: modeCandidate,
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:hotp-replay-mode-changed', function (event) {
    var mode = event && event.detail ? event.detail.mode : null;
    if (!allowedModes.has(mode)) {
      return;
    }
    if (hotpPanel) {
      hotpPanel.setAttribute('data-initial-replay-mode', mode);
    }
    if (currentProtocol !== 'hotp') {
      return;
    }
    var activeTab = getLastTab('hotp');
    if (activeTab !== 'replay') {
      return;
    }
    rememberMode('hotp', mode);
    pushUrlState(
        {
          protocol: 'hotp',
          tab: 'replay',
          mode: mode,
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:fido2-tab-changed', function (event) {
    var tab = event && event.detail ? event.detail.tab : null;
    if (!allowedTabs.has(tab)) {
      return;
    }
    rememberTab('fido2', tab);
    var activeMode = tab === 'replay' ? getLastFido2ReplayMode() : getLastFido2EvaluateMode();
    if (tab === 'replay') {
      rememberFido2Mode('replay');
      rememberInitialFido2Mode('replay', activeMode);
    } else {
      rememberFido2Mode(activeMode);
      rememberInitialFido2Mode(activeMode);
    }
    rememberMode('fido2', activeMode);
    if (currentProtocol !== 'fido2') {
      return;
    }
    pushUrlState(
        {
          protocol: 'fido2',
          tab: tab,
          mode: activeMode,
          fido2Mode: tab === 'replay' ? 'replay' : activeMode,
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:fido2-evaluate-mode-changed', function (event) {
    var mode = event && event.detail ? event.detail.mode : null;
    if (!allowedModes.has(mode)) {
      return;
    }
    rememberFido2EvaluateMode(mode);
    if (getLastTab('fido2') !== 'replay') {
      rememberFido2Mode(mode);
    }
    if (currentProtocol !== 'fido2') {
      rememberInitialFido2Mode(mode);
      return;
    }
    rememberInitialFido2Mode(mode);
    var activeTab = getLastTab('fido2');
    if (activeTab !== 'evaluate') {
      return;
    }
    rememberMode('fido2', mode);
    pushUrlState(
        {
          protocol: 'fido2',
          tab: 'evaluate',
          mode: mode,
          fido2Mode: mode,
        },
        { replace: Boolean(event.detail && event.detail.replace) });
  });

  global.addEventListener('operator:fido2-replay-mode-changed', function (event) {
    var mode = event && event.detail ? event.detail.mode : null;
    if (!allowedModes.has(mode)) {
      return;
    }
    rememberFido2ReplayMode(mode);
    rememberInitialFido2Mode('replay', mode);
    if (currentProtocol !== 'fido2') {
      return;
    }
    var activeTab = getLastTab('fido2');
    if (activeTab !== 'replay') {
      return;
    }
    rememberFido2Mode('replay');
    rememberMode('fido2', mode);
    pushUrlState(
        {
          protocol: 'fido2',
          tab: 'replay',
          mode: mode,
          fido2Mode: 'replay',
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
  initializeModeListeners();


  function initializeModeListeners() {
    var ocraEvaluateModeForm = documentRef.querySelector("[data-testid='mode-toggle']");
    if (ocraEvaluateModeForm && !ocraEvaluateModeForm.hasAttribute('data-mode-listener')) {
      ocraEvaluateModeForm.setAttribute('data-mode-listener', 'true');
      ocraEvaluateModeForm.addEventListener('change', function (event) {
        var target = event && event.target ? event.target : null;
        if (!target || target.name !== 'mode') {
          return;
        }
        var value = (target.value || '').toLowerCase();
        var nextMode = value === 'inline' ? 'inline' : 'stored';
        rememberMode('ocra', nextMode);
        if (currentProtocol === 'ocra' && getLastTab('ocra') === 'evaluate') {
          pushUrlState({ protocol: 'ocra', tab: 'evaluate', mode: nextMode }, { replace: true });
        }
      });
    }

    var hotpModeToggle = documentRef.querySelector("[data-testid='hotp-mode-toggle']");
    if (hotpModeToggle && !hotpModeToggle.hasAttribute('data-mode-listener')) {
      hotpModeToggle.setAttribute('data-mode-listener', 'true');
      hotpModeToggle.addEventListener('change', function (event) {
        var target = event && event.target ? event.target : null;
        if (!target || target.getAttribute('data-testid') == null) {
          return;
        }
        var testId = target.getAttribute('data-testid');
        var nextMode;
        if (testId === 'hotp-mode-select-inline') {
          nextMode = 'inline';
        } else if (testId === 'hotp-mode-select-stored') {
          nextMode = 'stored';
        } else {
          return;
        }
        rememberMode('hotp', nextMode);
        if (hotpPanel) {
          hotpPanel.setAttribute('data-initial-evaluate-mode', nextMode);
        }
        if (currentProtocol === 'hotp' && getLastTab('hotp') === 'evaluate') {
          pushUrlState({ protocol: 'hotp', tab: 'evaluate', mode: nextMode }, { replace: true });
        }
      });
    }
  }

  function getLastTab(protocol) {
    if (protocol === 'ocra' || protocol === 'hotp' || protocol === 'totp' || protocol === 'fido2') {
      return lastProtocolTabs[protocol] || 'evaluate';
    }
    return undefined;
  }

  function rememberTab(protocol, tab) {
    if ((protocol === 'ocra' || protocol === 'hotp') && allowedTabs.has(tab)) {
      lastProtocolTabs[protocol] = tab;
    }
    if (protocol === 'totp' && allowedTabs.has(tab)) {
      lastProtocolTabs[protocol] = tab;
    }
    if (protocol === 'fido2' && allowedTabs.has(tab)) {
      lastProtocolTabs[protocol] = tab;
    }
  }

  function getLastMode(protocol) {
    if (protocol === 'totp') {
      var tab = lastProtocolTabs[protocol] || 'evaluate';
      if (tab === 'replay') {
        return allowedTotpModes.has(lastTotpReplayMode) ? lastTotpReplayMode : 'stored';
      }
      return allowedTotpModes.has(lastTotpMode) ? lastTotpMode : 'inline';
    }
    if (protocol === 'fido2') {
      var fidoTab = lastProtocolTabs[protocol] || 'evaluate';
      if (fidoTab === 'replay') {
        return getLastFido2ReplayMode();
      }
      return getLastFido2EvaluateMode();
    }
    return allowedModes.has(lastProtocolModes[protocol])
        ? lastProtocolModes[protocol]
        : 'inline';
  }

  function normalizeModeAttribute(value) {
    if (typeof value !== 'string') {
      return null;
    }
    var normalized = value.trim().toLowerCase();
    if (allowedModes.has(normalized)) {
      return normalized;
    }
    return null;
  }

  function rememberMode(protocol, mode) {
    if (!allowedModes.has(mode)) {
      return;
    }
    lastProtocolModes[protocol] = mode;
    if (protocol === 'totp') {
      if ((lastProtocolTabs[protocol] || 'evaluate') === 'replay') {
        rememberTotpReplayMode(mode);
      } else {
        rememberTotpMode(mode);
      }
      return;
    }
    if (protocol === 'fido2') {
      if ((lastProtocolTabs[protocol] || 'evaluate') === 'replay') {
        rememberFido2ReplayMode(mode);
      } else {
        rememberFido2EvaluateMode(mode);
      }
      return;
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
    return allowedTotpModes.has(lastTotpMode) ? lastTotpMode : 'inline';
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

  function getLastFido2Mode() {
    return allowedFido2Modes.has(lastFido2Mode) ? lastFido2Mode : 'inline';
  }

  function rememberFido2Mode(mode) {
    if (allowedFido2Modes.has(mode)) {
      lastFido2Mode = mode;
    }
  }

  function getLastFido2EvaluateMode() {
    return allowedModes.has(lastFido2EvaluateMode) ? lastFido2EvaluateMode : 'inline';
  }

  function rememberFido2EvaluateMode(mode) {
    if (allowedModes.has(mode)) {
      lastFido2EvaluateMode = mode;
    }
  }

  function getLastFido2ReplayMode() {
    return allowedModes.has(lastFido2ReplayMode) ? lastFido2ReplayMode : 'inline';
  }

  function rememberFido2ReplayMode(mode) {
    if (allowedModes.has(mode)) {
      lastFido2ReplayMode = mode;
    }
  }
})(window);
