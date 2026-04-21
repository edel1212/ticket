package com.yoo.ticket.domain.seat.controller;

import com.yoo.ticket.domain.seat.dto.request.SeatOccupyRequest;
import com.yoo.ticket.domain.seat.dto.response.SeatOccupyResponse;
import com.yoo.ticket.domain.seat.dto.response.SeatResponse;
import com.yoo.ticket.domain.seat.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 좌석 관련 REST API 컨트롤러.
 * 좌석 조회 및 임시 점유 엔드포인트를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/trains")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    /**
     * 특정 열차의 좌석 목록을 조회합니다.
     *
     * @param trainId     열차 ID (Path Variable)
     * @param userDetails 인증된 사용자 정보
     * @return 200 OK + 좌석 목록 (상태 포함)
     */
    @GetMapping("/{trainId}/seats")
    public ResponseEntity<List<SeatResponse>> getSeatsByTrain(
            @PathVariable Long trainId,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<SeatResponse> seats = seatService.getSeatsByTrain(trainId);
        return ResponseEntity.ok(seats);
    }

    /**
     * 특정 좌석을 임시 점유합니다.
     *
     * @param trainId     열차 ID (Path Variable)
     * @param seatId      좌석 ID (Path Variable)
     * @param request     대기열 토큰을 포함한 요청 DTO
     * @param userDetails 인증된 사용자 정보
     * @return 200 OK + 점유 결과 (만료 시각 포함)
     */
    @PostMapping("/{trainId}/seats/{seatId}/occupy")
    public ResponseEntity<SeatOccupyResponse> occupySeat(
            @PathVariable Long trainId,
            @PathVariable Long seatId,
            @Valid @RequestBody SeatOccupyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String memberEmail = userDetails.getUsername();
        SeatOccupyResponse response = seatService.occupySeat(trainId, seatId, memberEmail, request.getQueueToken());
        return ResponseEntity.ok(response);
    }
}
