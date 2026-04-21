package com.yoo.ticket.domain.seat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoo.ticket.domain.seat.dto.request.SeatOccupyRequest;
import com.yoo.ticket.domain.seat.dto.response.SeatOccupyResponse;
import com.yoo.ticket.domain.seat.dto.response.SeatResponse;
import com.yoo.ticket.domain.seat.dto.response.SeatStatus;
import com.yoo.ticket.domain.seat.service.SeatService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 좌석 컨트롤러 통합 테스트.
 * MockMvc + Spring REST Docs를 사용하여 API 문서 스니펫을 자동 생성합니다.
 */
@WebMvcTest(
        controllers = SeatController.class,
        excludeAutoConfiguration = RedisAutoConfiguration.class
)
@AutoConfigureRestDocs
@Import(TestSecurityConfig.class)
class SeatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SeatService seatService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // ========== GET /api/v1/trains/{trainId}/seats ==========

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("좌석 목록 조회 API - 성공 시 200 OK와 좌석 목록을 반환한다")
    void getSeatsByTrain_성공() throws Exception {
        // given
        List<SeatResponse> seats = List.of(
                SeatResponse.builder().seatId(1L).seatNumber("1A").status(SeatStatus.AVAILABLE).build(),
                SeatResponse.builder().seatId(2L).seatNumber("2A").status(SeatStatus.OCCUPIED).build(),
                SeatResponse.builder().seatId(3L).seatNumber("3A").status(SeatStatus.RESERVED).build()
        );
        given(seatService.getSeatsByTrain(1L)).willReturn(seats);

        // when & then
        mockMvc.perform(get("/api/v1/trains/{trainId}/seats", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].seatId").value(1L))
                .andExpect(jsonPath("$[0].seatNumber").value("1A"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andDo(print())
                .andDo(document("seat/get-seats",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("trainId").description("열차 ID")
                        ),
                        responseFields(
                                fieldWithPath("[].seatId").description("좌석 ID"),
                                fieldWithPath("[].seatNumber").description("좌석 번호 (예: 1A, 2B)"),
                                fieldWithPath("[].status").description("좌석 상태 (AVAILABLE: 예매 가능, OCCUPIED: 임시 점유 중, RESERVED: 예매 완료)")
                        )
                ));
    }

    // ========== POST /api/v1/trains/{trainId}/seats/{seatId}/occupy ==========

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("좌석 점유 API - 성공 시 200 OK와 점유 결과를 반환한다")
    void occupySeat_성공() throws Exception {
        // given
        SeatOccupyRequest request = new SeatOccupyRequest("valid-queue-token");
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(5);
        SeatOccupyResponse response = SeatOccupyResponse.builder()
                .seatId(10L)
                .seatNumber("1A")
                .holdExpireAt(expireAt)
                .message("좌석이 임시 점유되었습니다. 5분 내에 예매를 완료해주세요.")
                .build();

        given(seatService.occupySeat(eq(1L), eq(10L), eq("test@example.com"), eq("valid-queue-token")))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/seats/{seatId}/occupy", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatId").value(10L))
                .andExpect(jsonPath("$.seatNumber").value("1A"))
                .andExpect(jsonPath("$.holdExpireAt").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andDo(print())
                .andDo(document("seat/occupy",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("trainId").description("열차 ID"),
                                parameterWithName("seatId").description("점유할 좌석 ID")
                        ),
                        requestFields(
                                fieldWithPath("queueToken").description("대기열 토큰 (대기열 진입 시 발급된 토큰)")
                        ),
                        responseFields(
                                fieldWithPath("seatId").description("점유된 좌석 ID"),
                                fieldWithPath("seatNumber").description("좌석 번호"),
                                fieldWithPath("holdExpireAt").description("점유 만료 시각 (5분 후)"),
                                fieldWithPath("message").description("점유 안내 메시지")
                        )
                ));

        verify(seatService).occupySeat(eq(1L), eq(10L), eq("test@example.com"), eq("valid-queue-token"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("좌석 점유 API - 유효하지 않은 큐 토큰 요청 시 401 Unauthorized를 반환한다")
    void occupySeat_큐토큰_불일치_401() throws Exception {
        // given
        SeatOccupyRequest request = new SeatOccupyRequest("invalid-token");
        given(seatService.occupySeat(anyLong(), anyLong(), anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.QUEUE_TOKEN_INVALID));

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/seats/{seatId}/occupy", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("Q002"))
                .andDo(print())
                .andDo(document("seat/occupy-token-invalid",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("trainId").description("열차 ID"),
                                parameterWithName("seatId").description("좌석 ID")
                        ),
                        requestFields(
                                fieldWithPath("queueToken").description("유효하지 않은 대기열 토큰")
                        ),
                        responseFields(
                                fieldWithPath("timestamp").description("에러 발생 시각"),
                                fieldWithPath("code").description("에러 코드 (Q002)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errors").description("필드 오류 목록").optional()
                        )
                ));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("좌석 점유 API - 이미 점유된 좌석 요청 시 409 Conflict를 반환한다")
    void occupySeat_이미점유_409() throws Exception {
        // given
        SeatOccupyRequest request = new SeatOccupyRequest("valid-queue-token");
        given(seatService.occupySeat(anyLong(), anyLong(), anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.SEAT_ALREADY_OCCUPIED));

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/seats/{seatId}/occupy", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S002"))
                .andDo(print())
                .andDo(document("seat/occupy-already-occupied",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("trainId").description("열차 ID"),
                                parameterWithName("seatId").description("이미 점유된 좌석 ID")
                        ),
                        requestFields(
                                fieldWithPath("queueToken").description("대기열 토큰")
                        ),
                        responseFields(
                                fieldWithPath("timestamp").description("에러 발생 시각"),
                                fieldWithPath("code").description("에러 코드 (S002)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errors").description("필드 오류 목록").optional()
                        )
                ));
    }

    @Test
    @DisplayName("좌석 점유 API - 인증되지 않은 사용자가 요청하면 401 Unauthorized를 반환한다")
    void occupySeat_비인증_401() throws Exception {
        // given
        SeatOccupyRequest request = new SeatOccupyRequest("some-token");

        // when & then
        mockMvc.perform(post("/api/v1/trains/{trainId}/seats/{seatId}/occupy", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andDo(print());

        verify(seatService, never()).occupySeat(anyLong(), anyLong(), anyString(), anyString());
    }
}
