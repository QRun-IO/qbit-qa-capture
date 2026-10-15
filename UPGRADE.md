# Upgrade notes

## 0.1.0 (first release)

Adopting the QBit:

1. Include `db/liquibase/qbit-qa-capture/qa_capture.changelog.sql` in the host's master changelog
   and run it (as the application database user).
2. Produce `QaCaptureQBitProducer` with `backendName`, `environment` and, if wanted,
   `parentAppName`, `backendVersion`, `withJamWebhookSecret(...)` and `withRoleResolver(...)`.
3. Call `QaCaptureCsp.customize(headers)` from the Next dashboard security headers customizer.
4. Add `%X{requestId}` to the log pattern.
5. Grant `qa_capture.manage`, `qa_capture.view_reports` and `qa_capture.use_qa_mode` as needed, and
   add a `qa_capture_settings` row per environment (capture stays off until then).
