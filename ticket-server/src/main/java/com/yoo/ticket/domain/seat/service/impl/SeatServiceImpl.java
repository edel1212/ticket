package com.yoo.ticket.domain.seat.service.impl;

import com.yoo.ticket.domain.reservation.repository.ReservationRepository;
import com.yoo.ticket.domain.seat.dto.response.SeatOccupyResponse;
import com.yoo.ticket.domain.seat.dto.response.SeatResponse;
import com.yoo.ticket.domain.seat.dto.response.SeatStatus;
import com.yoo.ticket.domain.seat.entity.Seat;
import com.yoo.ticket.domain.seat.repository.SeatRepository;
import com.yoo.ticket.domain.seat.service.SeatService;
import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 좌석 서비스 구현체.
 * Redisson 분산락을 사용하여 동시성 안전한 좌석 점유를 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private static final String TOKEN_KEY = "queue:token:%s:%d";
    private static final String HOLD_KEY = "seat:hold:%d:%d";
    private static final String LOCK_KEY = "seat:lock:%d:%d";
    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final RedissonClient redissonClient;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    @Override
    public List<SeatResponse> getSeatsByTrain(Long trainId) {
        List<Seat> seats = seatRepository.findByTrainTrainId(trainId);

        return seats.stream()
                .map(seat -> {
                    SeatStatus status = resolveStatus(trainId, seat.getId());
                    return SeatResponse.builder()
                            .seatId(seat.getId())
                            .seatNumber(seat.getSeatNumber())
                            .status(status)
                            .build();
                })
                .toList();
    }

    @Override
    public SeatOccupyResponse occupySeat(Long trainId, Long seatId, String memberEmail, String queueToken) {
        validateQueueToken(memberEmail, trainId, queueToken);

        Seat seat = seatRepository.findByIdAndTrainId(seatId, trainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));

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
            if (holdBucket.isExists()) {
                throw new BusinessException(ErrorCode.SEAT_ALREADY_OCCUPIED);
            }
            if (reservationRepository.existsBySeatId(seatId)) {
                throw new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED);
            }

            holdBucket.set(memberEmail, HOLD_TTL);
            log.info("좌석 임시 점유 완료 - trainId: {}, seatId: {}, memberEmail: {}", trainId, seatId, memberEmail);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        LocalDateTime holdExpireAt = LocalDateTime.now().plus(HOLD_TTL);
        return SeatOccupyResponse.builder()
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .holdExpireAt(holdExpireAt)
                .message("좌석이 임시 점유되었습니다. 5분 내에 예매를 완료해주세요.")
                .build();
    }

    private SeatStatus resolveStatus(Long trainId, Long seatId) {
        RBucket<String> holdBucket = redissonClient.getBucket(String.format(HOLD_KEY, trainId, seatId));
        if (holdBucket.isExists()) {
            return SeatStatus.OCCUPIED;
        }
        if (reservationRepository.existsBySeatId(seatId)) {
            return SeatStatus.RESERVED;
        }
        return SeatStatus.AVAILABLE;
    }

    private void validateQueueToken(String memberEmail, Long trainId, String queueToken) {
        RBucket<String> tokenBucket = redissonClient.getBucket(String.format(TOKEN_KEY, memberEmail, trainId));
        String storedToken = tokenBucket.get();
        if (storedToken == null || !storedToken.equals(queueToken)) {
            throw new BusinessException(ErrorCode.QUEUE_TOKEN_INVALID);
        }
    }
}
