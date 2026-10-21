package com.sprint.mission.ranking.service;

import com.sprint.mission.ranking.repository.SalesBatchRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 모든 상품의 최근 7일(168시간) 결제 수량을 {@code product.sales_7d}에 채운다.
 * 1,000개 묶음마다 트랜잭션을 나눠 긴 배치가 상품 행을 오래 잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SalesAggregationService {

    static final Duration WINDOW = Duration.ofHours(168);
    private static final int CHUNK_SIZE = 1_000;
    private static final int MAX_ATTEMPTS = 3;

    private final SalesBatchRepository salesBatchRepository;
    private final TransactionTemplate transactionTemplate;

    /** 판매량이 바뀐 상품 수를 돌려준다. 결과에 없는 기존 판매 상품은 0으로 내린다. */
    public int aggregate(Instant now) {
        Map<Long, Integer> sales = new TreeMap<>(salesBatchRepository.sumPaidQuantities(now.minus(WINDOW), now));
        TreeSet<Long> targets = new TreeSet<>(sales.keySet());
        targets.addAll(salesBatchRepository.findProductIdsWithSales());

        List<Long> ids = new ArrayList<>(targets);
        int changed = 0;
        for (int i = 0; i < ids.size(); i += CHUNK_SIZE) {
            Map<Long, Integer> chunk = new TreeMap<>();
            for (Long id : ids.subList(i, Math.min(i + CHUNK_SIZE, ids.size()))) {
                chunk.put(id, sales.getOrDefault(id, 0));
            }
            changed += updateWithRetry(chunk);
        }
        return changed;
    }

    /** 교착·잠금 실패면 묶음을 다시 시도한다. 절대값으로 덮어쓰므로 다시 해도 결과가 같다. */
    private int updateWithRetry(Map<Long, Integer> chunk) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> salesBatchRepository.updateSales(chunk));
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
