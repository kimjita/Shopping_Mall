package com.sprint.mission.product.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 상품 목록 조회 조건이다. 쿼리 파라미터로 받으며, 비어 있는 값은 거르지 않는 것으로 본다.
 * 가격은 모두 현재 판매가 기준이다.
 */
public record ProductSearchCondition(
        @Size(min = 1, max = 50, message = "검색어는 1~50자여야 합니다.")
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "검색어가 올바르지 않습니다.")
        String keyword,

        Long categoryId,

        @Size(max = 20, message = "브랜드는 최대 20개까지 고를 수 있습니다.")
        List<@NotNull(message = "브랜드 ID가 올바르지 않습니다.") Long> brandIds,

        @Min(value = 0, message = "최소 가격은 0원 이상이어야 합니다.")
        Long minPrice,

        @Min(value = 0, message = "최대 가격은 0원 이상이어야 합니다.")
        Long maxPrice,

        Boolean onSale,

        ProductSort sort,

        @Size(max = 200, message = "커서가 올바르지 않습니다.")
        String cursor,

        @Min(value = 1, message = "size는 1~50이어야 합니다.")
        @Max(value = 50, message = "size는 1~50이어야 합니다.")
        Integer size) {

    public static final int DEFAULT_SIZE = 20;

    /** 검색어는 앞뒤 공백을 지운 값으로 검증하고, 정렬과 크기에는 기본값을 채운다. */
    public ProductSearchCondition {
        if (keyword != null) {
            keyword = keyword.strip();
        }
        if (sort == null) {
            sort = ProductSort.NEWEST;
        }
        if (size == null) {
            size = DEFAULT_SIZE;
        }
    }

    @AssertTrue(message = "최소 가격은 최대 가격보다 클 수 없습니다.")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }

    public boolean onlyOnSale() {
        return Boolean.TRUE.equals(onSale);
    }
}
