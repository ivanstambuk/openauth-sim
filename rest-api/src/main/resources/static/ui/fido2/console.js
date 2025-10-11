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
  var storedCopyButton =
      storedResultPanel
          ? storedResultPanel.querySelector('[data-testid="fido2-copy-assertion"]')
          : null;
  var storedDownloadButton =
      storedResultPanel
          ? storedResultPanel.querySelector('[data-testid="fido2-download-assertion"]')
          : null;
  var storedMetadata =
      storedResultPanel
          ? storedResultPanel.querySelector('[data-testid="fido2-generated-assertion-metadata"]')
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
  var replayStoredStatus = panel.querySelector('[data-testid="fido2-replay-status"]');
  var replayReason = panel.querySelector('[data-testid="fido2-replay-reason"]');
  var replayMessage = panel.querySelector('[data-testid="fido2-replay-message"]');
  var replayTelemetry =
      panel.querySelector('[data-testid="fido2-replay-telemetry"]');
  var replayInlineResultPanel =
      panel.querySelector('[data-testid="fido2-replay-inline-result"]');
  var replayInlineStatus =
      replayInlineResultPanel
          ? replayInlineResultPanel.querySelector('[data-testid="fido2-replay-inline-status"]')
          : null;
  var replayInlineReason =
      replayInlineResultPanel
          ? replayInlineResultPanel.querySelector('[data-testid="fido2-replay-inline-reason"]')
          : null;
  var replayInlineMessage =
      replayInlineResultPanel
          ? replayInlineResultPanel.querySelector('[data-testid="fido2-replay-inline-message"]')
          : null;
  var replayInlineTelemetry =
      replayInlineResultPanel
          ? replayInlineResultPanel.querySelector('[data-testid="fido2-replay-inline-telemetry"]')
          : null;

  var storedForm = panel.querySelector('[data-testid="fido2-stored-form"]');
  var storedCredentialSelect = panel.querySelector('#fido2StoredCredentialId');
  var storedSeedActions = panel.querySelector('[data-testid="fido2-seed-actions"]');
  var storedSeedButton = panel.querySelector('[data-testid="fido2-seed-credentials"]');
  var storedSeedStatus = panel.querySelector('[data-testid="fido2-seed-status"]');
  var storedRpInput = panel.querySelector('#fido2StoredRpId');
  var storedOriginInput = panel.querySelector('#fido2StoredOrigin');
  var storedTypeInput = panel.querySelector('#fido2StoredType');
  var storedLoadSampleButton =
      panel.querySelector('[data-testid="fido2-stored-load-sample"]');
  var storedChallengeField = panel.querySelector('#fido2StoredChallenge');
  var storedPrivateKeyField = panel.querySelector('#fido2StoredPrivateKey');
  var storedCounterInput = panel.querySelector('#fido2StoredCounter');
  var storedUvSelect = panel.querySelector('#fido2StoredUvRequired');

  var inlineForm = panel.querySelector('[data-testid="fido2-inline-form"]');
  var inlineRpInput = panel.querySelector('#fido2InlineRpId');
  var inlineOriginInput = panel.querySelector('#fido2InlineOrigin');
  var inlineTypeInput = panel.querySelector('#fido2InlineType');
  var inlineCredentialNameInput = panel.querySelector('#fido2InlineCredentialName');
  var inlineCredentialIdField = panel.querySelector('#fido2InlineCredentialId');
  var inlineAlgorithmInput = panel.querySelector('#fido2InlineAlgorithm');
  var inlineCounterInput = panel.querySelector('#fido2InlineCounter');
  var inlineUvRequiredSelect = panel.querySelector('#fido2InlineUvRequired');
  var inlineChallengeField = panel.querySelector('#fido2InlineChallenge');
  var inlinePrivateKeyField = panel.querySelector('#fido2InlinePrivateKey');
  var inlineSampleSelect = panel.querySelector('#fido2InlineSampleSelect');
  var inlineLoadSampleButton =
      panel.querySelector('[data-testid="fido2-inline-load-sample"]');

  var replayForm = panel.querySelector('[data-testid="fido2-replay-form"]');
  var replayCredentialSelect = panel.querySelector('#fido2ReplayCredentialId');
  var replayOriginInput = panel.querySelector('#fido2ReplayOrigin');
  var replayRpInput = panel.querySelector('#fido2ReplayRpId');
  var replayTypeInput = panel.querySelector('#fido2ReplayType');
  var replayChallengeField = panel.querySelector('#fido2ReplayChallenge');
  var replayClientDataField = panel.querySelector('#fido2ReplayClientData');
  var replayAuthenticatorDataField =
      panel.querySelector('#fido2ReplayAuthenticatorData');
  var replaySignatureField = panel.querySelector('#fido2ReplaySignature');

  var replayInlineForm = panel.querySelector('[data-testid="fido2-replay-inline-form"]');
  var replayInlineCredentialNameInput =
      panel.querySelector('#fido2ReplayInlineCredentialName');
  var replayInlineCredentialIdField =
      panel.querySelector('#fido2ReplayInlineCredentialId');
  var replayInlinePublicKeyField =
      panel.querySelector('#fido2ReplayInlinePublicKey');
  var replayInlineRpInput = panel.querySelector('#fido2ReplayInlineRpId');
  var replayInlineOriginInput = panel.querySelector('#fido2ReplayInlineOrigin');
  var replayInlineTypeInput = panel.querySelector('#fido2ReplayInlineType');
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
  var replayInlineLoadSampleButton =
      panel.querySelector('[data-testid="fido2-replay-inline-load-sample"]');

  var seedDefinitionsNode = panel.querySelector('#fido2-seed-definitions');
  var inlineVectorsNode = panel.querySelector('#fido2-inline-vectors');

  var seedDefinitions = parseJson(seedDefinitionsNode);
  var inlineVectors = parseJson(inlineVectorsNode);
  var inlineVectorIndex = createInlineVectorIndex(inlineVectors);

  removeNode(seedDefinitionsNode);
  removeNode(inlineVectorsNode);

  populateInlineSampleOptions();
  refreshStoredCredentials();

  var currentTab = TAB_EVALUATE;
  var currentEvaluateMode = MODE_INLINE;
  var currentReplayMode = MODE_INLINE;

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
      applyStoredSample(storedCredentialSelect.value);
    });
  }

  if (storedSeedButton) {
    storedSeedButton.addEventListener('click', function () {
      seedStoredCredentials();
    });
  }
  if (storedLoadSampleButton) {
    storedLoadSampleButton.addEventListener('click', function (event) {
      event.preventDefault();
      applyStoredSample(storedCredentialSelect && storedCredentialSelect.value);
    });
  }

  if (inlineSampleSelect) {
    inlineSampleSelect.addEventListener('change', function () {
      applyInlineSample(inlineSampleSelect.value);
    });
  }
  if (inlineLoadSampleButton) {
    inlineLoadSampleButton.addEventListener('click', function () {
      applyInlineSample(inlineSampleSelect && inlineSampleSelect.value);
    });
  }
  if (replayInlineSampleSelect) {
    replayInlineSampleSelect.addEventListener('change', function () {
      applyReplayInlineSample(replayInlineSampleSelect.value);
    });
  }
  if (replayInlineLoadSampleButton) {
    replayInlineLoadSampleButton.addEventListener('click', function () {
      applyReplayInlineSample(replayInlineSampleSelect && replayInlineSampleSelect.value);
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
  if (storedCopyButton) {
    storedCopyButton.addEventListener('click', function (event) {
      event.preventDefault();
      copyToClipboard(storedAssertionJson ? storedAssertionJson.textContent : '');
    });
  }
  if (storedDownloadButton) {
    storedDownloadButton.addEventListener('click', function (event) {
      event.preventDefault();
      downloadAssertionJson(storedAssertionJson ? storedAssertionJson.textContent : '');
    });
  }

  setTab(TAB_EVALUATE, { broadcast: false, force: true });
  setEvaluateMode(MODE_INLINE, { broadcast: false, force: true });
  setReplayMode(MODE_INLINE, { broadcast: false, force: true });
  applyStoredSample(storedCredentialSelect && storedCredentialSelect.value);
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
      expectedType: elementValue(storedTypeInput),
      challenge: elementValue(storedChallengeField),
      privateKey: elementValue(storedPrivateKeyField),
      signatureCounter: signatureCounter,
      userVerificationRequired: uvValue,
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
      credentialName: elementValue(inlineCredentialNameInput),
      relyingPartyId: elementValue(inlineRpInput),
      origin: elementValue(inlineOriginInput),
      expectedType: elementValue(inlineTypeInput),
      credentialId: elementValue(inlineCredentialIdField),
      signatureCounter: signatureCounter,
      userVerificationRequired: uvRequired,
      algorithm: elementValue(inlineAlgorithmInput),
      challenge: elementValue(inlineChallengeField),
      privateKey: elementValue(inlinePrivateKeyField),
    };
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
      expectedType: elementValue(replayTypeInput),
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
      credentialName: elementValue(replayInlineCredentialNameInput),
      credentialId: elementValue(replayInlineCredentialIdField),
      publicKey: elementValue(replayInlinePublicKeyField),
      relyingPartyId: elementValue(replayInlineRpInput),
      origin: elementValue(replayInlineOriginInput),
      expectedType: elementValue(replayInlineTypeInput),
      signatureCounter: signatureCounter,
      userVerificationRequired: uvRequired,
      algorithm: elementValue(replayInlineAlgorithmInput),
      expectedChallenge: elementValue(replayInlineChallengeField),
      clientData: elementValue(replayInlineClientDataField),
      authenticatorData: elementValue(replayInlineAuthenticatorDataField),
      signature: elementValue(replayInlineSignatureField),
    };
    sendJsonRequest(endpoint, payload, csrfToken(replayInlineForm))
        .then(handleInlineReplaySuccess)
        .catch(handleInlineReplayError)
        .finally(function () {
          replayInlineButton.removeAttribute('disabled');
        });
  }

  function handleStoredEvaluationSuccess(response) {
    setStoredAssertionText(formatAssertionJson(response && response.assertion));
    enableStoredActions(true);
    updateStoredMetadata(
        formatEvaluationTelemetry(response && response.metadata, response && response.status));
  }

  function handleStoredEvaluationError(error) {
    var message = formatEvaluationErrorMessage(error, 'Stored generation');
    setStoredAssertionText(sanitizeMessage(message));
    enableStoredActions(false);
    updateStoredMetadata(message);
  }

  function handleInlineEvaluationSuccess(response) {
    setInlineGeneratedText(formatAssertionJson(response && response.assertion));
    hideInlineError();
    updateInlineTelemetry(
        formatEvaluationTelemetry(response && response.metadata, response && response.status));
  }

  function handleInlineEvaluationError(error) {
    var message = formatEvaluationErrorMessage(error, 'Inline generation');
    setInlineGeneratedText('Generation failed.');
    showInlineError(message);
    updateInlineTelemetry(message);
  }

  function handleStoredReplaySuccess(response) {
    var status = resolveReplayStatus(response && response.status, response && response.match);
    var reason = resolveReason(response && response.reasonCode, status);
    setStatusText(replayStoredStatus, status, 'match');
    setStatusText(replayReason, reason, status);
    setReplayMessage(formatReplaySuccessMessage(response && response.metadata, status));
    updateReplayTelemetry(formatReplayTelemetry(response && response.metadata, status));
  }

  function handleStoredReplayError(error) {
    var status = resolveReplayErrorStatus(error);
    var reason = resolveReason(readReasonCode(error), status);
    setStatusText(replayStoredStatus, status, 'error');
    setStatusText(replayReason, reason, status);
    setReplayMessage(formatReplayErrorMessage(error, 'Stored replay'));
    updateReplayTelemetry(formatReplayErrorMessage(error, 'Stored replay'));
  }

  function handleInlineReplaySuccess(response) {
    var status = resolveReplayStatus(response && response.status, response && response.match);
    var reason = resolveReason(response && response.reasonCode, status);
    setStatusText(replayInlineStatus, status, 'match');
    setStatusText(replayInlineReason, reason, status);
    setReplayInlineMessage(formatReplaySuccessMessage(response && response.metadata, status));
    updateReplayInlineTelemetry(formatReplayTelemetry(response && response.metadata, status));
  }

  function handleInlineReplayError(error) {
    var status = resolveReplayErrorStatus(error);
    var reason = resolveReason(readReasonCode(error), status);
    setStatusText(replayInlineStatus, status, 'error');
    setStatusText(replayInlineReason, reason, status);
    setReplayInlineMessage(formatReplayErrorMessage(error, 'Inline replay'));
    updateReplayInlineTelemetry(formatReplayErrorMessage(error, 'Inline replay'));
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
    toggleSection(evaluateStoredSection, mode === MODE_STORED);
    toggleSection(evaluateInlineSection, mode === MODE_INLINE);
    toggleSection(storedResultPanel, mode === MODE_STORED);
    toggleSection(inlineResultPanel, mode === MODE_INLINE);
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
    if (replayModeToggle) {
      replayModeToggle.setAttribute('data-mode', mode);
    }
    toggleSection(replayStoredSection, mode === MODE_STORED);
    toggleSection(replayInlineSection, mode === MODE_INLINE);
    toggleSection(replayInlineResultPanel, mode === MODE_INLINE);
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

  function refreshStoredCredentials() {
    if (!storedForm) {
      return;
    }
    var endpoint = storedForm.getAttribute('data-credentials-endpoint');
    fetchCredentials(endpoint).then(function (credentials) {
      updateCredentialSelect(storedCredentialSelect, credentials);
      updateCredentialSelect(replayCredentialSelect, credentials);
      toggleSeedActions(credentials.length === 0);
      if (credentials.length > 0) {
        var firstId = credentials[0].id;
        if (storedCredentialSelect && !storedCredentialSelect.value) {
          storedCredentialSelect.value = firstId;
        }
        if (replayCredentialSelect && !replayCredentialSelect.value) {
          replayCredentialSelect.value = firstId;
        }
        applyStoredSample(storedCredentialSelect && storedCredentialSelect.value);
        applyStoredReplaySamples(replayCredentialSelect && replayCredentialSelect.value);
      } else {
        applyStoredSample(null);
        applyStoredReplaySamples(null);
      }
    });
  }

  function applyStoredSample(credentialId) {
    if (!credentialId) {
      pendingStoredResult();
      setValue(storedChallengeField, '');
      setValue(storedPrivateKeyField, '');
      setValue(storedCounterInput, '');
      if (storedUvSelect) {
        storedUvSelect.checked = false;
      }
      return;
    }
    var definition = findSeedDefinition(credentialId);
    if (!definition) {
      pendingStoredResult();
      setValue(storedChallengeField, '');
      setValue(storedPrivateKeyField, '');
      setValue(storedCounterInput, '');
      if (storedUvSelect) {
        storedUvSelect.checked = false;
      }
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
    setValue(
        storedTypeInput,
        vector && vector.expectedType ? vector.expectedType : 'webauthn.get');
    setValue(storedChallengeField, vector ? vector.expectedChallengeBase64Url : '');
    setValue(storedPrivateKeyField, definition.privateKeyJwk || '');
    setValue(storedCounterInput, '');
    setStoredAssertionText('Awaiting submission.');
    enableStoredActions(false);
    updateStoredMetadata('Telemetry ready (sanitized).');
    if (inlineSampleSelect && vector) {
      inlineSampleSelect.value = vector.key;
    }
  }

  function applyStoredReplaySamples(credentialId) {
    if (!credentialId) {
      pendingReplayStoredResult();
      return;
    }
    var definition = findSeedDefinition(credentialId);
    if (!definition) {
      pendingReplayStoredResult();
      return;
    }
    var vectorKey =
        metadataValue(definition.metadata, 'presetKey')
            || metadataValue(definition.metadata, 'vectorId');
    var vector = resolveInlineVector(vectorKey);
    setValue(replayRpInput, definition.relyingPartyId || 'example.org');
    setValue(replayTypeInput, vector && vector.expectedType ? vector.expectedType : 'webauthn.get');
    setValue(replayOriginInput, vector ? vector.origin || 'https://example.org' : 'https://example.org');
    setValue(replayChallengeField, vector ? vector.expectedChallengeBase64Url : '');
    setValue(replayClientDataField, vector ? vector.clientDataBase64Url : '');
    setValue(replayAuthenticatorDataField, vector ? vector.authenticatorDataBase64Url : '');
    setValue(replaySignatureField, vector ? vector.signatureBase64Url : '');
    updateReplayTelemetry('Telemetry ready (sanitized).');
  }

  function applyInlineSample(selectedKey) {
    if (!inlineVectors.length) {
      return;
    }
    var vector = resolveInlineVector(selectedKey);
    if (!vector) {
      pendingInlineResult();
      return;
    }
    setValue(inlineRpInput, vector.relyingPartyId || 'example.org');
    setValue(inlineOriginInput, vector.origin || 'https://example.org');
    setValue(inlineTypeInput, vector.expectedType || 'webauthn.get');
    setValue(inlineCredentialNameInput, vector.credentialName || 'fido2-inline');
    setValue(inlineCredentialIdField, vector.credentialIdBase64Url || '');
    setValue(inlineAlgorithmInput, vector.algorithm || 'ES256');
    setValue(
        inlineCounterInput,
        vector.signatureCounter != null ? vector.signatureCounter : 0);
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

  function applyReplayInlineSample(selectedKey) {
    if (!inlineVectors.length) {
      return;
    }
    var vector = resolveInlineVector(selectedKey);
    if (!vector) {
      pendingReplayInlineResult();
      return;
    }
    setValue(replayInlineCredentialNameInput, vector.credentialName || 'fido2-inline');
    setValue(replayInlineCredentialIdField, vector.credentialIdBase64Url || '');
    setValue(replayInlinePublicKeyField, vector.publicKeyCoseBase64Url || '');
    setValue(replayInlineRpInput, vector.relyingPartyId || 'example.org');
    setValue(replayInlineOriginInput, vector.origin || 'https://example.org');
    setValue(replayInlineTypeInput, vector.expectedType || 'webauthn.get');
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
    setStoredAssertionText('Awaiting submission.');
    enableStoredActions(false);
    updateStoredMetadata('Telemetry pending (sanitized).');
  }

  function pendingInlineResult() {
    setInlineGeneratedText('Awaiting submission.');
    hideInlineError();
    updateInlineTelemetry('Telemetry pending (sanitized).');
  }

  function setStoredAssertionText(text) {
    if (storedAssertionJson) {
      storedAssertionJson.textContent = text;
    }
  }

  function enableStoredActions(enabled) {
    if (storedCopyButton) {
      if (enabled) {
        storedCopyButton.removeAttribute('disabled');
      } else {
        storedCopyButton.setAttribute('disabled', 'disabled');
      }
    }
    if (storedDownloadButton) {
      if (enabled) {
        storedDownloadButton.removeAttribute('disabled');
      } else {
        storedDownloadButton.setAttribute('disabled', 'disabled');
      }
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
    if (replayStoredStatus) {
      replayStoredStatus.textContent = 'pending';
    }
    if (replayReason) {
      replayReason.textContent = 'awaiting replay';
    }
    setReplayMessage('Awaiting replay.');
    updateReplayTelemetry('Awaiting replay (sanitized).');
  }

  function pendingReplayInlineResult() {
    if (replayInlineStatus) {
      replayInlineStatus.textContent = 'pending';
    }
    if (replayInlineReason) {
      replayInlineReason.textContent = 'awaiting replay';
    }
    setReplayInlineMessage('Awaiting replay.');
    updateReplayInlineTelemetry('Awaiting replay (sanitized).');
  }

  function updateStoredMetadata(message) {
    if (storedMetadata) {
      storedMetadata.textContent = sanitizeMessage(message);
    }
  }

  function updateInlineTelemetry(message) {
    if (inlineTelemetry) {
      inlineTelemetry.textContent = sanitizeMessage(message);
    }
  }

  function updateReplayTelemetry(message) {
    if (replayTelemetry) {
      replayTelemetry.textContent = sanitizeMessage(message);
    }
  }

  function updateReplayInlineTelemetry(message) {
    if (replayInlineTelemetry) {
      replayInlineTelemetry.textContent = sanitizeMessage(message);
    }
  }

  function setReplayMessage(message) {
    setMessageText(replayMessage, message, 'Awaiting replay.');
  }

  function setReplayInlineMessage(message) {
    setMessageText(replayInlineMessage, message, 'Awaiting replay.');
  }

  function setMessageText(element, message, fallback) {
    if (!element) {
      return;
    }
    var text = message;
    if (!text || !String(text).trim()) {
      text = fallback || 'sanitized';
    }
    element.textContent = sanitizeMessage(String(text));
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

  function formatReplayTelemetry(metadata, status) {
    if (!metadata) {
      return 'Replay telemetry recorded (sanitized).';
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

  function formatReplaySuccessMessage(metadata, status) {
    var origin = metadata && metadata.origin ? metadata.origin : 'unknown origin';
    return 'Replay ' + (status || 'match') + ' for ' + origin + '.';
  }

  function formatReplayErrorMessage(error, prefix) {
    var reason = readReasonCode(error);
    var message = readErrorMessage(error);
    var status = error && typeof error.status === 'number' && error.status > 0 ? 'HTTP ' + error.status : null;
    var parts = [prefix || 'Replay'];
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

  function copyToClipboard(text) {
    var content = text || '';
    if (!content) {
      return;
    }
    var nav = global.navigator;
    if (nav && nav.clipboard && nav.clipboard.writeText) {
      nav.clipboard.writeText(content).catch(function () {
        fallbackCopy(content);
      });
      return;
    }
    fallbackCopy(content);
  }

  function fallbackCopy(content) {
    var textarea = documentRef.createElement('textarea');
    textarea.value = content;
    textarea.setAttribute('readonly', 'readonly');
    textarea.style.position = 'absolute';
    textarea.style.left = '-9999px';
    documentRef.body.appendChild(textarea);
    textarea.select();
    try {
      documentRef.execCommand('copy');
    } catch (ex) {
      // ignore copy failures
    }
    documentRef.body.removeChild(textarea);
  }

  function downloadAssertionJson(text) {
    if (!text) {
      return;
    }
    var blob = new Blob([text], { type: 'application/json' });
    var url = global.URL.createObjectURL(blob);
    var link = documentRef.createElement('a');
    link.href = url;
    link.download = 'webauthn-assertion.json';
    documentRef.body.appendChild(link);
    link.click();
    documentRef.body.removeChild(link);
    global.URL.revokeObjectURL(url);
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
    populateSelectWithVectors(inlineSampleSelect);
    populateSelectWithVectors(replayInlineSampleSelect);
  }

  function populateSelectWithVectors(select) {
    if (!select) {
      return;
    }
    clearSelect(select);
    addPlaceholderOption(select, 'Select a sample vector');
    inlineVectors.forEach(function (vector) {
      var option = documentRef.createElement('option');
      option.value = vector.key;
      option.textContent = vector.label || vector.key;
      select.appendChild(option);
    });
    select.disabled = select.options.length <= 1;
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
    if (hasCredentials) {
      if (previous && elementHasOption(select, previous)) {
        select.value = previous;
      } else {
        select.value = credentials[0].id;
      }
    }
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
      updateSeedStatus('Seed endpoint unavailable.');
      return;
    }
    postJson(endpoint, {}, csrfToken(storedForm))
        .then(function (success) {
          if (success) {
            updateSeedStatus('Seeded sample credentials.');
            refreshStoredCredentials();
          } else {
            updateSeedStatus('Unable to seed credentials.');
          }
        })
        .catch(function () {
          updateSeedStatus('Unable to seed credentials.');
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
            resolve(request.status >= 200 && request.status < 300);
          }
        };
        request.onerror = function () {
          resolve(false);
        };
        request.send(JSON.stringify(payload || {}));
      } catch (error) {
        resolve(false);
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

  function toggleSeedActions(shouldShow) {
    if (!storedSeedActions) {
      return;
    }
    if (shouldShow) {
      storedSeedActions.removeAttribute('hidden');
      storedSeedActions.setAttribute('aria-hidden', 'false');
    } else {
      storedSeedActions.setAttribute('hidden', 'hidden');
      storedSeedActions.setAttribute('aria-hidden', 'true');
    }
  }

  function updateSeedStatus(message) {
    if (!storedSeedStatus) {
      return;
    }
    storedSeedStatus.textContent = message;
    storedSeedStatus.removeAttribute('hidden');
    storedSeedStatus.setAttribute('aria-hidden', 'false');
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
    if (key && inlineVectorIndex[key]) {
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
    if (element.tagName === 'SELECT') {
      element.value = value;
    } else if (element.tagName === 'TEXTAREA') {
      element.value = value == null ? '' : value;
    } else {
      element.value = value == null ? '' : value;
    }
  }

  function removeNode(node) {
    if (node && node.parentNode) {
      node.parentNode.removeChild(node);
    }
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
      setTab(TAB_REPLAY, mergeOptions(options, { replace: true, broadcast: false }));
      setReplayMode(MODE_STORED, mergeOptions(options, { broadcast: false, force: true }));
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
