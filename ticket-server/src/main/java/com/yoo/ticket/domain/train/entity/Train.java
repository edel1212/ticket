package com.yoo.ticket.domain.train.entity;

import com.yoo.ticket.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Train extends BaseTimeEntity {
    @Id
    @Comment("열차 식별 ID")
    private Long trainId;

    @Column(nullable = false, unique = true)
    @Comment("열차 번호")
    private String trainSerial; // 예: KTX-001

    @Column(nullable = false)
    @Comment("출발 시간")
    private LocalDateTime departureTime;
}
