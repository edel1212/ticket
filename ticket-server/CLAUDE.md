# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# 빌드
./gradlew build

# 실행 (기본 포트 8080, PORT 환경변수로 변경 가능)
./gradlew bootRun

# 전체 테스트
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.yoo.ticket.SomeTestClassName"

# API 문서 생성 (REST Docs 기반)
./gradlew asciidoctor

# 클린 빌드
./gradlew clean build
```

## Environment

`local` 프로파일이 기본 활성화되며, 아래 조건이 필요하다:
- MariaDB: `localhost:3306/ticket`
- 인증: `DB_USERNAME` / `DB_PASSWORD` 환경변수 (기본값: `root` / `123`)

테스트는 H2 인메모리 DB를 사용하므로 외부 DB 불필요.

## Architecture

Spring Boot 3.5.11 / Java 17 기반 기차 티켓 예약 REST API 서버.

**패키지 구조:** `com.yoo.ticket`
- `domain/` — 비즈니스 도메인별 패키지: `train`, `reservation`, `seat`, `waitlist`. 각 도메인이 entity, repository, service, controller를 독립적으로 소유.
- `global/` — 공통 관심사: 예외 처리, 공유 설정, 기본 엔티티.

**핵심 패턴:**
- **`BaseTimeEntity`** (`global/common/entity/`) — `@MappedSuperclass`. `createdAt` / `modifiedAt` 자동 관리. 모든 엔티티는 이를 상속.
- **`GlobalExceptionHandler`** (`global/exception/`) — `@RestControllerAdvice`. `ErrorCode` enum + `ErrorResponse` DTO로 표준화된 에러 응답 반환. 새 예외 타입 추가 시 여기에 핸들러 메서드와 `ErrorCode` 값을 추가.
- **`JpaConfig`** — `@EnableJpaAuditing` 활성화. `BaseTimeEntity` 타임스탬프 동작에 필수.
- **Snowflake ID** — `application.yml`에 설정 (datacenter-id=1, worker-id=1). 분산 ID 생성에 사용.
- **Spring REST Docs** — API 문서는 테스트 코드에서 자동 생성. 직접 작성 금지. `./gradlew asciidoctor`로 생성.
- **HATEOAS** — 응답에 하이퍼미디어 링크 포함 가능.

**데이터베이스:** 운영 MariaDB (ddl-auto=update), 테스트 H2. 엔티티 컬럼 설명은 `@Comment` 어노테이션 사용.

**모니터링:** Actuator로 `health`, `info`, `metrics`, `prometheus` 엔드포인트 노출. 로그는 콘솔 및 `logs/app.log` (롤링).

## 의존성 정책

유틸리티 클래스를 직접 구현하기 전에, 대중적으로 사용되거나 검증된 라이브러리가 있으면 `build.gradle`에 추가하는 방식을 우선한다.

- 직접 구현 대신 라이브러리를 먼저 검토한다.
- 라이브러리 선택 기준: 다운로드 수 / GitHub Stars / 마지막 릴리스 날짜 / Spring Boot 호환성.
- 라이브러리를 추가할 때는 사용 목적을 주석으로 함께 기록한다.

**적용 예시:**
- UUID v7 생성 → `com.fasterxml.uuid:java-uuid-generator` (직접 비트 조작 구현 금지)
