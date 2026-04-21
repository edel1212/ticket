package com.yoo.ticket.global.exception.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 관리 Enum
 */
@Getter
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid Input Value"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "Method Not Allowed"),
    ENTITY_NOT_FOUND(HttpStatus.BAD_REQUEST, "C003", "Entity Not Found"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C004", "Server Error"),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C005", "Invalid Type Value"),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C006", "Access is Denied"),

    // Member
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "M001", "Email Already Exists"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M002", "Member Not Found"),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "M003", "Invalid Password"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "M004", "Invalid Token"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "M005", "Expired Token"),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "M006", "Refresh Token Not Found"),

    // Queue
    TRAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "Q001", "열차를 찾을 수 없습니다."),
    QUEUE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Q002", "유효하지 않은 대기열 토큰입니다."),

    // Seat
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "좌석을 찾을 수 없습니다."),
    SEAT_ALREADY_OCCUPIED(HttpStatus.CONFLICT, "S002", "이미 점유된 좌석입니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "S003", "이미 예매된 좌석입니다."),
    SEAT_HOLD_NOT_FOUND(HttpStatus.BAD_REQUEST, "S004", "점유 정보가 없습니다. 먼저 좌석을 점유해주세요."),
    SEAT_HOLD_OTHER_MEMBER(HttpStatus.FORBIDDEN, "S005", "다른 사용자가 점유한 좌석입니다."),
    SEAT_LOCK_FAILED(HttpStatus.CONFLICT, "S006", "좌석 처리 중 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
