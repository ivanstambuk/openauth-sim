# Feature 005 Tasks – EMV/CAP Simulation Services

_Status:_ In review  
_Last updated:_ 2025-11-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

Linked plan: `docs/4-architecture/features/005/plan.md`


## Checklist

- [x] T-005-01 – Inline preset hydration (S39-02): include the selected preset `credentialId` when submitting inline evaluations so masked secrets fall back to persistence; extend JS + Selenium coverage and reran `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, and `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Inline preset hydration (S39-02): include the selected preset
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`

- [x] T-005-02 – Inline sample vector mode persistence (S39-02, S39-09): fixed operator UI evaluate panel so selecting a sample vector while inline mode is active keeps inline controls visible/editable (no automatic stored-mode switch); added JS + Selenium coverage and reran `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, and `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Inline sample vector mode persistence (S39-02, S39-09): fixed operator UI evaluate panel so selecting a sample vector while inline mode is active keeps inline controls visible/editable (no automatic stored-mode switch); added JS + Selenium coverage and reran
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`

- [x] T-005-03 – Stored preset secret hiding (S39-03): `toggleSensitiveFields` now applies `hidden`/`aria-hidden` to sensitive `.field-group` containers and mask wrappers whenever stored mode is active so ICC master key, CDOL1, Issuer Proprietary Bitmap, ICC payload template, and Issuer Application Data rows vanish; inline mode removes the attributes and restores the inputs. Added Node + Selenium assertions proving stored presets hide rows while inline mode keeps them editable. Commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon spotlessApply check --console=plain`.
  _Intent:_ Stored preset secret hiding (S39-03):
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `./gradlew --no-daemon spotlessApply check --console=plain`

- [x] T-005-04 – Stored mode label hiding (S39-03): extended `.emv-stored-mode` selectors to cover the same field groups/mask wrappers and refreshed Selenium fixtures to expect hidden labels/hints during stored submissions with inline mode regression coverage. Commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon spotlessApply check --console=plain`.
  _Intent:_ Stored mode label hiding (S39-03): extended
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `./gradlew --no-daemon spotlessApply check --console=plain`

