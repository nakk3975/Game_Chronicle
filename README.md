# Game Chronicle

**Steam에서 관측한 플레이를 나만의 타임라인으로 남기는 웹 서비스.**

Java 17 · Spring Boot 3.5 · Gradle 8.14 · MyBatis · React 19 + TypeScript · Supabase PostgreSQL · Render

## 구현된 기능

- Steam OpenID 로그인: 공급자·return_to·signed fields·nonce·서버 직접 검증, JDBC 웹 세션
- 기본 OFF인 자동 수집 동의, 일시중지, 정책 버전 이력
- 최대 100명 배치 관측, DB 임대와 fencing, 2회 종료 후보 확인, 장애 공백과 재시작 복구
- Steam 라이브러리와 누적 기준값 동기화, 누적시간 변화 스냅샷
- 대시보드, 날짜별 타임라인, 세션 구간 상세, 기록 제외/복원
- 라이브러리 검색·정렬·상세, 일별 잔디·시간대·요일 통계
- 이름·시간대 설정, CSV/JSON 전체 기록 내보내기, 최근 인증 확인 후 계정 삭제
- 가상 데이터임을 표시하는 샘플 화면, 다크/라이트 테마와 모바일 레이아웃
- Docker 단일 서비스 배포, GitHub Actions 빌드/테스트

**관측시간은 실제 실행시각을 보장하지 않는 추정값입니다.** 관측 공백을 합산하지 않으며, Steam 누적 플레이시간과 더하지 않습니다.

## 연결 프로젝트

