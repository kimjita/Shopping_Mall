package com.sprint.mission.payment.service;

import com.sprint.mission.cart.repository.CartItemRepository;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.CancelReason;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.dto.OrderDetailResponse;
import com.sprint.mission.order.repository.OrderItemRepository;
import com.sprint.mission.order.repository.OrderRepository;
import com.sprint.mission.order.service.OrderCanceler;
import com.sprint.mission.payment.client.PaymentGateway;
import com.sprint.mission.payment.client.PaymentGatewayException;
import com.sprint.mission.payment.client.PaymentResult;
import com.sprint.mission.payment.domain.Payment;
import com.sprint.mission.payment.dto.PaymentConfirmRequest;
import com.sprint.mission.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String ALREADY_PROCESSED = "ALREADY_PROCESSED_PAYMENT";
    private static final String UNKNOWN = "UNKNOWN";

    private final MemberService memberService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderCanceler orderCanceler;
    private final PaymentGateway gateway;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 결제를 승인하고 주문을 결제 완료로 바꾼다. 회원 행을 잠근 채 토스를 부르므로 같은 회원의 승인·만료·새 주문은 줄을 선다.
     * 토스가 승인한 뒤 우리 쪽에서 실패하면(커밋 실패 포함) 트랜잭션 밖에서 토스 취소로 보상하고 원래 예외를 던진다.
     */
    // shortcut: 회원 행 잠금과 DB 커넥션을 토스 호출(승인·조회·취소, 호출당 연결 3초 + 읽기 10초) 최대 세 번 동안 쥔다. 토스가 느리면 Hikari 풀(기본 10)이 바닥날 수 있다, Phase 12에서 잠금·검사 트랜잭션 → 토스 호출 → 마무리 트랜잭션으로 나누거나 전용 풀을 둔다.
    public OrderDetailResponse confirm(Long memberId, String orderNumber, PaymentConfirmRequest request) {
        AtomicBoolean approved = new AtomicBoolean();
        try {
            return transactionTemplate.execute(status -> confirmInTransaction(memberId, orderNumber, request, approved));
        } catch (RuntimeException e) {
            if (approved.get()) {
                compensate(orderNumber, request.paymentKey());
            }
            throw e;
        }
    }

    private OrderDetailResponse confirmInTransaction(Long memberId, String orderNumber, PaymentConfirmRequest request,
            AtomicBoolean approved) {
        memberService.getActiveForUpdate(memberId);
        Instant now = Instant.now(clock);
        Order order = orderRepository.findByOrderNumberAndMemberId(orderNumber, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        String paymentKey = request.paymentKey();

        if (order.getStatus() == OrderStatus.PAID) {
            boolean samePayment = paymentRepository.findByOrderId(order.getId())
                    .filter(payment -> payment.getPaymentKey().equals(paymentKey))
                    .isPresent();
            if (!samePayment) {
                throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE);
            }
            return OrderDetailResponse.of(order, orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()));
        }
        if (order.getStatus() != OrderStatus.PENDING || order.isExpired(now)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE);
        }
        long amount = order.getPaymentAmount();
        if (request.amount() != amount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        PaymentResult result = approve(paymentKey, orderNumber, amount);
        approved.set(true);
        if (!result.isDone()) {
            throw new BusinessException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        }
        if (result.totalAmount() != amount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        paymentRepository.save(Payment.approve(order.getId(), result, now));
        order.markPaid();
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        cartItemRepository.deleteByMemberIdAndSkuIdIn(memberId, items.stream().map(OrderItem::getSkuId).toList());
        orderRepository.flush();
        return OrderDetailResponse.of(order, items);
    }

    /**
     * 토스 승인을 부른다. 4xx에 토스 코드가 있는 확실한 거절만 PAYMENT_REJECTED다. 그 밖의 실패(시간 초과, 5xx, 읽지 못한 응답,
     * "이미 처리된 결제", 예상하지 못한 예외)는 승인되었을 수 있으므로 주문번호로 조회해 같은 결제면 이어 간다.
     */
    private PaymentResult approve(String paymentKey, String orderNumber, long amount) {
        try {
            return gateway.confirm(paymentKey, orderNumber, amount);
        } catch (RuntimeException e) {
            if (isRejection(e)) {
                throw new BusinessException(ErrorCode.PAYMENT_REJECTED);
            }
            log.warn("승인 결과를 알 수 없어 조회한다: orderNumber={}, cause={}", orderNumber, e.toString());
        }
        Optional<PaymentResult> found;
        try {
            found = gateway.findByOrderId(orderNumber).filter(result -> paymentKey.equals(result.paymentKey()));
        } catch (RuntimeException e) {
            found = Optional.empty();
        }
        if (found.isPresent()) {
            return found.get();
        }
        // 승인이 늦게 도착해 돈만 빠져나가는 일을 줄이려고 취소를 한 번 시도한다.
        // shortcut: 이 취소 뒤에 승인이 도착하면 결제만 남고, 같은 orderId로 다른 paymentKey에 DONE 결제가 있어도 채택하거나 취소하지 않는다(돈은 남고 주문은 만료된다), 정산 대사 배치가 생기면 그 배치가 찾아 처리한다.
        try {
            gateway.cancel(paymentKey, "승인 응답 시간 초과");
        } catch (RuntimeException e) {
            log.warn("승인 확인 실패 뒤 취소 실패: orderNumber={}, paymentKey={}, cause={}", orderNumber, paymentKey, e.toString());
        }
        throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
    }

    private static boolean isRejection(RuntimeException e) {
        return e instanceof PaymentGatewayException gatewayError
                && gatewayError.getStatus() >= 400 && gatewayError.getStatus() < 500
                && !UNKNOWN.equals(gatewayError.getCode()) && !ALREADY_PROCESSED.equals(gatewayError.getCode());
    }

    /**
     * 결제 완료 주문을 전부 취소한다. 회원 행을 잠근 채 토스 취소를 부른 뒤 결제 기록과 주문, 재고·쿠폰·적립금을 되돌린다.
     * 토스 취소가 성공한 뒤 DB가 실패해도 다시 요청하면 토스가 "이미 취소됨"을 알려 주므로(클라이언트가 성공으로 처리한다) 나머지가 마무리된다.
     */
    @Transactional
    // shortcut: 회원 행 잠금과 DB 커넥션을 토스 호출(취소, 호출당 연결 3초 + 읽기 10초) 동안 쥔다. 토스가 느리면 Hikari 풀(기본 10)이 바닥날 수 있다, Phase 12에서 잠금·검사 트랜잭션 → 토스 호출 → 마무리 트랜잭션으로 나누거나 전용 풀을 둔다.
    public OrderDetailResponse cancel(Long memberId, String orderNumber) {
        memberService.getActiveForUpdate(memberId);
        Instant now = Instant.now(clock);
        Order order = orderRepository.findByOrderNumberAndMemberId(orderNumber, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        boolean canceledByUser = order.getStatus() == OrderStatus.CANCELED
                && order.getCancelReason() == CancelReason.USER;
        if (!canceledByUser) {
            if (order.getStatus() != OrderStatus.PAID) {
                throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE);
            }
            Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
            try {
                gateway.cancel(payment.getPaymentKey(), "고객 요청");
            } catch (RuntimeException e) {
                log.warn("결제 취소 실패: orderNumber={}, cause={}", orderNumber, e.toString());
                throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
            }
            payment.cancel(now);
            orderCanceler.cancel(order, CancelReason.USER, now);
            orderRepository.flush();
        }
        return OrderDetailResponse.of(order, orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()));
    }

    private void compensate(String orderNumber, String paymentKey) {
        try {
            gateway.cancel(paymentKey, "주문 처리 실패");
        } catch (RuntimeException e) {
            log.error("보상 취소 실패: orderNumber={}, paymentKey={}, cause={}", orderNumber, paymentKey, e.toString());
        }
    }
}
