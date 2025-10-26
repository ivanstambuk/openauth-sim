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

  var verboseConsole = global.VerboseTraceConsole || null;
  var TAB_EVALUATE = 'evaluate';
  var TAB_REPLAY = 'replay';
  var MODE_STORED = 'stored';
  var MODE_INLINE = 'inline';
  var CEREMONY_ASSERTION = 'assertion';
  var CEREMONY_ATTESTATION = 'attestation';

  var ALGORITHM_SEQUENCE = ['ES256', 'ES384', 'ES512', 'RS256', 'PS256', 'EdDSA'];

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
  var ceremonyToggle =
      panel.querySelector('[data-testid="fido2-evaluate-ceremony-toggle"]');
  var ceremonyAssertionButton =
      panel.querySelector('[data-testid="fido2-evaluate-ceremony-select-assertion"]');
  var ceremonyAttestationButton =
      panel.querySelector('[data-testid="fido2-evaluate-ceremony-select-attestation"]');
  var evaluateHeading = panel.querySelector('#fido2-evaluate-heading');
  var evaluateStoredRadio =
      panel.querySelector('[data-testid="fido2-evaluate-mode-select-stored"]');
  var evaluateInlineRadio =
      panel.querySelector('[data-testid="fido2-evaluate-mode-select-inline"]');
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
  function clearVerboseTrace() {
    if (verboseConsole && typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    }
  }
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
  var replayHeading = panel.querySelector('#fido2-replay-heading');
  var replayCeremonyToggle =
      panel.querySelector('[data-testid="fido2-replay-ceremony-toggle"]');
  var replayCeremonyAssertionButton =
      panel.querySelector('[data-testid="fido2-replay-ceremony-select-assertion"]');
  var replayCeremonyAttestationButton =
      panel.querySelector('[data-testid="fido2-replay-ceremony-select-attestation"]');

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

  var attestationModeToggle =
      panel.querySelector('[data-testid="fido2-attestation-mode-toggle"]');
  var attestationInlineOption =
      panel.querySelector('[data-testid="fido2-attestation-mode-select-inline"]');
  var attestationStoredOption =
      panel.querySelector('[data-testid="fido2-attestation-mode-select-stored"]');
  var attestationInlineSection =
      panel.querySelector('[data-testid="fido2-attestation-inline-section"]');
  var attestationStoredSection =
      panel.querySelector('[data-testid="fido2-attestation-stored-section"]');
  var attestationStoredForm =
      panel.querySelector('[data-testid="fido2-attestation-stored-form"]');
  var attestationStoredCredentialSelect =
      panel.querySelector('#fido2AttestationStoredCredentialId');
  var attestationStoredRpInput =
      panel.querySelector('#fido2AttestationStoredRpId');
  var attestationStoredOriginInput =
      panel.querySelector('#fido2AttestationStoredOrigin');
  var attestationStoredChallengeField =
      panel.querySelector('#fido2AttestationStoredChallenge');
  var attestationStoredFormatInput =
      panel.querySelector('#fido2AttestationStoredFormat');
  var attestationStoredSubmitButton =
      panel.querySelector('[data-testid="fido2-attestation-stored-submit"]');
  var attestationSeedActions =
      panel.querySelector('[data-testid="fido2-attestation-seed-actions"]');
  var attestationSeedButton =
      panel.querySelector('[data-testid="fido2-attestation-seed"]');
  var attestationSeedStatus =
      panel.querySelector('[data-testid="fido2-attestation-seed-status"]');

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

  var replayAttestationForm =
      panel.querySelector('[data-testid="fido2-replay-attestation-form"]');
  var replayAttestationIdField =
      panel.querySelector('#fido2ReplayAttestationId');
  var replayAttestationFormatSelect =
      panel.querySelector('#fido2ReplayAttestationFormat');
  var replayAttestationRpInput =
      panel.querySelector('#fido2ReplayAttestationRpId');
  var replayAttestationOriginInput =
      panel.querySelector('#fido2ReplayAttestationOrigin');
  var replayAttestationChallengeField =
      panel.querySelector('#fido2ReplayAttestationChallenge');
  var replayAttestationClientDataField =
      panel.querySelector('#fido2ReplayAttestationClientDataJson');
  var replayAttestationObjectField =
      panel.querySelector('#fido2ReplayAttestationObject');
  var replayAttestationTrustAnchorsField =
      panel.querySelector('#fido2ReplayAttestationTrustAnchors');
  var replayAttestationSubmitButton =
      panel.querySelector('[data-testid="fido2-replay-attestation-submit"]');
  var replayAttestationResultPanel =
      panel.querySelector('[data-testid="fido2-replay-attestation-result"]');
  var replayAttestationStatusBadge =
      panel.querySelector('[data-testid="fido2-replay-attestation-status"]');
  var replayAttestationReason =
      panel.querySelector('[data-testid="fido2-replay-attestation-reason"]');
  var replayAttestationOutcome =
      panel.querySelector('[data-testid="fido2-replay-attestation-outcome"]');
  var replayAttestationAnchorSource =
      panel.querySelector('[data-testid="fido2-replay-attestation-anchor-source"]');
  var replayAttestationAnchorTrusted =
      panel.querySelector('[data-testid="fido2-replay-attestation-anchor-trusted"]');

  var seedDefinitionsNode = panel.querySelector('#fido2-seed-definitions');
  var inlineVectorsNode = panel.querySelector('#fido2-inline-vectors');
  var assertionViews = panel.querySelectorAll('[data-ceremony-view="assertion"]');
  var attestationViews = panel.querySelectorAll('[data-ceremony-view="attestation"]');
  var replayAssertionViews =
      panel.querySelectorAll('[data-replay-ceremony-view="assertion"]');
  var replayAttestationViews =
      panel.querySelectorAll('[data-replay-ceremony-view="attestation"]');
  var attestationVectorsNode = panel.querySelector('#fido2-attestation-vectors');
  var attestationForm =
      panel.querySelector('[data-testid="fido2-attestation-form"]');
  var attestationSampleSelect = panel.querySelector('#fido2AttestationSampleSelect');
  var attestationIdInput = panel.querySelector('#fido2AttestationId');
  var attestationRpInput = panel.querySelector('#fido2AttestationRpId');
  var attestationOriginInput = panel.querySelector('#fido2AttestationOrigin');
  var attestationFormatSelect = panel.querySelector('#fido2AttestationFormat');
  var attestationChallengeField = panel.querySelector('#fido2AttestationChallenge');
  var attestationCredentialKeyField =
      panel.querySelector('#fido2AttestationCredentialKey');
  var attestationPrivateKeyField =
      panel.querySelector('#fido2AttestationPrivateKey');
  var attestationSerialField = panel.querySelector('#fido2AttestationSerial');
  var attestationSigningModeSelect =
      panel.querySelector('#fido2AttestationSigningMode');
  var attestationCustomRootField =
      panel.querySelector('#fido2AttestationCustomRoot');
  var attestationSubmitButton =
      panel.querySelector('[data-testid="fido2-attestation-submit"]');
  var attestationResultPanel =
      panel.querySelector('[data-testid="fido2-attestation-result"]');
  var attestationResultJson =
      panel.querySelector('[data-testid="fido2-attestation-result-json"]');
  var attestationStatusBadge =
      panel.querySelector('[data-testid="fido2-attestation-status"]');
  var attestationCertificateSection =
      panel.querySelector('[data-testid="fido2-attestation-certificate-chain-section"]');
  var attestationCertificateHeading =
      panel.querySelector('[data-testid="fido2-attestation-certificate-heading"]');
  var attestationCertificateChain =
      panel.querySelector('[data-testid="fido2-attestation-certificate-chain"]');

  var hasStoredEvaluationResult = false;
  var hasInlineEvaluationResult = false;
  var hasStoredReplayResult = false;
  var hasInlineReplayResult = false;
  var hasAttestationResult = false;
  var hasReplayAttestationResult = false;

  var seedDefinitions = parseJson(seedDefinitionsNode);
  var inlineVectors = parseJson(inlineVectorsNode);
  var attestationVectors = parseJson(attestationVectorsNode);
  var inlineVectorIndex = createInlineVectorIndex(inlineVectors);
  var attestationVectorIndex = createAttestationVectorIndex(attestationVectors);
  var activeInlineCredentialName = null;
  var activeReplayInlineCredentialName = null;
  var inlineCounterSnapshotSeconds = null;
  var storedCounterSnapshotSeconds = null;
  var storedCounterBaseline = null;
  var activeStoredCredentialId = '';
  var suppressStoredCredentialSync = false;

  removeNode(seedDefinitionsNode);
  removeNode(inlineVectorsNode);
  removeNode(attestationVectorsNode);

  var evaluateStoredModeOption = findModeOption(evaluateStoredRadio);
  var evaluateInlineModeOption = findModeOption(evaluateInlineRadio);
  var replayStoredModeOption = findModeOption(replayStoredRadio);
  var replayInlineModeOption = findModeOption(replayInlineRadio);
  pendingAttestationResult();
  pendingReplayAttestationResult();
  populateInlineSampleOptions();
  populateAttestationVectorOptions();
  refreshStoredCredentials();
  initializeInlineCounter();
  initializeStoredCounter();
  var currentCeremony = CEREMONY_ASSERTION;
  var currentTab = TAB_EVALUATE;
  var currentEvaluateMode = MODE_INLINE;
  var lastAssertionEvaluateMode = MODE_INLINE;
  var currentReplayMode = MODE_INLINE;
  var lastReplayMode = MODE_INLINE;
  var currentReplayCeremony = CEREMONY_ASSERTION;
  var lastReplayAssertionMode = MODE_INLINE;
  var currentAttestationMode = MODE_INLINE;
  var lastBroadcastTab = null;
  var lastBroadcastEvaluateMode = null;
  var lastBroadcastReplayMode = null;

  function verboseAttach(payload) {
    if (!payload || typeof payload !== 'object' || !verboseConsole) {
      return payload;
    }
    if (typeof verboseConsole.attachVerboseFlag === 'function') {
      return verboseConsole.attachVerboseFlag(payload);
    }
    if (typeof verboseConsole.isEnabled === 'function' && verboseConsole.isEnabled()) {
      payload.verbose = true;
    } else if (Object.prototype.hasOwnProperty.call(payload, 'verbose')) {
      delete payload.verbose;
    }
    return payload;
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

  function verboseApplyResponse(payload, variant) {
    if (!verboseConsole) {
      return;
    }
    var options = { variant: variant || 'info', protocol: 'fido2' };
    if (typeof verboseConsole.handleResponse === 'function') {
      verboseConsole.handleResponse(payload, options);
    } else if (payload && payload.trace && typeof verboseConsole.renderTrace === 'function') {
      verboseConsole.renderTrace(payload.trace, options);
    } else if (typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    }
  }

  function verboseApplyError(error) {
    if (!verboseConsole) {
      return;
    }
    var payload = error && error.payload ? error.payload : null;
    var options = { variant: 'error', protocol: 'fido2' };
    if (typeof verboseConsole.handleError === 'function') {
      verboseConsole.handleError(payload, options);
    } else if (payload && payload.trace && typeof verboseConsole.renderTrace === 'function') {
      verboseConsole.renderTrace(payload.trace, options);
    } else if (typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    }
  }

  var initialPanelState = readInitialPanelState();
  currentEvaluateMode = initialPanelState.evaluateMode;
  lastAssertionEvaluateMode = currentEvaluateMode;
  currentReplayMode = initialPanelState.replayMode;
  lastReplayMode = currentReplayMode;
  lastReplayAssertionMode = currentReplayMode;

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

  if (ceremonyAssertionButton) {
    ceremonyAssertionButton.addEventListener('click', function (event) {
      event.preventDefault();
      setCeremony(CEREMONY_ASSERTION, { broadcast: true });
    });
  }
  if (ceremonyAttestationButton) {
    ceremonyAttestationButton.addEventListener('click', function (event) {
      event.preventDefault();
      setCeremony(CEREMONY_ATTESTATION, { broadcast: true });
    });
  }
  if (replayCeremonyAssertionButton) {
    replayCeremonyAssertionButton.addEventListener('click', function (event) {
      event.preventDefault();
      setReplayCeremony(CEREMONY_ASSERTION, { broadcast: true });
    });
  }
  if (replayCeremonyAttestationButton) {
    replayCeremonyAttestationButton.addEventListener('click', function (event) {
      event.preventDefault();
      setReplayCeremony(CEREMONY_ATTESTATION, { broadcast: true });
    });
  }

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
  if (attestationSampleSelect) {
    attestationSampleSelect.addEventListener('change', function () {
      applyAttestationSample(attestationSampleSelect.value);
    });
  }
  if (attestationCustomRootField && attestationSigningModeSelect) {
    attestationCustomRootField.addEventListener('input', function () {
      var value = attestationCustomRootField.value;
      if (typeof value === 'string' && value.trim()) {
        attestationSigningModeSelect.value = 'CUSTOM_ROOT';
      } else if (attestationSigningModeSelect.value === 'CUSTOM_ROOT') {
        attestationSigningModeSelect.value = 'SELF_SIGNED';
      }
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
  if (attestationStoredSubmitButton) {
    attestationStoredSubmitButton.addEventListener('click', function (event) {
      event.preventDefault();
      submitStoredAttestationGeneration();
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
  if (attestationInlineOption) {
    attestationInlineOption.addEventListener('change', function () {
      if (attestationInlineOption.checked) {
        setAttestationMode(MODE_INLINE);
      }
    });
  }
  if (attestationStoredOption) {
    attestationStoredOption.addEventListener('change', function () {
      if (attestationStoredOption.checked) {
        setAttestationMode(MODE_STORED);
      }
    });
  }
  if (attestationSeedButton) {
    attestationSeedButton.addEventListener('click', function (event) {
      event.preventDefault();
      seedStoredAttestations();
    });
  }
  if (attestationStoredCredentialSelect) {
    attestationStoredCredentialSelect.addEventListener('change', function () {
      setActiveStoredCredential(attestationStoredCredentialSelect.value, { force: true });
    });
  }
  if (replayAttestationSubmitButton) {
    replayAttestationSubmitButton.addEventListener('click', function (event) {
      event.preventDefault();
      submitReplayAttestation();
    });
  }
  if (attestationSubmitButton) {
    attestationSubmitButton.addEventListener('click', function (event) {
      event.preventDefault();
      submitAttestationGeneration();
    });
  }
  setCeremony(CEREMONY_ASSERTION, { broadcast: false, force: true });
  setReplayCeremony(CEREMONY_ASSERTION, { broadcast: false, force: true });
  setEvaluateMode(currentEvaluateMode, { broadcast: false, force: true });
  setReplayMode(currentReplayMode, { broadcast: false, force: true });
  preloadStoredCredentialSelects();
  setAttestationMode(currentAttestationMode, { broadcast: false, force: true });
  setTab(initialPanelState.tab, { broadcast: false, force: true, replace: true });
  updateEvaluateButtonCopy();
  updateReplayButtonCopy();
  setActiveStoredCredential(activeStoredCredentialId, { force: true });
  applyInlineSample(inlineSampleSelect && inlineSampleSelect.value);
  applyAttestationSample(attestationSampleSelect && attestationSampleSelect.value);

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
    payload = verboseAttach(payload);
    verboseBeginRequest();
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
    payload = verboseAttach(payload);
    verboseBeginRequest();
    sendJsonRequest(endpoint, payload, csrfToken(inlineForm))
        .then(handleInlineEvaluationSuccess)
        .catch(handleInlineEvaluationError)
        .finally(function () {
          evaluateInlineButton.removeAttribute('disabled');
        });
  }

  function submitAttestationGeneration() {
    if (!attestationForm || !attestationSubmitButton) {
      return;
    }
    var endpoint = attestationForm.getAttribute('data-attestation-endpoint');
    if (!endpoint) {
      handleAttestationGenerationError(
          { status: 0, payload: { reasonCode: 'endpoint_unavailable', message: 'Attestation generation endpoint unavailable.' } });
      return;
    }
    attestationSubmitButton.setAttribute('disabled', 'disabled');
    pendingAttestationResult();
    var seedPresetId = elementValue(attestationIdInput);
    var inputFormat = elementValue(attestationFormatSelect);
    var inputRpId = elementValue(attestationRpInput);
    var inputOrigin = elementValue(attestationOriginInput);
    var inputChallenge = elementValue(attestationChallengeField);
    var inputCredentialKey = elementValue(attestationCredentialKeyField);
    var inputAttestationKey = elementValue(attestationPrivateKeyField);
    var inputSerial = elementValue(attestationSerialField);
    var inputSigningMode = elementValue(attestationSigningModeSelect);
    var customRootRaw = elementValue(attestationCustomRootField);
    var inputCustomRoots = extractCustomRootCertificates(customRootRaw);

    var snapshot = {
      format: inputFormat,
      relyingPartyId: inputRpId,
      origin: inputOrigin,
      challengeBase64Url: inputChallenge,
      credentialPrivateKey: inputCredentialKey,
      attestationPrivateKey: inputAttestationKey,
      attestationCertificateSerial: inputSerial,
      signingMode: inputSigningMode,
      customRootCertificates: customRootRaw,
    };

    var sourceInfo = resolveAttestationInputSource(seedPresetId, snapshot);

    var payload = {
      inputSource: sourceInfo.inputSource,
      format: inputFormat,
      relyingPartyId: inputRpId,
      origin: inputOrigin,
      challenge: inputChallenge,
      credentialPrivateKey: inputCredentialKey,
      attestationPrivateKey: inputAttestationKey,
      attestationCertificateSerial: inputSerial,
      signingMode: inputSigningMode,
      customRootCertificates: inputCustomRoots,
    };
    if (sourceInfo.inputSource === 'PRESET') {
      payload.attestationId = seedPresetId;
    } else {
      if (sourceInfo.seedPresetId) {
        payload.seedPresetId = sourceInfo.seedPresetId;
      }
      if (sourceInfo.overrides && sourceInfo.overrides.length) {
        payload.overrides = sourceInfo.overrides;
      }
    }
    payload = verboseAttach(payload);
    verboseBeginRequest();
    sendJsonRequest(endpoint, payload, csrfToken(attestationForm))
        .then(handleAttestationGenerationSuccess)
        .catch(handleAttestationGenerationError)
        .finally(function () {
          attestationSubmitButton.removeAttribute('disabled');
        });
  }

  function resolveAttestationInputSource(seedPresetId, current) {
    var result = { inputSource: 'MANUAL', seedPresetId: '', overrides: [] };
    var seedId = typeof seedPresetId === 'string' ? seedPresetId.trim() : '';
    if (!seedId) {
      return result; // Manual without preset
    }
    var vector = attestationVectorIndex ? attestationVectorIndex[seedId] : null;
    if (!vector) {
      return result; // Manual without a known preset id
    }
    // Compare fields to detect overrides.
    var diffs = [];
    if (safeTrim(current.format) !== safeTrim(vector.format)) {
      diffs.push('format');
    }
    if (safeTrim(current.challengeBase64Url) !== safeTrim(vector.challengeBase64Url)) {
      diffs.push('challenge');
    }
    if (safeTrim(current.relyingPartyId) !== safeTrim(vector.relyingPartyId)) {
      diffs.push('relyingPartyId');
    }
    if (safeTrim(current.origin) !== safeTrim(vector.origin)) {
      diffs.push('origin');
    }
    if (safeTrim(current.credentialPrivateKey) !== safeTrim(vector.credentialPrivateKey)) {
      diffs.push('credentialPrivateKey');
    }
    if (safeTrim(current.attestationPrivateKey) !== safeTrim(vector.attestationPrivateKey)) {
      diffs.push('attestationPrivateKey');
    }
    if (safeTrim(current.attestationCertificateSerial) !== safeTrim(vector.attestationCertificateSerial)) {
      diffs.push('attestationCertificateSerial');
    }
    var normalizedSigningMode = normalizeSigningMode(current.signingMode);
    var vectorSigningMode = normalizeSigningMode(vector.signingMode || 'SELF_SIGNED');
    if (normalizedSigningMode !== vectorSigningMode) {
      diffs.push('signingMode');
    }
    var currentCustomRoots = safeTrim(current.customRootCertificates);
    var vectorCustomRoots = safeTrim(vector.customRootCertificates);
    if (currentCustomRoots !== vectorCustomRoots) {
      if (currentCustomRoots || vectorCustomRoots) {
        diffs.push('customRootCertificates');
      }
    }
    if (diffs.length === 0) {
      return { inputSource: 'PRESET', seedPresetId: seedId };
    }
    result.seedPresetId = seedId;
    result.overrides = diffs;
    return result;
  }

  function safeTrim(value) {
    return typeof value === 'string' ? value.trim() : '';
  }

  function normalizeSigningMode(value) {
    var normalized = safeTrim(value);
    if (!normalized) {
      return 'SELF_SIGNED';
    }
    return normalized.toUpperCase().replace(/-/g, '_');
  }

  function collectAttestationSnapshot() {
    return {
      format: elementValue(attestationFormatSelect),
      relyingPartyId: elementValue(attestationRpInput),
      origin: elementValue(attestationOriginInput),
      challengeBase64Url: elementValue(attestationChallengeField),
      credentialPrivateKey: elementValue(attestationCredentialKeyField),
      attestationPrivateKey: elementValue(attestationPrivateKeyField),
      attestationCertificateSerial: elementValue(attestationSerialField),
      signingMode: elementValue(attestationSigningModeSelect),
      customRootCertificates: elementValue(attestationCustomRootField),
    };
  }

  function handleAttestationGenerationSuccess(response) {
    hideAttestationError();
    var statusText = response && response.status ? String(response.status).trim() : 'success';
    if (!response || typeof response !== 'object') {
      verboseApplyResponse(null, 'success');
    } else {
      var variant = statusText.toLowerCase() === 'error' ? 'error' : 'success';
      verboseApplyResponse(response, variant);
    }
    var generated =
        response && response.generatedAttestation ? response.generatedAttestation : null;
    var metadata = response && response.metadata ? response.metadata : null;
    if (!generated) {
      setStatusBadge(attestationStatusBadge, 'error');
      setStatusText(attestationOutcome, 'generation_failed', 'generation_failed');
      toggleSection(attestationResultPanel, false);
      refreshEvaluationResultVisibility();
      if (attestationResultJson) {
        attestationResultJson.textContent = 'Awaiting submission.';
      }
      return;
    }

    setStatusBadge(attestationStatusBadge, statusText);

    var responsePayload = generated.response || {};
    if (attestationResultJson) {
      attestationResultJson.textContent = buildAttestationJson(generated, responsePayload);
    }
    renderAttestationCertificateChain(metadata);

    hasAttestationResult = true;
    toggleSection(attestationResultPanel, true);
    refreshEvaluationResultVisibility();
  }

  function handleAttestationGenerationError(error) {
    verboseApplyError(error);
    var status = resolveErrorStatus(error);
    var reason = readReasonCode(error) || 'generation_failed';
    var message = readErrorMessage(error) || 'Attestation generation failed.';
    setStatusBadge(attestationStatusBadge, status);
    showAttestationError(message, reason);
    hasAttestationResult = true;
    refreshEvaluationResultVisibility();
    if (attestationResultJson) {
      attestationResultJson.textContent = 'Awaiting submission.';
    }
  }

  function buildAttestationJson(generated, responsePayload) {
    var payload = {
      type: generated.type || 'public-key',
      id: generated.id || '',
      rawId: generated.rawId || '',
      response: {
        clientDataJSON: responsePayload.clientDataJSON || '',
        attestationObject: responsePayload.attestationObject || '',
      },
    };
    try {
      return JSON.stringify(payload, null, 2);
    } catch (error) {
      return 'Unable to render attestation JSON';
    }
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
    payload = verboseAttach(payload);
    verboseBeginRequest();
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
    payload = verboseAttach(payload);
    verboseBeginRequest();
    sendJsonRequest(endpoint, payload, csrfToken(replayInlineForm))
        .then(handleInlineReplaySuccess)
        .catch(handleInlineReplayError)
        .finally(function () {
          replayInlineButton.removeAttribute('disabled');
        });
  }

  function submitReplayAttestation() {
    if (!replayAttestationForm || !replayAttestationSubmitButton) {
      return;
    }
    var endpoint = replayAttestationForm.getAttribute('data-attestation-replay-endpoint');
    if (!endpoint) {
      handleReplayAttestationError(
          { status: 0, payload: { reasonCode: 'endpoint_unavailable', message: 'Attestation replay endpoint unavailable.' } });
      return;
    }
    replayAttestationSubmitButton.setAttribute('disabled', 'disabled');
    pendingReplayAttestationResult();
    var payload = {
      attestationId: elementValue(replayAttestationIdField) || undefined,
      format: elementValue(replayAttestationFormatSelect),
      relyingPartyId: elementValue(replayAttestationRpInput),
      origin: elementValue(replayAttestationOriginInput),
      expectedChallenge: elementValue(replayAttestationChallengeField),
      clientDataJson: elementValue(replayAttestationClientDataField),
      attestationObject: elementValue(replayAttestationObjectField),
      trustAnchors: normaliseTrustAnchors(elementValue(replayAttestationTrustAnchorsField)),
    };
    if (!payload.attestationId) {
      delete payload.attestationId;
    }
    payload = verboseAttach(payload);
    verboseBeginRequest();
    sendJsonRequest(endpoint, payload, csrfToken(replayAttestationForm))
        .then(handleReplayAttestationSuccess)
        .catch(handleReplayAttestationError)
        .finally(function () {
          replayAttestationSubmitButton.removeAttribute('disabled');
        });
  }

  function handleStoredEvaluationSuccess(response) {
    if (!response || typeof response !== 'object') {
      verboseApplyResponse(null, 'success');
    } else {
      verboseApplyResponse(response, 'success');
    }
    setStoredAssertionText(formatAssertionJson(response && response.assertion));
    hasStoredEvaluationResult = true;
    refreshEvaluationResultVisibility();
  }

  function handleStoredEvaluationError(error) {
    verboseApplyError(error);
    var message = formatEvaluationErrorMessage(error, 'Stored generation');
    setStoredAssertionText(sanitizeMessage(message));
    hasStoredEvaluationResult = true;
    refreshEvaluationResultVisibility();
  }

  function handleInlineEvaluationSuccess(response) {
    if (!response || typeof response !== 'object') {
      verboseApplyResponse(null, 'success');
    } else {
      verboseApplyResponse(response, 'success');
    }
    setInlineGeneratedText(formatAssertionJson(response && response.assertion));
    hideInlineError();
    updateInlineTelemetry(
        formatEvaluationTelemetry(response && response.metadata, response && response.status));
    hasInlineEvaluationResult = true;
    refreshEvaluationResultVisibility();
  }

  function handleInlineEvaluationError(error) {
    verboseApplyError(error);
    var telemetryMessage = formatEvaluationErrorMessage(error, 'Inline generation');
    var message = readErrorMessage(error) || telemetryMessage;
    var reason = readReasonCode(error);
    setInlineGeneratedText('Generation failed.');
    showInlineError(message, reason);
    updateInlineTelemetry(telemetryMessage);
    hasInlineEvaluationResult = true;
    refreshEvaluationResultVisibility();
  }

  function handleStoredReplaySuccess(response) {
    var status = resolveReplayStatus(response && response.status, response && response.match);
    var reason = resolveReason(response && response.reasonCode, status);
    if (!response || typeof response !== 'object') {
      verboseApplyResponse(null, 'info');
    } else {
      var statusLower = (status || '').toLowerCase();
      var variant = statusLower === 'match' || statusLower === 'success' ? 'success' : 'error';
      verboseApplyResponse(response, variant);
    }
    setStatusBadge(replayStatusBadge, status);
    setStatusText(replayOutcome, status, '—');
    setStatusText(replayReason, reason, '—');
    hasStoredReplayResult = true;
    refreshReplayResultVisibility();
  }

  function handleStoredReplayError(error) {
    verboseApplyError(error);
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
    if (!response || typeof response !== 'object') {
      verboseApplyResponse(null, 'info');
    } else {
      var statusLower = (status || '').toLowerCase();
      var variant = statusLower === 'match' || statusLower === 'success' ? 'success' : 'error';
      verboseApplyResponse(response, variant);
    }
    setStatusBadge(replayInlineStatusBadge, status);
    setStatusText(replayInlineOutcome, status, '—');
    setStatusText(replayInlineReason, reason, '—');
    hasInlineReplayResult = true;
    refreshReplayResultVisibility();
  }

  function handleInlineReplayError(error) {
    verboseApplyError(error);
    var status = resolveReplayErrorStatus(error);
    var reason = resolveReason(readReasonCode(error), status);
    setStatusBadge(replayInlineStatusBadge, status);
    setStatusText(replayInlineOutcome, status, '—');
    setStatusText(replayInlineReason, reason, '—');
    hasInlineReplayResult = true;
    refreshReplayResultVisibility();
  }

  function handleReplayAttestationSuccess(response) {
    var status = response && response.status ? response.status : 'success';
    if (!response || typeof response !== 'object') {
      verboseApplyResponse(null, 'info');
    } else {
      var variant = String(status).toLowerCase() === 'success' ? 'success' : 'error';
      verboseApplyResponse(response, variant);
    }
    hideReplayAttestationError();
    setStatusBadge(replayAttestationStatusBadge, status);
    var metadata = response && response.metadata ? response.metadata : null;
    var reason = metadataValue(metadata, 'reasonCode');
    if (!reason) {
      reason = status === 'success' ? 'match' : 'unknown';
    }
    setStatusText(replayAttestationReason, reason, '—');
    setStatusText(replayAttestationOutcome, status, '—');

    var anchorSource = metadataValue(metadata, 'anchorSource');
    var selfAttested = metadata && Boolean(metadata.selfAttestedFallback);
    if (!anchorSource && selfAttested) {
      anchorSource = 'self-attested';
    }
    if (!anchorSource && metadata && metadata.anchorProvided === false) {
      anchorSource = 'none';
    }
    if (!anchorSource) {
      anchorSource = status === 'success' ? 'provided' : 'unknown';
    }
    if (anchorSource) {
      anchorSource = anchorSource.replace(/_/g, ' ');
    }
    if (metadata && metadata.anchorTrusted) {
      anchorSource = anchorSource + ' (trusted)';
    }
    var anchorTrustedText = 'Not trusted';
    if (selfAttested) {
      anchorTrustedText = 'Self-attested';
    } else if (metadata && metadata.anchorTrusted) {
      anchorTrustedText = 'Trusted';
    }
    setStatusText(replayAttestationAnchorSource, anchorSource, '—');
    setStatusText(replayAttestationAnchorTrusted, anchorTrustedText, '—');

    hasReplayAttestationResult = true;
    refreshReplayResultVisibility();
  }

  function handleReplayAttestationError(error) {
    verboseApplyError(error);
    var message = readErrorMessage(error) || 'Attestation replay failed.';
    var reason = readReasonCode(error) || 'attestation_failed';
    setStatusBadge(replayAttestationStatusBadge, 'error');
    setStatusText(replayAttestationReason, reason, '—');
    setStatusText(replayAttestationOutcome, 'error', '—');
    setStatusText(replayAttestationAnchorSource, '—', '—');
    setStatusText(replayAttestationAnchorTrusted, '—', '—');
    showReplayAttestationError(message, reason);
    hasReplayAttestationResult = true;
    refreshReplayResultVisibility();
  }

  function pendingReplayAttestationResult() {
    hasReplayAttestationResult = false;
    setStatusBadge(replayAttestationStatusBadge, 'pending');
    setStatusText(replayAttestationReason, '—', '—');
    setStatusText(replayAttestationOutcome, '—', '—');
    setStatusText(replayAttestationAnchorSource, '—', '—');
    setStatusText(replayAttestationAnchorTrusted, '—', '—');
    hideReplayAttestationError();
    refreshReplayResultVisibility();
  }

  function showReplayAttestationError(message, reason) {
    if (!replayAttestationResultPanel) {
      return;
    }
    var normalizedMessage = sanitizeMessage(message || 'Attestation replay failed.');
    var options = {};
    if (reason && String(reason).trim().length > 0) {
      options.hint = 'Reason: ' + String(reason).trim();
    }
    if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
      global.ResultCard.showMessage(replayAttestationResultPanel, normalizedMessage, 'error', options);
    } else {
      var messageNode = replayAttestationResultPanel.querySelector('[data-result-message]');
      if (messageNode) {
        messageNode.textContent = normalizedMessage;
        messageNode.removeAttribute('hidden');
        messageNode.setAttribute('aria-hidden', 'false');
      }
      var hintNode = replayAttestationResultPanel.querySelector('[data-result-hint]');
      if (hintNode) {
        if (options.hint) {
          hintNode.textContent = options.hint;
          hintNode.removeAttribute('hidden');
          hintNode.setAttribute('aria-hidden', 'false');
        } else {
          hintNode.textContent = '';
          hintNode.setAttribute('hidden', 'hidden');
          hintNode.setAttribute('aria-hidden', 'true');
        }
      }
    }
  }

  function hideReplayAttestationError() {
    if (!replayAttestationResultPanel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.resetMessage === 'function') {
      global.ResultCard.resetMessage(replayAttestationResultPanel);
    } else {
      var messageNode = replayAttestationResultPanel.querySelector('[data-result-message]');
      if (messageNode) {
        messageNode.textContent = '';
        messageNode.setAttribute('hidden', 'hidden');
        messageNode.setAttribute('aria-hidden', 'true');
      }
      var hintNode = replayAttestationResultPanel.querySelector('[data-result-hint]');
      if (hintNode) {
        hintNode.textContent = '';
        hintNode.setAttribute('hidden', 'hidden');
        hintNode.setAttribute('aria-hidden', 'true');
      }
    }
  }

  function dispatchTabChange(tab, options) {
    if (options && options.broadcast === false) {
      return;
    }
    if (lastBroadcastTab === tab && !(options && options.force)) {
      return;
    }
    lastBroadcastTab = tab;
    try {
      var detail = { tab: tab };
      if (options && options.replace === true) {
        detail.replace = true;
      }
      global.dispatchEvent(
          new global.CustomEvent('operator:fido2-tab-changed', { detail: detail }));
    } catch (error) {
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to broadcast FIDO2 tab change', error);
      }
    }
  }

  function dispatchEvaluateModeChange(mode, options) {
    if (options && options.broadcast === false) {
      return;
    }
    if (lastBroadcastEvaluateMode === mode && !(options && options.force)) {
      return;
    }
    lastBroadcastEvaluateMode = mode;
    try {
      var detail = { mode: mode };
      if (options && options.replace === true) {
        detail.replace = true;
      }
      global.dispatchEvent(
          new global.CustomEvent('operator:fido2-evaluate-mode-changed', { detail: detail }));
    } catch (error) {
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to broadcast FIDO2 evaluate mode change', error);
      }
    }
  }

  function dispatchReplayModeChange(mode, options) {
    if (options && options.broadcast === false) {
      return;
    }
    if (lastBroadcastReplayMode === mode && !(options && options.force)) {
      return;
    }
    lastBroadcastReplayMode = mode;
    try {
      var detail = { mode: mode };
      if (options && options.replace === true) {
        detail.replace = true;
      }
      global.dispatchEvent(
          new global.CustomEvent('operator:fido2-replay-mode-changed', { detail: detail }));
    } catch (error) {
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to broadcast FIDO2 replay mode change', error);
      }
    }
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
    clearVerboseTrace();
    if (tab === TAB_EVALUATE) {
      setEvaluateMode(currentEvaluateMode, mergeOptions(options, { broadcast: false, force: true }));
    } else {
      setReplayMode(currentReplayMode, mergeOptions(options, { broadcast: false, force: true }));
    }
    dispatchTabChange(tab, options);
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
    if (currentCeremony === CEREMONY_ASSERTION) {
      lastAssertionEvaluateMode = mode;
    }
    if (evaluateModeToggle) {
      evaluateModeToggle.setAttribute('data-mode', mode);
    }
    if (evaluateStoredRadio) {
      evaluateStoredRadio.checked = mode === MODE_STORED;
    }
    if (evaluateInlineRadio) {
      evaluateInlineRadio.checked = mode === MODE_INLINE;
    }
    if (currentCeremony === CEREMONY_ATTESTATION) {
      toggleSection(evaluateStoredSection, false);
      toggleSection(evaluateInlineSection, false);
    } else {
      toggleSection(evaluateStoredSection, mode === MODE_STORED);
      toggleSection(evaluateInlineSection, mode === MODE_INLINE);
    }
    refreshEvaluationResultVisibility();
    toggleSeedActions();
    toggleAttestationSeedActions();
    if (mode === MODE_INLINE) {
      refreshInlineCounterAfterPreset();
    }
    updateEvaluateButtonCopy();
    clearVerboseTrace();
    dispatchEvaluateModeChange(mode, options);
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
    if (currentReplayCeremony === CEREMONY_ASSERTION) {
      lastReplayAssertionMode = mode;
    }
    if (replayModeToggle) {
      replayModeToggle.setAttribute('data-mode', mode);
    }
    if (replayStoredRadio) {
      replayStoredRadio.checked = mode === MODE_STORED;
    }
    if (replayInlineRadio) {
      replayInlineRadio.checked = mode === MODE_INLINE;
    }
    if (currentReplayCeremony === CEREMONY_ATTESTATION) {
      toggleSection(replayStoredSection, false);
      toggleSection(replayInlineSection, false);
    } else {
      toggleSection(replayStoredSection, mode === MODE_STORED);
      toggleSection(replayInlineSection, mode === MODE_INLINE);
    }
    refreshReplayResultVisibility();
    updateReplayButtonCopy();
    clearVerboseTrace();
    dispatchReplayModeChange(mode, options);
  }

  function setAttestationMode(mode, options) {
    if (mode !== MODE_STORED && mode !== MODE_INLINE) {
      mode = MODE_INLINE;
    }
    if (!options || !options.force) {
      if (currentAttestationMode === mode) {
        return;
      }
    }
    currentAttestationMode = mode;
    if (attestationModeToggle) {
      attestationModeToggle.setAttribute('data-mode', mode);
    }
    if (attestationInlineOption) {
      attestationInlineOption.checked = mode === MODE_INLINE;
    }
    if (attestationStoredOption) {
      attestationStoredOption.checked = mode === MODE_STORED;
    }
    toggleSection(attestationInlineSection, mode === MODE_INLINE);
    toggleSection(attestationStoredSection, mode === MODE_STORED);
    toggleAttestationInlineInputs(mode === MODE_INLINE);
    toggleAttestationSeedActions();
    if (mode === MODE_STORED) {
      applyAttestationStoredSelection(activeStoredCredentialId || null);
    }
    clearVerboseTrace();
  }

  function setReplayCeremony(ceremony, options) {
    if (ceremony !== CEREMONY_ASSERTION && ceremony !== CEREMONY_ATTESTATION) {
      return;
    }
    if (!options || !options.force) {
      if (currentReplayCeremony === ceremony) {
        return;
      }
    }
    currentReplayCeremony = ceremony;
    if (replayCeremonyToggle) {
      replayCeremonyToggle.setAttribute('data-mode', ceremony);
    }
    updateReplayCeremonyButtons(ceremony);
    updateReplayHeading(ceremony);
    if (ceremony === CEREMONY_ATTESTATION) {
      lockReplayModeForAttestationReplay();
      pendingReplayAttestationResult();
    } else {
      unlockReplayModeForAttestationReplay();
    }
    toggleNodeList(replayAssertionViews, ceremony === CEREMONY_ASSERTION);
    toggleNodeList(replayAttestationViews, ceremony === CEREMONY_ATTESTATION);
    refreshReplayResultVisibility();
    clearVerboseTrace();
    if (!options || options.broadcast !== false) {
      dispatchReplayModeChange(currentReplayMode, options);
    }
  }

  function setCeremony(ceremony, options) {
    if (ceremony !== CEREMONY_ASSERTION && ceremony !== CEREMONY_ATTESTATION) {
      return;
    }
    if (!options || !options.force) {
      if (currentCeremony === ceremony) {
        return;
      }
    }
    currentCeremony = ceremony;
    if (ceremonyToggle) {
      ceremonyToggle.setAttribute('data-mode', ceremony);
    }
    updateCeremonyButtons(ceremony);
    if (ceremony === CEREMONY_ATTESTATION) {
      lastAssertionEvaluateMode = currentEvaluateMode;
      lockEvaluateModeForAttestation();
      pendingAttestationResult();
      setAttestationMode(currentAttestationMode, { broadcast: false, force: true });
    } else {
      unlockEvaluateModeForAttestation();
    }
    toggleNodeList(assertionViews, ceremony === CEREMONY_ASSERTION);
    toggleNodeList(attestationViews, ceremony === CEREMONY_ATTESTATION);
    updateEvaluateHeading(ceremony);
    refreshEvaluationResultVisibility();
    toggleSeedActions();
    toggleAttestationSeedActions();
    clearVerboseTrace();
    if (!options || options.broadcast !== false) {
      dispatchEvaluateModeChange(currentEvaluateMode, options);
    }
  }

  function updateReplayHeading(ceremony) {
    if (!replayHeading) {
      return;
    }
    if (ceremony === CEREMONY_ATTESTATION) {
      replayHeading.textContent = 'Replay a WebAuthn attestation';
    } else {
      replayHeading.textContent = 'Replay a WebAuthn assertion';
    }
  }

  function toggleTabButton(button, active) {
    if (!button) {
      return;
    }
    button.classList.toggle('mode-pill--active', active);
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  }

  function updateEvaluateHeading(ceremony) {
    if (!evaluateHeading) {
      return;
    }
    if (ceremony === CEREMONY_ATTESTATION) {
      evaluateHeading.textContent = 'Generate a WebAuthn attestation';
    } else {
      evaluateHeading.textContent = 'Evaluate a WebAuthn assertion';
    }
  }

  function updateCeremonyButtons(ceremony) {
    toggleCeremonyButton(ceremonyAssertionButton, ceremony === CEREMONY_ASSERTION);
    toggleCeremonyButton(ceremonyAttestationButton, ceremony === CEREMONY_ATTESTATION);
  }

  function updateReplayCeremonyButtons(ceremony) {
    toggleCeremonyButton(replayCeremonyAssertionButton, ceremony === CEREMONY_ASSERTION);
    toggleCeremonyButton(replayCeremonyAttestationButton, ceremony === CEREMONY_ATTESTATION);
  }

  function toggleCeremonyButton(button, active) {
    if (!button) {
      return;
    }
    button.classList.toggle('mode-pill--active', active);
    button.setAttribute('aria-pressed', active ? 'true' : 'false');
  }

  function toggleNodeList(nodeList, visible) {
    if (!nodeList) {
      return;
    }
    for (var index = 0; index < nodeList.length; index += 1) {
      toggleSection(nodeList[index], visible);
    }
  }

  function lockEvaluateModeForAttestation() {
    setEvaluateMode(MODE_INLINE, { broadcast: false, force: true });
    if (evaluateModeToggle) {
      evaluateModeToggle.setAttribute('data-locked', 'true');
    }
    setModeOptionVisibility(evaluateStoredModeOption, false);
    setModeOptionVisibility(evaluateInlineModeOption, true);
    if (evaluateStoredRadio) {
      evaluateStoredRadio.setAttribute('aria-hidden', 'true');
      disableRadioInput(evaluateStoredRadio, true);
      evaluateStoredRadio.checked = false;
    }
    if (evaluateInlineRadio) {
      evaluateInlineRadio.removeAttribute('aria-hidden');
      disableRadioInput(evaluateInlineRadio, false);
      evaluateInlineRadio.checked = true;
    }
  }

  function unlockEvaluateModeForAttestation() {
    if (evaluateModeToggle) {
      evaluateModeToggle.removeAttribute('data-locked');
    }
    setModeOptionVisibility(evaluateStoredModeOption, true);
    setModeOptionVisibility(evaluateInlineModeOption, true);
    if (evaluateStoredRadio) {
      evaluateStoredRadio.removeAttribute('aria-hidden');
      disableRadioInput(evaluateStoredRadio, false);
    }
    if (evaluateInlineRadio) {
      evaluateInlineRadio.removeAttribute('aria-hidden');
      disableRadioInput(evaluateInlineRadio, false);
    }
    setEvaluateMode(lastAssertionEvaluateMode, { broadcast: false, force: true });
  }

  function lockReplayModeForAttestationReplay() {
    setReplayMode(MODE_INLINE, { broadcast: false, force: true });
    if (replayModeToggle) {
      replayModeToggle.setAttribute('data-locked', 'true');
    }
    setModeOptionVisibility(replayStoredModeOption, false);
    setModeOptionVisibility(replayInlineModeOption, true);
    if (replayStoredRadio) {
      replayStoredRadio.setAttribute('aria-hidden', 'true');
      disableRadioInput(replayStoredRadio, true);
      replayStoredRadio.checked = false;
    }
    if (replayInlineRadio) {
      replayInlineRadio.removeAttribute('aria-hidden');
      disableRadioInput(replayInlineRadio, false);
      replayInlineRadio.checked = true;
    }
  }

  function unlockReplayModeForAttestationReplay() {
    if (replayModeToggle) {
      replayModeToggle.removeAttribute('data-locked');
    }
    setModeOptionVisibility(replayStoredModeOption, true);
    setModeOptionVisibility(replayInlineModeOption, true);
    if (replayStoredRadio) {
      replayStoredRadio.removeAttribute('aria-hidden');
      disableRadioInput(replayStoredRadio, false);
    }
    if (replayInlineRadio) {
      replayInlineRadio.removeAttribute('aria-hidden');
      disableRadioInput(replayInlineRadio, false);
    }
    setReplayMode(lastReplayAssertionMode, { broadcast: false, force: true });
  }

  function setModeOptionVisibility(optionElement, visible) {
    if (!optionElement) {
      return;
    }
    if (visible) {
      optionElement.removeAttribute('hidden');
      optionElement.setAttribute('aria-hidden', 'false');
    } else {
      optionElement.setAttribute('hidden', 'hidden');
      optionElement.setAttribute('aria-hidden', 'true');
    }
  }

  function disableRadioInput(input, disabled) {
    if (!input) {
      return;
    }
    if (disabled) {
      input.disabled = true;
      input.setAttribute('disabled', 'disabled');
    } else {
      input.disabled = false;
      input.removeAttribute('disabled');
    }
  }

  function toggleSection(section, visible) {
    if (!section) {
      return;
    }
    if (visible) {
      section.removeAttribute('hidden');
      section.removeAttribute('aria-hidden');
      section.style.display = '';
    } else {
      section.setAttribute('hidden', 'hidden');
      section.setAttribute('aria-hidden', 'true');
      section.style.display = 'none';
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
    applyButtonLabel(
        attestationSubmitButton, 'data-attestation-label', 'Generate attestation');
  }

  function updateReplayButtonCopy() {
    applyButtonLabel(
        replayInlineButton, 'data-inline-label', 'Replay inline assertion');
    applyButtonLabel(
        replayStoredButton, 'data-stored-label', 'Replay stored assertion');
    applyButtonLabel(
        replayAttestationSubmitButton, 'data-attestation-label', 'Replay attestation');
  }

  function refreshEvaluationResultVisibility() {
    if (currentCeremony === CEREMONY_ATTESTATION) {
      toggleSection(storedResultPanel, false);
      toggleSection(inlineResultPanel, false);
      toggleSection(attestationResultPanel, hasAttestationResult);
      return;
    }
    toggleSection(attestationResultPanel, false);
    toggleSection(
        storedResultPanel,
        currentEvaluateMode === MODE_STORED && hasStoredEvaluationResult);
    toggleSection(
        inlineResultPanel,
        currentEvaluateMode === MODE_INLINE && hasInlineEvaluationResult);
  }

  function refreshReplayResultVisibility() {
    if (currentReplayCeremony === CEREMONY_ATTESTATION) {
      toggleSection(replayStoredResultPanel, false);
      toggleSection(replayInlineResultPanel, false);
      toggleSection(replayAttestationResultPanel, hasReplayAttestationResult);
      return;
    }
    toggleSection(replayAttestationResultPanel, false);
    toggleSection(
        replayStoredResultPanel,
        currentReplayMode === MODE_STORED && hasStoredReplayResult);
    toggleSection(
        replayInlineResultPanel,
        currentReplayMode === MODE_INLINE && hasInlineReplayResult);
  }

  function refreshStoredCredentials() {
    if (!storedForm) {
      return Promise.resolve([]);
    }
    var endpoint = storedForm.getAttribute('data-credentials-endpoint');
    return fetchCredentials(endpoint).then(function (credentials) {
      updateCredentialSelect(storedCredentialSelect, credentials);
      updateCredentialSelect(replayCredentialSelect, credentials);
      updateCredentialSelect(attestationStoredCredentialSelect, credentials);
      toggleSeedActions();
      toggleAttestationSeedActions();
      var nextActive = activeStoredCredentialId;
      if (
        nextActive
        && (!storedCredentialSelect
            || !elementHasOption(storedCredentialSelect, nextActive))
        && (!replayCredentialSelect
            || !elementHasOption(replayCredentialSelect, nextActive))
        && (!attestationStoredCredentialSelect
            || !elementHasOption(attestationStoredCredentialSelect, nextActive))) {
        nextActive = '';
      }
      setActiveStoredCredential(nextActive, { force: true });
      return credentials;
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
          || !elementHasOption(replayCredentialSelect, normalized))
      && (!attestationStoredCredentialSelect
          || !elementHasOption(attestationStoredCredentialSelect, normalized))) {
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
    syncStoredSelectValue(attestationStoredCredentialSelect, normalized);
    suppressStoredCredentialSync = false;
    if (shouldSync) {
      applyStoredSample(normalized || null);
      applyStoredReplaySamples(normalized || null);
    }
    applyAttestationStoredSelection(normalized || null);
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

  function applyAttestationSample(vectorId) {
    if (!attestationVectorIndex) {
      return;
    }
    var vector = vectorId ? attestationVectorIndex[vectorId] : null;
    if (!vector) {
      clearAttestationFields();
      pendingAttestationResult();
      return;
    }
    setValue(attestationIdInput, vector.vectorId || '');
    setValue(attestationFormatSelect, vector.format || 'packed');
    setValue(attestationRpInput, vector.relyingPartyId || 'example.org');
    setValue(attestationOriginInput, vector.origin || 'https://example.org');
    setValue(attestationChallengeField, vector.challengeBase64Url || '');
    setValue(attestationCredentialKeyField, vector.credentialPrivateKey || '');
    setValue(attestationPrivateKeyField, vector.attestationPrivateKey || '');
    setValue(attestationSerialField, vector.attestationCertificateSerial || '');
    if (attestationSigningModeSelect) {
      attestationSigningModeSelect.value = 'SELF_SIGNED';
    }
    if (attestationCustomRootField) {
      attestationCustomRootField.value = '';
    }
    if (attestationSampleSelect && vector.vectorId) {
      attestationSampleSelect.value = vector.vectorId;
    }
    pendingAttestationResult();
  }

  function clearAttestationFields() {
    setValue(attestationIdInput, '');
    setValue(attestationFormatSelect, 'packed');
    setValue(attestationRpInput, 'example.org');
    setValue(attestationOriginInput, 'https://example.org');
    setValue(attestationChallengeField, '');
    setValue(attestationCredentialKeyField, '');
    setValue(attestationPrivateKeyField, '');
    setValue(attestationSerialField, '');
    if (attestationSigningModeSelect) {
      attestationSigningModeSelect.value = 'SELF_SIGNED';
    }
    if (attestationCustomRootField) {
      attestationCustomRootField.value = '';
    }
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

  function pendingAttestationResult() {
    hasAttestationResult = false;
    setStatusBadge(attestationStatusBadge, 'pending');
    if (attestationResultJson) {
      attestationResultJson.textContent = 'Awaiting submission.';
    }
    renderAttestationCertificateChain(null);
    hideAttestationError();
    refreshEvaluationResultVisibility();
    toggleSection(attestationResultPanel, false);
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

  function showInlineError(message, reason) {
    if (!inlineResultPanel) {
      return;
    }
    var normalizedMessage = sanitizeMessage(message || 'Inline generation failed.');
    var options = {};
    if (reason && String(reason).trim().length > 0) {
      options.hint = 'Reason: ' + String(reason).trim();
    }
    if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
      global.ResultCard.showMessage(inlineResultPanel, normalizedMessage, 'error', options);
    } else {
      var messageNode = inlineResultPanel.querySelector('[data-result-message]');
      if (messageNode) {
        messageNode.textContent = normalizedMessage;
        messageNode.removeAttribute('hidden');
        messageNode.setAttribute('aria-hidden', 'false');
      }
      var hintNode = inlineResultPanel.querySelector('[data-result-hint]');
      if (hintNode) {
        if (options.hint) {
          hintNode.textContent = options.hint;
          hintNode.removeAttribute('hidden');
          hintNode.setAttribute('aria-hidden', 'false');
        } else {
          hintNode.textContent = '';
          hintNode.setAttribute('hidden', 'hidden');
          hintNode.setAttribute('aria-hidden', 'true');
        }
      }
    }
  }

  function hideInlineError() {
    if (!inlineResultPanel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.resetMessage === 'function') {
      global.ResultCard.resetMessage(inlineResultPanel);
    } else {
      var messageNode = inlineResultPanel.querySelector('[data-result-message]');
      if (messageNode) {
        messageNode.textContent = '';
        messageNode.setAttribute('hidden', 'hidden');
        messageNode.setAttribute('aria-hidden', 'true');
      }
      var hintNode = inlineResultPanel.querySelector('[data-result-hint]');
      if (hintNode) {
        hintNode.textContent = '';
        hintNode.setAttribute('hidden', 'hidden');
        hintNode.setAttribute('aria-hidden', 'true');
      }
    }
  }

  function showAttestationError(message, reason) {
    if (!attestationResultPanel) {
      return;
    }
    var normalizedMessage = sanitizeMessage(message || 'Attestation generation failed.');
    var options = {};
    if (reason && String(reason).trim().length > 0) {
      options.hint = 'Reason: ' + String(reason).trim();
    }
    if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
      global.ResultCard.showMessage(attestationResultPanel, normalizedMessage, 'error', options);
    } else {
      var messageNode = attestationResultPanel.querySelector('[data-result-message]');
      if (messageNode) {
        messageNode.textContent = normalizedMessage;
        messageNode.removeAttribute('hidden');
        messageNode.setAttribute('aria-hidden', 'false');
      }
      var hintNode = attestationResultPanel.querySelector('[data-result-hint]');
      if (hintNode) {
        if (options.hint) {
          hintNode.textContent = options.hint;
          hintNode.removeAttribute('hidden');
          hintNode.setAttribute('aria-hidden', 'false');
        } else {
          hintNode.textContent = '';
          hintNode.setAttribute('hidden', 'hidden');
          hintNode.setAttribute('aria-hidden', 'true');
        }
      }
    }
  }

  function hideAttestationError() {
    if (!attestationResultPanel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.resetMessage === 'function') {
      global.ResultCard.resetMessage(attestationResultPanel);
    } else {
      var messageNode = attestationResultPanel.querySelector('[data-result-message]');
      if (messageNode) {
        messageNode.textContent = '';
        messageNode.setAttribute('hidden', 'hidden');
        messageNode.setAttribute('aria-hidden', 'true');
      }
      var hintNode = attestationResultPanel.querySelector('[data-result-hint]');
      if (hintNode) {
        hintNode.textContent = '';
        hintNode.setAttribute('hidden', 'hidden');
        hintNode.setAttribute('aria-hidden', 'true');
      }
    }
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

  function renderAttestationCertificateChain(metadata) {
    var certificates =
        metadata && Array.isArray(metadata.certificateChainPem)
            ? metadata.certificateChainPem
            : [];
    var certificateCount =
        metadata && typeof metadata.certificateChainCount === 'number'
            ? metadata.certificateChainCount
            : certificates.length;
    if (!attestationCertificateSection || !attestationCertificateChain) {
      return;
    }
    if (!certificates.length) {
      attestationCertificateChain.textContent = '';
      if (attestationCertificateHeading) {
        attestationCertificateHeading.textContent = 'Certificate chain';
      }
      attestationCertificateSection.setAttribute('hidden', 'hidden');
      attestationCertificateSection.setAttribute('aria-hidden', 'true');
      return;
    }
    attestationCertificateChain.textContent = certificates.join('\\n\\n');
    if (attestationCertificateHeading) {
      attestationCertificateHeading.textContent =
          'Certificate chain (' + certificateCount + ')';
    }
    attestationCertificateSection.removeAttribute('hidden');
    attestationCertificateSection.setAttribute('aria-hidden', 'false');
  }

  function extractCustomRootCertificates(rawValue) {
    if (!rawValue) {
      return [];
    }
    var text = String(rawValue).trim();
    if (!text) {
      return [];
    }
    var matches =
        text.match(/-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/g);
    if (matches && matches.length) {
      return matches.map(function (entry) {
        return entry.trim();
      });
    }
    return [text];
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
      var normalized = String(status).trim();
      if (normalized) {
        return normalized;
      }
    }
    if (match === true || String(match).toLowerCase() === 'true') {
      return 'match';
    }
    if (match === false || String(match).toLowerCase() === 'false') {
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

  function normaliseTrustAnchors(text) {
    if (!text) {
      return [];
    }
    var normalised = text.replace(/\r\n/g, '\n').trim();
    if (!normalised) {
      return [];
    }
    var matches = normalised.match(/-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/g);
    if (matches && matches.length > 0) {
      return matches.map(function (match) {
        return match.trim();
      });
    }
    return [normalised];
  }

  function sendJsonRequest(endpoint, payload, csrf) {
    if (!endpoint) {
      return Promise.reject({ status: 0, payload: null });
    }
    if (typeof global.fetch !== 'function') {
      return Promise.reject({ status: 0, payload: null });
    }
    var requestBody;
    try {
      requestBody = JSON.stringify(payload || {});
    } catch (error) {
      return Promise.reject({ status: 0, payload: null });
    }
    var headers = { 'Content-Type': 'application/json' };
    if (csrf) {
      headers['X-CSRF-TOKEN'] = csrf;
    }
    return global
        .fetch(endpoint, {
          method: 'POST',
          credentials: 'same-origin',
          headers: headers,
          body: requestBody,
        })
        .then(function (response) {
          return response.text().then(function (responseText) {
            if (response.ok) {
              if (!responseText) {
                return {};
              }
              try {
                return JSON.parse(responseText);
              } catch (error) {
                return {};
              }
            }
            var parsed = null;
            if (responseText) {
              try {
                parsed = JSON.parse(responseText);
              } catch (error) {
                parsed = null;
              }
            }
            throw { status: response.status, payload: parsed };
          });
        })
        .catch(function (error) {
          if (error && typeof error.status === 'number') {
            throw error;
          }
          throw { status: 0, payload: null };
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

  function populateAttestationVectorOptions() {
    if (!attestationSampleSelect) {
      return;
    }
    clearSelect(attestationSampleSelect);
    addPlaceholderOption(attestationSampleSelect, 'Select a vector');
    var vectors = Array.isArray(attestationVectors) ? attestationVectors : [];
    for (var index = 0; index < vectors.length; index += 1) {
      var vector = vectors[index];
      if (!vector || !vector.vectorId) {
        continue;
      }
      var option = documentRef.createElement('option');
      option.value = vector.vectorId;
      option.textContent = vector.label || vector.vectorId;
      if (vector.format) {
        option.setAttribute('data-format', vector.format);
      }
      if (vector.algorithm) {
        option.setAttribute('data-algorithm', vector.algorithm);
      }
      attestationSampleSelect.appendChild(option);
    }
    enableSelect(attestationSampleSelect);
    if (vectors.length > 0) {
      attestationSampleSelect.value = vectors[0].vectorId;
    }
  }

  function coerceAlgorithmLabel(value) {
    if (value == null) {
      return '';
    }
    if (typeof value === 'string') {
      return value;
    }
    if (typeof value === 'object') {
      if (typeof value.name === 'string') {
        return value.name;
      }
      if (typeof value.label === 'string') {
        return value.label;
      }
    }
    return String(value);
  }

  function compareCredentialSummaries(a, b) {
    var rankA = algorithmSortKey(coerceAlgorithmLabel(a && a.algorithm));
    var rankB = algorithmSortKey(coerceAlgorithmLabel(b && b.algorithm));
    if (rankA !== rankB) {
      return rankA - rankB;
    }
    var labelA = normalizeLowercase(a && a.label);
    var labelB = normalizeLowercase(b && b.label);
    if (labelA !== labelB) {
      return labelA.localeCompare(labelB);
    }
    var idA = normalizeLowercase(a && a.id);
    var idB = normalizeLowercase(b && b.id);
    return idA.localeCompare(idB);
  }

  function normalizeLowercase(value) {
    if (!value || typeof value !== 'string') {
      return '';
    }
    return value.trim().toLowerCase();
  }

  function algorithmSortKey(algorithm) {
    if (!algorithm || typeof algorithm !== 'string') {
      return ALGORITHM_SEQUENCE.length;
    }
    var normalized = algorithm.trim();
    if (!normalized) {
      return ALGORITHM_SEQUENCE.length;
    }
    var upper = normalized.toUpperCase();
    for (var index = 0; index < ALGORITHM_SEQUENCE.length; index += 1) {
      if (ALGORITHM_SEQUENCE[index].toUpperCase() === upper) {
        return index;
      }
    }
    return ALGORITHM_SEQUENCE.length;
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
    var ordered = Array.isArray(credentials)
            ? credentials.slice().sort(compareCredentialSummaries)
            : [];
    ordered.forEach(function (summary) {
      var option = documentRef.createElement('option');
      option.value = summary.id;
      option.textContent = summary.label || summary.id;
      var algorithmLabel = coerceAlgorithmLabel(summary.algorithm);
      if (algorithmLabel) {
        option.setAttribute('data-algorithm', algorithmLabel);
      }
      select.appendChild(option);
    });
    var hasCredentials = ordered.length > 0;
    select.disabled = !hasCredentials;
    var desired = '';
    if (activeStoredCredentialId && elementHasOption(select, activeStoredCredentialId)) {
      desired = activeStoredCredentialId;
    } else if (previous && elementHasOption(select, previous)) {
      desired = previous;
    }
    select.value = desired;
  }

  function preloadStoredCredentialSelects() {
    if (!Array.isArray(seedDefinitions) || seedDefinitions.length === 0) {
      return;
    }
    var summaries = seedDefinitions.map(function (definition) {
      return {
        id: definition.credentialId,
        label: definition.label || definition.credentialId,
        algorithm: coerceAlgorithmLabel(definition.algorithm),
      };
    });
    populateSelectWithSummaries(attestationStoredCredentialSelect, summaries);
  }

  function populateSelectWithSummaries(select, summaries) {
    if (!select) {
      return;
    }
    var ordered = Array.isArray(summaries)
            ? summaries.slice().sort(compareCredentialSummaries)
            : [];
    clearSelect(select);
    addPlaceholderOption(select, select === attestationStoredCredentialSelect
            ? 'Select a stored credential'
            : 'Select a stored credential');
    ordered.forEach(function (summary) {
      if (!summary || !summary.id) {
        return;
      }
      var option = documentRef.createElement('option');
      option.value = summary.id;
      option.textContent = summary.label || summary.id;
      var summaryAlgorithm = coerceAlgorithmLabel(summary.algorithm);
      if (summaryAlgorithm) {
        option.setAttribute('data-algorithm', summaryAlgorithm);
      }
      select.appendChild(option);
    });
    select.disabled = select.options.length <= 1;
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
    if (!endpoint || typeof global.fetch !== 'function') {
      return Promise.resolve({ ok: false, status: 0, bodyText: '' });
    }
    var requestBody;
    try {
      requestBody = JSON.stringify(payload || {});
    } catch (error) {
      return Promise.resolve({ ok: false, status: 0, bodyText: '' });
    }
    var headers = { 'Content-Type': 'application/json' };
    if (csrf) {
      headers['X-CSRF-TOKEN'] = csrf;
    }
    return global
        .fetch(endpoint, {
          method: 'POST',
          credentials: 'same-origin',
          headers: headers,
          body: requestBody,
        })
        .then(function (response) {
          return response.text().then(function (bodyText) {
            return {
              ok: response.ok,
              status: response.status,
              bodyText: bodyText,
            };
          });
        })
        .catch(function () {
          return { ok: false, status: 0, bodyText: '' };
        });
  }

  function fetchCredentials(endpoint) {
    if (!endpoint) {
      return Promise.resolve([]);
    }
    if (typeof global.fetch !== 'function') {
      return Promise.resolve([]);
    }
    return global
        .fetch(endpoint, {
          method: 'GET',
          credentials: 'same-origin',
          headers: { Accept: 'application/json' },
        })
        .then(function (response) {
          return response.text().then(function (bodyText) {
            if (!response.ok) {
              return [];
            }
            try {
              var payload = JSON.parse(bodyText || '[]');
              return Array.isArray(payload) ? payload : [];
            } catch (error) {
              return [];
            }
          });
        })
        .catch(function () {
          return [];
        });
  }

  function toggleSeedActions() {
    if (!storedSeedActions) {
      return;
    }
    var visible =
        currentCeremony === CEREMONY_ASSERTION && currentEvaluateMode === MODE_STORED;
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

  function toggleAttestationSeedActions() {
    if (!attestationSeedActions) {
      return;
    }
    var visible =
        currentCeremony === CEREMONY_ATTESTATION && currentAttestationMode === MODE_STORED;
    if (visible) {
      attestationSeedActions.removeAttribute('hidden');
      attestationSeedActions.setAttribute('aria-hidden', 'false');
      if (attestationSeedButton) {
        attestationSeedButton.removeAttribute('disabled');
      }
    } else {
      attestationSeedActions.setAttribute('hidden', 'hidden');
      attestationSeedActions.setAttribute('aria-hidden', 'true');
      if (attestationSeedButton) {
        attestationSeedButton.setAttribute('disabled', 'disabled');
      }
      updateAttestationSeedStatus('', null);
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

  function updateAttestationSeedStatus(message, severity) {
    if (!attestationSeedStatus) {
      return;
    }
    attestationSeedStatus.classList.remove('credential-status--error', 'credential-status--warning');
    var text = typeof message === 'string' ? message.trim() : '';
    if (!text) {
      attestationSeedStatus.textContent = '';
      attestationSeedStatus.setAttribute('hidden', 'hidden');
      attestationSeedStatus.setAttribute('aria-hidden', 'true');
      return;
    }
    attestationSeedStatus.textContent = text;
    if (severity === 'error') {
      attestationSeedStatus.classList.add('credential-status--error');
    } else if (severity === 'warning') {
      attestationSeedStatus.classList.add('credential-status--warning');
    }
    attestationSeedStatus.removeAttribute('hidden');
    attestationSeedStatus.removeAttribute('aria-hidden');
  }

  function seedStoredAttestations() {
    if (!attestationStoredForm || !attestationSeedButton) {
      return;
    }
    var endpoint = attestationStoredForm.getAttribute('data-attestation-seed-endpoint');
    if (!endpoint) {
      updateAttestationSeedStatus('Seed endpoint unavailable.', 'error');
      return;
    }
    attestationSeedButton.setAttribute('disabled', 'disabled');
    postJson(endpoint, {}, csrfToken(attestationStoredForm))
        .then(function (response) {
          if (!response.ok) {
            updateAttestationSeedStatus('Unable to seed attestation credentials.', 'error');
            return;
          }
          var payload = parseSeedResponse(response.bodyText);
          var addedCount = resolveAddedCount(payload);
          refreshStoredCredentials().then(function () {
            if (addedCount === 0) {
              updateAttestationSeedStatus(
                  'All stored attestation credentials are already present.', 'warning');
            } else {
              updateAttestationSeedStatus(
                  'Seeded stored attestation credentials (' + addedCount + ').', null);
            }
          });
        })
        .catch(function () {
          updateAttestationSeedStatus('Unable to seed attestation credentials.', 'error');
        })
        .finally(function () {
          attestationSeedButton.removeAttribute('disabled');
        });
  }

  function toggleAttestationInlineInputs(enabled) {
    toggleFieldGroup(attestationCredentialKeyField, enabled);
    toggleFieldGroup(attestationPrivateKeyField, enabled);
    toggleFieldGroup(attestationCustomRootField, enabled);
    toggleFieldGroup(attestationSerialField, enabled);
    if (attestationSigningModeSelect) {
      attestationSigningModeSelect.disabled = !enabled;
    }
  }

  function toggleFieldGroup(field, enabled) {
    if (!field) {
      return;
    }
    var container = field.closest ? field.closest('.field-group') : null;
    if (!container) {
      container = field;
    }
    if (enabled) {
      container.style.display = '';
      container.removeAttribute('hidden');
      field.removeAttribute('hidden');
      field.removeAttribute('disabled');
    } else {
      container.style.display = 'none';
      container.setAttribute('hidden', 'hidden');
      field.setAttribute('hidden', 'hidden');
      field.setAttribute('disabled', 'disabled');
    }
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

  function submitStoredAttestationGeneration() {
    if (!attestationStoredForm || !attestationStoredSubmitButton) {
      return;
    }
    var endpoint = attestationStoredForm.getAttribute('data-attestation-endpoint');
    if (!endpoint) {
      handleAttestationGenerationError({
        status: 0,
        payload: {
          reasonCode: 'endpoint_unavailable',
          message: 'Attestation generation endpoint unavailable.',
        },
      });
      return;
    }
    var credentialId = elementValue(attestationStoredCredentialSelect);
    if (!credentialId) {
      handleAttestationGenerationError({
        status: 422,
        payload: {
          reasonCode: 'credential_id_required',
          message: 'Select a stored attestation credential before generating.',
        },
      });
      return;
    }
    var challenge = elementValue(attestationStoredChallengeField);
    if (!challenge) {
      handleAttestationGenerationError({
        status: 422,
        payload: {
          reasonCode: 'challenge_required',
          message: 'Provide a Base64URL challenge for stored attestation generation.',
        },
      });
      return;
    }
    var format = elementValue(attestationStoredFormatInput);
    if (!format) {
      handleAttestationGenerationError({
        status: 422,
        payload: {
          reasonCode: 'stored_attestation_required',
          message: 'Stored attestation metadata unavailable; seed attestation credentials first.',
        },
      });
      return;
    }
    attestationStoredSubmitButton.setAttribute('disabled', 'disabled');
    pendingAttestationResult();
    var payload = {
      inputSource: 'STORED',
      credentialId: credentialId,
      format: format,
      relyingPartyId: elementValue(attestationStoredRpInput),
      origin: elementValue(attestationStoredOriginInput),
      challenge: challenge,
    };
    payload = verboseAttach(payload);
    verboseBeginRequest();
    sendJsonRequest(endpoint, payload, csrfToken(attestationStoredForm))
        .then(handleAttestationGenerationSuccess)
        .catch(handleAttestationGenerationError)
        .finally(function () {
          attestationStoredSubmitButton.removeAttribute('disabled');
        });
  }

  function fetchAttestationMetadata(endpointTemplate, credentialId) {
    if (!endpointTemplate || !credentialId || typeof global.fetch !== 'function') {
      return Promise.resolve(null);
    }
    var endpoint = endpointTemplate.replace(
        '{credentialId}', encodeURIComponent(credentialId));
    return global
        .fetch(endpoint, {
          method: 'GET',
          credentials: 'same-origin',
          headers: { Accept: 'application/json' },
        })
        .then(function (response) {
          return response.text().then(function (bodyText) {
            if (!response.ok) {
              return null;
            }
            try {
              var payload = JSON.parse(bodyText || '{}');
              return payload && typeof payload === 'object' ? payload : null;
            } catch (error) {
              return null;
            }
          });
        })
        .catch(function () {
          return null;
        });
  }

  function applyAttestationStoredSelection(credentialId) {
    if (!attestationStoredForm) {
      return;
    }
    if (!credentialId) {
      clearAttestationStoredFields();
      return;
    }
    var endpointTemplate =
        attestationStoredForm.getAttribute('data-attestation-metadata-endpoint');
    if (!endpointTemplate) {
      populateAttestationStoredFields(credentialId, null);
      return;
    }
    fetchAttestationMetadata(endpointTemplate, credentialId).then(function (metadata) {
      if (credentialId !== activeStoredCredentialId) {
        return;
      }
      if (!metadata) {
        populateAttestationStoredFields(credentialId, null);
        return;
      }
      populateAttestationStoredFields(credentialId, metadata);
    });
  }

  function clearAttestationStoredFields() {
    setValue(attestationStoredRpInput, '');
    setValue(attestationStoredOriginInput, '');
    setValue(attestationStoredChallengeField, '');
    setValue(attestationStoredFormatInput, '');
  }

  function populateAttestationStoredFields(credentialId, metadata) {
    var vector = null;
    if (credentialId && attestationVectorIndex) {
      vector = attestationVectorIndex[credentialId] || null;
    }
    var relyingPartyId = metadata && metadata.relyingPartyId
        ? metadata.relyingPartyId
        : vector && vector.relyingPartyId ? vector.relyingPartyId : 'example.org';
    var origin = metadata && metadata.origin
        ? metadata.origin
        : vector && vector.origin ? vector.origin : 'https://example.org';
    var challenge = metadata && metadata.challenge
        ? metadata.challenge
        : vector && vector.challengeBase64Url ? vector.challengeBase64Url : '';
    var format = metadata && metadata.format
        ? metadata.format
        : vector && vector.format ? vector.format : '';

    setValue(attestationStoredRpInput, relyingPartyId || '');
    setValue(attestationStoredOriginInput, origin || '');
    setValue(attestationStoredChallengeField, challenge || '');
    setValue(attestationStoredFormatInput, format || '');
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

  function createAttestationVectorIndex(vectors) {
    var index = {};
    if (!Array.isArray(vectors)) {
      return index;
    }
    vectors.forEach(function (vector) {
      if (vector && vector.vectorId) {
        index[vector.vectorId] = vector;
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

  function findModeOption(element) {
    var current = element;
    while (current && current !== panel) {
      if (current.classList && current.classList.contains('mode-option')) {
        return current;
      }
      current = current.parentElement;
    }
    return null;
  }

  function removeNode(node) {
    if (node && node.parentNode) {
      node.parentNode.removeChild(node);
    }
  }

  function readInitialPanelState() {
    var tab = TAB_EVALUATE;
    var evaluateMode = MODE_INLINE;
    var replayMode = MODE_INLINE;
    if (panel) {
      var attr = panel.getAttribute('data-initial-fido2-mode');
      if (attr && typeof attr === 'string') {
        var normalized = attr.trim().toLowerCase();
        if (normalized === 'replay') {
          tab = TAB_REPLAY;
        } else if (normalized === MODE_INLINE || normalized === MODE_STORED) {
          evaluateMode = normalized;
        }
      }
      panel.removeAttribute('data-initial-fido2-mode');
      var replayAttr = panel.getAttribute('data-initial-fido2-replay-mode');
      if (replayAttr && typeof replayAttr === 'string') {
        var normalizedReplay = replayAttr.trim().toLowerCase();
        if (normalizedReplay === MODE_INLINE || normalizedReplay === MODE_STORED) {
          replayMode = normalizedReplay;
        }
      }
      panel.removeAttribute('data-initial-fido2-replay-mode');
    }
    if (tab === TAB_EVALUATE) {
      if (evaluateMode !== MODE_INLINE && evaluateMode !== MODE_STORED) {
        evaluateMode = MODE_INLINE;
      }
    } else {
      if (replayMode !== MODE_INLINE && replayMode !== MODE_STORED) {
        replayMode = MODE_INLINE;
      }
    }
    return {
      tab: tab,
      evaluateMode: evaluateMode,
      replayMode: replayMode,
    };
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
    setCeremony: function (ceremony, options) {
      setCeremony(ceremony, options || {});
    },
    getCeremony: function () {
      return currentCeremony;
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
