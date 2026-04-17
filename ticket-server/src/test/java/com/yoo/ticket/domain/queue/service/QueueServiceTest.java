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
import static org.mockito.Mockito.never;
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
        // given: tryAdd true (신규 등록 성공), 0-based rank=0 → 1-based rank=1
        given(trainRepository.existsById(TRAIN_ID)).willReturn(true);
        given(scoredSortedSet.tryAdd(anyDouble(), eq(MEMBER_EMAIL))).willReturn(true);
        given(scoredSortedSet.rank(MEMBER_EMAIL)).willReturn(0);

        // when
        QueueJoinResponse response = queueService.joinQueue(TRAIN_ID, MEMBER_EMAIL);

        // then: rank 변환 로직(0-based → 1-based) 명확히 검증
        assertThat(response).isNotNull();
        assertThat(response.getQueueToken()).isNotNull();
        assertThat(response.getTrainId()).isEqualTo(TRAIN_ID);
        assertThat(response.getStatus()).isEqualTo("WAITING");
        assertThat(response.getRank()).isEqualTo(1L);  // 0-based 0 → 1-based 1
        assertThat(response.getMessage()).contains("1번");

        // ZADD NX 호출 검증
        verify(scoredSortedSet).tryAdd(anyDouble(), eq(MEMBER_EMAIL));
        // 신규 등록 시 ZSET TTL 설정 검증
        verify(scoredSortedSet).expire(any(Duration.class));
        // 신규 등록 시 토큰 저장 호출 검증 (set(V value, Duration duration) 2-arg 버전)
        verify(bucket).set(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("joinQueue - 재진입 시 멱등성 보장: 기존 토큰과 순번을 그대로 반환하고 TTL을 갱신하지 않는다")
    @SuppressWarnings("unchecked")
    void joinQueue_재진입시_기존순번반환() {
        // given: tryAdd false (이미 존재) + 기존 토큰 조회
        given(trainRepository.existsById(TRAIN_ID)).willReturn(true);
        given(scoredSortedSet.tryAdd(anyDouble(), eq(MEMBER_EMAIL))).willReturn(false);
        given(bucket.get()).willReturn("existing-token");
        given(scoredSortedSet.rank(MEMBER_EMAIL)).willReturn(2);  // 0-based 2 → 1-based 3

        // when
        QueueJoinResponse response = queueService.joinQueue(TRAIN_ID, MEMBER_EMAIL);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getQueueToken()).isEqualTo("existing-token");
        assertThat(response.getTrainId()).isEqualTo(TRAIN_ID);
        assertThat(response.getStatus()).isEqualTo("WAITING");
        assertThat(response.getRank()).isEqualTo(3L);  // 0-based 2 → 1-based 3
        assertThat(response.getMessage()).contains("3번");

        // 재진입 시 새 토큰 저장 없음 검증 (기존 토큰을 그대로 사용)
        verify(bucket, never()).set(anyString(), any(Duration.class));
        // 재진입 시 ZSET TTL 갱신 없음 검증 (선착순 보장 - TTL 유지)
        verify(scoredSortedSet, never()).expire(any(Duration.class));
    }

    @Test
    @DisplayName("joinQueue - 재진입 시 토큰 소실 엣지 케이스: ZSET 잔여 TTL에 동기화하여 새 토큰을 재발급한다")
    @SuppressWarnings("unchecked")
    void joinQueue_재진입시_토큰소실_재발급() {
        // given: ZSET에 29분(1,740,000ms)이 남은 상태에서 토큰만 만료된 케이스
        long remainMs = Duration.ofMinutes(29).toMillis();

        given(trainRepository.existsById(TRAIN_ID)).willReturn(true);
        given(scoredSortedSet.tryAdd(anyDouble(), eq(MEMBER_EMAIL))).willReturn(false);
        given(bucket.get()).willReturn(null);  // 토큰 소실 케이스
        given(scoredSortedSet.remainTimeToLive()).willReturn(remainMs);
        given(scoredSortedSet.rank(MEMBER_EMAIL)).willReturn(5);  // 0-based 5 → 1-based 6

        // when
        QueueJoinResponse response = queueService.joinQueue(TRAIN_ID, MEMBER_EMAIL);

        // then: 새 토큰이 재발급되어 null이 아님
        assertThat(response).isNotNull();
        assertThat(response.getQueueToken()).isNotNull();
        assertThat(response.getRank()).isEqualTo(6L);

        // 핵심 검증: ZSET 잔여 TTL(29분)로 재발급 — 1시간 고정이 아님
        verify(bucket).set(anyString(), eq(Duration.ofMillis(remainMs)));
        verify(scoredSortedSet).remainTimeToLive();
        // 토큰 소실 재발급이라도 ZSET TTL은 갱신하지 않음 (순번 유지)
        verify(scoredSortedSet, never()).expire(any(Duration.class));
    }

    @Test
    @DisplayName("joinQueue - 재진입 시 토큰 소실 + ZSET TTL 없음: QUEUE_TTL(1시간) 폴백으로 재발급한다")
    @SuppressWarnings("unchecked")
    void joinQueue_재진입시_토큰소실_ZSET_TTL없음_폴백() {
        // given: ZSET의 remainTimeToLive()가 -1(TTL 없음, 비정상 상태)을 반환
        given(trainRepository.existsById(TRAIN_ID)).willReturn(true);
        given(scoredSortedSet.tryAdd(anyDouble(), eq(MEMBER_EMAIL))).willReturn(false);
        given(bucket.get()).willReturn(null);
        given(scoredSortedSet.remainTimeToLive()).willReturn(-1L);
        given(scoredSortedSet.rank(MEMBER_EMAIL)).willReturn(5);

        // when
        QueueJoinResponse response = queueService.joinQueue(TRAIN_ID, MEMBER_EMAIL);

        // then: remainMs <= 0 이므로 QUEUE_TTL(1시간) 폴백
        assertThat(response.getQueueToken()).isNotNull();
        verify(bucket).set(anyString(), eq(Duration.ofHours(1)));
        verify(scoredSortedSet, never()).expire(any(Duration.class));
    }

    @Test
    @DisplayName("joinQueue - ZRANK가 null 반환: rank를 1로 기본 처리한다")
    @SuppressWarnings("unchecked")
    void joinQueue_zrank_null_반환시_rank_기본값() {
        // given: 신규 등록 성공이지만 ZRANK 조회가 null 반환(극히 드문 경쟁 케이스)
        given(trainRepository.existsById(TRAIN_ID)).willReturn(true);
        given(scoredSortedSet.tryAdd(anyDouble(), eq(MEMBER_EMAIL))).willReturn(true);
        given(scoredSortedSet.rank(MEMBER_EMAIL)).willReturn(null);  // ZRANK null 반환

        // when
        QueueJoinResponse response = queueService.joinQueue(TRAIN_ID, MEMBER_EMAIL);

        // then: null 처리 로직 → (null → 0) + 1 = 1
        assertThat(response.getRank()).isEqualTo(1L);
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

        // 열차 미존재 시 Redis 접근 없음 검증
        verify(redissonClient, never()).getScoredSortedSet(anyString());
        verify(redissonClient, never()).getBucket(anyString());
    }
}
