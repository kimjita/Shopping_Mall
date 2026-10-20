package com.sprint.mission.order.domain;

import com.sprint.mission.checkout.domain.PriceBreakdown;
import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문이다. 금액·쿠폰 이름·배송지는 주문 시점의 스냅샷이고, 결제 기한은 생성 시각 + {@link #PAYMENT_WINDOW}이다.
 * 재고·쿠폰·적립금의 차감과 복원은 서비스가 하고, 이 엔티티는 상태와 값만 바꾼다.
 */
@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    /** 결제 대기 주문이 살아 있는 시간이다. */
    public static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, updatable = false)
    private String orderNumber;

    @Column(nullable = false, updatable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private long itemsPrice;

    @Column(nullable = false, updatable = false)
    private long saleDiscount;

    @Column(nullable = false, updatable = false)
    private long itemsSalePrice;

    @Column(nullable = false, updatable = false)
    private long couponDiscount;

    @Column(nullable = false, updatable = false)
    private long shippingFee;

    @Column(nullable = false, updatable = false)
    private long pointsUsed;

    @Column(nullable = false, updatable = false)
    private long paymentAmount;

    @Column(updatable = false)
    private Long memberCouponId;

    @Column(length = 50, updatable = false)
    private String couponName;

    @Embedded
    private ShippingInfo shipping;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant canceledAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CancelReason cancelReason;

    private Instant deliveredAt;

    private Instant confirmedAt;

    public static Order place(Long memberId, String orderNumber, PriceBreakdown price, Long memberCouponId,
            String couponName, ShippingInfo shipping, Instant now) {
        Order order = new Order();
        order.orderNumber = orderNumber;
        order.memberId = memberId;
        order.status = OrderStatus.PENDING;
        order.itemsPrice = price.itemsPrice();
        order.saleDiscount = price.saleDiscount();
        order.itemsSalePrice = price.itemsSalePrice();
        order.couponDiscount = price.couponDiscount();
        order.shippingFee = price.shippingFee();
        order.pointsUsed = price.pointsUsed();
        order.paymentAmount = price.paymentAmount();
        order.memberCouponId = memberCouponId;
        order.couponName = couponName;
        order.shipping = shipping;
        order.expiresAt = now.plus(PAYMENT_WINDOW);
        return order;
    }

    /** 저장한 금액 7개로 계산 결과를 다시 만든다. */
    public PriceBreakdown price() {
        return new PriceBreakdown(itemsPrice, saleDiscount, itemsSalePrice, couponDiscount, shippingFee,
                pointsUsed, paymentAmount);
    }

    /** 결제 기한이 지났는지 본다. 기한 시각 정각부터 만료다. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void markPaid() {
        if (!status.canTransitionTo(OrderStatus.PAID)) {
            throw new IllegalStateException("결제 완료로 바꿀 수 없는 주문 상태이다: " + status);
        }
        this.status = OrderStatus.PAID;
    }

    /** 관리자가 PREPARING·SHIPPING·DELIVERED로 한 단계씩 바꾼다. DELIVERED가 되면 배송 완료 시각을 남긴다. */
    public void advance(OrderStatus next, Instant now) {
        if (!OrderStatus.ADMIN_TARGETS.contains(next)) {
            throw new IllegalArgumentException("관리자가 바꿀 수 없는 대상 상태이다: " + next);
        }
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("주문 상태를 바꿀 수 없다: " + status + " -> " + next);
        }
        this.status = next;
        if (next == OrderStatus.DELIVERED) {
            this.deliveredAt = now;
        }
    }

    /** 배송 완료 주문을 구매확정한다. */
    public void confirm(Instant now) {
        if (!status.canTransitionTo(OrderStatus.CONFIRMED)) {
            throw new IllegalStateException("구매확정할 수 없는 주문 상태이다: " + status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    /** 적립 기준 금액이다. 배송비는 뺀다. 적립금을 많이 써서 결제금액이 배송비보다 작으면 0이다. */
    public long earnBase() {
        return Math.max(0, paymentAmount - shippingFee);
    }

    public void cancel(CancelReason reason, Instant now) {
        Objects.requireNonNull(reason, "취소 사유는 필수이다");
        if (!status.canTransitionTo(OrderStatus.CANCELED)) {
            throw new IllegalStateException("취소할 수 없는 주문 상태이다: " + status);
        }
        this.status = OrderStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = now;
    }
}
