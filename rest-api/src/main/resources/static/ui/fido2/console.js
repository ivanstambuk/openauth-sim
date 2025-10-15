(function (global) {
  'use strict';

  var documentRef = global.document;
  var panel = documentRef.querySelector('[data-protocol-panel="fido2"]');
  if (!panel) {
    global.Fido2Console = {
      setMode: function () {},
      getMode: function () {
        return 'stored';
      },
    };
    return;
  }

  var TAB_EVALUATE = 'evaluate';
  var TAB_REPLAY = 'replay';
  var MODE_STORED = 'stored';
  var MODE_INLINE = 'inline';

  var CLIENT_DATA_TYPE = 'webauthn.get';
  var tabContainer = panel.querySelector('[data-testid="fido2-panel-tabs"]');
  var evaluateTabButton = panel.querySelector('[data-testid="fido2-panel-tab-evaluate"]');
  var replayTabButton = panel.querySelector('[data-testid="fido2-panel-tab-replay"]');
  var evaluatePanel = panel.querySelector('[data-fido2-panel="evaluate"]');
  var replayPanel = panel.querySelector('[data-fido2-panel="replay"]');

  var evaluateModeToggle =
      panel.querySelector('[data-testid="fido2-evaluate-mode-toggle"]');
  var evaluateStoredSection =
      panel.querySelector('[data-testid="fido2-evaluate-stored-section"]');
  var evaluateInlineSection =
      panel.querySelector('[data-testid="fido2-evaluate-inline-section"]');
  var evaluateStoredButton =
      panel.querySelector('[data-testid="fido2-evaluate-stored-submit"]');
  var evaluateInlineButton =
      panel.querySelector('[data-testid="fido2-evaluate-inline-submit"]');
  var storedResultPanel = panel.querySelector('[data-testid="fido2-stored-result"]');
  var storedAssertionJson =
      storedResultPanel
          ? storedResultPanel.querySelector('[data-testid="fido2-generated-assertion-json"]')
          : null;
  var inlineResultPanel = panel.querySelector('[data-testid="fido2-inline-result"]');
  var inlineGeneratedJson =
      inlineResultPanel
          ? inlineResultPanel.querySelector('[data-testid="fido2-inline-generated-json"]')
          : null;
  var inlineErrorBanner =
      inlineResultPanel
          ? inlineResultPanel.querySelector('[data-testid="fido2-inline-error"]')
          : null;
  var inlineTelemetry =
      inlineResultPanel
          ? inlineResultPanel.querySelector('[data-testid="fido2-inline-telemetry"]')
          : null;

  var replayModeToggle =
      panel.querySelector('[data-testid="fido2-replay-mode-toggle"]');
  var replayStoredSection =
      panel.querySelector('[data-testid="fido2-replay-stored-section"]');
  var replayInlineSection =
      panel.querySelector('[data-testid="fido2-replay-inline-section"]');
  var replayStoredButton =
      panel.querySelector('[data-testid="fido2-replay-stored-submit"]');
  var replayInlineButton =
      panel.querySelector('[data-testid="fido2-replay-inline-submit"]');
  var replayStoredResultPanel =
      panel.querySelector('[data-testid="fido2-replay-result"]');
  var replayStatusBadge = panel.querySelector('[data-testid="fido2-replay-status"]');
  var replayReason = panel.querySelector('[data-testid="fido2-replay-reason"]');
  var replayOutcome = panel.querySelector('[data-testid="fido2-replay-outcome"]');
  var replayInlineResultPanel =
      panel.querySelector('[data-testid="fido2-replay-inline-result"]');
  var replayInlineStatusBadge =
      replayInlineResultPanel
          ? replayInlineResultPanel.querySelector('[data-testid="fido2-replay-inline-status"]')
          : null;
  var replayInlineReason =
      replayInlineResultPanel
          ? replayInlineResultPanel.querySelector('[data-testid="fido2-replay-inline-reason"]')
          : null;
  var replayInlineOutcome =
      replayInlineResultPanel
          ? replayInlineResultPanel.querySelector('[data-testid="fido2-replay-inline-outcome"]')
          : null;

  var storedForm = panel.querySelector('[data-testid="fido2-stored-form"]');
  var storedCredentialSelect = panel.querySelector('#fido2StoredCredentialId');
  var storedSeedActions = panel.querySelector('[data-testid="fido2-seed-actions"]');
  var storedSeedButton = panel.querySelector('[data-testid="fido2-seed-credentials"]');
  var storedSeedStatus = panel.querySelector('[data-testid="fido2-seed-status"]');
  var storedRpInput = panel.querySelector('#fido2StoredRpId');
  var storedOriginInput = panel.querySelector('#fido2StoredOrigin');
  var storedChallengeField = panel.querySelector('#fido2StoredChallenge');
  var storedCounterInput = panel.querySelector('#fido2StoredCounter');
  var storedCounterToggle =
      panel.querySelector('[data-testid="fido2-stored-counter-toggle"]');
  var storedCounterHint =
      panel.querySelector('[data-testid="fido2-stored-counter-hint"]');
  var storedCounterResetButton =
      panel.querySelector('[data-testid="fido2-stored-counter-reset"]');
  var storedPrivateKeyField = panel.querySelector('#fido2StoredPrivateKey');
  var storedUvSelect = panel.querySelector('#fido2StoredUvRequired');

  var inlineForm = panel.querySelector('[data-testid="fido2-inline-form"]');
  var inlineRpInput = panel.querySelector('#fido2InlineRpId');
  var inlineOriginInput = panel.querySelector('#fido2InlineOrigin');
  var inlineCredentialIdField = panel.querySelector('#fido2InlineCredentialId');
  var inlineAlgorithmInput = panel.querySelector('#fido2InlineAlgorithm');
  var inlineCounterInput = panel.querySelector('#fido2InlineCounter');
  var inlineCounterToggle =
      panel.querySelector('[data-testid="fido2-inline-counter-toggle"]');
  var inlineCounterHint =
      panel.querySelector('[data-testid="fido2-inline-counter-hint"]');
  var inlineCounterResetButton =
      panel.querySelector('[data-testid="fido2-inline-counter-reset"]');
  var inlineUvRequiredSelect = panel.querySelector('#fido2InlineUvRequired');
  var inlineChallengeField = panel.querySelector('#fido2InlineChallenge');
  var inlinePrivateKeyField = panel.querySelector('#fido2InlinePrivateKey');
  var inlineSampleSelect = panel.querySelector('#fido2InlineSampleSelect');

  var replayForm = panel.querySelector('[data-testid="fido2-replay-form"]');
  var replayCredentialSelect = panel.querySelector('#fido2ReplayCredentialId');
  var replayOriginInput = panel.querySelector('#fido2ReplayOrigin');
  var replayRpInput = panel.querySelector('#fido2ReplayRpId');
  var replayChallengeField = panel.querySelector('#fido2ReplayChallenge');
  var replayClientDataField = panel.querySelector('#fido2ReplayClientData');
  var replayAuthenticatorDataField =
      panel.querySelector('#fido2ReplayAuthenticatorData');
  var replaySignatureField = panel.querySelector('#fido2ReplaySignature');

  var replayInlineForm = panel.querySelector('[data-testid="fido2-replay-inline-form"]');
  var replayInlineCredentialIdField =
      panel.querySelector('#fido2ReplayInlineCredentialId');
  var replayInlinePublicKeyField =
      panel.querySelector('#fido2ReplayInlinePublicKey');
  var replayInlineRpInput = panel.querySelector('#fido2ReplayInlineRpId');
  var replayInlineOriginInput = panel.querySelector('#fido2ReplayInlineOrigin');
  var replayInlineAlgorithmInput =
      panel.querySelector('#fido2ReplayInlineAlgorithm');
  var replayInlineCounterInput =
      panel.querySelector('#fido2ReplayInlineCounter');
  var replayInlineUvRequiredSelect =
      panel.querySelector('#fido2ReplayInlineUvRequired');
  var replayInlineChallengeField =
      panel.querySelector('#fido2ReplayInlineChallenge');
  var replayInlineClientDataField =
      panel.querySelector('#fido2ReplayInlineClientData');
  var replayInlineAuthenticatorDataField =
      panel.querySelector('#fido2ReplayInlineAuthenticatorData');
  var replayInlineSignatureField =
      panel.querySelector('#fido2ReplayInlineSignature');
  var replayInlineSampleSelect =
      panel.querySelector('#fido2ReplayInlineSampleSelect');

  var seedDefinitionsNode = panel.querySelector('#fido2-seed-definitions');
  var inlineVectorsNode = panel.querySelector('#fido2-inline-vectors');

  var hasStoredEvaluationResult = false;
  var hasInlineEvaluationResult = false;
  var hasStoredReplayResult = false;
  var hasInlineReplayResult = false;

  var seedDefinitions = parseJson(seedDefinitionsNode);
  var inlineVectors = parseJson(inlineVectorsNode);
  var inlineVectorIndex = createInlineVectorIndex(inlineVectors);
  var activeInlineCredentialName = null;
  var activeReplayInlineCredentialName = null;
  var inlineCounterSnapshotSeconds = null;
  var storedCounterSnapshotSeconds = null;
  var storedCounterBaseline = null;
  var activeStoredCredentialId = '';
  var suppressStoredCredentialSync = false;

  removeNode(seedDefinitionsNode);
  removeNode(inlineVectorsNode);

  populateInlineSampleOptions();
  refreshStoredCredentials();
  initializeInlineCounter();
  initializeStoredCounter();

  var currentTab = TAB_EVALUATE;
  var currentEvaluateMode = MODE_INLINE;
  var currentReplayMode = MODE_INLINE;
  var lastReplayMode = MODE_INLINE;
  if (typeof global.__openauthFido2InitialReplayMode === 'string') {
    var initialReplayMode =
        global.__openauthFido2InitialReplayMode.toLowerCase();
    if (initialReplayMode === MODE_STORED || initialReplayMode === MODE_INLINE) {
      lastReplayMode = initialReplayMode;
      currentReplayMode = initialReplayMode;
    }
    global.__openauthFido2InitialReplayMode = undefined;
  }
  var initialLegacyMode = readInitialLegacyMode();

  if (evaluateTabButton) {
    evaluateTabButton.addEventListener('click', function (event) {
      event.preventDefault();
      setTab(TAB_EVALUATE, { broadcast: true });
    });
  }
  if (replayTabButton) {
    replayTabButton.addEventListener('click', function (event) {
      event.preventDefault();
      setTab(TAB_REPLAY, { broadcast: true });
    });
  }

  var evaluateStoredRadio =
      panel.querySelector('[data-testid="fido2-evaluate-mode-select-stored"]');
  var evaluateInlineRadio =
      panel.querySelector('[data-testid="fido2-evaluate-mode-select-inline"]');
  if (evaluateStoredRadio) {
    evaluateStoredRadio.addEventListener('change', function () {
      if (evaluateStoredRadio.checked) {
        setEvaluateMode(MODE_STORED, { broadcast: true });
      }
    });
  }
  if (evaluateInlineRadio) {
    evaluateInlineRadio.addEventListener('change', function () {
      if (evaluateInlineRadio.checked) {
        setEvaluateMode(MODE_INLINE, { broadcast: true });
      }
    });
  }

  var replayStoredRadio =
      panel.querySelector('[data-testid="fido2-replay-mode-select-stored"]');
  var replayInlineRadio =
      panel.querySelector('[data-testid="fido2-replay-mode-select-inline"]');
  if (replayStoredRadio) {
    replayStoredRadio.addEventListener('change', function () {
      if (replayStoredRadio.checked) {
        setReplayMode(MODE_STORED, { broadcast: true });
      }
    });
  }
  if (replayInlineRadio) {
    replayInlineRadio.addEventListener('change', function () {
      if (replayInlineRadio.checked) {
        setReplayMode(MODE_INLINE, { broadcast: true });
      }
    });
  }

  if (storedCredentialSelect) {
    storedCredentialSelect.addEventListener('change', function () {
      if (suppressStoredCredentialSync) {
        return;
      }
      setActiveStoredCredential(storedCredentialSelect.value);
    });
  }
  if (replayCredentialSelect) {
    replayCredentialSelect.addEventListener('change', function () {
      if (suppressStoredCredentialSync) {
        return;
      }
      setActiveStoredCredential(replayCredentialSelect.value);
    });
  }

  if (storedSeedButton) {
    storedSeedButton.addEventListener('click', function () {
      seedStoredCredentials();
    });
  }

  if (inlineSampleSelect) {
    inlineSampleSelect.addEventListener('change', function () {
      applyInlineSample(inlineSampleSelect.value);
    });
  }
  if (replayInlineSampleSelect) {
    replayInlineSampleSelect.addEventListener('change', function () {
      applyReplayInlineSample(replayInlineSampleSelect.value);
    });
  }

  if (evaluateStoredButton) {
    evaluateStoredButton.addEventListener('click', function (event) {
      event.preventDefault();
      submitStoredEvaluation();
    });
  }
  if (evaluateInlineButton) {
    evaluateInlineButton.addEventListener('click', function (event) {
      event.preventDefault();
      submitInlineEvaluation();
    });
  }
  if (replayStoredButton) {
    replayStoredButton.addEventListener('click', function (event) {
      event.preventDefault();
      submitStoredReplay();
    });
  }
  if (replayInlineButton) {
    replayInlineButton.addEventListener('click', function (event) {
      event.preventDefault();
      submitInlineReplay();
    });
  }
  setTab(TAB_EVALUATE, { broadcast: false, force: true });
  setEvaluateMode(MODE_INLINE, { broadcast: false, force: true });
  setReplayMode(lastReplayMode, { broadcast: false, force: true });
  if (initialLegacyMode) {
    legacySetMode(initialLegacyMode, { broadcast: false, force: true });
  }
  updateEvaluateButtonCopy();
  updateReplayButtonCopy();
  setActiveStoredCredential(activeStoredCredentialId, { force: true });
  applyInlineSample(inlineSampleSelect && inlineSampleSelect.value);

  function submitStoredEvaluation() {
    if (!storedForm || !evaluateStoredButton) {
      return;
    }
    var endpoint = storedForm.getAttribute('data-evaluate-endpoint');
    var credentialId = elementValue(storedCredentialSelect);
    if (!endpoint) {
      handleStoredEvaluationError(
          { status: 0, payload: { reasonCode: 'endpoint_unavailable', message: 'Evaluation endpoint unavailable.' } });
      return;
    }
    if (!credentialId) {
      handleStoredEvaluationError(
          { status: 0, payload: { reasonCode: 'credential_required', message: 'Select a stored credential before evaluating.' } });
      return;
    }
    evaluateStoredButton.setAttribute('disabled', 'disabled');
    pendingStoredResult();
    var signatureCounter = parseInteger(elementValue(storedCounterInput));
    var uvValue = checkboxValue(storedUvSelect);
    var payload = {
      credentialId: credentialId,
      relyingPartyId: elementValue(storedRpInput),
      origin: elementValue(storedOriginInput),
      expectedType: CLIENT_DATA_TYPE,
      challenge: elementValue(storedChallengeField),
      signatureCounter: signatureCounter,
      userVerificationRequired: uvValue,
      privateKey: elementValue(storedPrivateKeyField),
    };
    sendJsonRequest(endpoint, payload, csrfToken(storedForm))
        .then(handleStoredEvaluationSuccess)
        .catch(handleStoredEvaluationError)
        .finally(function () {
          evaluateStoredButton.removeAttribute('disabled');
        });
  }

  function submitInlineEvaluation() {
    if (!inlineForm || !evaluateInlineButton) {
      return;
    }
    var endpoint = inlineForm.getAttribute('data-evaluate-endpoint');
    if (!endpoint) {
      handleInlineEvaluationError(
          { status: 0, payload: { reasonCode: 'endpoint_unavailable', message: 'Evaluation endpoint unavailable.' } });
      return;
    }
    evaluateInlineButton.setAttribute('disabled', 'disabled');
    pendingInlineResult();
    var signatureCounter = parseInteger(elementValue(inlineCounterInput));
    var uvRequired = checkboxValue(inlineUvRequiredSelect);
    var payload = {
      relyingPartyId: elementValue(inlineRpInput),
      origin: elementValue(inlineOriginInput),
      expectedType: CLIENT_DATA_TYPE,
      credentialId: elementValue(inlineCredentialIdField),
      signatureCounter: signatureCounter,
      userVerificationRequired: uvRequired,
      algorithm: elementValue(inlineAlgorithmInput),
      challenge: elementValue(inlineChallengeField),
      privateKey: elementValue(inlinePrivateKeyField),
    };
    if (activeInlineCredentialName) {
      payload.credentialName = activeInlineCredentialName;
    }
    sendJsonRequest(endpoint, payload, csrfToken(inlineForm))
        .then(handleInlineEvaluationSuccess)
        .catch(handleInlineEvaluationError)
        .finally(function () {
          evaluateInlineButton.removeAttribute('disabled');
        });
  }

  function submitStoredReplay() {
    if (!replayForm || !replayStoredButton) {
      return;
    }
    var endpoint = replayForm.getAttribute('data-replay-endpoint');
    var credentialId = elementValue(replayCredentialSelect);
    if (!endpoint) {
      handleStoredReplayError(
          { status: 0, payload: { reasonCode: 'endpoint_unavailable', message: 'Replay endpoint unavailable.' } });
      return;
    }
    if (!credentialId) {
      handleStoredReplayError(
          { status: 0, payload: { reasonCode: 'credential_required', message: 'Select a stored credential before replaying.' } });
      return;
    }
    replayStoredButton.setAttribute('disabled', 'disabled');
    pendingReplayStoredResult();
    var payload = {
      credentialId: credentialId,
      relyingPartyId: elementValue(replayRpInput),
      origin: elementValue(replayOriginInput),
      expectedType: CLIENT_DATA_TYPE,
      expectedChallenge: elementValue(replayChallengeField),
      clientData: elementValue(replayClientDataField),
      authenticatorData: elementValue(replayAuthenticatorDataField),
      signature: elementValue(replaySignatureField),
    };
    sendJsonRequest(endpoint, payload, csrfToken(replayForm))
        .then(handleStoredReplaySuccess)
        .catch(handleStoredReplayError)
        .finally(function () {
          replayStoredButton.removeAttribute('disabled');
        });
  }

  function submitInlineReplay() {
    if (!replayInlineForm || !replayInlineButton) {
      return;
    }
    var endpoint = replayInlineForm.getAttribute('data-replay-endpoint');
    if (!endpoint) {
      handleInlineReplayError(
          { status: 0, payload: { reasonCode: 'endpoint_unavailable', message: 'Replay endpoint unavailable.' } });
      return;
    }
    replayInlineButton.setAttribute('disabled', 'disabled');
    pendingReplayInlineResult();
    var signatureCounter = parseInteger(elementValue(replayInlineCounterInput));
    var uvRequired = checkboxValue(replayInlineUvRequiredSelect);
    var payload = {
      credentialId: elementValue(replayInlineCredentialIdField),
      publicKey: elementValue(replayInlinePublicKeyField),
      relyingPartyId: elementValue(replayInlineRpInput),
      origin: elementValue(replayInlineOriginInput),
      expectedType: CLIENT_DATA_TYPE,
      signatureCounter: signatureCounter,
      userVerificationRequired: uvRequired,
      algorithm: elementValue(replayInlineAlgorithmInput),
      expectedChallenge: elementValue(replayInlineChallengeField),
      clientData: elementValue(replayInlineClientDataField),
      authenticatorData: elementValue(replayInlineAuthenticatorDataField),
      signature: elementValue(replayInlineSignatureField),
    };
    if (activeReplayInlineCredentialName) {
      payload.credentialName = activeReplayInlineCredentialName;
    }
    sendJsonRequest(endpoint, payload, csrfToken(replayInlineForm))
        .then(handleInlineReplaySuccess)
        .catch(handleInlineReplayError)
        .finally(function () {
          replayInlineButton.removeAttribute('disabled');
        });
  }

  function handleStoredEvaluationSuccess(response) {
    setStoredAssertionText(formatAssertionJson(response && response.assertion));
    hasStoredEvaluationResult = true;
    refreshEvaluationResultVisibility();
  }

  function handleStoredEvaluationError(error) {
    var message = formatEvaluationErrorMessage(error, 'Stored generation');
    setStoredAssertionText(sanitizeMessage(message));
    hasStoredEvaluationResult = true;
    refreshEvaluationResultVisibility();
  }

  function handleInlineEvaluationSuccess(response) {
    setInlineGeneratedText(formatAssertionJson(response && response.assertion));
    hideInlineError();
    updateInlineTelemetry(
        formatEvaluationTelemetry(response && response.metadata, response && response.status));
    hasInlineEvaluationResult = true;
    refreshEvaluationResultVisibility();
  }

  function handleInlineEvaluationError(error) {
    var message = formatEvaluationErrorMessage(error, 'Inline generation');
    setInlineGeneratedText('Generation failed.');
    showInlineError(message);
    updateInlineTelemetry(message);
    hasInlineEvaluationResult = true;
    refreshEvaluationResultVisibility();
  }

  function handleStoredReplaySuccess(response) {
    var status = resolveReplayStatus(response && response.status, response && response.match);
    var reason = resolveReason(response && response.reasonCode, status);
    setStatusBadge(replayStatusBadge, status);
    setStatusText(replayOutcome, status, '—');
    setStatusText(replayReason, reason, '—');
    hasStoredReplayResult = true;
    refreshReplayResultVisibility();
  }

  function handleStoredReplayError(error) {
    var status = resolveReplayErrorStatus(error);
    var reason = resolveReason(readReasonCode(error), status);
    setStatusBadge(replayStatusBadge, status);
    setStatusText(replayOutcome, status, '—');
    setStatusText(replayReason, reason, '—');
    hasStoredReplayResult = true;
    refreshReplayResultVisibility();
  }

  function handleInlineReplaySuccess(response) {
    var status = resolveReplayStatus(response && response.status, response && response.match);
    var reason = resolveReason(response && response.reasonCode, status);
    setStatusBadge(replayInlineStatusBadge, status);
    setStatusText(replayInlineOutcome, status, '—');
    setStatusText(replayInlineReason, reason, '—');
    hasInlineReplayResult = true;
    refreshReplayResultVisibility();
  }

  function handleInlineReplayError(error) {
    var status = resolveReplayErrorStatus(error);
    var reason = resolveReason(readReasonCode(error), status);
    setStatusBadge(replayInlineStatusBadge, status);
    setStatusText(replayInlineOutcome, status, '—');
    setStatusText(replayInlineReason, reason, '—');
    hasInlineReplayResult = true;
    refreshReplayResultVisibility();
  }

  function setTab(tab, options) {
    if (tab !== TAB_EVALUATE && tab !== TAB_REPLAY) {
      return;
    }
    if (!options || !options.force) {
      if (currentTab === tab) {
        return;
      }
    }
    currentTab = tab;
    toggleTabButton(evaluateTabButton, tab === TAB_EVALUATE);
    toggleTabButton(replayTabButton, tab === TAB_REPLAY);
    toggleSection(evaluatePanel, tab === TAB_EVALUATE);
    toggleSection(replayPanel, tab === TAB_REPLAY);
    if (!options || options.broadcast !== false) {
      broadcastModeChange(computeLegacyMode(), Boolean(options && options.replace));
    }
  }

  function setEvaluateMode(mode, options) {
    if (mode !== MODE_STORED && mode !== MODE_INLINE) {
      return;
    }
    if (!options || !options.force) {
      if (currentEvaluateMode === mode) {
        return;
      }
    }
    currentEvaluateMode = mode;
    if (evaluateModeToggle) {
      evaluateModeToggle.setAttribute('data-mode', mode);
    }
    if (evaluateStoredRadio) {
      evaluateStoredRadio.checked = mode === MODE_STORED;
    }
    if (evaluateInlineRadio) {
      evaluateInlineRadio.checked = mode === MODE_INLINE;
    }
    toggleSection(evaluateStoredSection, mode === MODE_STORED);
    toggleSection(evaluateInlineSection, mode === MODE_INLINE);
    refreshEvaluationResultVisibility();
    toggleSeedActions();
    if (mode === MODE_INLINE) {
      refreshInlineCounterAfterPreset();
    }
    updateEvaluateButtonCopy();
    if (!options || options.broadcast !== false) {
      broadcastModeChange(computeLegacyMode(), Boolean(options && options.replace));
    }
  }

  function setReplayMode(mode, options) {
    if (mode !== MODE_STORED && mode !== MODE_INLINE) {
      return;
    }
    if (!options || !options.force) {
      if (currentReplayMode === mode) {
        return;
      }
    }
    currentReplayMode = mode;
    lastReplayMode = mode;
    if (replayModeToggle) {
      replayModeToggle.setAttribute('data-mode', mode);
    }
    if (replayStoredRadio) {
      replayStoredRadio.checked = mode === MODE_STORED;
    }
    if (replayInlineRadio) {
      replayInlineRadio.checked = mode === MODE_INLINE;
    }
    toggleSection(replayStoredSection, mode === MODE_STORED);
    toggleSection(replayInlineSection, mode === MODE_INLINE);
    refreshReplayResultVisibility();
    updateReplayButtonCopy();
    if (!options || options.broadcast !== false) {
      broadcastModeChange(computeLegacyMode(), Boolean(options && options.replace));
    }
  }

  function toggleTabButton(button, active) {
    if (!button) {
      return;
    }
    button.classList.toggle('mode-pill--active', active);
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  }

  function toggleSection(section, visible) {
    if (!section) {
      return;
    }
    if (visible) {
      section.removeAttribute('hidden');
      section.removeAttribute('aria-hidden');
    } else {
      section.setAttribute('hidden', 'hidden');
      section.setAttribute('aria-hidden', 'true');
    }
  }

  function applyButtonLabel(button, attributeName, fallback) {
    if (!button) {
      return;
    }
    var label = button.getAttribute(attributeName);
    if (typeof label !== 'string' || !label.trim()) {
      label = fallback;
    }
    var normalized = label.trim();
    if (button.textContent !== normalized) {
      button.textContent = normalized;
    }
  }

  function updateEvaluateButtonCopy() {
    applyButtonLabel(
        evaluateInlineButton, 'data-inline-label', 'Generate inline assertion');
    applyButtonLabel(
        evaluateStoredButton, 'data-stored-label', 'Generate stored assertion');
  }

  function updateReplayButtonCopy() {
    applyButtonLabel(
        replayInlineButton, 'data-inline-label', 'Replay inline assertion');
    applyButtonLabel(
        replayStoredButton, 'data-stored-label', 'Replay stored assertion');
  }

  function refreshEvaluationResultVisibility() {
    toggleSection(
        storedResultPanel,
        currentEvaluateMode === MODE_STORED && hasStoredEvaluationResult);
    toggleSection(
        inlineResultPanel,
        currentEvaluateMode === MODE_INLINE && hasInlineEvaluationResult);
  }

  function refreshReplayResultVisibility() {
    toggleSection(
        replayStoredResultPanel,
        currentReplayMode === MODE_STORED && hasStoredReplayResult);
    toggleSection(
        replayInlineResultPanel,
        currentReplayMode === MODE_INLINE && hasInlineReplayResult);
  }

  function refreshStoredCredentials() {
    if (!storedForm) {
      return;
    }
    var endpoint = storedForm.getAttribute('data-credentials-endpoint');
    fetchCredentials(endpoint).then(function (credentials) {
      updateCredentialSelect(storedCredentialSelect, credentials);
      updateCredentialSelect(replayCredentialSelect, credentials);
      toggleSeedActions();
      var nextActive = activeStoredCredentialId;
      if (
        nextActive
        && (!storedCredentialSelect
            || !elementHasOption(storedCredentialSelect, nextActive))
        && (!replayCredentialSelect
            || !elementHasOption(replayCredentialSelect, nextActive))) {
        nextActive = '';
      }
      setActiveStoredCredential(nextActive, { force: true });
    });
  }

  function setActiveStoredCredential(credentialId, options) {
    var normalized =
        typeof credentialId === 'string' ? credentialId.trim() : '';
    if (
      normalized
      && (!storedCredentialSelect
          || !elementHasOption(storedCredentialSelect, normalized))
      && (!replayCredentialSelect
          || !elementHasOption(replayCredentialSelect, normalized))) {
      normalized = '';
    }
    var shouldSync = options && options.force === true;
    if (normalized !== activeStoredCredentialId) {
      activeStoredCredentialId = normalized;
      shouldSync = true;
    }
    suppressStoredCredentialSync = true;
    syncStoredSelectValue(storedCredentialSelect, normalized);
    syncStoredSelectValue(replayCredentialSelect, normalized);
    suppressStoredCredentialSync = false;
    if (shouldSync) {
      applyStoredSample(normalized || null);
      applyStoredReplaySamples(normalized || null);
    }
  }

  function syncStoredSelectValue(select, credentialId) {
    if (!select) {
      return;
    }
    if (credentialId && elementHasOption(select, credentialId)) {
      select.value = credentialId;
    } else {
      select.value = '';
    }
  }

  function applyStoredSample(credentialId) {
    pendingStoredResult();
    if (!credentialId) {
      clearStoredEvaluationFields();
      return;
    }
    var definition = findSeedDefinition(credentialId);
    if (!definition) {
      clearStoredEvaluationFields();
      return;
    }
    var vectorKey =
        metadataValue(definition.metadata, 'presetKey')
            || metadataValue(definition.metadata, 'vectorId');
    var vector = resolveInlineVector(vectorKey);
    setValue(storedRpInput, definition.relyingPartyId || 'example.org');
    setValue(
        storedOriginInput,
        vector && vector.origin ? vector.origin : 'https://example.org');
    setValue(storedChallengeField, vector ? vector.expectedChallengeBase64Url : '');
    var presetCounter = 0;
    if (typeof definition.signatureCounter === 'number'
        && isFinite(definition.signatureCounter)) {
      presetCounter = definition.signatureCounter;
    } else if (
      vector
      && typeof vector.signatureCounter === 'number'
      && isFinite(vector.signatureCounter)) {
      presetCounter = vector.signatureCounter;
    }
    storedCounterBaseline = presetCounter;
    storedCounterSnapshotSeconds = null;
    setValue(storedCounterInput, presetCounter);
    refreshStoredCounterAfterPreset();
    setValue(storedPrivateKeyField, definition.privateKeyJwk || '');
    setStoredAssertionText('Awaiting submission.');
    if (inlineSampleSelect && vector) {
      inlineSampleSelect.value = vector.key;
    }
  }

  function clearStoredEvaluationFields() {
    setValue(storedRpInput, '');
    setValue(storedOriginInput, '');
    setValue(storedChallengeField, '');
    storedCounterBaseline = null;
    storedCounterSnapshotSeconds = null;
    setValue(storedCounterInput, '');
    updateStoredCounterHintText();
    setValue(storedPrivateKeyField, '');
    if (storedUvSelect) {
      storedUvSelect.checked = false;
    }
  }

  function applyStoredReplaySamples(credentialId) {
    pendingReplayStoredResult();
    if (!credentialId) {
      clearStoredReplayFields();
      return;
    }
    var definition = findSeedDefinition(credentialId);
    if (!definition) {
      clearStoredReplayFields();
      return;
    }
    var vectorKey =
        metadataValue(definition.metadata, 'presetKey')
            || metadataValue(definition.metadata, 'vectorId');
    var vector = resolveInlineVector(vectorKey);
    setValue(replayRpInput, definition.relyingPartyId || 'example.org');
    setValue(replayOriginInput, vector ? vector.origin || 'https://example.org' : 'https://example.org');
    setValue(replayChallengeField, vector ? vector.expectedChallengeBase64Url : '');
    setValue(replayClientDataField, vector ? vector.clientDataBase64Url : '');
    setValue(replayAuthenticatorDataField, vector ? vector.authenticatorDataBase64Url : '');
    setValue(replaySignatureField, vector ? vector.signatureBase64Url : '');
    updateReplayTelemetry('Telemetry ready (sanitized).');
  }

  function clearStoredReplayFields() {
    setValue(replayRpInput, '');
    setValue(replayOriginInput, '');
    setValue(replayChallengeField, '');
    setValue(replayClientDataField, '');
    setValue(replayAuthenticatorDataField, '');
    setValue(replaySignatureField, '');
  }

  function applyInlineSample(selectedKey) {
    if (!inlineVectors.length) {
      return;
    }
    var vector = resolveInlineVector(selectedKey);
    if (!vector) {
      pendingInlineResult();
      activeInlineCredentialName = null;
      refreshInlineCounterAfterPreset();
      return;
    }
    activeInlineCredentialName = vector.credentialName || null;
    setValue(inlineRpInput, vector.relyingPartyId || 'example.org');
    setValue(inlineOriginInput, vector.origin || 'https://example.org');
    setValue(inlineCredentialIdField, vector.credentialIdBase64Url || '');
    setValue(inlineAlgorithmInput, vector.algorithm || 'ES256');
    var presetCounter =
        vector.signatureCounter != null ? vector.signatureCounter : 0;
    setValue(inlineCounterInput, presetCounter);
    refreshInlineCounterAfterPreset();
    if (inlineUvRequiredSelect) {
      inlineUvRequiredSelect.checked = Boolean(vector.userVerificationRequired);
    }
    setValue(inlineChallengeField, vector.expectedChallengeBase64Url || '');
    setValue(inlinePrivateKeyField, vector.privateKeyJwk || '');
    if (inlineSampleSelect) {
      inlineSampleSelect.value = vector.key;
    }
    setInlineGeneratedText('Awaiting submission.');
    hideInlineError();
    updateInlineTelemetry('Sample vector loaded (sanitized).');
  }

  function initializeInlineCounter() {
    if (!inlineCounterInput) {
      return;
    }
    setInlineCounterReadOnly(Boolean(inlineCounterToggle && inlineCounterToggle.checked));
    if (inlineCounterToggle) {
      inlineCounterToggle.addEventListener('change', function () {
        setInlineCounterReadOnly(Boolean(inlineCounterToggle.checked));
        if (inlineCounterToggle.checked) {
          refreshInlineCounterSnapshot();
        } else {
          updateInlineCounterHintText();
        }
      });
    }
    if (inlineCounterResetButton) {
      inlineCounterResetButton.addEventListener('click', function (event) {
        event.preventDefault();
        refreshInlineCounterSnapshot();
      });
    }
    if (inlineCounterToggle && inlineCounterToggle.checked) {
      refreshInlineCounterSnapshot();
    } else {
      updateInlineCounterHintText();
    }
  }

  function refreshInlineCounterAfterPreset() {
    if (inlineCounterToggle && inlineCounterToggle.checked) {
      refreshInlineCounterSnapshot();
    } else {
      updateInlineCounterHintText();
    }
  }

  function refreshInlineCounterSnapshot() {
    if (!inlineCounterInput) {
      return null;
    }
    var nowSeconds = currentUnixSeconds();
    inlineCounterSnapshotSeconds = nowSeconds;
    setValue(inlineCounterInput, nowSeconds);
    updateInlineCounterHintText();
    return nowSeconds;
  }

  function updateInlineCounterHintText() {
    if (!inlineCounterHint) {
      return;
    }
    if (!inlineCounterToggle || inlineCounterToggle.checked) {
      if (inlineCounterSnapshotSeconds != null) {
        inlineCounterHint.textContent =
            'Last autofill: ' + formatUnixSeconds(inlineCounterSnapshotSeconds);
      } else {
        inlineCounterHint.textContent = 'Last autofill: awaiting snapshot.';
      }
      return;
    }
    if (inlineCounterSnapshotSeconds != null) {
      inlineCounterHint.textContent =
          'Manual entry enabled (last autofill ' + formatUnixSeconds(inlineCounterSnapshotSeconds) + ').';
    } else {
      inlineCounterHint.textContent = 'Manual entry enabled.';
    }
  }

  function setInlineCounterReadOnly(readOnly) {
    if (!inlineCounterInput) {
      return;
    }
    if (readOnly) {
      inlineCounterInput.setAttribute('readonly', 'readonly');
    } else {
      inlineCounterInput.removeAttribute('readonly');
    }
  }

  function initializeStoredCounter() {
    if (!storedCounterInput) {
      return;
    }
    setStoredCounterReadOnly(Boolean(storedCounterToggle && storedCounterToggle.checked));
    if (storedCounterToggle) {
      storedCounterToggle.addEventListener('change', function () {
        var checked = Boolean(storedCounterToggle.checked);
        setStoredCounterReadOnly(checked);
        if (checked) {
          refreshStoredCounterSnapshot();
        } else {
          updateStoredCounterHintText();
        }
      });
    }
    if (storedCounterResetButton) {
      storedCounterResetButton.addEventListener('click', function (event) {
        event.preventDefault();
        refreshStoredCounterSnapshot();
      });
    }
    if (storedCounterToggle && storedCounterToggle.checked) {
      refreshStoredCounterSnapshot();
    } else {
      updateStoredCounterHintText();
    }
  }

  function refreshStoredCounterAfterPreset() {
    if (storedCounterToggle && storedCounterToggle.checked) {
      refreshStoredCounterSnapshot();
    } else {
      updateStoredCounterHintText();
    }
  }

  function refreshStoredCounterSnapshot() {
    if (!storedCounterInput) {
      return null;
    }
    var nowSeconds = currentUnixSeconds();
    storedCounterSnapshotSeconds = nowSeconds;
    setValue(storedCounterInput, nowSeconds);
    updateStoredCounterHintText();
    return nowSeconds;
  }

  function updateStoredCounterHintText() {
    if (!storedCounterHint) {
      return;
    }
    if (storedCounterToggle && storedCounterToggle.checked) {
      if (storedCounterSnapshotSeconds != null) {
        storedCounterHint.textContent =
            'Last autofill: ' + formatUnixSeconds(storedCounterSnapshotSeconds);
      } else {
        storedCounterHint.textContent = 'Last autofill: awaiting snapshot.';
      }
      return;
    }
    if (storedCounterSnapshotSeconds != null) {
      storedCounterHint.textContent =
          'Manual entry enabled (last autofill '
          + formatUnixSeconds(storedCounterSnapshotSeconds) + ').';
      return;
    }
    if (storedCounterBaseline != null) {
      storedCounterHint.textContent =
          'Manual entry enabled (stored counter ' + storedCounterBaseline + ').';
      return;
    }
    storedCounterHint.textContent = 'Manual entry enabled.';
  }

  function setStoredCounterReadOnly(readOnly) {
    if (!storedCounterInput) {
      return;
    }
    if (readOnly) {
      storedCounterInput.setAttribute('readonly', 'readonly');
    } else {
      storedCounterInput.removeAttribute('readonly');
    }
  }

  function formatUnixSeconds(epochSeconds) {
    if (typeof epochSeconds !== 'number' || !isFinite(epochSeconds)) {
      return 'unknown';
    }
    try {
      return new Date(epochSeconds * 1000).toISOString();
    } catch (error) {
      return String(epochSeconds);
    }
  }

  function currentUnixSeconds() {
    return Math.floor(Date.now() / 1000);
  }

  function applyReplayInlineSample(selectedKey) {
    if (!inlineVectors.length) {
      return;
    }
    var vector = resolveInlineVector(selectedKey);
    if (!vector) {
      pendingReplayInlineResult();
      activeReplayInlineCredentialName = null;
      return;
    }
    activeReplayInlineCredentialName = vector.credentialName || null;
    setValue(replayInlineCredentialIdField, vector.credentialIdBase64Url || '');
    var publicKeyText = vector.publicKeyJwk || vector.publicKeyCoseBase64Url || '';
    setValue(replayInlinePublicKeyField, publicKeyText);
    setValue(replayInlineRpInput, vector.relyingPartyId || 'example.org');
    setValue(replayInlineOriginInput, vector.origin || 'https://example.org');
    setValue(replayInlineAlgorithmInput, vector.algorithm || 'ES256');
    setValue(
        replayInlineCounterInput,
        vector.signatureCounter != null ? vector.signatureCounter : 0);
    if (replayInlineUvRequiredSelect) {
      replayInlineUvRequiredSelect.checked = Boolean(vector.userVerificationRequired);
    }
    setValue(replayInlineChallengeField, vector.expectedChallengeBase64Url || '');
    setValue(replayInlineClientDataField, vector.clientDataBase64Url || '');
    setValue(
        replayInlineAuthenticatorDataField,
        vector.authenticatorDataBase64Url || '');
    setValue(replayInlineSignatureField, vector.signatureBase64Url || '');
    if (replayInlineSampleSelect) {
      replayInlineSampleSelect.value = vector.key;
    }
    updateReplayInlineTelemetry('Sample vector loaded (sanitized).');
  }

  function pendingStoredResult() {
    hasStoredEvaluationResult = false;
    refreshEvaluationResultVisibility();
    setStoredAssertionText('Awaiting submission.');
  }

  function pendingInlineResult() {
    hasInlineEvaluationResult = false;
    refreshEvaluationResultVisibility();
    setInlineGeneratedText('Awaiting submission.');
    hideInlineError();
    updateInlineTelemetry('Telemetry pending (sanitized).');
  }

  function setStoredAssertionText(text) {
    if (storedAssertionJson) {
      storedAssertionJson.textContent = text;
    }
  }

  function setInlineGeneratedText(text) {
    if (inlineGeneratedJson) {
      inlineGeneratedJson.textContent = text;
    }
  }

  function showInlineError(message) {
    if (!inlineErrorBanner) {
      return;
    }
    inlineErrorBanner.textContent = sanitizeMessage(message || 'Generation failed.');
    inlineErrorBanner.removeAttribute('hidden');
    inlineErrorBanner.setAttribute('aria-hidden', 'false');
  }

  function hideInlineError() {
    if (!inlineErrorBanner) {
      return;
    }
    inlineErrorBanner.textContent = '';
    inlineErrorBanner.setAttribute('hidden', 'hidden');
    inlineErrorBanner.setAttribute('aria-hidden', 'true');
  }

  function pendingReplayStoredResult() {
    hasStoredReplayResult = false;
    refreshReplayResultVisibility();
    setStatusBadge(replayStatusBadge, 'pending');
    setStatusText(replayReason, '—', '—');
    setStatusText(replayOutcome, '—', '—');
  }

  function pendingReplayInlineResult() {
    hasInlineReplayResult = false;
    refreshReplayResultVisibility();
    setStatusBadge(replayInlineStatusBadge, 'pending');
    setStatusText(replayInlineReason, '—', '—');
    setStatusText(replayInlineOutcome, '—', '—');
  }

  function updateInlineTelemetry(message) {
    if (inlineTelemetry) {
      inlineTelemetry.textContent = sanitizeMessage(message);
    }
  }

  function updateReplayTelemetry(message) {
    // Replay panels no longer render telemetry rows; retain hook for preset loaders.
    void message;
  }

  function updateReplayInlineTelemetry(message) {
    updateReplayTelemetry(message);
  }

  function setStatusBadge(badge, status) {
    if (!badge) {
      return;
    }
    var normalized = status ? String(status).toLowerCase() : '';
    var isSuccess =
        normalized === 'validated' ||
        normalized === 'success' ||
        normalized === 'match';
    var isInvalid =
        normalized === 'invalid' ||
        normalized === 'otp_out_of_window' ||
        normalized === 'mismatch' ||
        normalized === 'error' ||
        normalized === 'failure';
    var label = status ? String(status) : 'Pending';
    badge.textContent = label;
    badge.classList.remove('status-badge--success', 'status-badge--error');
    if (isSuccess) {
      badge.classList.add('status-badge--success');
    } else if (isInvalid) {
      badge.classList.add('status-badge--error');
    }
  }

  function setStatusText(element, value, fallback) {
    if (!element) {
      return;
    }
    var text = value && String(value).trim();
    if (!text) {
      text = fallback || 'unknown';
    }
    element.textContent = text;
  }

  function sanitizeMessage(message) {
    if (!message) {
      return 'sanitized';
    }
    var lower = message.toLowerCase();
    if (lower.indexOf('challenge') >= 0 || lower.indexOf('signature') >= 0) {
      return 'sanitized';
    }
    return message;
  }

  function checkboxValue(element) {
    if (!element) {
      return null;
    }
    if (element.checked) {
      return true;
    }
    if (element.hasAttribute('data-null-when-unchecked')) {
      return null;
    }
    return false;
  }

  function resolveReason(reason, fallback) {
    if (!reason || !String(reason).trim()) {
      return fallback || 'unknown';
    }
    return String(reason).trim();
  }

  function resolveErrorStatus(error) {
    var status = error && typeof error.status === 'number' ? error.status : 0;
    if (status >= 400 && status < 500) {
      return 'invalid';
    }
    return 'error';
  }

  function resolveReplayStatus(status, match) {
    if (status) {
      return String(status).trim();
    }
    if (match === true) {
      return 'match';
    }
    if (match === false) {
      return 'mismatch';
    }
    return 'unknown';
  }

  function resolveReplayErrorStatus(error) {
    var status = error && typeof error.status === 'number' ? error.status : 0;
    if (status >= 400 && status < 500) {
      return 'invalid';
    }
    return 'error';
  }

  function readReasonCode(error) {
    if (error && error.payload && error.payload.reasonCode) {
      return String(error.payload.reasonCode);
    }
    return null;
  }

  function readErrorMessage(error) {
    if (error && error.payload && error.payload.message) {
      return String(error.payload.message);
    }
    return null;
  }

  function formatAssertionJson(assertion) {
    if (!assertion) {
      return 'No assertion returned.';
    }
    try {
      return JSON.stringify(assertion, null, 2);
    } catch (ex) {
      return 'Unable to format assertion payload.';
    }
  }

  function formatEvaluationTelemetry(metadata, status) {
    if (!metadata) {
      return 'Evaluation telemetry recorded (sanitized).';
    }
    var parts = [];
    if (metadata.telemetryId) {
      parts.push('telemetryId=' + metadata.telemetryId);
    }
    if (metadata.credentialSource) {
      parts.push('credentialSource=' + metadata.credentialSource);
    }
    if (typeof metadata.credentialReference === 'boolean') {
      parts.push('credentialReference=' + metadata.credentialReference);
    }
    if (metadata.credentialId) {
      parts.push('credentialId=' + metadata.credentialId);
    }
    if (metadata.origin) {
      parts.push('origin=' + metadata.origin);
    }
    if (metadata.algorithm) {
      parts.push('algorithm=' + metadata.algorithm);
    }
    if (typeof metadata.userVerificationRequired === 'boolean') {
      parts.push('userVerificationRequired=' + metadata.userVerificationRequired);
    }
    if (metadata.error) {
      parts.push('error=' + metadata.error);
    }
    parts.push('status=' + (status || 'unknown'));
    return parts.join(' · ');
  }

  function formatEvaluationErrorMessage(error, prefix) {
    var reason = readReasonCode(error);
    var message = readErrorMessage(error);
    var status = error && typeof error.status === 'number' && error.status > 0 ? 'HTTP ' + error.status : null;
    var parts = [prefix || 'Evaluation'];
    if (reason) {
      parts.push('reason: ' + reason);
    }
    if (status) {
      parts.push(status);
    }
    if (message) {
      parts.push(message);
    }
    return parts.join(' – ');
  }

  function elementValue(element) {
    if (!element) {
      return '';
    }
    var value = element.value;
    if (value == null) {
      return '';
    }
    if (typeof value === 'string') {
      return value.trim();
    }
    return String(value);
  }

  function parseInteger(value) {
    if (value == null || value === '') {
      return null;
    }
    var parsed = parseInt(value, 10);
    if (isNaN(parsed)) {
      return null;
    }
    return parsed;
  }

  function sendJsonRequest(endpoint, payload, csrf) {
    return new Promise(function (resolve, reject) {
      if (!endpoint) {
        reject({ status: 0, payload: null });
        return;
      }
      try {
        var request = new XMLHttpRequest();
        request.open('POST', endpoint, true);
        request.setRequestHeader('Content-Type', 'application/json');
        if (csrf) {
          request.setRequestHeader('X-CSRF-TOKEN', csrf);
        }
        request.onreadystatechange = function () {
          if (request.readyState === 4) {
            var responseText = request.responseText || '';
            if (request.status >= 200 && request.status < 300) {
              if (!responseText) {
                resolve({});
                return;
              }
              try {
                resolve(JSON.parse(responseText));
              } catch (error) {
                resolve({});
              }
              return;
            }
            var parsed = null;
            if (responseText) {
              try {
                parsed = JSON.parse(responseText);
              } catch (error) {
                parsed = null;
              }
            }
            reject({ status: request.status, payload: parsed });
          }
        };
        request.onerror = function () {
          reject({ status: 0, payload: null });
        };
        request.send(JSON.stringify(payload || {}));
      } catch (error) {
        reject({ status: 0, payload: null });
      }
    });
  }

  function populateInlineSampleOptions() {
    if (inlineSampleSelect) {
      if (inlineSampleSelect.options.length <= 1) {
        populateSelectWithVectors(inlineSampleSelect, 'Select a sample');
      } else {
        enableSelect(inlineSampleSelect);
      }
    }
    populateSelectWithVectors(replayInlineSampleSelect, 'Select a sample');
  }

  function populateSelectWithVectors(select, placeholderLabel) {
    if (!select) {
      return;
    }
    clearSelect(select);
    addPlaceholderOption(select, placeholderLabel || 'Select a sample');
    var seenAlgorithms = Object.create(null);
    inlineVectors.forEach(function (vector) {
      if (!vector || !vector.key) {
        return;
      }
      var algorithm = vector.algorithm || '';
      if (algorithm && seenAlgorithms[algorithm]) {
        return;
      }
      if (algorithm) {
        seenAlgorithms[algorithm] = true;
      }
      var option = documentRef.createElement('option');
      option.value = vector.key;
      option.textContent = vector.label || vector.key;
      if (vector.algorithm) {
        option.setAttribute('data-algorithm', vector.algorithm);
      }
      if (vector.credentialName) {
        option.setAttribute('data-credential-name', vector.credentialName);
      }
      select.appendChild(option);
    });
    enableSelect(select);
  }

  function addPlaceholderOption(select, label) {
    var option = documentRef.createElement('option');
    option.value = '';
    option.textContent = label;
    select.appendChild(option);
  }

  function clearSelect(select) {
    if (!select) {
      return;
    }
    while (select.options.length > 0) {
      select.remove(0);
    }
  }

  function enableSelect(select) {
    if (!select) {
      return;
    }
    if (select.options.length <= 1) {
      select.disabled = true;
      select.setAttribute('disabled', 'disabled');
    } else {
      select.disabled = false;
      select.removeAttribute('disabled');
    }
  }

  function updateCredentialSelect(select, credentials) {
    if (!select) {
      return;
    }
    var previous = select.value;
    clearSelect(select);
    addPlaceholderOption(select, 'Select a stored credential');
    credentials.forEach(function (summary) {
      var option = documentRef.createElement('option');
      option.value = summary.id;
      option.textContent = summary.label || summary.id;
      select.appendChild(option);
    });
    var hasCredentials = credentials.length > 0;
    select.disabled = !hasCredentials;
    var desired = '';
    if (activeStoredCredentialId && elementHasOption(select, activeStoredCredentialId)) {
      desired = activeStoredCredentialId;
    } else if (previous && elementHasOption(select, previous)) {
      desired = previous;
    }
    select.value = desired;
  }

  function elementHasOption(select, value) {
    for (var index = 0; index < select.options.length; index += 1) {
      if (select.options[index].value === value) {
        return true;
      }
    }
    return false;
  }

  function seedStoredCredentials() {
    if (!storedForm) {
      return;
    }
    var endpoint = storedForm.getAttribute('data-seed-endpoint');
    if (!endpoint) {
      updateSeedStatus('Seed endpoint unavailable.', 'error');
      return;
    }
    postJson(endpoint, {}, csrfToken(storedForm))
        .then(function (response) {
          if (!response.ok) {
            updateSeedStatus('Unable to seed sample credentials.', 'error');
            return;
          }
          var parsed = parseSeedResponse(response.bodyText);
          var addedCount = resolveAddedCount(parsed);
          var message =
              'Seeded ' +
              addedCount +
              ' sample credential' +
              (addedCount === 1 ? '' : 's') +
              '.';
          var severity = null;
          if (addedCount === 0) {
            message += ' All sample credentials are already present.';
            severity = 'warning';
          }
          updateSeedStatus(message, severity);
          refreshStoredCredentials();
        })
        .catch(function () {
          updateSeedStatus('Unable to seed sample credentials.', 'error');
        });
  }

  function csrfToken(form) {
    if (!form) {
      return null;
    }
    var csrfField = form.querySelector('input[name="_csrf"]');
    return csrfField ? csrfField.value : null;
  }

  function postJson(endpoint, payload, csrf) {
    return new Promise(function (resolve) {
      try {
        var request = new XMLHttpRequest();
        request.open('POST', endpoint, true);
        request.setRequestHeader('Content-Type', 'application/json');
        if (csrf) {
          request.setRequestHeader('X-CSRF-TOKEN', csrf);
        }
        request.onreadystatechange = function () {
          if (request.readyState === 4) {
            resolve({
              ok: request.status >= 200 && request.status < 300,
              status: request.status,
              bodyText: request.responseText || '',
            });
          }
        };
        request.onerror = function () {
          resolve({ ok: false, status: 0, bodyText: '' });
        };
        request.send(JSON.stringify(payload || {}));
      } catch (error) {
        resolve({ ok: false, status: 0, bodyText: '' });
      }
    });
  }

  function fetchCredentials(endpoint) {
    if (!endpoint) {
      return Promise.resolve([]);
    }
    return new Promise(function (resolve) {
      try {
        var request = new XMLHttpRequest();
        request.open('GET', endpoint, true);
        request.onreadystatechange = function () {
          if (request.readyState === 4) {
            if (request.status >= 200 && request.status < 300) {
              try {
                var payload = JSON.parse(request.responseText || '[]');
                resolve(Array.isArray(payload) ? payload : []);
              } catch (error) {
                resolve([]);
              }
            } else {
              resolve([]);
            }
          }
        };
        request.onerror = function () {
          resolve([]);
        };
        request.send();
      } catch (error) {
        resolve([]);
      }
    });
  }

  function toggleSeedActions() {
    if (!storedSeedActions) {
      return;
    }
    var visible = currentEvaluateMode === MODE_STORED;
    if (visible) {
      storedSeedActions.removeAttribute('hidden');
      storedSeedActions.setAttribute('aria-hidden', 'false');
      if (storedSeedButton) {
        storedSeedButton.removeAttribute('disabled');
      }
    } else {
      storedSeedActions.setAttribute('hidden', 'hidden');
      storedSeedActions.setAttribute('aria-hidden', 'true');
      if (storedSeedButton) {
        storedSeedButton.setAttribute('disabled', 'disabled');
      }
      if (storedSeedStatus) {
        storedSeedStatus.setAttribute('hidden', 'hidden');
        storedSeedStatus.setAttribute('aria-hidden', 'true');
      }
    }
  }

  function updateSeedStatus(message, severity) {
    if (!storedSeedStatus) {
      return;
    }
    storedSeedStatus.classList.remove('credential-status--error', 'credential-status--warning');
    var text = typeof message === 'string' ? message.trim() : '';
    if (!text) {
      storedSeedStatus.textContent = '';
      storedSeedStatus.setAttribute('hidden', 'hidden');
      storedSeedStatus.setAttribute('aria-hidden', 'true');
      return;
    }
    storedSeedStatus.textContent = text;
    if (severity === 'error') {
      storedSeedStatus.classList.add('credential-status--error');
    } else if (severity === 'warning') {
      storedSeedStatus.classList.add('credential-status--warning');
    }
    storedSeedStatus.removeAttribute('hidden');
    storedSeedStatus.removeAttribute('aria-hidden');
  }

  function parseSeedResponse(bodyText) {
    if (typeof bodyText !== 'string' || !bodyText.trim()) {
      return {};
    }
    try {
      var parsed = JSON.parse(bodyText);
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch (error) {
      return {};
    }
  }

  function resolveAddedCount(payload) {
    if (!payload || typeof payload !== 'object') {
      return 0;
    }
    if (typeof payload.addedCount === 'number' && !Number.isNaN(payload.addedCount)) {
      return payload.addedCount;
    }
    if (Array.isArray(payload.addedCredentialIds)) {
      return payload.addedCredentialIds.length;
    }
    return 0;
  }

  function parseJson(scriptNode) {
    if (!scriptNode || !scriptNode.textContent) {
      return [];
    }
    var raw = scriptNode.textContent.trim();
    if (!raw) {
      return [];
    }
    try {
      var parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed;
      }
      return [];
    } catch (error) {
      if (global.console && typeof global.console.error === 'function') {
        global.console.error('Failed to parse WebAuthn console data', error);
      }
      return [];
    }
  }

  function createInlineVectorIndex(vectors) {
    var index = {};
    vectors.forEach(function (vector) {
      if (vector && vector.key) {
        index[vector.key] = vector;
        if (vector.metadata && vector.metadata.vectorId) {
          index[vector.metadata.vectorId] = vector;
        }
      }
    });
    return index;
  }

  function resolveInlineVector(key) {
    if (!key) {
      return null;
    }
    if (inlineVectorIndex[key]) {
      return inlineVectorIndex[key];
    }
    if (inlineVectors.length > 0) {
      return inlineVectors[0];
    }
    return null;
  }

  function findSeedDefinition(credentialId) {
    for (var index = 0; index < seedDefinitions.length; index += 1) {
      if (seedDefinitions[index].credentialId === credentialId) {
        return seedDefinitions[index];
      }
    }
    return null;
  }

  function metadataValue(metadata, key) {
    if (!metadata || typeof metadata !== 'object') {
      return undefined;
    }
    return metadata[key];
  }

  function setValue(element, value) {
    if (!element) {
      return;
    }
    var normalized = value == null ? '' : value;
    if (element.dataset && element.dataset.jsonPretty === 'true' && typeof normalized === 'string') {
      var trimmed = normalized.trim();
      if (trimmed && (trimmed.charAt(0) === '{' || trimmed.charAt(0) === '[')) {
        try {
          normalized = JSON.stringify(JSON.parse(trimmed), null, 2);
        } catch (formatError) {
          // leave value as-is when parsing fails
        }
      }
    }
    if (element.tagName === 'SELECT') {
      element.value = normalized;
    } else {
      element.value = normalized;
    }
  }

  function removeNode(node) {
    if (node && node.parentNode) {
      node.parentNode.removeChild(node);
    }
  }

  function readInitialLegacyMode() {
    var candidate = null;
    if (panel) {
      var attr = panel.getAttribute('data-initial-fido2-mode');
      if (attr && typeof attr === 'string') {
        candidate = attr.trim().toLowerCase();
      }
      panel.removeAttribute('data-initial-fido2-mode');
    }
    if (!candidate && typeof global.__openauthFido2InitialMode === 'string') {
      candidate = global.__openauthFido2InitialMode.toLowerCase();
    }
    global.__openauthFido2InitialMode = undefined;
    if (candidate === 'replay' || candidate === 'inline' || candidate === 'stored') {
      return candidate;
    }
    return null;
  }

  function broadcastModeChange(mode, replace) {
    try {
      var event = new global.CustomEvent('operator:fido2-mode-changed', {
        detail: { mode: mode, replace: Boolean(replace) },
      });
      global.dispatchEvent(event);
    } catch (error) {
      if (global.console && typeof global.console.error === 'function') {
        global.console.error('Failed to broadcast FIDO2 mode change', error);
      }
    }
  }

  function computeLegacyMode() {
    if (currentTab === TAB_REPLAY) {
      return 'replay';
    }
    return currentEvaluateMode === MODE_INLINE ? 'inline' : 'stored';
  }

  function legacySetMode(mode, options) {
    var normalised = (mode || '').toLowerCase();
    if (normalised === 'replay') {
      var desiredReplayMode = lastReplayMode;
      if (options && options.replayMode && (options.replayMode === MODE_INLINE || options.replayMode === MODE_STORED)) {
        desiredReplayMode = options.replayMode;
      }
      if (desiredReplayMode !== MODE_INLINE && desiredReplayMode !== MODE_STORED) {
        desiredReplayMode = MODE_INLINE;
      }
      setTab(TAB_REPLAY, mergeOptions(options, { replace: true, broadcast: false }));
      setReplayMode(desiredReplayMode, mergeOptions(options, { broadcast: false, force: true }));
      broadcastModeChange('replay', Boolean(options && options.replace));
      return;
    }
    if (normalised === 'inline') {
      setTab(TAB_EVALUATE, mergeOptions(options, { replace: true, broadcast: false }));
      setEvaluateMode(MODE_INLINE, mergeOptions(options, { broadcast: false, force: true }));
      broadcastModeChange('inline', Boolean(options && options.replace));
      return;
    }
    setTab(TAB_EVALUATE, mergeOptions(options, { replace: true, broadcast: false }));
    setEvaluateMode(MODE_STORED, mergeOptions(options, { broadcast: false, force: true }));
    broadcastModeChange('stored', Boolean(options && options.replace));
  }

  function mergeOptions(options, overrides) {
    var merged = {};
    var key;
    if (options) {
      for (key in options) {
        if (Object.prototype.hasOwnProperty.call(options, key)) {
          merged[key] = options[key];
        }
      }
    }
    for (key in overrides) {
      if (Object.prototype.hasOwnProperty.call(overrides, key)) {
        merged[key] = overrides[key];
      }
    }
    return merged;
  }

  global.Fido2Console = {
    setMode: function (mode, options) {
      legacySetMode(mode, options || {});
    },
    getMode: function () {
      return computeLegacyMode();
    },
    setTab: function (tab, options) {
      setTab(tab, options || {});
    },
    getTab: function () {
      return currentTab;
    },
    setEvaluateMode: function (mode, options) {
      setEvaluateMode(mode, options || {});
    },
    getEvaluateMode: function () {
      return currentEvaluateMode;
    },
    setReplayMode: function (mode, options) {
      setReplayMode(mode, options || {});
    },
    getReplayMode: function () {
      return currentReplayMode;
    },
  };
})(window);
