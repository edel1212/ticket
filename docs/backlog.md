# 미개발 사항 백로그

> 기준 문서: `docs/api.md`, `docs/trd.md`  
> 작성일: 2026-04-21  
> 현재 브랜치: `feature/queue-idempotent-join`

---

## 현재 구현 vs PRD 비교 요약

| PRD 항목 | 현재 구현 상태 | 비고 |
|---|---|---|
| `POST /api/v1/queue/join` | ⚠️ 경로 불일치 | `/trains/{trainId}/queue`로 구현됨 |
| `GET /api/v1/queue/status` (SSE) | ❌ 미구현 | 적응형 폴링 + Active Pool 전환 |
| `DELETE /api/v1/queue/leave` | ❌ 미구현 | 대기열 이탈 |
| `GET /api/v1/trains/{trainId}/seats` | ⚠️ 부분 구현 | 응답 구조 누락 항목 있음 |
| `POST /api/v1/reservations` | ⚠️ 다른 방식으로 구현 | Kafka 비동기 미반영, PENDING 상태 없음 |
| Active Pool 스케줄러 | ❌ 미구현 | WAITING → ACTIVE 전환 핵심 로직 |
| Kafka 연동 | ❌ 미구현 | 예약 이벤트 비동기 발행 |
| ReservationFacade 패턴 | ❌ 미구현 | 트랜잭션 커밋 후 락 해제 보장 |
| Redis Hash 좌석 캐시 | ❌ 미구현 | Cache Aside 패턴 |

---

## 1. 대기열 API

### 1-1. ⚠️ 대기열 진입 경로 및 요청 구조 불일치

**PRD 정의**
```
POST /api/v1/queue/join
Body: { "trainId": 1 }
Header: X-Member-Id
```

**현재 구현**
```
POST /api/v1/trains/{trainId}/queue
Header: JWT Bearer Token (Authorization)
```

**해야 할 것**
- 경로를 `/api/v1/queue/join`으로 변경하거나, PRD 경로를 현재 구현에 맞게 업데이트
- 인증 방식 통일 결정 필요 (JWT Bearer vs `X-Member-Id` 헤더)

---

### 1-2. ❌ 대기 순번 실시간 조회 API (SSE)

**PRD 정의**
```
GET /api/v1/queue/status?trainId={trainId}
Header: X-Queue-Token
Accept: text/event-stream
```

**응답 (SSE 스트림)**
```
event: queue-status
data: {"status":"WAITING","rank":30,"message":"앞에 29명이 있습니다."}

event: queue-status
data: {"status":"ACTIVE","rank":0,"message":"입장 가능합니다."}
```

**해야 할 것**
- `GET /api/v1/queue/status` SSE 엔드포인트 구현
- Spring SseEmitter 기반 실시간 push
- 적응형 폴링 응답 포함: `pollingIntervalMs` (rank 기반 동적 조절)

  | rank 범위 | 폴링 간격 |
  |---|---|
  | 100 이상 | 10,000ms |
  | 10 ~ 99 | 5,000ms |
  | 3 ~ 9 | 2,000ms |
  | 1 ~ 2 | 1,000ms |
  | null | Active Pool 확인 후 ACTIVE/EXPIRED 반환 |

- `ZRANK null` 처리: Active Pool(`train:{trainId}:active_pool`) ZSCORE 확인 → 존재하면 ACTIVE 반환

---

### 1-3. ❌ 대기열 이탈 API

**PRD 정의 (TRD 기준)**
```
DELETE /api/v1/queue/leave
Header: X-Queue-Token, X-Member-Id
Body: { "trainId": 1 }
```

**해야 할 것**
- Redis `ZREM train:{trainId}:waiting_queue {memberEmail}` 실행
- 토큰 키(`queue:token:{memberEmail}:{trainId}`) 삭제

---

### 1-4. ❌ Active Pool 스케줄러

**TRD 정의**

WAITING 상태 사용자를 주기적으로 ACTIVE 상태로 전환하는 스케줄러.

**해야 할 것**
- `@Scheduled` 스케줄러 구현
- 실행 흐름:
  1. 분산락 획득: `lock:scheduler:queue-processor` (`tryLock(0, leaseTime)`)
  2. 만료 유저 정리: `ZREMRANGEBYSCORE train:{trainId}:active_pool 0 {currentTimeMillis}`
  3. 빈 슬롯 계산: `DB Connection Pool 크기 - ZCARD(active_pool)`
  4. 전환: `ZPOPMIN waiting_queue {빈슬롯수}` → `ZADD active_pool score={expireTs} {userId}`
- Active Pool 키: `train:{trainId}:active_pool` (ZSET, score = 만료 시각)
- Active 유효 시간: 10분

---

## 2. 좌석 API

### 2-1. ⚠️ 잔여 좌석 조회 응답 구조 누락

**PRD 정의 응답**
```json
{
  "trainId": 1,
  "trainSerial": "KTX-001",
  "departureTime": "2026-04-10T09:00:00",
  "totalSeats": 100,
  "availableCount": 57,
  "seats": [
    { "seatNumber": "A1", "status": "AVAILABLE" },
    { "seatNumber": "A2", "status": "RESERVED" }
  ]
}
```

