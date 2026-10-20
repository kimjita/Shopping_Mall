package com.sprint.mission.payment.service;

import com.sprint.mission.cart.repository.CartItemRepository;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.dto.OrderDetailResponse;
import com.sprint.mission.order.repository.OrderItemRepository;
import com.sprint.mission.order.repository.OrderRepository;
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
    private final PaymentGateway gateway;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 결제를 승인하고 주문을 결제 완료로 바꾼다. 회원 행을 잠근 채 토스를 부르므로 같은 회원의 승인·만료·새 주문은 줄을 선다.
     * 토스가 승인한 뒤 우리 쪽에서 실패하면(커밋 실패 포함) 트랜잭션 밖에서 토스 취소로 보상하고 원래 예외를 던진다.
     */
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
        // shortcut: 이 취소 뒤에 승인이 도착하면 결제만 남는다, 정산 대사 배치가 생기면 그 배치가 찾아 취소한다.
        try {
            gateway.cancel(paymentKey, "승인 응답 시간 초과");
        } catch (RuntimeException e) {
            log.warn("승인 확인 실패 뒤 취소 실패: orderNumber={}, paymentKey={}", orderNumber, paymentKey);
        }
        throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
    }

    private static boolean isRejection(RuntimeException e) {
        return e instanceof PaymentGatewayException gatewayError
                && gatewayError.getStatus() >= 400 && gatewayError.getStatus() < 500
                && !UNKNOWN.equals(gatewayError.getCode()) && !ALREADY_PROCESSED.equals(gatewayError.getCode());
    }

    private void compensate(String orderNumber, String paymentKey) {
        try {
            gateway.cancel(paymentKey, "주문 처리 실패");
        } catch (RuntimeException e) {
            log.error("보상 취소 실패: orderNumber={}, paymentKey={}", orderNumber, paymentKey);
        }
    }
}
