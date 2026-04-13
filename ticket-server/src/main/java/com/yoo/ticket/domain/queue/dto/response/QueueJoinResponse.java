package com.yoo.ticket.domain.queue.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 대기열 진입 응답 DTO.
 * 대기열 등록 성공 시 발급된 토큰과 현재 순번 정보를 담습니다.
 */
@Getter
@Builder
public class QueueJoinResponse {

    /**
     * 대기열 토큰 (UUID 문자열).
     * 이후 대기 상태 조회 시 사용합니다.
     */
    private String queueToken;

    /**
     * 열차 ID.
     */
    private Long trainId;

    /**
     * 대기 상태 (항상 "WAITING").
     */
    private String status;

    /**
     * 현재 대기 순번 (1부터 시작).
     */
    private Long rank;

    /**
     * 대기열 등록 안내 메시지.
     */
    private String message;
}
