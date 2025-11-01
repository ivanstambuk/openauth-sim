(function (global) {
  'use strict';

  var documentRef = global.document;
  var hotpPanel = documentRef.querySelector('[data-protocol-panel="hotp"]');
  var panelTabs = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-panel-tabs"]')
    : null;
  var verboseConsole = global.VerboseTraceConsole || null;
  var evaluateTabButton = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-panel-tab-evaluate"]')
    : null;
  var replayTabButton = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-panel-tab-replay"]')
    : null;
  var evaluatePanelContainer = hotpPanel
    ? hotpPanel.querySelector('[data-hotp-panel="evaluate"]')
    : null;
  var replayPanelContainer = hotpPanel
    ? hotpPanel.querySelector('[data-hotp-panel="replay"]')
    : null;
  var modeToggle = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-mode-toggle"]')
    : null;
  var storedEvaluationSection = hotpPanel
    ? hotpPanel.querySelector('[data-mode-section="stored"]')
    : null;
  var inlineEvaluationSection = hotpPanel
    ? hotpPanel.querySelector('[data-mode-section="inline"]')
    : null;
  var storedForm = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-stored-form"]')
    : null;
var inlineForm = hotpPanel
  ? hotpPanel.querySelector('[data-testid="hotp-inline-form"]')
  : null;
  function clearVerboseTrace() {
    if (verboseConsole && typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    }
  }
  var inlinePresetContainer = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-inline-preset"]')
    : null;
  var inlinePresetSelect = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-inline-preset-select"]')
    : null;
  var storedResultPanel = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-stored-result-panel"]')
    : null;
  var inlineResultPanel = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-inline-result-panel"]')
    : null;
  var storedStatus = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-stored-status"]')
    : null;
  var storedSelect = hotpPanel ? hotpPanel.querySelector('#hotpStoredCredentialId') : null;
  var inlineSecretField = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-inline-shared-secret"]')
    : null;
  var inlineSecretInput = inlineSecretField
    ? inlineSecretField.querySelector('[data-secret-input]')
    : null;
  var inlineSecretMessageNode = inlineSecretField
    ? inlineSecretField.querySelector('[data-secret-message]')
    : null;
  var inlineDigitsInput = hotpPanel ? hotpPanel.querySelector('#hotpInlineDigits') : null;
  var inlineCounterInput = hotpPanel ? hotpPanel.querySelector('#hotpInlineCounter') : null;
  var storedButton = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-stored-evaluate-button"]')
    : null;
  var inlineButton = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-inline-evaluate-button"]')
    : null;
  var inlineAlgorithmSelect = hotpPanel
    ? hotpPanel.querySelector('#hotpInlineAlgorithm')
    : null;
  var seedActions = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-seed-actions"]')
    : null;
  var seedButton = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-seed-credentials"]')
    : null;
  var seedStatus = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-seed-status"]')
    : null;
  var seedDefinitionNode = hotpPanel
    ? hotpPanel.querySelector('#hotp-seed-definitions')
    : null;
  var inlinePresetDefinitionNode = hotpPanel
    ? hotpPanel.querySelector('#hotp-inline-presets')
    : null;
  var inlinePresetPayload = [];
  if (inlinePresetDefinitionNode && inlinePresetDefinitionNode.textContent) {
    var presetText = inlinePresetDefinitionNode.textContent.trim();
    if (presetText.length) {
      try {
        inlinePresetPayload = JSON.parse(presetText);
      } catch (error) {
        if (global.console && typeof global.console.error === 'function') {
          global.console.error('Failed to parse HOTP inline presets', error);
        }
        inlinePresetPayload = [];
      }
    }
  }
  if (inlinePresetDefinitionNode && inlinePresetDefinitionNode.parentNode) {
    inlinePresetDefinitionNode.parentNode.removeChild(inlinePresetDefinitionNode);
  }
  var inlinePresetData = Object.create(null);
  if (Array.isArray(inlinePresetPayload)) {
    inlinePresetPayload.forEach(function (preset) {
      if (!preset || typeof preset.presetKey !== 'string') {
        return;
      }
      inlinePresetData[preset.presetKey] = preset;
    });
  }

  var hotpInitialEvaluateMode = hotpPanel
      ? normalizeModeAttribute(hotpPanel.getAttribute('data-initial-evaluate-mode'))
      : null;
  var hotpInitialReplayMode = hotpPanel
      ? normalizeModeAttribute(hotpPanel.getAttribute('data-initial-replay-mode'))
      : null;
  if (hotpPanel) {
    hotpPanel.removeAttribute('data-initial-evaluate-mode');
    hotpPanel.removeAttribute('data-initial-replay-mode');
  }

  var replayForm = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-replay-form"]')
    : null;
  var sampleEndpointBase = replayForm
    ? replayForm.getAttribute('data-sample-endpoint')
    : null;
  var replayModeToggle = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-mode-toggle"]')
    : null;
  var replayStoredPanel = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-stored-panel"]')
    : null;
  var replayInlinePanel = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-inline-panel"]')
    : null;
  var replayInlinePresetContainer = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-inline-preset"]')
    : null;
  var replayInlinePresetSelect = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-inline-sample-select"]')
    : null;
  var replayStoredSelect = replayForm
    ? replayForm.querySelector('#hotpReplayStoredCredentialId')
    : null;
  var replayStoredOtpInput = replayForm
    ? replayForm.querySelector('#hotpReplayStoredOtp')
    : null;
  var replayStoredCounterInput = replayForm
    ? replayForm.querySelector('#hotpReplayStoredCounter')
    : null;
  var replaySampleActions = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-sample-actions"]')
    : null;
  var replaySampleStatus = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-sample-status"]')
    : null;
  var replayInlineSecretField = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-inline-shared-secret"]')
    : null;
  var replayInlineSecretInput = replayInlineSecretField
    ? replayInlineSecretField.querySelector('[data-secret-input]')
    : null;
  var replayInlineSecretMessageNode = replayInlineSecretField
    ? replayInlineSecretField.querySelector('[data-secret-message]')
    : null;
  var replayInlineAlgorithmSelect = replayForm
    ? replayForm.querySelector('#hotpReplayInlineAlgorithm')
    : null;
  var replayInlineDigitsInput = replayForm
    ? replayForm.querySelector('#hotpReplayInlineDigits')
    : null;
  var replayInlineCounterInput = replayForm
    ? replayForm.querySelector('#hotpReplayInlineCounter')
    : null;
  var replayInlineOtpInput = replayForm
    ? replayForm.querySelector('#hotpReplayInlineOtp')
    : null;
  var inlineSecretController = global.SecretFieldBridge && inlineSecretInput
    ? global.SecretFieldBridge.create({
        container: inlineSecretField,
        textarea: inlineSecretInput,
        modeToggle: inlineSecretField
          ? inlineSecretField.querySelector('[data-secret-mode-toggle]')
          : null,
        modeButtons: inlineSecretField
          ? {
              hex: inlineSecretField.querySelector('[data-secret-mode-button="hex"]'),
              base32: inlineSecretField.querySelector('[data-secret-mode-button="base32"]')
            }
          : null,
        lengthNode: inlineSecretField
          ? inlineSecretField.querySelector('[data-secret-length]')
          : null,
        messageNode: inlineSecretMessageNode,
        defaultMessage: inlineSecretMessageNode && inlineSecretMessageNode.textContent
          ? inlineSecretMessageNode.textContent.trim()
          : '↔ Converts automatically when you switch modes.',
        errorPrefix: '⚠ ',
        requiredMessage: 'Provide the HOTP shared secret.'
      })
    : null;
  var replayInlineSecretController = global.SecretFieldBridge && replayInlineSecretInput
    ? global.SecretFieldBridge.create({
        container: replayInlineSecretField,
        textarea: replayInlineSecretInput,
        modeToggle: replayInlineSecretField
          ? replayInlineSecretField.querySelector('[data-secret-mode-toggle]')
          : null,
        modeButtons: replayInlineSecretField
          ? {
              hex: replayInlineSecretField.querySelector('[data-secret-mode-button="hex"]'),
              base32: replayInlineSecretField.querySelector('[data-secret-mode-button="base32"]')
            }
          : null,
        lengthNode: replayInlineSecretField
          ? replayInlineSecretField.querySelector('[data-secret-length]')
          : null,
        messageNode: replayInlineSecretMessageNode,
        defaultMessage: replayInlineSecretMessageNode && replayInlineSecretMessageNode.textContent
          ? replayInlineSecretMessageNode.textContent.trim()
          : '↔ Converts automatically when you switch modes.',
        errorPrefix: '⚠ ',
        requiredMessage: 'Provide the HOTP shared secret for replay.'
      })
    : null;
  var replayResultPanel = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-replay-result"]')
    : null;
  var replayStatusBadge = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-replay-status"]')
    : null;
  var replayReasonNode = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-replay-reason-code"]')
    : null;
  var replayOutcomeNode = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-replay-outcome"]')
    : null;
  var replaySubmitButton = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-submit"]')
    : null;

  var evaluationInitialized = false;
  var replayInitialized = false;
  var activePanel = 'evaluate';
  var HOTP_ALLOWED_TABS = ['evaluate', 'replay'];

  var seedDefinitionsPayload = [];
  if (seedDefinitionNode && seedDefinitionNode.textContent) {
    var payloadText = seedDefinitionNode.textContent.trim();
    if (payloadText.length) {
      try {
        seedDefinitionsPayload = JSON.parse(payloadText);
      } catch (error) {
        if (global.console && typeof global.console.error === 'function') {
          global.console.error('Failed to parse HOTP seed definitions', error);
        }
        seedDefinitionsPayload = [];
      }
    }
  }

  var seedMetadataByCredential = {};
  seedDefinitionsPayload.forEach(function (definition) {
    if (!definition || typeof definition.credentialId !== 'string') {
      return;
    }
    var metadata = definition.metadata && typeof definition.metadata === 'object'
      ? definition.metadata
      : {};
    if (metadata.label && !metadata.samplePresetLabel) {
      metadata.samplePresetLabel = metadata.label;
    }
    if (metadata.presetKey && !metadata.samplePresetKey) {
      metadata.samplePresetKey = metadata.presetKey;
    }
    seedMetadataByCredential[definition.credentialId] = metadata;
  });

  if (seedDefinitionNode && seedDefinitionNode.parentNode) {
    seedDefinitionNode.parentNode.removeChild(seedDefinitionNode);
  }


  var credentialCache = null;
  var credentialPromise = null;
  var inlinePresetActiveKey = '';
  var inlinePresetActiveLabel = '';
  var seedInProgress = false;
  var lastBroadcastHotpTab = null;
  var lastBroadcastHotpReplayMode = null;

  function setHidden(element, hidden) {
    if (!element) {
      return;
    }
    if (hidden) {
      element.setAttribute('hidden', 'hidden');
      element.setAttribute('aria-hidden', 'true');
    } else {
      element.removeAttribute('hidden');
      element.removeAttribute('aria-hidden');
    }
  }

  function setSeedStatus(message, severity) {
    if (!seedStatus) {
      return;
    }
    var text = typeof message === 'string' ? message.trim() : '';
    seedStatus.classList.remove('credential-status--error', 'credential-status--warning');
    if (!text) {
      seedStatus.textContent = '';
      setHidden(seedStatus, true);
      return;
    }
    seedStatus.textContent = text;
    if (severity === 'error') {
      seedStatus.classList.add('credential-status--error');
    } else if (severity === 'warning') {
      seedStatus.classList.add('credential-status--warning');
    }
    setHidden(seedStatus, false);
  }

  function setReplaySampleStatus(message, severity) {
    if (!replaySampleStatus) {
      return;
    }
    var text = typeof message === 'string' ? message.trim() : '';
    replaySampleStatus.classList.remove('credential-status--error', 'credential-status--warning');
    if (!text) {
      replaySampleStatus.textContent = '';
      setHidden(replaySampleStatus, true);
      return;
    }
    replaySampleStatus.textContent = text;
    if (severity === 'error') {
      replaySampleStatus.classList.add('credential-status--error');
    } else if (severity === 'warning') {
      replaySampleStatus.classList.add('credential-status--warning');
    }
    setHidden(replaySampleStatus, false);
  }

  function syncSeedControls(storedActive) {
    if (!seedActions) {
      return;
    }
    setHidden(seedActions, !storedActive);
    if (!seedButton) {
      return;
    }
    if (storedActive && !seedInProgress) {
      seedButton.removeAttribute('disabled');
    } else {
      seedButton.setAttribute('disabled', 'disabled');
    }
  }

  function csrfTokenFor(form) {
    if (!form) {
      return null;
    }
    var tokenInput = form.querySelector('input[name="_csrf"]');
    return tokenInput && tokenInput.value ? tokenInput.value : null;
  }

  function fetchDelegate(endpoint, options) {
    if (typeof global.fetch !== 'function') {
      return Promise.reject(new Error('Fetch API unavailable'));
    }
    return global.fetch(endpoint, options);
  }

  function storedSampleEndpoint(credentialId) {
    if (!sampleEndpointBase) {
      return null;
    }
    var base = sampleEndpointBase;
    if (!base) {
      return null;
    }
    if (base.endsWith('/')) {
      base = base.slice(0, -1);
    }
    return base + '/' + encodeURIComponent(credentialId) + '/sample';
  }

  function findCredentialSummary(credentialId) {
    if (!credentialCache || !credentialId) {
      return null;
    }
    for (var index = 0; index < credentialCache.length; index += 1) {
      var summary = credentialCache[index];
      if (summary && summary.id === credentialId) {
        return summary;
      }
    }
    return null;
  }

  function ensureCredentials(forceRefresh) {
    if (!storedForm || !storedSelect) {
      return Promise.resolve([]);
    }
    if (!forceRefresh && credentialCache && credentialCache.length) {
      renderCredentialOptions(credentialCache);
      return Promise.resolve(credentialCache);
    }
    if (credentialPromise) {
      return credentialPromise;
    }

    var endpoint = storedForm.getAttribute('data-credentials-endpoint');
    if (!endpoint) {
      credentialCache = [];
      renderCredentialOptions(credentialCache);
      return Promise.resolve([]);
    }

    if (storedStatus) {
      storedStatus.textContent = 'Loading stored credentials…';
    }
    storedSelect.setAttribute('disabled', 'disabled');
    if (replayStoredSelect) {
      replayStoredSelect.setAttribute('disabled', 'disabled');
    }

    credentialPromise = fetchDelegate(endpoint, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
      .then(function (response) {
        if (!response.ok) {
          throw new Error('Failed to load stored credentials');
        }
        return response.text();
      })
      .then(function (bodyText) {
        var list = [];
        if (bodyText) {
          try {
            list = JSON.parse(bodyText);
          } catch (error) {
            throw new Error('Invalid credential directory response');
          }
        }
        if (!Array.isArray(list)) {
          list = [];
        }
        credentialCache = list
          .map(function (item) {
            if (!item || typeof item.id !== 'string') {
              return null;
            }
            return {
              id: item.id,
              label: typeof item.label === 'string' ? item.label : item.id,
              digits: typeof item.digits === 'number' ? item.digits : null,
              counter: typeof item.counter === 'number' ? item.counter : null,
            };
          })
          .filter(Boolean)
          .sort(function (a, b) {
            return a.id.localeCompare(b.id, undefined, { sensitivity: 'base' });
          });
        renderCredentialOptions(credentialCache);
        if (storedStatus) {
          storedStatus.textContent =
            credentialCache.length === 0
              ? 'No HOTP credentials found. Import credentials via the CLI or REST API.'
              : 'Select a credential to evaluate an inbound OTP.';
        }
        return credentialCache;
      })
      .catch(function (error) {
        if (global.console && typeof global.console.error === 'function') {
          global.console.error(error);
        }
        credentialCache = [];
        renderCredentialOptions(credentialCache);
        if (storedStatus) {
          storedStatus.textContent = 'Unable to load stored credentials.';
        }
        return [];
      })
      .finally(function () {
        credentialPromise = null;
        storedSelect.removeAttribute('disabled');
        if (replayStoredSelect) {
          replayStoredSelect.removeAttribute('disabled');
        }
      });

    return credentialPromise;
  }

  function updateSelectOptions(selectElement, options) {
    if (!selectElement) {
      return;
    }
    var previousValue = selectElement.value;
    while (selectElement.firstChild) {
      selectElement.removeChild(selectElement.firstChild);
    }
    var placeholder = documentRef.createElement('option');
    placeholder.value = '';
    placeholder.textContent =
      options && options.length
        ? 'Select a credential'
        : 'No HOTP credentials available';
    selectElement.appendChild(placeholder);
    if (Array.isArray(options)) {
      options.forEach(function (option) {
        var opt = documentRef.createElement('option');
        opt.value = option.id;
        opt.textContent = option.label;
        selectElement.appendChild(opt);
      });
    }
    if (previousValue && selectElement.querySelector('option[value="' + previousValue + '"]')) {
      selectElement.value = previousValue;
    }
  }

  function renderCredentialOptions(options) {
    updateSelectOptions(storedSelect, options);
    updateSelectOptions(replayStoredSelect, options);
    updateStoredSampleHints();
  }

  function formatMetadata(metadata) {
    if (!metadata || typeof metadata !== 'object') {
      return '';
    }
    var parts = [];
    if (metadata.credentialSource) {
      parts.push('credentialSource=' + metadata.credentialSource);
    }
    if (metadata.credentialId) {
      parts.push('credentialId=' + metadata.credentialId);
    }
    if (metadata.credentialReference) {
      parts.push('credentialReference=' + metadata.credentialReference);
    }
    if (metadata.hashAlgorithm) {
      parts.push('hashAlgorithm=' + metadata.hashAlgorithm);
    }
    if (typeof metadata.digits === 'number') {
      parts.push('digits=' + metadata.digits);
    }
    if (typeof metadata.previousCounter === 'number') {
      parts.push('previousCounter=' + metadata.previousCounter);
    }
    if (typeof metadata.nextCounter === 'number') {
      parts.push('nextCounter=' + metadata.nextCounter);
    }
    if (metadata.samplePresetKey) {
      parts.push('samplePresetKey=' + metadata.samplePresetKey);
    }
    if (metadata.samplePresetLabel) {
      parts.push('samplePresetLabel=' + metadata.samplePresetLabel);
    }
    if (metadata.telemetryId) {
      parts.push('telemetryId=' + metadata.telemetryId);
    }
    return parts.join(' • ');
  }

  function parseJson(bodyText) {
    if (!bodyText) {
      return null;
    }
    try {
      return JSON.parse(bodyText);
    } catch (error) {
      if (global.console && typeof global.console.error === 'function') {
        global.console.error('Failed to parse HOTP response', error);
      }
      return null;
    }
  }

  function normalizeStatusLabel(status) {
    if (!status && status !== 0) {
      return 'Unknown';
    }
    var raw = String(status).trim();
    if (!raw) {
      return 'Unknown';
    }
    var lowered = raw.toLowerCase();
    if (lowered === 'generated' || lowered === 'success' || lowered === 'ok') {
      return 'Success';
    }
    if (lowered === 'failed' || lowered === 'failure') {
      return 'Failed';
    }
    if (lowered === 'error') {
      return 'Error';
    }
    if (lowered === 'invalid') {
      return 'Invalid';
    }
    var parts = raw
      .split(/[^A-Za-z0-9]+/)
      .filter(function (part) {
        return part && part.length > 0;
      })
      .map(function (part) {
        return part.charAt(0).toUpperCase() + part.slice(1).toLowerCase();
      });
    return parts.length > 0 ? parts.join(' ') : 'Unknown';
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

  function resetResultCard(panel) {
    if (!panel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.resetMessage === 'function') {
      global.ResultCard.resetMessage(panel);
    }
  }

  function showResultError(panel, message, hint) {
    if (!panel) {
      return;
    }
    var fallbackMessage = typeof message === 'string' && message.trim().length > 0
        ? message.trim()
        : 'Operation failed.';
    var options = {};
    if (typeof hint === 'string' && hint.trim().length > 0) {
      options.hint = hint.trim();
    }
    if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
      global.ResultCard.showMessage(panel, fallbackMessage, 'error', options);
    } else {
      var messageNode = panel.querySelector('[data-result-message]');
      if (messageNode) {
        messageNode.textContent = fallbackMessage;
        messageNode.removeAttribute('hidden');
        messageNode.setAttribute('aria-hidden', 'false');
      }
    }
    setHidden(panel, false);
  }

  function resolveApiErrorPayload(parsed, fallbackMessage) {
    var message = typeof fallbackMessage === 'string' && fallbackMessage.trim().length > 0
        ? fallbackMessage.trim()
        : 'Operation failed.';
    var reason = '';
    if (parsed) {
      if (typeof parsed.message === 'string' && parsed.message.trim().length > 0) {
        message = parsed.message.trim();
      } else if (
          parsed.error &&
          typeof parsed.error.description === 'string' &&
          parsed.error.description.trim().length > 0) {
        message = parsed.error.description.trim();
      }
      var details = parsed.details;
      if (details && typeof details === 'object') {
        var detailReason = details.reasonCode || details.reason || details.reason_code;
        if (typeof detailReason === 'string' && detailReason.trim().length > 0) {
          reason = detailReason.trim();
        }
      }
      if (!reason && typeof parsed.reasonCode === 'string' && parsed.reasonCode.trim().length > 0) {
        reason = parsed.reasonCode.trim();
      }
    }
    return { message: message, reason: reason };
  }

  function applyStatusBadge(statusNode, status) {
    if (!statusNode) {
      return;
    }
    var variant = resolveStatusVariant(status);
    var label = normalizeStatusLabel(status);
    statusNode.className = 'status-badge';
    if (variant === 'success') {
      statusNode.classList.add('status-badge--success');
    } else if (variant === 'error') {
      statusNode.classList.add('status-badge--error');
    } else {
      statusNode.classList.add('status-badge--info');
    }
    statusNode.textContent = label;
  }

  function renderResult(panel, payload) {
    if (!panel) {
      return;
    }
    resetResultCard(panel);
    if (!payload) {
      verboseApplyResponse(null, 'info');
      return;
    }
    var status = payload && payload.status ? payload.status : 'unknown';
    var metadata = payload && payload.metadata ? payload.metadata : null;
    var otp = payload && payload.otp ? payload.otp : null;
    var reason = payload && payload.reasonCode ? String(payload.reasonCode).trim() : '';
    var variant = resolveStatusVariant(status);
    verboseApplyResponse(payload, variant === 'error' ? 'error' : 'success');
    applyStatusBadge(panel.querySelector('[data-testid="hotp-result-status"]'), status);
    var otpNode = panel.querySelector('[data-testid="hotp-result-otp"]');
    if (otpNode) {
      var normalizedOtp = otp && otp.trim().length > 0 ? otp.trim() : '—';
      otpNode.textContent = normalizedOtp;
    }
    var metadataNode = panel.querySelector('[data-testid="hotp-result-metadata"]');
    if (metadataNode) {
      metadataNode.textContent = formatMetadata(metadata);
    }
    if (variant === 'error') {
      var label = normalizeStatusLabel(status);
      var hint = reason ? 'Reason: ' + reason : '';
      showResultError(panel, 'Evaluation returned status: ' + label + '.', hint);
      if (otpNode) {
        otpNode.textContent = '—';
      }
    } else {
      setHidden(panel, false);
    }
  }

  function normalizeHotpTab(value) {
    return HOTP_ALLOWED_TABS.indexOf(value) >= 0 ? value : 'evaluate';
  }

  function normalizeModeAttribute(value) {
    if (typeof value !== 'string') {
      return null;
    }
    var normalized = value.trim().toLowerCase();
    if (normalized === 'stored') {
      return 'stored';
    }
    if (normalized === 'inline') {
      return 'inline';
    }
    return null;
  }

  function parseHotpTabFromSearch(search) {
    try {
      var params = new global.URLSearchParams(search || global.location.search || '');
      return normalizeHotpTab(params.get('tab'));
    } catch (error) {
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to parse HOTP tab from URL parameters', error);
      }
      return 'evaluate';
    }
  }

  function parseHotpModeFromSearch(search) {
    try {
      var params = new global.URLSearchParams(search || global.location.search || '');
      var value = params.get('mode');
      if (!value && hotpInitialEvaluateMode) {
        value = hotpInitialEvaluateMode;
        hotpInitialEvaluateMode = null;
      }
      return value && value.trim().toLowerCase() === 'stored' ? 'stored' : 'inline';
    } catch (error) {
      return 'inline';
    }
  }

  function parseHotpReplayModeFromSearch(search) {
    try {
      var params = new global.URLSearchParams(search || global.location.search || '');
      var value = params.get('mode');
      if (!value && hotpInitialReplayMode) {
        value = hotpInitialReplayMode;
        hotpInitialReplayMode = null;
      }
      return value && value.trim().toLowerCase() === 'stored' ? 'stored' : 'inline';
    } catch (error) {
      return 'inline';
    }
  }

  function hotpHistoryState(tab) {
    return { protocol: 'hotp', tab: normalizeHotpTab(tab) };
  }

  function syncHotpUrl(tab, options) {
    if (!global.history || typeof global.history.pushState !== 'function') {
      return;
    }
    var desiredTab = normalizeHotpTab(tab);
    var params = new global.URLSearchParams(global.location.search);
    params.set('protocol', 'hotp');
    params.set('tab', desiredTab);
    var search = '?' + params.toString();
    var url = global.location.pathname + search;
    var state = hotpHistoryState(desiredTab);

    if (options && options.replace) {
      global.history.replaceState(state, '', url);
      return;
    }

    var currentState = global.history.state || {};
    if (
      global.location.search === search &&
      currentState.protocol === state.protocol &&
      currentState.tab === state.tab
    ) {
      return;
    }

    global.history.pushState(state, '', url);
  }

  function toggleTabState(button, active) {
    if (!button) {
      return;
    }
    button.classList.toggle('mode-pill--active', active);
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  }

  function setActivePanel(panel, options) {
    if (!hotpPanel) {
      return;
    }
    var desired = normalizeHotpTab(panel);
    var nextOptions = options || {};
    var samePanel = activePanel === desired;

    if (!samePanel) {
      activePanel = desired;
      toggleTabState(evaluateTabButton, desired === 'evaluate');
      toggleTabState(replayTabButton, desired === 'replay');
      setHidden(evaluatePanelContainer, desired !== 'evaluate');
      setHidden(replayPanelContainer, desired !== 'replay');
      clearVerboseTrace();
    } else if (nextOptions.force === true) {
      clearVerboseTrace();
    }

    if (nextOptions.skipUrlSync !== true) {
      syncHotpUrl(desired, {
        replace: nextOptions.replaceHistory === true || samePanel,
      });
    }

    if (desired === 'replay') {
      initializeReplay();
    } else {
      initializeEvaluation();
    }

    dispatchHotpTabChange(desired, {
      replace: nextOptions.replaceHistory === true || samePanel,
      broadcast: nextOptions.skipUrlSync === true ? false : true,
      force: nextOptions.force === true,
    });
  }

  function updateStoredSampleHints() {
    if (!replayStoredSelect) {
      return;
    }
    var credentialId = replayStoredSelect.value || '';
    var summary = findCredentialSummary(credentialId);
    if (replayStoredCounterInput) {
      if (summary && typeof summary.counter === 'number') {
        replayStoredCounterInput.value = String(summary.counter);
      } else if (summary && typeof summary.counter === 'string' && summary.counter.trim()) {
        replayStoredCounterInput.value = summary.counter.trim();
      } else {
        replayStoredCounterInput.value = '';
      }
    }
    if (!credentialId) {
      if (replayStoredOtpInput) {
        replayStoredOtpInput.value = '';
      }
      if (replaySampleActions) {
        setHidden(replaySampleActions, true);
      }
      setReplaySampleStatus('', null);
      return;
    }
    if (replaySampleActions) {
      setHidden(replaySampleActions, false);
    }
    if (replayStoredOtpInput) {
      replayStoredOtpInput.value = '';
    }
    if (replayResultPanel) {
      setHidden(replayResultPanel, true);
    }
    hideReplayError();
    applyStoredSample(credentialId, summary);
  }

  function applyStoredSample(credentialId, summary) {
    if (!credentialId) {
      return;
    }
    var endpoint = storedSampleEndpoint(credentialId);
    if (!endpoint) {
      setReplaySampleStatus('Sample data endpoint unavailable.', 'warning');
      return;
    }
    setReplaySampleStatus('Loading sample data…', null);

    fetchDelegate(endpoint, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
      .then(function (response) {
        return response.text().then(function (bodyText) {
          return { ok: response.ok, status: response.status, body: bodyText };
        });
      })
      .then(function (payload) {
        if (!payload.ok) {
          if (payload.status === 404) {
            setReplaySampleStatus('Sample data is not available for this credential.', 'warning');
          } else {
            setReplaySampleStatus('Unable to load sample data.', 'error');
          }
          return;
        }

        if (credentialId !== ((replayStoredSelect && replayStoredSelect.value) || '').trim()) {
          return;
        }
        var parsed = parseJson(payload.body) || {};
        if (replayStoredOtpInput) {
          replayStoredOtpInput.value = typeof parsed.otp === 'string' ? parsed.otp : '';
        }
        if (replayStoredCounterInput) {
          if (typeof parsed.counter === 'number') {
            replayStoredCounterInput.value = String(parsed.counter);
          } else if (typeof parsed.counter === 'string' && parsed.counter.trim().length > 0) {
            replayStoredCounterInput.value = parsed.counter.trim();
          }
        }
        var metadata = parsed.metadata && typeof parsed.metadata === 'object' ? parsed.metadata : {};
        var notes = metadata.notes && typeof metadata.notes === 'string' ? metadata.notes.trim() : '';
        var presetLabel =
          metadata.samplePresetLabel && typeof metadata.samplePresetLabel === 'string'
            ? metadata.samplePresetLabel.trim()
            : '';
        if (notes) {
          setReplaySampleStatus(notes, null);
        } else if (presetLabel) {
          setReplaySampleStatus('Sample "' + presetLabel + '" applied automatically.', null);
        } else {
          setReplaySampleStatus('Sample data applied automatically.', null);
        }
        if (summary && typeof parsed.counter === 'number') {
          summary.counter = parsed.counter;
        }
      })
      .catch(function (error) {
        if (global.console && typeof global.console.error === 'function') {
          global.console.error('Failed to load HOTP sample data', error);
        }
        setReplaySampleStatus('Unable to load sample data.', 'error');
      });
  }

  function setInlinePresetTracking(key, label) {
    inlinePresetActiveKey = key || '';
    inlinePresetActiveLabel = label ? label.trim() : '';
  }

  function clearInlinePresetTracking() {
    inlinePresetActiveKey = '';
    inlinePresetActiveLabel = '';
    if (inlinePresetContainer) {
      inlinePresetContainer.removeAttribute('data-preset-applied');
    }
    if (inlinePresetSelect && inlinePresetSelect.value) {
      inlinePresetSelect.value = '';
    }
  }

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
    var options = { variant: variant || 'info', protocol: 'hotp' };
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
    var options = { variant: 'error', protocol: 'hotp' };
    if (typeof verboseConsole.handleError === 'function') {
      verboseConsole.handleError(payload, options);
    } else if (payload && payload.trace && typeof verboseConsole.renderTrace === 'function') {
      verboseConsole.renderTrace(payload.trace, options);
    } else if (typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    }
  }

  function applyInlineEvaluationPreset(presetKey) {
    var preset = inlinePresetData[presetKey];
    if (!preset) {
      return;
    }
    if (inlineSecretController && typeof preset.sharedSecretHex === 'string') {
      inlineSecretController.applyPreset(preset.sharedSecretHex);
    } else if (inlineSecretInput && typeof preset.sharedSecretHex === 'string') {
      inlineSecretInput.value = preset.sharedSecretHex;
    }
    if (inlineAlgorithmSelect && preset.algorithm) {
      inlineAlgorithmSelect.value = preset.algorithm;
    }
    if (inlineDigitsInput && typeof preset.digits === 'number') {
      inlineDigitsInput.value = String(preset.digits);
    }
    if (inlineCounterInput && typeof preset.counter === 'number') {
      inlineCounterInput.value = String(preset.counter);
    }
    if (inlinePresetContainer) {
      inlinePresetContainer.setAttribute('data-preset-applied', presetKey);
    }
    setHidden(inlineResultPanel, true);
    hideInlineError();
  }

  function renderPresetOptions(selectNode) {
    if (!selectNode) {
      return;
    }
    while (selectNode.firstChild) {
      selectNode.removeChild(selectNode.firstChild);
    }
    var placeholder = documentRef.createElement('option');
    placeholder.value = '';
    placeholder.textContent = 'Select a sample';
    selectNode.appendChild(placeholder);
    Object.keys(inlinePresetData)
      .sort(function (a, b) {
        return a.localeCompare(b, undefined, { sensitivity: 'base' });
      })
      .forEach(function (key) {
        var preset = inlinePresetData[key];
        if (!preset) {
          return;
        }
        var option = documentRef.createElement('option');
        option.value = key;
        option.textContent = preset.label || key;
        selectNode.appendChild(option);
      });
  }

  function renderInlinePresetOptions() {
    renderPresetOptions(inlinePresetSelect);
    renderPresetOptions(replayInlinePresetSelect);
  }

  function applyInlinePreset(presetKey) {
    var preset = inlinePresetData[presetKey];
    if (!preset) {
      return;
    }
    if (replayInlineSecretController) {
      replayInlineSecretController.applyPreset(preset.sharedSecretHex || '');
    } else if (replayInlineSecretInput) {
      replayInlineSecretInput.value = preset.sharedSecretHex || '';
    }
    if (replayInlineAlgorithmSelect) {
      replayInlineAlgorithmSelect.value = preset.algorithm || 'SHA1';
    }
    if (replayInlineDigitsInput) {
      replayInlineDigitsInput.value = typeof preset.digits === 'number'
        ? String(preset.digits)
        : '';
    }
    if (replayInlineCounterInput) {
      replayInlineCounterInput.value = typeof preset.counter === 'number'
        ? String(preset.counter)
        : '';
    }
    if (replayInlineOtpInput) {
      replayInlineOtpInput.value = preset.otp || '';
    }
  }

  function hideReplayError() {
    if (!replayResultPanel) {
      return;
    }
    resetResultCard(replayResultPanel);
  }

  function showReplayError(message, error) {
    if (!replayResultPanel) {
      return;
    }
    if (typeof error !== 'undefined') {
      verboseApplyError(error);
    } else {
      verboseApplyError(null);
    }
    var hint = '';
    if (replayReasonNode) {
      replayReasonNode.textContent = 'validation_failure';
    }
    if (replayOutcomeNode) {
      replayOutcomeNode.textContent = 'error';
    }
    if (replayStatusBadge) {
      applyStatusBadge(replayStatusBadge, 'error');
    }
    showResultError(replayResultPanel, message || 'Replay request failed.', hint);
  }

  function renderReplayResult(payload) {
    if (!payload) {
      verboseApplyResponse(null, 'info');
      showReplayError('Unexpected replay response.');
      return;
    }
    hideReplayError();
    var status = payload && payload.status ? String(payload.status).trim() : 'unknown';
    if (!status) {
      status = 'unknown';
    }
    var variant = resolveStatusVariant(status);
    verboseApplyResponse(payload, variant === 'error' ? 'error' : 'success');
    if (replayStatusBadge) {
      applyStatusBadge(replayStatusBadge, status);
    }
    var reason =
      payload && payload.reasonCode && String(payload.reasonCode).trim().length > 0
        ? String(payload.reasonCode).trim()
        : 'unknown';
    if (replayReasonNode) {
      replayReasonNode.textContent = reason;
    }
    if (replayOutcomeNode) {
      replayOutcomeNode.textContent = status;
    }
    resetResultCard(replayResultPanel);
    if (variant === 'error') {
      var statusLabel = normalizeStatusLabel(status);
      var hint = reason ? 'Reason: ' + reason : '';
      showResultError(replayResultPanel, 'Replay request returned status: ' + statusLabel + '.', hint);
    } else {
      setHidden(replayResultPanel, false);
    }
  }

  function showStoredError(message) {
    if (!storedResultPanel) {
      return;
    }
    applyStatusBadge(storedResultPanel.querySelector('[data-testid="hotp-result-status"]'), 'error');
    var otpNode = storedResultPanel.querySelector('[data-testid="hotp-result-otp"]');
    if (otpNode) {
      otpNode.textContent = '—';
    }
    showResultError(storedResultPanel, message || 'Stored credential evaluation failed.');
  }

  function hideStoredError() {
    if (!storedResultPanel) {
      return;
    }
    resetResultCard(storedResultPanel);
  }

  function showInlineError(message, hint) {
    if (!inlineResultPanel) {
      return;
    }
    applyStatusBadge(inlineResultPanel.querySelector('[data-testid="hotp-result-status"]'), 'error');
    var otpNode = inlineResultPanel.querySelector('[data-testid="hotp-result-otp"]');
    if (otpNode) {
      otpNode.textContent = '—';
    }
    var normalizedMessage =
        typeof message === 'string' && message.trim().length > 0 ? message.trim() : 'Inline evaluation failed.';
    var options = {};
    if (typeof hint === 'string' && hint.trim().length > 0) {
      options.hint = hint.trim();
    }
    if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
      global.ResultCard.showMessage(inlineResultPanel, normalizedMessage, 'error', options);
    } else {
      showResultError(inlineResultPanel, normalizedMessage, options.hint);
    }
  }

  function hideInlineError() {
    if (!inlineResultPanel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.resetMessage === 'function') {
      global.ResultCard.resetMessage(inlineResultPanel);
    } else {
      resetResultCard(inlineResultPanel);
    }
  }

  function setReplayMode(mode, options) {
    if (!replayModeToggle) {
      return;
    }
    var normalized = mode === 'stored' ? 'stored' : 'inline';
    var currentModeAttr = replayModeToggle.getAttribute('data-mode') === 'stored' ? 'stored' : 'inline';
    var force = Boolean(options && options.force === true);
    if (!force && currentModeAttr === normalized) {
      return;
    }
    replayModeToggle.setAttribute('data-mode', normalized);
    var storedRadio = replayModeToggle.querySelector('[data-testid="hotp-replay-mode-select-stored"]');
    var inlineRadio = replayModeToggle.querySelector('[data-testid="hotp-replay-mode-select-inline"]');
    if (storedRadio) {
      storedRadio.checked = normalized === 'stored';
    }
    if (inlineRadio) {
      inlineRadio.checked = normalized === 'inline';
    }
    setHidden(replayStoredPanel, normalized !== 'stored');
    setHidden(replayInlinePanel, normalized !== 'inline');
    setHidden(replayInlinePresetContainer, normalized !== 'inline');
    if (normalized === 'stored') {
      updateStoredSampleHints();
    } else {
      if (replaySampleActions) {
        setHidden(replaySampleActions, true);
      }
      setReplaySampleStatus('', null);
    }
    setHidden(replayResultPanel, true);
    hideReplayError();
    if (!options || options.broadcast !== false) {
      dispatchHotpReplayModeChange(normalized, options);
    }
    clearVerboseTrace();
  }

  function dispatchHotpTabChange(tab, options) {
    if (options && options.broadcast === false) {
      return;
    }
    if (lastBroadcastHotpTab === tab && !(options && options.force)) {
      return;
    }
    lastBroadcastHotpTab = tab;
    try {
      var detail = { tab: tab };
      if (options && options.replace === true) {
        detail.replace = true;
      }
      global.dispatchEvent(
          new global.CustomEvent('operator:hotp-tab-changed', { detail: detail }));
    } catch (error) {
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to broadcast HOTP tab change', error);
      }
    }
  }

  function dispatchHotpReplayModeChange(mode, options) {
    if (options && options.broadcast === false) {
      return;
    }
    if (lastBroadcastHotpReplayMode === mode && !(options && options.force)) {
      return;
    }
    lastBroadcastHotpReplayMode = mode;
    try {
      var detail = { mode: mode };
      if (options && options.replace === true) {
        detail.replace = true;
      }
      global.dispatchEvent(
          new global.CustomEvent('operator:hotp-replay-mode-changed', { detail: detail }));
    } catch (error) {
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to broadcast HOTP replay mode change', error);
      }
    }
  }

  function currentReplayMode() {
    if (!replayModeToggle) {
      return 'inline';
    }
    var datasetMode = replayModeToggle.getAttribute('data-mode');
    return datasetMode === 'stored' ? 'stored' : 'inline';
  }

  function handleReplaySubmit() {
    if (!replayForm || !replaySubmitButton) {
      return;
    }

    var mode = currentReplayMode();
    var endpoint = replayForm.getAttribute('data-replay-endpoint') || '/api/v1/hotp/replay';
    var payload;

  if (mode === 'inline') {
      var algorithm = replayInlineAlgorithmSelect ? replayInlineAlgorithmSelect.value : 'SHA1';
      var digits = replayInlineDigitsInput ? parseInt(replayInlineDigitsInput.value, 10) : NaN;
      var counter = replayInlineCounterInput ? parseInt(replayInlineCounterInput.value, 10) : NaN;
      var otp = replayInlineOtpInput ? replayInlineOtpInput.value.trim() : '';
      var secretPayloadInline = {};

      if (replayInlineSecretController) {
        if (!replayInlineSecretController.validate()) {
          return;
        }
        secretPayloadInline = replayInlineSecretController.payload() || {};
      } else {
        var inlineSecretFallback = replayInlineSecretInput ? replayInlineSecretInput.value.trim() : '';
        if (inlineSecretFallback) {
          secretPayloadInline.sharedSecretHex = inlineSecretFallback;
        }
      }

      if ((!secretPayloadInline || Object.keys(secretPayloadInline).length === 0) || !otp || Number.isNaN(digits) || Number.isNaN(counter)) {
        showReplayError('Inline replay requires secret, digits, counter, and OTP values.');
        return;
      }

      payload = Object.assign({}, secretPayloadInline, {
        algorithm: algorithm || 'SHA1',
        digits: digits,
        counter: counter,
        otp: otp,
      });
    } else {
      var credentialId = replayStoredSelect ? replayStoredSelect.value.trim() : '';
      var storedOtp = replayStoredOtpInput ? replayStoredOtpInput.value.trim() : '';
      if (!credentialId) {
        showReplayError('Select a stored credential before submitting a replay request.');
        return;
      }
      if (!storedOtp || storedOtp.length < 6) {
        showReplayError('Provide the observed HOTP value to replay.');
        return;
      }
      payload = { credentialId: credentialId, otp: storedOtp };
    }

    replaySubmitButton.setAttribute('disabled', 'disabled');
    hideReplayError();
    setHidden(replayResultPanel, true);
    payload = verboseAttach(payload);
    verboseBeginRequest();

    fetchDelegate(endpoint, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfTokenFor(replayForm) || '',
      },
      credentials: 'same-origin',
      body: JSON.stringify(payload),
    })
      .then(function (response) {
        return response.text().then(function (bodyText) {
          return { ok: response.ok, status: response.status, body: bodyText };
        });
      })
      .then(function (payload) {
        if (!payload.ok) {
          var parsed = parseJson(payload.body);
          var message =
            parsed && parsed.error && parsed.error.description
              ? parsed.error.description
              : 'Replay request failed.';
          throw new Error(message);
        }
        var parsedBody = parseJson(payload.body) || {};
        renderReplayResult(parsedBody);
      })
      .catch(function (error) {
        verboseApplyError(error);
        var message = error && error.message ? error.message : 'Unable to submit HOTP replay request.';
        showReplayError(message, error);
      })
      .finally(function () {
        replaySubmitButton.removeAttribute('disabled');
      });
  }

  function setActiveMode(mode) {
    if (!modeToggle) {
      return;
    }
    var previousMode = modeToggle.getAttribute('data-mode') || '';
    modeToggle.setAttribute('data-mode', mode);
    var storedActive = mode === 'stored';
    var storedRadio = modeToggle.querySelector('[data-testid="hotp-mode-select-stored"]');
    var inlineRadio = modeToggle.querySelector('[data-testid="hotp-mode-select-inline"]');

    if (storedRadio) {
      storedRadio.checked = storedActive;
    }
    if (inlineRadio) {
      inlineRadio.checked = !storedActive;
    }

    syncSeedControls(storedActive);

    if (storedEvaluationSection) {
      setHidden(storedEvaluationSection, !storedActive);
    }
    if (inlineEvaluationSection) {
      setHidden(inlineEvaluationSection, storedActive);
    }

    if (storedActive) {
      hideInlineError();
      setHidden(inlineResultPanel, true);
      ensureCredentials(false);
    } else {
      hideStoredError();
      setHidden(storedResultPanel, true);
    }
    if (previousMode !== mode) {
      clearVerboseTrace();
    }
  }

  function setExternalMode(mode, options) {
    var normalized = mode === 'stored' ? 'stored' : 'inline';
    var force = Boolean(options && options.force === true);
    var replaceHistory = Boolean(options && options.replace === true);
    initializeEvaluation();
    setActivePanel('evaluate', { skipUrlSync: true, replaceHistory: replaceHistory });
    if (!force && modeToggle && modeToggle.getAttribute('data-mode') === normalized) {
      return;
    }
    setActiveMode(normalized);
  }

  function handleSeedRequest() {
    if (seedInProgress) {
      return;
    }
    if (!storedForm) {
      setSeedStatus('Seeding endpoint unavailable.', 'error');
      return;
    }
    var endpoint = storedForm.getAttribute('data-seed-endpoint');
    if (!endpoint) {
      setSeedStatus('Seeding endpoint unavailable.', 'error');
      return;
    }

    seedInProgress = true;
    syncSeedControls(true);
    setSeedStatus('Seeding sample credentials…', null);

    fetchDelegate(endpoint, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfTokenFor(storedForm) || '',
      },
      credentials: 'same-origin',
    })
      .then(function (response) {
        return response.text().then(function (bodyText) {
          return { ok: response.ok, body: bodyText };
        });
      })
      .then(function (payload) {
        if (!payload.ok) {
          throw new Error('Seed request failed.');
        }
        var parsed = parseJson(payload.body) || {};
        var addedCount = 0;
        if (typeof parsed.addedCount === 'number') {
          addedCount = parsed.addedCount;
        } else if (Array.isArray(parsed.addedCredentialIds)) {
          addedCount = parsed.addedCredentialIds.length;
        }
        var message =
          'Seeded ' + addedCount + ' sample credential' + (addedCount === 1 ? '' : 's') + '.';
        if (addedCount === 0) {
          message += ' All sample credentials are already present.';
        }
        var severity = addedCount === 0 ? 'warning' : null;
        setSeedStatus(message, severity);
        credentialCache = null;
        credentialPromise = null;
        return ensureCredentials(true).then(function () {
          updateStoredSampleHints();
        });
      })
      .catch(function () {
        setSeedStatus('Unable to seed sample credentials.', 'error');
      })
      .finally(function () {
        seedInProgress = false;
        var storedActive = modeToggle ? modeToggle.getAttribute('data-mode') === 'stored' : true;
        syncSeedControls(storedActive);
      });
  }

  function handleStoredEvaluate() {
    if (!storedForm || !storedButton) {
      return;
    }
    var credentialId = storedSelect ? storedSelect.value : '';
    if (!credentialId) {
      showStoredError('Select a HOTP credential before evaluating.');
      return;
    }

    storedButton.setAttribute('disabled', 'disabled');
    hideStoredError();
    setHidden(storedResultPanel, true);

    var payload = verboseAttach({ credentialId: credentialId });
    verboseBeginRequest();

    fetchDelegate(storedForm.getAttribute('data-evaluate-endpoint') || '/api/v1/hotp/evaluate', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfTokenFor(storedForm) || '',
      },
      credentials: 'same-origin',
      body: JSON.stringify(payload),
    })
      .then(function (response) {
        return response.text().then(function (bodyText) {
          return { ok: response.ok, status: response.status, body: bodyText };
        });
      })
      .then(function (payload) {
        if (!payload.ok) {
          var parsed = parseJson(payload.body);
          var message =
            parsed && parsed.error && parsed.error.description
              ? parsed.error.description
              : 'Stored credential evaluation failed.';
          throw new Error(message);
        }
        var parsedBody = parseJson(payload.body) || {};
        renderResult(storedResultPanel, parsedBody);
        ensureCredentials(true);
      })
      .catch(function (error) {
        verboseApplyError(error);
        showStoredError(error && error.message ? error.message : 'Unable to evaluate credential.');
      })
      .finally(function () {
        storedButton.removeAttribute('disabled');
      });
  }

  function handleInlineEvaluate() {
    if (!inlineForm || !inlineButton) {
      return;
    }
    var secretPayload = {};
    var digits = inlineDigitsInput ? parseInt(inlineDigitsInput.value, 10) : NaN;
    var counter = inlineCounterInput ? parseInt(inlineCounterInput.value, 10) : NaN;

    if (inlineSecretController) {
      if (!inlineSecretController.validate()) {
        return;
      }
      secretPayload = inlineSecretController.payload() || {};
      if (Object.keys(secretPayload).length === 0) {
        showInlineError('Provide the HOTP shared secret (hex or Base32).');
        return;
      }
    } else {
      var secret = inlineSecretInput ? inlineSecretInput.value : '';
      if (!secret || secret.trim().length === 0) {
        showInlineError('Provide the HOTP shared secret (hex encoded).');
        return;
      }
      secretPayload.sharedSecretHex = secret.trim();
    }
    if (Number.isNaN(digits)) {
      showInlineError('Digits must be between 6 and 8.');
      return;
    }
    if (Number.isNaN(counter) || counter < 0) {
      showInlineError('Counter must be zero or greater.');
      return;
    }

    inlineButton.setAttribute('disabled', 'disabled');
    hideInlineError();
    setHidden(inlineResultPanel, true);

    var payload = Object.assign({}, secretPayload, {
      algorithm: inlineAlgorithmSelect ? inlineAlgorithmSelect.value : 'SHA1',
      digits: digits,
      counter: counter,
    });

    if (inlinePresetActiveKey) {
      var metadataPayload = { presetKey: inlinePresetActiveKey };
      if (inlinePresetActiveLabel) {
        metadataPayload.presetLabel = inlinePresetActiveLabel;
      }
      payload.metadata = metadataPayload;
    }

    payload = verboseAttach(payload);
    verboseBeginRequest();

    fetchDelegate(inlineForm.getAttribute('data-evaluate-endpoint') || '/api/v1/hotp/evaluate/inline', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfTokenFor(inlineForm) || '',
      },
      credentials: 'same-origin',
      body: JSON.stringify(payload),
    })
      .then(function (response) {
        return response.text().then(function (bodyText) {
          return { ok: response.ok, status: response.status, body: bodyText };
        });
      })
      .then(function (payload) {
        if (!payload.ok) {
          var parsed = parseJson(payload.body);
          var apiError = resolveApiErrorPayload(parsed, 'Inline HOTP evaluation failed.');
          var error = new Error(apiError.message);
          if (apiError.reason) {
            error.reason = apiError.reason;
          }
          throw error;
        }
        var parsedBody = parseJson(payload.body) || {};
        renderResult(inlineResultPanel, parsedBody);
      })
      .catch(function (error) {
        verboseApplyError(error);
        var message =
            error && error.message ? error.message : 'Unable to evaluate inline parameters.';
        var hint = error && typeof error.reason === 'string' && error.reason.trim().length > 0
            ? 'Reason: ' + error.reason.trim()
            : null;
        showInlineError(message, hint);
      })
      .finally(function () {
        inlineButton.removeAttribute('disabled');
      });
  }

  function attachEvaluationHandlers() {
    if (modeToggle) {
      modeToggle.addEventListener('change', function (event) {
        var target = event && event.target;
        if (!target || target.getAttribute('data-testid') == null) {
          return;
        }
        if (target.getAttribute('data-testid') === 'hotp-mode-select-stored') {
          setActiveMode('stored');
        }
        if (target.getAttribute('data-testid') === 'hotp-mode-select-inline') {
          setActiveMode('inline');
        }
      });
    }

    if (storedButton) {
      storedButton.addEventListener('click', function (event) {
        event.preventDefault();
        handleStoredEvaluate();
      });
    }
    if (seedButton) {
      seedButton.addEventListener('click', function (event) {
        event.preventDefault();
        handleSeedRequest();
      });
    }
    if (inlineButton) {
      inlineButton.addEventListener('click', function (event) {
        event.preventDefault();
        handleInlineEvaluate();
      });
    }
    if (inlinePresetSelect) {
      inlinePresetSelect.addEventListener('change', function (event) {
        var target = event && event.target ? event.target : inlinePresetSelect;
        var value = target.value || '';
        if (value) {
          applyInlineEvaluationPreset(value);
          var option = target.options && target.options[target.selectedIndex];
          var label = option && option.textContent ? option.textContent.trim() : '';
          setInlinePresetTracking(value, label);
        } else {
          clearInlinePresetTracking();
        }
      });
    }
    var manualInputs = [inlineSecretInput, inlineDigitsInput, inlineCounterInput];
    manualInputs.forEach(function (input) {
      if (input) {
        input.addEventListener('input', function () {
          clearInlinePresetTracking();
        });
      }
    });
    if (inlineAlgorithmSelect) {
      inlineAlgorithmSelect.addEventListener('change', function () {
        clearInlinePresetTracking();
      });
    }
  }

  function attachReplayHandlers() {
    if (!replayForm) {
      return;
    }
    if (replayModeToggle) {
      replayModeToggle.addEventListener('change', function (event) {
        var target = event && event.target;
        if (!target) {
          return;
        }
        if (target.getAttribute('data-testid') === 'hotp-replay-mode-select-stored') {
          setReplayMode('stored', { broadcast: true });
        }
        if (target.getAttribute('data-testid') === 'hotp-replay-mode-select-inline') {
          setReplayMode('inline', { broadcast: true });
        }
      });
    }
    if (replayStoredSelect) {
      replayStoredSelect.addEventListener('change', function () {
        updateStoredSampleHints();
      });
    }
    if (replayInlinePresetSelect) {
      replayInlinePresetSelect.addEventListener('change', function (event) {
        var value = event && event.target ? event.target.value : '';
        if (value) {
          applyInlinePreset(value);
        } else {
          clearInlinePresetTracking();
        }
      });
    }
    if (replaySubmitButton) {
      replaySubmitButton.addEventListener('click', function (event) {
        event.preventDefault();
        handleReplaySubmit();
      });
    }
  }

  function initializeEvaluation() {
    if (evaluationInitialized) {
      if (!modeToggle || modeToggle.getAttribute('data-mode') === 'stored') {
        ensureCredentials(true);
      }
      return;
    }
    attachEvaluationHandlers();
    renderInlinePresetOptions();
    if (modeToggle) {
      setActiveMode(modeToggle.getAttribute('data-mode') || 'inline');
    } else {
      ensureCredentials(false);
    }
    evaluationInitialized = true;
  }

  function initializeReplay() {
    if (!replayForm) {
      return;
    }
    initializeEvaluation();
    if (!replayInitialized) {
      attachReplayHandlers();
      var desiredReplayMode = 'inline';
      try {
        var searchParams = new global.URLSearchParams(global.location.search || '');
        var queryMode = searchParams.get('mode');
        if (queryMode === 'stored' || queryMode === 'inline') {
          desiredReplayMode = queryMode;
        } else if (hotpInitialReplayMode) {
          desiredReplayMode = hotpInitialReplayMode;
          hotpInitialReplayMode = null;
        } else if (modeToggle && modeToggle.getAttribute('data-mode') === 'stored') {
          desiredReplayMode = 'stored';
        }
      } catch (error) {
        desiredReplayMode = hotpInitialReplayMode || 'inline';
        hotpInitialReplayMode = null;
      }
      setReplayMode(desiredReplayMode, { broadcast: false, force: true });
      dispatchHotpReplayModeChange(desiredReplayMode, { replace: true, force: true });
      replayInitialized = true;
    } else {
      updateStoredSampleHints();
    }
    ensureCredentials(false);
  }

  function isHotpActive() {
    var params = new global.URLSearchParams(global.location.search);
    return params.get('protocol') === 'hotp';
  }

  if (evaluateTabButton) {
    evaluateTabButton.addEventListener('click', function (event) {
      event.preventDefault();
      setActivePanel('evaluate');
    });
  }

  if (replayTabButton) {
    replayTabButton.addEventListener('click', function (event) {
      event.preventDefault();
      setActivePanel('replay');
    });
  }

  documentRef.addEventListener('operator:protocol-activated', function (event) {
    if (!event || !event.detail) {
      return;
    }
    if (event.detail.protocol === 'hotp') {
      setActivePanel(parseHotpTabFromSearch(), { replaceHistory: true });
    }
  });

  global.addEventListener('popstate', function () {
    if (isHotpActive()) {
      var tab = 'evaluate';
      if (global.history && global.history.state && global.history.state.protocol === 'hotp') {
        tab = normalizeHotpTab(global.history.state.tab);
      } else {
        tab = parseHotpTabFromSearch();
      }
      setActivePanel(tab, { skipUrlSync: true });
    }
  });

  if (hotpPanel && isHotpActive()) {
    var initialTab = parseHotpTabFromSearch();
    setActivePanel(initialTab, { replaceHistory: true });
    if (initialTab === 'evaluate') {
      var initialMode = parseHotpModeFromSearch();
      setExternalMode(initialMode, { force: true, replace: true });
    } else {
      var initialReplayMode = parseHotpReplayModeFromSearch();
      setReplayMode(initialReplayMode, { force: true, broadcast: false });
    }
  }

  global.HotpConsole = {
    setMode: function (mode, options) {
      setExternalMode(mode, options || {});
    },
    setReplayMode: function (mode, options) {
      var opts = options ? Object.assign({}, options) : {};
      if (typeof opts.broadcast === 'undefined') {
        opts.broadcast = false;
      }
      setReplayMode(mode === 'stored' ? 'stored' : 'inline', opts);
    },
    getReplayMode: function () {
      return currentReplayMode();
    },
  };
})(window);
