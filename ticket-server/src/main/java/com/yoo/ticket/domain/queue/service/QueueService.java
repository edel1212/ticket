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
     *   <li>열차 존재 여부 확인 — 없으면 {@code TRAIN_NOT_FOUND(404)} 예외</li>
     *   <li>Redis ZADD NX({@code train:{trainId}:waiting_queue})로 원자적 등록 시도</li>
     *   <li>신규 등록: UUID 토큰 생성 → ZADD NX(score=현재시각ms) → ZSET/토큰 모두 1시간 TTL 설정</li>
     *   <li>재진입 (이미 등록됨): 기존 토큰 조회 → 예외 없이 기존 순번 반환 (멱등성 보장).
     *       ZSET·토큰 TTL은 갱신하지 않으므로 최초 진입 시각 기준 순번이 유지됩니다.
     *       토큰만 소실된 엣지 케이스에서는 신규 토큰을 재발급하되,
     *       TTL을 ZSET 잔여 시간에 동기화하여 만료 불일치를 방지합니다.</li>
     *   <li>ZRANK로 현재 대기 순번 조회 후 응답에 포함</li>
     * </ul>
     *
     * <p>선착순 보장: ZADD NX는 원자적 연산으로, 최초 진입 시각 기준 순번이 영구 고정됩니다.
     * 재진입 시 score(timestamp)가 갱신되지 않으므로 순번 역전이 발생하지 않습니다.
     *
     * <p>Redis Key 설계:
     * <ul>
     *   <li>대기열 ZSET: {@code train:{trainId}:waiting_queue} (TTL 1시간)</li>
     *   <li>토큰 String: {@code queue:token:{memberEmail}:{trainId}} (신규: TTL 1시간, 토큰 소실 재발급: ZSET 잔여 TTL 동기화)</li>
     * </ul>
     *
     * @param trainId     대기열에 진입할 열차 ID
     * @param memberEmail 인증된 회원의 이메일 (Spring Security Subject)
     * @return 대기열 진입 결과 ({@link QueueJoinResponse}) — 토큰, 열차ID, 상태, 순번, 메시지 포함
     * @throws com.yoo.ticket.global.exception.BusinessException 열차 미존재(TRAIN_NOT_FOUND)
     */
    QueueJoinResponse joinQueue(Long trainId, String memberEmail);
}
