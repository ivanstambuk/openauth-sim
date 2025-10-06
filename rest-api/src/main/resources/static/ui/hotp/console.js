(function (global) {
  'use strict';

  var documentRef = global.document;
  var hotpPanel = documentRef.querySelector('[data-protocol-panel="hotp"]');
  var panelTabs = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-panel-tabs"]')
    : null;
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
  var storedErrorPanel = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-stored-error-panel"]')
    : null;
  var inlineErrorPanel = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-inline-error-panel"]')
    : null;
  var storedStatus = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-stored-status"]')
    : null;
  var storedSelect = hotpPanel ? hotpPanel.querySelector('#hotpStoredCredentialId') : null;
  var inlineSecretInput = hotpPanel ? hotpPanel.querySelector('#hotpInlineSecretHex') : null;
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

  var replayForm = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-replay-form"]')
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
  var replaySampleActions = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-sample-actions"]')
    : null;
  var replaySampleButton = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-sample-load"]')
    : null;
  var replaySampleMessage = replayForm
    ? replayForm.querySelector('[data-testid="hotp-replay-sample-message"]')
    : null;
  var replayInlineIdentifierInput = replayForm
    ? replayForm.querySelector('#hotpReplayInlineIdentifier')
    : null;
  var replayInlineSecretInput = replayForm
    ? replayForm.querySelector('#hotpReplayInlineSecretHex')
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
  var replayErrorPanel = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-replay-error"]')
    : null;
  var replayErrorMessage = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-replay-error-message"]')
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
    seedMetadataByCredential[definition.credentialId] = metadata;
  });

  if (seedDefinitionNode && seedDefinitionNode.parentNode) {
    seedDefinitionNode.parentNode.removeChild(seedDefinitionNode);
  }

  var HOTP_SAMPLE_SECRET_HEX = '3132333435363738393031323334353637383930';
  var STORED_SAMPLE_PRESETS = {
    'ui-hotp-demo': {
      otp: '755224',
      metadata: {
        label: 'stored-demo',
        notes: 'Replay sample generated from seeded HOTP credential.',
      },
    },
    'ui-hotp-demo-sha256': {
      otp: '89697997',
      metadata: {
        label: 'stored-demo-sha256',
        notes: 'Seeded HOTP SHA-256 demo credential.',
      },
    },
  };
  var STORED_SAMPLE_DATA = createStoredSampleData();
  var INLINE_SAMPLE_DATA = {
    'demo-inline': {
      label: 'Inline demo vector (SHA-1)',
      identifier: 'inline-sample',
      sharedSecretHex: HOTP_SAMPLE_SECRET_HEX,
      algorithm: 'SHA1',
      digits: 6,
      counter: 5,
      otp: '254676',
      metadata: {
        label: 'inline-demo',
        notes: 'Inline HOTP replay demo vector derived from RFC 4226 sample.',
      },
    },
    'inline-demo-sha256': {
      label: 'Inline demo vector (SHA-256)',
      identifier: 'inline-sample-sha256',
      sharedSecretHex: HOTP_SAMPLE_SECRET_HEX,
      algorithm: 'SHA256',
      digits: 8,
      counter: 5,
      otp: '89697997',
      metadata: {
        label: 'inline-demo-sha256',
        notes: 'Inline HOTP demo vector using SHA-256 with an 8-digit response.',
      },
    },
  };

  var credentialCache = null;
  var credentialPromise = null;
  var inlinePresetActiveKey = '';
  var inlinePresetActiveLabel = '';
  var seedInProgress = false;

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
    if (typeof global.fetch === 'function') {
      return global.fetch(endpoint, options);
    }
    return new Promise(function (resolve, reject) {
      try {
        var xhr = new XMLHttpRequest();
        var method = (options && options.method) || 'GET';
        xhr.open(method, endpoint, true);
        if (options && options.headers) {
          Object.keys(options.headers).forEach(function (key) {
            xhr.setRequestHeader(key, options.headers[key]);
          });
        }
        xhr.withCredentials = options && options.credentials === 'same-origin';
        xhr.onload = function () {
          var responseText = xhr.responseText || '';
          resolve({
            ok: xhr.status >= 200 && xhr.status < 300,
            status: xhr.status,
            text: function () {
              return Promise.resolve(responseText);
            },
          });
        };
        xhr.onerror = function () {
          reject(new Error('Request failed'));
        };
        xhr.send((options && options.body) || null);
      } catch (error) {
        reject(error);
      }
    });
  }

  function createStoredSampleData() {
    var result = {};
    Object.keys(STORED_SAMPLE_PRESETS).forEach(function (credentialId) {
      var preset = STORED_SAMPLE_PRESETS[credentialId];
      if (!preset) {
        return;
      }
      var metadataSource = seedMetadataByCredential[credentialId];
      var mergedMetadata = {};
      if (metadataSource && typeof metadataSource === 'object') {
        Object.keys(metadataSource).forEach(function (key) {
          if (Object.prototype.hasOwnProperty.call(metadataSource, key)) {
            mergedMetadata[key] = metadataSource[key];
          }
        });
      }
      var fallbackMetadata = preset.metadata || {};
      if (!mergedMetadata.label && fallbackMetadata.label) {
        mergedMetadata.label = fallbackMetadata.label;
      }
      if (!mergedMetadata.notes && fallbackMetadata.notes) {
        mergedMetadata.notes = fallbackMetadata.notes;
      }
      result[credentialId] = {
        otp: preset.otp,
        metadata: mergedMetadata,
      };
    });
    return result;
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
    if (['generated', 'success', 'ok', 'valid', 'completed'].indexOf(lowered) >= 0) {
      return 'success';
    }
    if (['failed', 'failure', 'error', 'invalid', 'denied', 'rejected'].indexOf(lowered) >= 0) {
      return 'error';
    }
    return 'info';
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
    setHidden(panel, false);
    var status = payload && payload.status ? payload.status : 'unknown';
    var metadata = payload && payload.metadata ? payload.metadata : null;
    var otp = payload && payload.otp ? payload.otp : null;
    applyStatusBadge(panel.querySelector('[data-testid="hotp-result-status"]'), status);
    var otpNode = panel.querySelector('[data-testid="hotp-result-otp"]');
    if (otpNode) {
      otpNode.textContent = otp && otp.trim().length > 0 ? otp.trim() : '—';
    }
    var metadataNode = panel.querySelector('[data-testid="hotp-result-metadata"]');
    if (metadataNode) {
      metadataNode.textContent = formatMetadata(metadata);
    }
  }

  function normalizeHotpTab(value) {
    return HOTP_ALLOWED_TABS.indexOf(value) >= 0 ? value : 'evaluate';
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
  }

  function updateStoredSampleHints() {
    if (!replayStoredSelect) {
      return;
    }
    var credentialId = replayStoredSelect.value || '';
    var sample = STORED_SAMPLE_DATA[credentialId];
    if (!credentialId) {
      setHidden(replaySampleActions, true);
      if (replaySampleButton) {
        replaySampleButton.setAttribute('disabled', 'disabled');
        replaySampleButton.setAttribute('aria-disabled', 'true');
      }
      return;
    }

    if (sample) {
      setHidden(replaySampleActions, false);
      if (replaySampleButton) {
        replaySampleButton.removeAttribute('disabled');
        replaySampleButton.removeAttribute('aria-disabled');
      }
      if (replaySampleMessage) {
        replaySampleMessage.textContent = 'Apply curated values for this credential.';
      }
    } else {
      setHidden(replaySampleActions, true);
      if (replaySampleButton) {
        replaySampleButton.setAttribute('disabled', 'disabled');
        replaySampleButton.setAttribute('aria-disabled', 'true');
      }
      if (replaySampleMessage) {
        replaySampleMessage.textContent = '';
      }
    }

  }

  function applyStoredSample() {
    if (!replayStoredSelect) {
      return;
    }
    var credentialId = replayStoredSelect.value || '';
    var sample = STORED_SAMPLE_DATA[credentialId];
    if (!sample) {
      return;
    }
    if (replayStoredOtpInput) {
      replayStoredOtpInput.value = sample.otp || '';
    }
    if (replaySampleMessage) {
      replaySampleMessage.textContent = 'Sample data applied';
    }
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

  function applyInlineEvaluationPreset(presetKey) {
    var preset = INLINE_SAMPLE_DATA[presetKey];
    if (!preset) {
      return;
    }
    if (inlineSecretInput && typeof preset.sharedSecretHex === 'string') {
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

  function renderInlinePresetOptions() {
    if (!inlinePresetSelect) {
      return;
    }
    while (inlinePresetSelect.firstChild) {
      inlinePresetSelect.removeChild(inlinePresetSelect.firstChild);
    }
    var placeholder = documentRef.createElement('option');
    placeholder.value = '';
    placeholder.textContent = 'Select a sample';
    inlinePresetSelect.appendChild(placeholder);
    Object.keys(INLINE_SAMPLE_DATA)
      .sort(function (a, b) {
        return a.localeCompare(b, undefined, { sensitivity: 'base' });
      })
      .forEach(function (key) {
        var preset = INLINE_SAMPLE_DATA[key];
        if (!preset) {
          return;
        }
        var option = documentRef.createElement('option');
        option.value = key;
        option.textContent = preset.label || key;
        inlinePresetSelect.appendChild(option);
      });
  }

  function applyInlinePreset(presetKey) {
    var preset = INLINE_SAMPLE_DATA[presetKey];
    if (!preset) {
      return;
    }
    if (replayInlineIdentifierInput) {
      replayInlineIdentifierInput.value = preset.identifier || '';
    }
    if (replayInlineSecretInput) {
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
    if (replayErrorPanel) {
      setHidden(replayErrorPanel, true);
    }
    if (replayErrorMessage) {
      replayErrorMessage.textContent = '';
    }
  }

  function showReplayError(message) {
    if (!replayErrorPanel) {
      return;
    }
    if (replayErrorMessage) {
      replayErrorMessage.textContent = message;
    }
    setHidden(replayResultPanel, true);
    setHidden(replayErrorPanel, false);
  }

  function renderReplayResult(payload) {
    if (!payload) {
      showReplayError('Unexpected replay response.');
      return;
    }
    hideReplayError();
    var status = payload && payload.status ? String(payload.status).trim() : 'unknown';
    if (!status) {
      status = 'unknown';
    }
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
    setHidden(replayResultPanel, false);
  }

  function showStoredError(message) {
    if (!storedErrorPanel) {
      return;
    }
    var messageNode = storedErrorPanel.querySelector('[data-testid="hotp-stored-error"]');
    if (messageNode) {
      messageNode.textContent = message;
    }
    setHidden(storedErrorPanel, false);
  }

  function hideStoredError() {
    setHidden(storedErrorPanel, true);
  }

  function showInlineError(message) {
    if (!inlineErrorPanel) {
      return;
    }
    var messageNode = inlineErrorPanel.querySelector('[data-testid="hotp-inline-error"]');
    if (messageNode) {
      messageNode.textContent = message;
    }
    setHidden(inlineErrorPanel, false);
  }

  function hideInlineError() {
    setHidden(inlineErrorPanel, true);
  }

  function setReplayMode(mode) {
    if (!replayModeToggle) {
      return;
    }
    var normalized = mode === 'inline' ? 'inline' : 'stored';
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
    }
    setHidden(replayResultPanel, true);
    hideReplayError();
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
      var identifier = replayInlineIdentifierInput ? replayInlineIdentifierInput.value.trim() : '';
      var secret = replayInlineSecretInput ? replayInlineSecretInput.value.trim() : '';
      var algorithm = replayInlineAlgorithmSelect ? replayInlineAlgorithmSelect.value : 'SHA1';
      var digits = replayInlineDigitsInput ? parseInt(replayInlineDigitsInput.value, 10) : NaN;
      var counter = replayInlineCounterInput ? parseInt(replayInlineCounterInput.value, 10) : NaN;
      var otp = replayInlineOtpInput ? replayInlineOtpInput.value.trim() : '';

      if (!identifier || !secret || !otp || Number.isNaN(digits) || Number.isNaN(counter)) {
        showReplayError('Inline replay requires identifier, secret, digits, counter, and OTP values.');
        return;
      }

      payload = {
        identifier: identifier,
        sharedSecretHex: secret,
        algorithm: algorithm || 'SHA1',
        digits: digits,
        counter: counter,
        otp: otp,
      };
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
        var message = error && error.message ? error.message : 'Unable to submit HOTP replay request.';
        showReplayError(message);
      })
      .finally(function () {
        replaySubmitButton.removeAttribute('disabled');
      });
  }

  function setActiveMode(mode) {
    if (!modeToggle) {
      return;
    }
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

    fetchDelegate(storedForm.getAttribute('data-evaluate-endpoint') || '/api/v1/hotp/evaluate', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfTokenFor(storedForm) || '',
      },
      credentials: 'same-origin',
      body: JSON.stringify({ credentialId: credentialId }),
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
    var secret = inlineSecretInput ? inlineSecretInput.value : '';
    var digits = inlineDigitsInput ? parseInt(inlineDigitsInput.value, 10) : NaN;
    var counter = inlineCounterInput ? parseInt(inlineCounterInput.value, 10) : NaN;

    if (!secret || secret.trim().length === 0) {
      showInlineError('Provide the HOTP shared secret (hex encoded).');
      return;
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

    var payload = {
      sharedSecretHex: secret.trim(),
      algorithm: inlineAlgorithmSelect ? inlineAlgorithmSelect.value : 'SHA1',
      digits: digits,
      counter: counter,
    };

    if (inlinePresetActiveKey) {
      var metadataPayload = { presetKey: inlinePresetActiveKey };
      if (inlinePresetActiveLabel) {
        metadataPayload.presetLabel = inlinePresetActiveLabel;
      }
      payload.metadata = metadataPayload;
    }

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
          var message =
            parsed && parsed.error && parsed.error.description
              ? parsed.error.description
              : 'Inline HOTP evaluation failed.';
          throw new Error(message);
        }
        var parsedBody = parseJson(payload.body) || {};
        renderResult(inlineResultPanel, parsedBody);
      })
      .catch(function (error) {
        showInlineError(error && error.message ? error.message : 'Unable to evaluate inline parameters.');
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
          setReplayMode('stored');
        }
        if (target.getAttribute('data-testid') === 'hotp-replay-mode-select-inline') {
          setReplayMode('inline');
        }
      });
    }
    if (replayStoredSelect) {
      replayStoredSelect.addEventListener('change', function () {
        updateStoredSampleHints();
      });
    }
    if (replaySampleButton) {
      replaySampleButton.addEventListener('click', function (event) {
        event.preventDefault();
        applyStoredSample();
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
      setReplayMode('inline');
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
    setActivePanel(parseHotpTabFromSearch(), { replaceHistory: true });
  }
})(window);
