package com.sprint.mission.product.dto;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.ProductStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 관리자 상품 목록 조건이다. 쿼리 파라미터로 받는다. 없는 값은 거르지 않고, 공백뿐인 keyword는 400이며, 빈 cursor는 커서가 없는 것으로 본다. */
public record AdminProductSearchCondition(
        @Size(min = 1, max = 50, message = "검색어는 1~50자여야 합니다.")
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "검색어가 올바르지 않습니다.")
        String keyword,

        ProductStatus status,

        Long brandId,

        Boolean lowStock,

        @Size(max = 20, message = "커서가 올바르지 않습니다.")
        String cursor,

        @Min(value = 1, message = "size는 1~50이어야 합니다.")
        @Max(value = 50, message = "size는 1~50이어야 합니다.")
        Integer size) {

    public static final int DEFAULT_SIZE = 20;

    private static final java.util.regex.Pattern CURSOR = java.util.regex.Pattern.compile("[1-9][0-9]{0,18}");

    /** 검색어는 앞뒤 공백을 지운 값으로 검증하고, 크기에는 기본값을 채운다. */
    public AdminProductSearchCondition {
        if (keyword != null) {
            keyword = keyword.strip();
        }
        if (size == null) {
            size = DEFAULT_SIZE;
        }
    }

    public boolean onlyLowStock() {
        return Boolean.TRUE.equals(lowStock);
    }

    /** 커서가 없으면 null이고, 마지막 상품 id가 아니면 INVALID_INPUT이다. */
    public Long cursorId() {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            if (CURSOR.matcher(cursor).matches()) {
                return Long.parseLong(cursor);
            }
        } catch (NumberFormatException e) {
            // 19자리가 Long 범위를 넘는 경우다.
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
