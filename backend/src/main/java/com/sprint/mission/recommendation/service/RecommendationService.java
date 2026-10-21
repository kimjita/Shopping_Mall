package com.sprint.mission.recommendation.service;

import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.ProductCard;
import com.sprint.mission.product.repository.ProductSearchRepository;
import com.sprint.mission.recommendation.dto.RecommendationItem;
import com.sprint.mission.recommendation.dto.RecommendationResponse;
import com.sprint.mission.recommendation.repository.RecommendationRepository;
import com.sprint.mission.recommendation.repository.RecommendationRepository.Row;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 피부 타입별 추천이다. 매일 새벽 배치가 만든 순위를 읽고, 그 뒤 판매가 중지된 상품은 조회할 때 뺀다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final ProductSearchRepository productSearchRepository;
    private final Clock clock;

    /** 한 트랜잭션에서 지우고 다시 넣으므로 커밋 전까지 읽는 쪽은 이전 결과를 본다. 넣은 행 수를 돌려준다. */
    @Transactional
    public int rebuild() {
        return recommendationRepository.rebuild(clock.instant());
    }

    /** rank는 배치가 매긴 값 그대로라서, 판매 중지 상품이 빠지면 번호가 건너뛸 수 있다. */
    public RecommendationResponse get(SkinType skinType) {
        List<Row> rows = recommendationRepository.findBySkinType(skinType);
        Map<Long, ProductCard> cards = productSearchRepository.findCards(
                rows.stream().map(Row::productId).toList(), clock.instant());

        List<RecommendationItem> items = new ArrayList<>();
        for (Row row : rows) {
            ProductCard card = cards.get(row.productId());
            if (card != null && card.status() == ProductStatus.ON_SALE) {
                items.add(new RecommendationItem(row.rank(), card.summary(), row.reviewCount(), row.ratingAvg()));
            }
        }
        return new RecommendationResponse(skinType, items);
    }
}
