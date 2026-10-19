package com.sprint.mission.order.service;

import com.sprint.mission.cart.domain.CartLine;
import com.sprint.mission.cart.repository.CartItemRepository;
import com.sprint.mission.checkout.service.Checkout;
import com.sprint.mission.checkout.service.CheckoutService;
import com.sprint.mission.coupon.repository.MemberCouponRepository;
import com.sprint.mission.coupon.service.UsableCoupon;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.repository.AddressRepository;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.CancelReason;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.domain.OrderNumberGenerator;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.domain.ShippingInfo;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.dto.OrderDetailResponse;
import com.sprint.mission.order.repository.OrderItemRepository;
import com.sprint.mission.order.repository.OrderRepository;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.ProductSummary;
import com.sprint.mission.product.repository.SkuRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final MemberService memberService;
    private final AddressRepository addressRepository;
    private final CheckoutService checkoutService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderCanceler orderCanceler;
    private final SkuRepository skuRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final PointService pointService;
    private final Clock clock;

    /**
     * 결제 대기 주문을 만들고 재고·쿠폰·적립금을 먼저 차감한다. 한 트랜잭션이라 어느 단계에서 실패해도 전부 되돌아간다.
     * 회원 행을 먼저 잠가 같은 회원의 주문·취소·만료를 줄 세우고, 재고는 sku id 오름차순으로 차감해 데드락을 피한다.
     * 장바구니 줄은 지우지 않는다(결제 승인 때 지운다).
     */
    @Transactional
    public OrderDetailResponse create(Long memberId, OrderCreateRequest request) {
        Instant now = Instant.now(clock);
        memberService.getActiveForUpdate(memberId);

        // 회원당 결제 대기 주문은 하나다. IDENTITY insert는 대기 중인 UPDATE를 먼저 flush하지 않으므로,
        // 새 주문을 넣기 전에 취소를 DB에 내려 부분 unique 인덱스를 비운다. 지금은 재고 복원 쿼리가 먼저 flush하지만
        // 그 동작에 기대지 않는다.
        orderRepository.findByMemberIdAndStatus(memberId, OrderStatus.PENDING).ifPresent(old -> {
            lockSkus(memberId, old, request.cartItemIds());
            orderCanceler.cancel(old, CancelReason.REPLACED, now);
            orderRepository.flush();
        });

        ShippingInfo shipping = addressRepository.findByIdAndMemberId(request.addressId(), memberId)
                .map(ShippingInfo::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
        Checkout checkout = checkoutService.prepare(memberId, request.cartItemIds(), request.memberCouponId(),
                request.points(), now);
        UsableCoupon coupon = checkout.coupon();

        Order order = orderRepository.save(Order.place(memberId, orderNumberGenerator.next(), checkout.breakdown(),
                coupon == null ? null : coupon.memberCouponId(), coupon == null ? null : coupon.name(), shipping,
                now));
        List<OrderItem> items = orderItemRepository.saveAll(checkout.lines().stream()
                .map(line -> toItem(order.getId(), line))
                .toList());

        checkout.lines().stream()
                .sorted(Comparator.comparing(line -> line.sku().getId()))
                .forEach(line -> {
                    if (skuRepository.adjustStock(line.sku().getId(), -line.item().getQuantity(), now) == 0) {
                        throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
                    }
                });
        if (coupon != null) {
            memberCouponRepository.findById(coupon.memberCouponId()).orElseThrow().use(order.getId(), now);
        }
        pointService.use(memberId, order.getPointsUsed(), "주문 사용", order.getId());
        return OrderDetailResponse.of(order, items);
    }

    /**
     * 복원(이전 주문)과 차감(새 주문)이 따로 정렬되면 두 단계 사이에서 잠금 순서가 뒤집혀 데드락이 나므로,
     * 두 주문의 옵션을 합쳐 한 번에 id 오름차순으로 잠근다. 남의 줄이나 없는 줄은 빠지고 주문서 검증이 걸러 낸다.
     */
    private void lockSkus(Long memberId, Order old, List<Long> cartItemIds) {
        Set<Long> skuIds = new TreeSet<>();
        orderItemRepository.findByOrderIdOrderByIdAsc(old.getId()).forEach(item -> skuIds.add(item.getSkuId()));
        cartItemRepository.findAllByMemberIdAndIdIn(memberId, cartItemIds).forEach(item -> skuIds.add(item.getSkuId()));
        skuRepository.lockAllForUpdate(skuIds);
    }

    private static OrderItem toItem(Long orderId, CartLine line) {
        ProductSummary summary = line.card().summary();
        return OrderItem.create(orderId, line.product().getId(), line.sku().getId(), summary.name(),
                line.sku().getName(), summary.thumbnailUrl(), line.unitPrice(), line.unitSalePrice(),
                line.item().getQuantity());
    }
}
