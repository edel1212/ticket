# API 명세

## 1. 공통 규칙

### Base URL
```
http://localhost:8080/api/v1
```

### 응답 형식
- Content-Type: `application/json`
- 문자 인코딩: UTF-8

### 인증 방식
- 대기열 진입 후 발급된 **Queue Token**을 HTTP Header에 포함
- 예약 API 호출 시 Active 상태의 토큰이 필수

| Header | 값 예시 | 설명 |
|---|---|---|
| `X-Queue-Token` | `550e8400-e29b-41d4-a716-446655440000` | 대기열 진입 시 발급된 UUID 토큰 |
| `X-Member-Id` | `1001` | 요청 회원 ID (인증 서버 미구현 시 헤더로 전달) |

> 현재 버전에서는 별도 JWT 인증 없이 `X-Member-Id` 헤더로 회원을 식별합니다.

---

### 에러 응답 포맷

모든 에러는 아래 형식으로 반환됩니다.

```json
{
  "code": "SEAT_ALREADY_RESERVED",
  "message": "이미 선점된 좌석입니다.",
  "timestamp": "2026-04-08T10:30:00"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| code | String | 에러 식별 코드 |
| message | String | 사람이 읽을 수 있는 에러 메시지 |
| timestamp | String | 에러 발생 시각 (ISO 8601) |

---

### 공통 에러 코드

| HTTP 상태 | code | 설명 |
|---|---|---|
| 400 | INVALID_REQUEST | 요청 파라미터 유효성 오류 |
| 401 | UNAUTHORIZED | 토큰 없음 또는 비활성 토큰 |
| 404 | RESOURCE_NOT_FOUND | 요청한 리소스 없음 |
| 409 | CONFLICT | 중복 요청 또는 이미 처리된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 서버 내부 오류 |

---

## 2. API 목록

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/queue/join` | 대기열 진입 및 토큰 발급 |
| GET | `/queue/status` | 내 대기 순번 실시간 조회 (SSE) |
| GET | `/trains/{trainId}/seats` | 잔여 좌석 조회 (Redis 캐시) |
| POST | `/reservations` | 좌석 선점 요청 (Kafka 비동기) |
| POST | `/reservations/{reservationId}/payments` | 더미 결제 확정 |

---

## 3. 엔드포인트 상세

---

### 3.1 대기열 진입

```
POST /api/v1/queue/join
```

#### 설명
- 사용자를 Redis ZSET(`train:{trainId}:waiting_queue`)에 등록합니다.
- Score는 `System.currentTimeMillis()`로 설정되어 선착순이 보장됩니다.
- 이미 대기열에 등록된 사용자가 재요청하면 기존 토큰을 그대로 반환합니다.
- 스케줄러가 주기적으로 상위 N명을 Active 상태로 전환합니다.

#### Request

**Headers**

| Header | 필수 | 설명 |
|---|---|---|
| `X-Member-Id` | Y | 회원 ID |
| `Content-Type` | Y | `application/json` |

**Body**

