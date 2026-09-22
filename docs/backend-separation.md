# OhPin backend separation

This document supersedes the direct-Supabase service boundary and no-new-environment/migration restriction in the earlier FE-only CFA-010/013/014/074 architecture for this backend separation.

Authority: user request in this task: separate Spring Boot DDD backend, retain existing Supabase, local/deployment environments, Docker, rename to OhPin-BE; replace Ponytail review with tests.

## Contract
- Preserve existing Pin Class UI, owner/participant identities, RLS, Storage paths and historical migrations. No Firebase migration and no restoration of Pin Feedback.
- Browser -> Spring Boot domain APIs -> Supabase Data API with the caller JWT and publishable key. Never use service-role credentials for application requests.
- Keep Supabase OAuth, isolated anonymous audience auth, resumable uploads and Realtime in the browser. Read-model mapping stays in the FE service; all business DB reads/writes move to BE.
- DDD packages: folder, lecture, question, experience, material. Each domain contains entity, dto, controller, repository and service packages as applicable, per the user clarification. No application/domain/infrastructure layer nesting within domains. Shared infrastructure owns authenticated HTTP, errors and security.
- Keep existing RLS constraints; add SECURITY INVOKER RPCs only for atomic material graph and point-question creation. Derive author/owner and slide relationships on the server/database.
- Audience API projections never include instructor notes, author UUIDs or private answers.
- Convert Storage source paths only (PDF/PPT/PPTX <= 1 GiB). Native LibreOffice/Poppler run in BE Docker; validated owner paths, bounded execution, per-job temporary directories, streamed download and cleanup.
- Local profile uses CLI Docker Supabase only. Production requires explicit Supabase URL/key and allowed origins. Credentials are runtime configuration and excluded from images/Git.
- Keep migration history in the backend. FE CLI commands delegate to sibling OhPin-BE.

## Verification
Write failing domain/security tests, implement, run JUnit and local Supabase integration checks (including RLS and atomic rollback), frontend lint/build and existing targeted tests. Build Docker image and exercise health/API. UI regression covers entry/auth redirect, folder views/Insights, player panels/notes/navigation. Record limits honestly.
