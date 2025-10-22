# Changelog

We keep human-readable highlights here and rely on conventional commits + dependency locks for machine diffing.

## Unreleased

- Scaffolded Gradle multi-module workspace with quality tooling and dependency locking.
- Added `core` credential model and MapDB-backed store with caching.
- Seeded documentation structure and ADR log.
- Adopted Palantir Java Format 2.78.0 (Spotless + pre-commit), reformatted JVM sources, and refreshed contributor guidance for the 120-character wrap.
- Retired legacy CLI/UI entry points (Feature 031) so telemetry, routing, and operator networking use the unified adapters and Fetch API (T3101–T3108, 2025-10-19).
