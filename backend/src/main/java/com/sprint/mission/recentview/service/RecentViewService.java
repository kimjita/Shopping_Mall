package com.sprint.mission.recentview.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.ProductCard;
import com.sprint.mission.product.repository.ProductSearchRepository;
import com.sprint.mission.product.service.ProductService;
import com.sprint.mission.recentview.dto.RecentViewItem;
import com.sprint.mission.recentview.repository.RecentViewRepository;
import com.sprint.mission.recentview.repository.RecentViewRepository.Viewed;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentViewService {

    /** 회원마다 최근 본 상품을 이만큼만 남긴다. */
    static final int MAX_RECENT_VIEWS = 50;

    private final RecentViewRepository recentViewRepository;
    private final MemberService memberService;
    private final ProductService productService;
    private final ProductSearchRepository productSearchRepository;
    private final Clock clock;

    /**
     * 판매 중인 상품만 기록한다. 이미 본 상품은 본 시각만 갱신하고, 50개를 넘으면 오래된 것부터 지운다.
     * 갱신과 정리가 각각 문장 하나이고 정리는 항상 최신 50개를 남기므로 회원 락은 필요 없다.
     */
    @Transactional
    public void record(Long memberId, Long productId) {
        memberService.getActive(memberId);
        Product product = productService.getProduct(productId);
        if (!product.isVisibleToPublic()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        recentViewRepository.upsert(memberId, productId, clock.instant());
        recentViewRepository.trim(memberId, MAX_RECENT_VIEWS);
    }

    /** 최근에 본 순서로 최대 50개를 읽는다. 삭제된 상품은 빠진다. */
    public List<RecentViewItem> list(Long memberId) {
        memberService.getActive(memberId);
        List<Viewed> viewed = recentViewRepository.findLatest(memberId, MAX_RECENT_VIEWS);
        Map<Long, ProductCard> cards = productSearchRepository.findCards(
                viewed.stream().map(Viewed::productId).toList(), clock.instant());
        List<RecentViewItem> items = new ArrayList<>();
        for (Viewed view : viewed) {
            ProductCard card = cards.get(view.productId());
            if (card == null || card.status() == ProductStatus.DELETED) {
                continue;
            }
            items.add(new RecentViewItem(card.summary(), card.status() == ProductStatus.ON_SALE, view.viewedAt()));
        }
        return items;
    }

    /** 본 적 없는 상품이어도 성공한다. */
    @Transactional
    public void remove(Long memberId, Long productId) {
        memberService.getActive(memberId);
        recentViewRepository.delete(memberId, productId);
    }

    @Transactional
    public void clear(Long memberId) {
        memberService.getActive(memberId);
        recentViewRepository.deleteAll(memberId);
    }
}
