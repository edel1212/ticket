package com.yoo.ticket.domain.seat.service;

import com.yoo.ticket.domain.seat.dto.response.SeatOccupyResponse;
import com.yoo.ticket.domain.seat.dto.response.SeatResponse;
import com.yoo.ticket.global.exception.BusinessException;

import java.util.List;

/**
 * 좌석 서비스 인터페이스.
 * 좌석 조회 및 임시 점유 관련 비즈니스 로직을 정의합니다.
 */
public interface SeatService {

    /**
     * 특정 열차의 좌석 목록을 상태와 함께 조회합니다.
     *
     * <p>각 좌석은 다음 세 가지 상태 중 하나로 반환됩니다:
     * <ul>
     *   <li>AVAILABLE: 예매 가능한 좌석</li>
     *   <li>OCCUPIED: Redis hold 키가 존재하는 임시 점유 좌석 (5분 TTL)</li>
     *   <li>RESERVED: DB에 예매 확정된 좌석</li>
     * </ul>
     *
     * @param trainId 조회할 열차 ID
     * @return 좌석 목록 (상태 포함)
     */
    List<SeatResponse> getSeatsByTrain(Long trainId);

    /**
     * 특정 열차의 좌석을 임시 점유합니다.
     *
     * <p>점유 처리 시 다음 검증을 수행합니다:
     * <ul>
     *   <li>대기열 토큰 유효성 확인</li>
     *   <li>좌석 존재 여부 확인</li>
     *   <li>분산락(Redisson RLock) 획득 (tryLock 3초, 점유 5초)</li>
     *   <li>중복 점유 여부 확인 (Redis hold 키)</li>
     *   <li>이미 예매된 좌석 여부 확인</li>
     * </ul>
     *
     * <p>점유 성공 시 Redis에 hold 키를 저장하며, TTL은 5분입니다.
     *
     * @param trainId     열차 ID
     * @param seatId      점유할 좌석 ID
     * @param memberEmail 점유를 요청하는 회원 이메일
     * @param queueToken  대기열 토큰 (유효성 검증에 사용)
     * @return 점유 결과 ({@link SeatOccupyResponse}) - 점유 만료 시각 포함
     * @throws BusinessException QUEUE_TOKEN_INVALID - 대기열 토큰이 유효하지 않은 경우
     * @throws BusinessException SEAT_NOT_FOUND - 해당 열차에 좌석이 존재하지 않는 경우
     * @throws BusinessException SEAT_LOCK_FAILED - 분산락 획득에 실패한 경우
     * @throws BusinessException SEAT_ALREADY_OCCUPIED - 이미 다른 사용자가 점유한 경우
     * @throws BusinessException SEAT_ALREADY_RESERVED - 이미 예매 확정된 좌석인 경우
     */
    SeatOccupyResponse occupySeat(Long trainId, Long seatId, String memberEmail, String queueToken);
}
