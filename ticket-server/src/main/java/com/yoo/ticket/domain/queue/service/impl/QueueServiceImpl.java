package com.yoo.ticket.domain.queue.service.impl;

import com.yoo.ticket.domain.queue.dto.response.QueueJoinResponse;
import com.yoo.ticket.domain.queue.service.QueueService;
import com.yoo.ticket.domain.train.repository.TrainRepository;
import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 대기열 서비스 구현체.
 * Redisson을 사용하여 Redis ZSET 기반 대기열을 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private static final String WAITING_QUEUE_KEY = "train:%d:waiting_queue";
    private static final String TOKEN_KEY = "queue:token:%s:%d";
    private static final Duration QUEUE_TTL = Duration.ofHours(1);
    private static final String STATUS_WAITING = "WAITING";

    private final RedissonClient redissonClient;
    private final TrainRepository trainRepository;

    @Override
    public QueueJoinResponse joinQueue(Long trainId, String memberEmail) {
        // 열차 존재 여부 확인
        if (!trainRepository.existsById(trainId)) {
            throw new BusinessException(ErrorCode.TRAIN_NOT_FOUND);
        }

        String queueKey = String.format(WAITING_QUEUE_KEY, trainId);
        String tokenKey = String.format(TOKEN_KEY, memberEmail, trainId);

        RScoredSortedSet<String> waitingQueue = redissonClient.getScoredSortedSet(queueKey);
        RBucket<String> tokenBucket = redissonClient.getBucket(tokenKey);

        // 중복 등록 여부 확인 (ZSCORE) → 이미 등록된 경우 409 예외
        Double existingScore = waitingQueue.getScore(memberEmail);
        if (existingScore != null) {
            log.info("대기열 중복 진입 시도 - trainId: {}, memberEmail: {}", trainId, memberEmail);
            throw new BusinessException(ErrorCode.ALREADY_IN_QUEUE);
        }

        // 신규 등록: UUID 토큰 생성 → ZADD → ZSET TTL 설정 → 토큰 저장
        String queueToken = UUID.randomUUID().toString();
        double score = System.currentTimeMillis();
        waitingQueue.add(score, memberEmail);
        waitingQueue.expire(QUEUE_TTL);
        tokenBucket.set(queueToken, QUEUE_TTL);
        log.info("대기열 신규 진입 - trainId: {}, memberEmail: {}", trainId, memberEmail);

        // ZRANK로 현재 순번 조회 (0-based → 1-based로 변환)
        Integer zeroBasedRank = waitingQueue.rank(memberEmail);
        long rank = (zeroBasedRank != null ? zeroBasedRank : 0) + 1;

        String message = "대기열에 등록되었습니다. 현재 대기 순번은 " + rank + "번입니다.";

        return QueueJoinResponse.builder()
                .queueToken(queueToken)
                .trainId(trainId)
                .status(STATUS_WAITING)
                .rank(rank)
                .message(message)
                .build();
    }
}
