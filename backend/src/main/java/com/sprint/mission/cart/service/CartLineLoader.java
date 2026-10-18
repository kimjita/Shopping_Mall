package com.sprint.mission.cart.service;

import com.sprint.mission.cart.domain.CartItem;
import com.sprint.mission.cart.domain.CartItemStatus;
import com.sprint.mission.cart.domain.CartLine;
import com.sprint.mission.cart.repository.CartItemRepository;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.ProductCard;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.product.repository.ProductSearchRepository;
import com.sprint.mission.product.repository.SkuRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 줄을 가격·상태까지 계산해서 읽는다. 옵션, 상품(정가·세일), 카드(이름·브랜드·이미지·상태)를 각각 한 번에 읽어서
 * 쿼리 수가 줄 수와 무관하다. 가격은 호출자가 넘긴 하나의 now로 계산한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartLineLoader {

    private final CartItemRepository cartItemRepository;
    private final SkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;

    /** 회원의 모든 줄을 담은 순서(id 오름차순)로 읽는다. 판매 중지·삭제된 상품의 줄도 UNAVAILABLE로 포함한다. */
    public List<CartLine> loadAll(Long memberId, Instant now) {
        return load(cartItemRepository.findByMemberIdOrderByIdAsc(memberId), now);
    }

    /**
     * 고른 줄만 요청 순서대로 읽는다. 하나라도 이 회원의 줄이 아니거나 없으면 CART_ITEM_NOT_FOUND다.
     * id는 서로 달라야 한다. 중복 검증은 호출자의 몫이다.
     */
    public List<CartLine> loadSelected(Long memberId, List<Long> cartItemIds, Instant now) {
        if (cartItemIds.isEmpty()) {
            return List.of();
        }
        Map<Long, CartItem> owned = cartItemRepository.findAllByMemberIdAndIdIn(memberId, cartItemIds).stream()
                .collect(Collectors.toMap(CartItem::getId, Function.identity()));
        if (owned.size() < cartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        return load(cartItemIds.stream().map(owned::get).toList(), now);
    }

    /** 이미 읽은 줄들을 계산한다. 장바구니 쓰기 응답이 방금 바꾼 줄 하나를 계산할 때도 쓴다. */
    List<CartLine> load(List<CartItem> items, Instant now) {
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Sku> skus = skuRepository.findAllById(items.stream().map(CartItem::getSkuId).distinct().toList())
                .stream().collect(Collectors.toMap(Sku::getId, Function.identity()));
        List<Long> productIds = skus.values().stream().map(Sku::getProductId).distinct().toList();
        Map<Long, Product> products = productRepository.findAllById(productIds)
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductCard> cards = productSearchRepository.findCards(productIds, now);

        return items.stream().map(item -> {
            Sku sku = skus.get(item.getSkuId());
            Product product = products.get(sku.getProductId());
            return new CartLine(item, sku, product, cards.get(product.getId()), product.unitPrice(sku),
                    product.unitSalePrice(sku, now),
                    CartItemStatus.of(product.getStatus(), sku.getStock(), item.getQuantity()));
        }).toList();
    }
}
