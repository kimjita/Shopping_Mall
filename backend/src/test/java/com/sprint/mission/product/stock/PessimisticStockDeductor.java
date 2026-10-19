package com.sprint.mission.product.stock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 행을 {@code for update}로 잠근 뒤 읽고 쓴다. 잠금을 트랜잭션이 끝날 때까지 쥔다. */
public class PessimisticStockDeductor implements StockDeductor {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public PessimisticStockDeductor(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public boolean deduct(Long skuId, int quantity) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Integer stock = jdbcTemplate.queryForObject("select stock from sku where id = ? for update",
                    Integer.class, skuId);
            if (stock == null || stock < quantity) {
                return false;
            }
            jdbcTemplate.update("update sku set stock = stock - ? where id = ?", quantity, skuId);
            return true;
        }));
    }
}
