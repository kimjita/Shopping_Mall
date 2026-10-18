package com.sprint.mission.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.dto.ProductSort;
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

        for (String broken : new String[] {"", "%%%", "not-a-cursor", notNumber, notInstant, unknownSort, missingId}) {
            assertThatThrownBy(() -> ProductCursor.decode(broken, ProductSort.PRICE_ASC))
                    .as(broken)
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        assertThatThrownBy(() -> ProductCursor.decode(notInstant, ProductSort.NEWEST))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    private static String encodeRaw(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
