package com.sprint.mission.product.service;

import com.sprint.mission.product.dto.ProductPage;
import com.sprint.mission.product.dto.ProductSearchCondition;
import com.sprint.mission.product.dto.ProductSummary;
import com.sprint.mission.product.repository.ProductSearchRepository;
import com.sprint.mission.product.repository.ProductSearchRepository.Row;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 상품 목록이다. 요청마다 시각을 한 번만 읽어 가격·세일 판단에 모두 같은 값을 쓴다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductSearchService {

    private final ProductSearchRepository productSearchRepository;
    private final CategoryService categoryService;
    private final Clock clock;

    public ProductPage search(ProductSearchCondition condition) {
        Instant now = clock.instant();
        List<Long> leafIds = condition.categoryId() == null ? null
                : categoryService.leafIdsUnder(condition.categoryId());

        // 다음 페이지가 있는지 알기 위해 한 개 더 읽는다.
        int size = condition.size();
        List<Row> rows = productSearchRepository.search(condition, leafIds, now, size + 1);

        boolean hasNext = rows.size() > size;
        List<Row> page = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext ? cursorOf(condition, page.get(page.size() - 1)) : null;
        return new ProductPage(page.stream().map(Row::summary).toList(), nextCursor);
    }

    private static String cursorOf(ProductSearchCondition condition, Row last) {
        ProductSummary summary = last.summary();
        return switch (condition.sort()) {
            case NEWEST -> ProductCursor.encode(condition.sort(), last.createdAt(), summary.id());
            case PRICE_ASC, PRICE_DESC -> ProductCursor.encode(condition.sort(), summary.salePrice(), summary.id());
        };
    }
}
