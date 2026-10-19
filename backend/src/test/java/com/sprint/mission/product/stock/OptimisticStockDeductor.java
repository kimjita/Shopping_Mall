package com.sprint.mission.product.stock;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;

/** 잠금 없이 읽은 version이 그대로일 때만 쓴다. 충돌하면 잠깐 쉬고 다시 읽는다. */
public class OptimisticStockDeductor implements StockDeductor {

    static final int MAX_ATTEMPTS = 100;

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong retries = new AtomicLong();

    public OptimisticStockDeductor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 충돌로 다시 시도한 횟수의 합계다. */
    public long retries() {
        return retries.get();
    }

    @Override
    public boolean deduct(Long skuId, int quantity) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // 트랜잭션을 열지 않으므로 읽기와 쓰기가 각각 자동 커밋된다.
            var row = jdbcTemplate.queryForMap("select stock, version from sku where id = ?", skuId);
            int stock = ((Number) row.get("stock")).intValue();
            long version = ((Number) row.get("version")).longValue();
            if (stock < quantity) {
                return false;
            }
            int updated = jdbcTemplate.update(
                    "update sku set stock = ?, version = version + 1 where id = ? and version = ?",
                    stock - quantity, skuId, version);
            if (updated == 1) {
                return true;
            }
            retries.incrementAndGet();
            sleepRandomly();
        }
        throw new IllegalStateException("낙관적 락 재시도가 " + MAX_ATTEMPTS + "회를 넘었다: skuId=" + skuId);
    }

    private void sleepRandomly() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, 6));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트됐다", e);
        }
    }
}