- [x] T-005-05 – Customer input inline grouping (S39-07): restructured Evaluate and Replay “Input from customer” sections into a single fieldset, wired mode-driven enablement/disablement (Identify = all disabled/cleared, Respond = challenge only, Sign = reference+amount while challenge stays masked), updated helper hints, refreshed JS unit coverage, adjusted Selenium coverage (Sign inline scenario deferred via TODO), and ran `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, and `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Customer input inline grouping (S39-07): restructured Evaluate and Replay “Input from customer” sections into a single fieldset, wired mode-driven enablement/disablement (Identify = all disabled/cleared, Respond = challenge only, Sign = reference+amount while challenge stays masked), updated helper hints, refreshed JS unit coverage, adjusted Selenium coverage (Sign inline scenario deferred via TODO), and ran
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`

- [x] T-005-06 – Inline preset full hydration (S39-02): taught the Node console harness to await credential summaries before dispatching preset change events so inline Evaluate/Replay hydration reliably loads sensitive defaults; reran `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test` (first attempt surfaced the existing Fido2 HTMLUnit flake; rerun via the `spotlessApply check` pipeline succeeded), `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, and `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Inline preset full hydration (S39-02): taught the Node console harness to await credential summaries before dispatching preset change events so inline Evaluate/Replay hydration reliably loads sensitive defaults; reran
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `spotlessApply check`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-07 – Card transaction grouping (S39-07): introduced a dedicated “Transaction” fieldset under the card configuration section for Evaluate and Replay, stacking ICC payload template and Issuer Application Data inputs with the required `"xxxx" is replaced by the ATC` helper copy plus new data-testid hooks. Extended Node + Selenium coverage (console harness now asserts ICC template masking in stored mode; Selenium checks legends/hints for both panels). Verification matrix: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Card transaction grouping (S39-07): introduced a dedicated “Transaction” fieldset under the card configuration section for Evaluate and Replay, stacking ICC payload template and Issuer Application Data inputs with the required
  _Verification commands:_
  - `"xxxx" is replaced by the ATC`
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-08 – Session key derivation grouping (S39-07): wrapped the master key, ATC, branch factor, height, and IV inputs beneath a dedicated “Session key derivation” fieldset (Evaluate + Replay), added helper copy + data-testid hooks, tweaked copy to call out all derivation inputs, layered CSS fieldset styling, and extended Node/Selenium coverage so stored mode keeps the numeric derivation rows visible while only masking secrets. Tests: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Session key derivation grouping (S39-07): wrapped the master key, ATC, branch factor, height, and IV inputs beneath a dedicated “Session key derivation” fieldset (Evaluate + Replay), added helper copy + data-testid hooks, tweaked copy to call out all derivation inputs, layered CSS fieldset styling, and extended Node/Selenium coverage so stored mode keeps the numeric derivation rows visible while only masking secrets. Tests:
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-09 – Branch factor & height row alignment (S39-07): wrapped the Branch factor (b) and Height (H) inputs inside `.emv-session-pair-row` containers on the Evaluate and Replay forms, added the shared CSS row plus `data-testid` hooks, extended Selenium coverage to assert the wrapper, and reran `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` followed by `./gradlew --no-daemon spotlessApply check` (second run succeeded after the initial 300 s timeout).
  _Intent:_ Branch factor & height row alignment (S39-07): wrapped the Branch factor (b) and Height (H) inputs inside
  _Verification commands:_
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-10 – Session key row width alignment (S39-07): wrapped the ICC master key + secret mask and ATC inputs inside a dedicated `.emv-session-master-row` container in `panel.html`, added matching CSS grid helpers so the row spans edge-to-edge, and kept stored-mode masking scoped to the master key while ATC stays visible. Extended Selenium coverage with `assertMasterAtcRow` plus new JS assertions confirming ATC containers remain interactive in stored mode. Verification: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Session key row width alignment (S39-07): wrapped the ICC master key + secret mask and ATC inputs inside a dedicated
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-11 – Card configuration isolation (S39-07): split the Evaluate + Replay templates so `.emv-card-block` now wraps only CDOL1/IPB inputs while the Transaction (`.emv-transaction-block`) and Input from customer (`.emv-customer-block`) fieldsets render as siblings; added a template regression test in `console.test.js` plus new Selenium assertions to enforce the hierarchy. Verification: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Card configuration isolation (S39-07): split the Evaluate + Replay templates so
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-12 – Input-from-customer row layout polish (S39-07): updated the Feature 039 spec + plan with R5.7, rebuilt the Evaluate + Replay templates so each mode owns a dedicated row (radio + left-aligned label) with its customer inputs on the same row (Respond + Challenge, Sign + Reference/Amount, Identify shows a placeholder), layered new `.emv-customer-row`/`fields` styling to keep the radios vertically aligned, and refreshed the console JS + Selenium coverage to assert the single-set Challenge/Reference/Amount DOM structure. Verification: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`.
  _Intent:_ Input-from-customer row layout polish (S39-07): updated the Feature 039 spec + plan with R5.7, rebuilt the Evaluate + Replay templates so each mode owns a dedicated row (radio + left-aligned label) with its customer inputs on the same row (Respond + Challenge, Sign + Reference/Amount, Identify shows a placeholder), layered new
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`

- [x] T-005-13 – Session key single-line inputs (S39-07): converted the ICC master key control from a textarea to a single-line text input so the master key and ATC share one row without vertical scrollbars, kept the `.secret-mask` overlay scoped to the master key column in stored mode, refreshed console JS fixtures plus Selenium/Node coverage, and reran `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, and `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Session key single-line inputs (S39-07): converted the ICC master key control from a textarea to a single-line text input so the master key and ATC share one row without vertical scrollbars, kept the
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-14 – IV/IPB/IAD single-line inputs (S39-07): swapped the Evaluate panel’s Initialization Vector, Issuer Proprietary Bitmap, and Issuer Application Data controls (plus the Replay panel’s issuer bitmap/application inputs) from `<textarea>` elements to `<input type="text">`, updated console.js DOM lookups and sensitive-field masks, refreshed Node fixtures + Selenium assertions to expect the new markup, and reran `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, and `./gradlew --no-daemon spotlessApply check` (second run required 600 s timeout after the initial 300 s limit expired).
  _Intent:_ IV/IPB/IAD single-line inputs (S39-07): swapped the Evaluate panel’s Initialization Vector, Issuer Proprietary Bitmap, and Issuer Application Data controls (plus the Replay panel’s issuer bitmap/application inputs) from
  _Verification commands:_
  - `<input type="text">`
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-15 – Preview window helper-text cleanup (S39-06): removed the helper sentence beneath the EMV/CAP "Preview window offsets" legend, eliminated the `aria-describedby` references, and verified `./gradlew --no-daemon spotlessApply check` (rerun with 600 s timeout) to cover Node + Selenium automation. _(Completed 2025-11-08)_
  _Intent:_ Preview window helper-text cleanup (S39-06): removed the helper sentence beneath the EMV/CAP "Preview window offsets" legend, eliminated the
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-16 – Evaluate hint removal (S39-02): deleted the informational paragraph (“Inline evaluation uses the parameters entered above…”) from the EMV Evaluate panel, removed the unused `data-testid="emv-stored-empty"` hook + JS references, updated console + Selenium tests to assert the hint stays absent, and reran `./gradlew --no-daemon spotlessApply check` (600 s timeout) for full coverage. _(Completed 2025-11-08)_
  _Intent:_ Evaluate hint removal (S39-02): deleted the informational paragraph (“Inline evaluation uses the parameters entered above…”) from the EMV Evaluate panel, removed the unused
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-17 – Replay CTA spacing parity (S39-07): documented R5.8 in the spec/plan, added Node template + Selenium assertions enforcing the shared `stack-offset-top-lg` class on both action bars, updated `panel.html` so Evaluate + Replay reuse the helper, ran `node --test rest-api/src/test/javascript/emv/console.test.js` and `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`; full `./gradlew --no-daemon spotlessApply check` left for the operator per current session note.
  _Intent:_ Replay CTA spacing parity (S39-07): documented R5.8 in the spec/plan, added Node template + Selenium assertions enforcing the shared
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-18 – Remove EMV helper copy (S39-07): deleted the Identify-mode hint and both session-derivation helper paragraphs from the Evaluate and Replay panels, stripped the related copy wiring from `console.js`, updated Node + Selenium coverage to drop the string assertions, and reran `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, and `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Remove EMV helper copy (S39-07): deleted the Identify-mode hint and both session-derivation helper paragraphs from the Evaluate and Replay panels, stripped the related copy wiring from
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-19 – Verbose trace schema documentation (2025-11-09) (S39-08): documented the authoritative `trace.provenance` JSON schema in the EMV/CAP spec/plan (now owned by Feature 005) and recorded the requirement in this tasks file so future increments have a fixed contract before staging tests. Documentation-only change; no Gradle commands executed.
  _Intent:_ Verbose trace schema documentation (2025-11-09) (S39-08): capture the `trace.provenance` schema in the governing EMV/CAP spec/plan so all modules share the same contract.
  _Verification commands:_
  - Documentation updates only (no commands executed)

