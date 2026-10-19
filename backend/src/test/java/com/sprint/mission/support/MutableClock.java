package com.sprint.mission.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 테스트가 시간을 직접 흘려보내는 Clock이다. 실제 현재 시각에서 시작하므로
 * 같은 Clock으로 발급하는 JWT가 만료되거나 아직 유효하지 않은 상태가 되지 않는다.
 */
public class MutableClock extends Clock {

    private volatile Instant now = Instant.now();

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void set(Instant instant) {
        now = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
