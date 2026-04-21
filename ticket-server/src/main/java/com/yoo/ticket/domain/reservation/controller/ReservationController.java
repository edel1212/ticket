package com.yoo.ticket.domain.reservation.controller;

import com.yoo.ticket.domain.reservation.dto.request.ReservationRequest;
import com.yoo.ticket.domain.reservation.dto.response.ReservationResponse;
import com.yoo.ticket.domain.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 예매 관련 REST API 컨트롤러.
 * 좌석 예매 확정 엔드포인트를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/trains")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 임시 점유된 좌석을 예매 확정합니다.
     *
     * @param trainId     열차 ID (Path Variable)
     * @param seatId      좌석 ID (Path Variable)
     * @param request     대기열 토큰을 포함한 요청 DTO
     * @param userDetails 인증된 사용자 정보
     * @return 201 Created + 예매 결과
     */
    @PostMapping("/{trainId}/seats/{seatId}/reserve")
    public ResponseEntity<ReservationResponse> createReservation(
            @PathVariable Long trainId,
            @PathVariable Long seatId,
            @Valid @RequestBody ReservationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String memberEmail = userDetails.getUsername();
        ReservationResponse response = reservationService.createReservation(trainId, seatId, memberEmail, request.getQueueToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
