(function (global) {
  'use strict';

  var documentRef = global.document;
  var hotpPanel = documentRef.querySelector('[data-protocol-panel="hotp"]');
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
  var storedOtpInput = hotpPanel ? hotpPanel.querySelector('#hotpStoredOtp') : null;
  var inlineSecretInput = hotpPanel ? hotpPanel.querySelector('#hotpInlineSecretHex') : null;
  var inlineDigitsInput = hotpPanel ? hotpPanel.querySelector('#hotpInlineDigits') : null;
  var inlineCounterInput = hotpPanel ? hotpPanel.querySelector('#hotpInlineCounter') : null;
  var inlineOtpInput = hotpPanel ? hotpPanel.querySelector('#hotpInlineOtp') : null;
  var storedButton = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-stored-evaluate-button"]')
    : null;
  var inlineButton = hotpPanel
    ? hotpPanel.querySelector('[data-testid="hotp-inline-evaluate-button"]')
    : null;
  var inlineIdentifierInput = hotpPanel
    ? hotpPanel.querySelector('#hotpInlineIdentifier')
    : null;
  var inlineAlgorithmSelect = hotpPanel
    ? hotpPanel.querySelector('#hotpInlineAlgorithm')
    : null;

  var initialized = false;
  var credentialCache = null;
  var credentialPromise = null;

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
      });

    return credentialPromise;
  }

  function renderCredentialOptions(options) {
    if (!storedSelect) {
      return;
    }
    var currentValue = storedSelect.value;
    while (storedSelect.firstChild) {
      storedSelect.removeChild(storedSelect.firstChild);
    }
    var placeholder = documentRef.createElement('option');
    placeholder.value = '';
    placeholder.textContent =
      options && options.length
        ? 'Select a credential'
        : 'No HOTP credentials available';
    storedSelect.appendChild(placeholder);
    if (Array.isArray(options)) {
      options.forEach(function (option) {
        var opt = documentRef.createElement('option');
        opt.value = option.id;
        opt.textContent = option.label;
        storedSelect.appendChild(opt);
      });
    }
    if (currentValue && storedSelect.querySelector('option[value="' + currentValue + '"]')) {
      storedSelect.value = currentValue;
    }
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

  function renderResult(panel, status, metadata) {
    if (!panel) {
      return;
    }
    setHidden(panel, false);
    var statusNode = panel.querySelector('[data-testid="hotp-result-status"]');
    if (statusNode) {
      statusNode.textContent = status || 'unknown';
    }
    var metadataNode = panel.querySelector('[data-testid="hotp-result-metadata"]');
    if (metadataNode) {
      metadataNode.textContent = formatMetadata(metadata);
    }
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

  function handleStoredEvaluate() {
    if (!storedForm || !storedButton) {
      return;
    }
    var credentialId = storedSelect ? storedSelect.value : '';
    var otp = storedOtpInput ? storedOtpInput.value : '';
    if (!credentialId) {
      showStoredError('Select a HOTP credential before evaluating.');
      return;
    }
    if (!otp || otp.trim().length < 6) {
      showStoredError('Provide the observed HOTP value to evaluate.');
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
      body: JSON.stringify({ credentialId: credentialId, otp: otp.trim() }),
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
        renderResult(storedResultPanel, parsedBody.status, parsedBody.metadata);
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
    var otp = inlineOtpInput ? inlineOtpInput.value : '';

    if (!secret || secret.trim().length === 0) {
      showInlineError('Provide the HOTP shared secret (hex encoded).');
      return;
    }
    if (!otp || otp.trim().length < 6) {
      showInlineError('Provide the HOTP value to evaluate.');
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
      identifier: inlineIdentifierInput ? inlineIdentifierInput.value : null,
      sharedSecretHex: secret.trim(),
      algorithm: inlineAlgorithmSelect ? inlineAlgorithmSelect.value : 'SHA1',
      digits: digits,
      counter: counter,
      otp: otp.trim(),
    };

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
        renderResult(inlineResultPanel, parsedBody.status, parsedBody.metadata);
      })
      .catch(function (error) {
        showInlineError(error && error.message ? error.message : 'Unable to evaluate inline parameters.');
      })
      .finally(function () {
        inlineButton.removeAttribute('disabled');
      });
  }

  function attachEventHandlers() {
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
    if (inlineButton) {
      inlineButton.addEventListener('click', function (event) {
        event.preventDefault();
        handleInlineEvaluate();
      });
    }
  }

  function init() {
    if (initialized) {
      if (!modeToggle || modeToggle.getAttribute('data-mode') === 'stored') {
        ensureCredentials(true);
      }
      return;
    }
    attachEventHandlers();
    if (modeToggle) {
      setActiveMode(modeToggle.getAttribute('data-mode') || 'stored');
    } else {
      ensureCredentials(false);
    }
    initialized = true;
  }

  function isHotpActive() {
    var params = new global.URLSearchParams(global.location.search);
    return params.get('protocol') === 'hotp';
  }

  documentRef.addEventListener('operator:protocol-activated', function (event) {
    if (!event || !event.detail) {
      return;
    }
    if (event.detail.protocol === 'hotp') {
      init();
    }
  });

  global.addEventListener('popstate', function () {
    if (isHotpActive()) {
      init();
    }
  });

  if (isHotpActive()) {
    init();
  }
})(window);
