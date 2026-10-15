# AGENTS.md

This file provides guidance to coding agents (Codex and others) working in this repository.

## Project Overview

**QBit QA Capture**: QQQ QBit for optional, provider-pluggable QA bug capture (Jam first), with sanitizer rules, request correlation and QA reports as standard QQQ tables. Design: `docs/DESIGN.md`.

## Build Commands

```bash
./mvnw compile          # Compile (always use the wrapper: Maven 3.10 cannot read the parent POM)
./mvnw test             # Unit tests
./mvnw verify           # Checkstyle, unit tests, JaCoCo gate (70% instructions, 90% classes)
```

## Naming (owner rule)

- Tables are lowercase snake_case prefixed `qa_capture_`. The QQQ table name and the backend (SQL) name are identical.
- Permissions use `qa_capture.<action>`.
- Qbit data lives in standard, visible QQQ tables, secured by permissions, never hidden.
- Processes run as table or record actions, never in the nav.
- Schema changes only through the qbit's Liquibase changelog; no superuser features; no filesystem.

## Code Style

- 3-space indentation, opening braces on the next line, `return (x);`.
- Javadoc flower-box comments (`/*****` with `**` lines) on every class and method.
- Fluent setters (`withX()`), wrapper types in config and entities.

## Dependencies

- Java 21, parent `com.kingsrook:qbit-build-parent:2.0.0`; the default-active `qqq-snapshot` profile imports `qqq-bom-pom:4.1.0-RC.11`.

## Release

- CircleCI qqq-orb 0.6.9: `develop` publishes SNAPSHOTs, `release/*` publishes RCs, `vX.Y.Z` tags publish releases.
- Merging a develop PR labelled `release:rc` cuts the next RC (`.github/workflows/release-rc.yml`).
