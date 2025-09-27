# Architecture (Draft)

Initial architecture targets:

- **Context (C4 Level 1):** Emulator runtime as a single node interacting with local MapDB storage and future clients (CLI, REST, UI).
- **Containers:**
  - `core` JVM library
  - Command-line interface (Picocli)
  - Spring Boot REST service
  - Server-rendered UI consuming REST API
- **Components:** Credential persistence adapters, crypto operations, protocol emulators.

Upcoming tasks:

1. Publish C4 diagrams (`docs/_assets`) once CLI/REST wiring starts.
2. Document MapDB on-disk format and caching behaviour.
3. Capture performance budgets (targeting 1000+ credential lookups/sec).
