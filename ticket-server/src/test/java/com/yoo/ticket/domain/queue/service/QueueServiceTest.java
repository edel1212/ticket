package com.yoo.ticket.domain.queue.service;

import com.yoo.ticket.domain.queue.dto.response.QueueJoinResponse;
import com.yoo.ticket.domain.queue.service.impl.QueueServiceImpl;
import com.yoo.ticket.domain.train.repository.TrainRepository;
import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * 대기열 서비스 단위 테스트.
 * RedissonClient와 TrainRepository를 Mock 처리하여 비즈니스 로직만 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @InjectMocks
    private QueueServiceImpl queueService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private TrainRepository trainRepository;

    @Mock
    @SuppressWarnings("rawtypes")
    private RScoredSortedSet scoredSortedSet;

    @Mock
    @SuppressWarnings("rawtypes")
    private RBucket bucket;

    private static final Long TRAIN_ID = 1L;
    private static final String MEMBER_EMAIL = "test@example.com";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // TRAIN_NOT_FOUND 테스트처럼 Redis에 접근하지 않는 경우에도 오류가 발생하지 않도록 lenient 처리
        lenient().when(redissonClient.getScoredSortedSet(anyString())).thenReturn(scoredSortedSet);
        lenient().when(redissonClient.getBucket(anyString())).thenReturn(bucket);
    }

    @Test
    @DisplayName("joinQueue - 신규 등록 성공: ZSET에 등록하고 WAITING 상태와 rank를 반환한다")
    @SuppressWarnings("unchecked")
    void joinQueue_성공_신규등록() {
        // given
        given(trainRepository.existsById(TRAIN_ID)).willReturn(true);
        given(scoredSortedSet.getScore(MEMBER_EMAIL)).willReturn(null);
        given(scoredSortedSet.add(anyDouble(), eq(MEMBER_EMAIL))).willReturn(true);
        given(scoredSortedSet.rank(MEMBER_EMAIL)).willReturn(0);

        // when
        QueueJoinResponse response = queueService.joinQueue(TRAIN_ID, MEMBER_EMAIL);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getQueueToken()).isNotNull();
        assertThat(response.getTrainId()).isEqualTo(TRAIN_ID);
        assertThat(response.getStatus()).isEqualTo("WAITING");
        assertThat(response.getRank()).isGreaterThanOrEqualTo(1L);
        assertThat(response.getMessage()).isNotBlank();

        // ZADD 호출 검증
        verify(scoredSortedSet).add(anyDouble(), eq(MEMBER_EMAIL));
        // ZSET TTL 설정 검증
        verify(scoredSortedSet).expire(any(Duration.class));
        // 토큰 저장 호출 검증 (set(V value, Duration duration) 2-arg 버전)
        verify(bucket).set(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("joinQueue - 이미 등록된 경우: ALREADY_IN_QUEUE(409) 예외를 던진다")
    @SuppressWarnings("unchecked")
    void joinQueue_이미등록된경우_예외발생() {
        // given: 이미 ZSET에 등록되어 있는 사용자
        given(trainRepository.existsById(TRAIN_ID)).willReturn(true);
        given(scoredSortedSet.getScore(MEMBER_EMAIL)).willReturn(System.currentTimeMillis() * 1.0);

        // when & then
        assertThatThrownBy(() -> queueService.joinQueue(TRAIN_ID, MEMBER_EMAIL))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ALREADY_IN_QUEUE));
    }

    @Test
    @DisplayName("joinQueue - 존재하지 않는 열차 ID: TRAIN_NOT_FOUND(404) 예외를 던진다")
    void joinQueue_존재하지않는열차_예외발생() {
        // given
        given(trainRepository.existsById(TRAIN_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> queueService.joinQueue(TRAIN_ID, MEMBER_EMAIL))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRAIN_NOT_FOUND));
    }
}
