package com.yoo.ticket.domain.reservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoo.ticket.domain.reservation.dto.request.ReservationRequest;
import com.yoo.ticket.domain.reservation.dto.response.ReservationResponse;
import com.yoo.ticket.domain.reservation.service.ReservationService;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 예매 컨트롤러 통합 테스트.
 * MockMvc + Spring REST Docs를 사용하여 API 문서 스니펫을 자동 생성합니다.
 */
@WebMvcTest(
        controllers = ReservationController.class,
        excludeAutoConfiguration = RedisAutoConfiguration.class
)
@AutoConfigureRestDocs
@Import(TestSecurityConfig.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // ========== POST /api/v1/trains/{trainId}/seats/{seatId}/reserve ==========

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("예매 확정 API - 성공 시 201 Created와 예매 정보를 반환한다")
    void createReservation_성공() throws Exception {
        // given
        ReservationRequest request = new ReservationRequest("valid-queue-token");
        ReservationResponse response = ReservationResponse.builder()
                .reservationId("reservation-uuid-v7")
                .trainId(1L)
                .seatId(10L)
                .seatNumber("1A")
                .createdAt(LocalDateTime.of(2026, 4, 21, 10, 0, 0))
                .build();

        given(reservationService.createReservation(eq(1L), eq(10L), eq("test@example.com"), eq("valid-queue-token")))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/seats/{seatId}/reserve", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value("reservation-uuid-v7"))
                .andExpect(jsonPath("$.trainId").value(1L))
                .andExpect(jsonPath("$.seatId").value(10L))
                .andExpect(jsonPath("$.seatNumber").value("1A"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andDo(print())
                .andDo(document("reservation/create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("trainId").description("열차 ID"),
                                parameterWithName("seatId").description("예매할 좌석 ID")
                        ),
                        requestFields(
                                fieldWithPath("queueToken").description("대기열 토큰 (대기열 진입 시 발급된 토큰)")
                        ),
                        responseFields(
                                fieldWithPath("reservationId").description("예매 ID (UUID v7)"),
                                fieldWithPath("trainId").description("열차 ID"),
                                fieldWithPath("seatId").description("예매된 좌석 ID"),
                                fieldWithPath("seatNumber").description("좌석 번호"),
                                fieldWithPath("createdAt").description("예매 확정 시각")
                        )
                ));

        verify(reservationService).createReservation(eq(1L), eq(10L), eq("test@example.com"), eq("valid-queue-token"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("예매 확정 API - 점유 정보가 없으면 400 Bad Request를 반환한다")
    void createReservation_점유없음_400() throws Exception {
        // given
        ReservationRequest request = new ReservationRequest("valid-queue-token");
        given(reservationService.createReservation(anyLong(), anyLong(), anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.SEAT_HOLD_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/seats/{seatId}/reserve", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("S004"))
                .andDo(print())
                .andDo(document("reservation/create-hold-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("trainId").description("열차 ID"),
                                parameterWithName("seatId").description("좌석 ID")
                        ),
                        requestFields(
                                fieldWithPath("queueToken").description("대기열 토큰")
                        ),
                        responseFields(
                                fieldWithPath("timestamp").description("에러 발생 시각"),
                                fieldWithPath("code").description("에러 코드 (S004)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errors").description("필드 오류 목록").optional()
                        )
                ));
    }

    @Test
    @DisplayName("예매 확정 API - 인증되지 않은 사용자가 요청하면 401 Unauthorized를 반환한다")
    void createReservation_비인증_401() throws Exception {
        // given
        ReservationRequest request = new ReservationRequest("some-token");

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/seats/{seatId}/reserve", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andDo(print());

        verify(reservationService, never()).createReservation(anyLong(), anyLong(), anyString(), anyString());
    }
}
