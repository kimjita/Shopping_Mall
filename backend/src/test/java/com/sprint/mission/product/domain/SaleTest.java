package com.sprint.mission.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SaleTest {

    private static final Instant STARTS_AT = Instant.parse("2026-10-18T00:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-10-25T00:00:00Z");

    private final Sale sale = new Sale(15, STARTS_AT, ENDS_AT);

    @Test
    void 세일가는_10원_단위로_내림한다() {
        assertThat(sale.apply(12_900)).isEqualTo(10_960);
        assertThat(sale.apply(14_900)).isEqualTo(12_660);
    }

    @Test
    void 시작_시각_정각에는_세일이다() {
        assertThat(sale.isActive(STARTS_AT)).isTrue();
    }

    @Test
    void 종료_시각_정각에는_세일이_아니다() {
        assertThat(sale.isActive(ENDS_AT)).isFalse();
        assertThat(sale.isActive(ENDS_AT.minusNanos(1))).isTrue();
    }

    @Test
    void 시작_전에는_세일이_아니다() {
        assertThat(sale.isActive(STARTS_AT.minusNanos(1))).isFalse();
    }
}
