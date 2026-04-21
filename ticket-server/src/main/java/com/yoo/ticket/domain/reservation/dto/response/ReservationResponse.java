package com.yoo.ticket.domain.reservation.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 예매 확정 응답 DTO.
 */
@Getter
@Builder
public class ReservationResponse {

    private final String reservationId;
    private final Long trainId;
    private final Long seatId;
    private final String seatNumber;
    private final LocalDateTime createdAt;
}
