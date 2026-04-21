package com.yoo.ticket.domain.reservation.repository;

import com.yoo.ticket.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 예매 리포지토리 인터페이스.
 * 예매 데이터 접근 레이어를 정의합니다.
 */
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    /**
     * 특정 좌석에 대한 예매 확정 여부를 확인합니다.
     *
     * @param seatId 확인할 좌석 ID
     * @return 해당 좌석에 예매 확정 내역이 존재하면 true, 아니면 false
     */
    boolean existsBySeatId(Long seatId);
}
