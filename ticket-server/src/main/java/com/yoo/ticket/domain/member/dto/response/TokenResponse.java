package com.yoo.ticket.domain.member.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 토큰 응답 DTO.
 * 로그인 및 토큰 재발급 시 반환됩니다.
 */
@Getter
@Builder
public class TokenResponse {

    /** Access Token */
    private String accessToken;

    /** Refresh Token */
    private String refreshToken;

    /** 토큰 타입 (항상 "Bearer") */
    @Builder.Default
    private String tokenType = "Bearer";

    /** Access Token 만료 시간 (초 단위) */
    private long expiresIn;
}
