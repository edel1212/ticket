package com.yoo.ticket.domain.seat.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 좌석 조회 응답 DTO.
 */
@Getter
@Builder
public class SeatResponse {

    private final Long seatId;
    private final String seatNumber;
    private final SeatStatus status;
}
