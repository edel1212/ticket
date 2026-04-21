package com.yoo.ticket.domain.reservation.service;

import com.yoo.ticket.domain.member.entity.Member;
import com.yoo.ticket.domain.member.enums.MemberRole;
import com.yoo.ticket.domain.member.repository.MemberRepository;
import com.yoo.ticket.domain.reservation.dto.response.ReservationResponse;
import com.yoo.ticket.domain.reservation.entity.Reservation;
import com.yoo.ticket.domain.reservation.repository.ReservationRepository;
import com.yoo.ticket.domain.reservation.service.impl.ReservationServiceImpl;
import com.yoo.ticket.domain.seat.entity.Seat;
import com.yoo.ticket.domain.seat.repository.SeatRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * 예매 서비스 단위 테스트.
 * RedissonClient, SeatRepository, MemberRepository, ReservationRepository를 Mock 처리합니다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @InjectMocks
    private ReservationServiceImpl reservationService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private MemberRepository memberRepository;

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
    private Member member;

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

        member = Member.builder()
                .id(UUID.randomUUID())
                .email(MEMBER_EMAIL)
                .password("encoded-password")
                .name("테스트유저")
                .role(MemberRole.USER)
                .build();

        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
    }

    @SuppressWarnings("unchecked")
    private void stubTokenAndHoldBuckets() {
        lenient().when(redissonClient.getBucket(contains("queue:token:"))).thenReturn(tokenBucket);
        lenient().when(redissonClient.getBucket(contains("seat:hold:"))).thenReturn(holdBucket);
    }

    @Test
    @DisplayName("createReservation - 성공 시 Reservation을 저장하고 hold 키를 삭제한다")
    @SuppressWarnings("unchecked")
    void createReservation_성공() throws InterruptedException {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.of(seat));
        given(memberRepository.findByEmail(MEMBER_EMAIL)).willReturn(Optional.of(member));
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(holdBucket.get()).willReturn(MEMBER_EMAIL);
        given(reservationRepository.existsBySeatId(SEAT_ID)).willReturn(false);

        Reservation savedReservation = Reservation.builder()
                .id("test-reservation-id")
                .member(member)
                .seat(seat)
                .train(train)
                .build();
        given(reservationRepository.save(any(Reservation.class))).willReturn(savedReservation);

        // when
        ReservationResponse response = reservationService.createReservation(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getReservationId()).isEqualTo("test-reservation-id");
        assertThat(response.getTrainId()).isEqualTo(TRAIN_ID);
        assertThat(response.getSeatId()).isEqualTo(SEAT_ID);
        assertThat(response.getSeatNumber()).isEqualTo("1A");

        verify(reservationRepository).save(any(Reservation.class));
        verify(holdBucket).delete();
    }

    @Test
    @DisplayName("createReservation - 큐 토큰 불일치 시 QUEUE_TOKEN_INVALID 예외를 던진다")
    @SuppressWarnings("unchecked")
    void createReservation_큐토큰_불일치_예외() {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn("wrong-token");

        // when & then
        assertThatThrownBy(() ->
                reservationService.createReservation(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.QUEUE_TOKEN_INVALID));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReservation - hold 정보가 없으면 SEAT_HOLD_NOT_FOUND 예외를 던진다")
    @SuppressWarnings("unchecked")
    void createReservation_점유정보없음_예외() throws InterruptedException {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.of(seat));
        given(memberRepository.findByEmail(MEMBER_EMAIL)).willReturn(Optional.of(member));
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(holdBucket.get()).willReturn(null);  // hold 없음

        // when & then
        assertThatThrownBy(() ->
                reservationService.createReservation(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SEAT_HOLD_NOT_FOUND));

        verify(lock).unlock();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReservation - 다른 사용자가 점유한 좌석이면 SEAT_HOLD_OTHER_MEMBER 예외를 던진다")
    @SuppressWarnings("unchecked")
    void createReservation_다른사용자점유_예외() throws InterruptedException {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.of(seat));
        given(memberRepository.findByEmail(MEMBER_EMAIL)).willReturn(Optional.of(member));
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(holdBucket.get()).willReturn("other@example.com");  // 다른 사용자가 점유

        // when & then
        assertThatThrownBy(() ->
                reservationService.createReservation(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SEAT_HOLD_OTHER_MEMBER));

        verify(lock).unlock();
    }

    @Test
    @DisplayName("createReservation - 이미 예매된 좌석이면 SEAT_ALREADY_RESERVED 예외를 던진다")
    @SuppressWarnings("unchecked")
    void createReservation_이미예매됨_예외() throws InterruptedException {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.of(seat));
        given(memberRepository.findByEmail(MEMBER_EMAIL)).willReturn(Optional.of(member));
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(holdBucket.get()).willReturn(MEMBER_EMAIL);
        given(reservationRepository.existsBySeatId(SEAT_ID)).willReturn(true);  // 이미 예매됨

        // when & then
        assertThatThrownBy(() ->
                reservationService.createReservation(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED));

        verify(lock).unlock();
    }

    @Test
    @DisplayName("createReservation - 락 획득 실패 시 SEAT_LOCK_FAILED 예외를 던진다")
    @SuppressWarnings("unchecked")
    void createReservation_락획득실패_예외() throws InterruptedException {
        // given
        stubTokenAndHoldBuckets();
        given(tokenBucket.get()).willReturn(QUEUE_TOKEN);
        given(seatRepository.findByIdAndTrainId(SEAT_ID, TRAIN_ID)).willReturn(Optional.of(seat));
        given(memberRepository.findByEmail(MEMBER_EMAIL)).willReturn(Optional.of(member));
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(false);

        // when & then
        assertThatThrownBy(() ->
                reservationService.createReservation(TRAIN_ID, SEAT_ID, MEMBER_EMAIL, QUEUE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SEAT_LOCK_FAILED));
    }
}
