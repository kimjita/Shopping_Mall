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
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "배송지를 찾을 수 없습니다."),
    ADDRESS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "배송지는 최대 10개까지 등록할 수 있습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    CATEGORY_NOT_LEAF(HttpStatus.BAD_REQUEST, "가장 아래 단계 카테고리를 선택해 주세요."),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "브랜드를 찾을 수 없습니다."),
    DUPLICATE_BRAND(HttpStatus.CONFLICT, "이미 있는 브랜드 이름입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    DUPLICATE_SKU_NAME(HttpStatus.CONFLICT, "같은 이름의 옵션이 이미 있습니다."),
    SKU_NOT_FOUND(HttpStatus.NOT_FOUND, "옵션을 찾을 수 없습니다."),
    STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "재고가 부족합니다."),
    SKU_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "옵션은 최대 20개까지 등록할 수 있습니다."),
    INVALID_SALE_PERIOD(HttpStatus.BAD_REQUEST, "세일 종료 시각은 시작 시각보다 늦어야 합니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 상품을 찾을 수 없습니다."),
    CART_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "장바구니에는 최대 50개 상품을 담을 수 있습니다."),
    CART_ITEM_UNAVAILABLE(HttpStatus.CONFLICT, "구매할 수 없는 상품이 포함되어 있습니다."),
    POINTS_NOT_ENOUGH(HttpStatus.CONFLICT, "적립금이 부족합니다."),
    POINTS_EXCEED_LIMIT(HttpStatus.BAD_REQUEST, "적립금은 결제금액이 100원 이상 남도록 사용할 수 있습니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
    COUPON_SOLD_OUT(HttpStatus.CONFLICT, "쿠폰이 모두 소진되었습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 받은 쿠폰입니다."),
    COUPON_NOT_ISSUABLE(HttpStatus.BAD_REQUEST, "지금은 받을 수 없는 쿠폰입니다."),
    COUPON_NOT_USABLE(HttpStatus.BAD_REQUEST, "사용할 수 없는 쿠폰입니다."),
    COUPON_NOT_APPLICABLE(HttpStatus.BAD_REQUEST, "쿠폰 최소 주문금액을 채우지 못했습니다.");

    /**
     * {@link #from(HttpStatusCode)}가 고르는 공통 코드다. 도메인 코드(예: 409 DUPLICATE_EMAIL)는 넣지 않는다.
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
     * Spring이 정한 HTTP 상태에 맞는 공통 코드를 찾는다. 같은 상태의 코드가 없으면 4xx는 INVALID_INPUT, 나머지는 INTERNAL_ERROR다.
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
