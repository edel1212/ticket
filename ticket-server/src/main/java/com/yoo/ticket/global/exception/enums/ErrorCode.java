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
    TRAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "Q001", "열차를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
