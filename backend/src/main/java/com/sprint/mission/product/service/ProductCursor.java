package com.sprint.mission.product.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.dto.ProductSort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 상품 목록의 커서다. 정렬 종류, 마지막 항목의 정렬값, id를 {@code SORT|값|id}로 이어 Base64url(패딩 없음)로 인코딩한다.
 * 정렬값은 NEWEST면 등록 시각(ISO-8601), 가격 정렬이면 현재 판매가, RATING이면 소수 둘째 자리까지의 평균 평점(리뷰가 없으면 {@code -}),
 * REVIEW_COUNT면 리뷰 수다. 해석할 수 없거나 정렬이 다르면 INVALID_INPUT이다.
 */
public final class ProductCursor {

    private static final String DELIMITER = "|";
    private static final String NO_RATING = "-";
    private static final BigDecimal MAX_RATING = new BigDecimal("5.00");
    private static final Instant MIN_INSTANT = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T23:59:59.999999Z");

    private ProductCursor() {
    }

    /**
     * sortValue는 NEWEST면 {@link Instant}, 가격 정렬이면 {@link Long}, RATING이면 {@link BigDecimal}(리뷰가 없으면 null),
     * REVIEW_COUNT면 {@link Integer}다.
     */
    public static String encode(ProductSort sort, Object sortValue, Long id) {
        String value = switch (sort) {
            case NEWEST -> ((Instant) sortValue).toString();
            case PRICE_ASC, PRICE_DESC -> String.valueOf((long) (Long) sortValue);
            // 4.5와 4.50이 같은 커서가 되도록 스케일을 맞춘다.
            case RATING -> sortValue == null ? NO_RATING : ((BigDecimal) sortValue).setScale(2).toPlainString();
            case REVIEW_COUNT -> String.valueOf((int) (Integer) sortValue);
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
                case RATING -> parseRating(parts[1]);
                case REVIEW_COUNT -> Integer.valueOf(parts[1]);
            };
            long id = Long.parseLong(parts[2]);
            if (id < 1 || !isInRange(value)) {
                throw invalid();
            }
            return new Decoded(value, id);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    /** {@code -}는 null이다. 부호·지수 표기·소수 셋째 자리 이상은 받지 않는다. */
    private static BigDecimal parseRating(String value) {
        if (NO_RATING.equals(value)) {
            return null;
        }
        if (!value.matches("\\d+(\\.\\d{1,2})?")) {
            throw invalid();
        }
        return new BigDecimal(value);
    }

    /**
     * 위조된 값이 DB 범위를 넘어 500이 되지 않도록 시각은 0001~9999년, 가격과 리뷰 수는 0 이상, 평균 평점은 0.00~5.00만 받는다.
     * 평균 평점의 null은 리뷰가 없다는 뜻이라 받는다.
     */
    private static boolean isInRange(Object value) {
        return switch (value) {
            case null -> true;
            case Instant instant -> !instant.isBefore(MIN_INSTANT) && !instant.isAfter(MAX_INSTANT);
            case BigDecimal rating -> rating.compareTo(MAX_RATING) <= 0;
            case Integer count -> count >= 0;
            default -> (Long) value >= 0;
        };
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }

    /** value는 NEWEST면 {@link Instant}, 가격 정렬이면 {@link Long}, RATING이면 {@link BigDecimal}(리뷰가 없으면 null), REVIEW_COUNT면 {@link Integer}다. */
    public record Decoded(Object value, Long id) {

        public Instant instantValue() {
            return (Instant) value;
        }

        public long longValue() {
            return (Long) value;
        }

        public BigDecimal bigDecimalValue() {
            return (BigDecimal) value;
        }

        public int intValue() {
            return (Integer) value;
        }
    }
}
