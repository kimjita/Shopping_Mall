package com.sprint.mission.wishlist.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.ProductCard;
import com.sprint.mission.product.repository.ProductSearchRepository;
import com.sprint.mission.product.service.ProductService;
import com.sprint.mission.wishlist.domain.Wishlist;
import com.sprint.mission.wishlist.dto.WishedProductIds;
import com.sprint.mission.wishlist.dto.WishlistItem;
import com.sprint.mission.wishlist.dto.WishlistPage;
import com.sprint.mission.wishlist.repository.WishlistRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    /** 커서는 마지막 찜 id의 10진수 문자열이다. Long 범위를 넘는 값은 파싱에서 걸러진다. */
    private static final Pattern CURSOR = Pattern.compile("[1-9][0-9]{0,18}");

    private final WishlistRepository wishlistRepository;
    private final MemberService memberService;
    private final ProductService productService;
    private final ProductSearchRepository productSearchRepository;
    private final Clock clock;

    /** 판매 중인 상품만 찜할 수 있다. 이미 찜했으면 아무 일도 없다. */
    @Transactional
    public void add(Long memberId, Long productId) {
        memberService.getActive(memberId);
        Product product = productService.getProduct(productId);
        if (!product.isVisibleToPublic()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        wishlistRepository.insertIfAbsent(memberId, productId, clock.instant());
    }

    /** 찜하지 않은 상품이어도 성공한다. */
    @Transactional
    public void remove(Long memberId, Long productId) {
        memberService.getActive(memberId);
        wishlistRepository.deleteByMemberIdAndProductId(memberId, productId);
    }

    /**
     * 최근에 찜한 순서로 읽는다. 삭제된 상품은 빠지므로 페이지가 size보다 짧을 수 있고,
     * nextCursor는 빠진 행까지 포함해 마지막으로 읽은 찜을 가리킨다.
     */
    public WishlistPage list(Long memberId, String cursor, int size) {
        memberService.getActive(memberId);
        Long cursorId = parseCursor(cursor);
        Instant now = clock.instant();

        // 다음 페이지가 있는지 알기 위해 한 개 더 읽는다.
        Limit limit = Limit.of(size + 1);
        List<Wishlist> rows = cursorId == null
                ? wishlistRepository.findByMemberIdOrderByIdDesc(memberId, limit)
                : wishlistRepository.findByMemberIdAndIdLessThanOrderByIdDesc(memberId, cursorId, limit);
        boolean hasNext = rows.size() > size;
        List<Wishlist> page = hasNext ? rows.subList(0, size) : rows;

        Map<Long, ProductCard> cards = productSearchRepository.findCards(
                page.stream().map(Wishlist::getProductId).toList(), now);
        List<WishlistItem> items = new ArrayList<>();
        for (Wishlist wishlist : page) {
            ProductCard card = cards.get(wishlist.getProductId());
            if (card == null || card.status() == ProductStatus.DELETED) {
                continue;
            }
            items.add(new WishlistItem(card.summary(), card.status() == ProductStatus.ON_SALE,
                    wishlist.getCreatedAt()));
        }
        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new WishlistPage(items, nextCursor);
    }

    /** 요청한 상품 중 찜한 것만 요청한 순서대로 돌려준다. */
    public WishedProductIds wished(Long memberId, List<Long> productIds) {
        memberService.getActive(memberId);
        Set<Long> wished = new HashSet<>(wishlistRepository.findProductIds(memberId, productIds));
        return new WishedProductIds(productIds.stream().distinct().filter(wished::contains).toList());
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        if (!CURSOR.matcher(cursor).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
