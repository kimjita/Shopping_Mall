package com.sprint.mission.order.service;

import com.sprint.mission.coupon.repository.MemberCouponRepository;
import com.sprint.mission.order.domain.CancelReason;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.repository.OrderItemRepository;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.repository.SkuRepository;
import java.time.Instant;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문을 취소하고 주문이 차감한 재고·쿠폰·적립금을 되돌린다. 대체(새 주문), 만료, 결제 취소가 함께 쓴다.
 * 호출자가 회원 행을 잠근 쓰기 트랜잭션 안에서 불러야 한다. 재고는 sku id 오름차순으로 복원해 잠금 순서를 지킨다.
 * 같은 트랜잭션이 이어서 주문서를 검증해도 낡은 값을 보지 않도록, 재고·적립금은 DB를 바로 바꾸고 쿠폰은
 * 엔티티를 바꾼다(쿠폰 조회 JPQL이 먼저 flush한다).
 */
@Component
@RequiredArgsConstructor
public class OrderCanceler {

    private final OrderItemRepository orderItemRepository;
    private final SkuRepository skuRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final PointService pointService;

    public void cancel(Order order, CancelReason reason, Instant now) {
        order.cancel(reason, now);
        orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .sorted(Comparator.comparing(OrderItem::getSkuId))
                .forEach(item -> skuRepository.restoreStock(item.getSkuId(), item.getQuantity(), now));
        if (order.getMemberCouponId() != null) {
            memberCouponRepository.findById(order.getMemberCouponId()).orElseThrow().restore();
        }
        pointService.restore(order.getMemberId(), order.getPointsUsed(), "주문 취소", order.getId());
    }
}
