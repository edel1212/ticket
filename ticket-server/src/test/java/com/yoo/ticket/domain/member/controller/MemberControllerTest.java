package com.yoo.ticket.domain.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoo.ticket.domain.member.dto.request.LoginRequest;
import com.yoo.ticket.domain.member.dto.request.SignUpRequest;
import com.yoo.ticket.domain.member.dto.request.TokenReissueRequest;
import com.yoo.ticket.domain.member.dto.response.MemberResponse;
import com.yoo.ticket.domain.member.dto.response.TokenResponse;
import com.yoo.ticket.domain.member.enums.MemberRole;
import com.yoo.ticket.domain.member.service.MemberService;
import com.yoo.ticket.global.common.generator.UuidV7Generator;
import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
import com.yoo.ticket.global.security.config.TestSecurityConfig;
import com.yoo.ticket.global.security.jwt.JwtTokenProvider;
import com.yoo.ticket.global.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 회원 컨트롤러 통합 테스트.
 * MockMvc + Spring REST Docs를 사용하여 API 문서 스니펫을 자동 생성합니다.
 */
@WebMvcTest(MemberController.class)
@AutoConfigureRestDocs
@Import(TestSecurityConfig.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // ========== POST /api/v1/members/sign-up ==========

    @Test
    @DisplayName("회원가입 API - 성공 시 201 Created와 회원 정보를 반환한다")
    void signUp_success() throws Exception {
        // given
        SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .password("password123")
                .name("테스트유저")
                .build();

        java.util.UUID memberId = UuidV7Generator.generate();
        MemberResponse response = MemberResponse.builder()
                .id(memberId)
                .email("test@example.com")
                .name("테스트유저")
                .role(MemberRole.USER)
                .build();

        given(memberService.signUp(any(SignUpRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/members/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(memberId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("테스트유저"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andDo(print())
                .andDo(document("member/sign-up",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("이메일 (로그인 ID, 유니크)"),
                                fieldWithPath("password").description("비밀번호 (최소 8자)"),
                                fieldWithPath("name").description("회원 이름 (최대 50자)")
                        ),
                        responseFields(
                                fieldWithPath("id").description("회원 ID"),
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("name").description("회원 이름"),
                                fieldWithPath("role").description("회원 역할 (USER, ADMIN)")
                        )
                ));
    }

    @Test
    @DisplayName("회원가입 API - 중복 이메일로 요청 시 409 Conflict를 반환한다")
    void signUp_duplicateEmail_returns409() throws Exception {
        // given
        SignUpRequest request = SignUpRequest.builder()
                .email("duplicate@example.com")
                .password("password123")
                .name("중복유저")
                .build();

        given(memberService.signUp(any(SignUpRequest.class)))
                .willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

        // when & then
        mockMvc.perform(post("/api/v1/members/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("M001"))
                .andDo(print())
                .andDo(document("member/sign-up-duplicate",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("중복된 이메일"),
                                fieldWithPath("password").description("비밀번호"),
                                fieldWithPath("name").description("회원 이름")
                        ),
                        responseFields(
                                fieldWithPath("timestamp").description("에러 발생 시각"),
                                fieldWithPath("code").description("에러 코드 (M001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errors").description("필드 오류 목록 (없으면 null)").optional()
                        )
                ));
    }

    @Test
    @DisplayName("회원가입 API - 유효성 검증 실패 시 400 Bad Request를 반환한다")
    void signUp_validationFail_returns400() throws Exception {
        // given - 이메일 형식 오류
        SignUpRequest request = SignUpRequest.builder()
                .email("invalid-email")
                .password("pw")        // 8자 미만
                .name("")              // 공백
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/members/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    // ========== POST /api/v1/members/login ==========

    @Test
    @DisplayName("로그인 API - 성공 시 200 OK와 토큰 정보를 반환한다")
    void login_success() throws Exception {
        // given
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        TokenResponse response = TokenResponse.builder()
                .accessToken("eyJhbGciOiJIUzI1NiJ9.accessToken")
                .refreshToken("eyJhbGciOiJIUzI1NiJ9.refreshToken")
                .expiresIn(3600L)
                .build();

        given(memberService.login(any(LoginRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600L))
                .andDo(print())
                .andDo(document("member/login",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("이메일 (로그인 ID)"),
                                fieldWithPath("password").description("비밀번호")
                        ),
                        responseFields(
                                fieldWithPath("accessToken").description("Access Token (Authorization 헤더에 사용)"),
                                fieldWithPath("refreshToken").description("Refresh Token (토큰 재발급에 사용)"),
                                fieldWithPath("tokenType").description("토큰 타입 (항상 Bearer)"),
                                fieldWithPath("expiresIn").description("Access Token 만료 시간 (초)")
                        )
                ));
    }

    @Test
    @DisplayName("로그인 API - 존재하지 않는 이메일로 요청 시 404 Not Found를 반환한다")
    void login_memberNotFound_returns404() throws Exception {
        // given
        LoginRequest request = LoginRequest.builder()
                .email("notexist@example.com")
                .password("password123")
                .build();

        given(memberService.login(any(LoginRequest.class)))
                .willThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/v1/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("M002"))
                .andDo(print())
                .andDo(document("member/login-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("존재하지 않는 이메일"),
                                fieldWithPath("password").description("비밀번호")
                        ),
                        responseFields(
                                fieldWithPath("timestamp").description("에러 발생 시각"),
                                fieldWithPath("code").description("에러 코드 (M002)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errors").description("필드 오류 목록").optional()
                        )
                ));
    }

    @Test
    @DisplayName("로그인 API - 잘못된 비밀번호로 요청 시 401 Unauthorized를 반환한다")
    void login_invalidPassword_returns401() throws Exception {
        // given
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongPassword")
                .build();

        given(memberService.login(any(LoginRequest.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_PASSWORD));

        // when & then
        mockMvc.perform(post("/api/v1/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("M003"))
                .andDo(print())
                .andDo(document("member/login-invalid-password",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("password").description("잘못된 비밀번호")
                        ),
                        responseFields(
                                fieldWithPath("timestamp").description("에러 발생 시각"),
                                fieldWithPath("code").description("에러 코드 (M003)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errors").description("필드 오류 목록").optional()
                        )
                ));
    }

    // ========== POST /api/v1/members/logout ==========

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("로그아웃 API - 인증된 사용자가 요청하면 200 OK를 반환한다")
    void logout_success() throws Exception {
        // given
        willDoNothing().given(memberService).logout(anyString());

        // when & then
        mockMvc.perform(post("/api/v1/members/logout"))
                .andExpect(status().isOk())
                .andDo(print())
                .andDo(document("member/logout",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())
                ));
    }

    @Test
    @DisplayName("로그아웃 API - 인증되지 않은 사용자가 요청하면 401 Unauthorized를 반환한다")
    void logout_unauthenticated_returns401() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/members/logout"))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    // ========== POST /api/v1/members/reissue ==========

    @Test
    @DisplayName("토큰 재발급 API - 유효한 Refresh Token으로 새 토큰을 발급받는다")
    void reissue_success() throws Exception {
        // given
        TokenReissueRequest request = TokenReissueRequest.builder()
                .refreshToken("validRefreshToken")
                .build();

        TokenResponse response = TokenResponse.builder()
                .accessToken("newAccessToken")
                .refreshToken("newRefreshToken")
                .expiresIn(3600L)
                .build();

        given(memberService.reissue(any(TokenReissueRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/members/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("newAccessToken"))
                .andExpect(jsonPath("$.refreshToken").value("newRefreshToken"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andDo(print())
                .andDo(document("member/reissue",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("refreshToken").description("Refresh Token (로그인 시 발급된 토큰)")
                        ),
                        responseFields(
                                fieldWithPath("accessToken").description("새로 발급된 Access Token"),
                                fieldWithPath("refreshToken").description("새로 발급된 Refresh Token"),
                                fieldWithPath("tokenType").description("토큰 타입 (항상 Bearer)"),
                                fieldWithPath("expiresIn").description("Access Token 만료 시간 (초)")
                        )
                ));
    }

    @Test
    @DisplayName("토큰 재발급 API - 만료된 Refresh Token으로 요청 시 401을 반환한다")
    void reissue_expiredToken_returns401() throws Exception {
        // given
        TokenReissueRequest request = TokenReissueRequest.builder()
                .refreshToken("expiredRefreshToken")
                .build();

        given(memberService.reissue(any(TokenReissueRequest.class)))
                .willThrow(new BusinessException(ErrorCode.EXPIRED_TOKEN));

        // when & then
        mockMvc.perform(post("/api/v1/members/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("M005"))
                .andDo(print())
                .andDo(document("member/reissue-expired",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("refreshToken").description("만료된 Refresh Token")
                        ),
                        responseFields(
                                fieldWithPath("timestamp").description("에러 발생 시각"),
                                fieldWithPath("code").description("에러 코드 (M005)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errors").description("필드 오류 목록").optional()
                        )
                ));
    }
}