- [x] T-005-20 – Implementation Drift Gate (S39-01, S39-05, S39-08): cross-checked the EMV/CAP provenance requirements (historically tracked under a separate provenance feature) against the implemented tests (core/application `EmvCap*ServiceTest`, REST MockMvc suites, Picocli tests, Node + Selenium harnesses, and the provenance schema helpers), documented the drift report in the Feature 005 plan, aligned the roadmap/how-to guides with the dual `trace-provenance-example.json` requirement, and reran `./gradlew --no-daemon spotlessApply check` after the documentation sweep.
  _Intent:_ Implementation Drift Gate (S39-01, S39-05, S39-08): confirm the EMV/CAP provenance spec/plan (now consolidated under Feature 005) matches the shipped implementation and tests.
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-21 – Verbose trace provenance tests (S39-08): introduced the canonical fixture (`docs/test-vectors/emv-cap/trace-provenance-example.json`) plus the shared `EmvCapTraceProvenanceSchema` helper, then extended every facade test suite to demand the richer schema. Application and replay tests now fail until provenance data exists, REST MockMvc + CLI JSON tests validate the schema and lock the identify-baseline trace to the fixture, and the JS/Selenium suites assert that `VerboseTraceConsole` renders the six provenance sections fed by the fixture-backed fetch stub. Tests remain red pending T-005-22 implementation, so no Gradle commands were executed beyond local linting.
  _Intent:_ Verbose trace provenance tests (S39-08): introduced the canonical fixture (
  _Verification commands:_
  - `docs/test-vectors/emv-cap/trace-provenance-example.json`

- [x] T-005-23 – Verbose trace provenance implementation (S39-08)
  _Intent:_ Verbose trace provenance implementation (S39-08)
  _Verification commands:_
  - `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*Trace*"`
  - `:core:test`
  - `EmvCapEvaluation/ReplayApplicationServiceTest`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*Trace*"`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*Emv*Trace*"`
  - `Protocol Context`
  - `Key Derivation`
  - `CDOL Breakdown`
  - `IAD Decoding`
  - `MAC Transcript`
  - `Decimalization Overlay`
  - `rest-api/src/main/resources/static/ui/emv/console.js`
  - `rest-api/src/main/resources/static/ui/shared/verbose-trace.js`
  - `rest-api/src/main/resources/static/ui/eudi-openid4vp/console.js`
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`
  - `docs/test-vectors/emv-cap/trace-provenance-example.json`
  - `rest-api/docs/test-vectors/emv-cap/`
  - `rest-api/`
  - `./gradlew --no-daemon --console=plain :application:test`
  - `:cli:test`
  - `:rest-api:test`
  - `:ui:test`
  - `pmdMain pmdTest`
  - `spotlessApply check`
  _Notes:_
  > - [x] T-005-24a – Planning sync (S39-08): reread spec R2.4 + fixture helper, documented the provenance field-to-source mapping inside `docs/4-architecture/features/005/plan.md` ("Provenance Field Mapping (T-005-24a)") and outlined the per-surface verification plan before resuming code work.
  >   _Verification commands:_
  >   - `docs/4-architecture/features/005/plan.md`
  > - [x] T-005-25b – Core/application builder (S39-08): extend verbose trace assembly to populate `trace.provenance` for evaluation + replay responses; start by updating `EmvCapEvaluationApplicationServiceTest`/`EmvCapReplayApplicationServiceTest` so they fail until provenance data flows, then run `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*Trace*"` (add `:core:test` if helper logic changes).
  > - Implemented `TraceAssembler` with protocol context, key-derivation, CDOL breakdown, IAD decoding, MAC transcript, and decimalization overlay builders that load issuer profile overrides when necessary (baseline fixture now matches `trace-provenance-example.json`).
  > - Extended `Trace` record with provenance data + ICC template/resolution, added issuer profile catalogue, and refreshed schema assertions to map the nested structure for `EmvCapEvaluation/ReplayApplicationServiceTest`.
  > - Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*Trace*"`.
  > - [x] T-005-26c – REST/CLI propagation (S39-08): extend `EmvCapEvaluationResponse`/`EmvCapReplayResponse` (plus their `Json` serializers) so `trace.provenance` travels through REST payloads unchanged, wire controller assemblers and `TraceAssemblerResponseFactory` helpers accordingly, and mirror the same structure in Picocli JSON output (`EmvCliEvaluateCommand`, `EmvCliEvaluateStoredCommand`, `EmvCliReplayCommand`). Update MockMvc + CLI JSON tests/snapshots first so they fail, then rerun `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*Trace*"` and `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*Emv*Trace*"` until the schema validator + identify-baseline fixture comparisons pass.
  > - 2025-11-09: Replay REST + CLI mismatch tests now assert that `trace.expectedOtp` is present, numeric, and equals `provenance.decimalizationOverlay.otp`, ensuring provenance data survives facade serialization. Verified via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*Trace*"` and `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*Emv*Trace*"`.
  > - [x] T-005-27d – Operator UI wiring (S39-08): feed the richer provenance payload through the JS fetch stub, make sure `console.js` passes all six sections to `VerboseTraceConsole.handleResponse`, and extend Selenium coverage so both Evaluate + Replay drawers render `Protocol Context`, `Key Derivation`, `CDOL Breakdown`, `IAD Decoding`, `MAC Transcript`, and `Decimalization Overlay`.
  > - 2025-11-09 completion: mirrored the provenance fetch into `rest-api/src/main/resources/static/ui/emv/console.js`, `rest-api/src/main/resources/static/ui/shared/verbose-trace.js`, and `rest-api/src/main/resources/static/ui/eudi-openid4vp/console.js` so all console variants forward the protocol context fields; refreshed the Node harness (`node --test rest-api/src/test/javascript/emv/console.test.js`) plus Selenium coverage (`OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`) to assert the six provenance sections render for Evaluate & Replay.
  > - [x] T-005-28e – Snapshot + verification sweep (S39-08): regenerate OpenAPI (`OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`), CLI snapshots/fixtures, and UI assets once provenance lands, then execute `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` (≥600 s timeout). Capture the passing command log plus any TODOs in the roadmap and `_current-session.md`.
  > - 2025-11-09 prep: mirrored `docs/test-vectors/emv-cap/trace-provenance-example.json` into `rest-api/docs/test-vectors/emv-cap/` so Gradle’s JS/Selenium runners (which execute from `rest-api/`) and Spring static assets can resolve the provenance fixture without depending on repo-relative paths. Keep both copies in sync until a dedicated sync task lands.
  > - 2025-11-09 verification: regenerated the OpenAPI snapshot (`OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`), then ran the full gate as discrete invocations (`./gradlew --no-daemon --console=plain :application:test`, `:cli:test`, `:rest-api:test`, `:ui:test`, `pmdMain pmdTest`, and finally `spotlessApply check`) so provenance fixtures, PMD, and Spotless all pass together. Logged the green commands in `_current-session.md`.

- [x] T-005-29 – Fixture scaffolding & red tests (S39-01): added `docs/test-vectors/emv-cap/{identify,respond,sign}-baseline.json`, captured session key/cryptogram/overlay/OTP metadata, and introduced `EmvCapSimulationVectorsTest` with deliberate failures pending domain wiring (Gradle run deferred until implementation). Commands: `./gradlew --no-daemon :core:test`.
  _Intent:_ Fixture scaffolding & red tests (S39-01): added
  _Verification commands:_
  - `docs/test-vectors/emv-cap/{identify,respond,sign}-baseline.json`
  - `./gradlew --no-daemon :core:test`

- [x] T-005-30 – Core implementation (S39-01): implemented session key derivation, CAP mode validation, Generate AC execution, and IPB masking to satisfy T-005-31 tests; introduced negative-path coverage for invalid hex and Identify-mode challenge misuse. Commands: `./gradlew --no-daemon :core:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Core implementation (S39-01): implemented session key derivation, CAP mode validation, Generate AC execution, and IPB masking to satisfy T-005-32 tests; introduced negative-path coverage for invalid hex and Identify-mode challenge misuse.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-33 – Application orchestration & telemetry (S39-01): added `EmvCapEvaluationApplicationService` with request/response records, sanitized telemetry adapters, mask-length analytics, and optional trace payload; introduced fixture-driven application tests covering all modes, validation failure handling, ATC substitution, and trace toggling. Commands: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Application orchestration & telemetry (S39-01): added
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-34 – REST endpoint integration (S39-01, S39-10): exposed `POST /api/v1/emv/cap/evaluate`, introduced controller/service/DTOs with validation, covered Identify/Respond/Sign flows plus includeTrace toggle and missing-field errors via MockMvc, and refreshed OpenAPI snapshots. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapEvaluationEndpointTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ REST endpoint integration (S39-01, S39-10): exposed
  _Verification commands:_
  - `POST /api/v1/emv/cap/evaluate`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapEvaluationEndpointTest"`
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon :rest-api:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-35 – Documentation & telemetry verification (S39-10): refreshed roadmap/knowledge map, added `docs/2-how-to/use-emv-cap-rest-operations.md`, updated `_current-session.md`, verified telemetry redaction via `./gradlew --no-daemon :application:test :rest-api:test`, and ran `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Documentation & telemetry verification (S39-10): refreshed roadmap/knowledge map, added
  _Verification commands:_
  - `docs/2-how-to/use-emv-cap-rest-operations.md`
  - `./gradlew --no-daemon :application:test :rest-api:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-36 – CLI parity (S39-01, S39-10): delivered `emv cap evaluate` Picocli command with mode-aware validation, text + JSON outputs, includeTrace toggle, telemetry emission, and coverage for invalid/override flows (identify/respond/sign). Commands: `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ CLI parity (S39-01, S39-10): delivered
  _Verification commands:_
  - `emv cap evaluate`
  - `./gradlew --no-daemon :cli:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-37 – Persistence & seeding (S39-04): wired `EmvCapCredentialPersistenceAdapter` + seeding application service, introduced shared `EmvCapSeedSamples`, exposed REST `POST /api/v1/emv/cap/credentials/seed`, added Picocli `emv cap seed`, and landed idempotency/telemetry coverage across application, REST, and CLI suites. Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.EmvCapSeedApplicationServiceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.EmvCapCredentialSeedingEndpointTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliTest"`.
  _Intent:_ Persistence & seeding (S39-04): wired
  _Verification commands:_
  - `POST /api/v1/emv/cap/credentials/seed`
  - `emv cap seed`
  - `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.EmvCapSeedApplicationServiceTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.EmvCapCredentialSeedingEndpointTest"`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliTest"`

- [x] T-005-38 – Operator UI integration (S39-01, S39-07): enabled the EMV/CAP console tab with stored credential presets, inline form, include-trace toggle, ResultCard wiring, and verbose trace panel plus new Selenium coverage. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Operator UI integration (S39-01, S39-07): enabled the EMV/CAP console tab with stored credential presets, inline form, include-trace toggle, ResultCard wiring, and verbose trace panel plus new Selenium coverage.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :rest-api:test`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-39a – Operator UI layout alignment (S39-07): refactored the EMV/CAP panel to the shared two-column layout, constrained the result pane to the OTP preview/status badge, and relocated mask length/masked digits/ATC/branch/height plus the ICC template into the verbose trace panel. Updated console JavaScript renders the preview row, writes metrics into the trace grid, and Selenium coverage now checks the relocated telemetry. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Operator UI layout alignment (S39-07): refactored the EMV/CAP panel to the shared two-column layout, constrained the result pane to the OTP preview/status badge, and relocated mask length/masked digits/ATC/branch/height plus the ICC template into the verbose trace panel. Updated console JavaScript renders the preview row, writes metrics into the trace grid, and Selenium coverage now checks the relocated telemetry.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-40b – Verbose trace visibility parity (S39-08): routed EMV/CAP responses through the shared `VerboseTraceConsole`, removed the bespoke panel, normalised trace payloads, and synced Selenium to assert copy-control reuse plus local include-trace gating against the global toggle. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Verbose trace visibility parity (S39-08): routed EMV/CAP responses through the shared
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :rest-api:test`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-41c – Fixture expansion (S39-01, S39-05): gathered **new, user-supplied** EMV/CAP vectors before the documentation sweep, including:
  _Intent:_ Fixture expansion (S39-01, S39-05): gathered **new, user-supplied** EMV/CAP vectors before the documentation sweep, including:
  _Verification commands:_
  - `b ∈ {2,6}`
  - `H ∈ {6,10}`
  - `./gradlew --no-daemon :core:test :application:test :rest-api:test`
  _Notes:_
  > * 2 `IDENTIFY` flows using branch factors `b ∈ {2,6}` and heights `H ∈ {6,10}`.
  > * 2 `RESPOND` flows with numeric challenges of length 4 and 8 respectively (branch factor 4, height 8).
  > * 2 `SIGN` flows with distinct reference+amount pairs (one amount <1000, one ≥5000) at branch factor 4, height 8.
  > * 2 mismatch samples per mode capturing negative OTP comparisons for replay regression (Δ offsets ±1 and ±2).
  > - Completed: wired the new fixtures into core/application/REST regression suites using parameterized coverage and reran `./gradlew --no-daemon :core:test :application:test :rest-api:test`.

- [x] T-005-42 – Documentation & full verification (S39-10): published updated how-to guides (`docs/2-how-to/use-emv-cap-rest-operations.md`) and new CLI/UI companions, refreshed `docs/4-architecture/knowledge-map.md` with the extended fixture set, marked Feature 039 replay scope across roadmap/plan/spec, verified OpenAPI snapshots via `:rest-api:test`, and ran the full quality gate on 2025-11-02 (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`).
  _Intent:_ Documentation & full verification (S39-10): published updated how-to guides (
  _Verification commands:_
  - `docs/2-how-to/use-emv-cap-rest-operations.md`
  - `docs/4-architecture/knowledge-map.md`
  - `:rest-api:test`
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`

- [x] T-005-43 – Replay scaffolding & red tests (S39-05, S39-06): extended the fixture catalogue with stored/inline replay vectors (`docs/test-vectors/emv-cap/replay-fixtures.json`), introduced failing coverage across application (`EmvCapReplayApplicationServiceTest`), REST (`EmvCapReplayEndpointTest`), CLI (`EmvCliReplayTest`), and UI (`EmvCapOperatorUiSeleniumTest`) suites, and documented pending implementation here prior to activation. Commands: `./gradlew --no-daemon :application:test :rest-api:test :cli:test :ui:test` (targeted classes), `./gradlew --no-daemon spotlessApply check` (expected red).
  _Intent:_ Replay scaffolding & red tests (S39-05, S39-06): extended the fixture catalogue with stored/inline replay vectors (
  _Verification commands:_
  - `docs/test-vectors/emv-cap/replay-fixtures.json`
  - `./gradlew --no-daemon :application:test :rest-api:test :cli:test :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-44 – Application replay orchestration & telemetry (S39-05): implemented `EmvCapReplayApplicationService`, request/response records, telemetry adapters, and verbose trace assembly while driving T-005-45 tests to green; ensured mismatch signalling, preview-window overrides, and stored credential lookups align with evaluation semantics. Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.EmvCapReplayApplicationServiceTest"`, `./gradlew --no-daemon spotlessApply check` (remained red on facade work).
  _Intent:_ Application replay orchestration & telemetry (S39-05): implemented
  _Verification commands:_
  - `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.EmvCapReplayApplicationServiceTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-46 – REST replay endpoint & OpenAPI (S39-05, S39-10): introduced `POST /api/v1/emv/cap/replay`, DTOs, validation, telemetry wiring, MockMvc coverage for stored/inline success plus mismatch flows, and refreshed OpenAPI snapshots. Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest"`.
  _Intent:_ REST replay endpoint & OpenAPI (S39-05, S39-10): introduced
  _Verification commands:_
  - `POST /api/v1/emv/cap/replay`
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest"`

- [x] T-005-47 – CLI replay command (S39-05, S39-10): added `emv cap replay` Picocli command covering stored/inline flows, preview-window overrides, includeTrace toggle, JSON/text parity, and telemetry emission; CLI tests now assert match/mismatch outcomes and sanitized telemetry. Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest"`, `./gradlew --no-daemon spotlessApply check` (still red pending UI replay).
  _Intent:_ CLI replay command (S39-05, S39-10): added
  _Verification commands:_
  - `emv cap replay`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-48 – Operator UI replay integration (S39-05, S39-07): activated the Replay tab with stored preset dropdown, inline overrides, OTP entry, preview-window controls, include-trace checkbox, updated result card, and shared verbose trace console; refreshed console JavaScript to synchronise credential caches, share trace metadata, gate copy controls, and align Selenium coverage for stored match and inline mismatch flows. Commands: `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`; `./gradlew --no-daemon spotlessApply check` still fails at Jacoco branch coverage (0.68 < 0.70) after rerunning module test suites (`./gradlew --no-daemon --rerun-tasks :core:test :core-ocra:test :core-shared:test :application:test :infra-persistence:test :cli:test :rest-api:test :ui:test`).
  _Intent:_ Operator UI replay integration (S39-05, S39-07): activated the Replay tab with stored preset dropdown, inline overrides, OTP entry, preview-window controls, include-trace checkbox, updated result card, and shared verbose trace console; refreshed console JavaScript to synchronise credential caches, share trace metadata, gate copy controls, and align Selenium coverage for stored match and inline mismatch flows.
  _Verification commands:_
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`
  - `./gradlew --no-daemon --rerun-tasks :core:test :core-ocra:test :core-shared:test :application:test :infra-persistence:test :cli:test :rest-api:test :ui:test`

- [x] T-005-49 – Replay documentation & final verification (S39-05, S39-10): updated REST/CLI/operator UI how-to guides with replay instructions, refreshed roadmap/knowledge map/session snapshot, and recorded telemetry behaviour. Added targeted EMV/TOTP replay tests to raise Jacoco branch coverage from 0.68 to 0.7000 and ran the full Gradle quality gate. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.totp.TotpEvaluationServiceTest"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.
  _Intent:_ Replay documentation & final verification (S39-05, S39-10): updated REST/CLI/operator UI how-to guides with replay instructions, refreshed roadmap/knowledge map/session snapshot, and recorded telemetry behaviour. Added targeted EMV/TOTP replay tests to raise Jacoco branch coverage from 0.68 to 0.7000 and ran the full Gradle quality gate.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.totp.TotpEvaluationServiceTest"`
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`

- [x] T-005-50 – Verbose trace key redaction (S39-08): align EMV verbose traces with HOTP/TOTP/OCRA by hashing master keys (SHA-256) across application, REST, CLI, and operator UI layers while keeping session keys visible; update fixture expectations, Selenium/MockMvc assertions, and operator documentation to confirm digests replace raw master keys. Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :ui:test`. 
  _Intent:_ Verbose trace key redaction (S39-08): align EMV verbose traces with HOTP/TOTP/OCRA by hashing master keys (SHA-256) across application, REST, CLI, and operator UI layers while keeping session keys visible; update fixture expectations, Selenium/MockMvc assertions, and operator documentation to confirm digests replace raw master keys.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*"`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`
  - `./gradlew --no-daemon :ui:test`
  - `sha256:<hex>`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  _Notes:_
  > - 2025-11-02: Application trace model now emits `masterKeySha256` digests (`sha256:<hex>`) alongside session keys; CLI/REST/UIs surface the digest in verbose traces and JSON payloads; OpenAPI snapshots refreshed.
  > - 2025-11-02: EMV Selenium suite still red pending T-005-51 (global verbose toggle cleanup) because the protocol-specific include-trace checkboxes remain in the template; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` currently fails on the expected checkbox assertions and is deferred to the next task.

- [x] T-005-52 – Console verbose toggle harmonization (S39-08): remove EMV-specific include-trace checkboxes, update UI templates/JS to defer to the global verbose toggle, refresh Selenium assertions, and clean up documentation/screenshots. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Console verbose toggle harmonization (S39-08): remove EMV-specific include-trace checkboxes, update UI templates/JS to defer to the global verbose toggle, refresh Selenium assertions, and clean up documentation/screenshots.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`
  - `:jacocoCoverageVerification`
  - `includeTrace == null`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest"`
  _Notes:_
  > - 2025-11-02: EMV evaluate/replay templates and console JS now rely solely on the global verbose toggle; targeted Selenium run (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`) and `./gradlew --no-daemon :ui:test` both pass.
  > - 2025-11-02: Initial full `./gradlew --no-daemon spotlessApply check` run failed at `:jacocoCoverageVerification` (branches 0.69 &lt; 0.70); remediation tracked before closing the task.
  > - 2025-11-02: `jacocoAggregatedReport` confirmed project-wide branch coverage at 2160/3087 (≈0.6997). Covering any single additional branch (e.g., exercising the `includeTrace == null`/`false` code paths in `EmvCapEvaluationService` or `EmvCapReplayService`) would satisfy the ≥0.70 enforcement.
  > - 2025-11-02: Added MockMvc tests for stored replay requests omitting `includeTrace` and explicitly setting it to `false`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest"` and the full `./gradlew --no-daemon spotlessApply check` now pass, and Jacoco reports 2161/3087 covered branches (≈0.7000).

- [x] T-005-53 – Operator UI stored evaluate parity (S39-01, S39-02): Evaluate tab now exposes a stored credential dropdown wired to the shared cache, enables preset submission with empty-state messaging, and keeps inline fields editable; Selenium coverage exercises stored submission, inline overrides, and global verbose toggle behaviour (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`).
  _Intent:_ Operator UI stored evaluate parity (S39-01, S39-02): Evaluate tab now exposes a stored credential dropdown wired to the shared cache, enables preset submission with empty-state messaging, and keeps inline fields editable; Selenium coverage exercises stored submission, inline overrides, and global verbose toggle behaviour (
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :ui:test`

- [x] T-005-54 – REST stored evaluate endpoint (S39-01, S39-10): `POST /api/v1/emv/cap/evaluate` accepts `credentialId`, resolves presets via `CredentialStore`, and records credential metadata in telemetry; MockMvc plus OpenAPI snapshot suites cover stored success, inline override precedence, validation errors, and includeTrace suppression (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapEvaluationEndpointTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`).
  _Intent:_ REST stored evaluate endpoint (S39-01, S39-10):
  _Verification commands:_
  - `POST /api/v1/emv/cap/evaluate`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapEvaluationEndpointTest"`
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`

- [x] T-005-55 – CLI stored evaluate support (S39-01, S39-10): added `emv cap evaluate-stored` Picocli command leveraging the shared store, supporting inline overrides, JSON/text parity, sanitized telemetry, and includeTrace gating; new CLI tests cover stored success, override precedence, trace toggling, and digest redaction (`./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliEvaluateStoredTest"`, `./gradlew --no-daemon spotlessApply check`).
  _Intent:_ CLI stored evaluate support (S39-01, S39-10): added
  _Verification commands:_
  - `emv cap evaluate-stored`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliEvaluateStoredTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-56 – Remove UI transaction override inputs (S39-03, S39-07): Evaluate/Replay panels drop resolved/override terminal payload controls, relying on verbose traces for resolved ICC data while REST/CLI retain advanced overrides; console JS and Selenium assertions updated accordingly and the full UI/REST suites remain green (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`).
  _Intent:_ Remove UI transaction override inputs (S39-03, S39-07): Evaluate/Replay panels drop resolved/override terminal payload controls, relying on verbose traces for resolved ICC data while REST/CLI retain advanced overrides; console JS and Selenium assertions updated accordingly and the full UI/REST suites remain green (
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-57 – Evaluate mode toggle alignment (S39-02): Reintroduced stored vs. inline evaluation selector on the EMV Evaluate tab, collapsed to a single Evaluate CTA bound to the active mode, updated console template/JavaScript wiring, and refreshed UI/Selenium/unit coverage. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Evaluate mode toggle alignment (S39-02): Reintroduced stored vs. inline evaluation selector on the EMV Evaluate tab, collapsed to a single Evaluate CTA bound to the active mode, updated console template/JavaScript wiring, and refreshed UI/Selenium/unit coverage.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-58 – Preview window controls alignment (S39-06): Surfaced backward/forward preview offsets on the Evaluate tab, propagated the shared window schema through application/REST/CLI layers, and refreshed coverage. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Preview window controls alignment (S39-06): Surfaced backward/forward preview offsets on the Evaluate tab, propagated the shared window schema through application/REST/CLI layers, and refreshed coverage.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`
  - `previewWindowBackward/Forward`
  - `emv cap evaluate`
  - `--window-backward/--window-forward`
  _Notes:_
  > - 2025-11-04: Application service now builds `OtpPreview` lists for window offsets and records telemetry `previewWindowBackward/Forward`; tests assert delta ordering and central OTP preservation.
  > - 2025-11-04: REST DTOs/response payloads expose `previewWindow` + `previews` arrays; OpenAPI snapshots regenerated and MockMvc suites updated to cover stored/inline preview adjustments.
  > - 2025-11-04: CLI `emv cap evaluate`/`evaluate-stored` introduce `--window-backward/--window-forward`, print preview tables in text mode, and emit preview arrays in JSON responses; unit tests extended accordingly.
  > - 2025-11-04: Operator UI adds preview offset inputs, preserves stored-mode submissions (without triggering inline fallback), renders multi-row preview tables, and Selenium coverage exercises non-zero offsets.

- [x] T-005-59 – Verbose diagnostic parity (S39-08): Ensured EMV verbose traces expose ATC, branch factor, height, mask length, and preview window offsets across application/REST/CLI/UI traces; synced documentation/snapshots and reran the full Gradle quality gate. Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.
  _Intent:_ Verbose diagnostic parity (S39-08): Ensured EMV verbose traces expose ATC, branch factor, height, mask length, and preview window offsets across application/REST/CLI/UI traces; synced documentation/snapshots and reran the full Gradle quality gate.
  _Verification commands:_
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`

- [x] T-005-60 – Replay selector ordering & helper copy (S39-06, S39-07): Reordered the replay mode toggle so inline parameters appear before stored credential, defaulted the toggle to inline, added concise helper copy (“Manual replay with full CAP derivation inputs.” / “Replay a seeded preset without advancing ATC.”), and refreshed Selenium coverage plus targeted UI/REST suites. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Replay selector ordering & helper copy (S39-06, S39-07): Reordered the replay mode toggle so inline parameters appear before stored credential, defaulted the toggle to inline, added concise helper copy (“Manual replay with full CAP derivation inputs.” / “Replay a seeded preset without advancing ATC.”), and refreshed Selenium coverage plus targeted UI/REST suites.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-61 – Evaluate selector placement parity (S39-06, S39-07): Moved the "Choose evaluation mode" fieldset directly beneath the Evaluate panel heading so the stored credential preset/inline parameter sections follow it, matching HOTP/TOTP/FIDO2 ordering; extended the Selenium suite with a document-order assertion to guarantee the selector precedes the preset controls. Commands: `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Evaluate selector placement parity (S39-06, S39-07): Moved the "Choose evaluation mode" fieldset directly beneath the Evaluate panel heading so the stored credential preset/inline parameter sections follow it, matching HOTP/TOTP/FIDO2 ordering; extended the Selenium suite with a document-order assertion to guarantee the selector precedes the preset controls.
  _Verification commands:_
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-62 – Sample vector terminology parity (S39-09): Aligned the EMV Evaluate and Replay dropdown labels with other protocols by renaming them to "Load a sample vector" (with "Select a sample" placeholder) while keeping EMV hints concise; updated Selenium coverage and reran targeted REST/UI suites plus `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Sample vector terminology parity (S39-09): Aligned the EMV Evaluate and Replay dropdown labels with other protocols by renaming them to "Load a sample vector" (with "Select a sample" placeholder) while keeping EMV hints concise; updated Selenium coverage and reran targeted REST/UI suites plus
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_
  > - Application trace model now carries the additional metadata, REST/CLI serializers render the new fields, and operator console verbose traces surface the values via `VerboseTraceConsole`.
  > - Tests covering EMV evaluation, CLI JSON/text output, REST endpoints, replay metadata, and JS/Selenium suites updated to assert the new diagnostics; OpenAPI snapshots refresh the schema additions.

- [x] T-005-63 – Sample vector spacing parity (S39-09): Applied the shared `stack-offset-top-lg` utility to the Evaluate and Replay sample vector containers so EMV/CAP spacing matches other protocol panels, and added Selenium assertions to guard the helper on both panels; reran Selenium and the full formatting/check pipeline. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Sample vector spacing parity (S39-09): Applied the shared
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-64 – Evaluate sample block refinement (S39-09): Moved seed actions inside the Evaluate preset field group, added inline spacing styles, extended Selenium assertions, and reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` plus `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Evaluate sample block refinement (S39-09): Moved seed actions inside the Evaluate preset field group, added inline spacing styles, extended Selenium assertions, and reran
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-65 – Sample vector styling parity (S39-09): Extended Selenium coverage so Evaluate and Replay sample vector selectors assert the shared inline preset container and dark surface dropdown, refactored `panel.html` markup accordingly, and reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, and `./gradlew --no-daemon spotlessApply check`.
  _Intent:_ Sample vector styling parity (S39-09): Extended Selenium coverage so Evaluate and Replay sample vector selectors assert the shared inline preset container and dark surface dropdown, refactored
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-005-66 – Stored credential secret sanitisation (S39-03): Staged failing REST/JS/Selenium coverage to require digest + length placeholders for stored credentials, removed raw secret fields from directory responses, updated the console masks to consume the new metadata, and forced stored evaluate/replay submissions to hydrate full credentials server-side before execution. Refreshed OpenAPI snapshots and reran the full quality pipeline.
  _Intent:_ Stored credential secret sanitisation (S39-03): Staged failing REST/JS/Selenium coverage to require digest + length placeholders for stored credentials, removed raw secret fields from directory responses, updated the console masks to consume the new metadata, and forced stored evaluate/replay submissions to hydrate full credentials server-side before execution. Refreshed OpenAPI snapshots and reran the full quality pipeline.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapCredentialDirectoryControllerTest"`
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon :rest-api:test`
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`
  _Notes:_
  > - 2025-11-05: REST `EmvCapCredentialDirectoryController` now emits only `masterKeySha256` + length metadata; JS helpers clear stored inputs, derive masks from length properties, and skip transmitting sensitive fields for stored replay; Selenium assertions confirm blank inputs plus digest/length placeholders in both evaluate and replay tabs.
  > - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapCredentialDirectoryControllerTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.

- [x] T-005-67 – Verbose console red tests (S39-08): Stage Node + Selenium assertions that require the shared `VerboseTraceConsole` to appear only when `includeTrace` is enabled and disappears when disabled; cover both Evaluate and Replay flows so the shared toggle contract fails fast until wiring lands.
  _Intent:_ Lock the expected shared-console behaviour (presence, copy CTA, provenance sections) in automated tests before updating implementation.
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*Trace*"`
  _Notes:_
  > - 2025-11-14 Node coverage now asserts that inline replay payloads must set `includeTrace=true`; the new test fails because `buildReplayPayload` currently omits the flag (captured in `_current-session.md` log 2025-11-14a).
  > - Selenium adds `inlineReplayDisplaysVerboseTrace` to confirm the shared verbose trace dock appears for inline replay when the global toggle is enabled; the suite passes today because REST already returns traces for stored + inline scenarios even though the payload omission still exists.
  > - REST trace suites (`io.openauth.sim.rest.emv.cap.*Trace*`) were rerun after staging the new coverage to keep snapshots warm.

- [x] T-005-68 – Verbose console integration (S39-08): Wire EMV/CAP responses through the shared `VerboseTraceConsole.handleResponse`, ensure `includeTrace` flows through REST/CLI/application seams, and update Node/Selenium tests plus CLI/REST trace assertions to go green.
  _Intent:_ Deliver the functional changes demanded by I8b so every facade reuses the shared console controls while keeping trace payloads/parity intact.
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*Trace*"`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*Trace*"`
  - `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*Trace*"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :ui:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/full-gate-credentials.db ./gradlew --no-daemon spotlessApply check`
  _Notes:_
  > - `buildReplayPayload` now sets `includeTrace` explicitly based on the verbose-toggle state, so inline replay submissions request traces just like evaluate/stored flows; the shared `VerboseTraceConsole` hook keeps rendering responses through `handleResponse`.
  > - The full Gradle gate first timed out and then failed on a MapDB file lock; re-running `spotlessApply check` with a dedicated persistence path cleared `WebAuthnCredentialSanitisationTest` and produced a clean pass.

- [x] T-005-69 – Documentation refresh (S39-10): Update the EMV/CAP REST, CLI, and operator UI how-to guides, refresh the knowledge map + roadmap entry #5, and capture the I8b/I9 clarifications inside the feature spec/plan/tasks (including notes about the shared verbose console plus includeTrace handling).
  _Intent:_ Keep all reference docs aligned with the new verbose console + replay guidance before the verification sweep.
  _Verification commands:_
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Rest/CLI/operator UI how-to guides now explain that Evaluate and Replay respect the single verbose-trace toggle (propagating `includeTrace`), the spec/plan capture that clarification for I8b/I9, and roadmap/knowledge-map entries highlight the shared `VerboseTraceConsole` behaviour.

- [x] T-005-70 – Verification & session log (S39-10): Run the full Gradle quality gate after the documentation refresh and record the passing commands plus any follow-ups in `_current-session.md` and the Feature 005 plan/tasks.
  _Intent:_ Provide the final green evidence for I9 and prove the docs/code/tests remain in sync.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`
  - `./gradlew --no-daemon qualityGate` (if required by governance)
  - Update `_current-session.md` with the command log (no command, tracked as a documentation step)
  _Notes:_ Both commands passed (see `_current-session.md` 2025-11-15c/15d logs) and confirm the post-doc refresh build remains green across modules plus the governance `qualityGate` sweep.

- [x] T-005-71 – Replay fixture scaffolding & backend red tests (S39-05): Extend `docs/test-vectors/emv-cap/*.json` with stored/inline mismatch cases, add failing assertions to `EmvCapReplayApplicationServiceTest`, `EmvCapReplayEndpointTest`, `EmvCapReplayServiceTest`, and `EmvCliReplayTest`, and document the expected telemetry/mismatch payloads.
  _Intent:_ Capture the replay expectations (preview windows, mismatch deltas, trace payloads) before implementation.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.EmvCapReplayApplicationServiceTest.storedReplayMismatchTelemetryIncludesExpectedOtpHash"` (PASS – telemetry now exposes `expectedOtpHash` + `mismatchReason`)
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest.storedReplayMismatchIncludesOtpHash"` (PASS – REST metadata surfaces `expectedOtpHash` for stored mismatches)
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayServiceTest.metadataIncludesExpectedOtpHash"` (PASS – REST metadata adapter propagates hashed OTP when telemetry provides it)
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest.inlineReplayMismatchReturnsMismatchStatus"` (PASS – CLI JSON metadata now includes `expectedOtpHash`)
  _Notes:_
  > - Added `docs/test-vectors/emv-cap/replay-mismatch.json` (FX-005-04) plus `EmvCapReplayMismatchFixtures` to keep hashed OTP expectations deterministic; the follow-up implementation (T-005-73) wires telemetry + metadata so `expectedOtpHash` and `mismatchReason` surface across application/REST/CLI as required by TE-005-05.

- [x] T-005-72 – Replay UI placeholders (S39-05): Add Replay tab placeholders in the operator console JS + Selenium suites (Evaluate/Replay drawer interactions, OTP mismatch messaging, includeTrace toggle) and guard them with `@Disabled`/TODO markers so they fail fast once re-enabled.
  _Intent:_ Ensure UI automation is ready the moment replay implementation resumes, without blocking Feature 005’s current review.
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  _Notes:_
  > - Replay result card now exposes a diagnostics banner with explicit `data-placeholder="T-005-72"` markup, the JS stores hashed OTP metadata and renders a hashed OTP guidance banner when mismatches occur and verbose tracing is disabled, and Node/Selenium suites now exercise the banner copy/CTA behaviour directly (tests were originally skipped/`@Disabled` and have been re-enabled now that TE-005-05 telemetry is wired).

- [x] T-005-74 – Replay mismatch banner & UI coverage (S39-05): Activate the Replay mismatch banner, wire hashed OTP metadata and verbose-trace guidance into the operator console, and enable the T-005-72 Node/Selenium tests.
  _Intent:_ Surface hashed OTP diagnostics and clear guidance in the UI when replay mismatches occur without verbose tracing, keeping operators aligned with the backend telemetry.
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest.replayMismatchDisplaysDiagnosticsBanner"`
  _Notes:_
  > - The EMV/CAP replay result card now shows a visible banner on mismatch responses when telemetry includes `expectedOtpHash` but the global “Enable verbose tracing for the next request” toggle is disabled; the banner copy surfaces the `sha256:` digest and instructs operators to enable verbose tracing and replay for full diagnostics, and the CTA button becomes enabled (no longer `disabled`/`aria-disabled`) while leaving replay submission mechanics unchanged.

- [x] T-005-73 – Replay mismatch telemetry & metadata wiring (S39-05): Wire `expectedOtpHash` + `mismatchReason` into EMV/CAP replay mismatch telemetry (TE-005-05), propagate hashed OTP metadata through REST/CLI responses, refresh OpenAPI snapshots, and rerun the full Gradle gate.
  _Intent:_ Deliver the replay mismatch diagnostics required for backend/CLI parity so operators and the placeholder UI banner can rely on hashed OTP metadata without exposing raw digits.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.EmvCapReplayApplicationServiceTest.storedReplayMismatchTelemetryIncludesExpectedOtpHash"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayServiceTest.metadataIncludesExpectedOtpHash"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest.storedReplayMismatchIncludesOtpHash"`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest.inlineReplayMismatchReturnsMismatchStatus"`
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_
  > - `EmvCapReplayApplicationService` now computes an `otpHash` for successful replays and an `expectedOtpHash` for mismatches using a `sha256:` digest of the expected OTP, adding a `mismatchReason` field to mismatch telemetry events (`emv.cap.replay.mismatch`) without logging raw digits.
  > - REST metadata (`EmvCapReplayMetadata`) and CLI JSON metadata both surface `expectedOtpHash` when telemetry provides it; OpenAPI snapshots were refreshed to document the new metadata field, and the full `spotlessApply check` pipeline passed with replay diagnostics in place (see `_current-session.md` logs for timestamps).

## Verification log
- 2025-11-09 – `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
- 2025-11-09 – `./gradlew --no-daemon --console=plain :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`
- 2025-11-09 – `node --test rest-api/src/test/javascript/emv/console.test.js`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration sweep)

## Notes / TODOs
- Maintain dual storage of `trace-provenance-example.json` (docs + rest-api fixture) until sync automation lands; see T-005-67 notes.
- Any future EMV refinements must update this tasks file with new entries even while the feature awaits owner acceptance.
