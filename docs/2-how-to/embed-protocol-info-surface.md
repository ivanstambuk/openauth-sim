# Embed the Protocol Info Surface

_Last updated: 2025-10-04_

This guide explains how to add the Protocol Info drawer/modal to a vanilla web application using the embeddable `protocol-info.css` and `protocol-info.js` assets. The surface exposes the same behaviour as the operator console: persistent auto-open, reduced-motion handling, CustomEvents, and the `ProtocolInfo` JavaScript API.

## 1. Include the assets

Copy or serve the assets from `rest-api/src/main/resources/static/ui/`:

```html
<link rel="stylesheet" href="/ui/protocol-info.css" />
...
<script src="/ui/protocol-info.js"></script>
```

> The CSS file ships with the required custom properties. You can override the palette by redefining the `--ocra-*` tokens before linking the stylesheet.

## 2. Provide the markup shell

Add a single trigger button alongside your navigation controls, plus the backdrop and surface elements that host the content. Update the trigger’s `data-protocol` attribute whenever your UI changes the active protocol so the component knows which descriptor to render.

```html
<div class="protocol-tabs-row">
  <div class="protocol-tabs" role="tablist" aria-label="Protocol selector">
    <button type="button" class="protocol-tab" data-protocol-tab="ocra">OCRA</button>
    <button type="button" class="protocol-tab" data-protocol-tab="hotp">HOTP</button>
    <!-- additional protocol tabs -->
  </div>
  <button
    type="button"
    class="protocol-info-trigger"
    data-protocol="ocra"
    aria-label="Protocol info"
    aria-haspopup="dialog"
    aria-controls="protocol-info-surface"
    aria-expanded="false"
  >
    <span aria-hidden="true">i</span>
  </button>
</div>

<div class="protocol-info-backdrop" data-testid="protocol-info-backdrop" hidden></div>
<aside
  id="protocol-info-surface"
  class="protocol-info-surface"
  data-testid="protocol-info-surface"
  data-open="false"
  data-surface-mode="drawer"
  data-active-protocol="ocra"
  role="complementary"
  aria-labelledby="protocol-info-title"
  hidden
>
  <header class="protocol-info-header">
    <div class="protocol-info-header__titles">
      <p class="protocol-info-subtitle" data-testid="protocol-info-subtitle">Protocol guidance</p>
      <h2 class="protocol-info-title" data-testid="protocol-info-title">OCRA</h2>
    </div>
    <div class="protocol-info-header__actions">
      <button type="button" class="protocol-info-action" data-testid="protocol-info-expand">Expand</button>
      <button type="button" class="protocol-info-action protocol-info-action--primary" data-testid="protocol-info-close">
        Close
      </button>
    </div>
  </header>
  <div class="protocol-info-body">
    <div class="protocol-info-summary" data-testid="protocol-info-summary"></div>
    <div class="protocol-info-accordion" data-testid="protocol-info-accordion"></div>
  </div>
</aside>
```

The component derives trigger state from the `data-protocol` attribute and keeps `aria-expanded` synchronised while it is open. Position the trigger near the navigation it augments so keyboard users discover it naturally.

## 3. Supply content data

Add a JSON payload (either inline or injected server-side) with the schema used across the operator console. The minimum shape for each protocol is:

```json
{
  "protocols": {
    "ocra": {
      "title": "Human-friendly title",
      "subtitle": "Short summary",
      "sections": [
        {
          "key": "overview",
          "heading": "Overview",
          "defaultOpen": true,
          "paragraphs": ["Paragraph copy"]
        },
        {
          "key": "how-it-works",
          "heading": "How it works",
          "steps": ["Step 1", "Step 2"]
        },
        {
          "key": "parameters",
          "heading": "Parameters & formats",
          "bullets": ["Key inputs"]
        },
        {
          "key": "security",
          "heading": "Security notes & pitfalls",
          "bullets": ["Risk"]
        },
        {
          "key": "references",
          "heading": "Specifications & test vectors",
          "links": [{ "label": "RFC 6287", "url": "https://www.rfc-editor.org/rfc/rfc6287" }]
        }
      ]
    }
  }
}
```

