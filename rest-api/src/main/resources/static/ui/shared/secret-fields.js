(function (global) {
  'use strict';

  var BASE32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  var BASE32_VALUES = Object.create(null);
  for (var i = 0; i < BASE32_ALPHABET.length; i += 1) {
    BASE32_VALUES[BASE32_ALPHABET.charAt(i)] = i;
  }

  var HEX_PATTERN = /^[0-9A-F]+$/;

  function hasText(value) {
    return typeof value === 'string' && value.trim().length > 0;
  }

  function normalizeBase32(input) {
    if (!hasText(input)) {
      return '';
    }
    var stripped = '';
    var padding = '';
    var encounteredPadding = false;
    for (var i = 0; i < input.length; i += 1) {
      var ch = input.charAt(i);
      if (ch === '\u200B' || ch === '-' || ch === '_' || ch === '\t' || ch === '\r' || ch === '\n') {
        continue;
      }
      if (ch === ' ') {
        continue;
      }
      if (ch === '=') {
        encounteredPadding = true;
        padding += '=';
        continue;
      }
      if (encounteredPadding) {
        throw new Error('Base32 padding may appear only at the end');
      }
      stripped += ch.toUpperCase();
    }
    if (!stripped) {
      return '';
    }
    return stripped + padding;
  }

  function validateHexValue(value) {
    if (!hasText(value)) {
      return null;
    }
    var trimmed = value.trim().toUpperCase();
    if (!HEX_PATTERN.test(trimmed)) {
      return 'Hex values may only contain 0-9 and A-F characters.';
    }
    if (trimmed.length % 2 !== 0) {
      return 'Hex values must contain an even number of characters.';
    }
    return null;
  }

  function hexToBytes(hexValue) {
    var normalized = hexValue.trim().toUpperCase();
    if (!HEX_PATTERN.test(normalized)) {
      throw new Error('Hex values may only contain 0-9 and A-F characters.');
    }
    if (normalized.length % 2 !== 0) {
      throw new Error('Hex values must contain an even number of characters.');
    }
    var bytes = [];
    for (var i = 0; i < normalized.length; i += 2) {
      bytes.push(parseInt(normalized.substring(i, i + 2), 16));
    }
    return {
      bytes: bytes,
      normalized: normalized
    };
  }

  function bytesToHex(bytes) {
    var hex = '';
    for (var i = 0; i < bytes.length; i += 1) {
      var segment = bytes[i].toString(16).toUpperCase();
      if (segment.length < 2) {
        segment = '0' + segment;
      }
      hex += segment;
    }
    return hex;
  }

  function bytesToBase32(bytes) {
    if (!bytes || bytes.length === 0) {
      return '';
    }
    var output = '';
    var buffer = 0;
    var bits = 0;
    for (var i = 0; i < bytes.length; i += 1) {
      buffer = (buffer << 8) | (bytes[i] & 0xff);
      bits += 8;
      while (bits >= 5) {
        bits -= 5;
        output += BASE32_ALPHABET.charAt((buffer >> bits) & 31);
      }
    }
    if (bits > 0) {
      output += BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 31);
    }
    var remainder = output.length % 8;
    if (remainder > 0) {
      output += new Array(8 - remainder + 1).join('=');
    }
    return output;
  }

  function base32ToHex(value) {
    var normalized = normalizeBase32(value);
    if (!normalized) {
      throw new Error('Base32 value must not be blank');
    }
    var paddingIndex = normalized.indexOf('=');
    var core = paddingIndex >= 0 ? normalized.substring(0, paddingIndex) : normalized;
    var bits = 0;
    var buffer = 0;
    var outputBytes = [];
    for (var i = 0; i < core.length; i += 1) {
      var ch = core.charAt(i);
      if (!Object.prototype.hasOwnProperty.call(BASE32_VALUES, ch)) {
        throw new Error('Base32 values may only contain A-Z and 2-7 characters.');
      }
      buffer = (buffer << 5) | BASE32_VALUES[ch];
      bits += 5;
      if (bits >= 8) {
        bits -= 8;
        outputBytes.push((buffer >> bits) & 0xff);
        buffer &= (1 << bits) - 1;
      }
    }
    if (bits > 0 && ((buffer << (8 - bits)) & 0xff) !== 0) {
      throw new Error('Base32 value has invalid trailing bits.');
    }
    return {
      hex: bytesToHex(outputBytes),
      normalized: paddingIndex >= 0 ? core + normalized.substring(paddingIndex) : core
    };
  }

  function hexToBase32(value) {
    if (!hasText(value)) {
      throw new Error('Hex value must not be blank');
    }
    var result = hexToBytes(value);
    return {
      base32: bytesToBase32(result.bytes),
      normalizedHex: result.normalized
    };
  }

  function setActiveButton(button, active) {
    if (!button) {
      return;
    }
    button.classList.toggle('mode-pill--active', active);
    button.setAttribute('aria-pressed', active ? 'true' : 'false');
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  }

  function SecretTextareaController(options) {
    options = options || {};
    this.textarea = options.textarea || null;
    if (!this.textarea) {
      return;
    }
    this.container = options.container || this.textarea.closest('[data-secret-field]') || null;
    this.modeToggle = options.modeToggle || (this.container
      ? this.container.querySelector('[data-secret-mode-toggle]')
      : null);
    this.modeButtons = {
      hex: options.modeButtons && options.modeButtons.hex
        ? options.modeButtons.hex
        : (this.modeToggle
          ? this.modeToggle.querySelector('[data-secret-mode-button="hex"]')
          : null),
      base32: options.modeButtons && options.modeButtons.base32
        ? options.modeButtons.base32
        : (this.modeToggle
          ? this.modeToggle.querySelector('[data-secret-mode-button="base32"]')
          : null)
    };
    this.modeIndicator = options.modeIndicator || (this.modeToggle
      ? this.modeToggle.querySelector('[data-secret-mode-indicator]')
      : null);
    this.lengthNode = options.lengthNode || (this.container
      ? this.container.querySelector('[data-secret-length]')
      : null);
    this.messageNode = options.messageNode || (this.container
      ? this.container.querySelector('[data-secret-message]')
      : null);
    this.defaultMessage = typeof options.defaultMessage === 'string'
      ? options.defaultMessage
      : '↔ Converts automatically when you switch modes.';
    this.errorPrefix = typeof options.errorPrefix === 'string'
      ? options.errorPrefix
      : '⚠ ';
    this.requiredMessage = options.requiredMessage || 'Shared secret is required.';
    this.uppercaseHex = options.uppercaseHex !== false;
    this.hexName = options.hexName || 'sharedSecretHex';
    this.base32Name = options.base32Name || 'sharedSecretBase32';
    this.onModeChange = typeof options.onModeChange === 'function' ? options.onModeChange : null;
    this.activeMode = options.initialMode === 'base32' ? 'base32' : 'hex';
    this.lastValidHex = '';
    this.lastValidBase32 = '';
    this.init();
  }

  SecretTextareaController.prototype.init = function () {
    this.textarea.setAttribute('data-secret-mode', this.activeMode);
    if (this.container) {
      this.container.setAttribute('data-secret-mode', this.activeMode);
    }
    this.syncNameAttribute();
    this.attachListeners();
    this.resetMessage();
    var initialValue = this.textarea.value ? this.textarea.value.trim() : '';
    if (hasText(initialValue)) {
      if (this.activeMode === 'base32') {
        this.applyBase32Value(initialValue);
      } else {
        this.applyHexValue(initialValue);
      }
    } else {
      this.textarea.value = '';
      this.textarea.setCustomValidity('');
    }
    this.updateButtons();
    this.updateLengthDisplay();
  };

  SecretTextareaController.prototype.updateMessage = function (content, state) {
    if (!this.messageNode) {
      return;
    }
    var text = typeof content === 'string' ? content : '';
    this.messageNode.textContent = text;
    if (!this.messageNode.hasAttribute('aria-live')) {
      this.messageNode.setAttribute('aria-live', 'polite');
    }
    if (state === 'error') {
      this.messageNode.classList.add('hint--error');
      this.messageNode.setAttribute('data-secret-message-state', 'error');
      this.messageNode.setAttribute('role', 'alert');
    } else {
      this.messageNode.classList.remove('hint--error');
      this.messageNode.removeAttribute('data-secret-message-state');
      this.messageNode.setAttribute('role', 'status');
    }
  };

  SecretTextareaController.prototype.resetMessage = function () {
    this.updateMessage(this.defaultMessage, 'default');
  };

  SecretTextareaController.prototype.showErrorMessage = function (message) {
    var text = typeof message === 'string' && message.trim().length > 0
      ? message.trim()
      : 'Invalid shared secret.';
    var prefix = this.errorPrefix || '';
    if (prefix && text.indexOf(prefix.trim()) !== 0) {
      text = prefix + text;
    }
    this.updateMessage(text, 'error');
  };

  SecretTextareaController.prototype.attachListeners = function () {
    var self = this;
    this.textarea.addEventListener('input', function () {
      self.handleInput();
    });
    this.textarea.addEventListener('change', function () {
      self.handleChange();
    });
    Object.keys(this.modeButtons).forEach(function (modeKey) {
      var button = self.modeButtons[modeKey];
      if (!button) {
        return;
      }
      button.addEventListener('click', function (event) {
        event.preventDefault();
        event.stopPropagation();
        self.switchMode(modeKey);
      });
    });
  };

  SecretTextareaController.prototype.handleInput = function () {
    if (this.activeMode === 'hex') {
      this.normalizeHexInput();
    } else {
      this.normalizeBase32Input();
    }
    this.updateLengthDisplay();
  };

  SecretTextareaController.prototype.handleChange = function () {
    if (this.activeMode === 'hex') {
      this.normalizeHexInput();
    } else {
      this.normalizeBase32Input();
    }
  };

  SecretTextareaController.prototype.normalizeHexInput = function () {
    var value = this.textarea.value || '';
    if (!hasText(value)) {
      this.textarea.value = '';
      this.textarea.setCustomValidity('');
      this.lastValidHex = '';
      this.lastValidBase32 = '';
      this.resetMessage();
      return;
    }
    var upper = this.uppercaseHex ? value.toUpperCase() : value;
    if (upper !== value) {
      var selectionStart = this.textarea.selectionStart;
      var selectionEnd = this.textarea.selectionEnd;
      this.textarea.value = upper;
      if (typeof selectionStart === 'number' && typeof selectionEnd === 'number') {
        this.textarea.setSelectionRange(selectionStart, selectionEnd);
      }
    }
    var validationError = validateHexValue(upper);
    if (validationError) {
      this.textarea.setCustomValidity(validationError);
      this.showErrorMessage(validationError);
      return;
    }
    this.textarea.setCustomValidity('');
    this.resetMessage();
    this.lastValidHex = upper.trim();
    try {
      var converted = hexToBase32(upper);
      this.lastValidBase32 = converted.base32;
    } catch (ignore) {
      this.lastValidBase32 = '';
    }
  };

  SecretTextareaController.prototype.normalizeBase32Input = function () {
    var value = this.textarea.value || '';
    if (!hasText(value)) {
      this.textarea.value = '';
      this.textarea.setCustomValidity('');
      this.lastValidHex = '';
      this.lastValidBase32 = '';
      this.resetMessage();
      return;
    }
    var normalized;
    try {
      normalized = normalizeBase32(value);
    } catch (error) {
      var message = error && error.message ? error.message : 'Invalid Base32 value.';
      this.textarea.setCustomValidity(message);
      this.showErrorMessage(message);
      return;
    }
    if (normalized !== value) {
      var selectionStart = this.textarea.selectionStart;
      var selectionEnd = this.textarea.selectionEnd;
      this.textarea.value = normalized;
      if (typeof selectionStart === 'number' && typeof selectionEnd === 'number') {
        var delta = normalized.length - value.length;
        var newStart = selectionStart + delta;
        var newEnd = selectionEnd + delta;
        this.textarea.setSelectionRange(newStart, newEnd);
      }
    }
    try {
      var converted = base32ToHex(normalized);
      this.lastValidHex = converted.hex;
      this.lastValidBase32 = converted.normalized;
      this.textarea.setCustomValidity('');
      this.resetMessage();
    } catch (error) {
      var decodeMessage = error && error.message ? error.message : 'Invalid Base32 value.';
      this.textarea.setCustomValidity(decodeMessage);
      this.showErrorMessage(decodeMessage);
    }
  };

  SecretTextareaController.prototype.applyHexValue = function (value) {
    var sanitized = value ? value.trim() : '';
    if (!hasText(sanitized)) {
      this.textarea.value = '';
      this.textarea.setCustomValidity('');
      this.lastValidHex = '';
      this.lastValidBase32 = '';
      this.resetMessage();
      return;
    }
    var validationError = validateHexValue(sanitized.toUpperCase());
    if (validationError) {
      this.textarea.value = sanitized.toUpperCase();
      this.textarea.setCustomValidity(validationError);
      this.showErrorMessage(validationError);
      return;
    }
    var upper = sanitized.toUpperCase();
    this.textarea.value = upper;
    this.textarea.setCustomValidity('');
    this.resetMessage();
    this.lastValidHex = upper;
    try {
      var converted = hexToBase32(upper);
      this.lastValidBase32 = converted.base32;
    } catch (ignore) {
      this.lastValidBase32 = '';
    }
  };

  SecretTextareaController.prototype.applyBase32Value = function (value) {
    var sanitized = value ? value.trim() : '';
    if (!hasText(sanitized)) {
      this.textarea.value = '';
      this.textarea.setCustomValidity('');
      this.lastValidHex = '';
      this.lastValidBase32 = '';
      this.resetMessage();
      return;
    }
    try {
      var converted = base32ToHex(sanitized);
      this.textarea.value = converted.normalized;
      this.textarea.setCustomValidity('');
      this.resetMessage();
      this.lastValidBase32 = converted.normalized;
      this.lastValidHex = converted.hex;
    } catch (error) {
      this.textarea.value = sanitized.toUpperCase();
      var message = error && error.message ? error.message : 'Invalid Base32 value.';
      this.textarea.setCustomValidity(message);
      this.showErrorMessage(message);
      this.lastValidBase32 = '';
      this.lastValidHex = '';
    }
  };

  SecretTextareaController.prototype.updateButtons = function () {
    setActiveButton(this.modeButtons.hex, this.activeMode === 'hex');
    setActiveButton(this.modeButtons.base32, this.activeMode === 'base32');
    if (this.modeIndicator) {
      this.modeIndicator.textContent = this.activeMode === 'base32' ? 'Base32' : 'Hex';
    }
    if (this.modeToggle) {
      this.modeToggle.setAttribute('data-secret-mode', this.activeMode);
    }
    if (this.container) {
      this.container.setAttribute('data-secret-mode', this.activeMode);
    }
    this.textarea.setAttribute('data-secret-mode', this.activeMode);
  };

  SecretTextareaController.prototype.updateLengthDisplay = function () {
    if (!this.lengthNode) {
      return;
    }
    var value = this.textarea.value ? this.textarea.value.trim() : '';
    if (!value) {
      this.lengthNode.textContent = 'Length: \u2014';
      return;
    }
    var count = value.length;
    this.lengthNode.textContent = 'Length: ' + count + ' ' + (count === 1 ? 'char' : 'chars');
  };

  SecretTextareaController.prototype.syncNameAttribute = function () {
    this.textarea.setAttribute('name', this.activeMode === 'base32' ? this.base32Name : this.hexName);
  };

  SecretTextareaController.prototype.switchMode = function (mode) {
    var targetMode = mode === 'base32' ? 'base32' : 'hex';
    if (targetMode === this.activeMode) {
      return true;
    }
    var value = this.textarea.value ? this.textarea.value.trim() : '';
    if (hasText(value)) {
      try {
        if (targetMode === 'base32') {
          var hexError = validateHexValue(value.toUpperCase());
          if (hexError) {
            throw new Error(hexError);
          }
          var encoded = hexToBase32(value);
          this.textarea.value = encoded.base32;
          this.lastValidHex = encoded.normalizedHex;
          this.lastValidBase32 = encoded.base32;
        } else {
          var decoded = base32ToHex(value);
          this.textarea.value = decoded.hex;
          this.lastValidHex = decoded.hex;
          this.lastValidBase32 = decoded.normalized;
        }
        this.textarea.setCustomValidity('');
        this.resetMessage();
      } catch (error) {
        var conversionMessage = error && error.message
          ? error.message
          : 'Unable to convert value between encodings.';
        this.textarea.setCustomValidity(conversionMessage);
        this.showErrorMessage(conversionMessage);
        this.textarea.reportValidity();
        return false;
      }
    } else {
      this.textarea.value = '';
      this.textarea.setCustomValidity('');
      this.lastValidHex = '';
      this.lastValidBase32 = '';
      this.resetMessage();
    }
    this.activeMode = targetMode;
    this.syncNameAttribute();
    this.updateButtons();
    this.updateLengthDisplay();
    this.textarea.setCustomValidity('');
    this.resetMessage();
    if (typeof this.onModeChange === 'function') {
      try {
        this.onModeChange(this.activeMode);
      } catch (ignore) {
        // no-op
      }
    }
    return true;
  };

  SecretTextareaController.prototype.clear = function () {
    this.textarea.value = '';
    this.textarea.setCustomValidity('');
    this.lastValidHex = '';
    this.lastValidBase32 = '';
    if (this.activeMode !== 'hex') {
      this.activeMode = 'hex';
      this.syncNameAttribute();
      this.updateButtons();
    }
    this.updateLengthDisplay();
    this.resetMessage();
  };

  SecretTextareaController.prototype.applyPreset = function (hexValue) {
    this.activeMode = 'hex';
    this.syncNameAttribute();
    this.updateButtons();
    this.applyHexValue(typeof hexValue === 'string' ? hexValue : '');
    this.updateLengthDisplay();
    if (typeof this.onModeChange === 'function') {
      try {
        this.onModeChange(this.activeMode);
      } catch (ignore) {
        // no-op
      }
    }
  };

  SecretTextareaController.prototype.payload = function () {
    var value = this.textarea.value ? this.textarea.value.trim() : '';
    if (!value) {
      return {};
    }
    if (this.activeMode === 'base32') {
      return (function (name, content) {
        var data = {};
        data[name] = content;
        return data;
      })(this.base32Name, value);
    }
    return (function (name, content, uppercase) {
      var data = {};
      data[name] = uppercase ? content.toUpperCase() : content;
      return data;
    })(this.hexName, value, this.uppercaseHex);
  };

  SecretTextareaController.prototype.validate = function () {
    var value = this.textarea.value ? this.textarea.value.trim() : '';
    if (!value) {
      this.textarea.setCustomValidity(this.requiredMessage);
      this.showErrorMessage(this.requiredMessage);
      this.textarea.reportValidity();
      this.textarea.setCustomValidity('');
      return false;
    }
    if (this.activeMode === 'base32') {
      try {
        base32ToHex(value);
        this.textarea.setCustomValidity('');
        this.resetMessage();
        return true;
      } catch (error) {
        var message = error && error.message ? error.message : 'Invalid Base32 value.';
        this.textarea.setCustomValidity(message);
        this.showErrorMessage(message);
        this.textarea.reportValidity();
        return false;
      }
    }
    var validationError = validateHexValue(value.toUpperCase());
    if (validationError) {
      this.textarea.setCustomValidity(validationError);
      this.textarea.reportValidity();
      this.showErrorMessage(validationError);
      return false;
    }
    this.textarea.setCustomValidity('');
    this.resetMessage();
    return true;
  };

  SecretTextareaController.prototype.mode = function () {
    return this.activeMode;
  };

  function createController(options) {
    if (!options || !options.textarea) {
      return null;
    }
    return new SecretTextareaController(options);
  }

  global.SecretFieldBridge = {
    create: createController,
    normalizeBase32: normalizeBase32,
    base32ToHex: base32ToHex,
    hexToBase32: hexToBase32
  };
}(window));
