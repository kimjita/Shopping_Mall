package com.sprint.mission.order.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.dto.AdminOrderDetailResponse;
import com.sprint.mission.order.dto.AdminOrderDetailResponse.AdminPayment;
import com.sprint.mission.order.dto.AdminOrderPage;
import com.sprint.mission.order.dto.AdminOrderSummary;
import com.sprint.mission.order.dto.OrderDetailResponse;
import com.sprint.mission.order.repository.OrderItemRepository;
import com.sprint.mission.order.repository.OrderRepository;
import com.sprint.mission.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final Pattern CURSOR = Pattern.compile("[1-9][0-9]{0,18}");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    /**
     * 전체 주문을 최신순으로 한 페이지 읽는다. status가 null이면 모든 상태다. 커서는 마지막 주문의 id이고,
     * 회원 이메일과 주문상품은 페이지 단위로 한 번에 읽는다.
     */
    @Transactional(readOnly = true)
    public AdminOrderPage list(OrderStatus status, String cursor, int size) {
        Long cursorId = parseCursor(cursor);

        // 다음 페이지가 있는지 알기 위해 한 개 더 읽는다.
        Limit limit = Limit.of(size + 1);
        List<Order> rows;
        if (status == null) {
            rows = cursorId == null
                    ? orderRepository.findAllByOrderByIdDesc(limit)
                    : orderRepository.findByIdLessThanOrderByIdDesc(cursorId, limit);
        } else {
            rows = cursorId == null
                    ? orderRepository.findByStatusOrderByIdDesc(status, limit)
                    : orderRepository.findByStatusAndIdLessThanOrderByIdDesc(status, cursorId, limit);
        }
        boolean hasNext = rows.size() > size;
        List<Order> page = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        if (page.isEmpty()) {
            return new AdminOrderPage(List.of(), null);
        }

        Map<Long, List<OrderItem>> itemsByOrder = orderItemRepository
                .findByOrderIdInOrderByIdAsc(page.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, String> emails = memberRepository
                .findAllById(page.stream().map(Order::getMemberId).distinct().toList()).stream()
                .collect(Collectors.toMap(Member::getId, Member::getEmail));
        List<AdminOrderSummary> summaries = page.stream().map(order -> {
            List<OrderItem> items = itemsByOrder.get(order.getId());
            return AdminOrderSummary.of(order, emails.get(order.getMemberId()), items.get(0), items.size());
        }).toList();
        return new AdminOrderPage(summaries, nextCursor);
    }

    /** 주문 하나를 주문자와 결제 정보까지 읽는다. 결제 전 주문이면 payment가 null이다. */
    @Transactional(readOnly = true)
    public AdminOrderDetailResponse get(String orderNumber) {
        return detail(orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND)));
    }

    /**
     * 주문을 PAID → PREPARING → SHIPPING → DELIVERED로 한 단계씩 바꾼다. 대상 상태를 먼저 검사하고(400),
     * 주문을 잠가 읽은 뒤(404) 지금 상태에서 갈 수 없으면 409다.
     */
    @Transactional
    public AdminOrderDetailResponse changeStatus(String orderNumber, OrderStatus status) {
        if (!OrderStatus.ADMIN_TARGETS.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Order order = lockOrder(orderNumber);
        if (!order.getStatus().canTransitionTo(status)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        order.advance(status, Instant.now(clock));
        return detail(order);
    }

    /**
     * 주문자 회원 행을 잠근 뒤 주문을 읽는다. 사용자 취소·결제 승인·만료와 같은 순서(회원 → 주문)로 줄을 선다.
     * 쓰기 트랜잭션 안에서 불러야 한다(없으면 회원 잠금이 곧바로 풀리므로 MANDATORY). 회원은 탈퇴했어도 잠근다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Order lockOrder(String orderNumber) {
        Long memberId = orderRepository.findMemberIdByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        memberRepository.findWithLockById(memberId);
        return orderRepository.findByOrderNumber(orderNumber).orElseThrow();
    }

    private AdminOrderDetailResponse detail(Order order) {
        String email = memberRepository.findById(order.getMemberId()).map(Member::getEmail).orElse(null);
        AdminPayment payment = paymentRepository.findByOrderId(order.getId()).map(AdminPayment::from).orElse(null);
        return new AdminOrderDetailResponse(
                OrderDetailResponse.of(order, orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())),
                order.getMemberId(), email, payment);
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
