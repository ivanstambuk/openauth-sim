# Feature <NNN> Tasks – <Descriptive Name>

_Status: Draft_  
_Last updated: YYYY-MM-DD_

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [ ] T-<NNN>-01 – <Task title> (F-<NNN>-01, N-<NNN>-01, S-<NNN>-01).  
  _Intent:_ What this task delivers (tests, implementation, docs).  
  _Verification commands:_  
  - `./gradlew --no-daemon :module:test --tests "…" `  
  - `node --test …`  
  - `./gradlew spotlessApply check`  
  _Notes:_ Link to related spec sections or follow-ups.

- [ ] T-<NNN>-02 – <Task title>.  
  _Intent:_ …  
  _Verification commands:_ …  

Add as many tasks as necessary, keeping stored vs inline, CLI vs REST, and UI increments separate.

## Verification Log (Optional)
Track long-running or shared commands (full Gradle gate, Selenium suites, etc.) with timestamps to avoid duplicate work.

- YYYY-MM-DD – `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`

## Notes / TODOs
Document temporary skips, deferred tests, or environment quirks so the next agent can follow up.
