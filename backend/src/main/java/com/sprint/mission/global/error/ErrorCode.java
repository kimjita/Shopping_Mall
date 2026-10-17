package com.sprint.mission.global.error;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");

    /**
     * {@link #from(HttpStatusCode)}가 고르는 공통 코드예요. 도메인 코드(예: 409 DUPLICATE_EMAIL)는 넣지 않아요.
     */
    private static final List<ErrorCode> COMMON_CODES = List.of(
            INVALID_INPUT, RESOURCE_NOT_FOUND, METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE,
            UNAUTHORIZED, FORBIDDEN, INTERNAL_ERROR);

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * Spring이 정한 HTTP 상태에 맞는 공통 코드를 찾아요. 같은 상태의 코드가 없으면 4xx는 INVALID_INPUT, 나머지는 INTERNAL_ERROR예요.
     */
    public static ErrorCode from(HttpStatusCode status) {
        return COMMON_CODES.stream()
                .filter(code -> code.status.value() == status.value())
                .findFirst()
                .orElse(status.is4xxClientError() ? INVALID_INPUT : INTERNAL_ERROR);
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
