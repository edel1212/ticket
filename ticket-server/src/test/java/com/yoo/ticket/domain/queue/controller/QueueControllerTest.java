package com.yoo.ticket.domain.queue.controller;

import com.yoo.ticket.domain.queue.dto.response.QueueJoinResponse;
import com.yoo.ticket.domain.queue.service.QueueService;
import com.yoo.ticket.global.security.config.TestSecurityConfig;
import com.yoo.ticket.global.security.jwt.JwtTokenProvider;
import com.yoo.ticket.global.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대기열 컨트롤러 통합 테스트.
 * MockMvc + Spring REST Docs를 사용하여 API 문서 스니펫을 자동 생성합니다.
 */
@WebMvcTest(
        controllers = QueueController.class,
        excludeAutoConfiguration = RedisAutoConfiguration.class
)
@AutoConfigureRestDocs
@Import(TestSecurityConfig.class)
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueService queueService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // ========== POST /api/v1/trains/{trainId}/queue ==========

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("대기열 진입 API - 성공 시 200 OK와 대기열 정보를 반환한다")
    void joinQueue_success() throws Exception {
        // given
        QueueJoinResponse response = QueueJoinResponse.builder()
                .queueToken("550e8400-e29b-41d4-a716-446655440000")
                .trainId(1L)
                .status("WAITING")
                .rank(1L)
                .message("대기열에 등록되었습니다. 현재 대기 순번은 1번입니다.")
                .build();

        given(queueService.joinQueue(anyLong(), anyString())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/queue", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueToken").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.trainId").value(1L))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.rank").value(1L))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andDo(print())
                .andDo(document("queue/join",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("trainId").description("열차 ID")
                        ),
                        responseFields(
                                fieldWithPath("queueToken").description("대기열 토큰 (UUID, 이후 상태 조회에 사용)"),
                                fieldWithPath("trainId").description("열차 ID"),
                                fieldWithPath("status").description("대기 상태 (WAITING)"),
                                fieldWithPath("rank").description("현재 대기 순번 (1부터 시작)"),
                                fieldWithPath("message").description("대기열 등록 안내 메시지")
                        )
                ));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("대기열 진입 API - 이미 등록된 경우 기존 토큰과 함께 200 OK를 반환한다")
    void joinQueue_alreadyRegistered_returnsExistingToken() throws Exception {
        // given: 이미 등록된 사용자 → 기존 토큰 재반환
        QueueJoinResponse response = QueueJoinResponse.builder()
                .queueToken("existing-token-uuid-12345")
                .trainId(1L)
                .status("WAITING")
                .rank(3L)
                .message("이미 대기열에 등록되어 있습니다. 현재 대기 순번은 3번입니다.")
                .build();

        given(queueService.joinQueue(anyLong(), anyString())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/queue", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueToken").value("existing-token-uuid-12345"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.rank").value(3L))
                .andDo(print())
                .andDo(document("queue/join-already-registered",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("trainId").description("열차 ID")
                        ),
                        responseFields(
                                fieldWithPath("queueToken").description("기존 대기열 토큰 (이미 등록된 경우 재반환)"),
                                fieldWithPath("trainId").description("열차 ID"),
                                fieldWithPath("status").description("대기 상태 (WAITING)"),
                                fieldWithPath("rank").description("현재 대기 순번"),
                                fieldWithPath("message").description("대기열 안내 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("대기열 진입 API - 인증되지 않은 사용자가 요청하면 401 Unauthorized를 반환한다")
    void joinQueue_unauthenticated_returns401() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/queue", 1L))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }
}