- GitHub: [nakk3975/Game_Chronicle](https://github.com/nakk3975/Game_Chronicle)
- Supabase: `Game_Chronicle` / `mpfvubjdrerkqvgevgrw` / 서울 리전
- DB: `chronicle` 비공개 스키마, 초기 테이블 12개 생성 완료. 기존 프로젝트에 schema.sql을 다시 실행하지 마세요.
- Render: [game-chronicle](https://game-chronicle.onrender.com) / NAKK / Singapore / free / 자동 배포 OFF
- 서버 비밀정보는 저장소에 포함하지 않습니다. Steam 실계정 자동 수집·공개 배포는 아직 검증 전입니다.

## 새 PC에서 실행

JDK 17 이상을 설치하되 Gradle toolchain이 사용하는 JDK 17도 설치하세요. `java -version`뿐 아니라 `javac -version`도 확인합니다. Node.js 22, Git이 필요합니다.

```powershell
git clone https://github.com/nakk3975/Game_Chronicle.git
cd Game_Chronicle
npm ci --prefix frontend
npm run build --prefix frontend
New-Item -ItemType Directory -Force backend/src/main/resources/static
Copy-Item frontend/dist/* backend/src/main/resources/static/ -Recurse -Force
```

같은 PowerShell 터미널에서 환경변수를 설정합니다. `.env`를 자동 로드하지 않으므로 VS Code 실행 설정 또는 환경변수를 사용하세요.

```powershell
$env:DB_URL="jdbc:postgresql://<Supabase Connect의 Session pooler 호스트>:5432/postgres?sslmode=require"
$env:DB_USERNAME="app_runtime.mpfvubjdrerkqvgevgrw"
$env:DB_PASSWORD="<런타임 계정 비밀번호>"
$env:APP_BASE_URL="http://localhost:8080"
$env:COOKIE_SECURE="false"
$env:TRACKING_ENABLED="false"
.\gradlew.bat :backend:bootRun
```

[http://localhost:8080](http://localhost:8080)에서 시작합니다. 본인 PC에서 실제 관측 시험을 하려면 `STEAM_API_KEY`를 환경변수에 넣고 `TRACKING_ENABLED=true`로 바꾼 뒤 서버를 재시작하고 화면에서 동의합니다. **서버 실행 스위치와 사용자 동의가 모두 켜져야 수집합니다.**

프런트엔드 수정 시 `npm run dev --prefix frontend`를 사용하면 `/api`와 `/auth` 요청이 8080으로 프록시됩니다. Steam callback은 APP_BASE_URL로 돌아오므로 로그인/동의 검증은 8080의 통합 빌드로 진행하는 것이 가장 간단합니다.

## DB 비밀번호 설정

`app_runtime`은 비밀번호 없는 NOLOGIN 역할로 생성했습니다. Supabase SQL Editor에서 관리자로 다음을 실행하여 **새로 정한 강한 비밀번호**를 설정합니다. 실행한 비밀번호를 커밋하지 마세요.

```sql
ALTER ROLE app_runtime LOGIN PASSWORD '<새 런타임 전용 비밀번호>';
```

`DB_USERNAME`은 세션 풀러 사용 시 `app_runtime.<project-ref>`입니다. 호스트·포트는 Supabase **Connect → Session pooler**에 나온 값을 확인하세요. 실제 JDBC 접속은 배포/로컬 설정 후 검증해야 합니다. `postgres` 소유자 계정을 앱 실행에 쓰지 않습니다.

새 DB로 옮길 때만 `backend/src/main/resources/db/schema.sql`을 관리자 권한으로 실행합니다. 브라우저에 이 스키마를 노출하거나 anon/authenticated에 권한을 부여하지 않습니다.

## Render 배포 준비

저장소의 `Dockerfile`은 React 빌드를 Spring Boot에 포함하여 동일 출처로 제공합니다. `render.yaml`은 **무료 웹 확인용, 수집 OFF**로 준비되어 있으며 자동 배포도 OFF입니다.

1. Render에서 GitHub 저장소를 연결하고 Blueprint 또는 Docker Web Service를 생성합니다.
2. 서비스의 **Environment → Add Environment Variable**에서 DB_URL, DB_USERNAME, DB_PASSWORD, STEAM_API_KEY를 입력하고 **Save, rebuild, and deploy**합니다. DB_URL은 `jdbc:postgresql://HOST:5432/postgres?sslmode=require` 형식을 권장합니다. Supabase의 `postgresql://USER:PASSWORD@HOST:5432/postgres` URI도 지원하며, 별도 DB_USERNAME/DB_PASSWORD 값이 있으면 그 값을 우선합니다. URI의 특수문자는 URL 인코딩해야 합니다. Supabase 프로젝트 API URL(`https://...supabase.co`)은 DB_URL이 아닙니다.
3. APP_BASE_URL을 실제 `https://...onrender.com` 주소로 설정합니다. COOKIE_SECURE=true를 유지합니다.
4. `/actuator/health/readiness`가 정상인지 확인합니다.
5. 24시간 자동 기록이 필요하면 상시 실행 인스턴스를 선택한 다음 TRACKING_ENABLED=true로 바꿉니다. 무료 유휴중단을 인위적 호출로 우회하지 않습니다.

Docker 빌드 안에서 백엔드 테스트도 수행합니다. 배포 전에 Steam 공개/비공개·오프라인·게임전환 실제 시험을 완료하세요.

## 테스트와 문서

```sh
./gradlew :backend:test :backend:bootJar
npm ci --prefix frontend
npm run build --prefix frontend
```

- [구조와 기획서 대응](docs/architecture.md)
- [검증 결과 및 남은 출시 조건](docs/verification.md)

P1의 메모·Wrapped·공유, P2의 PC 에이전트는 포함하지 않았습니다. 초기 버전은 직접 구간 집계와 동기 삭제를 사용하며, 대규모 운영용 집계 outbox·삭제 영수증·백업 복원 삭제 목록은 후속 구현입니다.

## Supabase 예약 수집기

Render 유휴 중단과 게임 수집을 분리하는 Edge Function 및 Cron 구성을 추가했습니다. 키 설정과 검증이 끝날 때까지 기존 Render 수집을 유지합니다. [전환 절차와 검증 범위](docs/edge-collector.md)를 참고하세요.
