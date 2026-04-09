package com.yoo.ticket.domain.member.service.impl;

import com.yoo.ticket.domain.member.dto.request.LoginRequest;
import com.yoo.ticket.domain.member.dto.request.SignUpRequest;
import com.yoo.ticket.domain.member.dto.request.TokenReissueRequest;
import com.yoo.ticket.domain.member.dto.response.MemberResponse;
import com.yoo.ticket.domain.member.dto.response.TokenResponse;
import com.yoo.ticket.domain.member.entity.Member;
import com.yoo.ticket.domain.member.repository.MemberRepository;
import com.yoo.ticket.domain.member.service.MemberService;
import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
import com.yoo.ticket.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 서비스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public MemberResponse signUp(SignUpRequest request) {
        // 이메일 중복 검증
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = Member.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .build();

        Member savedMember = memberRepository.save(member);
        log.info("회원가입 완료 - email: {}", savedMember.getEmail());

        return MemberResponse.from(savedMember);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        // 이메일로 회원 조회
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        // 토큰 생성
        String role = "ROLE_" + member.getRole().name();
        String accessToken = jwtTokenProvider.generateAccessToken(member.getEmail(), role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getEmail());

        // Refresh Token DB 저장
        member.updateRefreshToken(refreshToken);

        log.info("로그인 완료 - email: {}", member.getEmail());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiresIn())
                .build();
    }

    @Override
    @Transactional
    public void logout(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // Refresh Token 삭제
        member.updateRefreshToken(null);
        log.info("로그아웃 완료 - email: {}", email);
    }

    @Override
    @Transactional
    public TokenResponse reissue(TokenReissueRequest request) {
        String refreshToken = request.getRefreshToken();

        // Refresh Token 유효성 검증 (만료/위변조)
        jwtTokenProvider.validateToken(refreshToken);

        // 이메일 추출
        String email = jwtTokenProvider.getEmail(refreshToken);

        // 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // DB 저장된 Refresh Token과 비교
        if (member.getRefreshToken() == null || !member.getRefreshToken().equals(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        // 새 토큰 발급
        String role = "ROLE_" + member.getRole().name();
        String newAccessToken = jwtTokenProvider.generateAccessToken(email, role);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);

        // 새 Refresh Token DB 업데이트
        member.updateRefreshToken(newRefreshToken);

        log.info("토큰 재발급 완료 - email: {}", email);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiresIn())
                .build();
    }
}
