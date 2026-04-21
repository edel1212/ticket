package com.yoo.ticket.domain.reservation.service;

import com.yoo.ticket.domain.reservation.dto.response.ReservationResponse;
import com.yoo.ticket.global.exception.BusinessException;

/**
 * 예매 서비스 인터페이스.
 * 좌석 예매 확정 관련 비즈니스 로직을 정의합니다.
 */
public interface ReservationService {

    /**
     * 임시 점유된 좌석을 예매 확정합니다.
     *
     * <p>예매 확정 처리 시 다음 검증을 수행합니다:
     * <ul>
     *   <li>대기열 토큰 유효성 확인</li>
     *   <li>좌석 존재 여부 확인</li>
     *   <li>분산락(Redisson RLock) 획득 (tryLock 3초, 점유 5초)</li>
     *   <li>Redis hold 키 존재 여부 확인 (사전 점유 필수)</li>
     *   <li>hold 소유자와 요청자 일치 여부 확인</li>
     *   <li>중복 예매 여부 확인</li>
     * </ul>
     *
     * <p>예매 성공 시 DB에 Reservation을 저장하고 Redis hold 키를 삭제합니다.
     *
     * @param trainId     열차 ID
     * @param seatId      예매할 좌석 ID
     * @param memberEmail 예매를 요청하는 회원 이메일
     * @param queueToken  대기열 토큰 (유효성 검증에 사용)
     * @return 예매 결과 ({@link ReservationResponse}) - 예매 ID, 열차/좌석 정보, 생성 시각 포함
     * @throws BusinessException QUEUE_TOKEN_INVALID - 대기열 토큰이 유효하지 않은 경우
     * @throws BusinessException SEAT_NOT_FOUND - 해당 열차에 좌석이 존재하지 않는 경우
     * @throws BusinessException MEMBER_NOT_FOUND - 회원이 존재하지 않는 경우
     * @throws BusinessException SEAT_LOCK_FAILED - 분산락 획득에 실패한 경우
     * @throws BusinessException SEAT_HOLD_NOT_FOUND - 사전 점유 없이 예매를 시도한 경우
     * @throws BusinessException SEAT_HOLD_OTHER_MEMBER - 다른 사용자가 점유한 좌석을 예매 시도한 경우
     * @throws BusinessException SEAT_ALREADY_RESERVED - 이미 예매 확정된 좌석인 경우
     */
    ReservationResponse createReservation(Long trainId, Long seatId, String memberEmail, String queueToken);
}
