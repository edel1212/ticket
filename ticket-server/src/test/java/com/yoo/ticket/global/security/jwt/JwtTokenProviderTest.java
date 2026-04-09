package com.yoo.ticket.global.security.jwt;

import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.*;

/**
 * JwtTokenProvider 단위 테스트.
 * 토큰 생성, 파싱, 유효성 검증 로직을 검증합니다.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm-testing";
    private static final long ACCESS_TOKEN_EXPIRATION = 3600000L;   // 1시간
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7일

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_ROLE = "ROLE_USER";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
    }

    @Test
    @DisplayName("Access Token 생성 - 이메일과 역할로 Access Token을 생성할 수 있다")
    void generateAccessToken_success() {
        // when
        String accessToken = jwtTokenProvider.generateAccessToken(TEST_EMAIL, TEST_ROLE);

        // then
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @DisplayName("Refresh Token 생성 - 이메일로 Refresh Token을 생성할 수 있다")
    void generateRefreshToken_success() {
        // when
        String refreshToken = jwtTokenProvider.generateRefreshToken(TEST_EMAIL);

        // then
        assertThat(refreshToken).isNotBlank();
    }

    @Test
    @DisplayName("토큰 이메일 추출 - Access Token에서 이메일을 올바르게 추출한다")
    void getEmail_fromAccessToken_success() {
        // given
        String accessToken = jwtTokenProvider.generateAccessToken(TEST_EMAIL, TEST_ROLE);

        // when
        String extractedEmail = jwtTokenProvider.getEmail(accessToken);

        // then
        assertThat(extractedEmail).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("토큰 이메일 추출 - Refresh Token에서 이메일을 올바르게 추출한다")
    void getEmail_fromRefreshToken_success() {
        // given
        String refreshToken = jwtTokenProvider.generateRefreshToken(TEST_EMAIL);

        // when
        String extractedEmail = jwtTokenProvider.getEmail(refreshToken);

        // then
        assertThat(extractedEmail).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("Authentication 추출 - Access Token에서 Authentication 객체를 추출한다")
    void getAuthentication_success() {
        // given
        String accessToken = jwtTokenProvider.generateAccessToken(TEST_EMAIL, TEST_ROLE);

        // when
        Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);

        // then
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(TEST_EMAIL);
        assertThat(authentication.getAuthorities())
                .anyMatch(auth -> auth.getAuthority().equals(TEST_ROLE));
    }

    @Test
    @DisplayName("토큰 유효성 검증 - 유효한 토큰은 true를 반환한다")
    void validateToken_validToken_returnsTrue() {
        // given
        String accessToken = jwtTokenProvider.generateAccessToken(TEST_EMAIL, TEST_ROLE);

        // when
        boolean result = jwtTokenProvider.validateToken(accessToken);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("토큰 유효성 검증 - 만료된 토큰은 EXPIRED_TOKEN 예외를 발생시킨다")
    void validateToken_expiredToken_throwsExpiredTokenException() {
        // given - 만료 시간을 1ms로 설정하여 즉시 만료
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(TEST_SECRET, 1L, 1L);
        String expiredToken = shortLivedProvider.generateAccessToken(TEST_EMAIL, TEST_ROLE);

        // 토큰이 만료될 때까지 대기
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        // when & then
        assertThatThrownBy(() -> shortLivedProvider.validateToken(expiredToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("토큰 유효성 검증 - 위변조된 토큰은 INVALID_TOKEN 예외를 발생시킨다")
    void validateToken_tamperedToken_throwsInvalidTokenException() {
        // given
        String tamperedToken = "invalid.token.string";

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("Access Token 만료 시간 - 초 단위로 만료 시간을 반환한다")
    void getAccessTokenExpiresIn_success() {
        // when
        long expiresIn = jwtTokenProvider.getAccessTokenExpiresIn();

        // then
        assertThat(expiresIn).isEqualTo(ACCESS_TOKEN_EXPIRATION / 1000);
    }
}
