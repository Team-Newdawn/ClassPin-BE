# OhPin-BE

Pin Class의 Spring Boot 백엔드입니다. **기존 Supabase DB·Auth·Storage·Realtime을 유지**합니다.
Java 21, Spring Boot 3.5, Gradle Wrapper를 사용합니다.

## 폴더 구조

```text
src/main/java/com/ohpin/
├── folder/      # 강사 폴더
├── lecture/     # 강의 조회·상태·폴더 이동
├── material/    # 자료·슬라이드·메모·파일 변환
├── question/    # PIN 질문·공감·답변·해결
├── experience/  # 참여 후기
└── shared/      # 인증·설정·Supabase HTTP·공통 오류
```

각 업무 도메인은 `entity`, `dto`, `controller`, `repository`, `service`로 구분합니다.
Controller는 DTO를 받아 Service에 전달하고, Service는 Entity 규칙과 Repository를 사용합니다.
Repository 구현은 요청자의 Supabase JWT를 전달합니다. Entity는 순수 Java 모델이며,
JPA의 `ddl-auto`나 관리자 DB 연결로 기존 스키마·RLS를 변경하지 않습니다.

## 프론트와 백엔드 경계

- FE: 화면·UI 상태·응답 매핑, Supabase Google 로그인/익명 세션, 재개 가능한 Storage 업로드, Realtime 구독.
- BE: DB 조회·업무 저장·권한 확인, PDF/PPT/PPTX 변환, 삭제 파일 정리.
- BE의 업무 요청은 **publishable key + 호출자의 access token**을 사용합니다. 서비스 키는 필요 없습니다.
- 참여자 응답에 발표 메모·질문 작성자 UUID를 포함하지 않습니다.
- 원본 업로드 한도는 1 GiB이며 원본 바이트를 BE에 재전송하지 않습니다. `/api/convert`에는 `sourcePath`, `fileName`만 보냅니다.

## 로컬 실행

사전 준비: Java 21, Docker, Node 22.13 이상(개발 CLI/브라우저 테스트용).

```bash
npm ci
npm run supabase:start
npm run supabase:status
cp .env.local
# 위 status의 로컬 publishable key 또는 anon key를 .env.local에 입력
./gradlew bootRun
```

- API: `http://localhost:8080`, Supabase: `http://127.0.0.1:56321`.
- 기존 로컬 `pin_class` 데이터를 건드리지 않도록 `ohpin_be` 프로젝트와 5632x 포트를 사용합니다.
- local 프로필은 로컬 Supabase 호스트만 허용하며 `.env.local`을 자동으로 읽습니다.
- 메모리/디스크가 부족한 환경은 `npm run supabase:start -- -x studio,analytics,edge-runtime,vector`로 선택 서비스를 제외할 수 있습니다.
- 로컬 Google 로그인은 `supabase/config.toml`의 `[auth.external.google] enabled = true`로 변경하고,
  `.env`에 `SUPABASE_AUTH_EXTERNAL_GOOGLE_CLIENT_ID`, `SUPABASE_AUTH_EXTERNAL_GOOGLE_CLIENT_SECRET`을 설정합니다.
  Google OAuth 콜백은 `http://127.0.0.1:56321/auth/v1/callback`입니다. 기본 검증 설정은 Google provider를 꺼 둡니다.
- 프론트는 `ClassPin-FE/.env.local.example`을 `.env.local`로 복사하고 같은 로컬 키를 입력한 뒤 `npm run dev`로 실행합니다.

### Docker로 로컬 BE 실행

```bash
docker compose -f compose.local.yml up --build -d
```

Supabase CLI 스택은 먼저 실행해야 합니다. 컨테이너 내부에서는 `host.docker.internal:56321`,
브라우저에서는 `127.0.0.1:56321`을 사용하므로 `SUPABASE_PUBLIC_URL`을 분리했습니다.

## Docker 배포

```bash
cp .env.prod.example .env.prod
# 기존 운영 Supabase URL/publishable key, 프론트 HTTPS origin을 입력
# 먼저 아래 새 migration을 기존 Supabase에 적용한 후 BE -> FE 순서로 배포
docker build -t ohpin-be:latest .
docker compose --env-file .env.prod up --no-build -d
docker compose logs -f api
curl http://127.0.0.1:8082/actuator/health
```

- 기본 공개 포트는 `127.0.0.1:8082`입니다. HTTPS reverse proxy를 앞에 연결하세요.
  외부 바인딩이 필요하면 `API_BIND_ADDRESS`, 외부 포트는 `API_PORT`를 설정합니다.
- prod 프로필은 Supabase HTTPS URL과 명시적인 `ALLOWED_ORIGINS`가 필수입니다.
- `.env.prod`는 이미지에 포함되지 않습니다. 배포 시스템 환경변수/secret manager로도 주입할 수 있습니다.
- 이미지는 JUnit 테스트와 JAR 빌드를 수행하고, Java 21 JRE·LibreOffice·Poppler·한글 폰트를 포함합니다.
- 비루트 사용자, 읽기 전용 파일시스템, 임시 변환 디렉터리, healthcheck를 사용합니다.
- 큰 문서 변환을 지원하는 reverse proxy의 요청 시간 제한을 3600초로 설정하고 NDJSON 버퍼링을 끄세요.
- CPU·임시 디스크 사용량에 맞게 `CONVERSION_MAX_CONCURRENT`를 조절하세요. 기본값은 2입니다.
- 프론트 빌드 시 `NEXT_PUBLIC_API_BASE_URL=https://백엔드주소`를 반드시 지정합니다.
- local 전용 `/api/demo/**`는 prod에서 허용되지 않습니다. 데모 파일은 로컬 임시 디스크에만 보관됩니다.

