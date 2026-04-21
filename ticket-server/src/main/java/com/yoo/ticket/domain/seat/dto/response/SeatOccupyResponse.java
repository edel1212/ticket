package com.yoo.ticket.domain.seat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 좌석 임시 점유 응답 DTO.
 */
@Getter
@Builder
public class SeatOccupyResponse {

    private final Long seatId;
    private final String seatNumber;
    private final LocalDateTime holdExpireAt;
    private final String message;
}
