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

  var replayModeToggle = totpPanel.querySelector('[data-testid="totp-replay-mode-toggle"]');
  var replayStoredRadio = totpPanel.querySelector('[data-testid="totp-replay-mode-select-stored"]');
  var replayInlineRadio = totpPanel.querySelector('[data-testid="totp-replay-mode-select-inline"]');
  var replayStoredSection = totpPanel.querySelector('[data-replay-section="stored"]');
  var replayInlineSection = totpPanel.querySelector('[data-replay-section="inline"]');

  var replayStoredForm = totpPanel.querySelector('[data-testid="totp-replay-stored-form"]');
  var replayInlineForm = totpPanel.querySelector('[data-testid="totp-replay-inline-form"]');
  var replayStoredButton = totpPanel.querySelector('[data-testid="totp-replay-stored-submit"]');
  var replayInlineButton = totpPanel.querySelector('[data-testid="totp-replay-inline-submit"]');
  var replayResultPanel = totpPanel.querySelector('[data-testid="totp-replay-result-panel"]');
  var replayStatusBadge = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-status"]')
    : null;
  var replayReasonCode = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-reason-code"]')
    : null;
  var replayCredentialSource = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-credential-source"]')
    : null;
  var replayCredentialId = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-credential-id"]')
    : null;
  var replayMatchedSkew = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-matched-skew"]')
    : null;
  var replayAlgorithm = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-algorithm"]')
    : null;
  var replayDigits = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-digits"]')
    : null;
  var replayStep = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-step"]')
    : null;
  var replayDriftBackward = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-drift-backward"]')
    : null;
  var replayDriftForward = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-drift-forward"]')
    : null;
  var replayTimestampOverride = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-timestamp-override"]')
    : null;
  var replayTelemetry = replayResultPanel
    ? replayResultPanel.querySelector('[data-testid="totp-replay-telemetry"]')
    : null;
  var replayErrorPanel = totpPanel.querySelector('[data-testid="totp-replay-error-panel"]');
  var replayErrorReason = replayErrorPanel
    ? replayErrorPanel.querySelector('[data-testid="totp-replay-error-reason"]')
    : null;
  var replayErrorMessage = replayErrorPanel
    ? replayErrorPanel.querySelector('[data-testid="totp-replay-error-message"]')
    : null;

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

  function writeBoolean(node, value) {
    if (!node) {
      return;
    }
    if (value == null) {
      node.textContent = '—';
      return;
    }
    node.textContent = value ? 'Yes' : 'No';
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
    var metadata = response.metadata || {};
    setStatusBadge(replayStatusBadge, response.status || response.reasonCode);
    writeText(replayReasonCode, response.reasonCode || response.status || '—');
    writeText(replayCredentialSource, metadata.credentialSource);
    writeText(replayCredentialId, metadata.credentialId);
    writeText(replayMatchedSkew, metadata.matchedSkewSteps);
    writeText(replayAlgorithm, metadata.algorithm);
    writeText(replayDigits, metadata.digits);
    writeText(replayStep, metadata.stepSeconds);
    writeText(replayDriftBackward, metadata.driftBackwardSteps);
    writeText(replayDriftForward, metadata.driftForwardSteps);
    writeBoolean(replayTimestampOverride, metadata.timestampOverrideProvided);
    writeText(replayTelemetry, metadata.telemetryId);
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
  if (replayStoredButton) {
    replayStoredButton.addEventListener('click', function (event) {
      event.preventDefault();
      replayStored();
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
