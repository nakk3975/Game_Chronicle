# Supabase 수집기 전환

현재 단계: 함수와 예약 작업 배포 완료, Render는 전환 대응 버전 배포 예정. Steam API 키를 Supabase에 입력하고 실제 수집을 검증하기 전까지 engine=render 및 기존 수집기를 유지한다.

## 구성

- `game-chronicle-collector?task=poll`: 매분 최대 100명의 상태 관측. 기존 세션 JSON과 구간 계산을 유지한다.
- `?task=sync`: 매분 라이브러리 1명 처리. 기본 24시간마다 또는 사용자 요청으로 동기화한다. 게임별 왕복 대신 SQL 일괄 저장을 사용한다.
- `?task=health`: 인증된 설정·DB 연결 점검. API 키 값은 응답하지 않는다.
- `gc-maintenance`: 매시간 nonce/API 예산/만료 웹 세션/3일 지난 이 작업의 cron 실행 이력 정리.
- 웹 로그인/API는 Render에 유지된다. Render가 중단돼도 Supabase 예약 수집은 독립 실행된다. 웹 최초 접속의 기동 대기는 남는다.

## 보안과 일관성

호출은 Vault의 무작위 전용 Bearer 토큰으로 인증한다. 일반 사용자 JWT·공개 API 키로 수집기를 실행할 수 없다. `verify_jwt=false`는 이 전용 인증을 사용하기 위한 설정이다. HTTP 응답과 로그에는 Steam 키·사용자 게임 목록을 출력하지 않는다.

Edge의 기본 SUPABASE_DB_URL은 인증 시 Vault를 조회하는 데 사용한다. 업무 SQL은 항상 트랜잭션 시작 시 SET LOCAL ROLE app_runtime으로 권한을 줄이고 실행한다. chronicle 스키마는 Data API에 공개하지 않는다. 공개 RPC나 SECURITY DEFINER 함수를 추가하지 않는다.

작업별 120초 임대·토큰과 사용자별 fencing을 함께 검사한다. 저장 직전 사용자 행을 잠그고 동의·활성 상태를 다시 확인한다. Render의 늦은 응답은 engine=edge일 때 저장하지 않는다. 장애 구간을 플레이시간에 더하지 않는다. Steam 호출 예산은 트랜잭션 잠금으로 예약한다.

## 마지막 설정

1. Supabase Game_Chronicle → Edge Functions → Secrets에서 `STEAM_API_KEY`를 입력한다. Render의 기존 Steam 키와 같은 값이다. 저장소/채팅에 붙이지 않는다.
2. Vault 토큰으로 `?task=health`를 호출해 HTTP 200, `steamConfigured:true`를 확인한 뒤 `?task=probe`의 HTTP 200으로 Steam 키의 실제 유효성을 검증한다.
3. Render가 전환 대응 커밋으로 live인지 확인한다.
4. `supabase/collector-cutover.sql`로 소유권을 edge로 바꾸고 예약 작업 2개를 켠다.
5. 최소 2회의 예약 관측·last_heartbeat·lastSuccess·라이브러리 동기화를 확인한다.
6. Render의 `TRACKING_ENABLED=false`를 저장하고 재배포한다. `/me`의 수집기 상태는 DB heartbeat를 통해 확인한다.
7. Render 요청 없이 15분 넘게 지난 뒤에도 Supabase 관측 시각이 갱신되는지 확인한다. 이것은 별도 운영 검증이며 완료 전에는 검증됐다고 표기하지 않는다.

## 롤백

Render `TRACKING_ENABLED=true`로 재배포한 뒤 `supabase/collector-rollback.sql`을 실행한다. 기존 플레이 기록을 지우거나 다시 계산하지 않는다.

## 검증

- Node 엔진 테스트 11개: 정상 시작/종료, 공백, 게임 전환, 재시작, 오래된 관측, 후보 복귀, 기존 JSON 호환, 공개 상태 분류, 입력 불변.
- Java 테스트 33개: 기존 32개와 전환 후 Render 지연 쓰기 차단.
- Supabase 실제 인증된 health 호출 200 및 제한된 DB 역할 접근 확인.
- 미인증 호출 401, anon/authenticated의 제어 테이블 접근 차단.
- 실제 Steam 호출 및 예약 실행 전환은 키 설정 이후 검증한다.
