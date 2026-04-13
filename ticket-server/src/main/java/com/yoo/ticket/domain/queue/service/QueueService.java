package com.yoo.ticket.domain.queue.service;

import com.yoo.ticket.domain.queue.dto.response.QueueJoinResponse;

/**
 * 대기열 서비스 인터페이스.
 * KTX 열차 예매 대기열 진입 및 관련 비즈니스 로직을 정의합니다.
 */
public interface QueueService {

    /**
     * 특정 열차의 예매 대기열에 사용자를 등록합니다.
     *
     * <p>대기열 진입 시 다음 처리를 수행합니다:
     * <ul>
     *   <li>Redis ZSET({@code train:{trainId}:waiting_queue})에서 중복 등록 여부 확인</li>
     *   <li>신규 등록: UUID 토큰 생성 → ZADD(score=현재시각ms) → 토큰 1시간 TTL로 저장</li>
     *   <li>중복 등록: 기존 토큰을 재반환 (ZADD 생략)</li>
     *   <li>ZRANK로 현재 대기 순번 조회 후 응답에 포함</li>
     * </ul>
     *
     * <p>Redis Key 설계:
     * <ul>
     *   <li>대기열 ZSET: {@code train:{trainId}:waiting_queue}</li>
     *   <li>토큰 String: {@code queue:token:{memberEmail}:{trainId}} (TTL 1시간)</li>
     * </ul>
     *
     * @param trainId     대기열에 진입할 열차 ID
     * @param memberEmail 인증된 회원의 이메일 (Spring Security Subject)
     * @return 대기열 진입 결과 ({@link QueueJoinResponse}) — 토큰, 열차ID, 상태, 순번, 메시지 포함
     */
    QueueJoinResponse joinQueue(Long trainId, String memberEmail);
}
