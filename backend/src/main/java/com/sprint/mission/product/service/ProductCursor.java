package com.sprint.mission.product.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.dto.ProductSort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 상품 목록의 커서다. 정렬 종류, 마지막 항목의 정렬값, id를 {@code SORT|값|id}로 이어 Base64url(패딩 없음)로 인코딩한다.
 * 정렬값은 NEWEST면 등록 시각(ISO-8601), 가격 정렬이면 현재 판매가다. 해석할 수 없거나 정렬이 다르면 INVALID_INPUT이다.
 */
public final class ProductCursor {

    private static final String DELIMITER = "|";

    private ProductCursor() {
    }

    /** sortValue는 NEWEST면 {@link Instant}, 가격 정렬이면 {@link Long}이다. */
    public static String encode(ProductSort sort, Object sortValue, Long id) {
        String value = switch (sort) {
            case NEWEST -> ((Instant) sortValue).toString();
            case PRICE_ASC, PRICE_DESC -> String.valueOf((long) (Long) sortValue);
        };
        String raw = sort.name() + DELIMITER + value + DELIMITER + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String cursor, ProductSort expected) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + DELIMITER, -1);
            if (parts.length != 3 || ProductSort.valueOf(parts[0]) != expected) {
                throw invalid();
            }
            Object value = switch (expected) {
                case NEWEST -> Instant.parse(parts[1]);
                case PRICE_ASC, PRICE_DESC -> Long.valueOf(parts[1]);
            };
            return new Decoded(value, Long.parseLong(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }

    /** value는 NEWEST면 {@link Instant}, 가격 정렬이면 {@link Long}이다. */
    public record Decoded(Object value, Long id) {

        public Instant instantValue() {
            return (Instant) value;
        }

        public long longValue() {
            return (Long) value;
        }
    }
}
