package com.sprint.mission.payment.domain;

import com.sprint.mission.global.common.BaseEntity;
import com.sprint.mission.payment.client.PaymentResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 토스가 승인한 결제 기록이다. 주문당 한 건이고, 금액·결제수단·승인 시각은 토스 응답을 그대로 보관한다. */
@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, length = 200, updatable = false)
    private String paymentKey;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, length = 30, updatable = false)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant approvedAt;

    private Instant canceledAt;

    /** 응답에 승인 시각이 없으면 {@code now}를 쓴다. */
    public static Payment approve(Long orderId, PaymentResult result, Instant now) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.paymentKey = result.paymentKey();
        payment.amount = result.totalAmount();
        payment.method = result.method();
        payment.status = PaymentStatus.DONE;
        payment.approvedAt = result.approvedAt() != null ? result.approvedAt() : now;
        return payment;
    }

    public void cancel(Instant now) {
        if (status != PaymentStatus.DONE) {
            throw new IllegalStateException("취소할 수 없는 결제 상태이다: " + status);
        }
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = now;
    }
}
