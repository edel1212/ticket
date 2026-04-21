package com.yoo.ticket.domain.seat.service;

import com.yoo.ticket.domain.reservation.repository.ReservationRepository;
import com.yoo.ticket.domain.seat.dto.response.SeatOccupyResponse;
import com.yoo.ticket.domain.seat.dto.response.SeatResponse;
import com.yoo.ticket.domain.seat.dto.response.SeatStatus;
import com.yoo.ticket.domain.seat.entity.Seat;
import com.yoo.ticket.domain.seat.repository.SeatRepository;
import com.yoo.ticket.domain.seat.service.impl.SeatServiceImpl;
import com.yoo.ticket.domain.train.entity.Train;
import com.yoo.ticket.global.exception.BusinessException;
import com.yoo.ticket.global.exception.enums.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * 좌석 서비스 단위 테스트.
 * RedissonClient, SeatRepository, ReservationRepository를 Mock 처리하여 비즈니스 로직만 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @InjectMocks
    private SeatServiceImpl seatService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    @SuppressWarnings("rawtypes")
    private RBucket tokenBucket;

    @Mock
    @SuppressWarnings("rawtypes")
    private RBucket holdBucket;

    @Mock
    private RLock lock;

    private static final Long TRAIN_ID = 1L;
    private static final Long SEAT_ID = 10L;
    private static final String MEMBER_EMAIL = "test@example.com";
    private static final String QUEUE_TOKEN = "valid-token-uuid";

    private Train train;
    private Seat seat;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        train = Train.builder()
                .trainId(TRAIN_ID)
                .trainSerial("KTX-001")
                .departureTime(LocalDateTime.of(2026, 5, 1, 10, 0))
                .build();

        seat = Seat.builder()
                .id(SEAT_ID)
                .train(train)
                .seatNumber("1A")
                .build();

        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
    }

    // occupySeat 계열 테스트에서 공통으로 사용하는 스텁 설정
    @SuppressWarnings("unchecked")
    private void stubTokenAndHoldBuckets() {
        lenient().when(redissonClient.getBucket(contains("queue:token:"))).thenReturn(tokenBucket);
        lenient().when(redissonClient.getBucket(contains("seat:hold:"))).thenReturn(holdBucket);
    }

    @Test
    @DisplayName("getSeatsByTrain - AVAILABLE/OCCUPIED/RESERVED 혼합 상태를 정확히 반환한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSeatsByTrain_성공_혼합상태() {
        // given
        Seat seat1 = Seat.builder().id(1L).train(train).seatNumber("1A").build();
        Seat seat2 = Seat.builder().id(2L).train(train).seatNumber("2A").build();
        Seat seat3 = Seat.builder().id(3L).train(train).seatNumber("3A").build();

        given(seatRepository.findByTrainTrainId(TRAIN_ID)).willReturn(List.of(seat1, seat2, seat3));

        RBucket hold1 = mock(RBucket.class);
        RBucket hold2 = mock(RBucket.class);
        RBucket hold3 = mock(RBucket.class);
        given(hold1.isExists()).willReturn(false);  // AVAILABLE
        given(hold2.isExists()).willReturn(true);   // OCCUPIED
        given(hold3.isExists()).willReturn(false);  // RESERVED (DB 예매됨)

        given(redissonClient.getBucket("seat:hold:1:1")).willReturn(hold1);
        given(redissonClient.getBucket("seat:hold:1:2")).willReturn(hold2);
        given(redissonClient.getBucket("seat:hold:1:3")).willReturn(hold3);

        given(reservationRepository.existsBySeatId(1L)).willReturn(false);
        // seat2는 OCCUPIED(hold 존재)이므로 existsBySeatId 호출 없음 - lenient 처리
        lenient().when(reservationRepository.existsBySeatId(2L)).thenReturn(false);
        given(reservationRepository.existsBySeatId(3L)).willReturn(true);

        // when
        List<SeatResponse> result = seatService.getSeatsByTrain(TRAIN_ID);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(result.get(1).getStatus()).isEqualTo(SeatStatus.OCCUPIED);
        assertThat(result.get(2).getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    @DisplayName("occupySeat - 성공 시 hold 키를 저장하고 holdExpireAt을 반환한다")
    @SuppressWarnings("unchecked")
    void occupySeat_성공_신규점유() throws InterruptedException {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.of(seat));
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(holdBucket.isExists()).willReturn(false);
        given(reservationRepository.existsBySeatId(SEAT_ID)).willReturn(false);

        // when
        SeatOccupyResponse response = seatService.occupySeat(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getSeatId()).isEqualTo(SEAT_ID);
        assertThat(response.getSeatNumber()).isEqualTo("1A");
        assertThat(response.getHoldExpireAt()).isNotNull();
        assertThat(response.getHoldExpireAt()).isAfter(LocalDateTime.now());

        verify(holdBucket).set(eq(MEMBER_EMAIL), any());
    }

    @Test
    @DisplayName("occupySeat - 큐 토큰이 불일치하면 QUEUE_TOKEN_INVALID 예외를 던진다")
    @SuppressWarnings("unchecked")
    void occupySeat_큐토큰_불일치_예외() {
        // given: 저장된 토큰이 다름
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn("different-token");

        // when & then
        assertThatThrownBy(() -> seatService.occupySeat(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.QUEUE_TOKEN_INVALID));

        verify(seatRepository, never()).findByIdAndTrainId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("occupySeat - 좌석이 존재하지 않으면 SEAT_NOT_FOUND 예외를 던진다")
    @SuppressWarnings("unchecked")
    void occupySeat_좌석없음_예외() {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> seatService.occupySeat(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SEAT_NOT_FOUND));
    }

    @Test
    @DisplayName("occupySeat - hold 키가 이미 존재하면 SEAT_ALREADY_OCCUPIED 예외를 던진다")
    @SuppressWarnings("unchecked")
    void occupySeat_이미점유된좌석_예외() throws InterruptedException {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.of(seat));
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(holdBucket.isExists()).willReturn(true);  // 이미 점유 중

        // when & then
        assertThatThrownBy(() -> seatService.occupySeat(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SEAT_ALREADY_OCCUPIED));

        verify(lock).unlock();
    }

    @Test
    @DisplayName("occupySeat - ReservationRepository가 true를 반환하면 SEAT_ALREADY_RESERVED 예외를 던진다")
    @SuppressWarnings("unchecked")
    void occupySeat_이미예매된좌석_예외() throws InterruptedException {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.of(seat));
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(holdBucket.isExists()).willReturn(false);
        given(reservationRepository.existsBySeatId(SEAT_ID)).willReturn(true);  // 이미 예매됨

        // when & then
        assertThatThrownBy(() -> seatService.occupySeat(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED));

        verify(lock).unlock();
    }

    @Test
    @DisplayName("occupySeat - tryLock 실패 시 SEAT_LOCK_FAILED 예외를 던진다")
    @SuppressWarnings("unchecked")
    void occupySeat_락획득실패_예외() throws InterruptedException {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.of(seat));
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(false);  // 락 획득 실패

        // when & then
        assertThatThrownBy(() -> seatService.occupySeat(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SEAT_LOCK_FAILED));
    }
}
