# Backend separation verification — 2026-09-15

## Passed

- TDD baseline: `DomainRulesTest` failed before the Folder/Point/SourcePath implementations existed, then passed after implementation.
- Java 21 / Spring Boot 3.5.14: `./gradlew test bootJar`, 25 JUnit tests, 0 failures.
- Tests cover input bounds, Unicode, storage ownership/traversal, public projections, authentication/role denial, unknown payload fields, environment separation, retryable file cleanup, abandoned conversion responses, failed-conversion slot release and oversized source rejection.
- Supabase CLI 2.105.0: new isolated `ohpin_be` project on ports 5632x; full migration start and clean local reset succeeded.
- `npm run test:db`: 6 SQL files, 16 pgTAP assertions passed. The existing assertion-only class deletion test was wrapped with a TAP plan so the standard runner can execute it.
- HTTP integration: instructor/participant identities, cross-owner denial, folder CRUD, material graph creation, private notes, invalid graph/anchor rollback, question editing/reactions/answers/resolution, slide append/delete, experience and cleanup passed.
- Docker multi-stage build passed. The runtime ran as user `ohpin`, with a read-only root filesystem and writable temporary mount, and reported healthy.
- Real PDF, legacy PPT and PPTX conversion passed inside Docker. NDJSON page counts/events/done, Storage objects and browser-visible image URLs were checked.
- Frontend: 29 existing Node tests passed; ESLint and Next.js production build passed.
- Independent Chrome browser: signed-out entry redirected to login; a local test instructor session redirected to dashboard; folder open, grid/list switch, Insights, material player, rail collapse, question-panel keyboard resizing, note save/reload, filmstrip overflow and keyboard navigation passed. Arrow keys while editing notes did not navigate slides. Audience view did not expose instructor notes.

## Re-run

```bash
npm ci
npm run supabase:start
npm run supabase:status -- --output env > .env.integration.local
./gradlew test bootJar
npm run test:db
# API running on 8080:
python3 scripts/integration-test.py --status-file .env.integration.local
# Docker API running on 18080, includes native converters:
python3 scripts/integration-test.py --status-file .env.integration.local --api http://localhost:18080 --with-conversion
# Prepare a local-only fixture; requires Chrome installed, FE on 3000 and API on 8080:
python3 scripts/integration-test.py --status-file .env.integration.local --keep-browser-fixture
npm run test:browser
```

The browser fixture is written to `/tmp/ohpin-browser-fixture.json` with mode 0600 and intentionally retains local test identities/materials for browser testing. Normal integration runs delete the identities they created. Real Supabase service credentials are never required by the application; local test provisioning alone uses the CLI service-role key.

## Limits

- Actual Google OAuth consent was not exercised because local Google client credentials were not supplied. The existing OAuth flow was preserved; redirects and application auth handling were tested with isolated local Supabase identities.
- Production Supabase was neither modified nor migrated. No production deployment was performed.
- One-GiB sustained-load, multi-instance conversion throughput and production p95 performance were not load-tested.
- Initial Docker storage exhaustion was resolved by removing only newly downloaded optional Supabase image caches; existing unrelated containers and volumes were preserved. The validation stack excluded optional Studio/logging services.
