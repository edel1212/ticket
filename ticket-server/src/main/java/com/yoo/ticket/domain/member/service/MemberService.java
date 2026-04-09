package com.yoo.ticket.domain.member.service;

import com.yoo.ticket.domain.member.dto.request.LoginRequest;
import com.yoo.ticket.domain.member.dto.request.SignUpRequest;
import com.yoo.ticket.domain.member.dto.request.TokenReissueRequest;
import com.yoo.ticket.domain.member.dto.response.MemberResponse;
import com.yoo.ticket.domain.member.dto.response.TokenResponse;
import com.yoo.ticket.global.exception.BusinessException;

/**
 * 회원 서비스 인터페이스.
 * 회원가입, 로그인, 로그아웃, 토큰 재발급 관련 비즈니스 로직을 정의합니다.
 */
public interface MemberService {

    /**
     * 신규 회원을 등록합니다.
     *
     * <p>회원가입 요청 시 다음 검증을 수행합니다:
     * <ul>
     *   <li>이메일 중복 여부 확인</li>
     *   <li>비밀번호 BCrypt 암호화</li>
     * </ul>
     *
     * @param request 회원가입 요청 정보 (이메일, 비밀번호, 이름)
     * @return 생성된 회원 정보 ({@link MemberResponse})
     * @throws BusinessException DUPLICATE_EMAIL - 이미 등록된 이메일인 경우
     */
    MemberResponse signUp(SignUpRequest request);

    /**
     * 이메일과 비밀번호로 로그인하여 JWT 토큰을 발급합니다.
     *
     * <p>로그인 성공 시:
     * <ul>
     *   <li>Access Token (1시간 유효) 발급</li>
     *   <li>Refresh Token (7일 유효) 발급 및 DB 저장</li>
     * </ul>
     *
     * @param request 로그인 요청 정보 (이메일, 비밀번호)
     * @return JWT 토큰 응답 ({@link TokenResponse}) - accessToken, refreshToken, tokenType, expiresIn 포함
     * @throws BusinessException MEMBER_NOT_FOUND - 등록되지 않은 이메일인 경우
     * @throws BusinessException INVALID_PASSWORD - 비밀번호가 일치하지 않는 경우
     */
    TokenResponse login(LoginRequest request);

    /**
     * 로그아웃 처리를 수행합니다.
     * DB에 저장된 Refresh Token을 삭제합니다.
     *
     * @param email 로그아웃할 회원의 이메일
     * @throws BusinessException MEMBER_NOT_FOUND - 등록되지 않은 이메일인 경우
     */
    void logout(String email);

    /**
     * Refresh Token을 사용하여 새로운 Access Token과 Refresh Token을 발급합니다.
     *
     * <p>토큰 재발급 시 다음 검증을 수행합니다:
     * <ul>
     *   <li>Refresh Token 유효성 검증 (서명, 만료)</li>
     *   <li>DB에 저장된 Refresh Token과 일치 여부 확인</li>
     * </ul>
     *
     * @param request 토큰 재발급 요청 정보 ({@link TokenReissueRequest})
     * @return 새로 발급된 JWT 토큰 응답 ({@link TokenResponse})
     * @throws BusinessException INVALID_TOKEN - Refresh Token이 유효하지 않은 경우
     * @throws BusinessException EXPIRED_TOKEN - Refresh Token이 만료된 경우
     * @throws BusinessException REFRESH_TOKEN_NOT_FOUND - DB에 Refresh Token이 없는 경우 (로그아웃 상태)
     * @throws BusinessException MEMBER_NOT_FOUND - 해당 이메일의 회원이 없는 경우
     */
    TokenResponse reissue(TokenReissueRequest request);
}
