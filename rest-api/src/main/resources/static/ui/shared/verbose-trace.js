(function (global) {
  'use strict';

  var documentRef = global.document;
  var checkbox = documentRef.querySelector('[data-testid="verbose-trace-checkbox"]');
  var panel = documentRef.querySelector('[data-testid="verbose-trace-panel"]');
  var operationNode = panel ? panel.querySelector('[data-testid="verbose-trace-operation"]') : null;
  var summaryNode = panel ? panel.querySelector('[data-testid="verbose-trace-summary"]') : null;
  var detailsNode = panel ? panel.querySelector('[data-testid="verbose-trace-details"]') : null;
  var contentNode = panel ? panel.querySelector('[data-testid="verbose-trace-content"]') : null;
  var copyButton = panel ? panel.querySelector('[data-testid="verbose-trace-copy"]') : null;
  var copyLabel = copyButton ? copyButton.textContent.trim() : 'Copy trace';
  var copyResetTimer = null;
  var lastTraceText = '';

  function setPanelVisible(visible) {
    if (!panel) {
      return;
    }
    if (visible) {
      panel.removeAttribute('hidden');
      panel.setAttribute('data-trace-visible', 'true');
    } else {
      panel.setAttribute('data-trace-visible', 'false');
      panel.setAttribute('data-trace-variant', 'info');
      panel.setAttribute('hidden', 'hidden');
    }
  }

  function setVariant(variant) {
    if (!panel) {
      return;
    }
    var normalized = variant === 'error' || variant === 'success' ? variant : 'info';
    panel.setAttribute('data-trace-variant', normalized);
  }

  function resetCopyButton() {
    if (!copyButton) {
      return;
    }
    copyButton.textContent = copyLabel;
    copyButton.removeAttribute('data-copy-state');
  }

  function clearContent() {
    if (operationNode) {
      operationNode.textContent = '—';
    }
    if (summaryNode) {
      summaryNode.textContent = 'Trace steps';
    }
    if (contentNode) {
      contentNode.textContent = '';
    }
    if (detailsNode && typeof detailsNode.open !== 'undefined') {
      detailsNode.removeAttribute('open');
    }
    resetCopyButton();
    lastTraceText = '';
  }

  function stringifyValue(value) {
    if (value === null || value === undefined) {
      return String(value);
    }
    if (typeof value === 'string') {
      return value;
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
      return String(value);
    }
    if (Array.isArray(value)) {
      return '['
        + value
            .map(function (item) {
              return stringifyValue(item);
            })
            .join(', ')
        + ']';
    }
    if (value instanceof Date) {
      return value.toISOString();
    }
    if (typeof value === 'object') {
      var keys = Object.keys(value);
      var parts = [];
      keys.forEach(function (key) {
        parts.push(key + '=' + stringifyValue(value[key]));
      });
      return '{' + parts.join(', ') + '}';
    }
    return String(value);
  }

  function formatStep(step, index) {
    if (!step) {
      return '';
    }
    var lines = [];
    var stepId = step.id ? String(step.id) : 'step_' + (index + 1);
    var summary = step.summary ? String(step.summary) : '';
    var detail = step.detail ? String(step.detail) : '';
    var spec = step.spec ? String(step.spec) : '';
    var header = 'step.' + (index + 1) + ': ' + stepId;
    lines.push(header);
    if (summary) {
      lines.push('  summary = ' + summary);
    }
    if (detail) {
      lines.push('  detail = ' + detail);
    }
    if (spec) {
      lines.push('  spec = ' + spec);
    }
    var orderedAttributes = Array.isArray(step.orderedAttributes) ? step.orderedAttributes : null;
    if (orderedAttributes && orderedAttributes.length > 0) {
      orderedAttributes.forEach(function (entry) {
        if (!entry || typeof entry !== 'object') {
          return;
        }
        var name = entry.name ? String(entry.name) : '';
        if (!name) {
          return;
        }
        lines.push('  ' + name + ' = ' + stringifyValue(entry.value));
      });
    } else {
      var attributes = step.attributes && typeof step.attributes === 'object' ? step.attributes : null;
      if (attributes) {
        var attributeKeys = Object.keys(attributes);
        attributeKeys.forEach(function (key) {
          var bucket = attributes[key];
          if (bucket && typeof bucket === 'object' && !Array.isArray(bucket)) {
            var bucketKeys = Object.keys(bucket);
            bucketKeys.forEach(function (attributeName) {
              lines.push('  ' + attributeName + ' = ' + stringifyValue(bucket[attributeName]));
            });
          } else {
            lines.push('  ' + key + ' = ' + stringifyValue(bucket));
          }
        });
      }
    }
    var notes = step.notes && typeof step.notes === 'object' ? step.notes : null;
    if (notes) {
      var noteKeys = Object.keys(notes);
      noteKeys.forEach(function (key) {
        lines.push('  note.' + key + ' = ' + stringifyValue(notes[key]));
      });
    }
    return lines.join('\n');
  }

  function formatTrace(trace) {
    if (!trace || typeof trace !== 'object') {
      return '';
    }
    var lines = [];
    var operation = trace.operation ? String(trace.operation) : 'unknown';
    lines.push('operation = ' + operation);

    var metadata = trace.metadata && typeof trace.metadata === 'object' ? trace.metadata : null;
    if (metadata) {
      var metadataKeys = Object.keys(metadata);
      if (metadataKeys.length > 0) {
        metadataKeys.forEach(function (key) {
          lines.push('metadata.' + key + ' = ' + stringifyValue(metadata[key]));
        });
      }
    }

    var steps = Array.isArray(trace.steps) ? trace.steps : [];
    if (steps.length > 0) {
      steps.forEach(function (step, index) {
        lines.push(formatStep(step, index));
      });
    }

    var provenanceLines = formatProvenance(trace.provenance);
    if (provenanceLines) {
      if (lines.length > 0) {
        lines.push('');
      }
      provenanceLines.forEach(function (line) {
        lines.push(line);
      });
    }
    return lines.join('\n');
  }

  function formatProvenance(provenance) {
    if (!provenance || typeof provenance !== 'object') {
      return null;
    }
    var sections = [
      { key: 'protocolContext', title: 'Protocol Context' },
      { key: 'keyDerivation', title: 'Key Derivation' },
      { key: 'cdolBreakdown', title: 'CDOL Breakdown' },
      { key: 'iadDecoding', title: 'IAD Decoding' },
      { key: 'macTranscript', title: 'MAC Transcript' },
      { key: 'decimalizationOverlay', title: 'Decimalization Overlay' },
    ];
    var lines = [];
    sections.forEach(function (section) {
      var value = provenance[section.key];
      if (!value && value !== 0) {
        return;
      }
      var sectionLines = formatProvenanceSection(section.title, value);
      if (sectionLines.length === 0) {
        return;
      }
      if (lines.length > 0) {
        lines.push('');
      }
      sectionLines.forEach(function (line) {
        lines.push(line);
      });
    });
    return lines.length > 0 ? lines : null;
  }

  function formatProvenanceSection(title, value) {
    var lines = [];
    lines.push(title);
    var body = formatProvenanceValue(value, '  ');
    if (body.length === 0) {
      return [];
    }
    body.forEach(function (entry) {
      lines.push(entry);
    });
    return lines;
  }

  function formatProvenanceValue(value, indent) {
    var lines = [];
    if (value === null || value === undefined) {
      return lines;
    }
    var prefix = indent || '';
    if (Array.isArray(value)) {
      value.forEach(function (item, index) {
        var label = stringifyValue(item);
        lines.push(prefix + '- [' + index + '] ' + label);
      });
      return lines;
    }
    if (typeof value === 'object') {
      Object.keys(value).forEach(function (key) {
        var entry = value[key];
        if (Array.isArray(entry)) {
          lines.push(prefix + key + ':');
          var nested = formatProvenanceValue(entry, prefix + '  ');
          nested.forEach(function (line) {
            lines.push(line);
          });
        } else if (entry && typeof entry === 'object') {
          lines.push(prefix + key + ':');
          var nestedObject = formatProvenanceValue(entry, prefix + '  ');
          if (nestedObject.length > 0) {
            nestedObject.forEach(function (line) {
              lines.push(line);
            });
          } else {
            lines.push(prefix + '  ' + stringifyValue(entry));
          }
        } else {
          lines.push(prefix + key + ' = ' + stringifyValue(entry));
        }
      });
      return lines;
    }
    lines.push(prefix + stringifyValue(value));
    return lines;
  }

  function renderTrace(trace, options) {
    if (!panel) {
      return;
    }
    if (!trace) {
      clearContent();
      setPanelVisible(false);
      return;
    }
    var variant = options && options.variant ? options.variant : 'info';
    setVariant(variant);
    if (operationNode) {
      operationNode.textContent = trace.operation ? String(trace.operation) : '—';
    }
    if (summaryNode) {
      var stepCount = Array.isArray(trace.steps) ? trace.steps.length : 0;
      summaryNode.textContent = stepCount === 1 ? '1 step' : stepCount + ' steps';
    }
    var formatted = formatTrace(trace);
    lastTraceText = formatted;
    if (contentNode) {
      contentNode.textContent = formatted;
      contentNode.scrollTop = 0;
    }
    if (detailsNode && typeof detailsNode.open !== 'undefined') {
      detailsNode.setAttribute('open', '');
    }
    setPanelVisible(true);
    resetCopyButton();
  }

  function copyToClipboard() {
    if (!lastTraceText) {
      return;
    }

    function finalizeCopied() {
      if (!copyButton) {
        return;
      }
      copyButton.textContent = 'Copied!';
      copyButton.setAttribute('data-copy-state', 'copied');
      if (copyResetTimer) {
        global.clearTimeout(copyResetTimer);
      }
      copyResetTimer = global.setTimeout(function () {
        resetCopyButton();
      }, 1800);
    }

    if (navigator && navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
      navigator.clipboard.writeText(lastTraceText).then(
        finalizeCopied,
        function () {
          fallbackCopy();
        }
      );
      return;
    }
    fallbackCopy();

    function fallbackCopy() {
      var textarea = documentRef.createElement('textarea');
      textarea.value = lastTraceText;
      textarea.setAttribute('readonly', 'readonly');
      textarea.style.position = 'absolute';
      textarea.style.left = '-9999px';
      documentRef.body.appendChild(textarea);
      textarea.select();
      try {
        documentRef.execCommand('copy');
      } catch (error) {
        // ignore failures; best effort only
      }
      documentRef.body.removeChild(textarea);
      finalizeCopied();
    }
  }

  if (copyButton) {
    copyButton.addEventListener('click', copyToClipboard);
  }

  if (checkbox) {
    checkbox.addEventListener('change', function () {
      if (!checkbox.checked) {
        clearContent();
        setPanelVisible(false);
      }
    });
  }

  if (panel) {
    clearContent();
    setPanelVisible(false);
  }

  global.VerboseTraceConsole = {
    isEnabled: function () {
      return Boolean(checkbox && checkbox.checked);
    },

    beginRequest: function () {
      if (!panel) {
        return;
      }
      if (!this.isEnabled()) {
        clearContent();
        setPanelVisible(false);
        return;
      }
      resetCopyButton();
      setPanelVisible(false);
    },

    attachVerboseFlag: function (payload) {
      if (!payload || typeof payload !== 'object') {
        return payload;
      }
      if (this.isEnabled()) {
        payload.verbose = true;
      } else if (Object.prototype.hasOwnProperty.call(payload, 'verbose')) {
        delete payload.verbose;
      }
      return payload;
    },

    renderTrace: function (trace, options) {
      renderTrace(trace, options);
    },

    clearTrace: function () {
      clearContent();
      setPanelVisible(false);
    },

    handleResponse: function (payload, options) {
      var trace = payload && payload.trace ? payload.trace : null;
      if (trace) {
        renderTrace(trace, options);
      } else {
        clearContent();
        setPanelVisible(false);
      }
    },

    handleError: function (payload, options) {
      var trace = payload && payload.trace ? payload.trace : null;
      if (trace) {
        var merged = options || {};
        if (!merged.variant) {
          merged.variant = 'error';
        }
        renderTrace(trace, merged);
      } else {
        clearContent();
        setPanelVisible(false);
      }
    },
  };
})(window);
