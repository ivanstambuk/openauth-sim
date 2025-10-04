(function (global) {
  'use strict';

  var documentRef = global.document;

  function toArray(nodeList) {
    return Array.isArray(nodeList) ? nodeList : Array.prototype.slice.call(nodeList || []);
  }

  function getElement(selector) {
    return documentRef.querySelector(selector);
  }

  function getElements(selector) {
    return toArray(documentRef.querySelectorAll(selector));
  }

  var protocolInfoTrigger = null;
  var protocolInfoSurface = null;
  var protocolInfoBackdrop = null;
  var protocolInfoTitle = null;
  var protocolInfoSubtitle = null;
  var protocolInfoSummary = null;
  var protocolInfoAccordion = null;
  var protocolInfoExpandButton = null;
  var protocolInfoCloseButton = null;
  var protocolInfoDataElement = null;

  var protocolInfoContent = {};
  var protocolInfoSectionState = new Map();
  var protocolInfoStorageNamespace = 'protoInfo.v1';
  var protocolInfoStorageAvailable = false;
  var protocolInfoOpen = false;
  var protocolInfoMode = 'drawer';
  var protocolInfoActiveProtocol = null;
  var protocolInfoLastTrigger = null;
  var protocolInfoLastFocus = null;
  var protocolInfoMounted = false;
  var protocolInfoMotion = 'default';
  var protocolInfoMotionMedia = null;
  var protocolInfoMotionListener = null;
  var protocolInfoHostRoot = null;

  var protocolInfoHooks = {
    onProtocolActivated: null,
  };

  function initElements() {
    protocolInfoTrigger = getElement('.protocol-info-trigger');
    protocolInfoSurface = getElement("[data-testid='protocol-info-surface']");
    protocolInfoBackdrop = getElement("[data-testid='protocol-info-backdrop']");
    protocolInfoTitle =
      protocolInfoSurface && protocolInfoSurface.querySelector("[data-testid='protocol-info-title']");
    protocolInfoSubtitle =
      protocolInfoSurface && protocolInfoSurface.querySelector("[data-testid='protocol-info-subtitle']");
    protocolInfoSummary =
      protocolInfoSurface && protocolInfoSurface.querySelector("[data-testid='protocol-info-summary']");
    protocolInfoAccordion =
      protocolInfoSurface && protocolInfoSurface.querySelector("[data-testid='protocol-info-accordion']");
    protocolInfoExpandButton =
      protocolInfoSurface && protocolInfoSurface.querySelector("[data-testid='protocol-info-expand']");
    protocolInfoCloseButton =
      protocolInfoSurface && protocolInfoSurface.querySelector("[data-testid='protocol-info-close']");
    protocolInfoDataElement = documentRef.getElementById('protocol-info-data');
  }

  function parseProtocolInfoData() {
    if (!protocolInfoDataElement) {
      return {};
    }
    try {
      var parsed = JSON.parse(protocolInfoDataElement.textContent || '{}');
      if (parsed && typeof parsed === 'object' && parsed.protocols) {
        return parsed.protocols;
      }
      return {};
    } catch (error) {
      return {};
    }
  }

  function initProtocolInfoContent() {
    protocolInfoContent = parseProtocolInfoData();
    if (!protocolInfoActiveProtocol) {
      protocolInfoActiveProtocol = ensureProtocolKey(
        protocolInfoSurface && protocolInfoSurface.getAttribute('data-active-protocol')
      );
    }
  }

  function detectStorageAvailability() {
    try {
      var testKey = protocolInfoStorageNamespace + '.self-test';
      global.localStorage.setItem(testKey, '1');
      global.localStorage.removeItem(testKey);
      protocolInfoStorageAvailable = true;
    } catch (error) {
      protocolInfoStorageAvailable = false;
    }
  }

  function protocolInfoStorageKey(segment, protocol) {
    return protocolInfoStorageNamespace + '.' + segment + '.' + protocol;
  }

  function readProtocolInfoPreference(segment, protocol) {
    if (!protocolInfoStorageAvailable || !protocol) {
      return null;
    }
    try {
      return global.localStorage.getItem(protocolInfoStorageKey(segment, protocol));
    } catch (error) {
      return null;
    }
  }

  function writeProtocolInfoPreference(segment, protocol, value) {
    if (!protocolInfoStorageAvailable || !protocol) {
      return;
    }
    try {
      var key = protocolInfoStorageKey(segment, protocol);
      if (value === null || value === undefined) {
        global.localStorage.removeItem(key);
      } else {
        global.localStorage.setItem(key, value);
      }
    } catch (error) {
      // ignore storage exceptions
    }
  }

  function mergeProtocolInfoContent(fragment) {
    if (!fragment || typeof fragment !== 'object') {
      return;
    }
    var protocols =
      fragment.protocols && typeof fragment.protocols === 'object'
        ? fragment.protocols
        : fragment;
    Object.keys(protocols).forEach(function (key) {
      protocolInfoContent[key] = protocols[key];
    });
  }

  function applyProtocolInfoMotionPreference(matches) {
    protocolInfoMotion = matches ? 'reduced' : 'default';
    if (protocolInfoSurface) {
      protocolInfoSurface.setAttribute('data-motion', protocolInfoMotion);
    }
    if (protocolInfoBackdrop) {
      protocolInfoBackdrop.setAttribute('data-motion', protocolInfoMotion);
    }
  }

  function teardownProtocolInfoMotionWatcher() {
    if (!protocolInfoMotionMedia || !protocolInfoMotionListener) {
      return;
    }
    if (typeof protocolInfoMotionMedia.removeEventListener === 'function') {
      protocolInfoMotionMedia.removeEventListener('change', protocolInfoMotionListener);
    } else if (typeof protocolInfoMotionMedia.removeListener === 'function') {
      protocolInfoMotionMedia.removeListener(protocolInfoMotionListener);
    }
    protocolInfoMotionMedia = null;
    protocolInfoMotionListener = null;
  }

  function watchProtocolInfoMotionPreference() {
    teardownProtocolInfoMotionWatcher();
    if (typeof global.matchMedia !== 'function') {
      applyProtocolInfoMotionPreference(false);
      return;
    }
    protocolInfoMotionMedia = global.matchMedia('(prefers-reduced-motion: reduce)');
    applyProtocolInfoMotionPreference(Boolean(protocolInfoMotionMedia.matches));
    protocolInfoMotionListener = function (event) {
      applyProtocolInfoMotionPreference(Boolean(event.matches));
    };
    if (typeof protocolInfoMotionMedia.addEventListener === 'function') {
      protocolInfoMotionMedia.addEventListener('change', protocolInfoMotionListener);
    } else if (typeof protocolInfoMotionMedia.addListener === 'function') {
      protocolInfoMotionMedia.addListener(protocolInfoMotionListener);
    }
  }

  function ensureProtocolKey(protocol) {
    if (
      protocol &&
      protocolInfoContent &&
      Object.prototype.hasOwnProperty.call(protocolInfoContent, protocol)
    ) {
      return protocol;
    }
    var keys = Object.keys(protocolInfoContent || {});
    return keys.length > 0 ? keys[0] : null;
  }

  function getProtocolDescriptor(protocol) {
    var key = ensureProtocolKey(protocol);
    if (!key) {
      return null;
    }
    return protocolInfoContent[key] || null;
  }

  function renderProtocolInfoSummary(descriptor) {
    if (!protocolInfoSummary) {
      return;
    }
    protocolInfoSummary.innerHTML = '';
    if (!descriptor) {
      return;
    }
    var summaryItems = [];
    if (descriptor.subtitle) {
      summaryItems.push(descriptor.subtitle);
    }
    var overviewSection =
      Array.isArray(descriptor.sections)
        ? descriptor.sections.find(function (section) {
            return section.key === 'overview';
          })
        : null;
    if (overviewSection && Array.isArray(overviewSection.paragraphs)) {
      summaryItems.push(overviewSection.paragraphs[0]);
    }
    summaryItems
      .filter(function (text) {
        return typeof text === 'string' && text.trim().length > 0;
      })
      .slice(0, 2)
      .forEach(function (text) {
        var paragraph = documentRef.createElement('p');
        paragraph.textContent = text;
        protocolInfoSummary.appendChild(paragraph);
      });
  }

  function appendProtocolInfoContent(container, section) {
    if (!container || !section) {
      return;
    }
    if (Array.isArray(section.paragraphs)) {
      section.paragraphs
        .filter(function (text) {
          return typeof text === 'string';
        })
        .forEach(function (text) {
          var paragraph = documentRef.createElement('p');
          paragraph.textContent = text;
          container.appendChild(paragraph);
        });
    }
    if (Array.isArray(section.steps)) {
      var stepsList = documentRef.createElement('ol');
      section.steps
        .filter(function (text) {
          return typeof text === 'string';
        })
        .forEach(function (text) {
          var item = documentRef.createElement('li');
          item.textContent = text;
          stepsList.appendChild(item);
        });
      container.appendChild(stepsList);
    }
    if (Array.isArray(section.bullets)) {
      var bulletList = documentRef.createElement('ul');
      section.bullets
        .filter(function (text) {
          return typeof text === 'string';
        })
        .forEach(function (text) {
          var item = documentRef.createElement('li');
          item.textContent = text;
          bulletList.appendChild(item);
        });
      container.appendChild(bulletList);
    }
    if (Array.isArray(section.links)) {
      var linksList = documentRef.createElement('ul');
      linksList.className = 'protocol-info-links';
      section.links
        .filter(function (link) {
          return link && typeof link === 'object';
        })
        .forEach(function (link) {
          var item = documentRef.createElement('li');
          var anchor = documentRef.createElement('a');
          anchor.textContent = link.label || link.url || 'Reference';
          if (link.url) {
            anchor.href = link.url;
            anchor.target = '_blank';
            anchor.rel = 'noopener';
          }
          anchor.addEventListener('click', function () {
            documentRef.dispatchEvent(
              new global.CustomEvent('protocolinfo:spec-click', {
                detail: {
                  protocol: protocolInfoActiveProtocol,
                  href: link && link.url ? link.url : anchor.href || '',
                  label: anchor.textContent || '',
                },
              })
            );
          });
          item.appendChild(anchor);
          linksList.appendChild(item);
        });
      container.appendChild(linksList);
    }
  }

  function renderProtocolInfoSections(descriptor, resetSection) {
    if (!protocolInfoAccordion) {
      return;
    }
    protocolInfoAccordion.innerHTML = '';
    if (!descriptor || !Array.isArray(descriptor.sections)) {
      return;
    }
    var preferredSection = protocolInfoSectionState.get(protocolInfoActiveProtocol);
    var availableKeys = descriptor.sections.map(function (section) {
      return section.key;
    });
    if (resetSection || !preferredSection || availableKeys.indexOf(preferredSection) === -1) {
      var defaultSection = descriptor.sections.find(function (section) {
        return section.defaultOpen;
      });
      preferredSection = defaultSection ? defaultSection.key : availableKeys[0];
    }
    protocolInfoSectionState.set(protocolInfoActiveProtocol, preferredSection);
    if (protocolInfoActiveProtocol && preferredSection) {
      writeProtocolInfoPreference('panel', protocolInfoActiveProtocol, preferredSection);
    }

    descriptor.sections.forEach(function (section) {
      var sectionKey = section.key;
      var sectionElement = documentRef.createElement('section');
      sectionElement.className = 'protocol-info-section';
      sectionElement.setAttribute('data-testid', 'protocol-info-panel-' + sectionKey);
      sectionElement.setAttribute('data-section-key', sectionKey);

      var isOpen = sectionKey === preferredSection;
      sectionElement.setAttribute('data-open', isOpen ? 'true' : 'false');

      var heading = documentRef.createElement('h3');
      heading.className = 'protocol-info-section__heading';

      var headerButton = documentRef.createElement('button');
      headerButton.type = 'button';
      headerButton.className = 'protocol-info-accordion__header';
      headerButton.setAttribute('data-testid', 'protocol-info-accordion-header');
      headerButton.setAttribute('data-section-key', sectionKey);
      headerButton.setAttribute('aria-controls', 'protocol-info-panel-content-' + sectionKey);
      headerButton.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
      headerButton.textContent = section.heading || sectionKey;
      headerButton.addEventListener('click', function () {
        openProtocolInfoSection(sectionKey);
      });
      headerButton.addEventListener('keydown', function (event) {
        if (event.key === ' ' || event.key === 'Enter') {
          event.preventDefault();
          openProtocolInfoSection(sectionKey);
        }
      });

      heading.appendChild(headerButton);
      sectionElement.appendChild(heading);

      var content = documentRef.createElement('div');
      content.className = 'protocol-info-section__content';
      content.id = 'protocol-info-panel-content-' + sectionKey;
      if (!isOpen) {
        content.setAttribute('hidden', 'hidden');
      }

      appendProtocolInfoContent(content, section);
      sectionElement.appendChild(content);

      protocolInfoAccordion.appendChild(sectionElement);
    });
  }

  function renderProtocolInfo(protocol, options) {
    if (!protocolInfoSurface) {
      return;
    }
    var key = ensureProtocolKey(protocol);
    if (!key) {
      return;
    }
    var allowAutoOpen = Boolean(options && options.allowAutoOpen);
    var resetSection = Boolean(options && options.resetSection);
    protocolInfoActiveProtocol = key;
    protocolInfoSurface.setAttribute('data-active-protocol', key);
    var storedPanel = readProtocolInfoPreference('panel', key);
    if (storedPanel) {
      protocolInfoSectionState.set(key, storedPanel);
    }
    var storedSurface = readProtocolInfoPreference('surface', key);
    if (storedSurface) {
      setProtocolInfoMode(storedSurface, { persist: false });
    } else {
      setProtocolInfoMode('drawer', { persist: false });
    }
    var descriptor = getProtocolDescriptor(key);
    if (protocolInfoTitle) {
      protocolInfoTitle.textContent = (descriptor && descriptor.title) || key.toUpperCase();
    }
    if (protocolInfoSubtitle) {
      protocolInfoSubtitle.textContent = (descriptor && descriptor.subtitle) || 'Protocol guidance';
    }
    renderProtocolInfoSummary(descriptor);
    renderProtocolInfoSections(descriptor, resetSection);
    if (protocolInfoOpen && protocolInfoTrigger) {
      protocolInfoLastTrigger = protocolInfoTrigger;
    }
    syncProtocolInfoTriggerState();
    if (allowAutoOpen) {
      maybeAutoOpen(key, { preferredMode: storedSurface });
    }
  }

  function openProtocolInfoSection(sectionKey) {
    if (!protocolInfoAccordion) {
      return;
    }
    var sections = toArray(protocolInfoAccordion.querySelectorAll("[data-testid^='protocol-info-panel-']"));
    sections.forEach(function (sectionElement) {
      var key = sectionElement.getAttribute('data-section-key');
      var isTarget = key === sectionKey;
      var headerButton = sectionElement.querySelector("[data-testid='protocol-info-accordion-header']");
      var content = sectionElement.querySelector('.protocol-info-section__content');
      sectionElement.setAttribute('data-open', isTarget ? 'true' : 'false');
      if (headerButton) {
        headerButton.setAttribute('aria-expanded', isTarget ? 'true' : 'false');
      }
      if (content) {
        if (isTarget) {
          content.removeAttribute('hidden');
        } else {
          content.setAttribute('hidden', 'hidden');
        }
      }
    });
    protocolInfoSectionState.set(protocolInfoActiveProtocol, sectionKey);
    if (protocolInfoActiveProtocol) {
      writeProtocolInfoPreference('panel', protocolInfoActiveProtocol, sectionKey);
    }
  }

  function syncProtocolInfoTriggerState() {
    if (!protocolInfoTrigger) {
      return;
    }
    protocolInfoTrigger.setAttribute('data-protocol', protocolInfoActiveProtocol || '');
    protocolInfoTrigger.setAttribute('aria-expanded', protocolInfoOpen ? 'true' : 'false');
  }

  function setProtocolInfoMode(mode, options) {
    if (!protocolInfoSurface) {
      return;
    }
    protocolInfoMode = mode === 'modal' ? 'modal' : 'drawer';
    protocolInfoSurface.setAttribute('data-surface-mode', protocolInfoMode);
    if (protocolInfoBackdrop) {
      protocolInfoBackdrop.setAttribute('data-mode', protocolInfoMode);
    }
    if (protocolInfoMode === 'modal') {
      protocolInfoSurface.setAttribute('role', 'dialog');
      protocolInfoSurface.setAttribute('aria-modal', 'true');
    } else {
      protocolInfoSurface.setAttribute('role', 'complementary');
      protocolInfoSurface.removeAttribute('aria-modal');
    }
    if (protocolInfoHostRoot) {
      if (protocolInfoOpen && protocolInfoMode === 'modal') {
        protocolInfoHostRoot.setAttribute('aria-hidden', 'true');
      } else {
        protocolInfoHostRoot.removeAttribute('aria-hidden');
      }
    }
    var shouldPersist = !(options && options.persist === false);
    if (shouldPersist && protocolInfoActiveProtocol) {
      writeProtocolInfoPreference('surface', protocolInfoActiveProtocol, protocolInfoMode);
    }
  }

  function maybeAutoOpen(protocol, options) {
    if (!protocol || protocolInfoOpen) {
      return;
    }
    var viewportMatches = typeof global.matchMedia === 'function'
      ? global.matchMedia('(min-width: 992px)').matches
      : global.innerWidth >= 992;
    if (!viewportMatches) {
      return;
    }
    if (readProtocolInfoPreference('seen', protocol) === 'true') {
      return;
    }
    var preferredMode =
      (options && options.preferredMode) || readProtocolInfoPreference('surface', protocol) || 'drawer';
    setProtocolInfoMode(preferredMode, { persist: false });
    var trigger = protocolInfoTrigger;
    if (trigger) {
      protocolInfoLastTrigger = trigger;
      protocolInfoLastFocus = trigger;
    }
    openProtocolInfo({ mode: preferredMode });
  }

  function focusProtocolInfoCloseButton() {
    if (!protocolInfoCloseButton) {
      return;
    }
    protocolInfoCloseButton.focus({ preventScroll: true });
  }

  function focusProtocolInfoSurface() {
    if (!protocolInfoSurface) {
      return;
    }
    var focusable = toArray(
      protocolInfoSurface.querySelectorAll(
        "[data-testid='protocol-info-accordion-header'], button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])"
      )
    );
    var target = focusable.length > 0 ? focusable[0] : protocolInfoSurface;
    if (target && typeof target.focus === 'function') {
      target.focus({ preventScroll: true });
    }
  }

  function openProtocolInfo(options) {
    var config = options || {};
    var desiredMode = config.mode || protocolInfoMode;
    setProtocolInfoMode(desiredMode);
    if (!protocolInfoSurface || !protocolInfoBackdrop) {
      return;
    }
    protocolInfoOpen = true;
    protocolInfoSurface.removeAttribute('hidden');
    protocolInfoSurface.setAttribute('data-open', 'true');
    protocolInfoBackdrop.removeAttribute('hidden');
    protocolInfoBackdrop.setAttribute('data-visible', 'true');
    if (protocolInfoHostRoot && desiredMode === 'modal') {
      protocolInfoHostRoot.setAttribute('aria-hidden', 'true');
    }
    syncProtocolInfoTriggerState();
    writeProtocolInfoPreference('seen', protocolInfoActiveProtocol, 'true');
    documentRef.dispatchEvent(
      new global.CustomEvent('protocolinfo:open', {
        detail: { protocol: protocolInfoActiveProtocol, mode: desiredMode },
      })
    );
    if (desiredMode === 'modal' || config.focusModal) {
      focusProtocolInfoSurface();
    }
  }

  function closeProtocolInfo(options) {
    if (!protocolInfoSurface || !protocolInfoBackdrop) {
      return;
    }
    protocolInfoOpen = false;
    protocolInfoSurface.setAttribute('data-open', 'false');
    protocolInfoSurface.setAttribute('hidden', 'hidden');
    protocolInfoBackdrop.setAttribute('data-visible', 'false');
    protocolInfoBackdrop.setAttribute('hidden', 'hidden');
    if (protocolInfoHostRoot) {
      protocolInfoHostRoot.removeAttribute('aria-hidden');
    }
    syncProtocolInfoTriggerState();
    documentRef.dispatchEvent(
      new global.CustomEvent('protocolinfo:close', {
        detail: { protocol: protocolInfoActiveProtocol },
      })
    );
    var focusTarget = options && options.focusTarget
      ? options.focusTarget
      : protocolInfoLastTrigger || protocolInfoLastFocus;
    if (focusTarget && typeof focusTarget.focus === 'function') {
      focusTarget.focus({ preventScroll: true });
    }
  }

  function trapProtocolInfoFocus(event) {
    if (!protocolInfoSurface) {
      return;
    }
    var focusableSelectors = ['button', '[href]', 'input', 'select', 'textarea', "[tabindex]:not([tabindex='-1'])"];
    var focusableElements = toArray(protocolInfoSurface.querySelectorAll(focusableSelectors.join(','))).filter(
      function (element) {
        if (element.hasAttribute('disabled')) {
          return false;
        }
        if (element.getAttribute('aria-hidden') === 'true') {
          return false;
        }
        var rect = element.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
      }
    );
    if (focusableElements.length === 0) {
      event.preventDefault();
      return;
    }
    var index = focusableElements.indexOf(documentRef.activeElement);
    if (index === -1) {
      index = 0;
    }
    if (event.shiftKey) {
      index = index <= 0 ? focusableElements.length - 1 : index - 1;
    } else {
      index = index === focusableElements.length - 1 ? 0 : index + 1;
    }
    event.preventDefault();
    focusableElements[index].focus({ preventScroll: true });
  }

  function handleProtocolInfoKeydown(event) {
    if (event.key === 'Escape' && protocolInfoOpen) {
      event.preventDefault();
      closeProtocolInfo();
      return;
    }
    var isShortcut = event.code === 'Slash' && event.shiftKey;
    if (isShortcut) {
      event.preventDefault();
      var key = ensureProtocolKey(protocolInfoActiveProtocol);
      if (!key) {
        return;
      }
      if (protocolInfoTrigger) {
        protocolInfoTrigger.setAttribute('data-protocol', key);
        protocolInfoLastTrigger = protocolInfoTrigger;
        protocolInfoLastFocus = protocolInfoTrigger;
      }
      renderProtocolInfo(key, { resetSection: false });
      openProtocolInfo({ mode: 'drawer' });
      return;
    }
    if (protocolInfoOpen && protocolInfoMode === 'modal' && event.key === 'Tab') {
      trapProtocolInfoFocus(event);
    }
  }

  function initProtocolInfo() {
    if (!protocolInfoSurface) {
      return;
    }
    var initialKey = ensureProtocolKey(protocolInfoActiveProtocol);
    if (initialKey) {
      renderProtocolInfo(initialKey, { resetSection: true, allowAutoOpen: true });
    }
    syncProtocolInfoTriggerState();

    if (protocolInfoTrigger) {
      protocolInfoTrigger.addEventListener('click', function (event) {
        event.preventDefault();
        var triggerProtocol = protocolInfoTrigger.getAttribute('data-protocol');
        var resolvedProtocol = ensureProtocolKey(triggerProtocol || protocolInfoActiveProtocol);
        if (!resolvedProtocol) {
          return;
        }
        if (
          protocolInfoOpen &&
          protocolInfoActiveProtocol === resolvedProtocol &&
          protocolInfoMode === 'drawer'
        ) {
          protocolInfoLastTrigger = protocolInfoTrigger;
          closeProtocolInfo();
          return;
        }
        protocolInfoTrigger.setAttribute('data-protocol', resolvedProtocol);
        protocolInfoLastTrigger = protocolInfoTrigger;
        protocolInfoLastFocus = protocolInfoTrigger;
        renderProtocolInfo(resolvedProtocol, { resetSection: false });
        if (typeof protocolInfoHooks.onProtocolActivated === 'function') {
          protocolInfoHooks.onProtocolActivated(resolvedProtocol);
        }
        openProtocolInfo({ mode: 'drawer' });
      });
    }

    if (protocolInfoBackdrop) {
      protocolInfoBackdrop.addEventListener('click', function () {
        closeProtocolInfo();
      });
    }

    if (protocolInfoCloseButton) {
      protocolInfoCloseButton.addEventListener('click', function () {
        closeProtocolInfo();
      });
    }

    if (protocolInfoExpandButton) {
      protocolInfoExpandButton.addEventListener('click', function () {
        if (!protocolInfoOpen) {
          openProtocolInfo({ mode: 'modal', focusModal: true });
          return;
        }
        setProtocolInfoMode('modal');
        focusProtocolInfoCloseButton();
      });
    }

    documentRef.addEventListener('keydown', handleProtocolInfoKeydown);
  }

  function mountProtocolInfo(options) {
    options = options || {};
    if (options.data) {
      mergeProtocolInfoContent(options.data);
    }
    if (typeof options.onProtocolActivated === 'function') {
      protocolInfoHooks.onProtocolActivated = options.onProtocolActivated;
    }
    if (options.root && options.root.nodeType === 1) {
      protocolInfoHostRoot = options.root;
    }
    var refreshPreferences = Boolean(options.refreshPreferences);
    if (refreshPreferences || !protocolInfoMounted) {
      detectStorageAvailability();
    }
    if (!protocolInfoMounted) {
      watchProtocolInfoMotionPreference();
      initProtocolInfo();
      protocolInfoMounted = true;
    } else {
      if (refreshPreferences) {
        watchProtocolInfoMotionPreference();
      }
      if (protocolInfoActiveProtocol) {
        renderProtocolInfo(protocolInfoActiveProtocol, { resetSection: false });
      }
    }
    return { protocol: protocolInfoActiveProtocol };
  }

  function setProtocol(protocol, options) {
    if (!protocolInfoMounted) {
      mountProtocolInfo();
    }
    var resolved = ensureProtocolKey(protocol);
    if (!resolved) {
      return;
    }
    var allowAutoOpen = Boolean(options && options.autoOpen);
    var resetSection = Boolean(options && options.resetSection);
    renderProtocolInfo(resolved, { resetSection: resetSection, allowAutoOpen: allowAutoOpen });
    if (allowAutoOpen && !protocolInfoOpen) {
      maybeAutoOpen(resolved);
    }
    if (options && options.mode) {
      setProtocolInfoMode(options.mode);
    }
    if (options && options.notifyHost && typeof protocolInfoHooks.onProtocolActivated === 'function') {
      protocolInfoHooks.onProtocolActivated(resolved);
    }
  }

  function getDescriptor(protocol) {
    var resolved = ensureProtocolKey(protocol);
    if (!resolved) {
      return null;
    }
    var descriptor = getProtocolDescriptor(resolved);
    if (!descriptor) {
      return null;
    }
    try {
      return JSON.parse(JSON.stringify(descriptor));
    } catch (error) {
      return descriptor;
    }
  }

  initElements();
  initProtocolInfoContent();

  var ProtocolInfoAPI = {
    mount: function (options) {
      return mountProtocolInfo(options);
    },
    open: function (options) {
      options = options || {};
      if (!protocolInfoMounted) {
        this.mount({ root: protocolInfoHostRoot });
      }
      if (options.protocol) {
        setProtocol(options.protocol, {
          autoOpen: false,
          resetSection: Boolean(options.resetSection),
          mode: options.mode,
          notifyHost: false,
        });
      }
      if (options.mode) {
        setProtocolInfoMode(options.mode);
      }
      openProtocolInfo({
        mode: options.mode,
        focusModal: options.focusModal === true,
      });
    },
    close: function (options) {
      if (!protocolInfoMounted) {
        return;
      }
      closeProtocolInfo({ focusTarget: options && options.focusTarget });
    },
    setProtocol: function (protocol, options) {
      setProtocol(protocol, options || {});
    },
    getDescriptor: function (protocol) {
      return getDescriptor(protocol);
    },
    configure: function (options) {
      options = options || {};
      if (typeof options.onProtocolActivated === 'function') {
        protocolInfoHooks.onProtocolActivated = options.onProtocolActivated;
      }
      if (options.root && options.root.nodeType === 1) {
        protocolInfoHostRoot = options.root;
      }
    },
  };

  global.ProtocolInfo = ProtocolInfoAPI;
  ProtocolInfoAPI.mount();
})(window);
