package com.sprint.mission.ranking.service;

import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.ProductCard;
import com.sprint.mission.product.repository.ProductSearchRepository;
import com.sprint.mission.product.service.CategoryService;
import com.sprint.mission.ranking.dto.RankingItem;
import com.sprint.mission.ranking.dto.RankingResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최근 7일 판매량 랭킹이다. 상품 id를 먼저 고른 뒤 목록과 같은 카드로 읽고, 고른 순서를 그대로 유지한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private static final int SIZE = 20;

    private final ProductSearchRepository productSearchRepository;
    private final CategoryService categoryService;
    private final Clock clock;

    /** categoryId가 null이면 전체, 아니면 그 아래 말단 카테고리 전체의 순위다. 없는 카테고리면 CATEGORY_NOT_FOUND다. */
    public RankingResponse top(Long categoryId) {
        List<Long> leafIds = categoryId == null ? null : categoryService.leafIdsUnder(categoryId);
        List<Long> ids = productSearchRepository.topSellingIds(leafIds, SIZE);
        Map<Long, ProductCard> cards = productSearchRepository.findCards(ids, clock.instant());

        List<RankingItem> items = new ArrayList<>();
        for (Long id : ids) {
            ProductCard card = cards.get(id);
            // 두 쿼리 사이에 판매 중지·삭제된 상품은 건너뛰고, 순위는 남은 항목 기준으로 잇는다.
            if (card != null && card.status() == ProductStatus.ON_SALE) {
                items.add(new RankingItem(items.size() + 1, card.summary()));
            }
        }
        return new RankingResponse(items);
    }
}
