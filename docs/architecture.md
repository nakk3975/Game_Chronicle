# 구현 구조와 기획서 대응

React + TypeScript → 동일 출처 Spring Boot API → MyBatis → Supabase PostgreSQL `chronicle` 스키마.
Steam OpenID는 별도 검증 어댑터를 사용하며 Supabase Auth에 의존하지 않는다.
Supabase Cron이 매분 Edge Function을 호출해 Steam을 조회하고 같은 DB에 관측을 저장한다. collector_control의 engine과 heartbeat로 수집 소유권·상태를 공유한다.
웹 로그인 세션은 Spring Session JDBC, 플레이 기록은 play_session, 수집 동의는 app_user와 consent_event로 분리한다.

## 데이터와 권한
- JDBC 서버 전용 app_runtime 역할. 비밀번호 없는 NOLOGIN 상태로 생성하며 배포 때 관리자가 LOGIN 비밀번호를 설정한다.
- 13개 테이블에 RLS, backend_only 정책. anon/authenticated/PUBLIC에는 스키마·테이블 접근 권한 없음.
- 모든 개인 조회에 인증 세션 user_id 조건. API의 userId 입력으로 소유권을 선택할 수 없음.
- 수집기는 DB 임대 + fencing_token을 검사하고 짧은 트랜잭션 내 사용자 동의를 재확인한다.
- 부분 유일 인덱스로 사용자당 활성 세션 1개, 구간 음수 금지, FK cascade 삭제를 보장한다.
- 거래 데이터 공개 금지. 원문 Steam 응답·API 키·callback 쿼리 로그를 저장하지 않는다.

## 기록 정책
- 기본 60초, 최대 100명 배치. 2회 연속 무게임 후보이면 첫 후보 경계로 종료.
- 3회 실패 / 관측 간격 180초 초과 → 마지막 유효 시각으로 중단.
- 실패 이후 같은 게임이어도 새 유효 구간 생성. 공백은 합계 제외.
- 시작/종료 경계는 직전 정상 관측과 새 관측 사이 중간값. 경계 불명확 시 PARTIAL.
- 동일 시각/과거 응답은 폐기. 추적 중지는 쓰기 직전에 재검사.
- 누적게임시간 기준값·최신값·변화 스냅샷을 관측시간과 독립 보관.
- 24시간 라이브러리 동기화. 실패 시 기존 게임을 삭제하거나 0으로 덮어쓰지 않는다.
- API 예산은 최근 25시간 시간 버킷 합계로 보수적 관리. 90,000회에서 수집 요청 거부.

## 초기 구현에서 단순화한 항목
- 일별 집계/outbox 대신 원본 유효 구간을 요청 시 분할 집계한다. 조회 한도는 366일이다.
- 삭제는 동일 사용자 잠금을 잡고 즉시 cascade로 완료한다. 비동기 삭제 영수증·백업 tombstone은 후속 작업이다.
- 게임 메타와 개인 라이브러리를 user_game에 합쳤고, 추적 상태의 세부 경계는 JSONB에 보존한다.
- 메모·Wrapped·공유·PC 에이전트는 기획의 P1/P2 단계로 구현하지 않았다.
- 최근 게임 6시간 보조 동기화, 운영 UI/경고, 진단 세분화·별도 60초 제한, 집계 캐시, 게임별 커서 API는 후속 작업이다.
- 현재 진단 요청은 라이브러리 동기화와 같은 큐/10분 제한을 사용한다. UI는 이를 게임 목록 동기화로만 표현한다.

## 출시 전 실계정 검증
G0는 서버 API 키·실제 Steam 계정 없이 완료 처리하지 않는다. 기획서 POC-01~08의 공개/비공개/오프라인/게임전환 실험을 해야 한다.
현재 gameid 부재는 종료 후보일 뿐 실제 종료의 증거가 아니다. 공개 프로필 여부만으로 게임 정보 공개 여부를 확정하지 않는다.
운영 수집은 Supabase Cron·Edge Function에서 실행한다. Render는 TRACKING_ENABLED=false이며 웹 로그인·조회만 담당한다. Render 재배포 후 독립 관측은 확인했고, 실제 장시간 유휴 중단 시험은 별도 남아 있다. 자세한 전환·롤백 절차는 [수집기 문서](edge-collector.md)를 참고한다.

## 근거 문서
- https://partner.steamgames.com/doc/features/auth
- https://partner.steamgames.com/doc/webapi/ISteamUser
- https://supabase.com/docs/guides/database/connecting-to-postgres
- https://supabase.com/docs/guides/api/using-custom-schemas
- https://render.com/docs/blueprint-spec
- https://docs.spring.io/spring-boot/3.5/gradle-plugin/index.html
