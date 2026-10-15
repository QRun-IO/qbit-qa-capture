# QBit QA Capture

Optional, provider-pluggable QA bug capture for QQQ applications. Chosen users (an audience, or
anyone with the QA mode permission who switches QA mode on) can record a bug with screen,
console and network context; secrets and secure values are removed in the browser before
anything is sent. [Jam](https://jam.dev) is the first provider. Settings, audiences, sanitizer
rules and QA reports are standard QQQ tables, and every API response carries an `X-Request-Id`
that reports collect, so a bug links straight to the server log lines.

Design: [docs/DESIGN.md](docs/DESIGN.md). The UI contract is the `qa-capture` UI API extension
1.0.0, committed as [`src/main/resources/openapi/qqq-v1/qa-capture.yaml`](src/main/resources/openapi/qqq-v1/qa-capture.yaml);
the QQQ Next UI (`QRun-IO/qqq-frontend-next`) implements the client side.

## Requirements

- Java 21, QQQ 4.1 (built against `qqq-bom-pom` 4.1.0-RC.11).
- `qqq-middleware-javalin` (the UI API extension, request ids, recorder page and webhooks).
- An RDBMS backend for the four tables (PostgreSQL in production; the changelog runs as an
  ordinary application user).

## Install

```xml
<dependency>
   <groupId>com.kingsrook.qbits</groupId>
   <artifactId>qbit-qa-capture</artifactId>
   <version>0.1.0</version>
</dependency>
```

Include the changelog from the host's Liquibase master changelog (the QBit never runs DDL):

```yaml
- include: { file: db/liquibase/qbit-qa-capture/qa_capture.changelog.sql }
```

Produce the QBit after the host's Admin app exists:

```java
new QaCaptureQBitProducer()
   .withQBitConfig(new QaCaptureQBitConfig()
      .withBackendName("rdbms")
      .withEnvironment(System.getenv("APP_ENVIRONMENT"))            // "prod": selects the settings row
      .withBackendVersion(buildVersion)                               // from the host's manifest
      .withParentAppName("admin")                                     // nest under the Admin app
      .withJamWebhookSecret(System.getenv("QA_CAPTURE_JAM_WEBHOOK_SECRET"))
      .withRoleResolver(session -> identityProviderGroups(session)))  // optional, for ROLE audience rows
   .produce(qInstance);
```

## Tables

| Table | What it holds | How rows get created |
|---|---|---|
| `qa_capture_settings` | One row per environment: provider, team id, reusable recording link id, mode (`OFF`, `AUDIENCE`, `SELF_SERVE`, `AUDIENCE_AND_SELF_SERVE`), extra blur selectors. | The host's migration or an administrator. Nothing is seeded. |
| `qa_capture_audience` | Users (`USER`: user id or email) and roles (`ROLE`) who capture in the audience modes. Rows with source `QA_MODE` hold a user's self-serve QA mode switch when the host has no core key-value preference store; they never grant audience membership. | Administrators; the QA mode preference. |
| `qa_capture_sanitizer_rule` | What captures redact: `HEADER`, `BODY_FIELD`, `QUERY_PARAM`, `VALUE_PATTERN`, `DROP_PATH`, `BLUR_SELECTOR`. Active rows are the rule set served to browsers. | Built-in rows from the changelog (insert-only); `SECURED_DATA` rows at startup for fields the instance marks secure; administrators. |
| `qa_capture_report` | One reported bug: page, app, table, record, process, recent request ids, user, environment, build, node, browser, the Jam capture and the GitHub issue. | Report a bug (`POST /qqq/v1/qbit/qa-capture/report`) and unmatched provider webhooks. |

The QQQ table names equal the SQL names. The only foreign key is
`qa_capture_audience.settings_id` (cascade). Nothing references a WORM table. Every table has
help (including how its rows get created) from `help/qbit-qa-capture.yaml`.

Secure fields: a field is secure when core metadata marks it (`QFieldMetaData.isSensitive`, or a
field-level data classification of PII or PHI through core's `AiFieldMetaData`, which
`qbit-secured-data` classifications also set). There is no dependency on `qbit-secured-data`.
Startup adds one `BODY_FIELD` rule per secure field name, insert-only: a rule an administrator
edits or deactivates is never changed back.

## Permissions

| Permission | Grants |
|---|---|
| `qa_capture.manage` | Read and write settings, audience and sanitizer rules (and reports). |
| `qa_capture.view_reports` | Read and triage QA reports; the capability's `reportsHref`. |
| `qa_capture.use_qa_mode` | The "QA capture" preference section with the QA mode switch (self-serve modes). |

They are separately grantable. Capture itself is decided per user by the settings mode and
audience; the capability never answers 403 (it returns `enabled: false` and a reason).

`ROLE` audience rows match the names the config's role resolver returns, case-insensitively.
QQQ 4.1 sessions have no role list of their own, so the default resolver returns the session's
permission names; hosts pass their identity-provider groups (for example Authentik groups such as
`kof22-admins`). `USER` rows match the session user's id reference or email, case-insensitively.

## Configuration

| `QaCaptureQBitConfig` | Default | Meaning |
|---|---|---|
| `backendName` | required | Backend of the four tables. |
| `environment` | required | Selects the `qa_capture_settings` row (for example `prod`). |
| `backendVersion` | none | Reported in the capability context and on reports. |
| `node` | `HOSTNAME` env | Reported in the capability context and on reports. |
| `appName`, `appLabel` | `qaCapture`, `QA Capture` | The QBit's app (its four tables, no processes). |
| `parentAppName` | none | Nest the app under the host's Admin app (must exist when producing). |
| `withJamWebhookSecret(...)` / `withWebhookSecret(provider, ...)` | none | Webhook signing secret; without one the webhook answers 404. Never stored in tables or logged. |
| `withRoleResolver(...)` | permission names | Role (group) names for `ROLE` audience rows. |
| `withProvider(...)` / `withProviders(...)` | Jam | The provider registry (`QaCaptureProvider` SPI). |
| `registerRoutes` | `true` | The UI API extension and the route provider. |
| `syncSecuredDataRules` | `true` | Add `SECURED_DATA` rules at startup. |

Environment variables are read by the host, not the QBit. Suggested names:
`QA_CAPTURE_JAM_WEBHOOK_SECRET` (the `whsec_...` secret) and the host's existing environment and
build variables.

## Routes

| Route | Auth | What |
|---|---|---|
| `GET /qqq/v1/qbit/qa-capture/capability` (`qaCaptureCapability`) | session | The user's capability (contract). |
| `POST /qqq/v1/qbit/qa-capture/report` (`qaCaptureReportCreate`) | session | Create a report; 403 when capture is off for the user. |
| `GET /qa/recorder` | public | Jam's connected-domain page (team meta, both scripts, sanitizers from the active rules), its own CSP with the inline script's sha256, `Cache-Control: no-store`, never a redirect; 404 when capture is off. A matched route, so it wins over the Next dashboard fallback. |
| `POST /qqq/hooks/qa-capture/jam` | Svix signature | `jam.created`: attaches the capture to the report named by `recordingLink.reference` (status `CAPTURED`), else creates a report. Idempotent on `jamId`; 1 MB cap; 5-minute timestamp tolerance. |
| every `/qqq/*` and `/qa/*` response | | `X-Request-Id` (a valid incoming `[A-Za-z0-9._:-]{8,128}` value is reused, else a UUID). |

## Adopting in another app

1. Add the dependency, include the changelog, produce the QBit (above).
2. Add the provider origins to the Next dashboard CSP while capture is active:

   ```java
   new QApplicationJavalinServer(...)
      .withNextDashboardSecurityHeadersCustomizer(headers ->
      {
         myOtherCustomizations(headers);
         QaCaptureCsp.customize(headers);   // or QaCaptureCsp.customize(headers, qInstance)
      })
   ```

   `QaCaptureCsp.customize(NextDashboardSecurityHeaders headers, QInstance instance)` adds
   `script-src https://js.jam.dev` and `frame-src`/`connect-src https://*.jam.dev` only when the
   environment's settings row has a mode other than `OFF` and a team id; `'self'` is untouched. It
   reads the database in the system context and never throws (a failure is logged and the headers
   stay as they were). The dashboard rebuilds its headers when the QInstance is set, so settings
   changes reach the dashboard CSP on the next restart or instance hot swap. The capability, the
   recorder page and the webhook follow the settings immediately.
3. Put the request id in your log pattern: `%X{requestId}` (log4j ThreadContext key `requestId`).
   Code can read it with `QaCaptureRequestIds.current()`.
4. Grant the permissions, add a settings row for each environment and choose the audience.
5. Optional: define core's key-value preference store
   (`UserPreferencesKeyValueStoreMetaDataProvider`); the QA mode switch then lives there instead of
   in `qa_capture_audience`.

## Jam setup

1. In Jam, connect your domain: Jam verifies a public URL that must not redirect, must keep query
   parameters and must contain both scripts and the matching `jam:team` meta. Use
   `https://<your-host>/qa/recorder` (it answers 404 until a settings row turns capture on).
2. Copy the Jam team id (for example `0815d765-0953-4f79-91a3-f5ed435abbdd`) into
   `qa_capture_settings.team_id` for the environment and choose the mode.
3. Create a reusable Recording Link for the connected domain and put its id in
   `recording_link_id`; Report a bug opens it with `jam-recording`, `jam-title` and
   `jam-reference`.
4. Add a webhook for `jam.created` pointing to `https://<your-host>/qqq/hooks/qa-capture/jam`;
   pass its signing secret (`whsec_...`) to `withJamWebhookSecret` from your environment.
5. Connect Jam's GitHub integration to file issues from captures; paste the issue URL into the
   report's GitHub Issue field (status `FILED`) while triaging.

## Build

```bash
./mvnw verify
```

The foreign key guardrail runs PostgreSQL in Docker (Testcontainers) and is skipped without
Docker. The JavaScript sanitizer test runs in Node and is skipped without `node`. To regenerate the
UI API fragment after a deliberate contract change:
`./mvnw -Dtest=QaCaptureApiContractTest -Dqacapture.openapi.update=true test`.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