**현재 구현 응답**
```json
[
  { "seatId": 1, "seatNumber": "1A", "status": "AVAILABLE" }
]
```

**해야 할 것**
- 응답에 `trainSerial`, `departureTime`, `totalSeats`, `availableCount` 추가
- Redis Hash(`train:{trainId}:seats`) 기반 Cache Aside 패턴 적용
  - Cache Hit: Redis Hash에서 O(1) 조회
  - Cache Miss: DB 조회 후 Redis Hash에 저장

---

## 3. 예약 API

### 3-1. ⚠️ 예약 생성 API — Kafka 비동기·PENDING 상태 미반영

**PRD 정의**
```
POST /api/v1/reservations
Header: X-Queue-Token (Active 상태), X-Member-Id
Body: { "trainId": 1, "seatNumber": "A1" }
응답: 202 Accepted, status: "PENDING"
```

**PRD 핵심 요구사항**
- 토큰이 **Active 상태**인지 검증 (Active Pool ZSCORE 확인)
- `tryLock(0, 300, SECONDS)` — 락 실패 시 즉시 409 반환 (DB 접근 없음)
- Kafka 토픽 `reservation-confirmed`에 예약 요청 메시지 발행 (비동기)
- 응답 상태: `PENDING` (즉시 DB 확정 아님)

**현재 구현과 차이**
- Active Pool 검증 없음 (queueToken 존재 여부만 확인)
- Kafka 발행 없음
- DB에 즉시 CONFIRMED 상태로 저장 (동기)
- 응답 상태 `PENDING` 없음

**해야 할 것**
- Active Pool 검증 추가: `ZSCORE train:{trainId}:active_pool {memberEmail}`
- Kafka 발행 로직 추가 (`reservation-confirmed` 토픽)
- 응답 HTTP 상태 202 Accepted로 변경

---

## 4. 인프라·아키텍처

### 4-1. ❌ Kafka 연동

**TRD 정의**

| 토픽 | 발행 시점 | 소비 처리 |
|---|---|---|
| `reservation-confirmed` | 락 해제 이후 (Facade) | 알림 발송 등 후속 처리 |

**해야 할 것**
- `build.gradle`에 Kafka 의존성 추가 (`spring-kafka`)
- Kafka Producer 설정 (`KafkaConfig`)
- `ReservationConfirmedEvent` DTO 설계
- ReservationFacade에서 트랜잭션 커밋 후 이벤트 발행
- Kafka Consumer 구현 (후속 처리)
- Docker Compose에 Kafka 서비스 추가 (KRaft 모드, `bitnami/kafka`)

---

### 4-2. ❌ ReservationFacade 패턴

**TRD 정의 레이어**
```
Controller
    └─ ReservationFacade    ← 락 획득/해제, @Transactional 없음
           └─ ReservationService  ← 비즈니스 로직, @Transactional
```

**현재 구현 문제**

현재 `ReservationServiceImpl`에서 직접 RLock을 획득·해제하므로 트랜잭션 커밋 전에 락이 해제될 수 있다.

**해야 할 것**
- `ReservationFacade` 클래스 추출 (`@Component`, `@Transactional` 없음)
- Facade: 락 획득 → `reservationService.confirm()` 호출 → 커밋 완료 후 락 해제
- `ReservationService.confirm()`: `@Transactional`, DB INSERT/UPDATE만 담당
- `ReservationController`가 Service 대신 Facade 호출하도록 변경

---

### 4-3. ❌ Redis Hash 좌석 캐시 (Cache Aside)

**TRD 정의**
- Key: `train:{trainId}:seats` (Redis Hash)
- Field: `{seatNumber}`, Value: `AVAILABLE` / `RESERVED`
- Cache Miss 시 DB 조회 후 Hash에 저장

**현재 구현**
- `seat:hold:{trainId}:{seatId}` RBucket으로 점유 상태 관리 (PRD 명세와 다른 방식)

**해야 할 것**
- 좌석 조회 시 Redis Hash 먼저 확인 (Cache Hit)
- Cache Miss 시 DB 조회 후 Hash에 전체 좌석 상태 저장
- 예약 확정 시 Hash 동기화 (`HSET train:{trainId}:seats {seatNumber} RESERVED`)

---

## 5. 우선순위 제안

| 순위 | 항목 | 이유 |
|---|---|---|
| 1 | Active Pool 스케줄러 (1-4) | WAITING→ACTIVE 전환 없으면 예약 불가 |
| 2 | SSE 순번 조회 API (1-2) | Active 전환 감지 핵심 |
| 3 | ReservationFacade 패턴 (4-2) | 현재 코드의 락·트랜잭션 정합성 버그 수정 |
| 4 | 예약 API 개선 (3-1) | Active 검증, PENDING 상태 |
| 5 | 대기열 이탈 API (1-3) | 사용자 이탈 처리 |
| 6 | Kafka 연동 (4-1) | 비동기 후속 처리 |
| 7 | 좌석 조회 응답 구조 개선 (2-1) | 응답 완성도 |
| 8 | Redis Hash 캐시 (4-3) | 성능 최적화 |
| 9 | 대기열 진입 경로 통일 (1-1) | 팀 내 경로 규칙 합의 후 결정 |