### GitHub Actions 자동 배포

`.github/workflows/deploy.yml`은 `main` 브랜치 push와 GitHub Actions의 **Run workflow** 버튼으로 실행됩니다.
JUnit 테스트와 Docker 이미지 빌드가 성공하면 이미지를 EC2의 `/home/<EC2_USER>/ohpin-be`로 전송하고,
`/actuator/health`가 정상일 때 배포를 완료합니다. 새 컨테이너가 비정상이면 직전 이미지로 복구합니다.

Repository Actions secrets에는 다음 값을 설정합니다.

- `EC2_HOST`: EC2 공개 호스트명 또는 IP
- `EC2_USER`: SSH 사용자명
- `EC2_SSH_KEY`: PEM 개인 키 전체 내용
- `ENV_FILE`: `.env.prod.example` 형식의 운영 환경변수 전체 내용

`ENV_FILE`에는 Markdown 링크가 아닌 원문 URL을 사용합니다. 백엔드는 사용자 access token과 publishable key로
Supabase RLS를 통과하므로 `SUPABASE_SECRET_KEY`와 `SUPABASE_JWKS_URL`은 배포 대상에 포함하지 않습니다.
EC2 사용자는 Docker 및 `docker compose`를 비밀번호 없이 실행할 수 있어야 합니다.

## DB migration과 롤백

`supabase/migrations`가 유일한 스키마 이력입니다. FE에 있던 기존 SQL 파일은 그대로 이동했습니다.
새 `20260915015744_ohpin_atomic_backend_operations.sql`은 다음 기능을 추가합니다.

- `ohpin_create_material`: Course → Lecture → Material → Version → Slides 원자적 생성.
- `ohpin_submit_point_question`: 슬라이드 관계를 DB에서 확인한 뒤 좌표와 질문을 원자적으로 저장.
- `ohpin_delete_course` 및 `storage_cleanup_jobs`: cascade 삭제 시 파일 정리 경로를 보존.

먼저 로컬에서 `npm run supabase:reset`으로 검증하세요. **reset은 로컬 DB 데이터를 삭제합니다.**
운영에서는 기존 프로젝트를 백업하고 대상 프로젝트를 확인한 뒤 Supabase CLI의 migration push 절차를 따릅니다.
이 작업은 운영 DB에 migration을 적용하거나 서비스를 실제 배포하지 않았습니다.
추가 migration은 기존 FE와 호환됩니다. 애플리케이션 rollback 시 새 테이블/RPC는 남겨두어도 됩니다.

파일 정리 실패는 DB 삭제를 되돌리지 않습니다. 작업은 `storage_cleanup_jobs`에 남고,
다음 자료 삭제 또는 `POST /api/instructor/storage-cleanup` 요청에서 재시도합니다.
`cleanupPending: true`는 파일 정리가 아직 남았다는 뜻입니다. 아직 참조 중인 파일은 삭제하지 않습니다.

## API

모든 업무 API는 `Authorization: Bearer <Supabase access token>`을 요구합니다.
에러는 `{ "error": "..." }` 형태입니다. 원본 DB 오류나 토큰은 응답에 노출하지 않습니다.

| 영역 | 메서드 · 경로 |
|---|---|
| 프로필 | GET `/api/me` |
| 폴더 | GET/POST `/api/instructor/folders`, PATCH/DELETE `/api/instructor/folders/{id}` |
| 강의 | GET `/api/instructor/courses`, PATCH `/api/instructor/lectures/{id}` |
| 이동·삭제 | PATCH `/api/instructor/courses/{id}/folder`, DELETE `/api/instructor/courses/{id}` |
| 자료 | POST `/api/instructor/materials` |
| 슬라이드 | GET/POST `/api/instructor/versions/{id}/slides`, DELETE `/api/instructor/slides/{id}` |
| 발표 메모 | PUT `/api/instructor/slides/{id}/note` |
| 참여 | GET `/api/participant/join/{code}`, GET `/api/participant/lectures/{id}` |
| 상태·질문 | GET `/api/{instructor\|participant}/lectures/{id}/{state\|questions}` |
| 질문 등록 | POST `/api/participant/lectures/{id}/questions` |
| 수정·공감 | PATCH `/api/participant/questions/{id}`, PUT `/api/participant/questions/{id}/reaction` |
| 답변·해결 | POST `/api/instructor/questions/{id}/answers`, PUT `/api/instructor/questions/{id}/resolved` |
| 후기 | POST `/api/participant/experience` |
| 변환 | POST `/api/convert` → NDJSON `meta`, `slide`, `done` 또는 `error` |
| 정리 재시도 | POST `/api/instructor/storage-cleanup` |
| 헬스 | GET `/actuator/health`, `/actuator/health/liveness` |

## 검증

```bash
./gradlew test bootJar
npm run supabase:reset
npm run test:db
# API 실행 후: 키 파일은 gitignore에 포함된 로컬 임시 파일로만 보관
npm run supabase:status -- --output env > .env.integration.local
python3 scripts/integration-test.py --status-file .env.integration.local
```

통합 테스트는 localhost만 허용하며, 로컬 테스트 계정 생성·삭제에만 status의 서비스 키를 사용합니다.
실제 앱은 서비스 키를 사용하지 않습니다. 브라우저 테스트는 별도 문서 `docs/verification.md`를 참고하세요.
