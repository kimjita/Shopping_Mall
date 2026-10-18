package com.sprint.mission.cart.service;

import com.sprint.mission.cart.domain.CartItem;
import com.sprint.mission.cart.domain.CartItemStatus;
import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.dto.CartItemResponse;
import com.sprint.mission.cart.dto.CartResponse;
import com.sprint.mission.cart.repository.CartItemRepository;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.ProductCard;
import com.sprint.mission.product.dto.ProductSummary;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.product.repository.ProductSearchRepository;
import com.sprint.mission.product.repository.SkuRepository;
import com.sprint.mission.product.service.ProductService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 쓰기는 모두 회원 행 잠금으로 직렬화한다. 같은 옵션을 동시에 담아도 한 줄로 합쳐지고,
 * 50줄 제한과 재고 검증도 서로 앞지르지 않는다. 장바구니는 재고를 차감하지 않고(차감은 주문 시점이다), 담을 때의 재고만 확인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    static final int MAX_LINES = 50;
    static final int MAX_QUANTITY = 99;

    private final CartItemRepository cartItemRepository;
    private final MemberService memberService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ProductSearchRepository productSearchRepository;
    private final Clock clock;

    /** 쿼리 수는 항목 수와 상관없이 일정하다. 판매 중지·삭제된 상품의 줄도 UNAVAILABLE로 보여준다. */
    public CartResponse get(Long memberId) {
        memberService.getActive(memberId);
        return new CartResponse(toResponses(cartItemRepository.findByMemberIdOrderByIdAsc(memberId)));
    }

    /** 이미 담은 옵션이면 수량을 더한다. 합친 수량이 99를 넘으면 400, 재고보다 많으면 409이고 기존 수량은 그대로다. */
    @Transactional
    public CartItemResponse add(Long memberId, CartAddRequest request) {
        memberService.getActiveForUpdate(memberId);
        requireValidQuantity(request.quantity());
        Sku sku = skuRepository.findById(request.skuId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
        requireOnSale(sku);

        CartItem existing = cartItemRepository.findByMemberIdAndSkuId(memberId, sku.getId()).orElse(null);
        int total = request.quantity() + (existing == null ? 0 : existing.getQuantity());
        requireValidQuantity(total);
        requireStock(sku, total);

        if (existing != null) {
            existing.addQuantity(request.quantity());
            return toResponses(List.of(existing)).get(0);
        }
        if (cartItemRepository.countByMemberId(memberId) >= MAX_LINES) {
            throw new BusinessException(ErrorCode.CART_LIMIT_EXCEEDED);
        }
        CartItem item = cartItemRepository.save(CartItem.create(memberId, sku.getId(), request.quantity()));
        return toResponses(List.of(item)).get(0);
    }

    /** 수량을 대체한다. 판매 중이 아닌 상품은 바꿀 수 없다(뺄 수는 있다). */
    @Transactional
    public CartItemResponse changeQuantity(Long memberId, Long cartItemId, int quantity) {
        memberService.getActiveForUpdate(memberId);
        CartItem item = findOwned(cartItemId, memberId);
        requireValidQuantity(quantity);
        Sku sku = skuRepository.findById(item.getSkuId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
        requireOnSale(sku);
        requireStock(sku, quantity);

        item.changeQuantity(quantity);
        return toResponses(List.of(item)).get(0);
    }

    /** 상품 상태와 상관없이 뺄 수 있다. */
    @Transactional
    public void remove(Long memberId, Long cartItemId) {
        memberService.getActiveForUpdate(memberId);
        cartItemRepository.delete(findOwned(cartItemId, memberId));
    }

    private CartItem findOwned(Long cartItemId, Long memberId) {
        return cartItemRepository.findByIdAndMemberId(cartItemId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    /** 삭제되었거나 판매 중이 아닌 상품은 없는 상품으로 본다. */
    private void requireOnSale(Sku sku) {
        Product product = productService.getProduct(sku.getProductId());
        if (!product.isVisibleToPublic()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    /** DTO 검증을 거치지 않는 호출과 합친 수량을 위해 서비스도 범위를 지킨다. DB CHECK에 닿기 전에 막는다. */
    private static void requireValidQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static void requireStock(Sku sku, int quantity) {
        if (quantity > sku.getStock()) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
    }

    /**
     * 줄 목록을 응답으로 바꾼다. 옵션, 상품(정가·세일), 카드(이름·브랜드·이미지·상태)를 각각 한 번에 읽어서
     * 쿼리 수가 줄 수와 무관하다. 가격은 하나의 now로 계산한다.
     */
    private List<CartItemResponse> toResponses(List<CartItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        Map<Long, Sku> skus = skuRepository.findAllById(items.stream().map(CartItem::getSkuId).distinct().toList())
                .stream().collect(Collectors.toMap(Sku::getId, Function.identity()));
        List<Long> productIds = skus.values().stream().map(Sku::getProductId).distinct().toList();
        Map<Long, Product> products = productRepository.findAllById(productIds)
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductCard> cards = productSearchRepository.findCards(productIds, now);

        return items.stream().map(item -> {
            Sku sku = skus.get(item.getSkuId());
            Product product = products.get(sku.getProductId());
            ProductCard card = cards.get(sku.getProductId());
            ProductSummary summary = card.summary();
            long unitPrice = sku.price(product.getBasePrice());
            long unitSalePrice = product.activeSale(now).map(sale -> sale.apply(unitPrice)).orElse(unitPrice);
            return new CartItemResponse(item.getId(), sku.getId(), sku.getName(), product.getId(),
                    summary.name(), summary.brandName(), summary.thumbnailUrl(), item.getQuantity(),
                    unitPrice, unitSalePrice, unitSalePrice * item.getQuantity(),
                    CartItemStatus.of(product.getStatus(), sku.getStock(), item.getQuantity()));
        }).toList();
    }
}
