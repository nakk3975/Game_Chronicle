# 개발 환경과 배포 설정

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
$env:DB_USERNAME="app_runtime.<project-ref>"
$env:DB_PASSWORD="<런타임 계정 비밀번호>"
$env:APP_BASE_URL="http://localhost:8080"
$env:COOKIE_SECURE="false"
$env:TRACKING_ENABLED="false"
.\gradlew.bat :backend:bootRun
```

[http://localhost:8080](http://localhost:8080)에서 시작합니다. 별도의 개발 DB에서 실제 관측 시험을 하려면 `STEAM_API_KEY`를 환경변수에 넣고 `TRACKING_ENABLED=true`로 바꾼 뒤 서버를 재시작하고 화면에서 동의합니다. **Render 방식은 DB의 collector_control.engine=render, 서버 실행 스위치, 사용자 동의가 모두 켜져야 수집합니다. 운영 DB는 Edge 방식이므로 로컬 테스트를 위해 전환하지 마세요.**

프런트엔드 수정 시 `npm run dev --prefix frontend`를 사용하면 `/api`와 `/auth` 요청이 8080으로 프록시됩니다. Steam callback은 APP_BASE_URL로 돌아오므로 로그인/동의 검증은 8080의 통합 빌드로 진행하는 것이 가장 간단합니다.

## DB 비밀번호 설정

운영 `app_runtime`의 LOGIN과 비밀번호 설정은 완료했습니다. 새 DB에 설치하는 경우에만 역할을 설정하세요. Supabase SQL Editor에서 관리자로 다음을 실행하여 **새로 정한 강한 비밀번호**를 설정합니다. 실행한 비밀번호를 커밋하지 마세요.

```sql
ALTER ROLE app_runtime LOGIN PASSWORD '<새 런타임 전용 비밀번호>';
```

`DB_USERNAME`은 세션 풀러 사용 시 `app_runtime.<project-ref>`입니다. 호스트·포트는 Supabase **Connect → Session pooler**에 나온 값을 확인하세요. 새 환경의 JDBC 접속은 배포/로컬 설정 후 검증해야 합니다. `postgres` 소유자 계정을 앱 실행에 쓰지 않습니다.

새 DB로 옮길 때만 `backend/src/main/resources/db/schema.sql`을 관리자 권한으로 실행합니다. 브라우저에 이 스키마를 노출하거나 anon/authenticated에 권한을 부여하지 않습니다.

## Render 배포 준비

저장소의 `Dockerfile`은 React 빌드를 Spring Boot에 포함하여 동일 출처로 제공합니다. `render.yaml`은 **무료 웹 확인용, 수집 OFF**로 준비되어 있으며 자동 배포도 OFF입니다.

1. Render에서 GitHub 저장소를 연결하고 Blueprint 또는 Docker Web Service를 생성합니다.
2. 서비스의 **Environment → Add Environment Variable**에서 DB_URL, DB_USERNAME, DB_PASSWORD, STEAM_API_KEY를 입력하고 **Save, rebuild, and deploy**합니다. DB_URL은 `jdbc:postgresql://HOST:5432/postgres?sslmode=require` 형식을 권장합니다. Supabase의 `postgresql://USER:PASSWORD@HOST:5432/postgres` URI도 지원하며, 별도 DB_USERNAME/DB_PASSWORD 값이 있으면 그 값을 우선합니다. URI의 특수문자는 URL 인코딩해야 합니다. Supabase 프로젝트 API URL(`https://...supabase.co`)은 DB_URL이 아닙니다.
3. APP_BASE_URL을 실제 `https://...onrender.com` 주소로 설정합니다. COOKIE_SECURE=true를 유지합니다.
4. `/actuator/health/readiness`가 정상인지 확인합니다.
5. 운영 환경은 Supabase Cron과 Edge Function에서 수집합니다. 아래 전환 문서를 참고하고 Render의 TRACKING_ENABLED=false를 유지합니다.

Docker 빌드 안에서 백엔드 테스트도 수행합니다. 배포 전에 Steam 공개/비공개·오프라인·게임전환 실제 시험을 완료하세요.

