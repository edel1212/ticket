package com.yoo.ticket.domain.seat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좌석 임시 점유 요청 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SeatOccupyRequest {

    @NotBlank(message = "대기열 토큰은 필수입니다.")
    private String queueToken;
}
