# Changelog

All notable changes to this project are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

Nothing yet.

## [0.1.0] - Unreleased

### Added

- Repository scaffold: build, CI, community files.
- Tables `qa_capture_settings`, `qa_capture_audience`, `qa_capture_sanitizer_rule` and
  `qa_capture_report` with their Liquibase changelog (built-in sanitizer rules seeded insert-only,
  one guarded foreign key), help bundle and an app the host can nest under its Admin app.
- Permissions `qa_capture.manage`, `qa_capture.use_qa_mode` and `qa_capture.view_reports`.
- Enablement by audience (users and roles, host role resolver) or self-serve QA mode (the
  `qa-capture` user preference section).
- `qa-capture` UI API extension 1.0.0: `qaCaptureCapability` and `qaCaptureReportCreate`.
- Request correlation: `X-Request-Id` on `/qqq/*` and `/qa/*`, log4j ThreadContext `requestId`,
  `QaCaptureRequestIds.current()`.
- Public `/qa/recorder` page for Jam's connected domain, with hash-based CSP.
- Svix-verified Jam `jam.created` webhook at `/qqq/hooks/qa-capture/jam`.
- `QaCaptureCsp.customize(...)` for the Next dashboard Content-Security-Policy.
- Provider SPI (`QaCaptureProvider`) with Jam as the first provider.
- Secured-data sanitizer rules added at startup from core field metadata.
