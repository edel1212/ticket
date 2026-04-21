package com.yoo.ticket.domain.seat.entity;

import com.yoo.ticket.domain.train.entity.Train;
import com.yoo.ticket.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

/**
 * 좌석 엔티티.
 * 열차에 속한 개별 좌석 정보를 관리합니다.
 */
@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Seat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("좌석 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "train_id", nullable = false)
    @Comment("열차 (FK)")
    private Train train;

    @Column(nullable = false, length = 10)
    @Comment("좌석 번호 (예: 1A, 2B)")
    private String seatNumber;
}
