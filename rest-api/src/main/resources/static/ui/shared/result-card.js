(function (global) {
  'use strict';

  var ATTR_HIDDEN = 'hidden';
  var ATTR_ARIA_HIDDEN = 'aria-hidden';
  var MESSAGE_SELECTOR = '[data-result-message]';
  var HINT_SELECTOR = '[data-result-hint]';

  function ensurePanel(panel) {
    if (!panel || !panel.nodeType) {
      return null;
    }
    return panel;
  }

  function findMessage(panel) {
    var host = ensurePanel(panel);
    if (!host) {
      return null;
    }
    return host.querySelector(MESSAGE_SELECTOR);
  }

  function findHint(panel) {
    var host = ensurePanel(panel);
    if (!host) {
      return null;
    }
    return host.querySelector(HINT_SELECTOR);
  }

  function setVisibility(panel, visible) {
    var host = ensurePanel(panel);
    if (!host) {
      return;
    }
    if (visible) {
      host.removeAttribute(ATTR_HIDDEN);
      host.setAttribute(ATTR_ARIA_HIDDEN, 'false');
    } else {
      host.setAttribute(ATTR_HIDDEN, ATTR_HIDDEN);
      host.setAttribute(ATTR_ARIA_HIDDEN, 'true');
    }
  }

  function resetMessage(panel) {
    var messageNode = findMessage(panel);
    var hintNode = findHint(panel);
    if (!messageNode) {
      return;
    }
    messageNode.textContent = '';
    messageNode.setAttribute(ATTR_HIDDEN, ATTR_HIDDEN);
    messageNode.setAttribute(ATTR_ARIA_HIDDEN, 'true');
    messageNode.classList.remove('result-message--error', 'result-message--info');
    if (hintNode) {
      hintNode.textContent = '';
      hintNode.setAttribute(ATTR_HIDDEN, ATTR_HIDDEN);
      hintNode.setAttribute(ATTR_ARIA_HIDDEN, 'true');
    }
  }

  function showMessage(panel, message, tone, options) {
    var host = ensurePanel(panel);
    if (!host) {
      return;
    }
    options = options || {};
    var text = typeof message === 'string' && message.trim().length > 0
        ? message.trim()
        : 'An unexpected validation error occurred.';
    var messageNode = findMessage(host);
    var hintNode = findHint(host);
    if (messageNode) {
      messageNode.textContent = text;
      messageNode.removeAttribute(ATTR_HIDDEN);
      messageNode.setAttribute(ATTR_ARIA_HIDDEN, 'false');
      messageNode.classList.remove('result-message--error', 'result-message--info');
      if (tone === 'info') {
        messageNode.classList.add('result-message--info');
      } else {
        messageNode.classList.add('result-message--error');
      }
    }
    if (hintNode) {
      var hint =
          typeof options.hint === 'string' && options.hint.trim().length > 0
              ? options.hint.trim()
              : '';
      if (hint) {
        hintNode.textContent = hint;
        hintNode.removeAttribute(ATTR_HIDDEN);
        hintNode.setAttribute(ATTR_ARIA_HIDDEN, 'false');
      } else {
        hintNode.textContent = '';
        hintNode.setAttribute(ATTR_HIDDEN, ATTR_HIDDEN);
        hintNode.setAttribute(ATTR_ARIA_HIDDEN, 'true');
      }
    }
    setVisibility(host, true);
  }

  function showPanel(panel) {
    setVisibility(panel, true);
  }

  function hidePanel(panel) {
    setVisibility(panel, false);
  }

  global.ResultCard = {
    showPanel: showPanel,
    hidePanel: hidePanel,
    showMessage: showMessage,
    resetMessage: resetMessage,
  };
})(window);
