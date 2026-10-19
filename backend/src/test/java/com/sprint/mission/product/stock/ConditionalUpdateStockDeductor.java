package com.sprint.mission.product.stock;

import com.sprint.mission.product.repository.SkuRepository;
import java.time.Clock;
import org.springframework.transaction.support.TransactionTemplate;

/** 운영 코드와 같은 조건부 UPDATE 한 문장이다. 행 잠금을 UPDATE 순간부터 커밋까지만 쥔다. */
public class ConditionalUpdateStockDeductor implements StockDeductor {

    private final SkuRepository skuRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ConditionalUpdateStockDeductor(SkuRepository skuRepository, TransactionTemplate transactionTemplate,
                                          Clock clock) {
        this.skuRepository = skuRepository;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public boolean deduct(Long skuId, int quantity) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
                status -> skuRepository.adjustStock(skuId, -quantity, clock.instant()) == 1));
    }
}