Embed the payload with the expected ID so the script can discover it:

```html
<script type="application/json" id="protocol-info-data">{ ... }</script>
```

## 4. Initialise the JavaScript API

`protocol-info.js` exposes a framework-agnostic controller on `window.ProtocolInfo`.

```html
<script>
  (function () {
    var root = document.querySelector('.your-container');
    ProtocolInfo.mount({
      root: root,
      onProtocolActivated: function (protocol) {
        console.log('Surface opened for', protocol);
      },
    });
  })();
</script>
```

### Available methods

- `ProtocolInfo.mount(options)` – parses the JSON payload, attaches event listeners, and re-applies persisted preferences. Options:
  - `data` – additional protocol descriptors to merge at runtime.
  - `root` – host element that should be hidden with `aria-hidden` when the modal is open.
  - `onProtocolActivated(protocol)` – callback fired after the surface switches protocol via trigger clicks or keyboard shortcuts.
  - `refreshPreferences` – when `true`, re-evaluates reduced-motion and localStorage availability (used by tests and hot reload environments).
- `ProtocolInfo.setProtocol(protocol, config)` – render a specific protocol programmatically.
  - `config.autoOpen` – open the drawer/modal if it is closed (respects viewport + persistence rules).
  - `config.resetSection` – reset the accordion to the default section.
  - `config.mode` – force `'drawer'` or `'modal'` mode.
  - `config.notifyHost` – when `true`, invokes the registered `onProtocolActivated` callback.
- `ProtocolInfo.open({ protocol?, mode?, focusModal?, resetSection? })` – open the surface, optionally switching the protocol first.
- `ProtocolInfo.close({ focusTarget? })` – close the surface and restore focus. Pass a focus target to override the default trigger.
- `ProtocolInfo.getDescriptor(protocol)` – returns a defensive copy of the underlying descriptor so hosts can build custom UI alongside the drawer.
- `ProtocolInfo.configure({ root, onProtocolActivated })` – update host integration without remounting.

### CustomEvents and persistence

- `protocolinfo:open` / `protocolinfo:close` – dispatched on `document` with `{ protocol, mode }` detail payloads. Listen if you need analytics or logging hooks.
- `protocolinfo:spec-click` – dispatched when users open outbound links from the References section.
- LocalStorage keys follow the `protoInfo.v1` namespace:
  - `protoInfo.v1.seen.<protocol>` – tracks whether the drawer has auto-opened once per protocol.
  - `protoInfo.v1.surface.<protocol>` – remembers the last surface mode (`drawer` or `modal`).
  - `protoInfo.v1.panel.<protocol>` – stores the last accordion section key.

Guard server-side renders by wrapping calls in `if (typeof window !== 'undefined') { ... }` and supply a `ProtocolInfo.mount({ data })` invocation only in browser contexts.

## 5. Reduced-motion & keyboard support

The controller watches `matchMedia('(prefers-reduced-motion: reduce)')` and skips transitions when enabled. Invoke `ProtocolInfo.mount({ refreshPreferences: true })` after stubbing `matchMedia` in tests to recalculate motion classes. Keyboard shortcuts:

- `Shift` + `?` opens the drawer for the active protocol.
- `Esc` closes the surface.
- `Tab` cycles focus within the modal when expanded.

## 6. Demo and quick-start files

Open `rest-api/src/main/resources/static/ui/protocol-info-demo.html` in a browser to exercise the component without running the Spring Boot application. The demo wires the embeddable assets, JSON payload, and host callback discussed above.

## 7. Integration checklist

- [ ] Include the CSS and JS assets in the correct order (`protocol-info.js` must load before host scripts).
- [ ] Provide a `<script type="application/json" id="protocol-info-data">` element with the expected schema.
- [ ] Ensure the trigger maintains an up-to-date `data-protocol` value and an accessible label (e.g., `aria-label="Protocol info"`).
- [ ] Pass your container element as `root` so the modal toggles `aria-hidden` while active.
- [ ] Listen for CustomEvents or use `onProtocolActivated` if you need to synchronise additional UI state.
- [ ] Document in your host README how the persistence keys interact with localStorage to support privacy reviews.
