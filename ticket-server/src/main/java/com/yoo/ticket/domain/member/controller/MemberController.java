package com.yoo.ticket.domain.member.controller;

import com.yoo.ticket.domain.member.dto.request.LoginRequest;
import com.yoo.ticket.domain.member.dto.request.SignUpRequest;
import com.yoo.ticket.domain.member.dto.request.TokenReissueRequest;
import com.yoo.ticket.domain.member.dto.response.MemberResponse;
import com.yoo.ticket.domain.member.dto.response.TokenResponse;
import com.yoo.ticket.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 회원 관련 REST API 컨트롤러.
 * 회원가입, 로그인, 로그아웃, 토큰 재발급 엔드포인트를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * 회원가입 API.
     *
     * @param request 회원가입 요청 정보
     * @return 201 Created + 생성된 회원 정보
     */
    @PostMapping("/sign-up")
    public ResponseEntity<MemberResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        MemberResponse response = memberService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 로그인 API.
     *
     * @param request 로그인 요청 정보
     * @return 200 OK + JWT 토큰 정보
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = memberService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 로그아웃 API.
     * Authorization 헤더의 Access Token으로 인증된 사용자만 접근 가능합니다.
     *
     * @param userDetails 인증된 사용자 정보 (Spring Security 주입)
     * @return 200 OK
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        memberService.logout(userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    /**
     * 토큰 재발급 API.
     *
     * @param request Refresh Token 요청 정보
     * @return 200 OK + 새로 발급된 JWT 토큰 정보
     */
    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@Valid @RequestBody TokenReissueRequest request) {
        TokenResponse response = memberService.reissue(request);
        return ResponseEntity.ok(response);
    }
}
