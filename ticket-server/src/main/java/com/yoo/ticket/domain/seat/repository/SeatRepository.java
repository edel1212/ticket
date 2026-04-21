package com.yoo.ticket.domain.seat.repository;

import com.yoo.ticket.domain.seat.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 좌석 리포지토리 인터페이스.
 * 좌석 데이터 접근 레이어를 정의합니다.
 */
public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * 특정 열차의 모든 좌석을 조회합니다.
     *
     * @param trainId 열차 ID
     * @return 해당 열차에 속한 좌석 목록
     */
    List<Seat> findByTrainTrainId(Long trainId);

    /**
     * 특정 열차의 특정 좌석을 조회합니다.
     * Train PK 필드명이 trainId이므로 @Query로 명시합니다.
     *
     * @param id      좌석 ID
     * @param trainId 열차 ID
     * @return 해당 조건에 맞는 좌석 (없으면 empty)
     */
    @Query("SELECT s FROM Seat s WHERE s.id = :id AND s.train.trainId = :trainId")
    Optional<Seat> findByIdAndTrainId(@Param("id") Long id, @Param("trainId") Long trainId);
}
