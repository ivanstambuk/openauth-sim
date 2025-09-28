# Feature 002 – Persistence & Caching Hardening Tasks

_Status: In planning_
_Last updated: 2025-09-28_

## Execution Notes
- Follow the specification in `docs/4-architecture/specs/feature-002-persistence-hardening.md`.
- Work remains within the `CredentialStore` abstraction; new public APIs require explicit approval.
- Maintain secret redaction in all logs/metrics and ensure benchmarks do not persist sensitive data.
- Keep increments ≤10 minutes, run `./gradlew spotlessApply check`, and record benchmark outputs in the feature plan.

## Phase 0 – Foundations
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T201 | Establish benchmark harness and baseline metrics for MapDB + Caffeine. | FR-202, NFR-201, NFR-202 | No |
| T202 | Add structured metrics/logging (hit/miss, latency) with secret redaction checks. | FR-202, NFR-203, NFR-204 | No |

### T201 – Benchmark Harness Checklist
- [x] Introduced `MapDbCredentialStoreBaselineBenchmark` with opt-in execution flag/environment variable.
- [x] Captured baseline in-memory metrics (writes ≈1.7k ops/s; reads ≈567k ops/s; P99 ≈0.0043 ms) on 2025-09-28.
- [ ] Document rerun instructions and persist sample output for future comparisons.

## Phase 1 – Cache Strategy
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T203 | Tune Caffeine configuration (maximum size, TTL, eviction strategy) guided by benchmarks. | FR-201, NFR-201 | No |
| T204 | Document deployment profiles (embedded, local disk, container) with default settings. | FR-201 | Yes |

## Phase 2 – Storage Maintenance
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T205 | Implement MapDB compaction and integrity verification hooks with tests. | FR-203 | No |
| T206 | Surface admin APIs/tests to trigger maintenance safely. | FR-203 | No |

## Phase 3 – Optional Protections
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T207 | Define encryption provider interface and default no-op implementation. | FR-204, NFR-204 | No |
| T208 | Add documentation/tests covering encryption hook behaviour and key rotation guidance. | FR-204 | No |

## Phase 4 – Wrap-up
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T209 | Update concepts/knowledge map/roadmap with persistence findings and telemetry references. | FR-201–FR-204 | Yes |
| T210 | Record final benchmarks/self-review outcomes in the feature plan and close open questions. | NFR-201–NFR-204 | No |

## Open Follow-ups
- Populate benchmark results and decisions as tasks complete.
- Track any approval needed for additional tooling or dependencies before implementation.
