package com.yoo.ticket.domain.reservation.entity;

import com.yoo.ticket.domain.member.entity.Member;
import com.yoo.ticket.domain.seat.entity.Seat;
import com.yoo.ticket.domain.train.entity.Train;
import com.yoo.ticket.global.common.entity.BaseTimeEntity;
import com.yoo.ticket.global.common.generator.UuidV7;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

/**
 * 예매 엔티티.
 * 회원의 좌석 예매 확정 정보를 관리합니다.
 */
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Reservation extends BaseTimeEntity {

    @Id
    @UuidV7
    @Column(length = 36)
    @Comment("예매 ID (UUID v7)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    @Comment("예매 회원 (FK)")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    @Comment("예매 좌석 (FK)")
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "train_id", nullable = false)
    @Comment("예매 열차 (FK)")
    private Train train;
}
