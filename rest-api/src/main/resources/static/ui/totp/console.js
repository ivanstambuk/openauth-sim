(function (global) {
  'use strict';

  var documentRef = global.document;
  var totpPanel = documentRef.querySelector('[data-protocol-panel="totp"]');
  if (!totpPanel) {
    return;
  }

  var ALLOWED_TABS = ['evaluate', 'replay'];
  var ALLOWED_MODES = ['stored', 'inline'];

  var currentTab = 'evaluate';
  var currentMode = 'stored';
  var currentReplayMode = 'stored';
  var lastBroadcastTab = null;
  var lastBroadcastMode = null;
  var lastBroadcastReplayMode = null;

  var tabContainer = totpPanel.querySelector('[data-testid="totp-panel-tabs"]');
  var evaluateTabButton = totpPanel.querySelector('[data-testid="totp-panel-tab-evaluate"]');
  var replayTabButton = totpPanel.querySelector('[data-testid="totp-panel-tab-replay"]');
  var evaluatePanel = totpPanel.querySelector('[data-totp-panel="evaluate"]');
  var replayPanel = totpPanel.querySelector('[data-totp-panel="replay"]');

  var modeToggle = totpPanel.querySelector('[data-testid="totp-mode-toggle"]');
  var storedRadio = totpPanel.querySelector('[data-testid="totp-mode-select-stored"]');
  var inlineRadio = totpPanel.querySelector('[data-testid="totp-mode-select-inline"]');
  var storedSection = totpPanel.querySelector('[data-mode-section="stored"]');
  var inlineSection = totpPanel.querySelector('[data-mode-section="inline"]');

  var storedForm = totpPanel.querySelector('[data-testid="totp-stored-form"]');
  var storedButton = totpPanel.querySelector('[data-testid="totp-stored-evaluate-button"]');
  var storedSelect = totpPanel.querySelector('#totpStoredCredentialId');
  var storedStatusHint = totpPanel.querySelector('[data-testid="totp-stored-status"]');
  var replayStoredSelect = totpPanel.querySelector('#totpReplayStoredCredentialId');
  var replayStoredStatusHint =
      totpPanel.querySelector('[data-testid="totp-replay-stored-status"]');
  var replayStoredSampleActions =
      totpPanel.querySelector('[data-testid="totp-replay-sample-actions"]');
  var replayStoredSampleButton = replayStoredSampleActions
    ? replayStoredSampleActions.querySelector('[data-testid="totp-replay-sample-load"]')
    : null;
  var replayStoredSampleStatus = replayStoredSampleActions
    ? replayStoredSampleActions.querySelector('[data-testid="totp-replay-sample-status"]')
    : null;
  var credentialsEndpoint = storedForm
    ? storedForm.getAttribute('data-credentials-endpoint')
    : null;
  var seedEndpoint = storedForm ? storedForm.getAttribute('data-seed-endpoint') : null;
  var seedActions = totpPanel.querySelector('[data-testid="totp-seed-actions"]');
  var seedButton = seedActions
    ? seedActions.querySelector('[data-testid="totp-seed-credentials"]')
    : null;
  var seedStatus = seedActions
    ? seedActions.querySelector('[data-testid="totp-seed-status"]')
    : null;
  var seedDefinitionNode = totpPanel.querySelector('#totp-seed-definitions');
  var storedResultPanel = totpPanel.querySelector('[data-testid="totp-stored-result-panel"]');
  var storedStatusBadge = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid="totp-result-status"]')
    : null;
  var storedReasonCode = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid="totp-result-reason-code"]')
    : null;
  var storedSkew = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid="totp-result-skew"]')
    : null;
  var storedAlgorithm = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid="totp-result-algorithm"]')
    : null;
  var storedDigits = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid="totp-result-digits"]')
    : null;
  var storedStep = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid="totp-result-step"]')
    : null;
  var storedDriftBackward = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid="totp-result-drift-backward"]')
    : null;
  var storedDriftForward = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid="totp-result-drift-forward"]')
    : null;
  var storedTelemetry = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid="totp-result-telemetry"]')
    : null;
  var storedErrorPanel = totpPanel.querySelector('[data-testid="totp-stored-error-panel"]');
  var storedErrorReason = storedErrorPanel
    ? storedErrorPanel.querySelector('[data-testid="totp-stored-error-reason"]')
    : null;
  var storedErrorMessage = storedErrorPanel
    ? storedErrorPanel.querySelector('[data-testid="totp-stored-error-message"]')
    : null;

  var seedDefinitionsPayload = [];
  if (seedDefinitionNode && seedDefinitionNode.textContent) {
    var seedPayloadText = seedDefinitionNode.textContent.trim();
    if (seedPayloadText.length) {
      try {
        seedDefinitionsPayload = JSON.parse(seedPayloadText);
      } catch (error) {
        if (global.console && typeof global.console.error === 'function') {
          global.console.error('Failed to parse TOTP seed definitions', error);
        }
        seedDefinitionsPayload = [];
      }
    }
  }

  if (seedDefinitionNode && seedDefinitionNode.parentNode) {
    seedDefinitionNode.parentNode.removeChild(seedDefinitionNode);
  }

  var seedInProgress = false;
  var credentialCache = null;
  var credentialPromise = null;

  var inlineForm = totpPanel.querySelector('[data-testid="totp-inline-form"]');
  var inlineButton = totpPanel.querySelector('[data-testid="totp-inline-evaluate-button"]');
  var inlineResultPanel = totpPanel.querySelector('[data-testid="totp-inline-result-panel"]');
  var inlineStatusBadge = inlineResultPanel
    ? inlineResultPanel.querySelector('[data-testid="totp-inline-result-status"]')
    : null;
  var inlineReasonCode = inlineResultPanel
    ? inlineResultPanel.querySelector('[data-testid="totp-inline-result-reason-code"]')
    : null;
  var inlineTelemetry = inlineResultPanel
    ? inlineResultPanel.querySelector('[data-testid="totp-inline-result-telemetry"]')
    : null;
  var inlineErrorPanel = totpPanel.querySelector('[data-testid="totp-inline-error-panel"]');
  var inlineErrorReason = inlineErrorPanel
    ? inlineErrorPanel.querySelector('[data-testid="totp-inline-error-reason"]')
    : null;
  var inlineErrorMessage = inlineErrorPanel
    ? inlineErrorPanel.querySelector('[data-testid="totp-inline-error-message"]')
    : null;

  var inlinePresetContainer = totpPanel.querySelector('[data-testid="totp-inline-preset"]');
  var inlinePresetSelect = inlinePresetContainer
    ? inlinePresetContainer.querySelector('[data-testid="totp-inline-preset-select"]')
    : null;
  var inlinePresetDefinitionNode = totpPanel.querySelector('#totp-inline-presets');
  var inlinePresetPayload = [];
  if (inlinePresetDefinitionNode && inlinePresetDefinitionNode.textContent) {
    var presetText = inlinePresetDefinitionNode.textContent.trim();
    if (presetText.length) {
      try {
        inlinePresetPayload = JSON.parse(presetText);
      } catch (error) {
        if (global.console && typeof global.console.error === 'function') {
          global.console.error('Failed to parse TOTP inline presets', error);
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
      if (!preset || typeof preset.key !== 'string') {
        return;
      }
      inlinePresetData[preset.key] = preset;
    });
  }
  var inlinePresetActiveKey = '';
  var inlinePresetActiveLabel = '';
  var inlineSecretInput = totpPanel.querySelector('#totpInlineSecretHex');
  var inlineAlgorithmSelect = totpPanel.querySelector('#totpInlineAlgorithm');
  var inlineDigitsInput = totpPanel.querySelector('#totpInlineDigits');
  var inlineStepInput = totpPanel.querySelector('#totpInlineStepSeconds');
  var inlineOtpInput = totpPanel.querySelector('#totpInlineOtp');
  var inlineTimestampInput = totpPanel.querySelector('#totpInlineTimestamp');
  var inlineTimestampOverrideInput = totpPanel.querySelector('#totpInlineTimestampOverride');
  var inlineDriftBackwardInput = totpPanel.querySelector('#totpInlineDriftBackward');
  var inlineDriftForwardInput = totpPanel.querySelector('#totpInlineDriftForward');

  var replayModeToggle = totpPanel.querySelector('[data-testid="totp-replay-mode-toggle"]');
  var replayStoredRadio = totpPanel.querySelector('[data-testid="totp-replay-mode-select-stored"]');
  var replayInlineRadio = totpPanel.querySelector('[data-testid="totp-replay-mode-select-inline"]');
  var replayStoredSection = totpPanel.querySelector('[data-replay-section="stored"]');
  var replayInlineSection = totpPanel.querySelector('[data-replay-section="inline"]');

  var replayStoredForm = totpPanel.querySelector('[data-testid="totp-replay-stored-form"]');
  var replayInlineForm = totpPanel.querySelector('[data-testid="totp-replay-inline-form"]');
  var replayStoredButton = totpPanel.querySelector('[data-testid="totp-replay-stored-submit"]');
  var replayInlineButton = totpPanel.querySelector('[data-testid="totp-replay-inline-submit"]');
  var replayStoredOtpInput = totpPanel.querySelector('#totpReplayStoredOtp');
  var replayStoredTimestampInput = totpPanel.querySelector('#totpReplayStoredTimestamp');
  var replayStoredTimestampOverrideInput =
      totpPanel.querySelector('#totpReplayStoredTimestampOverride');
  var replayStoredDriftBackwardInput =
      totpPanel.querySelector('#totpReplayStoredDriftBackward');
  var replayStoredDriftForwardInput =
      totpPanel.querySelector('#totpReplayStoredDriftForward');
  var replayResultPanel = totpPanel.querySelector('[data-testid="totp-replay-result-panel"]');
  var replayStatusBadge = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-status"]')
    : null;
  var replayReasonCode = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-reason-code"]')
    : null;
  var replayOutcome = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-outcome"]')
    : null;
  var replayErrorPanel = totpPanel.querySelector('[data-testid="totp-replay-error-panel"]');
  var replayErrorReason = replayErrorPanel
    ? replayErrorPanel.querySelector('[data-testid="totp-replay-error-reason"]')
    : null;
  var replayErrorMessage = replayErrorPanel
    ? replayErrorPanel.querySelector('[data-testid="totp-replay-error-message"]')
    : null;
  var replayInlinePresetContainer =
      totpPanel.querySelector('[data-testid="totp-replay-inline-preset"]');
  var replayInlinePresetSelect = replayInlinePresetContainer
    ? replayInlinePresetContainer.querySelector('[data-testid="totp-replay-inline-preset-select"]')
    : null;
  var sampleEndpointBase = replayStoredForm
    ? replayStoredForm.getAttribute('data-sample-endpoint')
    : null;
  var replayInlineSecretInput = totpPanel.querySelector('#totpReplayInlineSecretHex');
  var replayInlineAlgorithmSelect = totpPanel.querySelector('#totpReplayInlineAlgorithm');
  var replayInlineDigitsInput = totpPanel.querySelector('#totpReplayInlineDigits');
  var replayInlineStepInput = totpPanel.querySelector('#totpReplayInlineStepSeconds');
  var replayInlineOtpInput = totpPanel.querySelector('#totpReplayInlineOtp');
  var replayInlineTimestampInput = totpPanel.querySelector('#totpReplayInlineTimestamp');
  var replayInlineTimestampOverrideInput =
      totpPanel.querySelector('#totpReplayInlineTimestampOverride');
  var replayInlineDriftBackwardInput = totpPanel.querySelector('#totpReplayInlineDriftBackward');
  var replayInlineDriftForwardInput = totpPanel.querySelector('#totpReplayInlineDriftForward');

  function setHidden(node, hidden) {
    if (!node) {
      return;
    }
    if (hidden) {
      node.setAttribute('hidden', 'hidden');
      node.setAttribute('aria-hidden', 'true');
    } else {
      node.removeAttribute('hidden');
      node.removeAttribute('aria-hidden');
    }
  }

  function writeText(node, value) {
    if (!node) {
      return;
    }
    node.textContent = value == null ? '—' : String(value);
  }

  function setPresetApplied(container, key) {
    if (!container) {
      return;
    }
    if (key) {
      container.setAttribute('data-preset-applied', key);
    } else {
      container.removeAttribute('data-preset-applied');
    }
  }

  function setInlinePresetTracking(key, label) {
    inlinePresetActiveKey = key || '';
    inlinePresetActiveLabel = label ? label.trim() : '';
    setPresetApplied(inlinePresetContainer, inlinePresetActiveKey);
  }

  function clearInlinePresetTracking() {
    setInlinePresetTracking('', '');
    if (inlinePresetSelect && inlinePresetSelect.value) {
      inlinePresetSelect.value = '';
    }
  }

  function applyInlineEvaluationPreset(presetKey) {
    var preset = inlinePresetData[presetKey];
    if (!preset) {
      clearInlinePresetTracking();
      return;
    }
    if (inlinePresetSelect && inlinePresetSelect.value !== presetKey) {
      inlinePresetSelect.value = presetKey;
    }
    if (inlineSecretInput) {
      inlineSecretInput.value = preset.sharedSecretHex || '';
    }
    if (inlineAlgorithmSelect && preset.algorithm) {
      inlineAlgorithmSelect.value = preset.algorithm;
    }
    if (inlineDigitsInput) {
      inlineDigitsInput.value =
          typeof preset.digits === 'number' ? String(preset.digits) : '';
    }
    if (inlineStepInput) {
      inlineStepInput.value =
          typeof preset.stepSeconds === 'number' ? String(preset.stepSeconds) : '';
    }
    if (inlineOtpInput) {
      inlineOtpInput.value = preset.otp || '';
    }
    if (inlineTimestampInput) {
      inlineTimestampInput.value =
          typeof preset.timestamp === 'number' ? String(preset.timestamp) : '';
    }
    if (inlineTimestampOverrideInput) {
      inlineTimestampOverrideInput.value = '';
    }
    if (inlineDriftBackwardInput) {
      inlineDriftBackwardInput.value =
          typeof preset.driftBackwardSteps === 'number'
              ? String(preset.driftBackwardSteps)
              : '';
    }
    if (inlineDriftForwardInput) {
      inlineDriftForwardInput.value =
          typeof preset.driftForwardSteps === 'number'
              ? String(preset.driftForwardSteps)
              : '';
    }
    setInlinePresetTracking(presetKey, preset.label || preset.key || presetKey);
    clearInlinePanels();
  }

  function applyReplayInlinePreset(presetKey) {
    var preset = inlinePresetData[presetKey];
    if (!preset) {
      setPresetApplied(replayInlinePresetContainer, '');
      return;
    }
    if (replayInlinePresetSelect && replayInlinePresetSelect.value !== presetKey) {
      replayInlinePresetSelect.value = presetKey;
    }
    if (replayInlineSecretInput) {
      replayInlineSecretInput.value = preset.sharedSecretHex || '';
    }
    if (replayInlineAlgorithmSelect && preset.algorithm) {
      replayInlineAlgorithmSelect.value = preset.algorithm;
    }
    if (replayInlineDigitsInput) {
      replayInlineDigitsInput.value =
          typeof preset.digits === 'number' ? String(preset.digits) : '';
    }
    if (replayInlineStepInput) {
      replayInlineStepInput.value =
          typeof preset.stepSeconds === 'number' ? String(preset.stepSeconds) : '';
    }
    if (replayInlineOtpInput) {
      replayInlineOtpInput.value = preset.otp || '';
    }
    if (replayInlineTimestampInput) {
      replayInlineTimestampInput.value =
          typeof preset.timestamp === 'number' ? String(preset.timestamp) : '';
    }
    if (replayInlineTimestampOverrideInput) {
      replayInlineTimestampOverrideInput.value = '';
    }
    if (replayInlineDriftBackwardInput) {
      replayInlineDriftBackwardInput.value =
          typeof preset.driftBackwardSteps === 'number'
              ? String(preset.driftBackwardSteps)
              : '';
    }
    if (replayInlineDriftForwardInput) {
      replayInlineDriftForwardInput.value =
          typeof preset.driftForwardSteps === 'number'
              ? String(preset.driftForwardSteps)
              : '';
    }
    setPresetApplied(replayInlinePresetContainer, presetKey);
    clearReplayPanels();
  }

  function renderInlinePresetOptions() {
    var keys = Object.keys(inlinePresetData);
    keys.sort(function (a, b) {
      var presetA = inlinePresetData[a] || {};
      var presetB = inlinePresetData[b] || {};
      var labelA = presetA.label || a;
      var labelB = presetB.label || b;
      return labelA.localeCompare(labelB, undefined, { sensitivity: 'base' });
    });

    function populate(selectNode) {
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
      keys.forEach(function (key) {
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

    populate(inlinePresetSelect);
    populate(replayInlinePresetSelect);
    if (inlinePresetContainer) {
      setHidden(inlinePresetContainer, keys.length === 0);
    }
    if (replayInlinePresetContainer) {
      setHidden(replayInlinePresetContainer, keys.length === 0);
    }
  }

  renderInlinePresetOptions();

  function setSeedStatus(message, severity) {
    if (!seedStatus) {
      return;
    }
    seedStatus.classList.remove('credential-status--warning', 'credential-status--error');
    if (!message) {
      seedStatus.textContent = '';
      setHidden(seedStatus, true);
      return;
    }
    seedStatus.textContent = message;
    if (severity === 'error') {
      seedStatus.classList.add('credential-status--error');
    } else if (severity === 'warning') {
      seedStatus.classList.add('credential-status--warning');
    }
    setHidden(seedStatus, false);
  }

  function syncSeedActions(storedActive) {
    if (!seedActions) {
      return;
    }
    var shouldShow = storedActive && !seedInProgress;
    setHidden(seedActions, !shouldShow);
    if (seedButton) {
      if (seedInProgress) {
        seedButton.setAttribute('disabled', 'disabled');
      } else {
        seedButton.removeAttribute('disabled');
      }
    }
    if (!storedActive) {
      setSeedStatus('', null);
    }
  }

  function updateStoredStatusMessage(count) {
    if (storedStatusHint) {
      storedStatusHint.textContent = count
          ? 'Select a stored credential before evaluating.'
          : 'No stored credentials available.';
    }
    if (replayStoredStatusHint) {
      replayStoredStatusHint.textContent = count
          ? 'Select a stored credential before replaying.'
          : 'No stored credentials available.';
    }
  }

  function setReplaySampleStatus(message, severity) {
    if (!replayStoredSampleStatus) {
      return;
    }
    replayStoredSampleStatus.classList.remove(
        'credential-status--warning', 'credential-status--error');
    if (!message) {
      replayStoredSampleStatus.textContent = '';
      setHidden(replayStoredSampleStatus, true);
      return;
    }
    replayStoredSampleStatus.textContent = message;
    if (severity === 'error') {
      replayStoredSampleStatus.classList.add('credential-status--error');
    } else if (severity === 'warning') {
      replayStoredSampleStatus.classList.add('credential-status--warning');
    }
    setHidden(replayStoredSampleStatus, false);
  }

  function storedReplayModeActive() {
    return !replayModeToggle || replayModeToggle.getAttribute('data-mode') === 'stored';
  }

  function syncReplaySampleActions() {
    if (!replayStoredSampleActions) {
      return;
    }
    var hasCredential =
        replayStoredSelect && typeof replayStoredSelect.value === 'string'
            ? replayStoredSelect.value.trim().length > 0
            : false;
    var shouldShow = hasCredential && storedReplayModeActive();
    setHidden(replayStoredSampleActions, !shouldShow);
    if (replayStoredSampleButton) {
      if (shouldShow) {
        replayStoredSampleButton.removeAttribute('disabled');
        replayStoredSampleButton.removeAttribute('aria-disabled');
      } else {
        replayStoredSampleButton.setAttribute('disabled', 'disabled');
        replayStoredSampleButton.setAttribute('aria-disabled', 'true');
      }
    }
    if (!shouldShow) {
      setReplaySampleStatus('', null);
    } else if (
      replayStoredSampleStatus &&
      (!replayStoredSampleStatus.textContent ||
        replayStoredSampleStatus.textContent.trim().length === 0)
    ) {
      setReplaySampleStatus('Apply sample data for this credential.', null);
    }
  }

  function storedSampleEndpoint(credentialId) {
    if (!sampleEndpointBase || !credentialId) {
      return null;
    }
    var base = sampleEndpointBase;
    if (base.endsWith('/')) {
      base = base.slice(0, -1);
    }
    return base + '/' + encodeURIComponent(credentialId) + '/sample';
  }

  function renderStoredOptions(select, credentials) {
    if (!select) {
      return;
    }
    var previousValue = select.value || '';
    while (select.firstChild) {
      select.removeChild(select.firstChild);
    }

    var placeholder = documentRef.createElement('option');
    placeholder.value = '';
    if (!credentials || credentials.length === 0) {
      placeholder.textContent = 'No stored credentials available';
      select.appendChild(placeholder);
      select.value = '';
      select.setAttribute('disabled', 'disabled');
      return;
    }

    placeholder.textContent = 'Select a credential';
    select.appendChild(placeholder);
    select.removeAttribute('disabled');

    credentials.forEach(function (summary) {
      if (!summary || typeof summary.id !== 'string' || !summary.id) {
        return;
      }
      var option = documentRef.createElement('option');
      option.value = summary.id;
      option.textContent = summary.label || summary.id;
      if (summary.algorithm) {
        option.setAttribute('data-algorithm', summary.algorithm);
      }
      if (typeof summary.digits === 'number') {
        option.setAttribute('data-digits', String(summary.digits));
      }
      if (typeof summary.stepSeconds === 'number') {
        option.setAttribute('data-step-seconds', String(summary.stepSeconds));
      }
      select.appendChild(option);
    });

    if (previousValue && credentials.some(function (summary) {
      return summary && summary.id === previousValue;
    })) {
      select.value = previousValue;
    } else {
      select.value = '';
    }
    if (select === replayStoredSelect) {
      syncReplaySampleActions();
    }
  }

  function populateStoredSelect(credentials) {
    renderStoredOptions(storedSelect, credentials);
    renderStoredOptions(replayStoredSelect, credentials);
    updateStoredStatusMessage(Array.isArray(credentials) ? credentials.length : 0);
  }

  function fetchStoredCredentials() {
    if (!credentialsEndpoint) {
      return Promise.resolve([]);
    }
    return getJson(credentialsEndpoint)
      .then(function (payload) {
        if (!Array.isArray(payload)) {
          return [];
        }
        return payload
          .map(function (summary) {
            if (!summary || typeof summary.id !== 'string') {
              return null;
            }
            return {
              id: summary.id,
              label: typeof summary.label === 'string' && summary.label.length
                ? summary.label
                : summary.id,
              algorithm: typeof summary.algorithm === 'string' ? summary.algorithm : '',
              digits: typeof summary.digits === 'number' ? summary.digits : null,
              stepSeconds: typeof summary.stepSeconds === 'number'
                ? summary.stepSeconds
                : null,
            };
          })
          .filter(function (entry) {
            return entry && entry.id;
          });
      })
      .catch(function (error) {
        if (global.console && typeof global.console.warn === 'function') {
          global.console.warn('Unable to load TOTP credentials', error);
        }
        throw error;
      });
  }

  function ensureStoredCredentials(force) {
    if (!storedSelect || !credentialsEndpoint) {
      return Promise.resolve([]);
    }
    if (force === true) {
      credentialCache = null;
      credentialPromise = null;
    }
    if (credentialPromise) {
      return credentialPromise;
    }
    if (credentialCache) {
      populateStoredSelect(credentialCache);
      return Promise.resolve(credentialCache);
    }
    credentialPromise = fetchStoredCredentials()
      .then(function (list) {
        credentialCache = list;
        populateStoredSelect(list);
        credentialPromise = null;
        return list;
      })
      .catch(function (error) {
        credentialCache = [];
        populateStoredSelect(credentialCache);
        credentialPromise = null;
        return [];
      });
    return credentialPromise;
  }

  function applyReplayStoredSample() {
    if (!replayStoredForm || !replayStoredSelect) {
      return;
    }
    var credentialId = (replayStoredSelect.value || '').trim();
    if (!credentialId) {
      setReplaySampleStatus('', null);
      return;
    }
    var endpoint = storedSampleEndpoint(credentialId);
    if (!endpoint) {
      return;
    }
    if (replayStoredSampleButton) {
      replayStoredSampleButton.setAttribute('disabled', 'disabled');
      replayStoredSampleButton.setAttribute('aria-disabled', 'true');
    }
    setReplaySampleStatus('Loading sample data…', null);
    getJson(endpoint)
      .then(function (payload) {
        if (!payload || typeof payload !== 'object') {
          setReplaySampleStatus('Sample data is not available.', 'warning');
          return;
        }
        if (replayStoredOtpInput) {
          replayStoredOtpInput.value =
              typeof payload.otp === 'string' ? payload.otp : '';
        }
        if (replayStoredTimestampInput) {
          if (typeof payload.timestamp === 'number') {
            replayStoredTimestampInput.value = String(payload.timestamp);
          } else if (typeof payload.timestamp === 'string') {
            replayStoredTimestampInput.value = payload.timestamp.trim();
          }
        }
        if (replayStoredTimestampOverrideInput) {
          replayStoredTimestampOverrideInput.value = '';
        }
        if (replayStoredDriftBackwardInput) {
          if (typeof payload.driftBackward === 'number') {
            replayStoredDriftBackwardInput.value = String(payload.driftBackward);
          }
        }
        if (replayStoredDriftForwardInput) {
          if (typeof payload.driftForward === 'number') {
            replayStoredDriftForwardInput.value = String(payload.driftForward);
          }
        }
        var metadata = payload.metadata && typeof payload.metadata === 'object'
          ? payload.metadata
          : {};
        var notes = metadata.notes && typeof metadata.notes === 'string'
          ? metadata.notes.trim()
          : '';
        var presetLabel = metadata.samplePresetLabel && typeof metadata.samplePresetLabel === 'string'
          ? metadata.samplePresetLabel.trim()
          : '';
        var message;
        if (notes) {
          message = 'Sample: ' + notes;
        } else if (presetLabel) {
          message = 'Sample "' + presetLabel + '" applied.';
        } else {
          message = 'Sample data applied.';
        }
        setReplaySampleStatus(message, null);
      })
      .catch(function (error) {
        if (error && (error.status === 404 || error.status === 400)) {
          setReplaySampleStatus('Sample data is not available for this credential.', 'warning');
        } else {
          setReplaySampleStatus('Unable to load sample data.', 'error');
        }
      })
      .finally(function () {
        if (replayStoredSampleButton) {
          replayStoredSampleButton.removeAttribute('disabled');
          replayStoredSampleButton.removeAttribute('aria-disabled');
        }
      });
  }

  function handleSeedRequest() {
    if (seedInProgress || !seedEndpoint) {
      return;
    }
    seedInProgress = true;
    syncSeedActions(true);
    setSeedStatus('Seeding sample credentials…', null);

    postJson(seedEndpoint, {}, csrfToken(storedForm))
      .then(function (response) {
        var addedCount = 0;
        if (response && typeof response.addedCount === 'number') {
          addedCount = response.addedCount;
        } else if (response && Array.isArray(response.addedCredentialIds)) {
          addedCount = response.addedCredentialIds.length;
        }
        var message =
          'Seeded ' + addedCount + ' sample credential' + (addedCount === 1 ? '' : 's') + '.';
        var severity = addedCount === 0 ? 'warning' : null;
        if (addedCount === 0) {
          message += ' All sample credentials are already present.';
        }
        setSeedStatus(message, severity);
        credentialCache = null;
        credentialPromise = null;
        return ensureStoredCredentials(true);
      })
      .catch(function () {
        setSeedStatus('Unable to seed sample credentials.', 'error');
      })
      .finally(function () {
        seedInProgress = false;
        var storedActive = modeToggle ? modeToggle.getAttribute('data-mode') === 'stored' : true;
        syncSeedActions(storedActive);
      });
  }

  function toInteger(value) {
    if (value == null) {
      return null;
    }
    var trimmed = String(value).trim();
    if (!trimmed) {
      return null;
    }
    var parsed = Number.parseInt(trimmed, 10);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function csrfToken(form) {
    if (!form) {
      return null;
    }
    var tokenField = form.querySelector('input[name="_csrf"]');
    return tokenField && tokenField.value ? tokenField.value : null;
  }

  function parseJson(bodyText) {
    if (!bodyText) {
      return null;
    }
    try {
      return JSON.parse(bodyText);
    } catch (error) {
      return null;
    }
  }

  function getJson(endpoint) {
    if (!endpoint) {
      return Promise.resolve(null);
    }

    if (typeof global.fetch === 'function') {
      return global
        .fetch(endpoint, {
          method: 'GET',
          credentials: 'same-origin',
          headers: {
            Accept: 'application/json',
          },
        })
        .then(function (response) {
          return response.text().then(function (bodyText) {
            if (response.ok) {
              return parseJson(bodyText);
            }
            var error = new Error('TOTP request failed');
            error.status = response.status;
            error.payload = parseJson(bodyText);
            throw error;
          });
        });
    }

    return new Promise(function (resolve, reject) {
      var XhrCtor = global.XMLHttpRequest;
      if (typeof XhrCtor !== 'function') {
        resolve(null);
        return;
      }
      try {
        var xhr = new XhrCtor();
        xhr.open('GET', endpoint, true);
        xhr.withCredentials = true;
        xhr.setRequestHeader('Accept', 'application/json');
        var DONE = typeof XhrCtor.DONE === 'number' ? XhrCtor.DONE : 4;
        xhr.onreadystatechange = function () {
          if (xhr.readyState !== DONE) {
            return;
          }
          var parsed = parseJson(xhr.responseText);
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(parsed);
            return;
          }
          var error = new Error('TOTP request failed');
          error.status = xhr.status;
          error.payload = parsed;
          reject(error);
        };
        xhr.onerror = function () {
          reject(new Error('Network error during TOTP request'));
        };
        xhr.send();
      } catch (error) {
        reject(error);
      }
    });
  }

  function postJson(endpoint, payload, csrf) {
    if (!endpoint) {
      return Promise.reject(new Error('Missing TOTP endpoint'));
    }
    var requestBody;
    try {
      requestBody = JSON.stringify(payload);
    } catch (error) {
      return Promise.reject(error);
    }

    if (typeof global.fetch === 'function') {
      var headers = {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      };
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
            var parsed = parseJson(bodyText);
            if (response.ok) {
              return parsed;
            }
            var error = new Error('TOTP request failed');
            error.status = response.status;
            error.payload = parsed;
            throw error;
          });
        });
    }

    return new Promise(function (resolve, reject) {
      var XhrCtor = global.XMLHttpRequest;
      if (typeof XhrCtor !== 'function') {
        reject(new Error('Browser does not support XMLHttpRequest'));
        return;
      }
      try {
        var xhr = new XhrCtor();
        xhr.open('POST', endpoint, true);
        xhr.withCredentials = true;
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.setRequestHeader('Content-Type', 'application/json');
        if (csrf) {
          xhr.setRequestHeader('X-CSRF-TOKEN', csrf);
        }
        var DONE = typeof XhrCtor.DONE === 'number' ? XhrCtor.DONE : 4;
        xhr.onreadystatechange = function () {
          if (xhr.readyState !== DONE) {
            return;
          }
          var parsed = parseJson(xhr.responseText);
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(parsed);
            return;
          }
          var error = new Error('TOTP request failed');
          error.status = xhr.status;
          error.payload = parsed;
          reject(error);
        };
        xhr.onerror = function () {
          reject(new Error('Network error during TOTP request'));
        };
        xhr.send(requestBody);
      } catch (error) {
        reject(error);
      }
    });
  }

  function normalizeTab(tab) {
    if (ALLOWED_TABS.indexOf(tab) >= 0) {
      return tab;
    }
    return 'evaluate';
  }

  function normalizeMode(mode) {
    if (ALLOWED_MODES.indexOf(mode) >= 0) {
      return mode;
    }
    return 'stored';
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
      global.dispatchEvent(new global.CustomEvent('operator:totp-tab-changed', { detail: detail }));
    } catch (error) {
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to broadcast TOTP tab change', error);
      }
    }
  }

  function dispatchModeChange(mode, options) {
    if (options && options.broadcast === false) {
      return;
    }
    if (lastBroadcastMode === mode && !(options && options.force)) {
      return;
    }
    lastBroadcastMode = mode;
    try {
      var detail = { mode: mode };
      if (options && options.replace === true) {
        detail.replace = true;
      }
      global.dispatchEvent(new global.CustomEvent('operator:totp-mode-changed', { detail: detail }));
    } catch (error) {
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to broadcast TOTP mode change', error);
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
          new global.CustomEvent('operator:totp-replay-mode-changed', { detail: detail }));
    } catch (error) {
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to broadcast TOTP replay mode change', error);
      }
    }
  }

  function setTab(tab, options) {
    var normalized = normalizeTab(tab);
    var force = options && options.force === true;
    if (!force && currentTab === normalized) {
      return;
    }
    currentTab = normalized;

    if (evaluateTabButton) {
      evaluateTabButton.classList.toggle('mode-pill--active', normalized === 'evaluate');
      evaluateTabButton.setAttribute('aria-selected', normalized === 'evaluate' ? 'true' : 'false');
    }
    if (replayTabButton) {
      replayTabButton.classList.toggle('mode-pill--active', normalized === 'replay');
      replayTabButton.setAttribute('aria-selected', normalized === 'replay' ? 'true' : 'false');
    }
    setHidden(evaluatePanel, normalized !== 'evaluate');
    setHidden(replayPanel, normalized !== 'replay');

    if (normalized === 'evaluate') {
      setMode(currentMode, { broadcast: false, force: true });
    } else if (normalized === 'replay') {
      setReplayMode(currentReplayMode, { broadcast: false, force: true });
    }

    dispatchTabChange(normalized, options);
  }

  function setMode(mode, options) {
    var normalized = normalizeMode(mode);
    var force = options && options.force === true;
    if (!force && currentMode === normalized) {
      return;
    }
    currentMode = normalized;
    if (modeToggle) {
      modeToggle.setAttribute('data-mode', normalized);
    }
    if (storedRadio) {
      storedRadio.checked = normalized === 'stored';
    }
    if (inlineRadio) {
      inlineRadio.checked = normalized === 'inline';
    }
    setHidden(storedSection, normalized !== 'stored');
    setHidden(inlineSection, normalized !== 'inline');
    syncSeedActions(normalized === 'stored');
    if (normalized === 'stored') {
      ensureStoredCredentials(options && options.force === true).catch(function () {
        // ignore load errors; status message already updated
      });
    }
    dispatchModeChange(normalized, options);
  }

  function setReplayMode(mode, options) {
    var normalized = normalizeMode(mode);
    var force = options && options.force === true;
    if (!force && currentReplayMode === normalized) {
      return;
    }
    currentReplayMode = normalized;
    if (replayModeToggle) {
      replayModeToggle.setAttribute('data-mode', normalized);
    }
    if (replayStoredRadio) {
      replayStoredRadio.checked = normalized === 'stored';
    }
    if (replayInlineRadio) {
      replayInlineRadio.checked = normalized === 'inline';
    }
    setHidden(replayStoredSection, normalized !== 'stored');
    setHidden(replayInlineSection, normalized !== 'inline');
    syncReplaySampleActions();
    if (normalized === 'stored') {
      ensureStoredCredentials(options && options.force === true).catch(function () {
        // ignore load errors; status message already updated
      });
    }
    dispatchReplayModeChange(normalized, options);
  }

  function clearStoredPanels() {
    setHidden(storedResultPanel, true);
    setHidden(storedErrorPanel, true);
  }

  function clearInlinePanels() {
    setHidden(inlineResultPanel, true);
    setHidden(inlineErrorPanel, true);
  }

  function clearReplayPanels() {
    setHidden(replayResultPanel, true);
    setHidden(replayErrorPanel, true);
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
        normalized === 'mismatch';
    badge.textContent = status ? String(status) : 'Unknown';
    badge.classList.remove('status-badge--success', 'status-badge--error');
    if (isSuccess) {
      badge.classList.add('status-badge--success');
    } else if (isInvalid) {
      badge.classList.add('status-badge--error');
    }
  }

  function handleStoredSuccess(response) {
    clearStoredPanels();
    if (!response || typeof response !== 'object') {
      return;
    }
    var metadata = response.metadata || {};
    setStatusBadge(storedStatusBadge, response.status || response.reasonCode);
    writeText(storedReasonCode, response.reasonCode || response.status || '—');
    writeText(storedSkew, metadata.matchedSkewSteps);
    writeText(storedAlgorithm, metadata.algorithm);
    writeText(storedDigits, metadata.digits);
    writeText(storedStep, metadata.stepSeconds);
    writeText(storedDriftBackward, metadata.driftBackwardSteps);
    writeText(storedDriftForward, metadata.driftForwardSteps);
    writeText(storedTelemetry, metadata.telemetryId);
    setHidden(storedResultPanel, false);
  }

  function handleStoredError(error) {
    clearStoredPanels();
    if (!storedErrorPanel) {
      return;
    }
    var reason = 'unexpected_error';
    var message = 'An unexpected error occurred during evaluation.';
    if (error && error.payload) {
      if (error.payload.reasonCode) {
        reason = error.payload.reasonCode;
      }
      if (error.payload.message) {
        message = error.payload.message;
      }
    } else if (error && error.status) {
      message = 'Evaluation failed with status ' + error.status + '.';
    }
    writeText(storedErrorReason, reason);
    writeText(storedErrorMessage, message);
    setHidden(storedErrorPanel, false);
  }

  function handleInlineSuccess(response) {
    clearInlinePanels();
    if (!response || typeof response !== 'object') {
      return;
    }
    setStatusBadge(inlineStatusBadge, response.status || response.reasonCode);
    writeText(inlineReasonCode, response.reasonCode || response.status || '—');
    writeText(inlineTelemetry, response.metadata && response.metadata.telemetryId);
    setHidden(inlineResultPanel, false);
  }

  function handleInlineError(error) {
    clearInlinePanels();
    if (!inlineErrorPanel) {
      return;
    }
    var reason = 'unexpected_error';
    var message = 'Inline evaluation failed unexpectedly.';
    if (error && error.payload) {
      if (error.payload.reasonCode) {
        reason = error.payload.reasonCode;
      }
      if (error.payload.message) {
        message = error.payload.message;
      }
    } else if (error && error.status) {
      message = 'Inline evaluation failed with status ' + error.status + '.';
    }
    writeText(inlineErrorReason, reason);
    writeText(inlineErrorMessage, message);
    setHidden(inlineErrorPanel, false);
  }

  function handleReplaySuccess(response) {
    clearReplayPanels();
    if (!response || typeof response !== 'object') {
      return;
    }
    setStatusBadge(replayStatusBadge, response.status || response.reasonCode);
    writeText(replayReasonCode, response.reasonCode || response.status || '—');
    writeText(replayOutcome, response.status || '—');
    setHidden(replayResultPanel, false);
  }

  function handleReplayError(error) {
    clearReplayPanels();
    if (!replayErrorPanel) {
      return;
    }
    var reason = 'unexpected_error';
    var message = 'TOTP replay failed unexpectedly.';
    if (error && error.payload) {
      if (error.payload.reasonCode) {
        reason = error.payload.reasonCode;
      }
      if (error.payload.message) {
        message = error.payload.message;
      }
    } else if (error && error.status) {
      message = 'Replay failed with status ' + error.status + '.';
    }
    writeText(replayErrorReason, reason);
    writeText(replayErrorMessage, message);
    setHidden(replayErrorPanel, false);
  }

  function evaluateStored() {
    if (!storedForm || !storedButton) {
      return;
    }
    var endpoint = storedForm.getAttribute('data-evaluate-endpoint');
    var payload = {
      credentialId: valueOf('#totpStoredCredentialId'),
      otp: valueOf('#totpStoredOtp'),
    };
    var timestamp = toInteger(valueOf('#totpStoredTimestamp'));
    var timestampOverride = toInteger(valueOf('#totpStoredTimestampOverride'));
    var driftBackward = toInteger(valueOf('#totpStoredDriftBackward'));
    var driftForward = toInteger(valueOf('#totpStoredDriftForward'));
    if (timestamp != null) {
      payload.timestamp = timestamp;
    }
    if (timestampOverride != null) {
      payload.timestampOverride = timestampOverride;
    }
    if (driftBackward != null) {
      payload.driftBackward = driftBackward;
    }
    if (driftForward != null) {
      payload.driftForward = driftForward;
    }
    storedButton.setAttribute('disabled', 'disabled');
    clearStoredPanels();
    postJson(endpoint, payload, csrfToken(storedForm))
      .then(handleStoredSuccess)
      .catch(handleStoredError)
      .finally(function () {
        storedButton.removeAttribute('disabled');
      });
  }

  function evaluateInline() {
    if (!inlineForm || !inlineButton) {
      return;
    }
    var endpoint = inlineForm.getAttribute('data-evaluate-endpoint');
    var payload = {
      sharedSecretHex: valueOf('#totpInlineSecretHex'),
      algorithm: valueOf('#totpInlineAlgorithm'),
      otp: valueOf('#totpInlineOtp'),
    };
    var digits = toInteger(valueOf('#totpInlineDigits'));
    var stepSeconds = toInteger(valueOf('#totpInlineStepSeconds'));
    var driftBackward = toInteger(valueOf('#totpInlineDriftBackward'));
    var driftForward = toInteger(valueOf('#totpInlineDriftForward'));
    var timestamp = toInteger(valueOf('#totpInlineTimestamp'));
    var timestampOverride = toInteger(valueOf('#totpInlineTimestampOverride'));
    if (digits != null) {
      payload.digits = digits;
    }
    if (stepSeconds != null) {
      payload.stepSeconds = stepSeconds;
    }
    if (driftBackward != null) {
      payload.driftBackward = driftBackward;
    }
    if (driftForward != null) {
      payload.driftForward = driftForward;
    }
    if (timestamp != null) {
      payload.timestamp = timestamp;
    }
    if (timestampOverride != null) {
      payload.timestampOverride = timestampOverride;
    }
    if (inlinePresetActiveKey) {
      var metadata = { presetKey: inlinePresetActiveKey };
      if (inlinePresetActiveLabel) {
        metadata.presetLabel = inlinePresetActiveLabel;
      }
      payload.metadata = metadata;
    }
    inlineButton.setAttribute('disabled', 'disabled');
    clearInlinePanels();
    postJson(endpoint, payload, csrfToken(inlineForm))
      .then(handleInlineSuccess)
      .catch(handleInlineError)
      .finally(function () {
        inlineButton.removeAttribute('disabled');
      });
  }

  function replayStored() {
    if (!replayStoredForm || !replayStoredButton) {
      return;
    }
    var endpoint = replayStoredForm.getAttribute('data-replay-endpoint');
    var payload = {
      credentialId: valueOf('#totpReplayStoredCredentialId'),
      otp: valueOf('#totpReplayStoredOtp'),
    };
    var timestamp = toInteger(valueOf('#totpReplayStoredTimestamp'));
    var timestampOverride = toInteger(valueOf('#totpReplayStoredTimestampOverride'));
    var driftBackward = toInteger(valueOf('#totpReplayStoredDriftBackward'));
    var driftForward = toInteger(valueOf('#totpReplayStoredDriftForward'));
    if (timestamp != null) {
      payload.timestamp = timestamp;
    }
    if (timestampOverride != null) {
      payload.timestampOverride = timestampOverride;
    }
    if (driftBackward != null) {
      payload.driftBackward = driftBackward;
    }
    if (driftForward != null) {
      payload.driftForward = driftForward;
    }
    replayStoredButton.setAttribute('disabled', 'disabled');
    clearReplayPanels();
    postJson(endpoint, payload, csrfToken(replayStoredForm))
      .then(handleReplaySuccess)
      .catch(handleReplayError)
      .finally(function () {
        replayStoredButton.removeAttribute('disabled');
      });
  }

  function replayInline() {
    if (!replayInlineForm || !replayInlineButton) {
      return;
    }
    var endpoint = replayInlineForm.getAttribute('data-replay-endpoint');
    var payload = {
      sharedSecretHex: valueOf('#totpReplayInlineSecretHex'),
      algorithm: valueOf('#totpReplayInlineAlgorithm'),
      otp: valueOf('#totpReplayInlineOtp'),
    };
    var digits = toInteger(valueOf('#totpReplayInlineDigits'));
    var stepSeconds = toInteger(valueOf('#totpReplayInlineStepSeconds'));
    var driftBackward = toInteger(valueOf('#totpReplayInlineDriftBackward'));
    var driftForward = toInteger(valueOf('#totpReplayInlineDriftForward'));
    var timestamp = toInteger(valueOf('#totpReplayInlineTimestamp'));
    var timestampOverride = toInteger(valueOf('#totpReplayInlineTimestampOverride'));
    if (digits != null) {
      payload.digits = digits;
    }
    if (stepSeconds != null) {
      payload.stepSeconds = stepSeconds;
    }
    if (driftBackward != null) {
      payload.driftBackward = driftBackward;
    }
    if (driftForward != null) {
      payload.driftForward = driftForward;
    }
    if (timestamp != null) {
      payload.timestamp = timestamp;
    }
    if (timestampOverride != null) {
      payload.timestampOverride = timestampOverride;
    }
    replayInlineButton.setAttribute('disabled', 'disabled');
    clearReplayPanels();
    postJson(endpoint, payload, csrfToken(replayInlineForm))
      .then(handleReplaySuccess)
      .catch(handleReplayError)
      .finally(function () {
        replayInlineButton.removeAttribute('disabled');
      });
  }

  function valueOf(selector) {
    var node = totpPanel.querySelector(selector);
    if (!node) {
      return '';
    }
    var raw = node.value != null ? node.value : '';
    return typeof raw === 'string' ? raw.trim() : raw;
  }

  function readTabFromUrl(search) {
    if (!search) {
      return 'evaluate';
    }
    try {
      var params = new global.URLSearchParams(search);
      return normalizeTab(params.get('totpTab'));
    } catch (error) {
      return 'evaluate';
    }
  }

  function readModeFromUrl(search) {
    if (!search) {
      return 'stored';
    }
    try {
      var params = new global.URLSearchParams(search);
      return normalizeMode(params.get('totpMode'));
    } catch (error) {
      return 'stored';
    }
  }

  function readReplayModeFromUrl(search) {
    if (!search) {
      return 'stored';
    }
    try {
      var params = new global.URLSearchParams(search);
      return normalizeMode(params.get('totpReplayMode'));
    } catch (error) {
      return 'stored';
    }
  }

  if (tabContainer) {
    tabContainer.addEventListener('click', function (event) {
      var target = event.target;
      if (!target || target.tagName !== 'BUTTON') {
        return;
      }
      var nextTab = normalizeTab(target.getAttribute('data-totp-panel-target'));
      event.preventDefault();
      setTab(nextTab);
    });
  }

  if (storedRadio) {
    storedRadio.addEventListener('change', function () {
      if (storedRadio.checked) {
        setMode('stored');
      }
    });
  }
  if (inlineRadio) {
    inlineRadio.addEventListener('change', function () {
      if (inlineRadio.checked) {
        setMode('inline');
      }
    });
  }
  if (storedButton) {
    storedButton.addEventListener('click', function (event) {
      event.preventDefault();
      evaluateStored();
    });
  }
  if (seedButton) {
    seedButton.addEventListener('click', function (event) {
      event.preventDefault();
      handleSeedRequest();
    });
  }
  if (inlinePresetSelect) {
    inlinePresetSelect.addEventListener('change', function () {
      var key = inlinePresetSelect.value;
      if (!key) {
        clearInlinePresetTracking();
        clearInlinePanels();
        return;
      }
      applyInlineEvaluationPreset(key);
    });
  }
  if (inlineButton) {
    inlineButton.addEventListener('click', function (event) {
      event.preventDefault();
      evaluateInline();
    });
  }

  if (replayStoredRadio) {
    replayStoredRadio.addEventListener('change', function () {
      if (replayStoredRadio.checked) {
        setReplayMode('stored');
      }
    });
  }
  if (replayInlineRadio) {
    replayInlineRadio.addEventListener('change', function () {
      if (replayInlineRadio.checked) {
        setReplayMode('inline');
      }
    });
  }
  if (replayStoredSelect) {
    replayStoredSelect.addEventListener('change', function () {
      syncReplaySampleActions();
    });
  }
  if (replayStoredButton) {
    replayStoredButton.addEventListener('click', function (event) {
      event.preventDefault();
      replayStored();
    });
  }
  if (replayStoredSampleButton) {
    replayStoredSampleButton.addEventListener('click', function (event) {
      event.preventDefault();
      applyReplayStoredSample();
    });
  }
  if (replayInlinePresetSelect) {
    replayInlinePresetSelect.addEventListener('change', function () {
      var key = replayInlinePresetSelect.value;
      if (!key) {
        setPresetApplied(replayInlinePresetContainer, '');
        clearReplayPanels();
        return;
      }
      applyReplayInlinePreset(key);
    });
  }
  if (replayInlineButton) {
    replayInlineButton.addEventListener('click', function (event) {
      event.preventDefault();
      replayInline();
    });
  }

  documentRef.addEventListener('operator:protocol-activated', function (event) {
    if (!event || !event.detail || event.detail.protocol !== 'totp') {
      return;
    }
    var detail = event.detail;
    if (detail.totpTab) {
      setTab(detail.totpTab, { broadcast: false, force: true, replace: detail.replace === true });
    }
    if (detail.totpMode) {
      setMode(detail.totpMode, { broadcast: false, force: true, replace: detail.replace === true });
    }
    if (detail.totpReplayMode) {
      setReplayMode(
          detail.totpReplayMode,
          { broadcast: false, force: true, replace: detail.replace === true });
    }
  });

  var initialTab = readTabFromUrl(global.location.search);
  setTab(initialTab, { broadcast: false, force: true, replace: true });
  var initialMode = readModeFromUrl(global.location.search);
  setMode(initialMode, { broadcast: false, force: true, replace: true });
  var initialReplayMode = readReplayModeFromUrl(global.location.search);
  setReplayMode(initialReplayMode, { broadcast: false, force: true, replace: true });
  syncReplaySampleActions();

  global.TotpConsole = {
    setTab: function (tab, options) {
      setTab(tab, options || {});
    },
    getTab: function () {
      return currentTab;
    },
    setMode: function (mode, options) {
      setMode(mode, options || {});
    },
    getMode: function () {
      return currentMode;
    },
    setReplayMode: function (mode, options) {
      setReplayMode(mode, options || {});
    },
    getReplayMode: function () {
      return currentReplayMode;
    },
  };
})(window);
