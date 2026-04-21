package com.yoo.ticket.domain.reservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예매 확정 요청 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {

    @NotBlank(message = "대기열 토큰은 필수입니다.")
    private String queueToken;
}
