---
name: Seat/Reservation 도메인 구현 패턴
description: 분산락 기반 좌석 점유/예매 도메인의 Redis Key 설계, JPA 쿼리 패턴, 테스트 주의사항
type: project
---

## Redis Key 설계

```
queue:token:{memberEmail}:{trainId}   — 대기열 토큰 (QueueServiceImpl 동일)
seat:hold:{trainId}:{seatId}          — 좌석 임시 점유 (5분 TTL, value=memberEmail)
seat:lock:{trainId}:{seatId}          — Redisson RLock (tryLock 3초 대기, 5초 점유)
```

## Train 엔티티 PK 필드명 주의사항

Train의 PK는 `trainId` (일반적인 `id`가 아님). Spring Data JPA 파생 쿼리에서 `findByTrainId`로 작성하면 `Seat.train.id`를 찾으려 해 `PropertyReferenceException` 발생.

**해결책:**
- `findByTrainTrainId(Long trainId)` — Spring Data JPA 경로 탐색
- `@Query("... WHERE s.train.trainId = :trainId")` — JPQL 명시

## SeatRepository 쿼리 패턴

```java
List<Seat> findByTrainTrainId(Long trainId);

@Query("SELECT s FROM Seat s WHERE s.id = :id AND s.train.trainId = :trainId")
Optional<Seat> findByIdAndTrainId(@Param("id") Long id, @Param("trainId") Long trainId);
```

## Mockito strict stubbing 주의사항

- `@ExtendWith(MockitoExtension.class)` 기본이 STRICT_STUBS
- setUp에서 `lenient().when(...)` 처리해도 특정 테스트에서 해당 stub을 사용하지 않으면 UnnecessaryStubbingException 발생 가능
- 해결책: setUp에서는 진짜 공통 stub만 lenient 처리, 테스트별로 필요한 것은 helper 메서드(`stubTokenAndHoldBuckets()`)로 분리
- `getSeatsByTrain`처럼 hold 존재 분기가 있으면 OCCUPIED 분기에서 `existsBySeatId` 호출 안 됨 → 해당 stub은 `lenient().when()`으로 처리

## data.sql 테스트 격리

운영용 `data.sql`이 H2 테스트 환경에서 실행되면 MariaDB 전용 SQL(`INSERT IGNORE`)이 실패함.

**해결책:** `src/test/resources/application.yml`에 추가:
```yaml
spring:
  sql:
    init:
      mode: never
```

## Reservation 엔티티 특이사항

- PK: `@UuidV7` + `@Column(length = 36)` → String 타입 UUID v7
- `@Builder` + `@AllArgsConstructor(access = PRIVATE)` 패턴
- 빌더로 생성 시 id를 명시하지 않으면 Hibernate가 UuidV7Generator로 자동 생성
- 테스트에서 mock save 반환값: `Reservation.builder().id("test-reservation-id").member(...).seat(...).train(...).build()`

## 흐름 정리

```
대기열 진입(QueueService) → 좌석 임시 점유(SeatService.occupySeat) → 예매 확정(ReservationService.createReservation)
```

**Why:** 동시 좌석 선택 경쟁 조건을 Redisson 분산락으로 해결. hold TTL(5분)로 미확정 점유 자동 해제.

**How to apply:** 새 도메인에서 동시성 제어가 필요하면 동일한 Lock Key 패턴(`{domain}:lock:{id1}:{id2}`)을 사용.
