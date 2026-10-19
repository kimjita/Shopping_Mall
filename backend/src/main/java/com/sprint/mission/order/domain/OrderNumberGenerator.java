package com.sprint.mission.order.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * 주문번호를 만든다. 서울 날짜 {@code yyyyMMdd}, {@code -}, 무작위 {@code [0-9A-Z]} 8자 순서이다.
 * 토스 orderId 규칙(6~64자, 영숫자·{@code -}·{@code _})을 만족한다. 겹치면 unique 제약이 막는다.
 */
@Component
public class OrderNumberGenerator {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int SUFFIX_LENGTH = 8;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        StringBuilder number = new StringBuilder(DATE.format(LocalDate.now(clock.withZone(SEOUL)))).append('-');
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            number.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return number.toString();
    }
}
