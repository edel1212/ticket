---
name: Member 도메인 구현 패턴 및 테스트 설정
description: JWT 인증 구현, Member 엔티티 설계, WebMvcTest 설정 패턴, 발견된 문제와 해결책
type: project
---

## Member 엔티티 설계
- 필드: id(IDENTITY), email(unique), password(BCrypt), name, role(MemberRole enum, 기본 USER), refreshToken(nullable)
- 메서드: updateRefreshToken(String), updatePassword(String)
- 로그아웃 시 refreshToken을 null로 설정

## JWT 설정 (application.yml)
- `jwt.secret`: HS256용 256비트 이상 키
- `jwt.access-token-expiration`: 3600000ms (1시간)
- `jwt.refresh-token-expiration`: 604800000ms (7일)
- JwtTokenProvider 생성자에서 Base64 인코딩 후 HMAC 키 생성

## ErrorCode 구조 (Member)
- M001: DUPLICATE_EMAIL (409 CONFLICT)
- M002: MEMBER_NOT_FOUND (404 NOT_FOUND)
- M003: INVALID_PASSWORD (401 UNAUTHORIZED)
- M004: INVALID_TOKEN (401 UNAUTHORIZED)
- M005: EXPIRED_TOKEN (401 UNAUTHORIZED)
- M006: REFRESH_TOKEN_NOT_FOUND (401 UNAUTHORIZED)

## @WebMvcTest + Spring Security 패턴
- `SecurityConfig`를 직접 @Import하면 JwtAuthenticationEntryPoint, JwtAccessDeniedHandler 등 모든 의존성이 필요해서 컨텍스트 로딩 실패
- 해결책: `TestSecurityConfig` (@TestConfiguration)를 별도 생성하여 @Import
  - ObjectMapper 의존성 없이 JwtTokenProvider만 의존
  - exceptionHandling에 인라인 람다로 entryPoint(401), accessDeniedHandler(403) 설정
- JwtTokenProvider는 @MockBean으로 등록
- CustomUserDetailsService도 @MockBean으로 등록 (UserDetailsService 구현체로 필요)

## 인증 미제공 시 응답 코드
- Spring Security 기본: 익명 사용자로 처리 후 AccessDeniedException → 403
- AuthenticationEntryPoint 등록 필요: 인증 없는 요청 → 401

## REST Docs 스니펫 네이밍
- "member/sign-up" — 회원가입 성공
- "member/sign-up-duplicate" — 이메일 중복
- "member/login" — 로그인 성공
- "member/login-not-found" — 이메일 미존재
- "member/login-invalid-password" — 비밀번호 오류
- "member/logout" — 로그아웃
- "member/reissue" — 토큰 재발급
- "member/reissue-expired" — 만료된 토큰 재발급 시도
