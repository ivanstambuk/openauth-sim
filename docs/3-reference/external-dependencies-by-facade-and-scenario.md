# External Dependencies by Facade and Scenario

_Status: Draft_  
_Last updated: 2025-11-15_  
_Owner: Documentation & Knowledge Automation_

## Table of contents

- [HOTP](#hotp)
  - [Native Java](#hotp--native-java)
  - [CLI](#hotp--cli)
  - [REST API](#hotp--rest-api)
  - [Operator UI](#hotp--operator-ui)
- [TOTP](#totp)
  - [Native Java](#totp--native-java)
  - [CLI](#totp--cli)
  - [REST API](#totp--rest-api)
  - [Operator UI](#totp--operator-ui)
- [OCRA](#ocra)
  - [Native Java](#ocra--native-java)
  - [CLI](#ocra--cli)
  - [REST API](#ocra--rest-api)
  - [Operator UI](#ocra--operator-ui)
- [EMV/CAP](#emvcap)
  - [Native Java](#emvcap--native-java)
  - [CLI](#emvcap--cli)
  - [REST API](#emvcap--rest-api)
  - [Operator UI](#emvcap--operator-ui)
- [FIDO2/WebAuthn](#fido2webauthn)
  - [Native Java](#fido2webauthn--native-java)
  - [CLI](#fido2webauthn--cli)
  - [REST API](#fido2webauthn--rest-api)
  - [Operator UI](#fido2webauthn--operator-ui)
- [EUDIW OpenID4VP](#eudiw-openid4vp)
  - [Native Java](#eudiw-openid4vp--native-java)
  - [CLI](#eudiw-openid4vp--cli)
  - [REST API](#eudiw-openid4vp--rest-api)
  - [Operator UI](#eudiw-openid4vp--operator-ui)

## Overview

This reference explains, for each simulator protocol, which major external dependencies are exercised for a given
consumption surface (Native Java, REST API, CLI, Operator UI), flow type (Evaluate vs Replay), and credential source
(stored vs inline). Most deployments package the simulator as a single “fat JAR”, so all libraries are present on the
classpath, but only a subset are actually touched per scenario.

The focus is on the main third‑party Java dependencies (with exact Maven coordinates) that shape the runtime stack:

- **Persistence:** `org.mapdb:mapdb:3.1.0` + `com.github.ben-manes.caffeine:caffeine:3.2.2`
- **Server/UI:** `org.springframework.boot:spring-boot-starter-web:3.3.4`, `org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`,
  `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`
- **CLI:** `info.picocli:picocli:4.7.7`
- **Tests-only:** `org.seleniumhq.selenium:htmlunit-driver:4.13.0`, plus Node.js test harnesses for console JavaScript
  (Node/npm, not a Java coordinate; not required in production)

Transitive libraries pulled in by Spring Boot or other dependencies are intentionally not listed individually.

> MapDB and Caffeine are **only exercised by stored flows** that dereference a `credentialId` via `CredentialStore`.
> Inline flows execute entirely in memory, even though the MapDB/Caffeine libraries are still present on the classpath.

The remainder of this document breaks dependencies down per protocol, facade, flow, and credential source.

---

## HOTP

HOTP supports Evaluate and Replay flows across all facades, with stored and inline variants.

### HOTP – Native Java

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | -                                                              |
| Evaluate | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | -                                                              |
| Replay   | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### HOTP – CLI

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Evaluate | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Replay   | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### HOTP – REST API

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### HOTP – Operator UI

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

---

## TOTP

### TOTP – Native Java

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | -                                                              |
| Evaluate | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | -                                                              |
| Replay   | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### TOTP – CLI

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Evaluate | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Replay   | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### TOTP – REST API

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### TOTP – Operator UI

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

---

## OCRA

OCRA also uses the same facade and persistence stack as HOTP/TOTP, with Evaluate and Replay flows in stored and inline
modes.

### OCRA – Native Java

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | -                                                              |
| Evaluate | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Replay   | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### OCRA – CLI

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Evaluate | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Replay   | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### OCRA – REST API

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### OCRA – Operator UI

| Flow     | Credential source | Dependencies (coordinates)                                     |
|----------|-------------------|----------------------------------------------------------------|
| Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

---

## FIDO2/WebAuthn

For FIDO2/WebAuthn, dependencies are split both by **operation type** (assertion vs attestation) and by flow/credential
source.

- **Assertion flows:** WebAuthn assertion Evaluate/Replay (authentication).
- **Attestation flows:** attestation generation and replay/verification.

### FIDO2/WebAuthn – Native Java

| Scenario    | Flow     | Credential source | Dependencies (coordinates)                                     |
|-------------|----------|-------------------|----------------------------------------------------------------|
| Assertion   | Evaluate | Inline            | -                                                              |
| Assertion   | Evaluate | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Assertion   | Replay   | Inline            | -                                                              |
| Assertion   | Replay   | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Attestation | Evaluate | Inline            | -                                                              |
| Attestation | Evaluate | Stored presets    | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Attestation | Replay   | Inline            | -                                                              |
| Attestation | Replay   | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### FIDO2/WebAuthn – CLI

| Scenario    | Flow     | Credential source | Dependencies (coordinates)                                     |
|-------------|----------|-------------------|----------------------------------------------------------------|
| Assertion   | Evaluate | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Assertion   | Evaluate | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Assertion   | Replay   | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Assertion   | Replay   | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Attestation | Evaluate | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Attestation | Evaluate | Stored presets    | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Attestation | Replay   | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Attestation | Replay   | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### FIDO2/WebAuthn – REST API

| Scenario    | Flow     | Credential source | Dependencies (coordinates)                                     |
|-------------|----------|-------------------|----------------------------------------------------------------|
| Assertion   | Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Assertion   | Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Assertion   | Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Assertion   | Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Attestation | Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Attestation | Evaluate | Stored presets    | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Attestation | Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Attestation | Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### FIDO2/WebAuthn – Operator UI

| Scenario    | Flow     | Credential source | Dependencies (coordinates)                                     |
|-------------|----------|-------------------|----------------------------------------------------------------|
| Assertion   | Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Assertion   | Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Assertion   | Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Assertion   | Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Attestation | Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Attestation | Evaluate | Stored presets    | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Attestation | Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Attestation | Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

---

## EMV/CAP

EMV/CAP exposes three high‑level scenarios—**Identify**, **Respond**, and **Sign**—each available in Evaluate and Replay
modes, with stored and inline variants. From a dependency perspective, all three scenario families behave the same, so
the tables below group them together.

### EMV/CAP – Native Java

| Scenario family          | Flow     | Credential source | Dependencies (coordinates)                                     |
|--------------------------|----------|-------------------|----------------------------------------------------------------|
| Identify/Respond/Sign    | Evaluate | Inline            | -                                                              |
| Identify/Respond/Sign    | Evaluate | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Identify/Respond/Sign    | Replay   | Inline            | -                                                              |
| Identify/Respond/Sign    | Replay   | Stored            | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### EMV/CAP – CLI

| Scenario family          | Flow     | Credential source | Dependencies (coordinates)                                     |
|--------------------------|----------|-------------------|----------------------------------------------------------------|
| Identify/Respond/Sign    | Evaluate | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Identify/Respond/Sign    | Evaluate | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Identify/Respond/Sign    | Replay   | Inline            | `info.picocli:picocli:4.7.7`                                   |
| Identify/Respond/Sign    | Replay   | Stored            | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### EMV/CAP – REST API

| Scenario family          | Flow     | Credential source | Dependencies (coordinates)                                     |
|--------------------------|----------|-------------------|----------------------------------------------------------------|
| Identify/Respond/Sign    | Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Identify/Respond/Sign    | Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Identify/Respond/Sign    | Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Identify/Respond/Sign    | Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### EMV/CAP – Operator UI

| Scenario family          | Flow     | Credential source | Dependencies (coordinates)                                     |
|--------------------------|----------|-------------------|----------------------------------------------------------------|
| Identify/Respond/Sign    | Evaluate | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Identify/Respond/Sign    | Evaluate | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Identify/Respond/Sign    | Replay   | Inline            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Identify/Respond/Sign    | Replay   | Stored            | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

---

## EUDIW OpenID4VP

EUDIW OpenID4VP introduces **Evaluate** (Generate) and **Replay** (Validate) flows for verifiable presentations across
Native Java, REST, CLI, and Operator UI. Credential material can come from inline SD‑JWT/mdoc payloads or stored
fixtures (trusted authorities, stored presentations).

### EUDIW OpenID4VP – Native Java

| Flow     | Credential source                        | Dependencies (coordinates)                                     |
|----------|------------------------------------------|----------------------------------------------------------------|
| Evaluate | Inline                                   | -                                                              |
| Evaluate | Stored fixtures (authorities/presentations) | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline                                   | -                                                              |
| Replay   | Stored                                   | `org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### EUDIW OpenID4VP – CLI

| Flow     | Credential source                        | Dependencies (coordinates)                                     |
|----------|------------------------------------------|----------------------------------------------------------------|
| Evaluate | Inline                                   | `info.picocli:picocli:4.7.7`                                   |
| Evaluate | Stored fixtures (authorities/presentations) | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline                                   | `info.picocli:picocli:4.7.7`                                   |
| Replay   | Stored                                   | `info.picocli:picocli:4.7.7`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### EUDIW OpenID4VP – REST API

| Flow     | Credential source                        | Dependencies (coordinates)                                     |
|----------|------------------------------------------|----------------------------------------------------------------|
| Evaluate | Inline                                   | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Evaluate | Stored fixtures (authorities/presentations) | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline                                   | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0` |
| Replay   | Stored                                   | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

### EUDIW OpenID4VP – Operator UI

| Flow     | Credential source                        | Dependencies (coordinates)                                     |
|----------|------------------------------------------|----------------------------------------------------------------|
| Evaluate | Inline                                   | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Evaluate | Stored fixtures (authorities/presentations) | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |
| Replay   | Inline                                   | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4` |
| Replay   | Stored                                   | `org.springframework.boot:spring-boot-starter-web:3.3.4`<br>`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0`<br>`org.springframework.boot:spring-boot-starter-thymeleaf:3.3.4`<br>`org.mapdb:mapdb:3.1.0`<br>`com.github.ben-manes.caffeine:caffeine:3.2.2` |

---

## Test‑only dependencies

The simulator includes additional dependencies that are **only used in tests or local verification** and are not required
for production consumption of any facade:

- `org.seleniumhq.selenium:htmlunit-driver:4.13.0` – exercises Operator UI flows end‑to‑end.
- Node.js + JS test frameworks (installed via Node/npm, not a Java coordinate) – drive console JavaScript unit tests for
  EMV/CAP and EUDIW panels.

These are wired through Gradle test tasks (for example `:rest-api:emvConsoleJsTest`, `:rest-api:eudiwConsoleJsTest`) and
do not affect runtime correctness or persistence behaviour for any Evaluate/Replay scenario.
