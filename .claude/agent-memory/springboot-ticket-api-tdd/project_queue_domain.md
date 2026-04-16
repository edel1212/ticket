---
name: Queue 도메인 구현 패턴
description: KTX 대기열 진입 기능 구현 — Redis ZSET, Redisson, TDD 패턴
type: project
---

## Redis / Redisson 설정

- 의존성: `org.redisson:redisson-spring-boot-starter:3.27.2`
- 설정 클래스: `global/config/RedissonConfig.java` (`@Configuration`, `SingleServerConfig`)
- `@Value("${spring.data.redis.host}")` / `@Value("${spring.data.redis.port}")` 바인딩
- `application-local.yml`에 `spring.data.redis.host/port/timeout` 추가
- 테스트 `application.yml`에도 `spring.data.redis.host/port` 추가 필요 (단일 `spring:` 블록 내에)

**Why:** `redisson-spring-boot-starter` 자동설정이 `@Value` 바인딩 실패 시 컨텍스트 로드 오류 발생

## Queue 도메인 Redis Key 설계

- 대기열 ZSET: `train:{trainId}:waiting_queue` — score = `System.currentTimeMillis()`
- 토큰 String: `queue:token:{memberEmail}:{trainId}` — TTL 1시간
- 멤버 식별자: email (SecurityContext subject = email)

## 비즈니스 로직

1. `RScoredSortedSet.getScore(memberEmail)` → null이면 신규, 값이면 기존
2. 신규: `UUID.randomUUID()` 토큰 → `add(score, memberEmail)` → `RBucket.set(token, 1, HOURS)`
3. 기존: `RBucket.get()` → 기존 토큰 반환
4. 공통: `rank(memberEmail)` → 0-based → +1 변환해 응답에 포함

## 컨트롤러 패턴

- `@AuthenticationPrincipal UserDetails` → `userDetails.getUsername()` = 이메일
- 200 OK 반환 (중복 등록 포함, 409 미사용)

## 테스트 패턴

- `QueueServiceTest`: `@ExtendWith(MockitoExtension.class)` + `@Mock RedissonClient`, `RScoredSortedSet`, `RBucket`
- `QueueControllerTest`: `@WebMvcTest(excludeAutoConfiguration = RedisAutoConfiguration.class)` 로 Redis 자동설정 제외
- `@SuppressWarnings("rawtypes")` 필요 — `RScoredSortedSet<String>` mock 시 raw type 경고 발생

## 테스트 YAML 주의사항

- 테스트용 `application.yml`에서 `spring:` 키는 반드시 하나만 선언
- `spring.data.redis`를 추가할 때 기존 `spring.datasource/jpa` 블록과 합쳐야 함
- 중복 `spring:` 키가 있으면 `DuplicateKeyException` 발생으로 컨텍스트 로드 실패

**How to apply:** 새 도메인에서 Redis 설정이 필요할 때 위 패턴을 재사용한다.
