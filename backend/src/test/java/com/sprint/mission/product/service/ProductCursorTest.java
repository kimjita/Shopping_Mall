package com.sprint.mission.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.dto.ProductSort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ProductCursorTest {

    @Test
    void 인코딩한_커서를_되돌린다() {
        Instant createdAt = Instant.parse("2026-10-18T01:02:03.123456Z");

        String newest = ProductCursor.encode(ProductSort.NEWEST, createdAt, 42L);
        String price = ProductCursor.encode(ProductSort.PRICE_ASC, 12_660L, 7L);

        ProductCursor.Decoded decodedNewest = ProductCursor.decode(newest, ProductSort.NEWEST);
        assertThat(decodedNewest.value()).isEqualTo(createdAt);
        assertThat(decodedNewest.id()).isEqualTo(42L);
        ProductCursor.Decoded decodedPrice = ProductCursor.decode(price, ProductSort.PRICE_ASC);
        assertThat(decodedPrice.value()).isEqualTo(12_660L);
        assertThat(decodedPrice.id()).isEqualTo(7L);
        assertThat(newest).matches("[A-Za-z0-9_-]+");
        assertThat(price).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void 평점_커서와_리뷰_수_커서를_되돌린다() {
        String rating = ProductCursor.encode(ProductSort.RATING, new BigDecimal("4.5"), 3L);
        String sameRating = ProductCursor.encode(ProductSort.RATING, new BigDecimal("4.50"), 3L);
        String noRating = ProductCursor.encode(ProductSort.RATING, null, 2L);
        String reviewCount = ProductCursor.encode(ProductSort.REVIEW_COUNT, 12, 9L);

        assertThat(rating).isEqualTo(sameRating);
        ProductCursor.Decoded decodedRating = ProductCursor.decode(rating, ProductSort.RATING);
        assertThat(decodedRating.bigDecimalValue()).isEqualByComparingTo("4.50");
        assertThat(decodedRating.id()).isEqualTo(3L);
        ProductCursor.Decoded decodedNoRating = ProductCursor.decode(noRating, ProductSort.RATING);
        assertThat(decodedNoRating.bigDecimalValue()).isNull();
        assertThat(decodedNoRating.id()).isEqualTo(2L);
        ProductCursor.Decoded decodedCount = ProductCursor.decode(reviewCount, ProductSort.REVIEW_COUNT);
        assertThat(decodedCount.intValue()).isEqualTo(12);
        assertThat(decodedCount.id()).isEqualTo(9L);
        assertThat(new String(Base64.getUrlDecoder().decode(rating), StandardCharsets.UTF_8)).isEqualTo("RATING|4.50|3");
        assertThat(new String(Base64.getUrlDecoder().decode(noRating), StandardCharsets.UTF_8)).isEqualTo("RATING|-|2");
    }

    @Test
    void 깨진_평점_커서와_리뷰_수_커서는_INVALID_INPUT() {
        for (String raw : new String[] {"RATING|abc|1", "RATING|5.01|1", "RATING|-1|1", "RATING|-0.01|1",
                "RATING|4.555|1", "RATING||1", "RATING|1e1|1", "RATING|-|0", "RATING|4.5|1|x"}) {
            assertThatThrownBy(() -> ProductCursor.decode(encodeRaw(raw), ProductSort.RATING))
                    .as(raw)
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        for (String raw : new String[] {"REVIEW_COUNT|-1|1", "REVIEW_COUNT|1.5|1", "REVIEW_COUNT|-|1",
                "REVIEW_COUNT|99999999999|1"}) {
            assertThatThrownBy(() -> ProductCursor.decode(encodeRaw(raw), ProductSort.REVIEW_COUNT))
                    .as(raw)
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        for (String raw : new String[] {"RATING|0.00|1", "RATING|5|1", "RATING|0|1", "REVIEW_COUNT|0|1"}) {
            ProductSort sort = ProductSort.valueOf(raw.substring(0, raw.indexOf('|')));
            assertThat(ProductCursor.decode(encodeRaw(raw), sort).id()).as(raw).isEqualTo(1L);
        }
    }

    @Test
    void 정렬이_다른_커서는_INVALID_INPUT() {
        String cursor = ProductCursor.encode(ProductSort.PRICE_ASC, 12_660L, 7L);

        assertThatThrownBy(() -> ProductCursor.decode(cursor, ProductSort.PRICE_DESC))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> ProductCursor.decode(cursor, ProductSort.NEWEST))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 깨진_커서는_INVALID_INPUT() {
        String notNumber = encodeRaw("PRICE_ASC|abc|7");
        String notInstant = encodeRaw("NEWEST|yesterday|7");
        String unknownSort = encodeRaw("POPULAR|100|7");
        String missingId = encodeRaw("PRICE_ASC|100");
        String negativePrice = encodeRaw("PRICE_ASC|-1|7");
        String zeroId = encodeRaw("PRICE_ASC|100|0");

        for (String broken : new String[] {"", "%%%", "not-a-cursor", notNumber, notInstant, unknownSort, missingId, negativePrice, zeroId}) {
            assertThatThrownBy(() -> ProductCursor.decode(broken, ProductSort.PRICE_ASC))
                    .as(broken)
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        for (String raw : new String[] {"NEWEST|yesterday|7", "NEWEST|+300000-01-01T00:00:00Z|7",
                "NEWEST|-0001-01-01T00:00:00Z|7", "NEWEST|2026-10-18T00:00:00Z|-5"}) {
            assertThatThrownBy(() -> ProductCursor.decode(encodeRaw(raw), ProductSort.NEWEST))
                    .as(raw)
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
    }

    private static String encodeRaw(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
