(function (global) {
  'use strict';

  var documentRef = global.document;
  var form = documentRef.querySelector('[data-testid="emv-form"]');
  if (!form) {
    return;
  }

  var verboseConsole = global.VerboseTraceConsole || null;
  var evaluateEndpoint = form.getAttribute('data-evaluate-endpoint') || '';
  var credentialsEndpoint = form.getAttribute('data-credentials-endpoint') || '';
  var seedEndpoint = form.getAttribute('data-seed-endpoint') || '';
  var csrfInput = form.querySelector('input[name="_csrf"]');
  var storedSelect = form.querySelector('#emvStoredCredentialId');
  var masterKeyInput = form.querySelector('#emvMasterKey');
  var atcInput = form.querySelector('#emvAtc');
  var branchFactorInput = form.querySelector('#emvBranchFactor');
  var heightInput = form.querySelector('#emvHeight');
  var ivInput = form.querySelector('#emvIv');
  var cdol1Input = form.querySelector('#emvCdol1');
  var ipbInput = form.querySelector('#emvIpb');
  var iccTemplateInput = form.querySelector('#emvIccTemplate');
  var issuerApplicationDataInput = form.querySelector('#emvIssuerApplicationData');
  var challengeInput = form.querySelector('#emvChallenge');
  var referenceInput = form.querySelector('#emvReference');
  var amountInput = form.querySelector('#emvAmount');
  var terminalInput = form.querySelector('#emvTerminalData');
  var iccOverrideInput = form.querySelector('#emvIccOverride');
  var iccResolvedInput = form.querySelector('#emvIccResolved');
  var includeTraceCheckbox = form.querySelector('[data-testid="emv-include-trace"] input[type="checkbox"]');
  var seedActions = form.querySelector('[data-testid="emv-seed-actions"]');
  var seedButton = seedActions ? seedActions.querySelector('[data-testid="emv-seed-credentials"]') : null;
  var seedStatus = seedActions ? seedActions.querySelector('[data-testid="emv-seed-status"]') : null;
  var resultPanel = documentRef.querySelector('[data-testid="emv-result-card"]');
  var statusBadge = resultPanel ? resultPanel.querySelector('[data-testid="emv-status"]') : null;
  var resultPreviewContainer = resultPanel ? resultPanel.querySelector('[data-result-preview]') : null;
  var resultPreviewBody = resultPreviewContainer
      ? resultPreviewContainer.querySelector('[data-result-preview-body]')
      : null;

  var seedDefinitionNode = documentRef.getElementById('emv-seed-definitions');
  var seedDefinitions = [];
  if (seedDefinitionNode && seedDefinitionNode.textContent) {
    try {
      seedDefinitions = JSON.parse(seedDefinitionNode.textContent.trim());
    } catch (error) {
      seedDefinitions = [];
      if (global.console && typeof global.console.warn === 'function') {
        global.console.warn('Unable to parse EMV/CAP seed definitions', error);
      }
    }
    seedDefinitionNode.parentNode.removeChild(seedDefinitionNode);
  }

  var credentialsCache = Object.create(null);
  var credentialsList = [];
  var loadingCredentials = false;
  var submitting = false;
  var seeding = false;

  if (storedSelect) {
    storedSelect.addEventListener('change', function () {
      applyCredential(storedSelect.value);
    });
  }

  if (seedButton) {
    seedButton.addEventListener('click', function () {
      handleSeedRequest();
    });
  }

  if (includeTraceCheckbox) {
    includeTraceCheckbox.addEventListener('change', function () {
      if (!includeTraceCheckbox.checked) {
        clearVerboseTrace();
      }
    });
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    handleSubmit();
  });

  loadCredentials();

  function loadCredentials() {
    if (!credentialsEndpoint || loadingCredentials) {
      return;
    }
    loadingCredentials = true;
    getJson(credentialsEndpoint)
      .then(function (payload) {
        credentialsCache = Object.create(null);
        credentialsList = Array.isArray(payload) ? payload : [];
        populateStoredSelect(credentialsList);
        if (storedSelect && storedSelect.value) {
          applyCredential(storedSelect.value);
        } else if (credentialsList.length > 0) {
          applyCredential(credentialsList[0].id);
        }
      })
      .catch(function (error) {
        if (global.console && typeof global.console.warn === 'function') {
          global.console.warn('Unable to load EMV/CAP credentials', error);
        }
        populateStoredSelect([]);
      })
      .then(function () {
        loadingCredentials = false;
      });
  }

  function populateStoredSelect(list) {
    if (!storedSelect) {
      return;
    }
    while (storedSelect.options.length > 1) {
      storedSelect.remove(1);
    }
    list.forEach(function (summary) {
      if (!summary || typeof summary.id !== 'string') {
        return;
      }
      credentialsCache[summary.id] = summary;
      var option = documentRef.createElement('option');
      option.value = summary.id;
      option.textContent =
        typeof summary.label === 'string' && summary.label.trim().length > 0
          ? summary.label.trim()
          : summary.id;
      storedSelect.appendChild(option);
    });
  }

  function applyCredential(credentialId) {
    if (!credentialId || !credentialsCache[credentialId]) {
      return;
    }
    var summary = credentialsCache[credentialId];
    if (typeof storedSelect.value !== 'string' || storedSelect.value !== credentialId) {
      storedSelect.value = credentialId;
    }
    setMode(summary.mode);
    setValue(masterKeyInput, summary.masterKey);
    setValue(atcInput, summary.defaultAtc);
    setValue(branchFactorInput, summary.branchFactor);
    setValue(heightInput, summary.height);
    setValue(ivInput, summary.iv);
    setValue(cdol1Input, summary.cdol1);
    setValue(ipbInput, summary.issuerProprietaryBitmap);
    setValue(iccTemplateInput, summary.iccDataTemplate);
    setValue(issuerApplicationDataInput, summary.issuerApplicationData);

    if (summary.defaults) {
      setValue(challengeInput, summary.defaults.challenge);
      setValue(referenceInput, summary.defaults.reference);
      setValue(amountInput, summary.defaults.amount);
    }

    if (summary.transaction) {
      setValue(terminalInput, summary.transaction.terminal);
      setValue(iccOverrideInput, summary.transaction.icc);
      setValue(iccResolvedInput, summary.transaction.iccResolved);
    } else {
      setValue(terminalInput, '');
      setValue(iccOverrideInput, '');
      setValue(iccResolvedInput, '');
    }
  }

  function handleSeedRequest() {
    if (!seedEndpoint || seeding) {
      return;
    }
    seeding = true;
    updateSeedStatus('Seeding canonical credentials…');
    postJson(seedEndpoint, {}, csrfToken(csrfInput))
      .then(function (response) {
        var addedCount = Array.isArray(response.addedCredentialIds)
          ? response.addedCredentialIds.length
          : typeof response.addedCount === 'number'
          ? response.addedCount
          : 0;
        var canonicalCount =
          typeof response.canonicalCount === 'number'
            ? response.canonicalCount
            : seedDefinitions.length;
        if (addedCount > 0) {
          updateSeedStatus(
            'Seeded ' + addedCount + ' credential' + (addedCount !== 1 ? 's' : '') + ' (canonical set: ' + canonicalCount + ').'
          );
        } else {
          updateSeedStatus('Canonical credentials already present.');
        }
        loadCredentials();
      })
      .catch(function (error) {
        updateSeedStatus('Unable to seed credentials.');
        if (global.console && typeof global.console.error === 'function') {
          global.console.error('EMV/CAP seeding failed', error);
        }
      })
      .then(function () {
        seeding = false;
      });
  }

  function handleSubmit() {
    if (submitting || !evaluateEndpoint) {
      return;
    }
    var payload = buildPayload();
    submitting = true;
    if (resultPanel && global.ResultCard && typeof global.ResultCard.resetMessage === 'function') {
      global.ResultCard.resetMessage(resultPanel);
    }
    clearPreview();
    verboseBeginRequest();
    postJson(evaluateEndpoint, payload, csrfToken(csrfInput))
      .then(function (response) {
        renderSuccess(response);
      })
      .catch(function (error) {
        renderFailure(error);
      })
      .then(function () {
        submitting = false;
      });
  }

  function buildPayload() {
    var mode = selectedMode();
    var payload = {
      mode: mode,
      masterKey: uppercase(value(masterKeyInput)),
      atc: uppercase(value(atcInput)),
      branchFactor: parseInteger(branchFactorInput),
      height: parseInteger(heightInput),
      iv: uppercase(value(ivInput)),
      cdol1: uppercase(value(cdol1Input)),
      issuerProprietaryBitmap: uppercase(value(ipbInput)),
      iccDataTemplate: uppercase(value(iccTemplateInput)),
      issuerApplicationData: uppercase(value(issuerApplicationDataInput)),
      customerInputs: {
        challenge: digitsValue(challengeInput),
        reference: digitsValue(referenceInput),
        amount: digitsValue(amountInput),
      },
    };

    if (!payload.customerInputs.challenge && !payload.customerInputs.reference && !payload.customerInputs.amount) {
      delete payload.customerInputs;
    }

    var transactionTerminal = uppercase(value(terminalInput));
    var transactionIcc = uppercase(value(iccOverrideInput));
    if (transactionTerminal || transactionIcc) {
      payload.transactionData = {};
      if (transactionTerminal) {
        payload.transactionData.terminal = transactionTerminal;
      }
      if (transactionIcc) {
        payload.transactionData.icc = transactionIcc;
      }
    }

    payload.includeTrace = isTraceRequested();

    return payload;
  }

  function isTraceRequested() {
    if (includeTraceCheckbox && !includeTraceCheckbox.checked) {
      return false;
    }
    if (verboseConsole && typeof verboseConsole.isEnabled === 'function') {
      return verboseConsole.isEnabled();
    }
    return true;
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

  function clearVerboseTrace() {
    if (!verboseConsole) {
      return;
    }
    if (typeof verboseConsole.clearTrace === 'function') {
      verboseConsole.clearTrace();
    } else if (typeof verboseConsole.beginRequest === 'function') {
      verboseConsole.beginRequest();
    }
  }

  function verboseApplyResponse(response, variant) {
    if (!verboseConsole) {
      return;
    }
    var payload = buildVerboseTracePayload(response);
    var options = { variant: variant || 'info', protocol: 'emv' };
    if (typeof verboseConsole.handleResponse === 'function') {
      verboseConsole.handleResponse(payload, options);
      return;
    }
    if (payload && payload.trace && typeof verboseConsole.renderTrace === 'function') {
      verboseConsole.renderTrace(payload.trace, options);
      return;
    }
    clearVerboseTrace();
  }

  function verboseApplyError(error) {
    if (!verboseConsole) {
      return;
    }
    var payload = buildVerboseTracePayload(error);
    var options = { variant: 'error', protocol: 'emv' };
    if (typeof verboseConsole.handleError === 'function') {
      verboseConsole.handleError(payload, options);
      return;
    }
    if (payload && payload.trace && typeof verboseConsole.renderTrace === 'function') {
      verboseConsole.renderTrace(payload.trace, options);
      return;
    }
    clearVerboseTrace();
  }

  function buildVerboseTracePayload(response) {
    var normalizedTrace = normalizeTrace(response);
    if (!normalizedTrace) {
      return null;
    }
    return { trace: normalizedTrace };
  }

  function normalizeTrace(response) {
    if (!response || !response.trace) {
      return null;
    }
    var trace = response.trace || {};
    var telemetry = response.telemetry && typeof response.telemetry === 'object' ? response.telemetry : {};
    var fields = telemetry.fields && typeof telemetry.fields === 'object' ? telemetry.fields : {};
    var metadata = {};

    if (typeof response.maskLength === 'number' && !Number.isNaN(response.maskLength)) {
      metadata.maskLength = response.maskLength;
    }
    if (typeof fields.maskedDigitsCount === 'number' && !Number.isNaN(fields.maskedDigitsCount)) {
      metadata.maskedDigitsCount = fields.maskedDigitsCount;
    }
    if (typeof fields.atc === 'string' && fields.atc.trim().length > 0) {
      metadata.atc = fields.atc.trim();
    }
    if (typeof fields.branchFactor === 'number' && !Number.isNaN(fields.branchFactor)) {
      metadata.branchFactor = fields.branchFactor;
    }
    if (typeof fields.height === 'number' && !Number.isNaN(fields.height)) {
      metadata.height = fields.height;
    }

    var steps = [];
    if (trace.sessionKey) {
      steps.push({
        id: 'session_key',
        summary: 'Derived session key',
        attributes: { sessionKey: trace.sessionKey },
      });
    }

    var generateInput = trace.generateAcInput || {};
    var inputAttributes = {};
    if (generateInput.terminal) {
      inputAttributes.terminal = generateInput.terminal;
    }
    if (trace.iccPayloadTemplate) {
      inputAttributes.iccTemplate = trace.iccPayloadTemplate;
    }
    if (trace.iccPayloadResolved) {
      inputAttributes.iccResolved = trace.iccPayloadResolved;
    } else if (generateInput.icc) {
      inputAttributes.icc = generateInput.icc;
    }
    if (Object.keys(inputAttributes).length > 0) {
      steps.push({
        id: 'generate_ac.input',
        summary: 'Generate AC inputs',
        attributes: inputAttributes,
      });
    }

    if (trace.generateAcResult) {
      steps.push({
        id: 'generate_ac.result',
        summary: 'Generate AC cryptogram',
        attributes: { generateAcResult: trace.generateAcResult },
      });
    }

    var maskAttributes = {};
    if (trace.bitmask) {
      maskAttributes.bitmask = trace.bitmask;
    }
    if (trace.maskedDigitsOverlay) {
      maskAttributes.maskedDigitsOverlay = trace.maskedDigitsOverlay;
    }
    if (Object.keys(maskAttributes).length > 0) {
      steps.push({
        id: 'masking',
        summary: 'Masked digits derivation',
        attributes: maskAttributes,
      });
    }

    if (trace.issuerApplicationData) {
      steps.push({
        id: 'issuer_application_data',
        summary: 'Issuer application data',
        attributes: { issuerApplicationData: trace.issuerApplicationData },
      });
    }

    var normalized = {
      operation: 'emv.cap.evaluate',
      steps: steps,
    };
    if (Object.keys(metadata).length > 0) {
      normalized.metadata = metadata;
    }
    return normalized;
  }

  function renderSuccess(body) {
    if (!resultPanel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.showPanel === 'function') {
      global.ResultCard.showPanel(resultPanel);
    } else {
      resultPanel.removeAttribute('hidden');
      resultPanel.setAttribute('aria-hidden', 'false');
    }

    clearPreview();

    var telemetry = body && body.telemetry ? body.telemetry : null;
    var status = telemetry && telemetry.status ? String(telemetry.status) : 'unknown';
    updateText(statusBadge, formatStatus(status));

    var fields = telemetry && telemetry.fields ? telemetry.fields : {};
    var reason = fields && typeof fields.reason === 'string' ? fields.reason : null;

    if (status === 'success') {
      var counter = fields && typeof fields.atc === 'string' ? fields.atc : '';
      renderPreview(counter, body && body.otp ? body.otp : '');
    } else {
      var reasonCode = telemetry && typeof telemetry.reasonCode === 'string' ? telemetry.reasonCode : '';
      var message = reason || 'Evaluation failed.';
      var hint = reasonCode ? 'Reason code: ' + reasonCode : '';
      if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
        global.ResultCard.showMessage(resultPanel, message, 'error', { hint: hint });
      }
    }

    if (isTraceRequested() && body && body.trace) {
      verboseApplyResponse(body, 'info');
    } else {
      clearVerboseTrace();
    }
  }

  function renderFailure(error) {
    if (!resultPanel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.showPanel === 'function') {
      global.ResultCard.showPanel(resultPanel);
    } else {
      resultPanel.removeAttribute('hidden');
      resultPanel.setAttribute('aria-hidden', 'false');
    }
    clearPreview();
    updateText(statusBadge, 'Error');
    verboseApplyError(error);

    var message = resolveErrorMessage(error);
    var hint = resolveErrorHint(error);
    if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
      global.ResultCard.showMessage(resultPanel, message, 'error', { hint: hint });
    }
  }

  function clearPreview() {
    if (!resultPreviewBody) {
      return;
    }
    while (resultPreviewBody.firstChild) {
      resultPreviewBody.removeChild(resultPreviewBody.firstChild);
    }
    if (resultPreviewContainer) {
      resultPreviewContainer.setAttribute('hidden', 'hidden');
      resultPreviewContainer.setAttribute('aria-hidden', 'true');
    }
  }

  function renderPreview(counter, otp) {
    if (!resultPreviewBody || !resultPreviewContainer) {
      return;
    }
    clearPreview();

    var row = documentRef.createElement('tr');
    row.className = 'result-preview__row result-preview__row--active';
    row.setAttribute('data-delta', '0');

    var counterCell = documentRef.createElement('th');
    counterCell.scope = 'row';
    counterCell.className = 'result-preview__cell result-preview__cell--counter';
    counterCell.textContent = normalizePreviewValue(counter);
    row.appendChild(counterCell);

    var deltaCell = documentRef.createElement('td');
    deltaCell.className = 'result-preview__cell result-preview__cell--delta';
    deltaCell.textContent = '0';
    deltaCell.setAttribute('aria-label', 'Delta zero');
    row.appendChild(deltaCell);

    var otpCell = documentRef.createElement('td');
    otpCell.className = 'result-preview__cell result-preview__cell--otp';
    otpCell.setAttribute('data-testid', 'emv-otp');
    otpCell.textContent = normalizePreviewValue(otp);
    row.appendChild(otpCell);

    resultPreviewBody.appendChild(row);
    resultPreviewContainer.removeAttribute('hidden');
    resultPreviewContainer.setAttribute('aria-hidden', 'false');
  }

  function normalizePreviewValue(value) {
    if (value == null) {
      return '—';
    }
    if (typeof value === 'string') {
      var trimmed = value.trim();
      return trimmed.length ? trimmed.toUpperCase() : '—';
    }
    if (typeof value === 'number') {
      return String(value);
    }
    return '—';
  }

  function updateSeedStatus(message) {
    if (!seedStatus) {
      return;
    }
    seedStatus.textContent = message || '';
  }

  function setMode(mode) {
    if (!mode) {
      return;
    }
    var normalized = String(mode).toUpperCase();
    var modeInputs = form.querySelectorAll('input[name="mode"]');
    for (var index = 0; index < modeInputs.length; index += 1) {
      var input = modeInputs[index];
      if (!input) {
        continue;
      }
      input.checked = input.value === normalized;
    }
  }

  function selectedMode() {
    var inputs = form.querySelectorAll('input[name="mode"]');
    for (var index = 0; index < inputs.length; index += 1) {
      var input = inputs[index];
      if (input && input.checked) {
        return input.value;
      }
    }
    return 'IDENTIFY';
  }

  function setValue(node, valueToSet) {
    if (!node) {
      return;
    }
    if (node.tagName === 'INPUT' || node.tagName === 'TEXTAREA') {
      if (typeof valueToSet === 'number') {
        node.value = String(valueToSet);
      } else if (typeof valueToSet === 'string') {
        node.value = valueToSet;
      } else {
        node.value = '';
      }
    }
  }

  function updateText(node, text) {
    if (!node) {
      return;
    }
    node.textContent = typeof text === 'string' ? text : '—';
  }

  function uppercase(text) {
    if (typeof text !== 'string') {
      return '';
    }
    return text.trim().toUpperCase();
  }

  function digitsValue(input) {
    var raw = value(input);
    return raw;
  }

  function value(input) {
    if (!input || typeof input.value !== 'string') {
      return '';
    }
    return input.value.trim();
  }

  function parseInteger(input) {
    var raw = value(input);
    if (!raw) {
      return null;
    }
    var parsed = parseInt(raw, 10);
    if (Number.isNaN(parsed)) {
      return null;
    }
    return parsed;
  }

  function csrfToken(node) {
    if (!node || typeof node.value !== 'string') {
      return null;
    }
    var trimmed = node.value.trim();
    return trimmed.length ? trimmed : null;
  }

  function formatStatus(status) {
    if (typeof status !== 'string' || !status.length) {
      return 'Unknown';
    }
    var normalized = status.trim().toLowerCase();
    if (normalized === 'success') {
      return 'Success';
    }
    if (normalized === 'invalid') {
      return 'Invalid';
    }
    if (normalized === 'error') {
      return 'Error';
    }
    return normalized.charAt(0).toUpperCase() + normalized.slice(1);
  }

  function resolveErrorMessage(error) {
    if (!error) {
      return 'Evaluation failed.';
    }
    if (typeof error === 'string') {
      return error.trim().length ? error.trim() : 'Evaluation failed.';
    }
    if (typeof error.message === 'string' && error.message.trim().length > 0) {
      return error.message.trim();
    }
    if (typeof error.detail === 'string' && error.detail.trim().length > 0) {
      return error.detail.trim();
    }
    if (typeof error.error === 'string' && error.error.trim().length > 0) {
      return error.error.trim();
    }
    return 'Evaluation failed.';
  }

  function resolveErrorHint(error) {
    if (!error || typeof error !== 'object') {
      return '';
    }
    if (typeof error === 'string') {
      return '';
    }
    if (typeof error.reasonCode === 'string' && error.reasonCode.trim().length > 0) {
      return 'Reason code: ' + error.reasonCode.trim();
    }
    if (error.details && typeof error.details === 'object') {
      if (typeof error.details.field === 'string' && error.details.field.trim().length > 0) {
        return 'Field: ' + error.details.field.trim();
      }
    }
    return '';
  }

  function getJson(endpoint) {
    if (typeof global.fetch !== 'function') {
      return Promise.resolve([]);
    }
    return global.fetch(endpoint, {
      method: 'GET',
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    }).then(function (response) {
      if (!response.ok) {
        var error = new Error('Request failed with status ' + response.status);
        error.status = response.status;
        throw error;
      }
      return response.json();
    });
  }

  function postJson(endpoint, payload, csrf) {
    if (typeof global.fetch !== 'function') {
      return Promise.reject(new Error('fetch is not available'));
    }
    var headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
    if (csrf) {
      headers['X-CSRF-TOKEN'] = csrf;
    }
    return global
      .fetch(endpoint, {
        method: 'POST',
        credentials: 'same-origin',
        headers: headers,
        body: JSON.stringify(payload),
      })
      .then(function (response) {
        if (response.ok) {
          if (response.status === 204) {
            return {};
          }
          return response.json();
        }
        return response.json().then(
          function (errorBody) {
            var error = new Error('Request failed');
            error.status = response.status;
            error.body = errorBody;
            throw error;
          },
          function () {
            var error = new Error('Request failed');
            error.status = response.status;
            throw error;
          }
        );
      })
      .catch(function (error) {
        if (error && error.body) {
          throw error.body;
        }
        throw error;
      });
  }
})(window);
