package com.yoo.ticket.domain.reservation.service.impl;

import com.yoo.ticket.domain.member.entity.Member;
import com.yoo.ticket.domain.member.repository.MemberRepository;
import com.yoo.ticket.domain.reservation.dto.response.ReservationResponse;
import com.yoo.ticket.domain.reservation.entity.Reservation;
import com.yoo.ticket.domain.reservation.repository.ReservationRepository;
import com.yoo.ticket.domain.reservation.service.ReservationService;
import com.yoo.ticket.domain.seat.entity.Seat;
import com.yoo.ticket.domain.seat.repository.SeatRepository;
import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 예매 서비스 구현체.
 * Redisson 분산락으로 동시성을 제어하고 DB 트랜잭션으로 예매를 확정합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private static final String TOKEN_KEY = "queue:token:%s:%d";
    private static final String HOLD_KEY = "seat:hold:%d:%d";
    private static final String LOCK_KEY = "seat:lock:%d:%d";

    private final RedissonClient redissonClient;
    private final SeatRepository seatRepository;
    private final MemberRepository memberRepository;
    private final ReservationRepository reservationRepository;

    @Override
    @Transactional
    public ReservationResponse createReservation(Long trainId, Long seatId, String memberEmail, String queueToken) {
        validateQueueToken(memberEmail, trainId, queueToken);

        Seat seat = seatRepository.findByIdAndTrainId(seatId, trainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));

        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        RLock lock = redissonClient.getLock(String.format(LOCK_KEY, trainId, seatId));
        try {
            if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.SEAT_LOCK_FAILED);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SEAT_LOCK_FAILED);
        }

        try {
            RBucket<String> holdBucket = redissonClient.getBucket(String.format(HOLD_KEY, trainId, seatId));
            String holdOwner = holdBucket.get();

            if (holdOwner == null) {
                throw new BusinessException(ErrorCode.SEAT_HOLD_NOT_FOUND);
            }
            if (!holdOwner.equals(memberEmail)) {
                throw new BusinessException(ErrorCode.SEAT_HOLD_OTHER_MEMBER);
            }
            if (reservationRepository.existsBySeatId(seatId)) {
                throw new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED);
            }

            Reservation reservation = Reservation.builder()
                    .member(member)
                    .seat(seat)
                    .train(seat.getTrain())
                    .build();
            Reservation saved = reservationRepository.save(reservation);

            holdBucket.delete();
            log.info("예매 확정 완료 - reservationId: {}, trainId: {}, seatId: {}, memberEmail: {}",
                    saved.getId(), trainId, seatId, memberEmail);

            return ReservationResponse.builder()
                    .reservationId(saved.getId())
                    .trainId(seat.getTrain().getTrainId())
                    .seatId(seat.getId())
                    .seatNumber(seat.getSeatNumber())
                    .createdAt(saved.getCreatedAt())
                    .build();

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void validateQueueToken(String memberEmail, Long trainId, String queueToken) {
        RBucket<String> tokenBucket = redissonClient.getBucket(String.format(TOKEN_KEY, memberEmail, trainId));
        String storedToken = tokenBucket.get();
        if (storedToken == null || !storedToken.equals(queueToken)) {
            throw new BusinessException(ErrorCode.QUEUE_TOKEN_INVALID);
        }
    }
}
