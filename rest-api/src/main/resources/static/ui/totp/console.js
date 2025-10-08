(function (global) {
  'use strict';

  var documentRef = global.document;
  var totpPanel = documentRef.querySelector('[data-protocol-panel="totp"]');
  if (!totpPanel) {
    return;
  }

  var ALLOWED_MODES = ['stored', 'inline'];
  var currentMode = null;
  var lastBroadcastMode = null;

  var modeToggle = totpPanel.querySelector('[data-testid="totp-mode-toggle"]');
  var storedRadio = totpPanel.querySelector('[data-testid="totp-mode-select-stored"]');
  var inlineRadio = totpPanel.querySelector('[data-testid="totp-mode-select-inline"]');
  var storedSection = totpPanel.querySelector('[data-mode-section="stored"]');
  var inlineSection = totpPanel.querySelector('[data-mode-section="inline"]');

  var storedForm = totpPanel.querySelector('[data-testid="totp-stored-form"]');
  var storedButton = totpPanel.querySelector('[data-testid="totp-stored-evaluate-button"]');
  var storedResultPanel = totpPanel.querySelector('[data-testid="totp-stored-result-panel"]');
  var storedStatusBadge = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid=\"totp-result-status\"]')
    : null;
  var storedReasonCode = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid=\"totp-result-reason-code\"]')
    : null;
  var storedSkew = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid=\"totp-result-skew\"]')
    : null;
  var storedAlgorithm = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid=\"totp-result-algorithm\"]')
    : null;
  var storedDigits = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid=\"totp-result-digits\"]')
    : null;
  var storedStep = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid=\"totp-result-step\"]')
    : null;
  var storedDriftBackward = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid=\"totp-result-drift-backward\"]')
    : null;
  var storedDriftForward = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid=\"totp-result-drift-forward\"]')
    : null;
  var storedTelemetry = storedResultPanel
    ? storedResultPanel.querySelector('[data-testid=\"totp-result-telemetry\"]')
    : null;
  var storedErrorPanel = totpPanel.querySelector('[data-testid=\"totp-stored-error-panel\"]');
  var storedErrorReason = storedErrorPanel
    ? storedErrorPanel.querySelector('[data-testid=\"totp-stored-error-reason\"]')
    : null;
  var storedErrorMessage = storedErrorPanel
    ? storedErrorPanel.querySelector('[data-testid=\"totp-stored-error-message\"]')
    : null;

  var inlineForm = totpPanel.querySelector('[data-testid=\"totp-inline-form\"]');
  var inlineButton = totpPanel.querySelector('[data-testid=\"totp-inline-evaluate-button\"]');
  var inlineResultPanel = totpPanel.querySelector('[data-testid=\"totp-inline-result-panel\"]');
  var inlineStatusBadge = inlineResultPanel
    ? inlineResultPanel.querySelector('[data-testid=\"totp-inline-result-status\"]')
    : null;
  var inlineReasonCode = inlineResultPanel
    ? inlineResultPanel.querySelector('[data-testid=\"totp-inline-result-reason-code\"]')
    : null;
  var inlineTelemetry = inlineResultPanel
    ? inlineResultPanel.querySelector('[data-testid=\"totp-inline-result-telemetry\"]')
    : null;
  var inlineErrorPanel = totpPanel.querySelector('[data-testid=\"totp-inline-error-panel\"]');
  var inlineErrorReason = inlineErrorPanel
    ? inlineErrorPanel.querySelector('[data-testid=\"totp-inline-error-reason\"]')
    : null;
  var inlineErrorMessage = inlineErrorPanel
    ? inlineErrorPanel.querySelector('[data-testid=\"totp-inline-error-message\"]')
    : null;

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

  function writeText(node, value) {
    if (!node) {
      return;
    }
    node.textContent = value == null ? '—' : String(value);
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
    var tokenInput = form.querySelector('input[name=\"_csrf\"]');
    return tokenInput && tokenInput.value ? tokenInput.value : null;
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
      return Promise.reject(new Error('Missing evaluation endpoint'));
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
            var error = new Error('TOTP evaluation failed');
            error.status = response.status;
            error.payload = parsed;
            throw error;
          });
        });
    }

    return new Promise(function (resolve, reject) {
      var XMLHttpRequestCtor = global.XMLHttpRequest;
      if (typeof XMLHttpRequestCtor !== 'function') {
        reject(new Error('Browser does not support XMLHttpRequest'));
        return;
      }
      try {
        var xhr = new XMLHttpRequestCtor();
        xhr.open('POST', endpoint, true);
        xhr.withCredentials = true;
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.setRequestHeader('Content-Type', 'application/json');
        if (csrf) {
          xhr.setRequestHeader('X-CSRF-TOKEN', csrf);
        }
        var DONE = typeof XMLHttpRequestCtor.DONE === 'number' ? XMLHttpRequestCtor.DONE : 4;
        xhr.onreadystatechange = function () {
          if (xhr.readyState !== DONE) {
            return;
          }
          var parsed = parseJson(xhr.responseText);
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(parsed);
            return;
          }
          var error = new Error('TOTP evaluation failed');
          error.status = xhr.status;
          error.payload = parsed;
          reject(error);
        };
        xhr.onerror = function () {
          reject(new Error('Network error during TOTP evaluation'));
        };
        xhr.send(requestBody);
      } catch (error) {
        reject(error);
      }
    });
  }

  function normalizeMode(mode) {
    if (ALLOWED_MODES.indexOf(mode) >= 0) {
      return mode;
    }
    return 'stored';
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
    if (storedSection) {
      setHidden(storedSection, normalized !== 'stored');
    }
    if (inlineSection) {
      setHidden(inlineSection, normalized !== 'inline');
    }
    dispatchModeChange(normalized, options);
  }

  function clearStoredPanels() {
    setHidden(storedResultPanel, true);
    setHidden(storedErrorPanel, true);
  }

  function clearInlinePanels() {
    setHidden(inlineResultPanel, true);
    setHidden(inlineErrorPanel, true);
  }

  function setStatusBadge(badge, status) {
    if (!badge) {
      return;
    }
    var isSuccess = status === 'validated' || status === 'success';
    var isInvalid = status === 'otp_out_of_window' || status === 'invalid';
    badge.textContent = isSuccess ? 'Success' : isInvalid ? 'Invalid' : String(status || 'Unknown');
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
    var driftBackward = toInteger(valueOf('#totpStoredDriftBackward'));
    var driftForward = toInteger(valueOf('#totpStoredDriftForward'));
    var timestampOverride = toInteger(valueOf('#totpStoredTimestampOverride'));
    if (timestamp != null) {
      payload.timestamp = timestamp;
    }
    if (driftBackward != null) {
      payload.driftBackward = driftBackward;
    }
    if (driftForward != null) {
      payload.driftForward = driftForward;
    }
    if (timestampOverride != null) {
      payload.timestampOverride = timestampOverride;
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

  function valueOf(selector) {
    var node = totpPanel.querySelector(selector);
    if (!node) {
      return '';
    }
    var raw = node.value != null ? node.value : '';
    return typeof raw === 'string' ? raw.trim() : raw;
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

  documentRef.addEventListener('operator:protocol-activated', function (event) {
    if (!event || !event.detail || event.detail.protocol !== 'totp') {
      return;
    }
    var desiredMode = normalizeMode(event.detail.totpMode);
    setMode(desiredMode, { broadcast: false });
  });

  var initialMode = normalizeMode(readModeFromUrl(global.location.search));
  setMode(initialMode, { broadcast: false, force: true });

  global.TotpConsole = {
    setMode: function (mode, options) {
      var nextOptions = {
        broadcast: options && options.broadcast === true,
        force: options && options.force === true,
      };
      if (options && options.replace === true) {
        nextOptions.replace = true;
      }
      setMode(mode, nextOptions);
    },
    getMode: function () {
      return currentMode;
    },
  };

  function readModeFromUrl(search) {
    if (!search) {
      return 'stored';
    }
    try {
      var params = new global.URLSearchParams(search);
      return params.get('totpMode') || 'stored';
    } catch (error) {
      return 'stored';
    }
  }
})(window);
