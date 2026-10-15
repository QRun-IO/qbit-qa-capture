# QBit QA Capture: design

Provider-pluggable QA bug capture for QQQ applications, Jam (https://jam.dev) first. This QBit
and the QQQ Next UI (`QRun-IO/qqq-frontend-next`) implement one shared contract: the `qa-capture`
UI API extension 1.0.0 (`src/main/resources/openapi/qqq-v1/qa-capture.yaml`) plus the raw routes
below. Owner priority 2026-10-15.

## Goals

- Off by default and zero cost when off: a client that does not see the `qa-capture` extension,
  or gets `enabled: false`, makes no further calls and loads nothing from the provider.
- Capture only for chosen users: an administrator audience (users, roles) or self-serve QA mode
  for holders of `qa_capture.use_qa_mode`.
- Secrets and secure values never leave the browser: sanitizer rules are data (a standard table),
  served to the client, applied before the provider receives anything (fail closed: Jam redacts
  when a sanitizer throws).
- Every bug links to server logs: `X-Request-Id` on every API response, kept by the client and
  stored on the report.
- All QBit data in standard, visible QQQ tables; no filesystem; DB changes only through the
  Liquibase changelog.

## Components

| Component | Class |
|---|---|
| Config and producer | `QaCaptureQBitConfig`, `QaCaptureQBitProducer` |
| Tables, possible values, app, permissions | `metadata.QaCaptureMetaData`, `metadata.QaCapturePermissionChecker` |
| Write validation (rule values, trimming) | `metadata.QaCaptureWriteValidator` |
| Help bundle | `help/qbit-qa-capture.yaml`, `metadata.QaCaptureHelp` |
| Enablement (pure) | `service.QaCaptureEnablement` |
| Capability, reports, webhook context | `service.QaCaptureService` |
| Sanitizer rule set | `service.SanitizerRules`, `web.SanitizerScript` |
| Secured-data rules | `service.SecuredDataRuleSync` |
| QA mode preference | `service.QaModePreference` |
| UI API extension | `api.QaCaptureUiApi`, `api.CapabilitySpec`, `api.ReportCreateSpec`, `api.QaCaptureSchemas` |
| Raw routes | `web.QaCaptureRouteProvider`, `web.RecorderPage`, `web.QaCaptureRequestIds` |
| Dashboard CSP | `QaCaptureCsp` |
| Provider SPI | `spi.QaCaptureProvider`, `spi.JamProvider`, `spi.SvixSignatureVerifier` |

## Enablement

For the configured environment's settings row: `OFF` mode gives reason `OFF`; no row, no team id
or an unknown provider gives `NOT_CONFIGURED`. Otherwise an active administrator audience row
matching the user (audience modes) gives `AUDIENCE`; in self-serve modes a user with
`qa_capture.use_qa_mode` gets `QA_MODE` when their QA mode is on and `QA_MODE_OFF` when it is off;
everyone else gets `NO_ACCESS`.

QA mode is the `qa-capture` user preference section (one boolean), visible only with
`qa_capture.use_qa_mode` (core's section permission). It is stored in core's key-value preference
store when the host defines it, else as a `qa_capture_audience` row with source `QA_MODE`. No
fifth table.

## Request correlation

The route provider adds before and after handlers on `/qqq/*` and `/qa/*`: a valid incoming
`X-Request-Id` (`[A-Za-z0-9._:-]{8,128}`) is reused, else a UUID is generated; the id is set as the
response header and in the log4j ThreadContext (`requestId`) for the request and removed afterwards
(after handlers also run after exceptions). Storing request ids on audits needs core support; see
the QRun-IO/qqq issue linked from the pull request.

## Recorder page and CSP

`GET /qa/recorder` is a registered route, so the Next dashboard's SPA fallback (which only handles
requests no endpoint matched) never serves it, including its 404. Its inline module script is not
user dependent (it embeds the active rules), and its CSP allows it by sha256 hash.

`QaCaptureCsp.customize` adds the provider origins to the dashboard CSP only while capture is
active. The dashboard rebuilds its headers when the QInstance is set (startup and hot swap), so
settings changes reach the dashboard CSP on the next restart or hot swap.

## Webhooks

`POST /qqq/hooks/qa-capture/{provider}` delegates to the provider. Jam: Svix HMAC-SHA256 over
`{svix-id}.{svix-timestamp}.{body}` with the `whsec_` secret, constant-time comparison against
every `v1` signature, 5-minute timestamp tolerance, 1 MB cap; only `jam.created` for the
configured team is processed; idempotent on `jamId` (unique column).

## Placement

The QBit adds one app (`qaCapture`) with its four tables and no processes; `parentAppName` nests it
under the host's Admin app.
