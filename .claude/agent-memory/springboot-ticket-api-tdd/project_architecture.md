---
name: 프로젝트 아키텍처 및 도메인 규칙
description: ticket-server Spring Boot 3.5.11 구조, 패키지 규칙, 공통 컴포넌트
type: project
---

## 기본 정보
- 패키지: `com.yoo.ticket`
- Spring Boot 3.5.11 / Java 17
- 운영 DB: MariaDB (localhost:3306/ticket), 테스트 DB: H2 인메모리

## 패키지 구조 원칙
- `domain/{도메인}/` — entity, repository, service, service/impl, controller, dto/request, dto/response, enums
- `global/` — exception, security, config, common/entity

## 공통 컴포넌트
- `BaseTimeEntity` — `@MappedSuperclass`, createdAt/modifiedAt 자동 관리. 모든 엔티티 상속 필수
- `GlobalExceptionHandler` — `@RestControllerAdvice`. 새 예외 추가 시 여기에 핸들러 + ErrorCode 추가
- `BusinessException` — RuntimeException 상속. `ErrorCode` 필드 포함. 도메인 예외의 기본 클래스
- `ErrorCode` — Common(C001-C006) + Member(M001-M006) 코드 보유
- `JpaConfig` — `@EnableJpaAuditing`. BaseTimeEntity 동작에 필수

## Security 구조
- `SecurityConfig` — SecurityFilterChain, PasswordEncoder 빈 정의
- `JwtTokenProvider` — 토큰 생성(generateAccessToken, generateRefreshToken), 검증(validateToken), 파싱(getEmail, getAuthentication)
- `JwtAuthenticationFilter` — OncePerRequestFilter, Authorization Bearer 헤더에서 토큰 추출 및 SecurityContext 설정
- `CustomUserDetailsService` — UserDetailsService 구현, 이메일로 회원 조회

## 열려 있는 엔드포인트 (permitAll)
- POST /api/v1/members/sign-up
- POST /api/v1/members/login
- POST /api/v1/members/reissue
- /actuator/**
