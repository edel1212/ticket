package com.yoo.ticket.domain.member.service;

import com.yoo.ticket.domain.member.dto.request.LoginRequest;
import com.yoo.ticket.domain.member.dto.request.SignUpRequest;
import com.yoo.ticket.domain.member.dto.request.TokenReissueRequest;
import com.yoo.ticket.domain.member.dto.response.MemberResponse;
import com.yoo.ticket.domain.member.dto.response.TokenResponse;
import com.yoo.ticket.domain.member.entity.Member;
import com.yoo.ticket.domain.member.enums.MemberRole;
import com.yoo.ticket.domain.member.repository.MemberRepository;
import com.yoo.ticket.domain.member.service.impl.MemberServiceImpl;
import com.yoo.ticket.global.common.generator.UuidV7Generator;
import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
import com.yoo.ticket.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * MemberService 단위 테스트.
 * Mockito를 사용한 서비스 레이어 비즈니스 로직 검증.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberServiceImpl memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    // ========== signUp 테스트 ==========

    @Test
    @DisplayName("회원가입 성공 - 정상적인 요청으로 회원가입에 성공한다")
    void signUp_success() {
        // given
        SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .password("password123")
                .name("테스트유저")
                .build();

        Member savedMember = Member.builder()
                .id(UuidV7Generator.generate())
                .email("test@example.com")
                .password("encodedPassword")
                .name("테스트유저")
                .build();

        given(memberRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");
        given(memberRepository.save(any(Member.class))).willReturn(savedMember);

        // when
        MemberResponse response = memberService.signUp(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("테스트유저");
        assertThat(response.getRole()).isEqualTo(MemberRole.USER);

        then(memberRepository).should().existsByEmail("test@example.com");
        then(passwordEncoder).should().encode("password123");
        then(memberRepository).should().save(any(Member.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 중복 이메일로 가입 시 DUPLICATE_EMAIL 예외가 발생한다")
    void signUp_duplicateEmail_throwsException() {
        // given
        SignUpRequest request = SignUpRequest.builder()
                .email("duplicate@example.com")
                .password("password123")
                .name("중복유저")
                .build();

        given(memberRepository.existsByEmail("duplicate@example.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        then(memberRepository).should(never()).save(any(Member.class));
    }

    // ========== login 테스트 ==========

    @Test
    @DisplayName("로그인 성공 - 올바른 이메일/비밀번호로 토큰을 발급받는다")
    void login_success() {
        // given
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        Member member = Member.builder()
                .id(UuidV7Generator.generate())
                .email("test@example.com")
                .password("encodedPassword")
                .name("테스트유저")
                .role(MemberRole.USER)
                .build();

        given(memberRepository.findByEmail("test@example.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("password123", "encodedPassword")).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(anyString(), anyString())).willReturn("accessToken");
        given(jwtTokenProvider.generateRefreshToken(anyString())).willReturn("refreshToken");
        given(jwtTokenProvider.getAccessTokenExpiresIn()).willReturn(3600L);

        // when
        TokenResponse response = memberService.login(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("accessToken");
        assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 이메일로 MEMBER_NOT_FOUND 예외가 발생한다")
    void login_memberNotFound_throwsException() {
        // given
        LoginRequest request = LoginRequest.builder()
                .email("notexist@example.com")
                .password("password123")
                .build();

        given(memberRepository.findByEmail("notexist@example.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("로그인 실패 - 잘못된 비밀번호로 INVALID_PASSWORD 예외가 발생한다")
    void login_invalidPassword_throwsException() {
        // given
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongPassword")
                .build();

        Member member = Member.builder()
                .id(UuidV7Generator.generate())
                .email("test@example.com")
                .password("encodedPassword")
                .name("테스트유저")
                .build();

        given(memberRepository.findByEmail("test@example.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> memberService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
    }

    // ========== logout 테스트 ==========

    @Test
    @DisplayName("로그아웃 성공 - 정상적으로 Refresh Token이 삭제된다")
    void logout_success() {
        // given
        String email = "test@example.com";

        Member member = Member.builder()
                .id(UuidV7Generator.generate())
                .email(email)
                .password("encodedPassword")
                .name("테스트유저")
                .refreshToken("existingRefreshToken")
                .build();

        given(memberRepository.findByEmail(email)).willReturn(Optional.of(member));

        // when
        memberService.logout(email);

        // then
        assertThat(member.getRefreshToken()).isNull();
        then(memberRepository).should().findByEmail(email);
    }

    @Test
    @DisplayName("로그아웃 실패 - 존재하지 않는 회원으로 MEMBER_NOT_FOUND 예외가 발생한다")
    void logout_memberNotFound_throwsException() {
        // given
        String email = "notexist@example.com";

        given(memberRepository.findByEmail(email)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberService.logout(email))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // ========== reissue 테스트 ==========

    @Test
    @DisplayName("토큰 재발급 성공 - 유효한 Refresh Token으로 새 토큰을 발급받는다")
    void reissue_success() {
        // given
        String refreshToken = "validRefreshToken";
        String email = "test@example.com";

        TokenReissueRequest request = TokenReissueRequest.builder()
                .refreshToken(refreshToken)
                .build();

        Member member = Member.builder()
                .id(UuidV7Generator.generate())
                .email(email)
                .password("encodedPassword")
                .name("테스트유저")
                .role(MemberRole.USER)
                .refreshToken(refreshToken)
                .build();

        given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtTokenProvider.getEmail(refreshToken)).willReturn(email);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(member));
        given(jwtTokenProvider.generateAccessToken(anyString(), anyString())).willReturn("newAccessToken");
        given(jwtTokenProvider.generateRefreshToken(anyString())).willReturn("newRefreshToken");
        given(jwtTokenProvider.getAccessTokenExpiresIn()).willReturn(3600L);

        // when
        TokenResponse response = memberService.reissue(request);

        // then
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getRefreshToken()).isEqualTo("newRefreshToken");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("토큰 재발급 실패 - DB에 저장된 Refresh Token과 불일치 시 예외 발생")
    void reissue_refreshTokenMismatch_throwsException() {
        // given
        String refreshToken = "requestedRefreshToken";
        String email = "test@example.com";

        TokenReissueRequest request = TokenReissueRequest.builder()
                .refreshToken(refreshToken)
                .build();

        Member member = Member.builder()
                .id(UuidV7Generator.generate())
                .email(email)
                .password("encodedPassword")
                .name("테스트유저")
                .refreshToken("differentRefreshToken") // 다른 토큰이 저장됨
                .build();

        given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtTokenProvider.getEmail(refreshToken)).willReturn(email);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> memberService.reissue(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 로그아웃 상태(refreshToken=null)에서 예외 발생")
    void reissue_noRefreshToken_throwsException() {
        // given
        String refreshToken = "someRefreshToken";
        String email = "test@example.com";

        TokenReissueRequest request = TokenReissueRequest.builder()
                .refreshToken(refreshToken)
                .build();

        Member member = Member.builder()
                .id(UuidV7Generator.generate())
                .email(email)
                .password("encodedPassword")
                .name("테스트유저")
                .refreshToken(null) // 로그아웃 상태
                .build();

        given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtTokenProvider.getEmail(refreshToken)).willReturn(email);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> memberService.reissue(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }
}
