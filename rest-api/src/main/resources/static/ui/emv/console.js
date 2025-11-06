(function (global) {
  'use strict';

  var documentRef = global.document;
  var rootPanel = documentRef.querySelector('[data-protocol-panel="emv"]');
  var form = documentRef.querySelector('[data-testid="emv-form"]');
  var replayForm = documentRef.querySelector('[data-testid="emv-replay-form"]');
  if (!rootPanel || !form) {
    return;
  }

  var verboseConsole = global.VerboseTraceConsole || null;
  var tabsContainer = rootPanel.querySelector('[data-testid="emv-console-tabs"]');
  var evaluateTabButton = tabsContainer
      ? tabsContainer.querySelector('[data-testid="emv-console-tab-evaluate"]')
      : null;
  var replayTabButton = tabsContainer
      ? tabsContainer.querySelector('[data-testid="emv-console-tab-replay"]')
      : null;
  var evaluatePanel = rootPanel.querySelector('[data-emv-panel="evaluate"]');
  var replayPanel = rootPanel.querySelector('[data-emv-panel="replay"]');
  var evaluateEndpoint = form.getAttribute('data-evaluate-endpoint') || '';
  var storedEndpoint = form.getAttribute('data-stored-endpoint') || evaluateEndpoint;
  var credentialsEndpoint = form.getAttribute('data-credentials-endpoint') || '';
  var seedEndpoint = form.getAttribute('data-seed-endpoint') || '';
  var csrfInput = form.querySelector('input[name="_csrf"]');
  var storedSelect = form.querySelector('#emvStoredCredentialId');
  var storedEmptyState = form.querySelector('[data-testid="emv-stored-empty"]');
  var evaluateModeToggle = form.querySelector('[data-testid="emv-evaluate-mode-toggle"]');
  var evaluateModeRadios = evaluateModeToggle
      ? Array.prototype.slice.call(evaluateModeToggle.querySelectorAll('input[name="emvEvaluateMode"]'))
      : [];
  var actionBar = form.querySelector('[data-testid="emv-action-bar"]');
  var evaluateButton = form.querySelector('[data-testid="emv-evaluate-submit"]');
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
  var customerHint = form.querySelector('[data-testid="emv-customer-hint"]');
  var capModeRadios = Array.prototype.slice.call(form.querySelectorAll('input[name="mode"]'));
  var windowBackwardInput = form.querySelector('#emvWindowBackward');
  var windowForwardInput = form.querySelector('#emvWindowForward');
  var storedSubmissionState = createStoredSubmissionState({ snapshot: captureEvaluateSnapshot });
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

  var replayEndpoint = replayForm ? replayForm.getAttribute('data-replay-endpoint') || '' : '';
  var replayCredentialsEndpoint = replayForm
      ? replayForm.getAttribute('data-credentials-endpoint') || credentialsEndpoint
      : credentialsEndpoint;
  var replayCsrfInput = replayForm ? replayForm.querySelector('input[name="_csrf"]') : null;
  var replayModeToggle = replayForm ? replayForm.querySelector('[data-testid="emv-replay-mode-toggle"]') : null;
  var replayModeStoredRadio = replayModeToggle
      ? replayModeToggle.querySelector('#emvReplayModeStored')
      : null;
  var replayModeInlineRadio = replayModeToggle
      ? replayModeToggle.querySelector('#emvReplayModeInline')
      : null;
  var replayStoredSection = replayForm ? replayForm.querySelector('[data-replay-section="stored"]') : null;
  var replayStoredSelect = replayForm ? replayForm.querySelector('#emvReplayStoredCredentialId') : null;
  var replayCapModeRadios = replayForm
      ? Array.prototype.slice.call(replayForm.querySelectorAll('input[name="emvReplayCapMode"]'))
      : [];
  var replayMasterKeyInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-master-key"] input')
      : null;
  var replayAtcInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-atc"] input')
      : null;
  var replayBranchFactorInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-branch-factor"] input')
      : null;
  var replayHeightInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-height"] input')
      : null;
  var replayIvInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-iv"] input')
      : null;
  var replayCdol1Input = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-cdol1"] textarea')
      : null;
  var replayIssuerBitmapInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-issuer-bitmap"] textarea')
      : null;
  var replayChallengeInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-challenge"] input')
      : null;
  var replayReferenceInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-reference"] input')
      : null;
  var replayAmountInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-amount"] input')
      : null;
  var replayCustomerHint = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-customer-hint"]')
      : null;
  var replayIccTemplateInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-icc-template"] textarea')
      : null;
  var replayIssuerApplicationDataInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-issuer-application-data"] textarea')
      : null;
  var replayOtpInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-otp"] input')
      : null;
  var replayDriftBackwardInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-drift-backward"] input')
      : null;
  var replayDriftForwardInput = replayForm
      ? replayForm.querySelector('[data-testid="emv-replay-drift-forward"] input')
      : null;
  var replayResultPanel = rootPanel.querySelector('[data-testid="emv-replay-result-card"]');
  var replayStatusBadge = replayResultPanel
      ? replayResultPanel.querySelector('[data-testid="emv-replay-status"]')
      : null;
  var replayOtpNode = replayResultPanel
      ? replayResultPanel.querySelector('[data-testid="emv-replay-otp"]')
      : null;
  var replayMatchedDeltaNode = replayResultPanel
      ? replayResultPanel.querySelector('[data-testid="emv-replay-matched-delta"]')
      : null;
  var replayReasonNode = replayResultPanel
      ? replayResultPanel.querySelector('[data-testid="emv-replay-reason"]')
      : null;
  var CUSTOMER_HINT_COPY = {
    IDENTIFY: 'Identify mode does not accept customer inputs.',
    RESPOND: 'Respond mode enables Challenge; Reference and Amount remain disabled.',
    SIGN: 'Sign mode enables Reference and Amount; Challenge stays disabled.',
  };

  var evaluateSensitiveFields = buildSensitiveFields([
    { input: masterKeyInput, mask: form.querySelector('[data-testid="emv-master-key-mask"]'), formatter: formatMasterKeyMask },
    { input: cdol1Input, mask: form.querySelector('[data-testid="emv-cdol1-mask"]'), formatter: formatHexMaskFactory('cdol1') },
    { input: ipbInput, mask: form.querySelector('[data-testid="emv-ipb-mask"]'), formatter: formatHexMaskFactory('issuerProprietaryBitmap') },
    { input: iccTemplateInput, mask: form.querySelector('[data-testid="emv-icc-template-mask"]'), formatter: formatHexMaskFactory('iccDataTemplate') },
    { input: issuerApplicationDataInput, mask: form.querySelector('[data-testid="emv-issuer-application-data-mask"]'), formatter: formatHexMaskFactory('issuerApplicationData') },
  ]);

  var replaySensitiveFields = buildSensitiveFields([
    {
      input: replayMasterKeyInput,
      mask: replayForm ? replayForm.querySelector('[data-testid="emv-replay-master-key-mask"]') : null,
      formatter: formatMasterKeyMask,
    },
    {
      input: replayCdol1Input,
      mask: replayForm ? replayForm.querySelector('[data-testid="emv-replay-cdol1-mask"]') : null,
      formatter: formatHexMaskFactory('cdol1'),
    },
    {
      input: replayIssuerBitmapInput,
      mask: replayForm ? replayForm.querySelector('[data-testid="emv-replay-ipb-mask"]') : null,
      formatter: formatHexMaskFactory('issuerProprietaryBitmap'),
    },
    {
      input: replayIccTemplateInput,
      mask: replayForm ? replayForm.querySelector('[data-testid="emv-replay-icc-template-mask"]') : null,
      formatter: formatHexMaskFactory('iccDataTemplate'),
    },
    {
      input: replayIssuerApplicationDataInput,
      mask: replayForm ? replayForm.querySelector('[data-testid="emv-replay-issuer-application-data-mask"]') : null,
      formatter: formatHexMaskFactory('issuerApplicationData'),
    },
  ]);

  var currentStoredSummary = null;
  var currentReplaySummary = null;

  var credentialsCache = Object.create(null);
  var credentialsList = [];
  var loadingCredentials = false;
  var submitting = false;
  var replaySubmitting = false;
  var seeding = false;
  var replayInitialized = false;
  var activePanel = null;

  initializeTabs();

  updateEvaluateSensitiveFields(selectedEvaluateMode());

  if (storedSelect) {
    storedSelect.addEventListener('change', function () {
      if (storedSelect.value) {
        var modeBeforeSelection = selectedEvaluateMode();
        applyCredential(storedSelect.value);
        if (modeBeforeSelection !== 'stored') {
          updateStoredControls();
        }
      } else if (storedSubmissionState && typeof storedSubmissionState.clearBaseline === 'function') {
        storedSubmissionState.clearBaseline();
        currentStoredSummary = null;
        updateStoredControls();
      } else {
        currentStoredSummary = null;
        updateStoredControls();
      }
    });
  }

  if (evaluateModeRadios.length > 0) {
    evaluateModeRadios.forEach(function (input) {
      if (!input) {
        return;
      }
      input.addEventListener('change', function () {
        updateStoredControls();
      });
    });
  }

  if (capModeRadios.length > 0) {
    capModeRadios.forEach(function (input) {
      if (!input) {
        return;
      }
      input.addEventListener('change', function () {
        if (input.checked) {
          updateCustomerInputsForMode(input.value);
        }
      });
    });
  }

  if (seedButton) {
    seedButton.addEventListener('click', function () {
      handleSeedRequest();
    });
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    handleFormSubmit();
  });

  if (replayForm) {
    replayForm.addEventListener('submit', function (event) {
      event.preventDefault();
      handleReplaySubmit();
    });
  }

  updateStoredControls();
  loadCredentials();
  registerTestHooks();

  function loadCredentials() {
    var endpoint = credentialsEndpoint || replayCredentialsEndpoint;
    if (!endpoint || loadingCredentials) {
      return;
    }
    loadingCredentials = true;
    getJson(endpoint)
      .then(function (payload) {
        credentialsCache = Object.create(null);
        credentialsList = Array.isArray(payload) ? payload : [];
        populateStoredSelect(credentialsList);
        if (storedSelect && storedSelect.value && credentialsCache[storedSelect.value]) {
          applyCredential(storedSelect.value);
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
        updateStoredControls();
      });
  }

  function populateStoredSelect(list) {
    var entries = Array.isArray(list) ? list : [];
    entries.forEach(function (summary) {
      if (summary && typeof summary.id === 'string') {
        credentialsCache[summary.id] = summary;
      }
    });
    populateEvaluateStoredSelect(entries);
    populateReplayStoredSelect(entries);
  }

  function populateEvaluateStoredSelect(list) {
    if (!storedSelect) {
      return;
    }
    var previousValue = storedSelect.value || '';
    var entries = Array.isArray(list) ? list : [];
    var placeholderOption = storedSelect.options && storedSelect.options.length ? storedSelect.options[0] : null;
    if (placeholderOption) {
      placeholderOption.textContent =
        entries.length > 0 ? 'Select a preset credential' : 'No stored credentials available';
    }
    if (entries.length === 0) {
      storedSelect.value = '';
    }
    while (storedSelect.options.length > 1) {
      storedSelect.remove(1);
    }
    list.forEach(function (summary) {
      if (!summary || typeof summary.id !== 'string') {
        return;
      }
      var option = documentRef.createElement('option');
      option.value = summary.id;
      option.textContent =
        typeof summary.label === 'string' && summary.label.trim().length > 0
          ? summary.label.trim()
          : summary.id;
      storedSelect.appendChild(option);
    });
    if (entries.length > 0) {
      var hasPrevious = previousValue && entries.some(function (summary) {
        return summary && summary.id === previousValue;
      });
      storedSelect.value = hasPrevious ? previousValue : '';
    }
    if (storedSelect.value && credentialsCache[storedSelect.value]) {
      currentStoredSummary = credentialsCache[storedSelect.value];
    } else {
      currentStoredSummary = null;
    }
    updateStoredControls();
  }

  function populateReplayStoredSelect(list) {
    if (!replayStoredSelect) {
      return;
    }
    var previousValue = replayStoredSelect.value;
    while (replayStoredSelect.options.length > 1) {
      replayStoredSelect.remove(1);
    }
    list.forEach(function (summary) {
      if (!summary || typeof summary.id !== 'string') {
        return;
      }
      var option = documentRef.createElement('option');
      option.value = summary.id;
      option.textContent =
        typeof summary.label === 'string' && summary.label.trim().length > 0
          ? summary.label.trim()
          : summary.id;
      replayStoredSelect.appendChild(option);
    });
    if (previousValue && replayStoredSelect.querySelector('option[value="' + previousValue + '"]')) {
      replayStoredSelect.value = previousValue;
    } else if (!previousValue && list.length > 0) {
      replayStoredSelect.value = list[0].id;
    }
    if (replayStoredSelect && replayStoredSelect.value && credentialsCache[replayStoredSelect.value]) {
      currentReplaySummary = credentialsCache[replayStoredSelect.value];
    } else if (!replayModeIsStored()) {
      currentReplaySummary = null;
    }
    if (replayModeIsStored() && replayStoredSelect.value) {
      applyReplayCredential(replayStoredSelect.value);
    } else if (replayModeIsStored()) {
      currentReplaySummary = null;
      updateReplaySensitiveFields();
    }
  }

  function applyCredential(credentialId) {
    if (!credentialId || !credentialsCache[credentialId]) {
      return;
    }
    var summary = credentialsCache[credentialId];
    currentStoredSummary = summary;
    refreshSensitiveMasks(evaluateSensitiveFields, summary);
    refreshSensitiveMasks(replaySensitiveFields, summary);
    if (typeof storedSelect.value !== 'string' || storedSelect.value !== credentialId) {
      storedSelect.value = credentialId;
    }
    setMode(summary.mode);
    setValue(atcInput, summary.defaultAtc);
    setValue(branchFactorInput, summary.branchFactor);
    setValue(heightInput, summary.height);
    setValue(ivInput, summary.iv);
    clearEvaluateSensitiveInputs();

    if (summary.defaults) {
      setValue(challengeInput, summary.defaults.challenge);
      setValue(referenceInput, summary.defaults.reference);
      setValue(amountInput, summary.defaults.amount);
    }

    applyReplayDefaults(credentialId, summary);

    if (storedSubmissionState && typeof storedSubmissionState.resetBaseline === 'function') {
      storedSubmissionState.resetBaseline(credentialId);
    }
    updateStoredControls();
  }

  function applyReplayDefaults(credentialId, summary) {
    if (!summary) {
      return;
    }
    if (replayStoredSelect && !replayStoredSelect.value && credentialId) {
      replayStoredSelect.value = credentialId;
    }
    if (!replayStoredSelect || !credentialId) {
      applyCredentialSummaryToReplay(summary);
      return;
    }
    if (!replayStoredSelect.value) {
      applyReplayCredential(credentialId);
      return;
    }
    if (replayModeIsStored() && replayStoredSelect.value === credentialId) {
      applyReplayCredential(credentialId);
      return;
    }
    if (!replayInitialized) {
      applyReplayCredential(credentialId);
    }
  }

  function applyReplayCredential(credentialId) {
    if (!credentialId || !credentialsCache[credentialId]) {
      return;
    }
    if (replayStoredSelect && replayStoredSelect.value !== credentialId) {
      replayStoredSelect.value = credentialId;
    }
    var summary = credentialsCache[credentialId];
    currentReplaySummary = summary;
    setReplayCapMode(summary.mode);
    applyCredentialSummaryToReplay(summary);
    updateReplaySensitiveFields();
  }

  function applyCredentialSummaryToReplay(summary) {
    if (!summary) {
      return;
    }
    currentReplaySummary = summary;
    refreshSensitiveMasks(replaySensitiveFields, summary);
    setValue(replayAtcInput, summary.defaultAtc);
    setValue(replayBranchFactorInput, summary.branchFactor);
    setValue(replayHeightInput, summary.height);
    setValue(replayIvInput, summary.iv);
    clearReplaySensitiveInputs();

    if (summary.defaults) {
      setValue(replayChallengeInput, summary.defaults.challenge);
      setValue(replayReferenceInput, summary.defaults.reference);
      setValue(replayAmountInput, summary.defaults.amount);
    } else {
      setValue(replayChallengeInput, '');
      setValue(replayReferenceInput, '');
      setValue(replayAmountInput, '');
    }

    setValue(replayOtpInput, '');
  }

  function initializeTabs() {
    setActivePanel('evaluate');
    if (evaluateTabButton) {
      evaluateTabButton.addEventListener('click', function () {
        setActivePanel('evaluate');
      });
    }
    if (replayTabButton) {
      replayTabButton.addEventListener('click', function () {
        setActivePanel('replay');
      });
    }
  }

  function setActivePanel(panel) {
    if (!panel) {
      return;
    }
    if (panel !== 'evaluate' && panel !== 'replay') {
      panel = 'evaluate';
    }
    if (activePanel === panel) {
      return;
    }
    var previous = activePanel;
    activePanel = panel;
    toggleTabState(evaluateTabButton, panel === 'evaluate');
    toggleTabState(replayTabButton, panel === 'replay');
    setPanelVisibility(evaluatePanel, panel !== 'evaluate');
    setPanelVisibility(replayPanel, panel !== 'replay');
    if (panel === 'replay') {
      initializeReplay();
    }
    if (previous && previous !== panel) {
      clearVerboseTrace();
    }
  }

  function toggleTabState(button, active) {
    if (!button) {
      return;
    }
    button.classList.toggle('mode-pill--active', !!active);
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  }

  function setPanelVisibility(panel, hidden) {
    if (!panel) {
      return;
    }
    if (hidden) {
      panel.setAttribute('hidden', 'hidden');
      panel.setAttribute('aria-hidden', 'true');
    } else {
      panel.removeAttribute('hidden');
      panel.removeAttribute('aria-hidden');
    }
  }

  function initializeReplay() {
    if (!replayForm || replayInitialized) {
      return;
    }
    replayInitialized = true;

    if (replayStoredSelect) {
      replayStoredSelect.addEventListener('change', function () {
        applyReplayCredential(replayStoredSelect.value);
      });
    }

    if (replayModeStoredRadio) {
      replayModeStoredRadio.addEventListener('change', function () {
        if (replayModeStoredRadio.checked) {
          updateReplayMode('stored');
        }
      });
    }
    if (replayModeInlineRadio) {
      replayModeInlineRadio.addEventListener('change', function () {
        if (replayModeInlineRadio.checked) {
          updateReplayMode('inline');
        }
      });
    }

    replayCapModeRadios.forEach(function (radio) {
      if (!radio) {
        return;
      }
      radio.addEventListener('change', function () {
        if (radio.checked) {
          setReplayCapMode(radio.value);
        }
      });
    });

    updateReplayCustomerInputsForMode(selectedReplayCapMode());
    updateReplayMode(replayModeToggle && replayModeToggle.getAttribute('data-mode'));
    if (replayStoredSelect && replayStoredSelect.value) {
      applyReplayCredential(replayStoredSelect.value);
    } else if (credentialsList.length > 0) {
      applyReplayCredential(credentialsList[0].id);
    }
  }

  function selectedReplayMode() {
    if (!replayModeToggle) {
      return 'stored';
    }
    var mode = replayModeToggle.getAttribute('data-mode');
    if (mode === 'inline') {
      return 'inline';
    }
    return 'stored';
  }

  function updateReplayMode(mode) {
    var normalized = mode === 'inline' ? 'inline' : 'stored';
    if (replayModeToggle) {
      replayModeToggle.setAttribute('data-mode', normalized);
    }
    if (replayForm) {
      if (normalized === 'stored') {
        replayForm.classList.add('emv-stored-mode');
      } else {
        replayForm.classList.remove('emv-stored-mode');
      }
    }
    if (replayModeStoredRadio) {
      replayModeStoredRadio.checked = normalized === 'stored';
    }
    if (replayModeInlineRadio) {
      replayModeInlineRadio.checked = normalized === 'inline';
    }
    if (replayStoredSection) {
      setPanelVisibility(replayStoredSection, normalized !== 'stored');
    }
    if (normalized === 'stored' && replayStoredSelect && replayStoredSelect.value) {
      applyReplayCredential(replayStoredSelect.value);
    }
    updateReplaySensitiveFields();
    if (normalized === 'inline') {
      toggleSensitiveFields(replaySensitiveFields, false, null);
    }
    updateReplayCustomerInputsForMode(selectedReplayCapMode());
  }

  function replayModeIsStored() {
    return selectedReplayMode() === 'stored';
  }

  function selectedReplayCapMode() {
    for (var index = 0; index < replayCapModeRadios.length; index += 1) {
      var radio = replayCapModeRadios[index];
      if (radio && radio.checked) {
        return radio.value;
      }
    }
    return 'IDENTIFY';
  }

  function setReplayCapMode(mode) {
    if (!mode) {
      return;
    }
    var normalized = normalizeCapMode(mode);
    for (var index = 0; index < replayCapModeRadios.length; index += 1) {
      var radio = replayCapModeRadios[index];
      if (!radio) {
        continue;
      }
      radio.checked = radio.value === normalized;
    }
    updateReplayCustomerInputsForMode(normalized);
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

  function handleFormSubmit() {
    var mode = selectedEvaluateMode();
    if (mode === 'stored') {
      if (!hasStoredCredential()) {
        return Promise.resolve(null);
      }
      return handleStoredSubmit();
    }
    return handleInlineSubmit();
  }

  function handleInlineSubmit() {
    if (submitting || !evaluateEndpoint) {
      return Promise.resolve(null);
    }
    if (storedSubmissionState && typeof storedSubmissionState.recordInline === 'function') {
      storedSubmissionState.recordInline();
    }
    var payload = buildPayload();
    submitting = true;
    updateStoredControls();
    if (resultPanel && global.ResultCard && typeof global.ResultCard.resetMessage === 'function') {
      global.ResultCard.resetMessage(resultPanel);
    }
    clearPreviewTable();
    verboseBeginRequest();
    return postJson(evaluateEndpoint, payload, csrfToken(csrfInput))
      .then(function (response) {
        renderSuccess(response);
      })
      .catch(function (error) {
        renderFailure(error);
      })
      .then(function () {
        submitting = false;
        updateStoredControls();
      });
  }

  function handleStoredSubmit() {
    if (submitting || !storedEndpoint || !storedSelect) {
      return Promise.resolve(null);
    }
    var credentialId = storedSelect.value;
    if (!credentialId) {
      return Promise.resolve(null);
    }
    if (storedSubmissionState && typeof storedSubmissionState.hasOverrides === 'function') {
      if (storedSubmissionState.hasOverrides(credentialId)) {
        if (typeof storedSubmissionState.recordInline === 'function') {
          storedSubmissionState.recordInline();
        }
        return handleInlineSubmit();
      }
      if (typeof storedSubmissionState.recordStored === 'function') {
        storedSubmissionState.recordStored();
      }
    }
    var payload = storedSubmissionState && typeof storedSubmissionState.buildStoredPayload === 'function'
      ? storedSubmissionState.buildStoredPayload(credentialId, isTraceRequested())
      : { credentialId: credentialId, includeTrace: isTraceRequested() };
    submitting = true;
    updateStoredControls();
    if (resultPanel && global.ResultCard && typeof global.ResultCard.resetMessage === 'function') {
      global.ResultCard.resetMessage(resultPanel);
    }
    clearPreviewTable();
    verboseBeginRequest();
    return postJson(storedEndpoint, payload, csrfToken(csrfInput))
      .then(function (response) {
        renderSuccess(response);
      })
      .catch(function (error) {
        renderFailure(error);
      })
      .then(function () {
        submitting = false;
        updateStoredControls();
      });
  }

  function captureEvaluateSnapshot() {
    var branchFactorValue = parseInteger(branchFactorInput);
    var heightValue = parseInteger(heightInput);
    return {
      mode: selectedMode(),
      masterKey: uppercase(value(masterKeyInput)),
      atc: uppercase(value(atcInput)),
      branchFactor: branchFactorValue == null ? '' : String(branchFactorValue),
      height: heightValue == null ? '' : String(heightValue),
      previewWindowBackward: value(windowBackwardInput),
      previewWindowForward: value(windowForwardInput),
      iv: uppercase(value(ivInput)),
      cdol1: uppercase(value(cdol1Input)),
      issuerProprietaryBitmap: uppercase(value(ipbInput)),
      iccDataTemplate: uppercase(value(iccTemplateInput)),
      issuerApplicationData: uppercase(value(issuerApplicationDataInput)),
      challenge: digitsValue(challengeInput),
      reference: digitsValue(referenceInput),
      amount: digitsValue(amountInput),
    };
  }

  function buildPayload() {
    var mode = selectedMode();
    var credentialId = storedSelect && typeof storedSelect.value === 'string'
      ? storedSelect.value.trim()
      : '';
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

    attachPreviewWindow(payload);

    payload.includeTrace = isTraceRequested();

    if (credentialId) {
      payload.credentialId = credentialId;
    }

    return payload;
  }

  function attachPreviewWindow(payload) {
    if (!payload) {
      return;
    }
    var backward = windowBackwardInput ? parseInteger(windowBackwardInput) : null;
    var forward = windowForwardInput ? parseInteger(windowForwardInput) : null;
    if (backward == null && forward == null) {
      return;
    }
    var windowPayload = {};
    if (backward != null) {
      windowPayload.backward = backward;
    }
    if (forward != null) {
      windowPayload.forward = forward;
    }
    payload.previewWindow = windowPayload;
  }

  function createStoredSubmissionState(options) {
    var snapshotFn = options && typeof options.snapshot === 'function' ? options.snapshot : function () {
      return {};
    };
    var baselineCredential = null;
    var baselineSnapshot = null;
    var lastSubmission = null;

    function normalizeSnapshot(source) {
      if (!source || typeof source !== 'object') {
        return {};
      }
      var normalized = {};
      Object.keys(source).forEach(function (key) {
        var value = source[key];
        if (value == null) {
          normalized[key] = '';
        } else {
          normalized[key] = typeof value === 'string' ? value : String(value);
        }
      });
      return normalized;
    }

    return {
      resetBaseline: function (credentialId) {
        baselineCredential = credentialId || null;
        baselineSnapshot = normalizeSnapshot(snapshotFn());
      },
      clearBaseline: function () {
        baselineCredential = null;
        baselineSnapshot = null;
      },
      hasOverrides: function (credentialId) {
        if (!credentialId || !baselineSnapshot || baselineCredential !== credentialId) {
          return true;
        }
        var current = normalizeSnapshot(snapshotFn());
        var keys = Object.keys(baselineSnapshot);
        for (var index = 0; index < keys.length; index += 1) {
          var key = keys[index];
          if (key === 'previewWindowBackward' || key === 'previewWindowForward') {
            continue;
          }
          if (baselineSnapshot[key] !== current[key]) {
            return true;
          }
        }
        return false;
      },
      buildStoredPayload: function (credentialId, includeTrace) {
        var payload = { credentialId: credentialId };
        if (includeTrace !== undefined) {
          payload.includeTrace = !!includeTrace;
        }
        attachPreviewWindow(payload);
        lastSubmission = 'stored';
        return payload;
      },
      recordInline: function () {
        lastSubmission = 'inline';
      },
      recordStored: function () {
        lastSubmission = 'stored';
      },
      clearSubmission: function () {
        lastSubmission = null;
      },
      lastSubmission: function () {
        return lastSubmission;
      },
    };
  }

  function registerTestHooks() {
    if (!global.EmvConsoleTestHooks) {
      global.EmvConsoleTestHooks = {};
    }
    global.EmvConsoleTestHooks.createStoredSubmissionState = createStoredSubmissionState;
    if (typeof global.EmvConsoleTestHooks.attach === 'function') {
      global.EmvConsoleTestHooks.attach({
        storedState: storedSubmissionState,
        handleStoredSubmit: handleStoredSubmit,
        handleInlineSubmit: handleInlineSubmit,
        storedSelect: storedSelect,
        updateStoredControls: updateStoredControls,
        selectedEvaluateMode: selectedEvaluateMode,
        setEvaluateMode: setEvaluateMode,
        setCapMode: setMode,
        updateCustomerInputsForMode: updateCustomerInputsForMode,
        updateReplayCustomerInputsForMode: updateReplayCustomerInputsForMode,
        selectedReplayMode: selectedReplayMode,
        setReplayMode: updateReplayMode,
        challengeInput: challengeInput,
        referenceInput: referenceInput,
        amountInput: amountInput,
        replayChallengeInput: replayChallengeInput,
        replayReferenceInput: replayReferenceInput,
        replayAmountInput: replayAmountInput,
        customerHint: customerHint,
        replayCustomerHint: replayCustomerHint,
      });
    }
  }

  function isTraceRequested() {
    return isTraceEnabled();
  }

  function isReplayTraceRequested() {
    return isTraceEnabled();
  }

  function isTraceEnabled() {
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

  function verboseApplyResponse(response, variant, context) {
    if (!verboseConsole) {
      return;
    }
    var payload = buildVerboseTracePayload(response, context);
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

  function verboseApplyError(error, context) {
    if (!verboseConsole) {
      return;
    }
    var payload = buildVerboseTracePayload(error, context);
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

  function buildVerboseTracePayload(response, context) {
    var normalizedTrace = normalizeTrace(response, context);
    if (!normalizedTrace) {
      return null;
    }
    return { trace: normalizedTrace };
  }

  function normalizeTrace(response, context) {
    if (!response || !response.trace) {
      return null;
    }
    var trace = response.trace || {};
    if (typeof trace.operation === 'string') {
      var replayMetadata = {};
      if (trace.metadata && typeof trace.metadata === 'object') {
        replayMetadata = Object.assign({}, trace.metadata);
      }
      if (context && context.metadata && typeof context.metadata === 'object') {
        replayMetadata = Object.assign(replayMetadata, context.metadata);
      }
      if (context && context.suppliedOtp) {
        replayMetadata.suppliedOtp = context.suppliedOtp;
      }
      return {
        operation: trace.operation,
        metadata: replayMetadata,
        steps: Array.isArray(trace.steps) ? trace.steps : [],
      };
    }
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
    if (typeof fields.previewWindowBackward === 'number' && !Number.isNaN(fields.previewWindowBackward)) {
      metadata.previewWindowBackward = fields.previewWindowBackward;
    }
    if (typeof fields.previewWindowForward === 'number' && !Number.isNaN(fields.previewWindowForward)) {
      metadata.previewWindowForward = fields.previewWindowForward;
    }
    if (typeof fields.credentialSource === 'string' && fields.credentialSource.trim().length > 0) {
      metadata.credentialSource = fields.credentialSource.trim();
    }
    if (typeof fields.credentialId === 'string' && fields.credentialId.trim().length > 0) {
      metadata.credentialId = fields.credentialId.trim();
    }
    if (typeof trace.maskLength === 'number' && !Number.isNaN(trace.maskLength)) {
      metadata.maskLength = trace.maskLength;
    }
    if (typeof trace.atc === 'string' && trace.atc.trim().length > 0) {
      metadata.atc = trace.atc.trim();
    }
    if (typeof trace.branchFactor === 'number' && !Number.isNaN(trace.branchFactor)) {
      metadata.branchFactor = trace.branchFactor;
    }
    if (typeof trace.height === 'number' && !Number.isNaN(trace.height)) {
      metadata.height = trace.height;
    }
    if (typeof trace.previewWindowBackward === 'number' && !Number.isNaN(trace.previewWindowBackward)) {
      metadata.previewWindowBackward = trace.previewWindowBackward;
    }
    if (typeof trace.previewWindowForward === 'number' && !Number.isNaN(trace.previewWindowForward)) {
      metadata.previewWindowForward = trace.previewWindowForward;
    }

    var steps = [];
    if (trace.masterKeySha256) {
      steps.push({
        id: 'master_key',
        summary: 'Master key digest',
        attributes: { 'masterKey.sha256': trace.masterKeySha256 },
      });
    }
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
    if (context && context.metadata && typeof context.metadata === 'object') {
      metadata = Object.assign(metadata, context.metadata);
    }
    if (context && context.suppliedOtp) {
      metadata.suppliedOtp = context.suppliedOtp;
    }
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

    clearPreviewTable();

    var telemetry = body && body.telemetry ? body.telemetry : null;
    var status = telemetry && telemetry.status ? String(telemetry.status) : 'unknown';
    updateText(statusBadge, formatStatus(status));

    var fields = telemetry && telemetry.fields ? telemetry.fields : {};
    var reason = fields && typeof fields.reason === 'string' ? fields.reason : null;

    if (status === 'success') {
      var counter = fields && typeof fields.atc === 'string' ? fields.atc : '';
      var previews = body && Array.isArray(body.previews) ? body.previews : [];
      renderPreviewList(previews, { counter: counter, otp: body && body.otp ? body.otp : '' });
    } else {
      var reasonCode = telemetry && typeof telemetry.reasonCode === 'string' ? telemetry.reasonCode : '';
      var message = reason || 'Evaluation failed.';
      var hint = reasonCode ? 'Reason code: ' + reasonCode : '';
      if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
        global.ResultCard.showMessage(resultPanel, message, 'error', { hint: hint });
      }
    }

    if (isTraceRequested() && body && body.trace) {
      verboseApplyResponse(body, 'info', null);
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
    clearPreviewTable();
    updateText(statusBadge, 'Error');
    verboseApplyError(error, null);

    var message = resolveErrorMessage(error);
    var hint = resolveErrorHint(error);
    if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
      global.ResultCard.showMessage(resultPanel, message, 'error', { hint: hint });
    }
  }

  function clearPreviewTable() {
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

  function normalizeDelta(value) {
    if (typeof value === 'number' && isFinite(value)) {
      return value;
    }
    if (typeof value === 'string') {
      var parsed = parseInt(value, 10);
      return isNaN(parsed) ? null : parsed;
    }
    return null;
  }

  function formatDeltaDisplay(delta) {
    var normalized = normalizeDelta(delta);
    if (normalized == null) {
      return '—';
    }
    if (normalized === 0) {
      return '[0]';
    }
    if (normalized > 0) {
      return '+' + normalized;
    }
    return String(normalized);
  }

  function formatDeltaLabel(delta) {
    var normalized = normalizeDelta(delta);
    if (normalized == null) {
      return 'Delta unavailable';
    }
    if (normalized === 0) {
      return 'Delta zero';
    }
    if (normalized > 0) {
      return 'Delta plus ' + normalized;
    }
    return 'Delta minus ' + Math.abs(normalized);
  }

  function renderPreviewList(previews, fallback) {
    if (!resultPreviewBody || !resultPreviewContainer) {
      return;
    }
    clearPreviewTable();
    var entries = Array.isArray(previews) ? previews.slice() : [];
    if (!entries.length && fallback) {
      entries.push({
        counter: fallback.counter || null,
        delta: 0,
        otp: fallback.otp || null,
      });
    }
    entries.forEach(function (entry) {
      var row = documentRef.createElement('tr');
      row.className = 'result-preview__row';
      var deltaValue = normalizeDelta(entry && entry.delta);
      if (deltaValue === 0) {
        row.classList.add('result-preview__row--active');
      }
      if (deltaValue != null) {
        row.setAttribute('data-delta', String(deltaValue));
      }

      var counterCell = documentRef.createElement('th');
      counterCell.scope = 'row';
      counterCell.className = 'result-preview__cell result-preview__cell--counter';
      counterCell.textContent = normalizePreviewValue(entry && entry.counter);
      row.appendChild(counterCell);

      var deltaCell = documentRef.createElement('td');
      deltaCell.className = 'result-preview__cell result-preview__cell--delta';
      deltaCell.textContent = formatDeltaDisplay(deltaValue);
      deltaCell.setAttribute('aria-label', formatDeltaLabel(deltaValue));
      row.appendChild(deltaCell);

      var otpCell = documentRef.createElement('td');
      otpCell.className = 'result-preview__cell result-preview__cell--otp';
      otpCell.textContent = normalizePreviewValue(entry && entry.otp);
      if (deltaValue === 0) {
        otpCell.setAttribute('data-testid', 'emv-otp');
      }
      row.appendChild(otpCell);

      resultPreviewBody.appendChild(row);
    });
    if (entries.length > 0) {
      resultPreviewContainer.removeAttribute('hidden');
      resultPreviewContainer.setAttribute('aria-hidden', 'false');
    }
  }

  function clearReplayResult() {
    if (!replayResultPanel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.resetMessage === 'function') {
      global.ResultCard.resetMessage(replayResultPanel);
    }
    updateText(replayStatusBadge, '—');
    updateText(replayOtpNode, '—');
    if (replayMatchedDeltaNode) {
      replayMatchedDeltaNode.textContent = '';
      replayMatchedDeltaNode.setAttribute('hidden', 'hidden');
      replayMatchedDeltaNode.setAttribute('aria-hidden', 'true');
    }
    if (replayReasonNode) {
      replayReasonNode.textContent = '';
      replayReasonNode.setAttribute('hidden', 'hidden');
      replayReasonNode.setAttribute('aria-hidden', 'true');
    }
  }

  function handleReplaySubmit() {
    if (replaySubmitting || !replayEndpoint) {
      return;
    }
    var payload = buildReplayPayload();
    replaySubmitting = true;
    clearReplayResult();
    verboseBeginRequest();
    postJson(replayEndpoint, payload.body, csrfToken(replayCsrfInput || csrfInput))
      .then(function (response) {
        renderReplaySuccess(response, payload.context);
      })
      .catch(function (error) {
        renderReplayFailure(error, payload.context);
      })
      .then(function () {
        replaySubmitting = false;
      });
  }

  function buildReplayPayload() {
    var capMode = selectedReplayCapMode();
    var credentialMode = selectedReplayMode();
    var credentialId = replayStoredSelect ? replayStoredSelect.value : '';
    var otpText = digitsValue(replayOtpInput);
    var driftBackward = parseInteger(replayDriftBackwardInput);
    var driftForward = parseInteger(replayDriftForwardInput);

    var payload = {
      mode: capMode,
      otp: otpText,
      driftBackward: typeof driftBackward === 'number' ? driftBackward : 0,
      driftForward: typeof driftForward === 'number' ? driftForward : 0,
    };

    if (!isReplayTraceRequested()) {
      payload.includeTrace = false;
    }

    if (credentialMode === 'stored' && credentialId) {
      payload.credentialId = credentialId;
    }

    if (credentialMode !== 'stored') {
      payload.masterKey = uppercase(value(replayMasterKeyInput));
      payload.atc = uppercase(value(replayAtcInput));
      payload.branchFactor = parseInteger(replayBranchFactorInput);
      payload.height = parseInteger(replayHeightInput);
      payload.iv = uppercase(value(replayIvInput));
      payload.cdol1 = uppercase(value(replayCdol1Input));
      payload.issuerProprietaryBitmap = uppercase(value(replayIssuerBitmapInput));
      payload.iccDataTemplate = uppercase(value(replayIccTemplateInput));
      payload.issuerApplicationData = uppercase(value(replayIssuerApplicationDataInput));
    }

    var customerInputs = {
      challenge: digitsValue(replayChallengeInput),
      reference: digitsValue(replayReferenceInput),
      amount: digitsValue(replayAmountInput),
    };
    if (customerInputs.challenge || customerInputs.reference || customerInputs.amount) {
      payload.customerInputs = customerInputs;
    }

    var context = {
      suppliedOtp: otpText,
      metadata: {
        credentialSource: credentialMode === 'stored' ? 'stored' : 'inline',
        mode: capMode,
      },
    };
    if (payload.credentialId) {
      context.metadata.credentialId = payload.credentialId;
    }

    return { body: payload, context: context };
  }

  function renderReplaySuccess(body, requestContext) {
    if (!replayResultPanel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.showPanel === 'function') {
      global.ResultCard.showPanel(replayResultPanel);
    } else {
      replayResultPanel.removeAttribute('hidden');
      replayResultPanel.setAttribute('aria-hidden', 'false');
    }

    var status = body && body.status ? String(body.status) : 'unknown';
    updateText(replayStatusBadge, formatReplayStatus(status));

    var suppliedOtp = requestContext && requestContext.suppliedOtp ? requestContext.suppliedOtp : '';
    var responseMetadata = body && body.metadata && typeof body.metadata === 'object' ? body.metadata : {};

    if (status === 'match') {
      updateText(replayOtpNode, suppliedOtp ? 'Matched OTP: ' + suppliedOtp : 'Matched OTP');
      if (replayMatchedDeltaNode) {
        var delta = typeof responseMetadata.matchedDelta === 'number' ? responseMetadata.matchedDelta : 0;
        replayMatchedDeltaNode.textContent = 'Δ = ' + delta;
        replayMatchedDeltaNode.removeAttribute('hidden');
        replayMatchedDeltaNode.removeAttribute('aria-hidden');
      }
      if (replayReasonNode) {
        replayReasonNode.textContent = '';
        replayReasonNode.setAttribute('hidden', 'hidden');
        replayReasonNode.setAttribute('aria-hidden', 'true');
      }
    } else if (status === 'mismatch') {
      updateText(replayOtpNode, suppliedOtp ? 'Supplied OTP: ' + suppliedOtp : 'Supplied OTP');
      if (replayMatchedDeltaNode) {
        replayMatchedDeltaNode.textContent = '';
        replayMatchedDeltaNode.setAttribute('hidden', 'hidden');
        replayMatchedDeltaNode.setAttribute('aria-hidden', 'true');
      }
      var mismatchMessage = resolveReplayMessage(body && body.reasonCode ? body.reasonCode : 'otp_mismatch');
      if (replayReasonNode) {
        replayReasonNode.textContent = mismatchMessage;
        replayReasonNode.removeAttribute('hidden');
        replayReasonNode.removeAttribute('aria-hidden');
      }
    } else {
      updateText(replayOtpNode, suppliedOtp ? 'Supplied OTP: ' + suppliedOtp : 'Supplied OTP');
      if (replayMatchedDeltaNode) {
        replayMatchedDeltaNode.textContent = '';
        replayMatchedDeltaNode.setAttribute('hidden', 'hidden');
        replayMatchedDeltaNode.setAttribute('aria-hidden', 'true');
      }
    }

    var traceContext = {
      suppliedOtp: suppliedOtp,
      metadata: Object.assign({}, requestContext && requestContext.metadata ? requestContext.metadata : {}, responseMetadata),
    };
    if (isReplayTraceRequested() && body && body.trace) {
      verboseApplyResponse(body, 'info', traceContext);
    } else {
      clearVerboseTrace();
    }
  }

  function renderReplayFailure(error, requestContext) {
    if (!replayResultPanel) {
      return;
    }
    if (global.ResultCard && typeof global.ResultCard.showPanel === 'function') {
      global.ResultCard.showPanel(replayResultPanel);
    } else {
      replayResultPanel.removeAttribute('hidden');
      replayResultPanel.setAttribute('aria-hidden', 'false');
    }

    updateText(replayStatusBadge, 'Error');
    var suppliedOtp = requestContext && requestContext.suppliedOtp ? requestContext.suppliedOtp : '';
    updateText(replayOtpNode, suppliedOtp ? 'Supplied OTP: ' + suppliedOtp : 'Supplied OTP');
    if (replayMatchedDeltaNode) {
      replayMatchedDeltaNode.textContent = '';
      replayMatchedDeltaNode.setAttribute('hidden', 'hidden');
      replayMatchedDeltaNode.setAttribute('aria-hidden', 'true');
    }
    var message = resolveErrorMessage(error);
    var hint = resolveErrorHint(error);
    if (global.ResultCard && typeof global.ResultCard.showMessage === 'function') {
      global.ResultCard.showMessage(replayResultPanel, message, 'error', { hint: hint });
    }
    if (replayReasonNode) {
      replayReasonNode.textContent = message;
      replayReasonNode.removeAttribute('hidden');
      replayReasonNode.removeAttribute('aria-hidden');
    }
    verboseApplyError(error, {
      suppliedOtp: suppliedOtp,
      metadata: Object.assign({}, requestContext && requestContext.metadata ? requestContext.metadata : {}),
    });
  }

  function formatReplayStatus(status) {
    if (typeof status !== 'string') {
      return 'Unknown';
    }
    var normalized = status.trim().toLowerCase();
    if (normalized === 'match') {
      return 'Match';
    }
    if (normalized === 'mismatch') {
      return 'Mismatch';
    }
    if (normalized === 'error') {
      return 'Error';
    }
    return normalized.length ? normalized.charAt(0).toUpperCase() + normalized.slice(1) : 'Unknown';
  }

  function resolveReplayMessage(reasonCode) {
    if (typeof reasonCode !== 'string') {
      return 'Replay failed.';
    }
    if (reasonCode === 'otp_mismatch') {
      return 'OTP mismatch';
    }
    return reasonCode.replace(/_/g, ' ');
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

  function updateEvaluateSensitiveFields(mode) {
    var hideInputs = mode === 'stored';
    toggleSensitiveFields(evaluateSensitiveFields, hideInputs, hideInputs ? currentStoredSummary : null);
  }

  function updateReplaySensitiveFields() {
    var storedMode = replayModeStoredRadio
      ? !!replayModeStoredRadio.checked
      : replayModeIsStored();
    toggleSensitiveFields(replaySensitiveFields, storedMode, storedMode ? currentReplaySummary : null);
    if (!storedMode && replayForm) {
      replayForm.classList.remove('emv-stored-mode');
    }
  }

  function refreshSensitiveMasks(fields, summary) {
    if (!Array.isArray(fields)) {
      return;
    }
    fields.forEach(function (field) {
      if (field && typeof field.update === 'function') {
        field.update(summary);
      }
    });
  }

  function clearEvaluateSensitiveInputs() {
    setValue(masterKeyInput, '');
    setValue(cdol1Input, '');
    setValue(ipbInput, '');
    setValue(iccTemplateInput, '');
    setValue(issuerApplicationDataInput, '');
  }

  function clearReplaySensitiveInputs() {
    setValue(replayMasterKeyInput, '');
    setValue(replayCdol1Input, '');
    setValue(replayIssuerBitmapInput, '');
    setValue(replayIccTemplateInput, '');
    setValue(replayIssuerApplicationDataInput, '');
  }

  function toggleSensitiveFields(fields, hideInputs, summary) {
    if (!Array.isArray(fields) || fields.length === 0) {
      return;
    }
    fields.forEach(function (field) {
      if (!field || !field.input) {
        return;
      }
      if (typeof field.update === 'function') {
        field.update(summary);
      }
      if (global.console && typeof global.console.log === 'function') {
        global.console.log('[emv] toggleSensitiveFields', field.input.id || 'unknown', hideInputs);
      }
      if (hideInputs) {
        applyStoredVisibility(field);
      } else {
        applyInlineVisibility(field);
      }
    });
  }

  function applyStoredVisibility(field) {
    setFieldVisibility(field, true);
    field.input.setAttribute('aria-hidden', 'true');
    field.input.style.opacity = '';
    field.input.style.pointerEvents = 'none';
    field.input.style.userSelect = 'none';
    field.input.style.display = '';
    field.input.setAttribute('tabindex', '-1');
    field.input.setAttribute('data-secret-mode', 'stored');
    if (field.mask) {
      field.mask.style.display = 'none';
    }
  }

  function applyInlineVisibility(field) {
    setFieldVisibility(field, false);
    field.input.removeAttribute('aria-hidden');
    field.input.style.opacity = '';
    field.input.style.pointerEvents = '';
    field.input.style.userSelect = '';
    field.input.style.display = '';
    field.input.removeAttribute('tabindex');
    field.input.setAttribute('data-secret-mode', 'inline');
    if (field.mask) {
      field.mask.style.display = 'none';
    }
  }

  function setFieldVisibility(field, hidden) {
    if (field.container) {
      if (hidden) {
        field.container.setAttribute('hidden', 'hidden');
        field.container.setAttribute('aria-hidden', 'true');
      } else {
        field.container.removeAttribute('hidden');
        field.container.removeAttribute('aria-hidden');
      }
    }
    if (field.mask) {
      if (hidden) {
        field.mask.setAttribute('hidden', 'hidden');
        field.mask.setAttribute('aria-hidden', 'true');
      } else {
        field.mask.removeAttribute('hidden');
        field.mask.removeAttribute('aria-hidden');
      }
    }
  }

  function normalizeCapMode(mode) {
    if (typeof mode !== 'string') {
      return 'IDENTIFY';
    }
    var normalized = mode.trim().toUpperCase();
    if (normalized === 'RESPOND' || normalized === 'SIGN') {
      return normalized;
    }
    return 'IDENTIFY';
  }

  function updateCustomerInputsForMode(mode) {
    var normalized = normalizeCapMode(mode || selectedMode());
    if (normalized === 'RESPOND') {
      setCustomerFieldState(challengeInput, true);
      setCustomerFieldState(referenceInput, false);
      setValue(referenceInput, '');
      setCustomerFieldState(amountInput, false);
      setValue(amountInput, '');
    } else if (normalized === 'SIGN') {
      setCustomerFieldState(challengeInput, false);
      setCustomerFieldState(referenceInput, true);
      setCustomerFieldState(amountInput, true);
    } else {
      setCustomerFieldState(challengeInput, false);
      setCustomerFieldState(referenceInput, false);
      setCustomerFieldState(amountInput, false);
      setValue(challengeInput, '');
      setValue(referenceInput, '');
      setValue(amountInput, '');
    }
    updateCustomerHintMessage(customerHint, normalized);
  }

  function updateReplayCustomerInputsForMode(mode) {
    var normalized = normalizeCapMode(mode || selectedReplayCapMode());
    if (normalized === 'RESPOND') {
      setCustomerFieldState(replayChallengeInput, true);
      setCustomerFieldState(replayReferenceInput, false);
      setValue(replayReferenceInput, '');
      setCustomerFieldState(replayAmountInput, false);
      setValue(replayAmountInput, '');
    } else if (normalized === 'SIGN') {
      setCustomerFieldState(replayChallengeInput, false);
      setCustomerFieldState(replayReferenceInput, true);
      setCustomerFieldState(replayAmountInput, true);
    } else {
      setCustomerFieldState(replayChallengeInput, false);
      setCustomerFieldState(replayReferenceInput, false);
      setCustomerFieldState(replayAmountInput, false);
      setValue(replayChallengeInput, '');
      setValue(replayReferenceInput, '');
      setValue(replayAmountInput, '');
    }
    updateCustomerHintMessage(replayCustomerHint, normalized);
  }

  function setCustomerFieldState(input, enabled) {
    if (!input) {
      return;
    }
    if (enabled) {
      input.disabled = false;
      input.removeAttribute('disabled');
      input.removeAttribute('aria-disabled');
    } else {
      input.disabled = true;
      input.setAttribute('disabled', 'disabled');
      input.setAttribute('aria-disabled', 'true');
    }
    var container = typeof input.closest === 'function' ? input.closest('.field-group') : null;
    if (container) {
      if (enabled) {
        container.removeAttribute('aria-disabled');
      } else {
        container.setAttribute('aria-disabled', 'true');
      }
    }
  }

  function updateCustomerHintMessage(target, mode) {
    if (!target) {
      return;
    }
    var normalized = normalizeCapMode(mode);
    var message = CUSTOMER_HINT_COPY[normalized] || CUSTOMER_HINT_COPY.IDENTIFY;
    if (target.textContent !== message) {
      target.textContent = message;
    }
    target.setAttribute('data-mode', normalized);
  }

  function buildSensitiveFields(definitions) {
    if (!Array.isArray(definitions)) {
      return [];
    }
    return definitions
      .map(function (definition) {
        if (!definition || !definition.input || !definition.mask) {
          return null;
        }
        var valueNode = definition.mask.querySelector('[data-mask-value]');
        var formatter = typeof definition.formatter === 'function'
          ? definition.formatter
          : defaultMaskFormatter;
        var container = definition.container;
        if (!container && definition.input && typeof definition.input.closest === 'function') {
          container = definition.input.closest('.field-group');
        }
        return {
          input: definition.input,
          mask: definition.mask,
          container: container,
          valueNode: valueNode,
          update: function (summary) {
            if (!this.valueNode) {
              return;
            }
            var text = formatter(summary);
            if (!text || !String(text).trim()) {
              text = defaultMaskFormatter(summary);
            }
            this.valueNode.textContent = text;
          },
        };
      })
      .filter(Boolean);
  }

  function defaultMaskFormatter(summary) {
    return summary ? 'Hidden (value available)' : 'Select a stored credential to view masked value.';
  }

  function formatMasterKeyMask(summary) {
    if (!summary || typeof summary.masterKeySha256 !== 'string') {
      return '';
    }
    var digest = summary.masterKeySha256.trim();
    return digest.length ? digest : 'sha256:UNAVAILABLE';
  }

  function formatHexMaskFactory(property) {
    return function (summary) {
      if (!summary) {
        return '';
      }
      var lengthProperty = property + 'HexLength';
      var lengthValue = summary[lengthProperty];
      if (typeof lengthValue === 'number' && lengthValue > 0) {
        return 'Hidden (' + lengthValue + ' hex chars)';
      }
      var raw = summary[property];
      if (typeof raw === 'string') {
        var trimmed = raw.trim();
        if (trimmed.length) {
          return 'Hidden (' + trimmed.length + ' hex chars)';
        }
      }
      return 'Hidden (no stored value)';
    };
  }

  function updateStoredControls() {
    var mode = selectedEvaluateMode();
    if (form) {
      if (mode === 'stored') {
        form.classList.add('emv-stored-mode');
      } else {
        form.classList.remove('emv-stored-mode');
      }
    }
    if (evaluateModeToggle) {
      evaluateModeToggle.setAttribute('data-mode', mode);
    }
    if (actionBar) {
      actionBar.setAttribute('data-mode', mode);
    }
    updateEvaluateButton(mode);
    updateEvaluationHint(mode);
    updateSeedControls(mode);
    updateEvaluateSensitiveFields(mode);
    updateCustomerInputsForMode(selectedMode());
  }

  function updateEvaluateButton(mode) {
    if (!evaluateButton) {
      return;
    }
    var label = mode === 'stored' ? 'Evaluate stored credential' : 'Evaluate inline parameters';
    if (evaluateButton.textContent !== label) {
      evaluateButton.textContent = label;
    }
    if (submitting) {
      evaluateButton.setAttribute('disabled', 'disabled');
      return;
    }
    if (mode === 'stored' && !hasStoredCredential()) {
      evaluateButton.setAttribute('disabled', 'disabled');
    } else {
      evaluateButton.removeAttribute('disabled');
    }
  }

  function updateEvaluationHint(mode) {
    if (!storedEmptyState) {
      return;
    }
    if (credentialsList.length === 0) {
      storedEmptyState.textContent =
        'No stored credentials available. Seed sample credentials to enable preset evaluation.';
      return;
    }
    if (mode === 'stored') {
      if (!hasStoredCredential()) {
        storedEmptyState.textContent = 'Select a stored credential to evaluate a preset.';
        return;
      }
      storedEmptyState.textContent =
        'Stored evaluations send the selected credential. Modify any field to fall back to inline parameters.';
      return;
    }
    storedEmptyState.textContent =
      'Inline evaluation uses the parameters entered above. Selecting a sample populates inline fields; switch to stored mode to evaluate the preset.';
  }

  function updateSeedControls(mode) {
    if (!seedActions) {
      return;
    }
    var storedActive = mode === 'stored';
    if (storedActive) {
      seedActions.removeAttribute('hidden');
      seedActions.removeAttribute('aria-hidden');
      if (seedButton) {
        if (seeding) {
          seedButton.setAttribute('disabled', 'disabled');
        } else {
          seedButton.removeAttribute('disabled');
        }
      }
    } else {
      seedActions.setAttribute('hidden', 'hidden');
      seedActions.setAttribute('aria-hidden', 'true');
      if (seedButton) {
        seedButton.setAttribute('disabled', 'disabled');
      }
      updateSeedStatus('');
    }
  }

  function hasStoredCredential() {
    return (
      !!storedSelect &&
      typeof storedSelect.value === 'string' &&
      storedSelect.value.trim().length > 0
    );
  }

  function setEvaluateMode(mode) {
    if (!evaluateModeRadios || evaluateModeRadios.length === 0) {
      return;
    }
    var normalized = mode === 'stored' ? 'stored' : 'inline';
    for (var index = 0; index < evaluateModeRadios.length; index += 1) {
      var input = evaluateModeRadios[index];
      if (!input) {
        continue;
      }
      input.checked = input.value === normalized;
    }
    updateStoredControls();
  }

  function selectedEvaluateMode() {
    if (!evaluateModeRadios || evaluateModeRadios.length === 0) {
      return 'inline';
    }
    for (var index = 0; index < evaluateModeRadios.length; index += 1) {
      var input = evaluateModeRadios[index];
      if (input && input.checked) {
        return input.value === 'stored' ? 'stored' : 'inline';
      }
    }
    return 'inline';
  }

  function setMode(mode) {
    if (!mode) {
      return;
    }
    var normalized = normalizeCapMode(mode);
    var modeInputs = form.querySelectorAll('input[name="mode"]');
    for (var index = 0; index < modeInputs.length; index += 1) {
      var input = modeInputs[index];
      if (!input) {
        continue;
      }
      input.checked = input.value === normalized;
    }
    updateCustomerInputsForMode(normalized);
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
