# 검증 결과

기준일: 2026-09-13. 구현과 실계정 운영 검증을 구분합니다.

## 통과

- Java 17 / Gradle 8.14.3: 백엔드 컴파일, 단위·MVC 테스트 33개, bootJar 생성.
- React TypeScript 타입 검사와 Vite production build.
- 상태 전이 9개: 시작·종료 중간값, 첫 종료 후보 경계, 게임 전환, 3회 실패, 실패 후 같은 게임, 무게임 복귀 공백, 재시작, 오래된 응답 폐기, 추적 중지.
- 통계/CSV 4개: 서울 자정 분할, 조회 기간 자르기, DST 반복 시간, 수식·따옴표 처리.
- OpenID 필드 검증 5개: 정상 assertion, 잘못된 공급자, 누락된 signed 필드, identity 불일치, 오래된 nonce.
- 권한/경합·입력 검증 5개: 늦은 라이브러리 실패 무시, 잘못된 페이지 커서 400, 동의 철회 후 쓰기 차단, 임대 만료 후 쓰기 차단, 타 사용자 세션 404.
- DB 설정 6개: JDBC 유지, URI 자격 증명 분리·디코딩, 별도 환경변수 우선, 누락 진단, 오류 메시지 비밀정보 미포함, JDBC query 비밀번호 차단.
- 통합 JAR 실행: DB_URL 누락 시 안전한 설정 안내와 함께 종료하는 것 확인.
- MyBatis 매퍼 등록/SQL 매핑 구성 검사 1개.
- 실제 Spring Security MVC 테스트 2개: 비로그인 API 401, CSRF 없는 변경 요청 403.
- Supabase 실제 SQL 트랜잭션: 중복 활성 세션 차단, 음수 구간 차단, FK cascade 삭제. 테스트 트랜잭션은 rollback.
- Supabase 스키마: 13개 테이블 모두 RLS 활성, anon/authenticated 스키마 접근 권한 없음, runtime 역할 usage 있음.
- Supabase security advisor: 지적 사항 없음. performance advisor의 consent FK 인덱스 누락은 추가 인덱스로 해결.

## 미검증 / 출시 전 필요

- Render는 DB 설정 수정 후 정상 배포되었고 app_runtime JDBC 연결을 확인했습니다. 이전 DB_URL 누락/NOLOGIN 장애는 해결됐습니다.
- 배포 사이트의 샘플 화면과 라이브러리 레이아웃을 브라우저에서 확인했습니다. 실계정 OpenID 왕복과 공개 설정별 게임 전환 시나리오는 별도 시험 범위입니다.
- Supabase Edge Function의 실제 Steam API 조회와 라이브러리 동기화를 확인했습니다. 예약 전환의 상세 결과는 [수집기 검증](edge-collector.md)을 참고하세요.
- 부하 테스트, 백업/복원, 실계정 POC-01~08, 외부 Steam 장애 주입 시험은 미완료입니다.

## 구현 범위 주의

기획서 전체의 출시 완료를 의미하지 않습니다. P0의 주요 흐름을 구현한 초기 버전이며, 운영용 집계 outbox·삭제 영수증/복원 삭제 목록·세부 진단·최근 게임 보조 동기화는 후속 항목입니다. P1/P2는 미구현입니다.
