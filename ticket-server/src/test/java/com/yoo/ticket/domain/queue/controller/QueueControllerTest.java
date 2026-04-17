package com.yoo.ticket.domain.queue.controller;

import com.yoo.ticket.domain.queue.dto.response.QueueJoinResponse;
import com.yoo.ticket.domain.queue.service.QueueService;
import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

        // 컨트롤러가 @WithMockUser의 username(이메일)을 서비스에 올바르게 전달하는지 검증
        verify(queueService).joinQueue(eq(1L), eq("test@example.com"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("대기열 진입 API - 존재하지 않는 열차 ID 요청 시 404 Not Found를 반환한다")
    void joinQueue_trainNotFound_returns404() throws Exception {
        // given
        given(queueService.joinQueue(anyLong(), anyString()))
                .willThrow(new BusinessException(ErrorCode.TRAIN_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/queue", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("Q001"))
                .andExpect(jsonPath("$.message").value("열차를 찾을 수 없습니다."))
                .andDo(print())
                .andDo(document("queue/join-train-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("trainId").description("열차 ID")
                        ),
                        responseFields(
                                fieldWithPath("timestamp").description("에러 발생 시각"),
                                fieldWithPath("code").description("에러 코드 (Q001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errors").description("필드 에러 목록 (없을 경우 null)").optional()
                        )
                ));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("대기열 진입 API - trainId가 숫자가 아닌 경우 400 Bad Request를 반환한다")
    void joinQueue_invalidTrainIdType_returns400() throws Exception {
        // when & then: Long 타입 파싱 실패 → MethodArgumentTypeMismatchException → 400
        mockMvc.perform(post("/api/v1/trains/{trainId}/queue", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C005"))
                .andDo(print());

        // 타입 오류 시 서비스 호출 없음 검증
        verify(queueService, never()).joinQueue(anyLong(), anyString());
    }

    @Test
    @DisplayName("대기열 진입 API - 인증되지 않은 사용자가 요청하면 401 Unauthorized를 반환한다")
    void joinQueue_unauthenticated_returns401() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/queue", 1L))
                .andExpect(status().isUnauthorized())
                .andDo(print());

        // 인증 실패 시 서비스가 호출되지 않아야 함 (Security 필터에서 차단)
        verify(queueService, never()).joinQueue(anyLong(), anyString());
    }
}
