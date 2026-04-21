package com.yoo.ticket.domain.seat.dto.response;

/**
 * 좌석 상태 열거형.
 */
public enum SeatStatus {
    /** 예매 가능 */
    AVAILABLE,
    /** 임시 점유 중 (5분 TTL) */
    OCCUPIED,
    /** 예매 확정 완료 */
    RESERVED
}
