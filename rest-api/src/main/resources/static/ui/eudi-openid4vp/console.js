(function (global) {
  'use strict';

  var documentRef = global.document;
  var panel = documentRef.querySelector('[data-protocol-panel="eudi-openid4vp"]');
  if (!panel) {
    return;
  }

  var state = {
    tab: 'evaluate',
    evaluateMode: 'inline',
    replayMode: 'inline',
  };
  var protocolQueryValue = 'eudi-openid4vp';

  var verboseConsole = global.VerboseTraceConsole || null;
  var locationRef = global.location || null;
  var historyRef = global.history || null;
  var initialSearch = typeof global.__operatorConsoleInitialSearch === 'string'
          && global.__operatorConsoleInitialSearch
      ? global.__operatorConsoleInitialSearch
      : (locationRef && typeof locationRef.search === 'string' ? locationRef.search : '');
  var hydratedInitialState = false;
  var initialTabAttr = panel.getAttribute('data-initial-tab') || '';
  var initialModeAttr = panel.getAttribute('data-initial-mode') || '';
  if (!initialSearch && (initialTabAttr || initialModeAttr)) {
    var initialParams = new global.URLSearchParams();
    initialParams.set('protocol', 'eudiw');
    if (initialTabAttr) {
      initialParams.set('tab', initialTabAttr);
    }
    if (initialModeAttr) {
      initialParams.set('mode', initialModeAttr);
    }
    initialSearch = '?' + initialParams.toString();
    protocolQueryValue = 'eudiw';
  }

  var dataset = readConsoleData();
  global.EudiwConsoleDataSnapshot = dataset;
  var trustedAuthorityMap = buildTrustedAuthorityMap(dataset.trustedAuthorities || []);
  var walletPresetMap = buildWalletPresetMap(dataset.walletPresets || []);
  var storedPresentationMap = buildStoredPresentationMap(dataset.storedPresentations || []);

  var evaluateTabButton = panel.querySelector('[data-testid="eudiw-panel-tab-evaluate"]');
  var replayTabButton = panel.querySelector('[data-testid="eudiw-panel-tab-replay"]');
  var evaluatePanel = panel.querySelector('[data-testid="eudiw-evaluate-panel"]');
  var replayPanel = panel.querySelector('[data-testid="eudiw-replay-panel"]');
  var profileSelect = panel.querySelector('[data-testid="eudiw-profile-select"]');
  var baselineBanner = panel.querySelector('[data-testid="eudiw-baseline-banner"]');
  var dcqlSelect = panel.querySelector('[data-testid="eudiw-dcql-preset-select"]');
  var dcqlPreview = panel.querySelector('[data-testid="eudiw-dcql-preview"]');
  var trustedAuthoritiesList = panel.querySelector('[data-testid="eudiw-trusted-authorities"]');
  var responseModeSelect = panel.querySelector('[data-testid="eudiw-response-mode-select"]');
  var inlineSampleSelect = panel.querySelector('[data-testid="eudiw-inline-sample-select"]');
  var inlineSampleApply = panel.querySelector('[data-testid="eudiw-inline-sample-apply"]');
  var inlineCredentialId = panel.querySelector('[data-testid="eudiw-inline-credential-id"]');
  var inlineFormat = panel.querySelector('[data-testid="eudiw-inline-format"]');
  var inlinePolicies = panel.querySelector('[data-testid="eudiw-inline-policies"]');
  var inlineSdJwt = panel.querySelector('[data-testid="eudiw-inline-sdjwt"]');
  var inlineDisclosures = panel.querySelector('[data-testid="eudiw-inline-disclosures"]');
  var inlineKbJwt = panel.querySelector('[data-testid="eudiw-inline-kbjwt"]');
  var inlineDeviceResponse = panel.querySelector('[data-testid="eudiw-inline-device-response"]');
  var walletPresetSelect = panel.querySelector('[data-testid="eudiw-wallet-preset-select"]');
  var evaluateModeToggle = panel.querySelector('[data-testid="eudiw-evaluate-mode-toggle"]');
  var replayModeToggle = panel.querySelector('[data-testid="eudiw-replay-mode-toggle"]');
  var evaluateInlineSection = panel.querySelector('[data-testid="eudiw-evaluate-inline-section"]');
  var evaluateStoredSection = panel.querySelector('[data-testid="eudiw-evaluate-stored-section"]');
  var replayInlineSection = panel.querySelector('[data-testid="eudiw-replay-inline-section"]');
  var replayStoredSection = panel.querySelector('[data-testid="eudiw-replay-stored-section"]');
  var seedActions = panel.querySelector('[data-testid="eudiw-seed-actions"]');
  var replayStoredSelect = panel.querySelector('[data-testid="eudiw-replay-stored-select"]');
  var replayVpTokenInput = panel.querySelector('[data-testid="eudiw-replay-vp-token"]');
  var replayResponseModeSelect = panel.querySelector('[data-testid="eudiw-replay-response-mode"]');
  var replayPolicyInput = panel.querySelector('[data-testid="eudiw-replay-policy"]');
  var simulateButton = panel.querySelector('[data-testid="eudiw-simulate-button"]');
  var validateButton = panel.querySelector('[data-testid="eudiw-validate-button"]');
  var csrfInputs = panel.querySelectorAll('input[name="_csrf"]');
  var qrAsciiNode = panel.querySelector('[data-testid="eudiw-qr-ascii"]');
  var qrAltUrlInput = panel.querySelector('[data-testid="eudiw-qr-alt-url"]');
  var resultStatusNode = panel.querySelector('[data-testid="eudiw-result-status"]');
  var resultMessageNode = panel.querySelector('[data-result-message]');
  var resultHintNode = panel.querySelector('[data-result-hint]');
  var resultPresentationContainer = panel.querySelector('[data-testid="eudiw-result-presentations"]');
  var replayStatusNode = panel.querySelector('[data-testid="eudiw-replay-status"]');
  var replayProblemDetailNode = panel.querySelector('[data-testid="eudiw-replay-problem-detail"]');
  var replayPresentationContainer = panel.querySelector('[data-testid="eudiw-replay-presentations"]');
  var replaySampleSelect = panel.querySelector('[data-testid="eudiw-replay-sample-select"]');
  var replaySampleApply = panel.querySelector('[data-testid="eudiw-replay-sample-apply"]');

  registerTestHooks();
  initialize();
  applyStateFromSearch({ sync: true });

  function initialize() {
    wireTabs();
    wireProfileSelect();
    wireModeToggles();
    populateDcqlPresets();
    populateTrustedAuthoritiesFromPreset();
    populateInlineSamples();
    populateWalletPresets();
    populateStoredPresentations();
    autoApplyDefaults();
    maybeShowSeedActions();
    wireReplaySampleSelector();
    wireSimulateButton();
    wireValidateButton();
  }

  function readConsoleData() {
    var node = panel.querySelector('#eudiw-console-data');
    if (!node) {
      return {
        dcqlPresets: [],
        walletPresets: [],
        inlineSamples: { sdJwt: [], mdoc: [] },
        storedPresentations: [],
        trustedAuthorities: [],
      };
    }
    var text = node.textContent || '';
    node.parentNode.removeChild(node);
    if (!text.trim()) {
      return {
        dcqlPresets: [],
        walletPresets: [],
        inlineSamples: { sdJwt: [], mdoc: [] },
        storedPresentations: [],
        trustedAuthorities: [],
      };
    }
    try {
      return JSON.parse(text);
    } catch (error) {
      if (global.console && typeof global.console.error === 'function') {
        global.console.error('Failed to parse EUDIW console data', error);
      }
      return {
        dcqlPresets: [],
        walletPresets: [],
        inlineSamples: { sdJwt: [], mdoc: [] },
        storedPresentations: [],
        trustedAuthorities: [],
      };
    }
  }

  function buildTrustedAuthorityMap(entries) {
    var map = Object.create(null);
    if (!Array.isArray(entries)) {
      return map;
    }
    entries.forEach(function (entry) {
      if (!entry || !entry.policy) {
        return;
      }
      map[entry.policy] = entry;
    });
    return map;
  }

  function buildWalletPresetMap(entries) {
    var map = Object.create(null);
    if (!Array.isArray(entries)) {
      return map;
    }
    entries.forEach(function (entry) {
      if (!entry || !entry.id) {
        return;
      }
      map[entry.id] = entry;
    });
    return map;
  }

  function buildStoredPresentationMap(entries) {
    var map = Object.create(null);
    if (!Array.isArray(entries)) {
      return map;
    }
    entries.forEach(function (entry) {
      if (!entry || !entry.id) {
        return;
      }
      map[entry.id] = entry;
    });
    return map;
  }

  function wireTabs() {
    if (evaluateTabButton) {
      evaluateTabButton.addEventListener('click', function () {
        setActiveTab('evaluate');
      });
    }
    if (replayTabButton) {
      replayTabButton.addEventListener('click', function () {
        setActiveTab('replay');
      });
    }
  }

  function setActiveTab(tab, options) {
    options = options || {};
    var nextTab = tab === 'replay' ? 'replay' : 'evaluate';
    var previousTab = state.tab;
    state.tab = nextTab;
    if (state.tab === 'evaluate') {
      setTabState(evaluateTabButton, replayTabButton, evaluatePanel, replayPanel);
    } else {
      setTabState(replayTabButton, evaluateTabButton, replayPanel, evaluatePanel);
    }
    if (!options.silent && (previousTab !== nextTab || options.forceSync)) {
      syncDeepLink({ replace: options.replaceHistory === true });
    }
  }

  function setTabState(activeButton, inactiveButton, activePanel, inactivePanel) {
    if (activeButton) {
      activeButton.classList.add('mode-pill--active');
      activeButton.setAttribute('aria-selected', 'true');
    }
    if (inactiveButton) {
      inactiveButton.classList.remove('mode-pill--active');
      inactiveButton.setAttribute('aria-selected', 'false');
    }
    setVisibility(activePanel, true);
    setVisibility(inactivePanel, false);
    if (activePanel) {
      activePanel.removeAttribute('aria-hidden');
    }
    if (inactivePanel) {
      inactivePanel.setAttribute('aria-hidden', 'true');
    }
  }

  function wireProfileSelect() {
    if (!profileSelect) {
      return;
    }
    profileSelect.addEventListener('change', updateBaselineBanner);
    updateBaselineBanner();
  }

  function updateBaselineBanner() {
    if (!baselineBanner) {
      return;
    }
    var profile = (profileSelect && profileSelect.value) || 'HAIP';
    var showBanner = profile.toUpperCase() === 'BASELINE';
    setVisibility(baselineBanner, showBanner);
  }

  function wireModeToggles() {
    if (evaluateModeToggle) {
      evaluateModeToggle.addEventListener('change', function (event) {
        if (!event || !event.target) {
          return;
        }
        if (event.target.matches('[data-testid="eudiw-evaluate-mode-select-inline"]')) {
          setEvaluateMode('inline');
        } else if (event.target.matches('[data-testid="eudiw-evaluate-mode-select-stored"]')) {
          setEvaluateMode('stored');
        }
      });
    }
    if (replayModeToggle) {
      replayModeToggle.addEventListener('change', function (event) {
        if (!event || !event.target) {
          return;
        }
        if (event.target.matches('[data-testid="eudiw-replay-mode-select-inline"]')) {
          setReplayMode('inline');
        } else if (event.target.matches('[data-testid="eudiw-replay-mode-select-stored"]')) {
          setReplayMode('stored');
        }
      });
    }
    setEvaluateMode('inline', { silent: true });
    setReplayMode('inline', { silent: true });
  }

  function setEvaluateMode(mode, options) {
    options = options || {};
    state.evaluateMode = mode === 'stored' ? 'stored' : 'inline';
    var inlineSelected = state.evaluateMode === 'inline';
    setVisibility(evaluateInlineSection, inlineSelected);
    setVisibility(evaluateStoredSection, !inlineSelected);
    if (evaluateModeToggle) {
      evaluateModeToggle.setAttribute('data-mode', state.evaluateMode);
      var inlineInput = evaluateModeToggle.querySelector('[data-testid="eudiw-evaluate-mode-select-inline"]');
      var storedInput = evaluateModeToggle.querySelector('[data-testid="eudiw-evaluate-mode-select-stored"]');
      if (inlineInput) {
        inlineInput.checked = inlineSelected;
      }
      if (storedInput) {
        storedInput.checked = !inlineSelected;
      }
    }
    if (!options.silent && state.tab === 'evaluate') {
      syncDeepLink({ replace: options.replaceHistory === true });
    }
  }

  function setReplayMode(mode, options) {
    options = options || {};
    state.replayMode = mode === 'stored' ? 'stored' : 'inline';
    var inlineSelected = state.replayMode === 'inline';
    setVisibility(replayInlineSection, inlineSelected);
    setVisibility(replayStoredSection, !inlineSelected);
    if (replayModeToggle) {
      replayModeToggle.setAttribute('data-mode', state.replayMode);
      var inlineReplay = replayModeToggle.querySelector('[data-testid="eudiw-replay-mode-select-inline"]');
      var storedReplay = replayModeToggle.querySelector('[data-testid="eudiw-replay-mode-select-stored"]');
      if (inlineReplay) {
        inlineReplay.checked = inlineSelected;
      }
      if (storedReplay) {
        storedReplay.checked = !inlineSelected;
      }
    }
    if (!options.silent && state.tab === 'replay') {
      syncDeepLink({ replace: options.replaceHistory === true });
    }
  }

  function populateDcqlPresets() {
    if (!dcqlSelect) {
      return;
    }
    clearChildren(dcqlSelect);
    var presets = Array.isArray(dataset.dcqlPresets) ? dataset.dcqlPresets : [];
    if (!presets.length) {
      var placeholder = documentRef.createElement('option');
      placeholder.value = '';
      placeholder.textContent = 'No presets available';
      placeholder.disabled = true;
      placeholder.selected = true;
      dcqlSelect.appendChild(placeholder);
      return;
    }
    presets.forEach(function (preset, index) {
      if (!preset || !preset.id) {
        return;
      }
      var option = documentRef.createElement('option');
      option.value = preset.id;
      option.textContent = preset.label || preset.id;
      if (index === 0) {
        option.selected = true;
      }
      dcqlSelect.appendChild(option);
    });
    dcqlSelect.addEventListener('change', function () {
      updateDcqlPreview(dcqlSelect.value);
    });
    updateDcqlPreview(dcqlSelect.value || presets[0].id);
  }

  function updateDcqlPreview(presetId) {
    var preset = findById(dataset.dcqlPresets, presetId);
    if (!preset) {
      if (dcqlPreview) {
        dcqlPreview.value = '';
      }
      renderTrustedAuthoritiesFromPolicies([]);
      return;
    }
    if (dcqlPreview) {
      dcqlPreview.value = formatJson(preset.json || '');
    }
    renderTrustedAuthoritiesFromPolicies(preset.trustedAuthorityPolicies || []);
  }

  function populateTrustedAuthoritiesFromPreset() {
    if (!dcqlSelect) {
      renderTrustedAuthoritiesFromPolicies([]);
      return;
    }
    updateDcqlPreview(dcqlSelect.value);
  }

  function renderTrustedAuthoritiesFromPolicies(policies) {
    if (!trustedAuthoritiesList) {
      return;
    }
    clearChildren(trustedAuthoritiesList);
    if (!Array.isArray(policies) || !policies.length) {
      var emptyItem = documentRef.createElement('li');
      emptyItem.textContent = 'No trusted authority filters for this preset.';
      trustedAuthoritiesList.appendChild(emptyItem);
      return;
    }
    policies.forEach(function (policy) {
      var entry = trustedAuthorityMap[policy];
      var item = documentRef.createElement('li');
      if (entry && entry.label) {
        item.textContent = entry.label + ' (' + policy + ')';
      } else {
        item.textContent = policy;
      }
      trustedAuthoritiesList.appendChild(item);
    });
  }

  function populateInlineSamples() {
    if (!inlineSampleSelect) {
      return;
    }
    clearChildren(inlineSampleSelect);
    var samples = (dataset.inlineSamples && dataset.inlineSamples.sdJwt) || [];
    if (!samples.length) {
      var placeholder = documentRef.createElement('option');
      placeholder.value = '';
      placeholder.textContent = 'No inline samples available';
      placeholder.disabled = true;
      placeholder.selected = true;
      inlineSampleSelect.appendChild(placeholder);
      if (inlineSampleApply) {
        inlineSampleApply.disabled = true;
      }
      return;
    }
    samples.forEach(function (sample, index) {
      if (!sample || !sample.key) {
        return;
      }
      var option = documentRef.createElement('option');
      option.value = sample.key;
      option.textContent = sample.label || sample.key;
      if (index === 0) {
        option.selected = true;
      }
      inlineSampleSelect.appendChild(option);
    });
    if (inlineSampleApply) {
      inlineSampleApply.addEventListener('click', function () {
        var selected = inlineSampleSelect.value;
        var sample = findById(samples, selected);
        if (sample) {
          applyInlineSample(sample);
        }
      });
    }
  }

  function applyInlineSample(sample) {
    if (!sample) {
      return;
    }
    setValue(inlineCredentialId, sample.credentialId || '');
    setValue(inlineFormat, sample.format || '');
    setValue(
        inlinePolicies,
        Array.isArray(sample.trustedAuthorityPolicies)
            ? sample.trustedAuthorityPolicies.join(', ')
            : '');
    setValue(inlineSdJwt, sample.compactSdJwt || '');
    setValue(inlineDisclosures, formatJson(sample.disclosuresJson || ''));
    setValue(inlineKbJwt, sample.kbJwt || '');
    setValue(inlineDeviceResponse, sample.deviceResponseBase64 || '');
  }

  function populateWalletPresets() {
    if (!walletPresetSelect) {
      return;
    }
    clearChildren(walletPresetSelect);
    var presets = Array.isArray(dataset.walletPresets) ? dataset.walletPresets : [];
    if (!presets.length) {
      var placeholder = documentRef.createElement('option');
      placeholder.value = '';
      placeholder.textContent = 'No presets available';
      placeholder.disabled = true;
      placeholder.selected = true;
      walletPresetSelect.appendChild(placeholder);
      return;
    }
    presets.forEach(function (preset, index) {
      if (!preset || !preset.id) {
        return;
      }
      var option = documentRef.createElement('option');
      option.value = preset.id;
      option.textContent = preset.label || preset.id;
      if (index === 0) {
        option.selected = true;
      }
      walletPresetSelect.appendChild(option);
    });
  }

  function populateStoredPresentations() {
    var stored = Array.isArray(dataset.storedPresentations) ? dataset.storedPresentations : [];
    if (replayStoredSelect) {
      clearChildren(replayStoredSelect);
      if (!stored.length) {
        var placeholder = documentRef.createElement('option');
        placeholder.value = '';
        placeholder.textContent = 'No stored presentations available';
        placeholder.disabled = true;
        placeholder.selected = true;
        replayStoredSelect.appendChild(placeholder);
      } else {
        stored.forEach(function (entry, index) {
          if (!entry || !entry.id) {
            return;
          }
          var option = documentRef.createElement('option');
          option.value = entry.id;
          option.textContent = entry.label || entry.id;
          if (index === 0) {
            option.selected = true;
          }
          replayStoredSelect.appendChild(option);
        });
      }
    }
  }

  function autoApplyDefaults() {
    var sdJwtSamples = (dataset.inlineSamples && dataset.inlineSamples.sdJwt) || [];
    if (sdJwtSamples.length) {
      applyInlineSample(sdJwtSamples[0]);
    }
    var presets = Array.isArray(dataset.dcqlPresets) ? dataset.dcqlPresets : [];
    if (presets.length) {
      updateDcqlPreview(presets[0].id);
    }
  }

  function maybeShowSeedActions() {
    if (!seedActions) {
      return;
    }
    var stored = Array.isArray(dataset.storedPresentations) ? dataset.storedPresentations : [];
    setVisibility(seedActions, stored.length > 0);
  }

  function setValue(element, value) {
    if (!element) {
      return;
    }
    element.value = value || '';
  }

  function setVisibility(element, visible) {
    if (!element) {
      return;
    }
    if (visible) {
      element.removeAttribute('hidden');
      element.setAttribute('aria-hidden', 'false');
    } else {
      element.setAttribute('hidden', 'hidden');
      element.setAttribute('aria-hidden', 'true');
    }
  }

  function findById(list, id) {
    if (!Array.isArray(list)) {
      return null;
    }
    for (var i = 0; i < list.length; i += 1) {
      if (list[i] && list[i].id === id) {
        return list[i];
      }
      if (list[i] && list[i].key === id) {
        return list[i];
      }
    }
    return null;
  }

  function formatJson(text) {
    if (!text || typeof text !== 'string') {
      return text || '';
    }
    var trimmed = text.trim();
    if (!trimmed) {
      return '';
    }
    try {
      var parsed = JSON.parse(trimmed);
      return JSON.stringify(parsed, null, 2);
    } catch (error) {
      return text;
    }
  }

  function clearChildren(node) {
    if (!node) {
      return;
    }
    while (node.firstChild) {
      node.removeChild(node.firstChild);
    }
  }

  function wireReplaySampleSelector() {
    if (!replaySampleSelect || !replaySampleApply || !replayVpTokenInput) {
      return;
    }
    var samples = (dataset.inlineSamples && dataset.inlineSamples.sdJwt) || [];
    clearChildren(replaySampleSelect);
    if (!samples.length) {
      var option = documentRef.createElement('option');
      option.value = '';
      option.textContent = 'No samples available';
      option.disabled = true;
      option.selected = true;
      replaySampleSelect.appendChild(option);
      replaySampleApply.disabled = true;
      return;
    }
    samples.forEach(function (sample, index) {
      if (!sample || !sample.key) {
        return;
      }
      var option = documentRef.createElement('option');
      option.value = sample.key;
      option.textContent = sample.label || sample.key;
      if (index === 0) {
        option.selected = true;
      }
      replaySampleSelect.appendChild(option);
    });
    replaySampleApply.addEventListener('click', function () {
      var selected = replaySampleSelect.value;
      var sample = findById(samples, selected);
      if (!sample) {
        return;
      }
      var disclosures = [];
      if (sample.disclosuresJson) {
        try {
          var parsed = JSON.parse(sample.disclosuresJson);
          if (Array.isArray(parsed)) {
            disclosures = parsed;
          }
        } catch (error) {
          disclosures = [];
        }
      }
      var payload = {
        credentialId: sample.credentialId || sample.key,
        format: sample.format || 'dc+sd-jwt',
        vpToken: {
          vp_token: sample.compactSdJwt || '',
          presentation_submission: {},
        },
        keyBindingJwt: sample.kbJwt || null,
        disclosures: disclosures,
        trustedAuthorityPolicies: Array.isArray(sample.trustedAuthorityPolicies)
            ? sample.trustedAuthorityPolicies
            : [],
      };
      replayVpTokenInput.value = JSON.stringify(payload, null, 2);
    });
  }

  function wireSimulateButton() {
    if (!simulateButton) {
      return;
    }
    simulateButton.addEventListener('click', function () {
      runSimulation();
    });
  }

  function wireValidateButton() {
    if (!validateButton) {
      return;
    }
    validateButton.addEventListener('click', function () {
      runValidation();
    });
  }

  function runSimulation() {
    var requestEndpoint = panel.getAttribute('data-request-endpoint');
    var walletEndpoint = panel.getAttribute('data-wallet-endpoint');
    if (!requestEndpoint || !walletEndpoint) {
      return;
    }
    setButtonBusy(simulateButton, true);
    verboseBeginRequest();
    renderEvaluateStatus('Running simulation…', '', 'info');
    var authorizationPayload = buildAuthorizationPayload();
    var walletPayload;
    try {
      walletPayload = buildWalletPayload();
    } catch (error) {
      setButtonBusy(simulateButton, false);
      renderSimulateError({ message: error && error.message ? error.message : 'Invalid inline parameters.' });
      verboseApplyError({ payload: null });
      return;
    }
    sendJson(withVerboseQuery(requestEndpoint), authorizationPayload)
        .then(function (authResponse) {
          applyAuthorizationData(authResponse);
          walletPayload.requestId = authResponse.requestId;
          walletPayload.profile = authResponse.profile;
          walletPayload.responseMode = authResponse.responseMode;
          return sendJson(withVerboseQuery(walletEndpoint), walletPayload)
              .then(function (walletResponse) {
                renderSimulateResult(walletResponse, walletPayload);
                var tracePayload = buildEvaluateTracePayload(authResponse, walletResponse);
                verboseApplyResponse(tracePayload, 'info');
              });
        })
        .catch(function (error) {
          renderSimulateError(error);
          verboseApplyError(error);
        })
        .finally(function () {
          setButtonBusy(simulateButton, false);
        });
  }

  function runValidation() {
    var validateEndpoint = panel.getAttribute('data-validate-endpoint');
    if (!validateEndpoint) {
      return;
    }
    setButtonBusy(validateButton, true);
    verboseBeginRequest();
    renderReplayStatus('Validating presentation…', '', 'info');
    var payload;
    try {
      payload = buildValidationPayload();
    } catch (error) {
      setButtonBusy(validateButton, false);
      renderReplayError({ message: error && error.message ? error.message : 'Invalid replay input.' });
      verboseApplyError({ payload: null });
      return;
    }
    sendJson(withVerboseQuery(validateEndpoint), payload)
        .then(function (response) {
          renderReplayResult(response);
          var tracePayload = buildValidationTracePayload(response);
          verboseApplyResponse(tracePayload, 'info');
        })
        .catch(function (error) {
          renderReplayError(error);
          verboseApplyError(error);
        })
        .finally(function () {
          setButtonBusy(validateButton, false);
        });
  }

  function buildAuthorizationPayload() {
    var profile = (profileSelect && profileSelect.value) || 'HAIP';
    var responseMode = (responseModeSelect && responseModeSelect.value) || 'DIRECT_POST_JWT';
    return {
      profile: profile,
      responseMode: responseMode,
      dcqlPreset: dcqlSelect && dcqlSelect.value ? dcqlSelect.value : null,
      signedRequest: profile.toUpperCase() === 'HAIP',
      includeQrAscii: true,
    };
  }

  function buildWalletPayload() {
    var profile = (profileSelect && profileSelect.value) || 'HAIP';
    var responseMode = (responseModeSelect && responseModeSelect.value) || 'DIRECT_POST_JWT';
    var payload = {
      profile: profile,
      responseMode: responseMode,
      trustedAuthorityPolicy: null,
      walletPreset: null,
      inlineSdJwt: null,
    };
    if (state.evaluateMode === 'stored') {
      if (!walletPresetSelect || !walletPresetSelect.value) {
        throw new Error('Select a wallet preset for stored mode.');
      }
      payload.walletPreset = walletPresetSelect.value;
      var preset = walletPresetMap[payload.walletPreset];
      if (preset && Array.isArray(preset.trustedAuthorityPolicies) && preset.trustedAuthorityPolicies.length) {
        payload.trustedAuthorityPolicy = preset.trustedAuthorityPolicies[0];
      }
    } else {
      var inline = buildInlineWalletPayload();
      payload.inlineSdJwt = inline.payload;
      payload.trustedAuthorityPolicy = inline.trustedAuthorityPolicy;
    }
    return payload;
  }

  function buildInlineWalletPayload() {
    var credentialId = inlineCredentialId ? inlineCredentialId.value.trim() : '';
    if (!credentialId) {
      throw new Error('Credential ID is required for inline simulation.');
    }
    var format = inlineFormat ? inlineFormat.value.trim() : '';
    if (!format) {
      throw new Error('Format is required for inline simulation.');
    }
    var compactSdJwt = inlineSdJwt ? inlineSdJwt.value.trim() : '';
    if (!compactSdJwt) {
      throw new Error('SD-JWT compact value is required.');
    }
    var disclosures = [];
    if (inlineDisclosures && inlineDisclosures.value.trim()) {
      try {
        var parsed = JSON.parse(inlineDisclosures.value);
        disclosures = Array.isArray(parsed) ? parsed : [];
      } catch (error) {
        throw new Error('Disclosures must be valid JSON.');
      }
    }
    var policies = parsePolicyList(inlinePolicies ? inlinePolicies.value : '');
    return {
      payload: {
        credentialId: credentialId,
        format: format,
        compactSdJwt: compactSdJwt,
        disclosures: disclosures,
        keyBindingJwt: inlineKbJwt && inlineKbJwt.value ? inlineKbJwt.value.trim() : null,
        trustedAuthorityPolicies: policies,
      },
      trustedAuthorityPolicy: policies.length ? policies[0] : null,
    };
  }

  function buildValidationPayload() {
    var profile = (profileSelect && profileSelect.value) || 'HAIP';
    var responseModeOverride =
        replayResponseModeSelect && replayResponseModeSelect.value
            ? replayResponseModeSelect.value
            : null;
    var payload = {
      profile: profile,
      responseMode: responseModeOverride,
      presetId: null,
      inlineVpToken: null,
      trustedAuthorityPolicy:
          replayPolicyInput && replayPolicyInput.value ? replayPolicyInput.value.trim() : null,
      inlineDcqlJson: dcqlPreview ? dcqlPreview.value || null : null,
    };
    if (state.replayMode === 'stored') {
      if (!replayStoredSelect || !replayStoredSelect.value) {
        throw new Error('Select a stored presentation to validate.');
      }
      payload.presetId = replayStoredSelect.value;
      if (!payload.trustedAuthorityPolicy) {
        var stored = storedPresentationMap[payload.presetId];
        if (stored && Array.isArray(stored.trustedAuthorityPolicies) && stored.trustedAuthorityPolicies.length) {
          payload.trustedAuthorityPolicy = stored.trustedAuthorityPolicies[0];
        }
      }
    } else {
      var inlinePayload = parseReplayInlinePayload(replayVpTokenInput ? replayVpTokenInput.value : '');
      payload.inlineVpToken = inlinePayload;
      if (!payload.trustedAuthorityPolicy
              && Array.isArray(inlinePayload.trustedAuthorityPolicies)
              && inlinePayload.trustedAuthorityPolicies.length) {
        payload.trustedAuthorityPolicy = inlinePayload.trustedAuthorityPolicies[0];
      }
    }
    return payload;
  }

  function parseReplayInlinePayload(text) {
    if (!text || !text.trim()) {
      throw new Error('Inline VP Token JSON is required.');
    }
    var parsed;
    try {
      parsed = JSON.parse(text);
    } catch (error) {
      throw new Error('Inline VP Token JSON must be valid JSON.');
    }
    if (!parsed.credentialId || !parsed.format || !parsed.vpToken) {
      throw new Error('credentialId, format, and vpToken fields are required for inline validation.');
    }
    if (!parsed.disclosures || !Array.isArray(parsed.disclosures)) {
      parsed.disclosures = [];
    }
    if (!parsed.trustedAuthorityPolicies || !Array.isArray(parsed.trustedAuthorityPolicies)) {
      parsed.trustedAuthorityPolicies = [];
    }
    return parsed;
  }

  function sendJson(endpoint, payload) {
    if (!endpoint || typeof global.fetch !== 'function') {
      return Promise.reject({ status: 0, payload: null });
    }
    var body;
    try {
      body = JSON.stringify(payload || {});
    } catch (error) {
      return Promise.reject({ status: 0, payload: null });
    }
    var headers = { 'Content-Type': 'application/json' };
    var csrf = csrfToken();
    if (csrf) {
      headers['X-CSRF-TOKEN'] = csrf;
    }
    return global
        .fetch(endpoint, {
          method: 'POST',
          credentials: 'same-origin',
          headers: headers,
          body: body,
        })
        .then(function (response) {
          return response.text().then(function (text) {
            if (!text) {
              if (response.ok) {
                return {};
              }
              throw { status: response.status, payload: null };
            }
            try {
              var json = JSON.parse(text);
              if (response.ok) {
                return json;
              }
              throw { status: response.status, payload: json };
            } catch (error) {
              if (response.ok) {
                return {};
              }
              throw { status: response.status, payload: null };
            }
          });
        })
        .catch(function (error) {
          if (error && typeof error.status === 'number') {
            throw error;
          }
          throw { status: 0, payload: null };
        });
  }

  function csrfToken() {
    if (!csrfInputs || !csrfInputs.length) {
      return '';
    }
    for (var index = 0; index < csrfInputs.length; index += 1) {
      var input = csrfInputs[index];
      if (input && typeof input.value === 'string' && input.value.trim()) {
        return input.value.trim();
      }
    }
    return '';
  }

  function appendQuery(endpoint, query) {
    if (!endpoint) {
      return endpoint;
    }
    return endpoint + (endpoint.indexOf('?') === -1 ? '?' : '&') + query;
  }

  function verboseEnabled() {
    return Boolean(
        verboseConsole
            && typeof verboseConsole.isEnabled === 'function'
            && verboseConsole.isEnabled());
  }

  function withVerboseQuery(endpoint) {
    if (!verboseEnabled()) {
      return endpoint;
    }
    return appendQuery(endpoint, 'verbose=true');
  }

  function verboseBeginRequest() {
    if (!verboseConsole) {
      return;
    }
    if (typeof verboseConsole.beginRequest === 'function') {
      verboseConsole.beginRequest();
    } else if (typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    }
  }

  function verboseApplyResponse(tracePayload, variant) {
    if (!verboseConsole) {
      return;
    }
    var options = { variant: variant || 'info', protocol: 'eudiw' };
    if (tracePayload) {
      var envelope = { trace: tracePayload };
      if (typeof verboseConsole.handleResponse === 'function') {
        verboseConsole.handleResponse(envelope, options);
        return;
      }
      if (typeof verboseConsole.renderTrace === 'function') {
        verboseConsole.renderTrace(tracePayload, options);
        return;
      }
    }
    if (typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    }
  }

  function verboseApplyError(error) {
    if (!verboseConsole) {
      return;
    }
    var payload = error && error.payload ? error.payload : null;
    var options = { variant: 'error', protocol: 'eudiw' };
    if (payload && payload.trace && typeof verboseConsole.handleError === 'function') {
      verboseConsole.handleError(payload, options);
      return;
    }
    if (typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    }
  }

  function buildEvaluateTracePayload(authResponse, walletResponse) {
    var hasNewRequestTrace = hasVerboseTracePayload(authResponse);
    var hasNewWalletTrace = hasVerboseTracePayload(walletResponse);
    if (hasNewRequestTrace || hasNewWalletTrace) {
      return mergeVerboseTraces(
          hasNewRequestTrace ? authResponse.trace : null,
          hasNewWalletTrace ? walletResponse.trace : null,
          'eudiw.wallet.simulate');
    }

    var steps = [];
    if (authResponse && authResponse.trace) {
      var requestTrace = authResponse.trace;
      var trustedAuthorities = Array.isArray(requestTrace.trustedAuthorities)
          ? requestTrace.trustedAuthorities.join(', ')
          : '';
      steps.push(traceStep('authorization.request', 'Authorization request created', '', [
        ['request_id', requestTrace.requestId || authResponse.requestId || ''],
        ['profile', authResponse.profile || ''],
        ['dcql_hash', requestTrace.dcqlHash || ''],
        ['trusted_authorities', trustedAuthorities || ''],
      ]));
    }
    if (walletResponse && walletResponse.trace) {
      var presentations = Array.isArray(walletResponse.trace.presentations)
          ? walletResponse.trace.presentations
          : [];
      presentations.forEach(function (entry, index) {
        steps.push(traceStep(
            'wallet.presentation.' + (entry.id || index + 1),
            'Presentation ' + (entry.credentialId || entry.id || index + 1),
            '',
            [
              ['credential_id', entry.credentialId || ''],
              ['format', entry.format || ''],
              ['holder_binding', entry.holderBinding ? 'true' : 'false'],
              ['vp_token_hash', entry.vpTokenHash || ''],
              ['kb_jwt_hash', entry.kbJwtHash || ''],
              ['disclosure_hashes', Array.isArray(entry.disclosureHashes)
                  ? entry.disclosureHashes.join(', ')
                  : ''],
              ['trusted_authority', entry.trustedAuthorityMatch || walletResponse.trace.trustedAuthorityMatch || ''],
            ]));
      });
    }
    return {
      operation: 'eudiw.wallet.simulate',
      metadata: {
        requestId: (walletResponse && walletResponse.requestId) || (authResponse && authResponse.requestId) || '',
        profile: walletResponse ? walletResponse.profile : authResponse ? authResponse.profile : '',
        responseMode: walletResponse ? walletResponse.responseMode : authResponse ? authResponse.responseMode : '',
      },
      steps: steps,
    };
  }

  function buildValidationTracePayload(response) {
    if (!response) {
      return null;
    }
    if (hasVerboseTracePayload(response)) {
      return response.trace;
    }
    var steps = [];
    if (response.trace) {
      var validationPresentations = Array.isArray(response.trace.presentations)
          ? response.trace.presentations
          : [];
      validationPresentations.forEach(function (entry, index) {
        steps.push(traceStep(
            'validation.presentation.' + (entry.id || index + 1),
            'Validated presentation ' + (entry.credentialId || entry.id || index + 1),
            '',
            [
              ['credential_id', entry.credentialId || ''],
              ['format', entry.format || ''],
              ['holder_binding', entry.holderBinding ? 'true' : 'false'],
              ['vp_token_hash', entry.vpTokenHash || ''],
              ['kb_jwt_hash', entry.kbJwtHash || ''],
              ['disclosure_hashes', Array.isArray(entry.disclosureHashes)
                  ? entry.disclosureHashes.join(', ')
                  : ''],
              ['trusted_authority', entry.trustedAuthorityMatch || response.trace.trustedAuthorityMatch || ''],
            ]));
      });
    }
    return {
      operation: 'eudiw.wallet.validate',
      metadata: {
        requestId: response.requestId || '',
        profile: response.profile || '',
        responseMode: response.responseMode || '',
      },
      steps: steps,
    };
  }

  function traceStep(id, summary, detail, orderedAttributes) {
    var step = {
      id: id,
      summary: summary,
      detail: detail,
    };
    if (Array.isArray(orderedAttributes)) {
      step.orderedAttributes = orderedAttributes.map(function (entry) {
        return {
          name: entry[0],
          value: entry[1],
        };
      });
    }
    return step;
  }

  function hasVerboseTracePayload(response) {
    return Boolean(
        response
            && response.trace
            && Array.isArray(response.trace.steps)
            && response.trace.metadata);
  }

  function mergeVerboseTraces(requestTrace, walletTrace, fallbackOperation) {
    var metadata = {};
    if (requestTrace && requestTrace.metadata) {
      metadata = Object.assign(metadata, requestTrace.metadata);
    }
    if (walletTrace && walletTrace.metadata) {
      metadata = Object.assign(metadata, walletTrace.metadata);
    }
    var steps = [];
    if (requestTrace && Array.isArray(requestTrace.steps)) {
      steps = steps.concat(requestTrace.steps);
    }
    if (walletTrace && Array.isArray(walletTrace.steps)) {
      steps = steps.concat(walletTrace.steps);
    }
    return {
      operation: (walletTrace && walletTrace.operation)
          || (requestTrace && requestTrace.operation)
          || fallbackOperation,
      metadata: metadata,
      steps: steps,
    };
  }

  function renderSimulateResult(response, context) {
    if (!response) {
      return;
    }
    applyStatusBadge(resultStatusNode, response.status || 'UNKNOWN');
    resultStatusNode.setAttribute('data-status', response.status || '');
    updateResultMessage('', '', 'success');
    renderPresentationSections(
        resultPresentationContainer,
        Array.isArray(response.presentations) ? response.presentations : [],
        buildTraceLookup(response.trace),
        {
          kind: 'evaluate',
          responseMode: response.responseMode || (context && context.responseMode) || '—',
          sourceLabel: describePresentationSource(context),
          emptyMessage: 'Awaiting presentation results. Run a simulation to render outputs.',
        });
  }

  function renderSimulateError(error) {
    var detail = (error && error.payload && error.payload.detail) || error.message || 'Simulation failed.';
    applyStatusBadge(resultStatusNode, 'FAILED');
    resultStatusNode.setAttribute('data-status', 'FAILED');
    updateResultMessage('Simulation failed', detail, 'error');
    renderPresentationSections(
        resultPresentationContainer,
        [],
        null,
        {
          kind: 'evaluate',
          emptyMessage: 'No presentations to display. Fix the error and retry.',
        });
  }

  function renderReplayResult(response) {
    if (!response) {
      return;
    }
    applyStatusBadge(replayStatusNode, response.status || 'UNKNOWN');
    replayStatusNode.setAttribute('data-status', response.status || '');
    setNodeText(replayProblemDetailNode, '');
    renderPresentationSections(
        replayPresentationContainer,
        Array.isArray(response.presentations) ? response.presentations : [],
        buildTraceLookup(response.trace),
        {
          kind: 'replay',
          responseMode: response.responseMode || '—',
          sourceLabel: 'Validation payload',
          emptyMessage: 'No presentations to validate yet.',
        });
  }

  function renderReplayError(error) {
    var detail = (error && error.payload && error.payload.detail) || error.message || 'Validation failed.';
    applyStatusBadge(replayStatusNode, 'FAILED');
    replayStatusNode.setAttribute('data-status', 'FAILED');
    setNodeText(replayProblemDetailNode, detail);
    renderPresentationSections(
        replayPresentationContainer,
        [],
        null,
        {
          kind: 'replay',
          emptyMessage: 'Validation failed before presentations could be rendered.',
        });
  }

  function isEudiwProtocolParam(value) {
    if (!value) {
      return false;
    }
    var normalized = String(value).trim().toLowerCase();
    return normalized === 'eudiw' || normalized === 'eudi-openid4vp';
  }

  function canonicalizeProtocolQuery(value) {
    if (!value) {
      return 'eudi-openid4vp';
    }
    var normalized = String(value).trim().toLowerCase();
    return normalized === 'eudiw' ? 'eudiw' : 'eudi-openid4vp';
  }

  function setProtocolQueryValue(nextValue) {
    if (nextValue === undefined || nextValue === null || nextValue === '') {
      return;
    }
    protocolQueryValue = canonicalizeProtocolQuery(nextValue);
  }

  function currentProtocolQueryValue() {
    return canonicalizeProtocolQuery(protocolQueryValue);
  }

  function normalizeTabParam(value) {
    if (!value) {
      return 'evaluate';
    }
    var normalized = String(value).trim().toLowerCase();
    return normalized === 'replay' ? 'replay' : 'evaluate';
  }

  function normalizeModeParam(value) {
    if (!value) {
      return 'inline';
    }
    var normalized = String(value).trim().toLowerCase();
    return normalized === 'stored' ? 'stored' : 'inline';
  }

  function isAllowedTabValue(value) {
    if (!value) {
      return false;
    }
    var normalized = String(value).trim().toLowerCase();
    return normalized === 'evaluate' || normalized === 'replay';
  }

  function isAllowedModeValue(value) {
    if (!value) {
      return false;
    }
    var normalized = String(value).trim().toLowerCase();
    return normalized === 'inline' || normalized === 'stored';
  }

  function currentModeForTab(tab) {
    return tab === 'replay' ? (state.replayMode || 'inline') : (state.evaluateMode || 'inline');
  }

  function syncDeepLink(options) {
    var locationTarget = locationRef || global.location;
    if (!locationTarget) {
      return;
    }
    var tab = state.tab || 'evaluate';
    var mode = currentModeForTab(tab);
    var params = new global.URLSearchParams();
    var protocolValue = currentProtocolQueryValue();
    params.set('protocol', protocolValue);
    params.set('tab', tab);
    params.set('mode', mode);
    var search = '?' + params.toString();
    var pathname = locationTarget.pathname || '';
    var url = pathname + search;
    var historyTarget = historyRef || global.history;
    var method = options && options.replace ? 'replaceState' : 'pushState';
    var historyUpdated = false;
    if (historyTarget && typeof historyTarget[method] === 'function') {
      historyTarget[method](
          { protocol: protocolValue, tab: tab, mode: mode },
          '',
          url);
      historyUpdated = true;
    }
    if (!historyUpdated && locationTarget && locationTarget.search !== search) {
      locationTarget.search = search;
    }
  }

  function applyStateFromSearch(options) {
    var opts = options || {};
    var searchValue;
    if (typeof opts.search === 'string') {
      searchValue = opts.search;
    } else if (!hydratedInitialState && initialSearch) {
      searchValue = initialSearch;
    } else {
      var sourceLocation = locationRef || global.location;
      searchValue = sourceLocation && typeof sourceLocation.search === 'string' ? sourceLocation.search : '';
    }
    if (!searchValue) {
      var historyState = historyRef && historyRef.state;
      if (historyState && isEudiwState(historyState)) {
        searchValue = stateToSearch(historyState);
      }
    }
    if (!searchValue) {
      return;
    }
    var params = new global.URLSearchParams(searchValue);
    var protocolParam = params.get('protocol');
    if (protocolParam && !isEudiwProtocolParam(protocolParam)) {
      return;
    }
    if (protocolParam) {
      setProtocolQueryValue(protocolParam);
    }
    var desiredTab = normalizeTabParam(params.get('tab'));
    var desiredMode = normalizeModeParam(params.get('mode'));
    if (desiredTab === 'replay') {
      setActiveTab('replay', { silent: true });
      setReplayMode(desiredMode, { silent: true });
    } else {
      setActiveTab('evaluate', { silent: true });
      setEvaluateMode(desiredMode, { silent: true });
    }
    hydratedInitialState = true;
    initialSearch = '';
    if (opts.sync === true) {
      syncDeepLink({ replace: true });
    }
  }

  function renderEvaluateStatus(message, hint, variant) {
    updateResultMessage(message, hint, variant);
  }

  function renderReplayStatus(message, detail, variant) {
    applyStatusBadge(replayStatusNode, message || '');
    replayStatusNode.setAttribute('data-status', variant || '');
    setNodeText(replayProblemDetailNode, detail || '');
  }

  if (documentRef && typeof documentRef.addEventListener === 'function') {
    documentRef.addEventListener('operator:protocol-activated', function (event) {
      var detail = event && event.detail ? event.detail : null;
      if (!detail || detail.protocol !== 'eudi-openid4vp') {
        return;
      }
      applyStateFromSearch({ sync: false });
      syncDeepLink({ replace: true });
    });
  }

  if (typeof global.addEventListener === 'function') {
    global.addEventListener('popstate', function (event) {
      var state = event && event.state ? event.state : null;
      var stateProtocol = state && state.protocol ? state.protocol : null;
      if (stateProtocol && stateProtocol !== 'eudi-openid4vp' && stateProtocol !== 'eudiw') {
        return;
      }
      if (stateProtocol) {
        setProtocolQueryValue(stateProtocol);
      }
      var sourceLocation = locationRef || global.location;
      if (!stateProtocol && sourceLocation && sourceLocation.search) {
        var params = new global.URLSearchParams(sourceLocation.search);
        var protocolParam = params.get('protocol');
        if (!isEudiwProtocolParam(protocolParam)) {
          return;
        }
        setProtocolQueryValue(protocolParam);
      } else if (!stateProtocol && !sourceLocation) {
        return;
      }
      applyStateFromSearch({ search: sourceLocation && sourceLocation.search, sync: false });
    });
  }

  function applyAuthorizationData(response) {
    if (!response) {
      return;
    }
    if (qrAsciiNode && response.qr && response.qr.ascii) {
      qrAsciiNode.textContent = response.qr.ascii;
    }
    if (qrAltUrlInput) {
      qrAltUrlInput.value = (response.qr && response.qr.uri) || response.requestUri || '';
    }
  }

  function describePresentationSource(context) {
    if (state.evaluateMode === 'stored') {
      var preset = context && context.walletPreset ? walletPresetMap[context.walletPreset] : null;
      return preset ? 'Stored preset ' + preset.label : 'Stored preset';
    }
    return 'Inline parameters';
  }

  function formatTrustedAuthority(policy) {
    if (!policy) {
      return '—';
    }
    var entry = trustedAuthorityMap[policy];
    if (entry && entry.label) {
      return entry.label + ' (' + policy + ')';
    }
    return policy;
  }

  function updateResultMessage(message, hint, variant) {
    if (resultMessageNode) {
      resultMessageNode.classList.remove('result-message--error', 'result-message--info');
      if (message) {
        resultMessageNode.textContent = message;
        resultMessageNode.removeAttribute('hidden');
        resultMessageNode.setAttribute('aria-hidden', 'false');
        resultMessageNode.setAttribute('data-status', variant || 'info');
        if (variant === 'error') {
          resultMessageNode.classList.add('result-message--error');
        } else {
          resultMessageNode.classList.add('result-message--info');
        }
      } else {
        resultMessageNode.textContent = '';
        resultMessageNode.setAttribute('hidden', 'hidden');
        resultMessageNode.setAttribute('aria-hidden', 'true');
        resultMessageNode.removeAttribute('data-status');
      }
    }
    if (resultHintNode) {
      if (hint) {
        resultHintNode.textContent = hint;
        resultHintNode.removeAttribute('hidden');
        resultHintNode.setAttribute('aria-hidden', 'false');
      } else {
        resultHintNode.textContent = '';
        resultHintNode.setAttribute('hidden', 'hidden');
        resultHintNode.setAttribute('aria-hidden', 'true');
      }
    }
  }

  function resolveStatusVariant(status) {
    var lowered = !status && status !== 0 ? '' : String(status).trim().toLowerCase();
    if (!lowered) {
      return 'info';
    }
    if (['generated', 'success', 'ok', 'valid', 'completed', 'match'].indexOf(lowered) >= 0) {
      return 'success';
    }
    if (['failed', 'failure', 'error', 'invalid', 'denied', 'rejected', 'mismatch'].indexOf(lowered) >= 0) {
      return 'error';
    }
    return 'info';
  }

  function applyStatusBadge(statusNode, status) {
    if (!statusNode) {
      return;
    }
    var raw = status === null || status === undefined ? '' : String(status).trim();
    var variant = resolveStatusVariant(raw);
    statusNode.className = 'status-badge';
    if (variant === 'success') {
      statusNode.classList.add('status-badge--success');
    } else if (variant === 'error') {
      statusNode.classList.add('status-badge--error');
    } else {
      statusNode.classList.add('status-badge--info');
    }
    statusNode.textContent = raw ? raw : '—';
  }

  function setNodeText(node, value) {
    if (!node) {
      return;
    }
    if (value === null || value === undefined || value === '') {
      node.textContent = '—';
      return;
    }
    node.textContent = value;
  }

  function setButtonBusy(button, busy) {
    if (!button) {
      return;
    }
    if (busy) {
      button.setAttribute('data-busy', 'true');
      button.setAttribute('disabled', 'disabled');
    } else {
      button.removeAttribute('data-busy');
      button.removeAttribute('disabled');
    }
  }

  function buildTraceLookup(trace) {
    if (!trace) {
      return null;
    }
    if (Array.isArray(trace.presentations)) {
      var byIdLegacy = Object.create(null);
      var byCredentialLegacy = Object.create(null);
      trace.presentations.forEach(function (entry) {
        if (!entry) {
          return;
        }
        var key = entry.id || entry.presentationId || entry.credentialId;
        if (key) {
          byIdLegacy[key] = entry;
        }
        if (entry.credentialId) {
          byCredentialLegacy[entry.credentialId] = entry;
        }
      });
      return { byId: byIdLegacy, byCredential: byCredentialLegacy };
    }
    if (Array.isArray(trace.steps)) {
      return buildTraceLookupFromSteps(trace.steps);
    }
    return null;
  }

  function buildTraceLookupFromSteps(steps) {
    var byId = Object.create(null);
    var byCredential = Object.create(null);
    steps.forEach(function (step) {
      if (!step || typeof step.id !== 'string') {
        return;
      }
      if (step.id.indexOf('presentation') === -1) {
        return;
      }
      var entry = {
        id: step.id,
        credentialId: extractTraceAttribute(step, 'credential_id'),
        format: extractTraceAttribute(step, 'format'),
        holderBinding: extractTraceAttribute(step, 'holder_binding') === 'true',
        vpTokenHash: extractTraceAttribute(step, 'vp_token_hash'),
        kbJwtHash: extractTraceAttribute(step, 'kb_jwt_hash'),
        disclosureHashes: extractTraceAttribute(step, 'disclosure_hashes'),
        deviceResponseHash: extractTraceAttribute(step, 'device_response_hash'),
        trustedAuthorityMatch: extractTraceAttribute(step, 'trusted_authority'),
      };
      if (entry.id) {
        byId[entry.id] = entry;
      }
      if (entry.credentialId) {
        byCredential[entry.credentialId] = entry;
      }
    });
    return { byId: byId, byCredential: byCredential };
  }

  function extractTraceAttribute(step, attributeName) {
    if (!step) {
      return '';
    }
    if (Array.isArray(step.orderedAttributes)) {
      for (var index = 0; index < step.orderedAttributes.length; index += 1) {
        var attribute = step.orderedAttributes[index];
        if (attribute && attribute.name === attributeName) {
          return attribute.value || '';
        }
      }
    }
    if (step.attributes && typeof step.attributes === 'object') {
      var typeKeys = Object.keys(step.attributes);
      for (var t = 0; t < typeKeys.length; t += 1) {
        var bucket = step.attributes[typeKeys[t]];
        if (bucket && Object.prototype.hasOwnProperty.call(bucket, attributeName)) {
          return bucket[attributeName];
        }
      }
    }
    if (step.notes && Object.prototype.hasOwnProperty.call(step.notes, attributeName)) {
      return step.notes[attributeName];
    }
    return '';
  }

  function isEudiwState(state) {
    if (!state || !state.protocol) {
      return false;
    }
    var protocol = String(state.protocol).trim().toLowerCase();
    return protocol === 'eudi-openid4vp' || protocol === 'eudiw';
  }

  function stateToSearch(nextState) {
    if (!nextState || typeof nextState !== 'object') {
      return '';
    }
    var params = new global.URLSearchParams();
    var protocolValue = nextState.protocol
        ? canonicalizeProtocolQuery(nextState.protocol)
        : currentProtocolQueryValue();
    params.set('protocol', protocolValue);
    var normalizedTab = isAllowedTabValue(nextState.tab) ? normalizeTabParam(nextState.tab) : null;
    if (normalizedTab) {
      params.set('tab', normalizedTab);
    }
    var normalizedMode = isAllowedModeValue(nextState.mode) ? normalizeModeParam(nextState.mode) : null;
    if (normalizedMode) {
      params.set('mode', normalizedMode);
    }
    var rendered = params.toString();
    return rendered ? '?' + rendered : '';
  }

  function renderPresentationSections(container, presentations, traceLookup, options) {
    if (!container) {
      return;
    }
    clearChildren(container);
    if (!Array.isArray(presentations) || !presentations.length) {
      var placeholder = documentRef.createElement('p');
      placeholder.className = 'presentation-placeholder';
      placeholder.textContent = (options && options.emptyMessage)
          ? options.emptyMessage
          : 'No presentations available.';
      container.appendChild(placeholder);
      return;
    }
    for (var index = 0; index < presentations.length; index += 1) {
      var presentation = presentations[index];
      var traceEntry = resolveTraceEntry(presentation, traceLookup);
      var traceId = deriveTraceId(presentation, traceEntry, index);
      var section = documentRef.createElement('details');
      section.className = 'presentation-card';
      section.setAttribute(
          'data-testid',
          options && options.kind === 'replay' ? 'eudiw-replay-presentation' : 'eudiw-result-presentation');
      section.setAttribute('data-trace-id', traceId);
      if (index === 0) {
        section.setAttribute('open', 'open');
      }
      section.appendChild(renderPresentationSummary(presentation, traceEntry, options));
      section.appendChild(renderPresentationBody(presentation, traceEntry, options, traceId));
      container.appendChild(section);
    }
  }

  function resolveTraceEntry(presentation, traceLookup) {
    if (!traceLookup) {
      return null;
    }
    if (presentation && presentation.credentialId && traceLookup.byCredential[presentation.credentialId]) {
      return traceLookup.byCredential[presentation.credentialId];
    }
    var ids = Object.keys(traceLookup.byId);
    if (ids.length) {
      return traceLookup.byId[ids[0]];
    }
    return null;
  }

  function deriveTraceId(presentation, traceEntry, index) {
    if (traceEntry && (traceEntry.id || traceEntry.presentationId)) {
      return traceEntry.id || traceEntry.presentationId;
    }
    if (traceEntry && traceEntry.credentialId) {
      return traceEntry.credentialId;
    }
    if (presentation && presentation.credentialId) {
      return presentation.credentialId;
    }
    return 'presentation-' + index;
  }

  function renderPresentationSummary(presentation, traceEntry, options) {
    var summary = documentRef.createElement('summary');
    summary.className = 'presentation-card__summary';

    var title = documentRef.createElement('span');
    title.className = 'presentation-card__title';
    var formatLabel = presentation && presentation.format ? presentation.format : 'Unknown format';
    var credentialLabel = presentation && presentation.credentialId ? presentation.credentialId : 'Credential';
    title.textContent = formatLabel + ' • ' + credentialLabel;
    summary.appendChild(title);

    var meta = documentRef.createElement('span');
    meta.className = 'presentation-card__meta';
    var metaParts = [];
    if (options && options.sourceLabel) {
      metaParts.push(options.sourceLabel);
    }
    if (options && options.responseMode) {
      metaParts.push(options.responseMode);
    }
    meta.textContent = metaParts.join(' · ');
    summary.appendChild(meta);

    return summary;
  }

  function renderPresentationBody(presentation, traceEntry, options, traceId) {
    var body = documentRef.createElement('div');
    body.className = 'presentation-card__body';

    var details = documentRef.createElement('dl');
    details.className = 'presentation-card__details';
    appendDetailRow(details, 'Format', presentation && presentation.format ? presentation.format : '—');
    appendDetailRow(details, 'Credential ID', presentation && presentation.credentialId ? presentation.credentialId : '—');
    appendDetailRow(details, 'Holder binding', presentation && presentation.holderBinding ? 'Yes' : 'No');
    appendDetailRow(
        details,
        'Trusted authority',
        formatTrustedAuthority(presentation && presentation.trustedAuthorityMatch));
    if (options && options.responseMode) {
      appendDetailRow(details, 'Response mode', options.responseMode);
    }
    if (traceEntry && traceEntry.vpTokenHash) {
      appendDetailRow(details, 'VP Token hash', traceEntry.vpTokenHash);
    }
    if (traceEntry && Array.isArray(traceEntry.disclosureHashes) && traceEntry.disclosureHashes.length) {
      appendDetailRow(details, 'Disclosures', traceEntry.disclosureHashes.length + ' hash(es)');
    }
    body.appendChild(details);

    var jsonText = formatVpTokenPayload(presentation && presentation.vpToken);

    var codeBlock = documentRef.createElement('textarea');
    codeBlock.className = 'code-textarea presentation-card__token';
    codeBlock.setAttribute('readonly', 'readonly');
    codeBlock.setAttribute('spellcheck', 'false');
    codeBlock.value = jsonText;
    codeBlock.setAttribute('rows', '8');
    codeBlock.setAttribute(
        'data-testid',
        options && options.kind === 'replay' ? 'eudiw-replay-vp-token-result' : 'eudiw-result-vp-token');
    body.appendChild(codeBlock);

    return body;
  }

  function appendDetailRow(container, label, value) {
    var term = documentRef.createElement('dt');
    term.textContent = label;
    container.appendChild(term);
    var detail = documentRef.createElement('dd');
    detail.textContent = value === undefined || value === null || value === '' ? '—' : value;
    container.appendChild(detail);
  }

  function formatVpTokenPayload(vpToken) {
    if (!vpToken) {
      return '';
    }
    if (typeof vpToken === 'string') {
      return vpToken;
    }
    try {
      return JSON.stringify(vpToken, null, 2);
    } catch (error) {
      return String(vpToken);
    }
  }

  function registerTestHooks() {
    var hooks = global.EudiwConsoleTestHooks || {};
    hooks.renderPresentationsForTest = function (options) {
      if (!options || !options.container) {
        return;
      }
      renderPresentationSections(
          options.container,
          Array.isArray(options.presentations) ? options.presentations : [],
          buildTraceLookup({ presentations: options.tracePresentations || [] }),
          {
            kind: options.kind || 'evaluate',
            responseMode: options.responseMode || 'DIRECT_POST_JWT',
            sourceLabel: options.sourceLabel || '',
            emptyMessage: options.emptyMessage || 'No presentations configured.',
          });
    };
    hooks.applyUrlStateForTest = function (search) {
      applyStateFromSearch({ search: search, sync: false });
    };
    hooks.syncUrlForTest = function (options) {
      syncDeepLink(options || {});
    };
    hooks.readStateForTest = function () {
      return {
        tab: state.tab,
        evaluateMode: state.evaluateMode,
        replayMode: state.replayMode,
      };
    };
    global.EudiwConsoleTestHooks = hooks;
  }

  function parsePolicyList(value) {
    if (!value || typeof value !== 'string') {
      return [];
    }
    return value
        .split(',')
        .map(function (entry) {
          return entry.trim();
        })
        .filter(function (entry) {
          return entry.length > 0;
        });
  }
})(window);