```json
{
  "trainId": 1
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| trainId | Long | Y | 대기열에 진입할 열차 ID |

#### Response

**200 OK** (이미 대기열에 있는 경우 재발급 없이 기존 토큰 반환)

```json
{
  "queueToken": "550e8400-e29b-41d4-a716-446655440000",
  "trainId": 1,
  "status": "WAITING",
  "rank": 42,
  "message": "대기열에 등록되었습니다. 현재 41명이 앞에 있습니다."
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| queueToken | String | 대기열 UUID 토큰 (이후 API 호출 시 사용) |
| trainId | Long | 열차 ID |
| status | String | `WAITING` / `ACTIVE` |
| rank | Integer | 현재 대기 순번 (1-based, 내 앞에 rank-1명) |
| message | String | 안내 메시지 |

#### 에러 케이스

| HTTP | code | 설명 |
|---|---|---|
| 400 | INVALID_REQUEST | trainId 누락 또는 유효하지 않은 값 |
| 404 | TRAIN_NOT_FOUND | 존재하지 않는 열차 ID |

---

### 3.2 대기 순번 실시간 조회 (SSE)

```
GET /api/v1/queue/status
```

#### 설명
- **Server-Sent Events(SSE)** 방식으로 연결을 유지하며 실시간으로 순번을 push합니다.
- 서버는 주기적으로 Redis ZRANK를 조회하여 현재 순위를 클라이언트에 전송합니다.
- status가 `ACTIVE`로 전환되면 마지막 이벤트를 전송 후 SSE 연결을 종료합니다.
- 클라이언트는 `ACTIVE` 수신 시 예약 API(`POST /reservations`)를 호출할 수 있습니다.

#### Request

**Headers**

| Header | 필수 | 설명 |
|---|---|---|
| `X-Queue-Token` | Y | 대기열 진입 시 발급받은 토큰 |
| `Accept` | Y | `text/event-stream` |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| trainId | Long | Y | 조회할 열차 ID |

#### Response

**200 OK** — `Content-Type: text/event-stream`

SSE 이벤트 스트림 형식으로 응답합니다.

```
event: queue-status
data: {"status":"WAITING","rank":30,"message":"앞에 29명이 있습니다."}

event: queue-status
data: {"status":"WAITING","rank":10,"message":"앞에 9명이 있습니다."}

event: queue-status
data: {"status":"ACTIVE","rank":0,"message":"입장 가능합니다. 지금 바로 예약하세요!"}
```

| SSE 필드 | 타입 | 설명 |
|---|---|---|
| status | String | `WAITING` / `ACTIVE` |
| rank | Integer | 현재 대기 순번 (0이면 Active 전환) |
| message | String | 안내 메시지 |

#### 에러 케이스

| HTTP | code | 설명 |
|---|---|---|
| 401 | UNAUTHORIZED | 유효하지 않은 토큰 또는 토큰 없음 |
| 404 | QUEUE_NOT_FOUND | 대기열에 존재하지 않는 토큰 |

---

### 3.3 잔여 좌석 조회

```
GET /api/v1/trains/{trainId}/seats
```

#### 설명
- Redis Hash(`train:{trainId}:seats`)에서 좌석 상태를 O(1)로 조회합니다.
- 캐시가 없는 경우(Cache Miss) DB에서 조회 후 Redis에 저장합니다 (Cache Aside 패턴).
- AVAILABLE 상태의 좌석만 예약 가능합니다.

#### Request

**Path Variables**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| trainId | Long | Y | 열차 ID |

**Headers**

| Header | 필수 | 설명 |
|---|---|---|
| `X-Queue-Token` | Y | 대기열 진입 시 발급받은 토큰 |

#### Response

**200 OK**

```json
{
  "trainId": 1,
  "trainSerial": "KTX-001",
  "departureTime": "2026-04-10T09:00:00",
  "totalSeats": 100,
  "availableCount": 57,
  "seats": [
    { "seatNumber": "A1", "status": "AVAILABLE" },
    { "seatNumber": "A2", "status": "RESERVED" },
    { "seatNumber": "A3", "status": "AVAILABLE" }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| trainId | Long | 열차 ID |
| trainSerial | String | 열차 번호 |
| departureTime | String | 출발 시간 (ISO 8601) |
| totalSeats | Integer | 총 좌석 수 |
| availableCount | Integer | 예약 가능 잔여 좌석 수 |
| seats | Array | 좌석 목록 |
| seats[].seatNumber | String | 좌석 번호 |
| seats[].status | String | `AVAILABLE` / `RESERVED` |

#### 에러 케이스

| HTTP | code | 설명 |
|---|---|---|
| 401 | UNAUTHORIZED | 유효하지 않은 토큰 |
| 404 | TRAIN_NOT_FOUND | 존재하지 않는 열차 ID |

---

### 3.4 좌석 선점 요청 (예약 생성)

```
POST /api/v1/reservations
```

#### 설명
1. `X-Queue-Token` 헤더의 토큰이 **Active 상태**인지 Redis에서 검증합니다.
2. Redisson 분산 락(`lock:seat:{trainId}:{seatNumber}`)을 **tryLock(0초 대기)** 으로 획득 시도합니다.
   - 락 획득 실패 → 즉시 **409 Conflict** 반환 (DB I/O 없음)
3. 락 획득 성공 시 Kafka 토픽(`reservation.request`)에 예약 요청 메시지를 발행합니다.
4. Kafka Consumer가 메시지를 소비하여 DB에 `PENDING` 상태로 INSERT합니다.
5. 클라이언트는 **202 Accepted** 응답을 받고, 이후 결제 API로 확정합니다.

> **주의:** 이 API는 예약을 즉시 확정하지 않습니다. Kafka 비동기 처리로 DB INSERT가 지연될 수 있습니다.

#### Request

**Headers**

| Header | 필수 | 설명 |
|---|---|---|
| `X-Queue-Token` | Y | Active 상태의 대기열 토큰 |
| `X-Member-Id` | Y | 회원 ID |
| `Content-Type` | Y | `application/json` |

**Body**

```json
{
  "trainId": 1,
  "seatNumber": "A1"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| trainId | Long | Y | 예약할 열차 ID |
| seatNumber | String | Y | 예약할 좌석 번호 (예: A1, B12) |

#### Response

**202 Accepted**

```json
{
  "reservationId": 1001,
  "trainId": 1,
  "seatNumber": "A1",
  "status": "PENDING",
  "message": "예약 요청이 접수되었습니다. 5분 이내에 결제를 완료해주세요.",
  "expireAt": "2026-04-08T10:35:00"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| reservationId | Long | 예약 ID (결제 API에 사용) |
| trainId | Long | 열차 ID |
| seatNumber | String | 좌석 번호 |
| status | String | `PENDING` (결제 대기 중) |
| message | String | 안내 메시지 |
| expireAt | String | 결제 만료 시각 (요청 시각 + 5분, ISO 8601) |

#### 에러 케이스

| HTTP | code | 설명 |
|---|---|---|
| 401 | UNAUTHORIZED | 토큰 없음 또는 WAITING 상태 토큰 (Active 아님) |
| 404 | TRAIN_NOT_FOUND | 존재하지 않는 열차 ID |
| 404 | SEAT_NOT_FOUND | 존재하지 않는 좌석 번호 |
| 409 | SEAT_ALREADY_RESERVED | 이미 선점된 좌석 (분산 락 획득 실패) |
| 400 | INVALID_REQUEST | 요청 파라미터 유효성 오류 |

---

### 3.5 결제 확정 (더미 결제)

```
POST /api/v1/reservations/{reservationId}/payments
```

#### 설명
1. `reservationId`에 해당하는 예약이 **PENDING** 상태인지 확인합니다.
2. 예약의 `member_id`와 `X-Member-Id` 헤더가 일치하는지 검증합니다.
3. 결제 만료 시각(`expireAt`) 이전인지 확인합니다.
4. DB에서 Reservation 상태를 `COMPLETED`로 업데이트합니다.
5. Seat 테이블의 해당 좌석 status를 `RESERVED`로 업데이트합니다.
6. Redis 좌석 캐시(`train:{trainId}:seats`)에서 해당 좌석을 `RESERVED`로 갱신합니다.
7. Redis 분산 락을 해제합니다.

> 실제 PG 연동 없이 요청 수신 즉시 결제 성공으로 처리하는 더미 결제입니다.

#### Request

**Path Variables**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| reservationId | Long | Y | 결제할 예약 ID |

**Headers**

| Header | 필수 | 설명 |
|---|---|---|
| `X-Queue-Token` | Y | Active 상태의 대기열 토큰 |
| `X-Member-Id` | Y | 회원 ID |

#### Response

**200 OK**

```json
{
  "reservationId": 1001,
  "trainId": 1,
  "trainSerial": "KTX-001",
  "seatNumber": "A1",
  "memberId": 42,
  "status": "COMPLETED",
  "paidAt": "2026-04-08T10:32:15",
  "message": "예약이 확정되었습니다."
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| reservationId | Long | 예약 ID |
| trainId | Long | 열차 ID |
| trainSerial | String | 열차 번호 |
| seatNumber | String | 좌석 번호 |
| memberId | Long | 예약자 회원 ID |
| status | String | `COMPLETED` |
| paidAt | String | 결제 완료 시각 (ISO 8601) |
| message | String | 확정 안내 메시지 |

#### 에러 케이스

| HTTP | code | 설명 |
|---|---|---|
| 401 | UNAUTHORIZED | 토큰 없음, 또는 예약자와 요청자 불일치 |
| 404 | RESERVATION_NOT_FOUND | 존재하지 않는 예약 ID |
| 409 | RESERVATION_ALREADY_COMPLETED | 이미 완료된 예약 |
| 409 | RESERVATION_EXPIRED | 결제 가능 시간(5분) 초과 → CANCELLED 처리됨 |
| 409 | RESERVATION_CANCELLED | 이미 취소된 예약 |

---

## 4. 흐름 요약

```
[대기열 진입]     POST /queue/join          → queueToken 발급 (status: WAITING)
      ↓
[순번 조회]       GET  /queue/status (SSE)  → status: ACTIVE 수신 시 다음 단계
      ↓
[좌석 조회]       GET  /trains/{id}/seats   → 예약 가능 좌석 확인
      ↓
[좌석 선점]       POST /reservations        → 202 Accepted, reservationId 발급 (PENDING)
      ↓
[결제 확정]       POST /reservations/{id}/payments → 200 OK (COMPLETED)
```

> 결제 미완료 시 5분 후 자동 CANCELLED 처리 및 분산 락 해제
