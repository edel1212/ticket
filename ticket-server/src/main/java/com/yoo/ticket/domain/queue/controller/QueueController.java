package com.yoo.ticket.domain.queue.controller;

import com.yoo.ticket.domain.queue.dto.response.QueueJoinResponse;
import com.yoo.ticket.domain.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 열차 대기열 관련 REST API 컨트롤러.
 * 예매 대기열 진입 엔드포인트를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/trains")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /**
     * 특정 열차의 예매 대기열에 진입합니다.
     *
     * <p>JWT 인증이 필요하며, SecurityContext에서 회원 이메일을 추출합니다.
     * 이미 등록된 경우 기존 대기열 토큰을 그대로 반환합니다.
     *
     * @param trainId     대기열에 진입할 열차 ID (Path Variable)
     * @param userDetails 인증된 사용자 정보 (Spring Security 주입)
     * @return 200 OK + 대기열 진입 결과 ({@link QueueJoinResponse})
     */
    @PostMapping("/{trainId}/queue")
    public ResponseEntity<QueueJoinResponse> joinQueue(
            @PathVariable Long trainId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String memberEmail = userDetails.getUsername();
        QueueJoinResponse response = queueService.joinQueue(trainId, memberEmail);
        return ResponseEntity.ok(response);
    }
}
