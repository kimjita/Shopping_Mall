package com.sprint.mission.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderNumberGeneratorTest {

    private final OrderNumberGenerator generator =
            new OrderNumberGenerator(Clock.fixed(Instant.parse("2026-10-18T15:30:00Z"), ZoneOffset.UTC));

    @Test
    void 서울_날짜로_시작하고_형식에_맞는다() {
        String number = generator.next();

        assertThat(number).startsWith("20261019-");
        assertThat(number).matches("^\\d{8}-[0-9A-Z]{8}$");
    }

    @Test
    void 천_번_뽑아도_중복이_없다() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            numbers.add(generator.next());
        }

        assertThat(numbers).hasSize(1_000);
    }
}
