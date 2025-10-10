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

  var MODE_STORED = 'stored';
  var MODE_INLINE = 'inline';
  var MODE_REPLAY = 'replay';
  var ALLOWED_MODES = new Set([MODE_STORED, MODE_INLINE, MODE_REPLAY]);

  var modeToggle = panel.querySelector('[data-testid="fido2-mode-toggle"]');
  var storedModeButton = panel.querySelector('[data-testid="fido2-stored-mode-button"]');
  var inlineModeButton = panel.querySelector('[data-testid="fido2-inline-mode-button"]');
  var replayModeButton = panel.querySelector('[data-testid="fido2-replay-mode-button"]');

  var storedSection = panel.querySelector('[data-fido2-section="stored"]');
  var inlineSection = panel.querySelector('[data-fido2-section="inline"]');
  var replaySection = panel.querySelector('[data-fido2-section="replay"]');

  var seedDefinitionsNode = panel.querySelector('#fido2-seed-definitions');
  var inlineVectorsNode = panel.querySelector('#fido2-inline-vectors');

  var storedForm = panel.querySelector('[data-testid="fido2-stored-form"]');
  var storedCredentialSelect = panel.querySelector('#fido2StoredCredentialId');
  var storedSeedActions = panel.querySelector('[data-testid="fido2-seed-actions"]');
  var storedSeedButton = panel.querySelector('[data-testid="fido2-seed-credentials"]');
  var storedSeedStatus = panel.querySelector('[data-testid="fido2-seed-status"]');
  var storedResultStatus = panel.querySelector('[data-testid="result-status"]');
  var storedReason = panel.querySelector('[data-testid="fido2-stored-reason"]');
  var storedTelemetry = panel.querySelector('[data-testid="fido2-stored-telemetry"]');
  var storedRpInput = panel.querySelector('#fido2StoredRpId');
  var storedOriginInput = panel.querySelector('#fido2StoredOrigin');
  var storedTypeInput = panel.querySelector('#fido2StoredType');
  var storedChallengeField = panel.querySelector('#fido2StoredChallenge');
  var storedClientDataField = panel.querySelector('#fido2StoredClientData');
  var storedAuthenticatorDataField = panel.querySelector('#fido2StoredAuthenticatorData');
  var storedSignatureField = panel.querySelector('#fido2StoredSignature');

  var inlineForm = panel.querySelector('[data-testid="fido2-inline-form"]');
  var inlineRpInput = panel.querySelector('#fido2InlineRpId');
  var inlineOriginInput = panel.querySelector('#fido2InlineOrigin');
  var inlineTypeInput = panel.querySelector('#fido2InlineType');
  var inlineCredentialIdField = panel.querySelector('#fido2InlineCredentialId');
  var inlinePublicKeyField = panel.querySelector('#fido2InlinePublicKey');
  var inlineAlgorithmInput = panel.querySelector('#fido2InlineAlgorithm');
  var inlineCounterInput = panel.querySelector('#fido2InlineCounter');
  var inlineUvRequiredSelect = panel.querySelector('#fido2InlineUvRequired');
  var inlineChallengeField = panel.querySelector('#fido2InlineChallenge');
  var inlineClientDataField = panel.querySelector('#fido2InlineClientData');
  var inlineAuthenticatorDataField = panel.querySelector('#fido2InlineAuthenticatorData');
  var inlineSignatureField = panel.querySelector('#fido2InlineSignature');
  var inlineLoadSampleButton = panel.querySelector('[data-testid="fido2-inline-load-sample"]');
  var inlineStatusText = panel.querySelector('[data-testid="fido2-inline-status"]');
  var inlineTelemetry = panel.querySelector('[data-testid="fido2-inline-telemetry"]');

  var replayForm = panel.querySelector('[data-testid="fido2-replay-form"]');
  var replayCredentialSelect = panel.querySelector('#fido2ReplayCredentialId');
  var replayOriginInput = panel.querySelector('#fido2ReplayOrigin');
  var replayChallengeField = panel.querySelector('#fido2ReplayChallenge');
  var replayClientDataField = panel.querySelector('#fido2ReplayClientData');
  var replayAuthenticatorDataField = panel.querySelector('#fido2ReplayAuthenticatorData');
  var replaySignatureField = panel.querySelector('#fido2ReplaySignature');
  var replayStatusText = panel.querySelector('[data-testid="fido2-replay-status"]');
  var replayTelemetry = panel.querySelector('[data-testid="fido2-replay-telemetry"]');

  var seedDefinitions = parseJson(seedDefinitionsNode);
  var inlineVectors = parseJson(inlineVectorsNode);

  removeNode(seedDefinitionsNode);
  removeNode(inlineVectorsNode);

  populateStoredOptions();
  populateReplayOptions();

  var currentMode = MODE_STORED;

  if (storedModeButton) {
    storedModeButton.addEventListener('click', function (event) {
      event.preventDefault();
      setMode(MODE_STORED);
    });
  }
  if (inlineModeButton) {
    inlineModeButton.addEventListener('click', function (event) {
      event.preventDefault();
      setMode(MODE_INLINE);
    });
  }
  if (replayModeButton) {
    replayModeButton.addEventListener('click', function (event) {
      event.preventDefault();
      setMode(MODE_REPLAY);
    });
  }

  if (storedCredentialSelect) {
    storedCredentialSelect.addEventListener('change', function () {
      applyStoredSample(storedCredentialSelect.value);
    });
  }

  if (storedForm) {
    storedForm.addEventListener('submit', function (event) {
      event.preventDefault();
      pendingStoredResult();
    });
  }

  if (storedSeedButton) {
    storedSeedButton.addEventListener('click', function () {
      populateStoredOptions(true);
      populateReplayOptions(true);
      updateSeedStatus('Seeded sample credentials.');
    });
  }

  if (inlineLoadSampleButton) {
    inlineLoadSampleButton.addEventListener('click', function () {
      applyInlineSample();
    });
  }

  if (inlineForm) {
    inlineForm.addEventListener('submit', function (event) {
      event.preventDefault();
      pendingInlineResult();
    });
  }

  if (replayForm) {
    replayForm.addEventListener('submit', function (event) {
      event.preventDefault();
      pendingReplayResult();
    });
  }

  setMode$current(MODE_STORED, { force: true, broadcast: false });
  applyStoredSample(storedCredentialSelect && storedCredentialSelect.value);
  applyInlineSample();

  function setMode$new(mode, options) {
    if (!ALLOWED_MODES.has(mode)) {
      return;
    }
    if (!options || !options.force) {
      if (currentMode === mode) {
        return;
      }
    }
    currentMode = mode;
    if (modeToggle) {
      modeToggle.setAttribute('data-mode', mode);
    }
    toggleModeButton(storedModeButton, mode === MODE_STORED);
    toggleModeButton(inlineModeButton, mode === MODE_INLINE);
    toggleModeButton(replayModeButton, mode === MODE_REPLAY);
    toggleSection(storedSection, mode === MODE_STORED);
    toggleSection(inlineSection, mode === MODE_INLINE);
    toggleSection(replaySection, mode === MODE_REPLAY);
    if (!options || options.broadcast !== false) {
      broadcastModeChange(mode, Boolean(options && options.replace));
    }
  }

  function toggleModeButton(button, active) {
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

  function populateStoredOptions(force) {
    if (!storedCredentialSelect) {
      return;
    }
    var hasOptions = storedCredentialSelect.options.length > 1;
    if (hasOptions && !force) {
      return;
    }
    clearSelect(storedCredentialSelect);
    addPlaceholderOption(storedCredentialSelect, 'Select a stored credential');
    seedDefinitions.forEach(function (definition) {
      var option = documentRef.createElement('option');
      option.value = definition.credentialId;
      option.textContent = definition.label || definition.credentialId;
      storedCredentialSelect.appendChild(option);
    });
    storedCredentialSelect.disabled = storedCredentialSelect.options.length <= 1;
  if (storedSeedActions) {
    var shouldShow = storedCredentialSelect.options.length <= 1;
    if (shouldShow) {
      storedSeedActions.removeAttribute('hidden');
      storedSeedActions.setAttribute('aria-hidden', 'false');
    } else {
      storedSeedActions.setAttribute('hidden', 'hidden');
      storedSeedActions.setAttribute('aria-hidden', 'true');
    }
  }
  }

  function populateReplayOptions(force) {
    if (!replayCredentialSelect) {
      return;
    }
    var hasOptions = replayCredentialSelect.options.length > 1;
    if (hasOptions && !force) {
      return;
    }
    clearSelect(replayCredentialSelect);
    addPlaceholderOption(replayCredentialSelect, 'Select a stored credential');
    seedDefinitions.forEach(function (definition) {
      var option = documentRef.createElement('option');
      option.value = definition.credentialId;
      option.textContent = definition.label || definition.credentialId;
      replayCredentialSelect.appendChild(option);
    });
    replayCredentialSelect.disabled = replayCredentialSelect.options.length <= 1;
  }

  function applyStoredSample(credentialId) {
    if (!credentialId) {
      return;
    }
    var definition = null;
    for (var index = 0; index < seedDefinitions.length; index += 1) {
      if (seedDefinitions[index].credentialId === credentialId) {
        definition = seedDefinitions[index];
        break;
      }
    }
    if (!definition) {
      return;
    }
    setValue(storedRpInput, definition.relyingPartyId || 'example.org');
    setValue(storedOriginInput, 'https://example.org');
    setValue(storedTypeInput, 'webauthn.get');
    setValue(storedChallengeField, inlineVectors.length ? inlineVectors[0].expectedChallengeBase64Url : '');
    setValue(storedClientDataField, inlineVectors.length ? inlineVectors[0].clientDataBase64Url : '');
    setValue(
        storedAuthenticatorDataField,
        inlineVectors.length ? inlineVectors[0].authenticatorDataBase64Url : '');
    setValue(storedSignatureField, inlineVectors.length ? inlineVectors[0].signatureBase64Url : '');
    updateStoredTelemetry('Telemetry ready (sanitized).');
  }

  function applyInlineSample() {
    if (!inlineVectors.length) {
      return;
    }
    var vector = inlineVectors[0];
    setValue(inlineRpInput, vector.relyingPartyId || 'example.org');
    setValue(inlineOriginInput, vector.origin || 'https://example.org');
    setValue(inlineTypeInput, vector.expectedType || 'webauthn.get');
    setValue(inlineCredentialIdField, vector.credentialIdBase64Url || '');
    setValue(inlinePublicKeyField, vector.publicKeyCoseBase64Url || '');
    setValue(inlineAlgorithmInput, vector.algorithm || 'ES256');
    setValue(inlineCounterInput, vector.signatureCounter != null ? vector.signatureCounter : 0);
    if (inlineUvRequiredSelect) {
      inlineUvRequiredSelect.value = vector.userVerificationRequired ? 'true' : 'false';
    }
    setValue(inlineChallengeField, vector.expectedChallengeBase64Url || '');
    setValue(inlineClientDataField, vector.clientDataBase64Url || '');
    setValue(inlineAuthenticatorDataField, vector.authenticatorDataBase64Url || '');
    setValue(inlineSignatureField, vector.signatureBase64Url || '');
    updateInlineTelemetry('Sample vector loaded (sanitized).');
  }

  function pendingStoredResult() {
    if (storedResultStatus) {
      storedResultStatus.textContent = 'pending';
    }
    if (storedReason) {
      storedReason.textContent = 'awaiting verification';
    }
    updateStoredTelemetry('Awaiting verification (sanitized).');
  }

  function pendingInlineResult() {
    if (inlineStatusText) {
      inlineStatusText.textContent = 'pending';
    }
    updateInlineTelemetry('Awaiting verification (sanitized).');
  }

  function pendingReplayResult() {
    if (replayStatusText) {
      replayStatusText.textContent = 'pending';
    }
    updateReplayTelemetry('Awaiting replay (sanitized).');
  }

  function updateStoredTelemetry(message) {
    if (storedTelemetry) {
      storedTelemetry.textContent = sanitizeMessage(message);
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

  function clearSelect(select) {
    while (select.options.length > 0) {
      select.remove(0);
    }
  }

  function addPlaceholderOption(select, label) {
    var option = documentRef.createElement('option');
    option.value = '';
    option.textContent = label;
    select.appendChild(option);
  }

  function setValue(element, value) {
    if (!element) {
      return;
    }
    if (element.tagName === 'SELECT') {
      element.value = value;
    } else {
      element.value = value == null ? '' : value;
    }
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

  function removeNode(node) {
    if (node && node.parentNode) {
      node.parentNode.removeChild(node);
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

  function setMode(mode, options) {
    setMode$new(mode, options || {});
  }

  function setMode$current(mode, options) {
    setMode(mode, options);
  }

  global.Fido2Console = {
    setMode: function (mode, options) {
      setMode(mode, options);
    },
    getMode: function () {
      return currentMode;
    },
  };
})(window);
